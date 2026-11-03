package com.accidentguard.data.repository

import com.accidentguard.data.api.ApiService
import com.accidentguard.data.model.*
import kotlinx.coroutines.delay
import retrofit2.Response
import android.util.Log

class CallRepository(private val apiService: ApiService) {

    private val TAG = "CallRepository"

    suspend fun getActiveCalls(authHeader: String): Response<List<EmergencyCallSession>> {
        return apiService.getActiveCalls(authHeader)
    }

    /**
     * Fallback strategy: Poll for an active emergency session if the initial report didn't provide one.
     */
    suspend fun discoverActiveSession(
        authHeader: String,
        maxRetries: Int = 5,
        initialDelay: Long = 2000
    ): EmergencyCallSession? {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "Discovering active session, attempt ${attempt + 1}")
                val response = apiService.getActiveCalls(authHeader)
                if (response.isSuccessful) {
                    val sessions = response.body()
                    val activeSession = sessions?.firstOrNull { 
                        it.status == "ACTIVE" || it.status == "DISPATCHED" 
                    }
                    if (activeSession != null) {
                        Log.d(TAG, "Active session discovered: ${activeSession.id}")
                        return activeSession
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery attempt $attempt failed", e)
            }
            delay(currentDelay)
            currentDelay *= 2 // Exponential backoff
        }
        return null
    }

    suspend fun getCallDetails(authHeader: String, sessionId: Int): Response<EmergencyCallSession> {
        return apiService.getCallDetails(authHeader, sessionId)
    }

    suspend fun acceptCall(authHeader: String, sessionId: Int): Response<ApiResponse<CallAcceptResponse>> {
        return apiService.acceptCall(authHeader, sessionId)
    }

    suspend fun rejectCall(authHeader: String, sessionId: Int, reason: String? = null): Response<ApiResponse<Unit>> {
        return apiService.rejectCall(authHeader, sessionId, CallActionRequest(reason))
    }

    suspend fun getCallToken(authHeader: String, sessionId: Int): Response<CallTokenResponse> {
        return apiService.getCallToken(authHeader, sessionId)
    }

    suspend fun endCall(authHeader: String, sessionId: Int): Response<ApiResponse<Unit>> {
        return apiService.endCall(authHeader, sessionId)
    }
}
