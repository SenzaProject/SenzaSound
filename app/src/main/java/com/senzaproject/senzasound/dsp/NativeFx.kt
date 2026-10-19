package com.senzaproject.senzasound.dsp

import android.media.audiofx.AudioEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Jembatan ke pustaka efek native libsenzafx.so (jika terpasang di vendor).
 * Konstruktor AudioEffect(type, uuid, ...) tersembunyi dan setParameter() berstatus protected,
 * jadi keduanya dipanggil lewat refleksi.
 * Bila pustaka tidak ada atau Android menolak refleksi, create() mengembalikan null dan mesin Java dipakai.
 */
class NativeFx private constructor(
    private val fx: AudioEffect,
    private val setParam: java.lang.reflect.Method,
) {
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
            val ctor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java, UUID::class.java, Integer.TYPE, Integer.TYPE,
            )
            ctor.isAccessible = true
            val method = AudioEffect::class.java.getDeclaredMethod(
                "setParameter", IntArray::class.java, ByteArray::class.java,
            )
            method.isAccessible = true
            val effect = ctor.newInstance(TYPE, IMPL, 0, session) as AudioEffect
            NativeFx(effect, method)
        } catch (t: Throwable) {
            null
        }
    }

    private fun floats(v: FloatArray): ByteArray {
        val b = ByteBuffer.allocate(4 * v.size).order(ByteOrder.nativeOrder())
        v.forEach { b.putFloat(it) }
        return b.array()
    }

    private fun send(key: Int, value: ByteArray) {
        setParam.invoke(fx, intArrayOf(key), value)
    }

    fun apply(
        on: Boolean,
        gainsDb: FloatArray,
        spatial: Float,
        leveler: Float,
        loudnessDb: Float,
        limiterDb: Float,
    ) {
        send(K_EQ, floats(gainsDb))
        send(K_SPATIAL, floats(floatArrayOf(spatial)))
        send(K_LEVELER, floats(floatArrayOf(leveler)))
        send(K_LOUDNESS, floats(floatArrayOf(loudnessDb)))
        send(K_LIMITER, floats(floatArrayOf(limiterDb)))
        send(K_ENABLE, ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(if (on) 1 else 0).array())
        fx.setEnabled(true)
    }

    fun release() {
        try { fx.release() } catch (t: Throwable) { }
    }
}
