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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class LiveLocationState(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speedKmh: Float = 0.0f,
    val gpsStatus: String = "Locating...",
    val lastUpdatedText: String = "Never",
    val isLoading: Boolean = true,
    val isTrackingActive: Boolean = false,
    val accuracy: Float = 0f,
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = false
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

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

        // Register receiver for location provider changes
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(gpsReceiver, filter)
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
