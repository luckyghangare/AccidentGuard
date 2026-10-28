
package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.data.model.HospitalStats
import com.accidentguard.data.repository.HospitalRepository
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HospitalUiState {
    object Idle : HospitalUiState()
    object Loading : HospitalUiState()
    object ActionLoading : HospitalUiState()
    data class Success(val message: String) : HospitalUiState()
    data class Error(val message: String) : HospitalUiState()
}

class HospitalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = HospitalRepository()
    val tokenManager = TokenManager(application)
    private val notificationHelper = NotificationHelper(application)

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts

    private val _stats = MutableStateFlow<HospitalStats?>(null)
    val stats: StateFlow<HospitalStats?> = _stats

    private val _uiState = MutableStateFlow<HospitalUiState>(HospitalUiState.Idle)
    val uiState: StateFlow<HospitalUiState> = _uiState

    init {
        loadDashboard()
    }

    fun resetState() {
        _uiState.value = HospitalUiState.Idle
    }

    fun loadDashboard(isUserInitiated: Boolean = false) {
        val token = tokenManager.getAuthToken() ?: return

        viewModelScope.launch {
            if (isUserInitiated) {
                _uiState.value = HospitalUiState.Loading
            }

            val previousAlerts = _alerts.value
            val statsResult = repo.getStats(token)
            val alertsResult = repo.getAlerts(token)

            statsResult.onSuccess { newStats ->
                _stats.value = newStats
                tokenManager.saveHospitalFleet(
                    newStats.totalAmbulances,
                    newStats.availableAmbulances
                )
            }

            alertsResult.onSuccess { newAlerts ->
                if (previousAlerts.isNotEmpty() &&
                    newAlerts.size > previousAlerts.size
                ) {
                    val count = newAlerts.size - previousAlerts.size
                    notificationHelper.showNotification(
                        "New Emergency",
                        "$count new accident report(s) received."
                    )
                }

                _alerts.value = newAlerts
                _uiState.value = if (isUserInitiated) {
                    HospitalUiState.Success("Dashboard Refreshed")
                } else {
                    HospitalUiState.Idle
                }
            }.onFailure { error ->
                _uiState.value = HospitalUiState.Error(
                    error.message ?: "Failed to fetch emergency alerts"
                )
            }

            if (statsResult.isFailure && alertsResult.isSuccess) {
                // Keep the successfully loaded alerts visible if stats fail.
                _uiState.value = if (isUserInitiated) {
                    HospitalUiState.Success("Dashboard Refreshed")
                } else {
                    HospitalUiState.Idle
                }
            }
        }
    }

    fun directDispatchAmbulance(alertId: Int) {
        val token = tokenManager.getAuthToken() ?: return

        val available = _stats.value?.availableAmbulances
            ?: tokenManager.getHospitalAvailableAmbulances()

        if (available <= 0) {
            _uiState.value = HospitalUiState.Error(
                "No ambulances available for dispatch!"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading

            repo.dispatchAmbulance(
                token = token,
                alertId = alertId
            ).onSuccess {
                notificationHelper.showNotification(
                    "Ambulance Dispatched",
                    "An ambulance has been assigned to the emergency."
                )
                _uiState.value = HospitalUiState.Success(
                    "Ambulance dispatched successfully!"
                )
                loadDashboard()
            }.onFailure { error ->
                val message = error.message ?: "Dispatch failed"
                _uiState.value = HospitalUiState.Error(
                    if (message.contains("available", ignoreCase = true) ||
                        message.contains("empty", ignoreCase = true)
                    ) {
                        "All ambulances are currently busy."
                    } else {
                        "Dispatch failed: $message"
                    }
                )
            }
        }
    }

    fun dispatchAmbulance(
        alertId: Int,
        ambulanceNumber: String,
        driverName: String,
        driverPhone: String,
        etaMinutes: Int,
        notes: String
    ) {
        val token = tokenManager.getAuthToken() ?: return

        if (ambulanceNumber.isBlank()) {
            _uiState.value = HospitalUiState.Error(
                "Ambulance number is required"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading

            repo.dispatchAmbulance(
                token = token,
                alertId = alertId,
                ambulanceNumber = ambulanceNumber,
                driverName = driverName,
                driverPhone = driverPhone,
                etaMinutes = etaMinutes,
                notes = notes
            ).onSuccess { alert ->
                notificationHelper.showNotification(
                    "Ambulance Dispatched",
                    "Unit $ambulanceNumber is on the way."
                )
                _uiState.value = HospitalUiState.Success(
                    "Ambulance ${alert.ambulanceNumber} dispatched!"
                )
                loadDashboard()
            }.onFailure { error ->
                _uiState.value = HospitalUiState.Error(
                    "Assignment failed: ${error.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun admitPatient(
        alertId: Int,
        notes: String = "Patient admitted and stabilized"
    ) {
        val token = tokenManager.getAuthToken() ?: return

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading

            repo.admitPatient(token, alertId, notes)
                .onSuccess {
                    notificationHelper.showNotification(
                        "Patient Admitted",
                        "The patient admission status was updated."
                    )
                    _uiState.value = HospitalUiState.Success(
                        "Patient admitted successfully."
                    )
                    loadDashboard()
                }
                .onFailure { error ->
                    _uiState.value = HospitalUiState.Error(
                        error.message ?: "Failed to admit patient"
                    )
                }
        }
    }

    fun updateFleet(
        total: Int? = null,
        available: Int? = null
    ) {
        val token = tokenManager.getAuthToken() ?: return

        val currentTotal = _stats.value?.totalAmbulances
            ?: tokenManager.getHospitalTotalAmbulances()
        val currentAvailable = _stats.value?.availableAmbulances
            ?: tokenManager.getHospitalAvailableAmbulances()

        val targetTotal = total ?: currentTotal
        val targetAvailable = available ?: currentAvailable

        if (targetTotal < 1) {
            _uiState.value = HospitalUiState.Error(
                "Total fleet must be at least 1"
            )
            return
        }

        if (targetAvailable < 0 || targetAvailable > targetTotal) {
            _uiState.value = HospitalUiState.Error(
                "Available ambulances must be between 0 and total fleet"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading

            repo.updateFleet(token, total, available)
                .onSuccess { updatedHospital ->
                    _stats.value = _stats.value?.copy(
                        totalAmbulances = updatedHospital.totalAmbulances,
                        availableAmbulances = updatedHospital.availableAmbulances
                    )

                    tokenManager.saveHospitalFleet(
                        updatedHospital.totalAmbulances,
                        updatedHospital.availableAmbulances
                    )

                    _uiState.value = HospitalUiState.Success(
                        "Fleet status updated."
                    )
                    loadDashboard()
                }
                .onFailure { error ->
                    _uiState.value = HospitalUiState.Error(
                        error.message ?: "Failed to update fleet status"
                    )
                }
        }
    }

    fun adjustAvailableFleet(delta: Int) {
        val total = _stats.value?.totalAmbulances
            ?: tokenManager.getHospitalTotalAmbulances()
        val available = _stats.value?.availableAmbulances
            ?: tokenManager.getHospitalAvailableAmbulances()

        val newAvailable = (available + delta).coerceIn(0, total)

        if (newAvailable != available) {
            updateFleet(total = total, available = newAvailable)
        }
    }

    fun logout() {
        tokenManager.clear()
        _alerts.value = emptyList()
        _stats.value = null
        _uiState.value = HospitalUiState.Idle
    }
}
