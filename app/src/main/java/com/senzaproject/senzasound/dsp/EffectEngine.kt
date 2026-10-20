package com.senzaproject.senzasound.dsp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.senzaproject.senzasound.data.AppState
import com.senzaproject.senzasound.data.Eq
import com.senzaproject.senzasound.data.Endpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ln
import kotlin.math.max

/**
 * Mesin efek Senza Sound. Memasang rantai efek pada sesi audio:
 * sesi 0 (global, jika perangkat mengizinkan) dan sesi yang diumumkan pemutar lain / pemutar internal.
 */
object EffectEngine {
    /** Status sesi yang sedang terpasang. */
    data class Status(
        val sessions: List<Int> = emptyList(),
        val globalAttached: Boolean = false,
        val errors: List<String> = emptyList(),
    )

    /** Kemampuan perangkat, diuji pada sesi uji sementara. */
    data class Caps(
        val probed: Boolean = false,
        val native: Boolean = false,
        val dynamics: Boolean = false,
        val legacyEq: Boolean = false,
        val bass: Boolean = false,
        val virtualizer: Boolean = false,
        val loudness: Boolean = false,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    private val _caps = MutableStateFlow(Caps())
    val caps: StateFlow<Caps> = _caps

    private val sessions = LinkedHashMap<Int, SessionFx>()
    private val notes = mutableListOf<String>()
    private var last = AppState()
    private var endpoint = Endpoint.SPEAKER

    @Synchronized
    fun update(s: AppState) {
        last = s
        sessions.values.forEach { it.apply(s, endpoint) }
        publish()
    }

    @Synchronized
    fun setEndpoint(e: Endpoint) {
        endpoint = e
        sessions.values.forEach { it.apply(last, endpoint) }
        publish()
    }

    @Synchronized
    fun attach(session: Int, s: AppState) {
        last = s
        val existing = sessions[session]
        if (existing != null) {
            existing.apply(s, endpoint)
        } else {
            val fx = SessionFx(session)
            fx.apply(s, endpoint)
            sessions[session] = fx
        }
        publish()
    }

    @Synchronized
    fun detach(session: Int) {
        sessions.remove(session)?.release()
        publish()
    }

    @Synchronized
    fun releaseAll() {
        sessions.values.forEach { it.release() }
        sessions.clear()
        publish()
    }

    /** Catatan galat dari komponen lain (mis. layanan latar). */
    @Synchronized
    fun report(message: String) {
        if (!notes.contains(message)) notes += message
        publish()
    }

    /** Menguji kemampuan perangkat memakai sesi audio sementara. Aman dipanggil dari thread latar. */
    fun probe() {
        var track: AudioTrack? = null
        try {
            val min = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(max(min, 4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val sid = track.audioSessionId
            val fx = SessionFx(sid)
            var legacy = false
            try {
                val e = Equalizer(0, sid)
                legacy = true
                e.release()
            } catch (t: Throwable) { }
            _caps.value = Caps(
                probed = true,
                native = fx.hasNative,
                dynamics = fx.hasDynamics,
                legacyEq = legacy || fx.hasLegacyEq,
                bass = fx.hasBass,
                virtualizer = fx.hasVirtualizer,
                loudness = fx.hasLoudness,
            )
            fx.release()
        } catch (t: Throwable) {
            _caps.value = Caps(probed = true)
        } finally {
            try { track?.release() } catch (t: Throwable) { }
        }
    }

    private fun publish() {
        val list = sessions.values.toList()
        _status.value = Status(
            sessions = sessions.keys.toList(),
            globalAttached = sessions.containsKey(0),
            errors = (notes + list.flatMap { f -> f.errors.map { "Sesi ${f.session}: $it" } }).distinct().take(8),
        )
    }
}

private class SessionFx(val session: Int) {
    private var nat: NativeFx? = null
    private var dp: DynamicsProcessing? = null
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    val errors = mutableListOf<String>()

    val hasNative get() = nat != null
    val hasDynamics get() = dp != null
    val hasLegacyEq get() = eq != null
    val hasBass get() = bass != null
    val hasVirtualizer get() = virt != null
    val hasLoudness get() = loud != null

    init {
        // Pustaka native (vendor) lebih diutamakan; bila ada, mesin Java tidak dipasang.
        nat = NativeFx.create(session)
        if (nat == null) initJavaChain()
    }

    private fun initJavaChain() {
        try {
            dp = buildDynamics()
        } catch (t: Throwable) {
            errors += "Prosesor dinamika tidak tersedia (${t.javaClass.simpleName})"
        }
        if (dp == null) {
            try {
                eq = Equalizer(0, session)
            } catch (t: Throwable) {
                errors += "EQ cadangan tidak tersedia (${t.javaClass.simpleName})"
            }
        }
        try { bass = BassBoost(0, session) } catch (t: Throwable) { errors += "Penguat bass tidak tersedia" }
        try { virt = Virtualizer(0, session) } catch (t: Throwable) { errors += "Virtualisasi spasial tidak tersedia" }
        try { loud = LoudnessEnhancer(session) } catch (t: Throwable) { errors += "Penguat kenyaringan tidak tersedia" }
    }

    private fun buildDynamics(): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, Eq.BANDS,   // pre-EQ 32 band
            true, 3,          // kompresor multiband (volume leveler)
            false, 0,         // post-EQ
            true,             // limiter
        ).build()
        return DynamicsProcessing(0, session, cfg)
    }

    fun apply(app: AppState, endpoint: Endpoint) {
        val on = app.enabled
        val s = app.current
        val gains = EqMath.effectiveGains(s, endpoint)

        nat?.let { n ->
            try {
                n.apply(on, gains, s.spatial, s.leveler, s.loudness * 8f, if (s.safeVolume) -12f else -1f, s.bass)
            } catch (t: Throwable) {
                errors += "Gagal menerapkan efek native: ${t.message}"
            }
            return
        }

        // Headroom: redam sedikit sebelum EQ agar tidak clipping, lalu kembalikan lewat penguat kenyaringan.
        val maxBoost = max(0f, gains.maxOrNull() ?: 0f)
        val atten = (maxBoost * 0.3f).coerceAtMost(4f)

        dp?.let { d ->
            try {
                for (i in 0 until Eq.BANDS) {
                    d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, Eq.EDGES[i], gains[i]))
                }
                d.setInputGainAllChannelsTo(-atten)

                // Volume leveler = kompresor 3 band (bass / vokal / treble).
                val l = s.leveler
                val cross = floatArrayOf(160f, 3500f, 20000f)
                val thr = -12f - 18f * l
                val ratio = 1.5f + 2.5f * l
                val makeup = -thr * (1f - 1f / ratio) * 0.6f
                for (i in 0 until 3) {
                    val band = DynamicsProcessing.MbcBand(
                        l > 0.02f, cross[i], 15f, 250f, ratio, thr, 6f, -80f, 1f, 0f, makeup,
                    )
                    d.setMbcBandAllChannelsTo(i, band)
                }
                // Limiter; "volume aman" menurunkan ambang batas.
                val limThr = if (s.safeVolume) -12f else -0.5f
                val limiter = DynamicsProcessing.Limiter(true, true, 0, 1f, 60f, 10f, limThr, 0f)
                d.setLimiterAllChannelsTo(limiter)
                d.setEnabled(on)
            } catch (t: Throwable) {
                errors += "Gagal menerapkan DSP: ${t.message}"
            }
        }

