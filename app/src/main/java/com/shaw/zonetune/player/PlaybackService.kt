package com.shaw.zonetune.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shaw.zonetune.MainActivity
import com.shaw.zonetune.R
import com.shaw.zonetune.ZoneTuneApp

/**
 * Minimal foreground service so playback can continue in background.
 * MediaStyle controls will be enriched in a later phase.
 */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val track = ZoneTuneApp.instance.playerController.state.value.current
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track?.title ?: getString(R.string.app_name))
            .setContentText(track?.artist ?: "正在播放")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "听域播放",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "zonetune_playback"
        const val NOTIFICATION_ID = 1001
    }
}
