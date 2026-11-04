package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.repository.AccidentRepository
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.accidentguard.data.repository.VehicleRepository
import kotlinx.coroutines.flow.flatMapLatest

data class CrashAlertState(
    val isCrashDetected: Boolean = false,
    val vehicleName: String = "Car 01",
    val crashTime: String = "10:32 PM",
    val locationStr: String = "21.1458, 79.0882",
    val impactG: Float = 4.8f,
    val countdownSeconds: Int = 10,
    val alertStatusMessage: String = "System monitoring vehicle",
    val isCancelled: Boolean = false,
    val isSent: Boolean = false,
    val reportedAccidentId: Long? = null,
    val activeCallSessionId: Int? = null,
    val livekitToken: String? = null,
    val livekitUrl: String? = null
)

class CrashViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val vehicleRepo = VehicleRepository()
    private val accidentRepo = AccidentRepository()
    private val notificationHelper = NotificationHelper(application)
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(CrashAlertState())
    val uiState: StateFlow<CrashAlertState> = _uiState

    fun triggerSimulatedCrash() {
        timerJob?.cancel()
        _uiState.value = CrashAlertState(
            isCrashDetected = true,
            vehicleName = "Car 01",
            crashTime = "Just Now",
            locationStr = "Live GPS Location",
            impactG = 5.2f,
            countdownSeconds = 10,
            alertStatusMessage = "CRASH DETECTED! Alerting contacts in 10s...",
            isCancelled = false,
            isSent = false
        )

        // HIGH PRIORITY NOTIFICATION
        notificationHelper.showNotification(
            "⚠️ CRASH DETECTED!",
            "Impact detected on Car 01. Emergency alert will be sent in 10 seconds.",
            isCritical = true
        )

        timerJob = viewModelScope.launch {
            for (sec in 9 downTo 0) {
                delay(1000)
                if (_uiState.value.isCancelled) break
                _uiState.value = _uiState.value.copy(
                    countdownSeconds = sec,
                    alertStatusMessage = if (sec > 0) "Emergency notification will be sent in $sec seconds." else "Emergency notification sent."
                )
            }
            if (!_uiState.value.isCancelled) {
                val token = tokenManager.getAuthToken() ?: ""
                
                // Real backend reporting
                val activeVehicleRes = vehicleRepo.getVehicles(token)
                val activeVehicleId = activeVehicleRes.getOrNull()?.firstOrNull()?.id ?: 2L

                val reportRequest = com.accidentguard.data.model.AccidentCreateRequest(
                    vehicleId = activeVehicleId,
                    latitude = 21.1458,
                    longitude = 79.0882,
                    impactG = _uiState.value.impactG
                )
                
                val reportResult = accidentRepo.reportAccident(token, reportRequest)
                
                reportResult.onSuccess { accident ->
                    android.util.Log.d("CrashViewModel", "Accident reported successfully. ID: ${accident.id}, Session: ${accident.callSessionId}")
                    _uiState.value = _uiState.value.copy(
                        isSent = true,
                        alertStatusMessage = "Emergency notification sent.",
                        reportedAccidentId = accident.id,
                        activeCallSessionId = accident.callSessionId,
                        livekitToken = accident.livekitToken,
                        livekitUrl = accident.livekitUrl
                    )
                    
                    // If backend returned a token, start the call service immediately
                    if (accident.callSessionId != null && accident.livekitToken != null && accident.livekitUrl != null) {
                        android.util.Log.i("CrashViewModel", "Starting CallService immediately for victim join")
                        com.accidentguard.data.service.CallService.startService(
                            getApplication<Application>().applicationContext,
                            sessionId = accident.callSessionId,
                            token = accident.livekitToken,
                            url = accident.livekitUrl,
                            victimName = "Emergency Call (You)"
                        )
                    } else {
                        // Fallback to polling if token was not returned for some reason
                        android.util.Log.w("CrashViewModel", "Token not in report response, starting polling")
                        startCallPolling(token)
                    }
                }.onFailure {
                    android.util.Log.e("CrashViewModel", "Failed to report accident", it)
                    _uiState.value = _uiState.value.copy(
                        isSent = false,
                        alertStatusMessage = "Failed to report accident: ${it.message}"
                    )
                }
            }
        }
    }

    fun cancelEmergencyAlert() {
        timerJob?.cancel()
        val token = tokenManager.getAuthToken() ?: "demo_token"
        val accidentId = _uiState.value.reportedAccidentId
        
        viewModelScope.launch {
            if (accidentId != null) {
                accidentRepo.cancelAlert(token, accidentId)
            } else {
                // If ID is null, we are likely still in countdown, but we can call with 0 or skip
                // For now, let's just log and skip the backend call if not reported yet
                android.util.Log.d("CrashViewModel", "Alert cancelled before reporting.")
            }

            _uiState.value = _uiState.value.copy(
                isCancelled = true,
                isCrashDetected = false,
                alertStatusMessage = "Emergency alert cancelled."
            )
            // CANCEL NOTIFICATION
            notificationHelper.showNotification("Alert Cancelled", "Emergency SOS was cancelled by the user.")
        }
    }


    private val callRepo = com.accidentguard.data.repository.CallRepository(com.accidentguard.data.api.RetrofitClient.apiService)

    private fun startCallPolling(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(alertStatusMessage = "Waiting for emergency session...")
            
            val activeSession = callRepo.discoverActiveSession(
                authHeader = "Bearer $token",
                maxRetries = 10,
                initialDelay = 2000
            )

            if (activeSession != null && !_uiState.value.isCancelled) {
                android.util.Log.i("CrashViewModel", "Active session discovered via polling: ${activeSession.id}")
                _uiState.value = _uiState.value.copy(
                    activeCallSessionId = activeSession.id,
                    alertStatusMessage = "Emergency session active. Connecting..."
                )
                
                // Start CallService - it will handle token acquisition itself if needed
                com.accidentguard.data.service.CallService.startService(
                    getApplication<Application>().applicationContext,
                    sessionId = activeSession.id,
                    token = "", // Service will fetch fresh token
                    url = "",   // Service will fetch fresh URL
                    victimName = "Emergency Call (You)"
                )
            } else if (!_uiState.value.isCancelled) {
                android.util.Log.e("CrashViewModel", "Failed to discover active session after retries")
                _uiState.value = _uiState.value.copy(alertStatusMessage = "Could not establish emergency link.")
            }
        }
    }

    fun resetToNormal() {
        timerJob?.cancel()
        _uiState.value = CrashAlertState()
    }
}
