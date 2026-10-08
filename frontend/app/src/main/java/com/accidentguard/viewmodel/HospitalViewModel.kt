package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.data.model.HospitalStats
import com.accidentguard.data.repository.HospitalRepository
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

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts

    private val _stats = MutableStateFlow<HospitalStats?>(null)
    val stats: StateFlow<HospitalStats?> = _stats

    private val _uiState = MutableStateFlow<HospitalUiState>(HospitalUiState.Idle)
    val uiState: StateFlow<HospitalUiState> = _uiState

    init {
        loadDashboard()
    }

    fun loadDashboard(isUserInitiated: Boolean = false) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading

            // Load Stats
            repo.getStats(token, tokenManager).onSuccess {
                _stats.value = it
            }

            // Load Alerts
            repo.getAlerts(token).onSuccess {
                _alerts.value = it
                _uiState.value = if (isUserInitiated) HospitalUiState.Success("Dashboard Refreshed") else HospitalUiState.Idle
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Failed to fetch emergency alerts")
            }
        }
    }

    fun directDispatchAmbulance(alertId: Int, customAmbNo: String? = null) {
        val token = tokenManager.getAuthToken() ?: return
        val currentAvail = _stats.value?.availableAmbulances ?: tokenManager.getHospitalAvailableAmbulances()
        val ambNo = customAmbNo?.ifBlank { null } ?: "AMB-${(alertId % 15) + 1}"

        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            val result = repo.dispatchAmbulance(
                token = token,
                alertId = alertId,
                ambulanceNumber = ambNo,
                driverName = "Emergency Trauma Team",
                driverPhone = tokenManager.getUserPhone().ifBlank { "108" },
                etaMinutes = 8,
                notes = "Direct single-tap emergency dispatch"
            )
            result.onSuccess {
                _uiState.value = HospitalUiState.Success("⚡ Ambulance $ambNo directly dispatched!")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Direct dispatch failed")
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
            val result = repo.dispatchAmbulance(
                token = token,
                alertId = alertId,
                ambulanceNumber = ambulanceNumber.ifBlank { "AMB-01" },
                driverName = driverName.ifBlank { "Emergency Unit" },
                driverPhone = driverPhone.ifBlank { "108" },
                etaMinutes = etaMinutes,
                notes = notes
            )
            result.onSuccess { updatedAlert ->
                _uiState.value = HospitalUiState.Success("Ambulance ${updatedAlert.ambulanceNumber} dispatched successfully!")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Ambulance dispatch failed")
            }
        }
    }

    fun admitPatient(alertId: Int, notes: String = "Patient admitted and stabilized") {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            val result = repo.admitPatient(token, alertId, notes)
            result.onSuccess {
                _uiState.value = HospitalUiState.Success("✅ Patient marked as Admitted/Treated. Fleet unit restored.")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Failed to admit patient")
            }
        }
    }

    fun updateFleet(total: Int? = null, available: Int? = null) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = HospitalUiState.Loading
            val result = repo.updateFleet(token, total, available)
            result.onSuccess { updatedHospital ->
                _uiState.value = HospitalUiState.Success("Ambulance fleet status updated: ${updatedHospital.availableAmbulances}/${updatedHospital.totalAmbulances} units ready")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Failed to update fleet status")
            }
        }
    }

    fun adjustAvailableFleet(delta: Int) {
        val currentTotal = _stats.value?.totalAmbulances ?: tokenManager.getHospitalTotalAmbulances()
        val currentAvail = _stats.value?.availableAmbulances ?: tokenManager.getHospitalAvailableAmbulances()
        val newAvail = (currentAvail + delta).coerceIn(0, currentTotal)
        if (newAvail != currentAvail) {
            updateFleet(available = newAvail)
        }
    }

    fun adjustTotalFleet(delta: Int) {
        val currentTotal = _stats.value?.totalAmbulances ?: tokenManager.getHospitalTotalAmbulances()
        val currentAvail = _stats.value?.availableAmbulances ?: tokenManager.getHospitalAvailableAmbulances()
        val newTotal = (currentTotal + delta).coerceAtLeast(1)
        val newAvail = currentAvail.coerceAtMost(newTotal)
        updateFleet(total = newTotal, available = newAvail)
    }

    fun resetState() {
        _uiState.value = HospitalUiState.Idle
    }

    fun logout() {
        tokenManager.clear()
    }
}
