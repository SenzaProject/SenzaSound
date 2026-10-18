package com.senzaproject.senzasound.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.senzaproject.senzasound.R
import com.senzaproject.senzasound.data.AppRepo
import com.senzaproject.senzasound.dsp.EffectEngine
import com.senzaproject.senzasound.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Menjaga proses tetap hidup dan memasang efek global (sesi 0). */
class EffectService : Service() {
    companion object {
        const val ACTION_STOP = "com.senzaproject.senzasound.STOP"
        const val ACTION_TOGGLE = "com.senzaproject.senzasound.TOGGLE"
        private const val CHANNEL = "senza_fx"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppRepo.init(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Senza Sound", NotificationManager.IMPORTANCE_LOW),
        )
        scope.launch {
            AppRepo.state.collect {
                nm.notify(NOTIF_ID, buildNotification())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppRepo.update { it.copy(enabled = false) }
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> AppRepo.update { it.copy(enabled = !it.enabled) }
        }
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        } catch (t: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        EffectEngine.attach(0, AppRepo.state.value)
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val s = AppRepo.state.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, EffectService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, EffectService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Senza Sound " + if (s.enabled) "aktif" else "mati")
            .setContentText("Profil: ${s.profile.label}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (s.enabled) "Matikan" else "Hidupkan", toggle)
            .addAction(0, "Tutup", stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        EffectEngine.detach(0)
        super.onDestroy()
    }
}
