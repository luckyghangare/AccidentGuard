package com.accidentguard.viewmodel

import android.Manifest
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.service.LocationService
import com.accidentguard.data.repository.AccidentRepository
import com.accidentguard.data.repository.HospitalRepository
import com.accidentguard.data.model.Accident
import com.accidentguard.data.model.Hospital
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.data.local.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class LiveLocationState(
    val latitude: Double = 21.1458, // Nagpur default
    val longitude: Double = 79.0882,
    val speedKmh: Float = 0.0f,
    val gpsStatus: String = "Locating...",
    val lastUpdatedText: String = "Never",
    val isLoading: Boolean = true,
    val isSafetyLoading: Boolean = false,
    val safetyErrorMessage: String? = null,
    val isTrackingActive: Boolean = false,
    val accuracy: Float = 0f,
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = false,
    val accidents: List<Accident> = emptyList(),
    val hospitals: List<Hospital> = emptyList(),
    val emergencyAlerts: List<EmergencyAlert> = emptyList(),
    val isUsingDemoData: Boolean = false,
    val accidentOverlayCount: Int = 0,
    val hospitalOverlayCount: Int = 0,
    val alertOverlayCount: Int = 0,
    val osmHospitalOverlayCount: Int = 0
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val tokenManager = TokenManager(context)
    private val accidentRepository = AccidentRepository()
    private val hospitalRepository = HospitalRepository()

    private val _uiState = MutableStateFlow(LiveLocationState())
    val uiState: StateFlow<LiveLocationState> = _uiState

    // Receiver to detect GPS toggle changes in real-time
    private val gpsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkRequirements()
            }
        }
    }

    init {
        checkRequirements()
        observeLocationUpdates()
        fetchSafetyData()

        // Register receiver for location provider changes
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(gpsReceiver, filter)
    }

    fun fetchSafetyData(force: Boolean = false) {
        val token = tokenManager.getAuthToken()
        android.util.Log.d("LocationViewModel", "fetchSafetyData: Starting fetch. Token available: ${token != null}")
        
        if (token == null) {
            android.util.Log.w("LocationViewModel", "fetchSafetyData: No authentication token found")
            _uiState.value = _uiState.value.copy(safetyErrorMessage = "Not authenticated")
            return
        }

        // Avoid redundant fetches unless forced
        if (!force && _uiState.value.isSafetyLoading) {
            android.util.Log.d("LocationViewModel", "fetchSafetyData: Already loading, skipping")
            return
        }

        val currentLat = _uiState.value.latitude
        val currentLon = _uiState.value.longitude

        android.util.Log.d("LocationViewModel", "fetchSafetyData: Fetching for Lat: $currentLat, Lon: $currentLon")
        _uiState.value = _uiState.value.copy(isSafetyLoading = true, safetyErrorMessage = null)

        viewModelScope.launch {
            try {
                // Fetch Accidents
                accidentRepository.getAccidentHistory(token).onSuccess { list ->
                    android.util.Log.d("LocationViewModel", "fetchSafetyData: Successfully fetched ${list.size} accidents")
                    _uiState.value = _uiState.value.copy(
                        accidents = list,
                        accidentOverlayCount = list.size,
                        isUsingDemoData = _uiState.value.isUsingDemoData || list.any { it.source == "demo" }
                    )
                }.onFailure { e ->
                    android.util.Log.e("LocationViewModel", "fetchSafetyData: Accident fetch failed: ${e.message}")
                    _uiState.value = _uiState.value.copy(safetyErrorMessage = "Accidents: ${e.message}")
                }

                // Fetch Nearby Hospitals
                hospitalRepository.getNearbyHospitals(token, currentLat, currentLon).onSuccess { list ->
                    android.util.Log.d("LocationViewModel", "fetchSafetyData: Successfully fetched ${list.size} hospitals")
                    val backendHosp = list.count { it.source == "backend" }
                    val osmHosp = list.count { it.source == "osm" }
                    val isDemo = list.any { it.source == "demo" }
                    
                    _uiState.value = _uiState.value.copy(
                        hospitals = list,
                        hospitalOverlayCount = backendHosp,
                        osmHospitalOverlayCount = osmHosp,
                        isUsingDemoData = _uiState.value.isUsingDemoData || isDemo
                    )
                }.onFailure { e ->
                    android.util.Log.e("LocationViewModel", "fetchSafetyData: Hospital fetch failed: ${e.message}")
                }

                // Fetch Hospital Alerts
                hospitalRepository.getAlerts(token).onSuccess { alerts ->
                    android.util.Log.d("LocationViewModel", "fetchSafetyData: Successfully fetched ${alerts.size} emergency alerts")
                    _uiState.value = _uiState.value.copy(
                        emergencyAlerts = alerts,
                        alertOverlayCount = alerts.size
                    )
                }.onFailure { e ->
                    android.util.Log.e("LocationViewModel", "fetchSafetyData: Alert fetch failed: ${e.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("LocationViewModel", "fetchSafetyData: Unexpected error: ${e.message}", e)
                _uiState.value = _uiState.value.copy(safetyErrorMessage = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isSafetyLoading = false)
            }
        }
    }

    fun checkRequirements() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val permissionGranted = hasFineLocation || hasCoarseLocation

        val gpsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (e: Exception) {
                false
            }
        }

        _uiState.value = _uiState.value.copy(
            hasPermission = permissionGranted,
            isGpsEnabled = gpsEnabled,
            isTrackingActive = LocationService.isTracking
        )

        // Auto-start tracking if requirements are met and not already tracking
        if (permissionGranted && gpsEnabled && !LocationService.isTracking) {
            startTracking()
        } else if ((!permissionGranted || !gpsEnabled) && LocationService.isTracking) {
            stopTracking()
        }
    }

    private fun observeLocationUpdates() {
        viewModelScope.launch {
            LocationService.locationFlow.collect { location ->
                updateStateWithLocation(location)
            }
        }
    }

    private fun updateStateWithLocation(location: Location) {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())

        val oldLat = _uiState.value.latitude
        val oldLon = _uiState.value.longitude

        _uiState.value = _uiState.value.copy(
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = location.speed * 3.6f,
            accuracy = location.accuracy,
            gpsStatus = when {
                location.accuracy < 15 -> "Live: High Accuracy"
                location.accuracy < 40 -> "Live: Medium Accuracy"
                else -> "Live: Low Accuracy"
            },
            lastUpdatedText = currentTime,
            isLoading = false
        )

        // Refresh safety data if we move more than 500 meters or if data hasn't been loaded
        val distance = FloatArray(1)
        Location.distanceBetween(oldLat, oldLon, location.latitude, location.longitude, distance)
        val shouldRefresh = distance[0] > 500 || (_uiState.value.accidents.isEmpty() && _uiState.value.hospitals.isEmpty())
        
        if (shouldRefresh) {
            android.util.Log.d("LocationViewModel", "Significant movement (${distance[0]}m) or empty state. Refreshing safety data.")
            fetchSafetyData()
        }
    }

    fun startTracking() {
        if (_uiState.value.hasPermission && _uiState.value.isGpsEnabled) {
            val intent = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _uiState.value = _uiState.value.copy(isTrackingActive = true, isLoading = true)
        }
    }

    fun stopTracking() {
        val intent = Intent(context, LocationService::class.java)
        context.stopService(intent)
        _uiState.value = _uiState.value.copy(isTrackingActive = false, gpsStatus = "Tracking Stopped", isLoading = false)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(gpsReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
}
