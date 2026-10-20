package com.senzaproject.senzasound.data

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Konstanta equalizer 32 band (1/3 oktaf, 20 Hz – 20 kHz). */
object Eq {
    const val BANDS = 32
    const val MAX_DB = 12f
    const val Q = 3.0f

    val FREQS: FloatArray = FloatArray(BANDS) { i -> (20.0 * Math.pow(1000.0, i / (BANDS - 1.0))).toFloat() }

    /** Batas atas tiap band (Hz) untuk prosesor dinamika. */
    val EDGES: FloatArray = FloatArray(BANDS) { i ->
        if (i == BANDS - 1) 20000f else sqrt(FREQS[i].toDouble() * FREQS[i + 1]).toFloat()
    }

    val LABELS: List<String> = FREQS.map { f ->
        if (f < 1000f) f.roundToInt().toString()
        else {
            val k = (f / 100f).roundToInt() / 10f
            (if (k == k.toInt().toFloat()) k.toInt().toString() else k.toString()) + "K"
        }
    }

    /** Titik jangkar 10 band untuk preset (diinterpolasi ke 32 band). */
    val ANCHOR_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    fun interpAnchors(a: List<Float>, f: Float): Float {
        if (f <= ANCHOR_FREQS.first()) return a.first()
        if (f >= ANCHOR_FREQS.last()) return a.last()
        for (i in 0 until ANCHOR_FREQS.size - 1) {
            val lo = ANCHOR_FREQS[i]
            val hi = ANCHOR_FREQS[i + 1]
            if (f <= hi) {
                val t = (ln(f) - ln(lo)) / (ln(hi) - ln(lo))
                return a[i] + (a[i + 1] - a[i]) * t
            }
        }
        return a.last()
    }

    fun expand(anchors: List<Float>): List<Float> = List(BANDS) { i -> interpAnchors(anchors, FREQS[i]) }

    val PRESETS: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Punch" to listOf(5f, 7f, 6f, 3f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(8f, 7f, 5f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f),
        "Vokal Jernih" to listOf(-1f, -1f, 0f, -1f, 1f, 3f, 5f, 4f, 2f, 1f),
        "Bass + Vokal" to listOf(6f, 6f, 4f, 0f, 0f, 2f, 4f, 3f, 1f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 3.5f, 5f, 6f),
        "Rock" to listOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 3.5f, 4.5f, 5f),
        "Pop" to listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 1f, 2f),
        "Jazz" to listOf(3f, 2f, 1f, 2f, -2f, -2f, 0f, 1f, 2f, 3f),
        "Klasik" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
        "Elektronik" to listOf(5f, 4f, 1f, 0f, -2f, 2f, 1f, 2f, 4f, 5f),
        "Hip-Hop" to listOf(5f, 4f, 2f, 3f, -1f, -1f, 1f, -1f, 2f, 3f),
        "Loudness" to listOf(6f, 4f, 0f, 0f, -1f, 0f, 0f, -1f, 4f, 6f),
        "Podcast" to listOf(-4f, -3f, -1f, 1f, 3f, 4f, 4f, 2f, 0f, -2f),
        "Redam Treble" to listOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -4f, -6f, -8f),
    )

    /** Gain 32 band untuk preset bawaan, atau null bila tidak ada. */
    fun presetBands(name: String): List<Float>? = PRESETS[name]?.let { expand(it) }
}

enum class Profile(val label: String) {
    MOVIE("Film"), MUSIC("Musik"), GAME("Game"), VOICE("Suara"), CUSTOM("Kustom")
}

/** "Intelligent EQ": kurva target yang dicampur ke equalizer. */
enum class Ieq(val label: String) {
    OFF("Mati"), BALANCED("Seimbang"), DETAILED("Detail"), WARM("Hangat")
}

enum class Endpoint(val label: String) {
    SPEAKER("Speaker"), HEADPHONE("Headphone"), BLUETOOTH("Bluetooth")
}

/** Semua pengaturan suara untuk satu profil. */
data class SoundSettings(
    val bands: List<Float> = List(Eq.BANDS) { 0f },
    val eqPreset: String = "Flat",
    val ieq: Ieq = Ieq.OFF,
    val ieqAmount: Float = 0.5f,
    val spatial: Float = 0f,
    val dialog: Float = 0f,
    val bass: Float = 0f,
    val leveler: Float = 0f,
    val loudness: Float = 0f,
    val safeVolume: Boolean = false,
)

object Defaults {
    fun forProfile(p: Profile): SoundSettings = when (p) {
        Profile.MOVIE -> SoundSettings(
            ieq = Ieq.BALANCED, ieqAmount = 0.6f, spatial = 0.8f, dialog = 0.6f,
            bass = 0.7f, leveler = 0.3f,
        )
        Profile.MUSIC -> SoundSettings(
            ieq = Ieq.BALANCED, ieqAmount = 0.5f, spatial = 0.35f, dialog = 0.35f,
            bass = 0.65f, leveler = 0.15f,
        )
        Profile.GAME -> SoundSettings(
            ieq = Ieq.DETAILED, ieqAmount = 0.7f, spatial = 0.7f, dialog = 0.35f,
            bass = 0.5f, leveler = 0.2f,
        )
        Profile.VOICE -> SoundSettings(
            bands = Eq.expand(Eq.PRESETS.getValue("Podcast")), eqPreset = "Podcast",
            ieq = Ieq.DETAILED, ieqAmount = 0.4f, dialog = 0.9f, leveler = 0.5f,
        )
        Profile.CUSTOM -> SoundSettings()
    }
}

data class AppState(
    val enabled: Boolean = true,
    val profile: Profile = Profile.MUSIC,
    val profiles: Map<Profile, SoundSettings> =
        Profile.values().associateWith { Defaults.forProfile(it) },
    val userPresets: Map<String, List<Float>> = emptyMap(),
) {
    val current: SoundSettings
        get() = profiles[profile] ?: Defaults.forProfile(profile)
}
