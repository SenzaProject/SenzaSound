package com.senzaproject.senzasound.data

/** Konstanta equalizer 10 band. */
object Eq {
    val FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val LABELS = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")

    /** Batas atas tiap band (Hz) untuk DynamicsProcessing. */
    val EDGES = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f)
    const val BANDS = 10
    const val MAX_DB = 12f

    val PRESETS: LinkedHashMap<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(6f, 5f, 4f, 2f, 0.5f, 0f, 0f, 0f, 0f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 2f, 3.5f, 5f, 6f),
        "Vokal" to listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f),
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
            ieq = Ieq.BALANCED, ieqAmount = 0.6f, spatial = 0.8f, dialog = 0.5f,
            bass = 0.4f, leveler = 0.3f,
        )
        Profile.MUSIC -> SoundSettings(
            ieq = Ieq.BALANCED, ieqAmount = 0.5f, spatial = 0.35f, bass = 0.35f, leveler = 0.1f,
        )
        Profile.GAME -> SoundSettings(
            ieq = Ieq.DETAILED, ieqAmount = 0.7f, spatial = 0.7f, dialog = 0.2f,
            bass = 0.3f, leveler = 0.2f,
        )
        Profile.VOICE -> SoundSettings(
            bands = Eq.PRESETS.getValue("Podcast"), eqPreset = "Podcast",
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
