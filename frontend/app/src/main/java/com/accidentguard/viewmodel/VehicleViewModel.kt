package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.repository.SensorRepository
import com.accidentguard.data.repository.VehicleRepository
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VehicleMonitoringState(
    val obstacleDistanceCm: Float = 0.0f,
    val obstacleStatus: String = "SAFE", // SAFE, WARNING, DANGER, CRITICAL
    val vehicleSpeedKmh: Float = 0.0f,
    val accelerationG: Float = 1.0f,
    val gyroscopeRadS: Float = 0.0f,
    val mpuStatus: String = "Normal",
    val latitude: Double = 21.1458,
    val longitude: Double = 79.0882,
    val gpsStatus: String = "Not connected",
    val esp32Status: String = "Not connected",
    val lastUpdatedText: String = "Just now",
    val isLoading: Boolean = false
)

class VehicleViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val vehicleRepo = VehicleRepository()
    private val sensorRepo = SensorRepository()
    private val notificationHelper = NotificationHelper(application)

    private val _uiState = MutableStateFlow(VehicleMonitoringState())
    val uiState: StateFlow<VehicleMonitoringState> = _uiState

    init {
        startRealtimeMonitoring()
    }

    fun startRealtimeMonitoring() {
        viewModelScope.launch {
            while (true) { // Real-time loop
                val token = tokenManager.getAuthToken() ?: break

                // Get logged in user's vehicle
                val vehicleRes = vehicleRepo.getVehicles(token)
                val vehicle = vehicleRes.getOrNull()?.firstOrNull()

                if (vehicle != null) {
                    val result = sensorRepo.getLatestSensorReading(token, vehicle.id)
                    result.onSuccess { reading ->
                        val dist = reading.obstacleDistance
                        val statusText = when {
                            dist < 10 -> {
                                // CRITICAL ALERT NOTIFICATION
                                if (_uiState.value.obstacleStatus != "CRITICAL") {
                                    notificationHelper.showNotification(
                                        "🛑 CRITICAL WARNING",
                                        "Obstacle detected very close (${dist.toInt()}cm)! Brake now.",
                                        isCritical = true
                                    )
                                }
                                "CRITICAL"
                            }
                            dist <= 20 -> "DANGER"
                            dist <= 50 -> "WARNING"
                            else -> "SAFE"
                        }

                        // HIGH G-FORCE NOTIFICATION
                        if (reading.accelerationG > 4.0f && _uiState.value.mpuStatus != "High Impact") {
                            notificationHelper.showNotification(
                                "⚠️ High Impact Detected",
                                "Sudden deceleration of ${reading.accelerationG}G detected.",
                                isCritical = true
                            )
                        }

                        _uiState.value = _uiState.value.copy(
                            obstacleDistanceCm = dist,
                            obstacleStatus = statusText,
                            accelerationG = reading.accelerationG,
                            gyroscopeRadS = reading.gyroscopeRadS,
                            mpuStatus = if (reading.accelerationG > 3.5f) "High Impact" else "Normal",
                            gpsStatus = "Connected",
                            esp32Status = "Connected",
                            lastUpdatedText = "Live"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        gpsStatus = "No vehicle added",
                        esp32Status = "No vehicle added"
                    )
                }
                delay(2000) // Polling interval
            }
        }
    }
}
