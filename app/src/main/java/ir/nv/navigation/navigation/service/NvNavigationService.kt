package ir.nv.navigation.navigation.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.nv.navigation.MainActivity
import ir.nv.navigation.R

class NvNavigationService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
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
            .setSmallIcon(R.drawable.ic_nv_navigation)
            .setContentTitle(if (destination.isBlank()) "NV Navigation" else "در مسیر $destination")
            .setContentText(remaining.ifBlank { "راهنمای مسیر فعال است" })
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent(this))
            .addAction(
                0,
                "پایان مسیر",
                PendingIntent.getActivity(
                    this,
                    1,
                    Intent(this, MainActivity::class.java).apply {
                        action = ACTION_STOP_NAVIGATION
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createChannels() {
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
        manager.createNotificationChannel(
            NotificationChannel(
                HAZARD_CHANNEL_ID,
                "هشدارهای هوشمند مسیر",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "هشدار آب‌وهوا و خطرهای مهم در مسیر NV"
                setShowBadge(false)
            }
        )
    }

    companion object {
        const val ACTION_STOP_NAVIGATION = "ir.nv.navigation.action.STOP_NAVIGATION"
        private const val ACTION_STOP_SERVICE = "ir.nv.navigation.action.STOP_NAVIGATION_SERVICE"
        private const val CHANNEL_ID = "nv_navigation"
        private const val HAZARD_CHANNEL_ID = "nv_route_hazards"
        private const val NOTIFICATION_ID = 101
        private const val HAZARD_NOTIFICATION_BASE = 3_100
        private const val EXTRA_DESTINATION = "destination"
        private const val EXTRA_REMAINING = "remaining"

        fun start(context: Context, destination: String?, remaining: String? = null) {
            val intent = Intent(context, NvNavigationService::class.java)
                .putExtra(EXTRA_DESTINATION, destination)
                .putExtra(EXTRA_REMAINING, remaining)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NvNavigationService::class.java).setAction(ACTION_STOP_SERVICE)
            )
        }

        fun notifyHazard(context: Context, title: String, detail: String, stableKey: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                if (manager.getNotificationChannel(HAZARD_CHANNEL_ID) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            HAZARD_CHANNEL_ID,
                            "هشدارهای هوشمند مسیر",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "هشدار آب‌وهوا و خطرهای مهم در مسیر NV"
                            setShowBadge(false)
                        }
                    )
                }
            }

            val notification = NotificationCompat.Builder(context, HAZARD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nv_navigation)
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(mainPendingIntent(context))
                .build()
            NotificationManagerCompat.from(context).notify(
                HAZARD_NOTIFICATION_BASE + (stableKey.hashCode() and 0x3ff),
                notification
            )
        }

        private fun mainPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
