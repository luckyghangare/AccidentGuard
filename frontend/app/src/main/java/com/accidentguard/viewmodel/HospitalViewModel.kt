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
    object ActionLoading : HospitalUiState() // Specifically for dispatch/admit/fleet updates
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
            if (isUserInitiated) {
                _uiState.value = HospitalUiState.Loading
            }

            // Fetch Stats and Alerts concurrently
            val statsResult = repo.getStats(token)
            val alertsResult = repo.getAlerts(token)

            if (statsResult.isSuccess) {
                val newStats = statsResult.getOrNull()
                _stats.value = newStats
                
                // Persist latest stats to TokenManager for offline/startup fallback
                newStats?.let {
                    tokenManager.saveHospitalFleet(it.totalAmbulances, it.availableAmbulances)
                }
            }

            if (alertsResult.isSuccess) {
                _alerts.value = alertsResult.getOrDefault(emptyList())
                _uiState.value = if (isUserInitiated) HospitalUiState.Success("Dashboard Refreshed") else HospitalUiState.Idle
            } else {
                val errorMsg = alertsResult.exceptionOrNull()?.message ?: "Failed to fetch emergency alerts"
                _uiState.value = HospitalUiState.Error(errorMsg)
            }
        }
    }

    fun directDispatchAmbulance(alertId: Int) {
        val token = tokenManager.getAuthToken() ?: return
        
        // Optimistic check
        val currentAvail = _stats.value?.availableAmbulances ?: tokenManager.getHospitalAvailableAmbulances()
        if (currentAvail <= 0) {
            _uiState.value = HospitalUiState.Error("No ambulances available for dispatch!")
            return
        }

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading
            val result = repo.dispatchAmbulance(token = token, alertId = alertId)
            
            result.onSuccess {
                _uiState.value = HospitalUiState.Success("Ambulance dispatched successfully!")
                loadDashboard()
            }.onFailure { error ->
                val errorMsg = error.message ?: "Dispatch failed"
                if (errorMsg.contains("available", ignoreCase = true) || errorMsg.contains("empty", ignoreCase = true)) {
                    _uiState.value = HospitalUiState.Error("Failed: All ambulances are currently busy.")
                } else {
                    _uiState.value = HospitalUiState.Error("System Error: $errorMsg")
                }
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
            _uiState.value = HospitalUiState.Error("Ambulance number is required")
            return
        }

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading
            val result = repo.dispatchAmbulance(
                token = token,
                alertId = alertId,
                ambulanceNumber = ambulanceNumber,
                driverName = driverName,
                driverPhone = driverPhone,
                etaMinutes = etaMinutes,
                notes = notes
            )
            result.onSuccess {
                _uiState.value = HospitalUiState.Success("Ambulance ${it.ambulanceNumber} dispatched!")
                loadDashboard()
            }.onFailure { error ->
                _uiState.value = HospitalUiState.Error("Assignment Failed: ${error.message ?: "Unknown error"}")
            }
        }
    }

    fun admitPatient(alertId: Int, notes: String = "Patient admitted and stabilized") {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading
            val result = repo.admitPatient(token, alertId, notes)
            result.onSuccess {
                _uiState.value = HospitalUiState.Success("Patient admitted successfully.")
                loadDashboard()
            }.onFailure {
                _uiState.value = HospitalUiState.Error(it.message ?: "Failed to admit patient")
            }
        }
    }

    fun updateFleet(total: Int? = null, available: Int? = null) {
        val token = tokenManager.getAuthToken() ?: return
        
        // Basic validation
        if (total != null && total < 1) {
            _uiState.value = HospitalUiState.Error("Total fleet must be at least 1")
            return
        }
        if (available != null && total != null && available > total) {
            _uiState.value = HospitalUiState.Error("Available cannot exceed total")
            return
        }

        viewModelScope.launch {
            _uiState.value = HospitalUiState.ActionLoading
            val result = repo.updateFleet(token, total, available)
            result.onSuccess {
                _uiState.value = HospitalUiState.Success("Fleet status updated.")
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
