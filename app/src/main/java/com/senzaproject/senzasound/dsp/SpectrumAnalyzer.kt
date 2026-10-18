package com.senzaproject.senzasound.dsp

import android.media.audiofx.Visualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** Analyzer spektrum real-time berbasis android.media.audiofx.Visualizer (butuh izin mikrofon). */
class SpectrumAnalyzer {
    companion object { const val BANDS = 28 }

    private val _bands = MutableStateFlow(FloatArray(BANDS))
    val bands: StateFlow<FloatArray> = _bands
    private var visualizer: Visualizer? = null
    private val smooth = FloatArray(BANDS)

    fun start(session: Int): Boolean {
        stop()
        return try {
            val v = Visualizer(session)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(minOf(range[1], 1024))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) process(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.setEnabled(true)
            visualizer = v
            true
        } catch (t: Throwable) {
            visualizer = null
            false
        }
    }

    fun stop() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (t: Throwable) { }
        visualizer = null
        _bands.value = FloatArray(BANDS)
    }

    private fun process(fft: ByteArray) {
        val n = fft.size / 2
        if (n < 8) return
        val out = FloatArray(BANDS)
        val maxBin = (n - 1).toFloat()
        for (b in 0 until BANDS) {
            val lo = max(1, (maxBin.pow(b.toFloat() / BANDS)).toInt())
            val hi = max(lo + 1, (maxBin.pow((b + 1).toFloat() / BANDS)).toInt())
            var peak = 0f
            for (k in lo until minOf(hi, n)) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                peak = max(peak, hypot(re, im))
            }
            out[b] = (ln(1f + peak) / ln(182f)).coerceIn(0f, 1f)
        }
        for (i in 0 until BANDS) {
            smooth[i] = if (out[i] > smooth[i]) out[i] else smooth[i] * 0.85f + out[i] * 0.15f
        }
        _bands.value = smooth.copyOf()
    }
}
