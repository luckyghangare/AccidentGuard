
package com.accidentguard.data.repository

import android.util.Log
import com.accidentguard.data.api.ApiService
import com.accidentguard.data.model.*
import kotlinx.coroutines.delay
import retrofit2.Response

class CallRepository(private val apiService: ApiService) {

    private val TAG = "CallRepository"

    // Backend active call statuses
    private val activeCallStatuses = setOf(
        "RINGING",
        "CONNECTING",
        "CONNECTED"
    )

    suspend fun getActiveCalls(
        authHeader: String
    ): Response<List<EmergencyCallSession>> {
        return apiService.getActiveCalls(authHeader)
    }

    /**
     * Discovers an active emergency call session.
     * Uses exponential backoff when no session is found.
     */
    suspend fun discoverActiveSession(
        authHeader: String,
        maxRetries: Int = 5,
        initialDelay: Long = 2000
    ): EmergencyCallSession? {

        var currentDelay = initialDelay

        repeat(maxRetries) { attempt ->

            try {
                Log.d(
                    TAG,
                    "Discovering active session, attempt ${attempt + 1}"
                )

                val response = apiService.getActiveCalls(authHeader)

                if (response.isSuccessful) {

                    val sessions = response.body()

                    val activeSession = sessions?.firstOrNull { session ->
                        session.status.uppercase() in activeCallStatuses
                    }

                    if (activeSession != null) {

                        Log.d(
                            TAG,
                            "Active session discovered: ${activeSession.id}, status: ${activeSession.status}"
                        )

                        return activeSession
                    }

                    Log.d(
                        TAG,
                        "No active session found in attempt ${attempt + 1}"
                    )

                } else {

                    Log.e(
                        TAG,
                        "Active calls API failed: HTTP ${response.code()}"
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Discovery attempt ${attempt + 1} failed",
                    e
                )
            }

            if (attempt < maxRetries - 1) {
                delay(currentDelay)
                currentDelay = (currentDelay * 2).coerceAtMost(30000)
            }
        }

        Log.w(TAG, "No active emergency session discovered")

        return null
    }

    suspend fun getCallDetails(
        authHeader: String,
        sessionId: Int
    ): Response<EmergencyCallSession> {
        return apiService.getCallDetails(authHeader, sessionId)
    }

    suspend fun acceptCall(
        authHeader: String,
        sessionId: Int
    ): Response<ApiResponse<CallAcceptResponse>> {
        return apiService.acceptCall(authHeader, sessionId)
    }

    suspend fun rejectCall(
        authHeader: String,
        sessionId: Int,
        reason: String? = null
    ): Response<ApiResponse<Unit>> {
        return apiService.rejectCall(
            authHeader,
            sessionId,
            CallActionRequest(reason)
        )
    }

    suspend fun getCallToken(
        authHeader: String,
        sessionId: Int
    ): Response<CallTokenResponse> {
        return apiService.getCallToken(authHeader, sessionId)
    }

    suspend fun endCall(
        authHeader: String,
        sessionId: Int
    ): Response<ApiResponse<Unit>> {
        return apiService.endCall(authHeader, sessionId)
    }
}
