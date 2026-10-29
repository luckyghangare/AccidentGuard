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

        // If notification payload is present, Firebase displays it automatically in background.
        // In foreground, we handle it here.
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "AccidentGuard Alert", it.body ?: "")
            return // Avoid processing data payload as a second notification
        }

        // If only data payload is present (or if we want custom handling)
        if (remoteMessage.data.isNotEmpty()) {
            val title = remoteMessage.data["title"] ?: "AccidentGuard Emergency"
            val body = remoteMessage.data["body"] ?: "A crash has been detected."
            sendNotification(title, body)
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
