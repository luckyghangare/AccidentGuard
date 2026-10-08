package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.Vehicle
import com.accidentguard.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VehicleManagementState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class VehicleManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repo = VehicleRepository()

    private val _uiState = MutableStateFlow(VehicleManagementState())
    val uiState: StateFlow<VehicleManagementState> = _uiState

    init {
        fetchVehicles()
    }

    fun fetchVehicles() {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repo.getVehicles(token)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(vehicles = list, isLoading = false)
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false, vehicles = emptyList())
                }
        }
    }

    fun addVehicle(name: String, vehicleNumber: String, deviceId: String?) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.addVehicle(token, name, vehicleNumber, deviceId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(successMessage = "Vehicle added successfully!")
                    fetchVehicles()
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
