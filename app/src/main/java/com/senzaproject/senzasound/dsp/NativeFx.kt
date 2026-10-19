package com.senzaproject.senzasound.dsp

import android.media.audiofx.AudioEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Jembatan ke pustaka efek native libsenzafx.so (jika terpasang di vendor).
 * Bila pustaka tidak ada, create() mengembalikan null dan mesin Java dipakai.
 */
class NativeFx private constructor(private val fx: AudioEffect) {
    companion object {
        val TYPE: UUID = UUID.fromString("634b1d63-68d0-4951-9fde-bbd2bf82af18")
        val IMPL: UUID = UUID.fromString("861f6b79-b150-4566-9994-c2a58291efa1")

        private const val K_ENABLE = 1
        private const val K_EQ = 2
        private const val K_SPATIAL = 3
        private const val K_LEVELER = 4
        private const val K_LOUDNESS = 5
        private const val K_LIMITER = 6

        fun create(session: Int): NativeFx? = try {
            NativeFx(AudioEffect(TYPE, IMPL, 0, session))
        } catch (t: Throwable) {
            null
        }
    }

    private fun key(k: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(k).array()

    private fun floats(v: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(4 * v.size).order(ByteOrder.nativeOrder())
        v.forEach { b.putFloat(it) }
        return b.array()
    }

    private fun int(v: Int): ByteArray = key(v)

    fun apply(
        on: Boolean,
        gainsDb: FloatArray,
        spatial: Float,
        leveler: Float,
        loudnessDb: Float,
        limiterDb: Float,
    ) {
        fx.setParameter(key(K_EQ), floats(gainsDb))
        fx.setParameter(key(K_SPATIAL), floats(floatArrayOf(spatial)))
        fx.setParameter(key(K_LEVELER), floats(floatArrayOf(leveler)))
        fx.setParameter(key(K_LOUDNESS), floats(floatArrayOf(loudnessDb)))
        fx.setParameter(key(K_LIMITER), floats(floatArrayOf(limiterDb)))
        fx.setParameter(key(K_ENABLE), int(if (on) 1 else 0))
        fx.setEnabled(true)
    }

    fun release() {
        try { fx.release() } catch (t: Throwable) { }
    }
}
