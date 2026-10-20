package com.senzaproject.senzasound.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.senzaproject.senzasound.data.AppRepo
import com.senzaproject.senzasound.dsp.EffectEngine

/** Menerima pengumuman sesi audio dari aplikasi pemutar musik dan video. */
class SessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppRepo.init(context)
        val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (session <= 0) return
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION ->
                EffectEngine.attach(session, AppRepo.state.value)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION ->
                EffectEngine.detach(session)
        }
    }
}
