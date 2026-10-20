package com.senzaproject.senzasound.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.senzaproject.senzasound.data.AppRepo
import com.senzaproject.senzasound.data.AppState
import com.senzaproject.senzasound.data.Defaults
import com.senzaproject.senzasound.data.Endpoint
import com.senzaproject.senzasound.data.Eq
import com.senzaproject.senzasound.data.Ieq
import com.senzaproject.senzasound.data.Profile
import com.senzaproject.senzasound.data.SoundSettings
import com.senzaproject.senzasound.dsp.EffectEngine
import com.senzaproject.senzasound.dsp.PlayerController
import com.senzaproject.senzasound.dsp.SpectrumAnalyzer
import com.senzaproject.senzasound.dsp.ToneGenerator
import com.senzaproject.senzasound.service.EffectService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SoundViewModel(private val app: Application) : AndroidViewModel(app) {
    val state: StateFlow<AppState> = AppRepo.state
    val engine = EffectEngine.status
    val caps = EffectEngine.caps
    val player = PlayerController(app)
    val analyzer = SpectrumAnalyzer()
    val tone = ToneGenerator()

    private val _endpoint = MutableStateFlow(Endpoint.SPEAKER)
    val endpoint: StateFlow<Endpoint> = _endpoint

    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshEndpoint()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshEndpoint()
    }

    init {
        AppRepo.init(app)
        refreshEndpoint()
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        // Pasang efek global langsung dari proses aplikasi, lalu uji kemampuan perangkat di thread latar.
        try { EffectEngine.attach(0, AppRepo.state.value) } catch (t: Throwable) { }
        viewModelScope.launch(Dispatchers.Default) { EffectEngine.probe() }
        viewModelScope.launch {
            while (true) {
                player.tick()
                delay(300)
            }
        }
    }

    private fun refreshEndpoint() {
        var result = Endpoint.SPEAKER
        for (d in audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                26, // TYPE_BLE_HEADSET
                -> result = Endpoint.BLUETOOTH
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> if (result != Endpoint.BLUETOOTH) result = Endpoint.HEADPHONE
            }
        }
        _endpoint.value = result
        EffectEngine.setEndpoint(result)
    }

    // ---- Pengaturan ----
    private fun edit(f: (SoundSettings) -> SoundSettings) {
        AppRepo.update { s -> s.copy(profiles = s.profiles + (s.profile to f(s.current))) }
    }

    fun setEnabled(on: Boolean) {
        AppRepo.update { it.copy(enabled = on) }
        if (on) ensureService() else app.stopService(Intent(app, EffectService::class.java))
    }

    fun ensureService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, EffectService::class.java))
        } catch (t: Throwable) { }
    }

    fun setProfile(p: Profile) = AppRepo.update { it.copy(profile = p) }
    fun setSpatial(v: Float) = edit { it.copy(spatial = v) }
    fun setDialog(v: Float) = edit { it.copy(dialog = v) }
    fun setBass(v: Float) = edit { it.copy(bass = v) }
    fun setLeveler(v: Float) = edit { it.copy(leveler = v) }
    fun setLoudness(v: Float) = edit { it.copy(loudness = v) }
    fun setIeq(i: Ieq) = edit { it.copy(ieq = i) }
    fun setIeqAmount(v: Float) = edit { it.copy(ieqAmount = v) }
    fun setSafeVolume(b: Boolean) = edit { it.copy(safeVolume = b) }

    fun setBand(index: Int, db: Float) = edit { s ->
        val b = s.bands.toMutableList()
        b[index] = db.coerceIn(-Eq.MAX_DB, Eq.MAX_DB)
        s.copy(bands = b, eqPreset = "Kustom")
    }

    fun applyPreset(name: String) {
        val bands = Eq.presetBands(name) ?: AppRepo.state.value.userPresets[name] ?: return
        edit { it.copy(bands = bands, eqPreset = name) }
    }

    fun resetEq() = applyPreset("Flat")

    fun savePreset(name: String) {
        val n = name.trim()
        if (n.isEmpty() || Eq.PRESETS.containsKey(n)) return
        AppRepo.update { s ->
            val bands = s.current.bands
            s.copy(
                userPresets = s.userPresets + (n to bands),
                profiles = s.profiles + (s.profile to s.current.copy(eqPreset = n)),
            )
        }
    }

    fun deletePreset(name: String) {
        AppRepo.update { s -> s.copy(userPresets = s.userPresets - name) }
    }

    fun resetProfile() {
        AppRepo.update { s -> s.copy(profiles = s.profiles + (s.profile to Defaults.forProfile(s.profile))) }
    }

    // ---- Analyzer: pilih sesi yang aktif ----
    fun updateAnalyzer(hasPermission: Boolean) {
        if (!hasPermission) {
            analyzer.stop()
            return
        }
        val toneSession = tone.session.value
        val p = player.ui.value
        val session = when {
            toneSession > 0 -> toneSession
            p.playing && p.sessionId > 0 -> p.sessionId
            else -> 0
        }
        analyzer.start(session)
    }

    fun toggleTone(mode: ToneGenerator.Mode, hasPermission: Boolean) {
        player.release()
        tone.toggle(mode)
        updateAnalyzer(hasPermission)
    }

    override fun onCleared() {
        analyzer.stop()
        tone.stop()
        player.release()
        try { audioManager.unregisterAudioDeviceCallback(deviceCallback) } catch (t: Throwable) { }
        super.onCleared()
    }
}
