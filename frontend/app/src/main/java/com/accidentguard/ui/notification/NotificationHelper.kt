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
        const val CALL_CHANNEL_ID = "emergency_calls"
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

            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Emergency Voice Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming emergency voice call invitations"
                setSound(null, null) // We might want custom sound or high priority ringing later
                enableVibration(true)
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(generalChannel)
            manager.createNotificationChannel(safetyChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    fun showCallInvitation(sessionId: Int, victimName: String) {
        // Start ringtone
        RingtonePlayer.startRinging(context)

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("session_id", sessionId)
            putExtra("victim_name", victimName)
            putExtra("navigate_to", "incoming_call")
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, sessionId, fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerIntent = Intent(context, com.accidentguard.data.service.CallActionReceiver::class.java).apply {
            action = com.accidentguard.data.service.CallActionReceiver.ACTION_ANSWER
            putExtra(com.accidentguard.data.service.CallActionReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(com.accidentguard.data.service.CallActionReceiver.EXTRA_VICTIM_NAME, victimName)
        }
        val answerPendingIntent = PendingIntent.getBroadcast(
            context, sessionId + 1000, answerIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val declineIntent = Intent(context, com.accidentguard.data.service.CallActionReceiver::class.java).apply {
            action = com.accidentguard.data.service.CallActionReceiver.ACTION_DECLINE
            putExtra(com.accidentguard.data.service.CallActionReceiver.EXTRA_SESSION_ID, sessionId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context, sessionId + 2000, declineIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle("Emergency Call Invitation")
            .setContentText("Incoming call: $victimName needs help!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(false) // Don't auto-cancel on tap, handle via actions/receiver
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .addAction(R.drawable.ic_check, "Answer", answerPendingIntent)
            .addAction(R.drawable.ic_close, "Decline", declinePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(sessionId, builder.build())
            } catch (e: SecurityException) {
                // Handle missing permission
            }
        }
    }

    fun cancelCallNotification(sessionId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(sessionId)
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
