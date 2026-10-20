package com.senzaproject.senzasound.dsp

import com.senzaproject.senzasound.data.Endpoint
import com.senzaproject.senzasound.data.Eq
import com.senzaproject.senzasound.data.Ieq
import com.senzaproject.senzasound.data.SoundSettings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object EqMath {
    private val IEQ_ANCHORS = mapOf(
        Ieq.BALANCED to listOf(2.5f, 2f, 1f, 0f, -0.5f, 0f, 0.5f, 1f, 0.5f, -1f),
        Ieq.DETAILED to listOf(2f, 1f, 1f, 0.5f, 0f, 0.5f, 2f, 3f, 3f, 1f),
        Ieq.WARM to listOf(3.5f, 3f, 2f, 1f, 0f, -0.5f, -1f, -1.5f, -2.5f, -3.5f),
    )

    private fun octaves(f: Float, center: Float): Float = ln(f / center) / ln(2f)
    private fun bump(f: Float, center: Float, sigma: Float): Float {
        val o = octaves(f, center)
        return exp(-(o * o) / (2f * sigma * sigma))
    }

    /** Kurva penguat dialog (dB pada amount = 1): kejernihan vokal 2–5 kHz, bobot 180 Hz, kurangi "keruh" 380 Hz. */
    fun dialogShape(f: Float): Float =
        5.5f * bump(f, 2800f, 0.9f) + 2f * bump(f, 180f, 0.8f) - 2.5f * bump(f, 380f, 0.6f)

    /**
     * Kurva bass (dB pada amount = 1). Speaker kecil tak mampu memproduksi sub-bass, jadi tenaga
     * dipusatkan di 120–200 Hz; headphone mendapat dorongan sub-bass 55 Hz yang dalam.
     */
    fun bassShape(f: Float, endpoint: Endpoint): Float =
        if (endpoint == Endpoint.SPEAKER) 7f * bump(f, 120f, 0.75f) + 3f * bump(f, 200f, 0.6f)
        else 8f * bump(f, 55f, 0.8f) + 4.5f * bump(f, 110f, 0.8f)

    /** Gabungan EQ pengguna + Intelligent EQ + penguat dialog + bass (dB per band, 32 band). */
    fun effectiveGains(s: SoundSettings, endpoint: Endpoint): FloatArray {
        val out = FloatArray(Eq.BANDS)
        val ieq = IEQ_ANCHORS[s.ieq]
        for (i in 0 until Eq.BANDS) {
            val f = Eq.FREQS[i]
            var g = if (i < s.bands.size) s.bands[i] else 0f
            if (ieq != null) g += Eq.interpAnchors(ieq, f) * s.ieqAmount
            g += dialogShape(f) * s.dialog
            g += bassShape(f, endpoint) * s.bass
            out[i] = g.coerceIn(-18f, 18f)
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
            val alpha = sin(w0) / (2.0 * Eq.Q)
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
