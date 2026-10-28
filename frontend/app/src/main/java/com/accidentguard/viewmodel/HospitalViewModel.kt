package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.data.model.Hospital
import com.accidentguard.data.model.HospitalStats
import com.accidentguard.data.repository.HospitalRepository
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HospitalUiState {
    object Idle : HospitalUiState()
    object Loading : HospitalUiState()
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
            _uiState.value = HospitalUiState.Loading

            // Load Alerts
            repo.getAlerts(token).onSuccess { newAlerts ->
                // Check if there are new alerts since last load to notify
                val currentAlerts = _alerts.value
                if (currentAlerts.isNotEmpty() && newAlerts.size > currentAlerts.size) {
                    val count = newAlerts.size - currentAlerts.size
                    notificationHelper.showNotification(
                        "🚨 NEW EMERGENCY", 
                        "You have $count new accident report(s) nearby!",
                        isCritical = true
                    )
                }
                _alerts.value = newAlerts
                _uiState.value = if (isUserInitiated) HospitalUiState.Success("Dashboard Refreshed") else HospitalUiState.Idle
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Failed to fetch alerts")
            }

            // Load Stats
            repo.getStats(token, tokenManager).onSuccess {
                _stats.value = it
            }
        }
    }

    fun directDispatchAmbulance(alertId: Int, customAmbNo: String? = null) {
        val token = tokenManager.getAuthToken() ?: return
        val ambNo = customAmbNo?.ifBlank { null } ?: "AMB-${(alertId % 15) + 1}"

        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            repo.dispatchAmbulance(
                token = token,
                alertId = alertId,
                ambulanceNumber = ambNo,
                driverName = "Trauma Team",
                driverPhone = "108",
                etaMinutes = 8,
                notes = "Urgent dispatch"
            ).onSuccess {
                notificationHelper.showNotification("Ambulance Dispatched", "Unit $ambNo is on the way to the accident site.")
                _uiState.value = HospitalUiState.Success("⚡ Dispatched!")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Dispatch failed")
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
        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            repo.dispatchAmbulance(
                token = token,
                alertId = alertId,
                ambulanceNumber = ambulanceNumber,
                driverName = driverName,
                driverPhone = driverPhone,
                etaMinutes = etaMinutes,
                notes = notes
            ).onSuccess {
                notificationHelper.showNotification("Ambulance Dispatched", "Unit $ambulanceNumber is on the way.")
                _uiState.value = HospitalUiState.Success("⚡ Dispatched!")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Dispatch failed")
            }
        }
    }

    fun adjustAvailableFleet(delta: Int) {
        val statsVal = _stats.value
        val currentTotal = statsVal?.totalAmbulances ?: tokenManager.getHospitalTotalAmbulances()
        val currentAvail = statsVal?.availableAmbulances ?: tokenManager.getHospitalAvailableAmbulances()
        val newAvail = (currentAvail + delta).coerceIn(0, currentTotal)
        updateFleet(total = currentTotal, available = newAvail)
    }

    fun updateFleet(total: Int, available: Int) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            repo.updateFleet(token, total, available).onSuccess { updatedHospital ->
                val currentStats = _stats.value
                if (currentStats != null) {
                    _stats.value = currentStats.copy(
                        totalAmbulances = updatedHospital.totalAmbulances,
                        availableAmbulances = updatedHospital.availableAmbulances
                    )
                }
                _uiState.value = HospitalUiState.Success("Fleet Updated")
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Fleet update failed")
            }
        }
    }

    fun admitPatient(alertId: Int) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            repo.admitPatient(token, alertId, "Stabilized").onSuccess {
                notificationHelper.showNotification("Patient Admitted", "Victim safely reached the hospital and is being treated.")
                _uiState.value = HospitalUiState.Success("✅ Patient Admitted")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Admission failed")
            }
        }
    }

    fun logout() {
        tokenManager.clear()
        notificationHelper.showNotification("Logged Out", "Hospital authority logged out.")
    }
}
