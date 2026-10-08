package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.repository.GpsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import com.accidentguard.data.repository.VehicleRepository

data class LiveLocationState(
    val vehicleName: String = "No vehicle added",
    val latitude: Double = 21.1458,
    val longitude: Double = 79.0882,
    val speedKmh: Float = 0.0f,
    val gpsStatus: String = "Not connected",
    val lastUpdatedText: String = "Just now",
    val isLoading: Boolean = false
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val vehicleRepo = VehicleRepository()
    private val gpsRepo = GpsRepository()

    private val _uiState = MutableStateFlow(LiveLocationState())
    val uiState: StateFlow<LiveLocationState> = _uiState

    init {
        fetchLiveLocation()
    }

    fun fetchLiveLocation() {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Get logged in user's vehicle first
            val vehicleRes = vehicleRepo.getVehicles(token)
            val vehicle = vehicleRes.getOrNull()?.firstOrNull()

            if (vehicle != null) {
                val result = gpsRepo.getLatestLocation(token, vehicle.id)
                result.onSuccess { loc ->
                    _uiState.value = _uiState.value.copy(
                        vehicleName = vehicle.name,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speedKmh = loc.speed,
                        gpsStatus = "GPS Active",
                        lastUpdatedText = "Live",
                        isLoading = false
                    )
                }.onFailure {
                    _uiState.value = _uiState.value.copy(
                        vehicleName = vehicle.name,
                        gpsStatus = "No GPS signal yet",
                        isLoading = false
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    vehicleName = "No vehicle registered",
                    gpsStatus = "Please add a vehicle",
                    speedKmh = 0.0f,
                    isLoading = false
                )
            }
        }
    }
}

