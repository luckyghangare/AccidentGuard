
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

        const val ACTION_ANSWER =
            "com.accidentguard.ACTION_ANSWER"

        const val ACTION_DECLINE =
            "com.accidentguard.ACTION_DECLINE"

        const val ACTION_END_CALL =
            "com.accidentguard.ACTION_END_CALL"

        const val EXTRA_SESSION_ID = "session_id"

        const val EXTRA_VICTIM_NAME = "victim_name"
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val appContext = context.applicationContext

        val sessionId = intent.getIntExtra(
            EXTRA_SESSION_ID,
            -1
        )

        val victimName = intent.getStringExtra(
            EXTRA_VICTIM_NAME
        ) ?: "Someone"

        Log.d(
            TAG,
            "Received action: ${intent.action}, session: $sessionId"
        )

        if (sessionId <= 0) {

            Log.e(
                TAG,
                "Invalid session ID: $sessionId"
            )

            return
        }

        when (intent.action) {

            ACTION_ANSWER -> {

                RingtonePlayer.stopRinging()

                NotificationHelper(appContext)
                    .cancelCallNotification(sessionId)

                handleAnswer(
                    appContext,
                    sessionId,
                    victimName
                )
            }

            ACTION_DECLINE -> {

                RingtonePlayer.stopRinging()

                NotificationHelper(appContext)
                    .cancelCallNotification(sessionId)

                handleDecline(
                    appContext,
                    sessionId
                )
            }

            ACTION_END_CALL -> {

                handleEndCall(
                    appContext,
                    sessionId
                )
            }

            else -> {

                Log.w(
                    TAG,
                    "Unknown action: ${intent.action}"
                )
            }
        }
    }

    // ---------------------------------------------------------
    // ANSWER CALL
    // ---------------------------------------------------------

    private fun handleAnswer(
        context: Context,
        sessionId: Int,
        victimName: String
    ) {

        val launchIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                "session_id",
                sessionId
            )

            putExtra(
                "victim_name",
                victimName
            )

            putExtra(
                "navigate_to",
                "incoming_call"
            )

            putExtra(
                "auto_accept",
                true
            )
        }

        try {

            context.startActivity(launchIntent)

            Log.d(
                TAG,
                "Opening incoming call screen for $sessionId"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to open incoming call screen",
                exception
            )
        }
    }

    // ---------------------------------------------------------
    // DECLINE CALL
    // ---------------------------------------------------------

    private fun handleDecline(
        context: Context,
        sessionId: Int
    ) {

        val tokenManager = TokenManager(context)

        val jwtToken = tokenManager.getAuthToken()

        if (jwtToken.isNullOrBlank()) {

            Log.e(
                TAG,
                "Cannot decline: authentication token missing"
            )

            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val response = RetrofitClient.apiService.rejectCall(
                    authHeader = "Bearer $jwtToken",
                    sessionId = sessionId,
                    request = CallActionRequest(
                        reason = "Declined from notification"
                    )
                )

                if (response.isSuccessful) {

                    Log.d(
                        TAG,
                        "Call declined successfully: $sessionId"
                    )

                } else {

                    Log.e(
                        TAG,
                        "Decline failed: HTTP ${response.code()}, session $sessionId"
                    )
                }

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error declining call: $sessionId",
                    exception
                )

            } finally {

                pendingResult.finish()
            }
        }
    }

    // ---------------------------------------------------------
    // END CALL
    // ---------------------------------------------------------

    private fun handleEndCall(
        context: Context,
        sessionId: Int
    ) {

        Log.d(
            TAG,
            "End call requested for session $sessionId"
        )

        // Stop local ringtone if one is active.
        RingtonePlayer.stopRinging()

        // Stop only the requested session.
        // IMPORTANT: Pass the session ID to CallService.
        try {

            CallService.stopService(
                context,
                sessionId
            )

            Log.d(
                TAG,
                "Local CallService stop requested for $sessionId"
            )

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Failed to request local service stop",
                exception
            )
        }

        // Cancel the active call notification.
        NotificationHelper(context)
            .cancelCallNotification(sessionId)

        // Report call termination to backend.
        val tokenManager = TokenManager(context)

        val jwtToken = tokenManager.getAuthToken()

        if (jwtToken.isNullOrBlank()) {

            Log.e(
                TAG,
                "Local call stopped, but backend update skipped: token missing"
            )

            return
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val response = RetrofitClient.apiService.endCall(
                    "Bearer $jwtToken",
                    sessionId
                )

                if (response.isSuccessful) {

                    Log.d(
                        TAG,
                        "Backend call termination successful: $sessionId"
                    )

                } else {

                    Log.e(
                        TAG,
                        "Backend end call failed: HTTP ${response.code()}, session $sessionId"
                    )
                }

            } catch (exception: Exception) {

                Log.e(
                    TAG,
                    "Error reporting call termination: $sessionId",
                    exception
                )

            } finally {

                pendingResult.finish()
            }
        }
    }
}
