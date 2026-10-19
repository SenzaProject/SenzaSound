package com.senzaproject.senzasound.dsp

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
 * sesi 0 (global, jika ROM mengizinkan) dan sesi yang diumumkan pemutar lain / pemutar internal.
 */
object EffectEngine {
    data class Status(
        val sessions: List<Int> = emptyList(),
        val native: Boolean = false,
        val dynamics: Boolean = false,
        val legacyEq: Boolean = false,
        val bass: Boolean = false,
        val virtualizer: Boolean = false,
        val loudness: Boolean = false,
        val errors: List<String> = emptyList(),
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private val sessions = LinkedHashMap<Int, SessionFx>()
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

    private fun publish() {
        val list = sessions.values.toList()
        _status.value = Status(
            sessions = sessions.keys.toList(),
            native = list.any { it.hasNative },
            dynamics = list.any { it.hasDynamics },
            legacyEq = list.any { it.hasLegacyEq },
            bass = list.any { it.hasBass },
            virtualizer = list.any { it.hasVirtualizer },
            loudness = list.any { it.hasLoudness },
            errors = list.flatMap { f -> f.errors.map { "Sesi ${f.session}: $it" } }.distinct().take(8),
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
            errors += "DynamicsProcessing tidak tersedia (${t.javaClass.simpleName})"
        }
        if (dp == null) {
            try {
                eq = Equalizer(0, session)
            } catch (t: Throwable) {
                errors += "Equalizer tidak tersedia (${t.javaClass.simpleName})"
            }
        }
        try { bass = BassBoost(0, session) } catch (t: Throwable) { errors += "BassBoost tidak tersedia" }
        try { virt = Virtualizer(0, session) } catch (t: Throwable) { errors += "Virtualizer tidak tersedia" }
        try { loud = LoudnessEnhancer(session) } catch (t: Throwable) { errors += "LoudnessEnhancer tidak tersedia" }
    }

    private fun buildDynamics(): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, Eq.BANDS,   // pre-EQ
            true, 3,          // multiband compressor (volume leveler)
            false, 0,         // post-EQ
            true,             // limiter
        ).build()
        return DynamicsProcessing(0, session, cfg)
    }

    fun apply(app: AppState, endpoint: Endpoint) {
        val on = app.enabled
        val s = app.current
        val gains = EqMath.effectiveGains(s)

        nat?.let { n ->
            try {
                n.apply(on, gains, s.spatial, s.leveler, s.loudness * 8f, if (s.safeVolume) -12f else -1f)
            } catch (t: Throwable) {
                errors += "Gagal menerapkan efek native: ${t.message}"
            }
            return
        }

        dp?.let { d ->
            try {
                for (i in 0 until Eq.BANDS) {
                    d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, Eq.EDGES[i], gains[i]))
                }
                // Headroom agar boost EQ tidak membuat distorsi.
                val maxBoost = gains.maxOrNull() ?: 0f
                d.setInputGainAllChannelsTo(-(max(0f, maxBoost) * 0.5f).coerceAtMost(6f))

                // Volume leveler = kompresor 3 band.
                val l = s.leveler
                val cross = floatArrayOf(300f, 3000f, 20000f)
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
                val limThr = if (s.safeVolume) -12f else -1f
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
                if (b.strengthSupported) b.setStrength((s.bass * 700f).toInt().toShort())
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
                l.setTargetGain((s.loudness * 800f).toInt())
                l.setEnabled(on && s.loudness > 0.02f)
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
