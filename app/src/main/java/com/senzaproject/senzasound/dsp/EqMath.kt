package com.senzaproject.senzasound.dsp

import com.senzaproject.senzasound.data.Eq
import com.senzaproject.senzasound.data.Ieq
import com.senzaproject.senzasound.data.SoundSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object EqMath {
    private val IEQ_CURVES = mapOf(
        Ieq.BALANCED to floatArrayOf(2.5f, 2f, 1f, 0f, -0.5f, 0f, 0.5f, 1f, 0.5f, -1f),
        Ieq.DETAILED to floatArrayOf(2f, 1f, 1f, 0.5f, 0f, 0.5f, 2f, 3f, 3f, 1f),
        Ieq.WARM to floatArrayOf(3.5f, 3f, 2f, 1f, 0f, -0.5f, -1f, -1.5f, -2.5f, -3.5f),
    )
    private val DIALOG = floatArrayOf(0f, -1f, -1.5f, -1f, 0f, 2f, 4f, 4f, 1f, 0f)
    private val BASS = floatArrayOf(5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f)

    /** Gabungan EQ pengguna + Intelligent EQ + penguat dialog + bass (dB per band). */
    fun effectiveGains(s: SoundSettings): FloatArray {
        val out = FloatArray(Eq.BANDS)
        val curve = IEQ_CURVES[s.ieq]
        for (i in 0 until Eq.BANDS) {
            var g = if (i < s.bands.size) s.bands[i] else 0f
            if (curve != null) g += curve[i] * s.ieqAmount
            g += DIALOG[i] * s.dialog
            g += BASS[i] * s.bass * 0.6f
            out[i] = g.coerceIn(-15f, 15f)
        }
        return out
    }

    /** Respons frekuensi gabungan (dB) dari filter peaking tiap band. */
    fun responseDb(gains: FloatArray, freq: Float): Float {
        val fs = 48000.0
        val w = 2.0 * PI * freq / fs
        val cw = cos(w); val sw = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        var total = 0.0
        for (i in gains.indices) {
            if (abs(gains[i]) < 0.01f) continue
            val a = 10.0.pow(gains[i] / 40.0)
            val w0 = 2.0 * PI * Eq.FREQS[i] / fs
            val alpha = sin(w0) / (2.0 * 1.2)
            val b0 = 1 + alpha * a
            val b1 = -2 * cos(w0)
            val b2 = 1 - alpha * a
            val a0 = 1 + alpha / a
            val a2 = 1 - alpha / a
            val nr = b0 + b1 * cw + b2 * c2
            val ni = -(b1 * sw + b2 * s2)
            val dr = a0 + b1 * cw + a2 * c2
            val di = -(b1 * sw + a2 * s2)
            val mag = sqrt((nr * nr + ni * ni) / (dr * dr + di * di))
            total += 20.0 * log10(mag)
        }
        return total.toFloat()
    }
}
