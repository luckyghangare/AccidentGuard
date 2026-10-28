package com.accidentguard.ui.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.accidentguard.MainActivity
import com.accidentguard.R

class NotificationHelper(private val context: Context) {

    companion object {
        const val GENERAL_CHANNEL_ID = "general_alerts"
        const val SAFETY_CHANNEL_ID = "safety_alerts"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val generalChannel = NotificationChannel(
                GENERAL_CHANNEL_ID,
                "General Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Used for general app updates and info"
            }

            val safetyChannel = NotificationChannel(
                SAFETY_CHANNEL_ID,
                "Safety & Accident Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts regarding your safety and vehicle"
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(generalChannel)
            manager.createNotificationChannel(safetyChannel)
        }
    }

    fun showNotification(title: String, message: String, isCritical: Boolean = false) {
        val channelId = if (isCritical) SAFETY_CHANNEL_ID else GENERAL_CHANNEL_ID
        val priority = if (isCritical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System icon as placeholder
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: SecurityException) {
                // Permission not granted for Android 13+
            }
        }
    }
}
