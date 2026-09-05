package ir.nv.navigation.navigation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ir.nv.navigation.MainActivity
import ir.nv.navigation.R

class NvNavigationService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val destination = intent?.getStringExtra(EXTRA_DESTINATION).orEmpty()
        val remaining = intent?.getStringExtra(EXTRA_REMAINING).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification(destination, remaining))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(destination: String, remaining: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (destination.isBlank()) "NV Navigation" else "در مسیر $destination")
            .setContentText(remaining.ifBlank { "راهنمای مسیر فعال است" })
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                "پایان مسیر",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, NvNavigationService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "NV Navigation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "راهنمای فعال مسیر NV"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "nv_navigation"
        private const val NOTIFICATION_ID = 101
        private const val EXTRA_DESTINATION = "destination"
        private const val EXTRA_REMAINING = "remaining"
        private const val ACTION_STOP = "ir.nv.navigation.action.STOP_NAVIGATION"

        fun start(context: Context, destination: String?, remaining: String? = null) {
            val intent = Intent(context, NvNavigationService::class.java)
                .putExtra(EXTRA_DESTINATION, destination)
                .putExtra(EXTRA_REMAINING, remaining)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NvNavigationService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
