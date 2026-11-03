package com.accidentguard.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.accidentguard.MainActivity
import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.CallActionRequest
import com.accidentguard.ui.notification.NotificationHelper
import com.accidentguard.ui.notification.RingtonePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_ANSWER = "com.accidentguard.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.accidentguard.ACTION_DECLINE"
        const val ACTION_END_CALL = "com.accidentguard.ACTION_END_CALL"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_VICTIM_NAME = "victim_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val victimName = intent.getStringExtra(EXTRA_VICTIM_NAME) ?: "Someone"
        
        Log.d(TAG, "Received action: ${intent.action} for session: $sessionId")
        
        when (intent.action) {
            ACTION_ANSWER -> {
                RingtonePlayer.stopRinging()
                NotificationHelper(context).cancelCallNotification(sessionId)
                handleAnswer(context, sessionId, victimName)
            }
            ACTION_DECLINE -> {
                RingtonePlayer.stopRinging()
                NotificationHelper(context).cancelCallNotification(sessionId)
                handleDecline(context, sessionId)
            }
            ACTION_END_CALL -> {
                handleEndCall(context, sessionId)
            }
        }
    }

    private fun handleAnswer(context: Context, sessionId: Int, victimName: String) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("session_id", sessionId)
            putExtra("victim_name", victimName)
            putExtra("navigate_to", "incoming_call")
            putExtra("auto_accept", true)
        }
        
        // Android 12+ requires specific handling for full-screen intents and notification clicks
        // to ensure the activity is brought to front immediately.
        context.startActivity(launchIntent)
    }

    private fun handleDecline(context: Context, sessionId: Int) {
        val tokenManager = TokenManager(context)
        val jwtToken = tokenManager.getAuthToken()
        val pendingResult = goAsync()

        if (!jwtToken.isNullOrEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitClient.apiService.rejectCall(
                        authHeader = "Bearer $jwtToken",
                        sessionId = sessionId,
                        request = CallActionRequest(reason = "Declined from notification")
                    )
                    if (response.isSuccessful) {
                        Log.d(TAG, "Call declined successfully")
                    } else {
                        Log.e(TAG, "Failed to decline call: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error declining call", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            pendingResult.finish()
        }
    }

    private fun handleEndCall(context: Context, sessionId: Int) {
        val tokenManager = TokenManager(context)
        val jwtToken = tokenManager.getAuthToken()
        val pendingResult = goAsync()

        // 1. Tell the service to stop immediately if it's running
        val stopServiceIntent = Intent(context, CallService::class.java).apply {
            action = CallService.ACTION_STOP_SERVICE
        }
        context.startService(stopServiceIntent)

        // 2. Notify the backend asynchronously
        if (!jwtToken.isNullOrEmpty() && sessionId != -1) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.apiService.endCall("Bearer $jwtToken", sessionId)
                    Log.d(TAG, "Call ended reported to backend")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reporting end call to backend", e)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            pendingResult.finish()
        }
    }
}
