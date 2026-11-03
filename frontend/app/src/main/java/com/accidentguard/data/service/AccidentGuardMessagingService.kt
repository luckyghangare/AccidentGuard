package com.accidentguard.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accidentguard.MainActivity
import com.accidentguard.R
import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.FcmTokenRequest
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.accidentguard.ui.notification.NotificationHelper

class AccidentGuardMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_Service"
        const val CHANNEL_ID = "accident_alerts_channel"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val tokenManager = TokenManager(applicationContext)
        val jwtToken = tokenManager.getAuthToken()

        if (!jwtToken.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitClient.apiService.updateFcmToken(
                        authHeader = "Bearer $jwtToken",
                        request = FcmTokenRequest(fcmToken = token)
                    )
                    if (response.isSuccessful) {
                        Log.d(TAG, "FCM token registered successfully on server")
                    } else {
                        Log.e(TAG, "Failed to register FCM token: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering FCM token", e)
                }
            }
        } else {
            Log.d(TAG, "User not logged in; FCM token not sent to server yet.")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        val type = remoteMessage.data["type"]
        if (type != null) {
            handleCallNotification(type, remoteMessage.data)
            if (type.startsWith("EMERGENCY_CALL")) {
                return // Handled by call logic
            }
        }

        // If notification payload is present, Firebase displays it automatically in background.
        // In foreground, we handle it here.
        remoteMessage.notification?.let {
            val title = it.title ?: "AccidentGuard Alert"
            val body = it.body ?: ""

            // Check if we have structured data to enhance the body
            val victimName = remoteMessage.data["victim_name"]
            val location = remoteMessage.data["location"]

            val finalBody = if (victimName != null && location != null && !body.contains(victimName)) {
                "EMERGENCY: $victimName may have been in an accident near $location."
            } else {
                body
            }

            sendNotification(title, finalBody)
            return
        }

        // If only data payload is present (or if we want custom handling)
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "AccidentGuard Emergency"
            val body = remoteMessage.data["body"] ?: "A crash has been detected."
            sendNotification(title, body)
        }
    }

    private fun handleCallNotification(type: String, data: Map<String, String>) {
        val helper = NotificationHelper(applicationContext)
        val sessionIdStr = data["session_id"]
        val sessionId = sessionIdStr?.toIntOrNull()

        if (sessionId == null) {
            Log.e(TAG, "Call notification missing or invalid session_id: $sessionIdStr")
            return
        }

        val victimName = data["victim_name"] ?: "Someone"
        Log.d(TAG, "Handling call event: $type for session: $sessionId")

        when (type) {
            "EMERGENCY_CALL_INVITATION" -> {
                helper.showCallInvitation(sessionId, victimName)
            }
            "EMERGENCY_CALL_CANCELLED",
            "EMERGENCY_CALL_ALREADY_CONNECTED",
            "EMERGENCY_CALL_MISSED",
            "EMERGENCY_CALL_FAILED",
            "EMERGENCY_CALL_ACCEPTED" -> {
                com.accidentguard.ui.notification.RingtonePlayer.stopRinging()
                helper.cancelCallNotification(sessionId)

                val title = when (type) {
                    "EMERGENCY_CALL_CANCELLED" -> "Call Cancelled"
                    "EMERGENCY_CALL_ALREADY_CONNECTED" -> "Call Answered"
                    "EMERGENCY_CALL_MISSED" -> "Missed Emergency Call"
                    "EMERGENCY_CALL_FAILED" -> "Call Failed"
                    "EMERGENCY_CALL_ACCEPTED" -> "Call Accepted"
                    else -> "Emergency Call Update"
                }

                val body = when (type) {
                    "EMERGENCY_CALL_CANCELLED" -> "The emergency invitation for $victimName has been cancelled."
                    "EMERGENCY_CALL_ALREADY_CONNECTED" -> "Another contact has already joined the call for $victimName."
                    "EMERGENCY_CALL_MISSED" -> "You missed an emergency call from $victimName."
                    "EMERGENCY_CALL_FAILED" -> "Could not connect to the emergency call for $victimName."
                    "EMERGENCY_CALL_ACCEPTED" -> "The emergency call for $victimName was accepted."
                    else -> "The emergency call status has changed."
                }

                // Only show outcome notification for relevant events if not already connected
                if (type != "EMERGENCY_CALL_ACCEPTED") {
                    helper.showNotification(title, body, isCritical = false)
                }
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_emergency)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Accident Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for crash detection and emergency responses"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
