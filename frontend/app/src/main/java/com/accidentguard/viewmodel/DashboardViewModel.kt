package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.GpsLocation
import com.accidentguard.data.model.SensorReading
import com.accidentguard.data.model.Vehicle
import com.accidentguard.data.repository.GpsRepository
import com.accidentguard.data.repository.SensorRepository
import com.accidentguard.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val userName: String = "",
    val vehicleName: String = "No vehicle",
    val vehicleStatus: String = "Not connected",
    val speedKmh: Float = 0.0f,
    val obstacleDistanceCm: Float = 0.0f,
    val obstacleStatus: String = "UNKNOWN",
    val gpsStatus: String = "Not connected",
    val batteryPercent: Int = 0,
    val distanceTodayKm: Float = 0.0f,
    val drivingTimeMin: Int = 0,
    val maxSpeedKmh: Float = 0.0f,
    val suddenBrakes: Int = 0,
    val safetyStatusText: String = "Add a vehicle to start monitoring",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val vehicleRepo = VehicleRepository()
    private val gpsRepo = GpsRepository()
    private val sensorRepo = SensorRepository()

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        loadDashboardData()
    }

    fun refresh() {
        loadDashboardData()
    }

    fun loadDashboardData() {
        val token = tokenManager.getAuthToken() ?: return
        val userName = tokenManager.getUserName()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, userName = userName)

            // Fetch user's vehicles
            val vehicleRes = vehicleRepo.getVehicles(token)
            val vehicleList = vehicleRes.getOrNull() ?: emptyList()
            val vehicle = vehicleList.firstOrNull()

            if (vehicle != null) {
                // Fetch latest GPS for this user's specific vehicle
                val gpsRes = gpsRepo.getLatestLocation(token, vehicle.id)
                val gps = gpsRes.getOrNull()

                // Fetch latest Sensor reading for this user's specific vehicle
                val sensorRes = sensorRepo.getLatestSensorReading(token, vehicle.id)
                val sensor = sensorRes.getOrNull()

                val currentDist = sensor?.obstacleDistance ?: 68.0f
                val obstacleStatusText = when {
                    currentDist < 10 -> "CRITICAL"
                    currentDist <= 20 -> "DANGER"
                    currentDist <= 50 -> "WARNING"
                    else -> "SAFE"
                }

                _uiState.value = _uiState.value.copy(
                    userName = userName,
                    vehicleName = vehicle.name,
                    vehicleStatus = vehicle.status.replaceFirstChar { it.uppercase() },
                    speedKmh = gps?.speed ?: 0.0f,
                    obstacleDistanceCm = currentDist,
                    obstacleStatus = obstacleStatusText,
                    gpsStatus = "GPS Active",
                    batteryPercent = vehicle.batteryLevel,
                    safetyStatusText = when {
                        currentDist < 20 -> "Warning: Close obstacle detected"
                        else -> "Vehicle is currently safe"
                    },
                    isLoading = false
                )
            } else {
                // User has no vehicle registered yet
                _uiState.value = _uiState.value.copy(
                    userName = userName,
                    vehicleName = "No vehicle added",
                    vehicleStatus = "Not connected",
                    speedKmh = 0.0f,
                    obstacleDistanceCm = 0.0f,
                    obstacleStatus = "SAFE",
                    gpsStatus = "No vehicle registered",
                    batteryPercent = 0,
                    safetyStatusText = "Add a vehicle in Settings to start monitoring",
                    isLoading = false
                )
            }

        }
    }
}
