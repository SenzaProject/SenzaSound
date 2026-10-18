package com.senzaproject.senzasound.data

import android.content.Context
import android.content.SharedPreferences
import com.senzaproject.senzasound.dsp.EffectEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Sumber kebenaran tunggal: menyimpan pengaturan dan mendorongnya ke mesin efek. */
object AppRepo {
    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    @Synchronized
    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("senza_sound", Context.MODE_PRIVATE)
        prefs = p
        _state.value = Codec.decode(p.getString("state", null))
    }

    @Synchronized
    fun update(block: (AppState) -> AppState) {
        val next = block(_state.value)
        _state.value = next
        prefs?.edit()?.putString("state", Codec.encode(next))?.apply()
        EffectEngine.update(next)
    }
}

private object Codec {
    private fun floats(a: JSONArray?): List<Float> {
        val out = MutableList(Eq.BANDS) { 0f }
        if (a != null) for (i in 0 until minOf(a.length(), Eq.BANDS)) out[i] = a.optDouble(i, 0.0).toFloat()
        return out
    }

    private fun settingsToJson(s: SoundSettings) = JSONObject().apply {
        put("bands", JSONArray(s.bands.map { it.toDouble() }))
        put("eqPreset", s.eqPreset)
        put("ieq", s.ieq.name)
        put("ieqAmount", s.ieqAmount.toDouble())
        put("spatial", s.spatial.toDouble())
        put("dialog", s.dialog.toDouble())
        put("bass", s.bass.toDouble())
        put("leveler", s.leveler.toDouble())
        put("loudness", s.loudness.toDouble())
        put("safeVolume", s.safeVolume)
    }

    private fun settingsFromJson(o: JSONObject, d: SoundSettings) = SoundSettings(
        bands = if (o.has("bands")) floats(o.optJSONArray("bands")) else d.bands,
        eqPreset = o.optString("eqPreset", d.eqPreset),
        ieq = runCatching { Ieq.valueOf(o.optString("ieq")) }.getOrDefault(d.ieq),
        ieqAmount = o.optDouble("ieqAmount", d.ieqAmount.toDouble()).toFloat(),
        spatial = o.optDouble("spatial", d.spatial.toDouble()).toFloat(),
        dialog = o.optDouble("dialog", d.dialog.toDouble()).toFloat(),
        bass = o.optDouble("bass", d.bass.toDouble()).toFloat(),
        leveler = o.optDouble("leveler", d.leveler.toDouble()).toFloat(),
        loudness = o.optDouble("loudness", d.loudness.toDouble()).toFloat(),
        safeVolume = o.optBoolean("safeVolume", d.safeVolume),
    )

    fun encode(s: AppState): String {
        val root = JSONObject()
        root.put("enabled", s.enabled)
        root.put("profile", s.profile.name)
        val ps = JSONObject()
        s.profiles.forEach { (p, v) -> ps.put(p.name, settingsToJson(v)) }
        root.put("profiles", ps)
        val up = JSONObject()
        s.userPresets.forEach { (n, b) -> up.put(n, JSONArray(b.map { it.toDouble() })) }
        root.put("userPresets", up)
        return root.toString()
    }

    fun decode(json: String?): AppState {
        if (json.isNullOrBlank()) return AppState()
        return try {
            val root = JSONObject(json)
            val profiles = Profile.values().associateWith { p ->
                val o = root.optJSONObject("profiles")?.optJSONObject(p.name)
                val d = Defaults.forProfile(p)
                if (o != null) settingsFromJson(o, d) else d
            }
            val presets = LinkedHashMap<String, List<Float>>()
            root.optJSONObject("userPresets")?.let { up ->
                up.keys().forEach { k -> presets[k] = floats(up.optJSONArray(k)) }
            }
            AppState(
                enabled = root.optBoolean("enabled", true),
                profile = runCatching { Profile.valueOf(root.optString("profile")) }
                    .getOrDefault(Profile.MUSIC),
                profiles = profiles,
                userPresets = presets,
            )
        } catch (t: Throwable) {
            AppState()
        }
    }
}