        eq?.let { e ->
            try {
                val range = e.bandLevelRange
                for (b in 0 until e.numberOfBands) {
                    val hz = e.getCenterFreq(b.toShort()) / 1000f
                    val g = interpolate(gains, hz)
                    val mb = (g * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
                e.setEnabled(on)
            } catch (t: Throwable) {
                errors += "Gagal menerapkan EQ: ${t.message}"
            }
        }

        bass?.let { b ->
            try {
                if (b.strengthSupported) b.setStrength((s.bass * 1000f).toInt().toShort())
                b.setEnabled(on && s.bass > 0.02f)
            } catch (t: Throwable) { }
        }

        virt?.let { v ->
            try {
                val mode = if (endpoint == Endpoint.SPEAKER) Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
                else Virtualizer.VIRTUALIZATION_MODE_BINAURAL
                try { v.forceVirtualizationMode(mode) } catch (t: Throwable) { }
                if (v.strengthSupported) v.setStrength((s.spatial * 1000f).toInt().toShort())
                v.setEnabled(on && s.spatial > 0.02f)
            } catch (t: Throwable) { }
        }

        loud?.let { l ->
            try {
                // gain = kenyaringan pengguna + pengganti headroom yang diredam di atas
                val mb = (s.loudness * 800f + atten * 100f * 0.9f).toInt()
                l.setTargetGain(mb)
                l.setEnabled(on && mb > 20)
            } catch (t: Throwable) { }
        }
    }

    /** Interpolasi gain pada skala frekuensi logaritmik. */
    private fun interpolate(gains: FloatArray, hz: Float): Float {
        if (hz <= Eq.FREQS.first()) return gains.first()
        if (hz >= Eq.FREQS.last()) return gains.last()
        for (i in 0 until Eq.BANDS - 1) {
            val a = Eq.FREQS[i]; val b = Eq.FREQS[i + 1]
            if (hz in a..b) {
                val t = (ln(hz) - ln(a)) / (ln(b) - ln(a))
                return gains[i] + (gains[i + 1] - gains[i]) * t
            }
        }
        return 0f
    }

    fun release() {
        nat?.release()
        nat = null
        try { dp?.release() } catch (t: Throwable) { }
        try { eq?.release() } catch (t: Throwable) { }
        try { bass?.release() } catch (t: Throwable) { }
        try { virt?.release() } catch (t: Throwable) { }
        try { loud?.release() } catch (t: Throwable) { }
        dp = null; eq = null; bass = null; virt = null; loud = null
    }
}
