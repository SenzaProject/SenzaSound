package com.senzaproject.senzasound.dsp

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import com.senzaproject.senzasound.data.AppRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Pemutar musik sederhana dengan sesi audio sendiri, sehingga efek pasti aktif. */
class PlayerController(private val ctx: Context) {
    data class Track(val uri: Uri, val title: String)
    data class UiState(
        val tracks: List<Track> = emptyList(),
        val index: Int = -1,
        val playing: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val sessionId: Int = 0,
        val error: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    private var mp: MediaPlayer? = null

    private fun nameOf(uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        } catch (t: Throwable) { }
        return uri.lastPathSegment ?: "Trek"
    }

    fun setTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach {
            try {
                ctx.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (t: Throwable) { }
        }
        _ui.value = UiState(tracks = uris.map { Track(it, nameOf(it)) })
        play(0)
    }

    fun play(i: Int) {
        val tracks = _ui.value.tracks
        if (i !in tracks.indices) return
        releasePlayer()
        try {
            val m = MediaPlayer()
            m.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            m.setDataSource(ctx, tracks[i].uri)
            m.setOnCompletionListener { next() }
            m.prepare()
            val sid = m.audioSessionId
            EffectEngine.attach(sid, AppRepo.state.value)
            m.start()
            mp = m
            _ui.value = _ui.value.copy(
                index = i, playing = true, positionMs = 0, durationMs = m.duration, sessionId = sid, error = null,
            )
        } catch (t: Throwable) {
            _ui.value = _ui.value.copy(index = i, playing = false, sessionId = 0, error = "Gagal memutar: ${t.message}")
        }
    }

    fun toggle() {
        val m = mp ?: return
        try {
            if (m.isPlaying) m.pause() else m.start()
            _ui.value = _ui.value.copy(playing = m.isPlaying)
        } catch (t: Throwable) { }
    }

    fun next() {
        val s = _ui.value
        if (s.tracks.isEmpty()) return
        play((s.index + 1) % s.tracks.size)
    }

    fun prev() {
        val s = _ui.value
        if (s.tracks.isEmpty()) return
        val pos = mp?.currentPosition ?: 0
        if (pos > 3000) { mp?.seekTo(0) } else play((s.index - 1 + s.tracks.size) % s.tracks.size)
    }

    fun seekTo(ms: Int) {
        try { mp?.seekTo(ms) } catch (t: Throwable) { }
    }

    fun tick() {
        val m = mp ?: return
        try {
            _ui.value = _ui.value.copy(positionMs = m.currentPosition, playing = m.isPlaying)
        } catch (t: Throwable) { }
    }

    private fun releasePlayer() {
        mp?.let {
            try { EffectEngine.detach(it.audioSessionId) } catch (t: Throwable) { }
            try { it.release() } catch (t: Throwable) { }
        }
        mp = null
    }

    fun release() {
        releasePlayer()
        _ui.value = _ui.value.copy(playing = false, sessionId = 0)
    }
}
