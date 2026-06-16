package com.shimul.meetingsummarizer.recording

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
import androidx.core.content.ContextCompat
import com.shimul.meetingsummarizer.MainActivity
import com.shimul.meetingsummarizer.R

/**
 * Foreground service that keeps a recording running while the app is backgrounded.
 *
 * It doesn't capture audio itself — the engine does — but having a running
 * foreground service of type "microphone" keeps the process alive and grants
 * background mic access, and shows the required ongoing notification.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val paused = intent?.getBooleanExtra(EXTRA_PAUSED, false) ?: false
        startAsForeground(paused)
        return START_NOT_STICKY
    }

    private fun startAsForeground(paused: Boolean) {
        createChannel()
        val notification = buildNotification(paused)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while a meeting is being recorded" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(paused: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (paused) "Recording paused" else "Recording meeting")
            .setContentText(
                if (paused) "Tap to resume in the app" else "Transcribing in the background"
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 1001
        private const val EXTRA_PAUSED = "paused"
        private const val ACTION_STOP = "com.shimul.meetingsummarizer.STOP_RECORDING"

        /** Start (or update) the foreground recording notification. */
        fun start(context: Context, paused: Boolean = false) {
            val intent = Intent(context, RecordingService::class.java)
                .putExtra(EXTRA_PAUSED, paused)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Update the notification to reflect paused/recording state. */
        fun update(context: Context, paused: Boolean) = start(context, paused)

        /** Stop the service and remove the notification. */
        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
