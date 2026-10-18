package com.senzaproject.senzasound.dsp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.senzaproject.senzasound.data.AppRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Penguji suara: sweep 20 Hz–20 kHz, pink noise, dan tes kiri/kanan. */
class ToneGenerator {
    enum class Mode(val label: String) {
        SWEEP("Sweep 20Hz–20kHz"), PINK("Pink noise"), LEFT("Tes kiri"), RIGHT("Tes kanan")
    }

    private val _active = MutableStateFlow<Mode?>(null)
    val active: StateFlow<Mode?> = _active
    private val _session = MutableStateFlow(0)
    val session: StateFlow<Int> = _session

    @Volatile private var running = false
    private var thread: Thread? = null

    fun toggle(mode: Mode) {
        val wasSame = _active.value == mode
        stop()
        if (!wasSame) start(mode)
    }

    fun start(mode: Mode) {
        stop()
        running = true
        _active.value = mode
        val t = Thread { run(mode) }
        thread = t
        t.start()
    }

    fun stop() {
        running = false
        try { thread?.join(1500) } catch (e: InterruptedException) { }
        thread = null
        _active.value = null
        _session.value = 0
    }

    private fun run(mode: Mode) {
        val rate = 44100
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val sid = track.audioSessionId
            EffectEngine.attach(sid, AppRepo.state.value)
            _session.value = sid
            track.play()

            val frames = 2048
            val buf = ShortArray(frames * 2)
            var phase = 0.0
            var t = 0.0
            val sweepLen = 12.0
            var b0 = 0f; var b1 = 0f; var b2 = 0f; var b3 = 0f; var b4 = 0f; var b5 = 0f; var b6 = 0f
            while (running) {
                for (i in 0 until frames) {
                    var l = 0f
                    var r = 0f
                    when (mode) {
                        Mode.SWEEP -> {
                            val f = 20.0 * 1000.0.pow((t % sweepLen) / sweepLen)
                            phase += 2.0 * PI * f / rate
                            val v = (sin(phase) * 0.25).toFloat()
                            l = v; r = v
                        }
                        Mode.PINK -> {
                            // Filter pink noise Paul Kellet.
                            val w = Random.nextFloat() * 2f - 1f
                            b0 = 0.99886f * b0 + w * 0.0555179f
                            b1 = 0.99332f * b1 + w * 0.0750759f
                            b2 = 0.96900f * b2 + w * 0.1538520f
                            b3 = 0.86650f * b3 + w * 0.3104856f
                            b4 = 0.55000f * b4 + w * 0.5329522f
                            b5 = -0.7616f * b5 - w * 0.0168980f
                            val p = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.035f
                            b6 = w * 0.115926f
                            l = p; r = p
                        }
                        Mode.LEFT -> {
                            phase += 2.0 * PI * 1000.0 / rate
                            l = (sin(phase) * 0.25).toFloat()
                        }
                        Mode.RIGHT -> {
                            phase += 2.0 * PI * 1000.0 / rate
                            r = (sin(phase) * 0.25).toFloat()
                        }
                    }
                    buf[2 * i] = (l.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    buf[2 * i + 1] = (r.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    t += 1.0 / rate
                }
                track.write(buf, 0, buf.size)
            }
        } catch (t: Throwable) {
            // diabaikan; status akan kembali ke "mati"
        } finally {
            try {
                track?.let {
                    EffectEngine.detach(it.audioSessionId)
                    it.stop()
                    it.release()
                }
            } catch (t: Throwable) { }
            running = false
            _active.value = null
            _session.value = 0
        }
    }
}
