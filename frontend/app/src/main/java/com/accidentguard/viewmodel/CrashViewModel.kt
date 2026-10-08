package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.repository.AccidentRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CrashAlertState(
    val isCrashDetected: Boolean = false,
    val vehicleName: String = "Car 01",
    val crashTime: String = "10:32 PM",
    val locationStr: String = "21.1458, 79.0882",
    val impactG: Float = 4.8f,
    val countdownSeconds: Int = 10,
    val alertStatusMessage: String = "System monitoring vehicle",
    val isCancelled: Boolean = false,
    val isSent: Boolean = false
)

class CrashViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val accidentRepo = AccidentRepository()
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(CrashAlertState())
    val uiState: StateFlow<CrashAlertState> = _uiState

    fun triggerSimulatedCrash() {
        timerJob?.cancel()
        _uiState.value = CrashAlertState(
            isCrashDetected = true,
            vehicleName = "Car 01",
            crashTime = "10:32 PM",
            locationStr = "21.1458, 79.0882",
            impactG = 4.8f,
            countdownSeconds = 10,
            alertStatusMessage = "Emergency notification will be sent in 10 seconds.",
            isCancelled = false,
            isSent = false
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
                _uiState.value = _uiState.value.copy(
                    isSent = true,
                    alertStatusMessage = "Emergency notification sent."
                )
            }
        }
    }

    fun cancelEmergencyAlert() {
        timerJob?.cancel()
        val token = tokenManager.getAuthToken() ?: "demo_token"
        viewModelScope.launch {
            accidentRepo.cancelAlert(token, 1)
            _uiState.value = _uiState.value.copy(
                isCancelled = true,
                isCrashDetected = false,
                alertStatusMessage = "Emergency alert cancelled."
            )
        }
    }

    fun resetToNormal() {
        timerJob?.cancel()
        _uiState.value = CrashAlertState()
    }
}
