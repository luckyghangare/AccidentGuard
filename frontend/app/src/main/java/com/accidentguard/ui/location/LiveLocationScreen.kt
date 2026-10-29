package com.accidentguard.ui.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.osmdroid.util.BoundingBox
import android.graphics.drawable.Drawable
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.LocationViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationScreen(
    navController: NavController,
    viewModel: LocationViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsState()

    // Initialize Osmdroid Configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember { MapView(context) }

    // Map Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    val currentGeoPoint = GeoPoint(state.latitude, state.longitude)

    // Helper to fit all markers
    fun fitAllMarkers() {
        val points = mutableListOf<GeoPoint>()
        points.add(currentGeoPoint)
        state.accidents.forEach { points.add(GeoPoint(it.latitude, it.longitude)) }
        state.hospitals.forEach { points.add(GeoPoint(it.latitude, it.longitude)) }
        state.emergencyAlerts.forEach { points.add(GeoPoint(it.latitude, it.longitude)) }

        if (points.size > 1) {
            val box = BoundingBox.fromGeoPoints(points)
            // Zoom to bounding box with padding
            mapView.zoomToBoundingBox(box, true, 150)
        } else {
            mapView.controller.animateTo(currentGeoPoint)
            mapView.controller.setZoom(17.5)
        }
    }

    // One-time initial auto-fit when data arrives
    var initialFitPerformed by remember { mutableStateOf(false) }
    LaunchedEffect(state.accidents, state.hospitals) {
        if (!initialFitPerformed && (state.accidents.isNotEmpty() || state.hospitals.isNotEmpty())) {
            fitAllMarkers()
            initialFitPerformed = true
        }
    }

    // Sync camera when location changes
    LaunchedEffect(state.latitude, state.longitude) {
        if (state.isTrackingActive && !state.isLoading) {
            mapView.controller.animateTo(currentGeoPoint)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Safety Map", fontWeight = FontWeight.Bold, color = PrimaryBlueDark) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    StatusBadge(status = state.gpsStatus)
                }
            )
        },
        bottomBar = { BottomNavigationBar(navController = navController) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = { fitAllMarkers() },
                    containerColor = Color.White,
                    contentColor = PrimaryBlue,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.ZoomOutMap, contentDescription = "Fit All Markers")
                }
                FloatingActionButton(
                    onClick = {
                        mapView.controller.animateTo(currentGeoPoint)
                        mapView.controller.setZoom(17.5)
                    },
                    containerColor = Color.White,
                    contentColor = PrimaryBlue,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = {
                    mapView.apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(currentGeoPoint)
                    }
                },
                update = { view ->
                    view.overlays.clear()

                    // User Location Marker
                    val userMarker = Marker(view).apply {
                        position = currentGeoPoint
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Your Location"
                        snippet = "Accident Guard Protection Active"
                        icon = ContextCompat.getDrawable(context, com.accidentguard.R.drawable.ic_user_location)
                    }
                    view.overlays.add(userMarker)

                    // Accident Markers (Red Triangle)
                    state.accidents.forEach { accident ->
                        val accidentMarker = Marker(view).apply {
                            position = GeoPoint(accident.latitude, accident.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Accident Reported"
                            snippet = "Impact: ${accident.impactG}G at ${accident.timeStr}"
                            icon = ContextCompat.getDrawable(context, com.accidentguard.R.drawable.ic_accident)
                        }
                        view.overlays.add(accidentMarker)
                    }

                    // Emergency Alert Markers (Red Circle Alert)
                    state.emergencyAlerts.forEach { alert ->
                        val alertMarker = Marker(view).apply {
                            position = GeoPoint(alert.latitude, alert.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "EMERGENCY: ${alert.status}"
                            snippet = "Victim: ${alert.victim.userName} (${alert.damageSeverity})"
                            icon = ContextCompat.getDrawable(context, com.accidentguard.R.drawable.ic_emergency)
                        }
                        view.overlays.add(alertMarker)
                    }

                    // Static Hospital Markers (Green Square)
                    state.hospitals.forEach { hospital ->
                        val hospMarker = Marker(view).apply {
                            position = GeoPoint(hospital.latitude, hospital.longitude)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = hospital.name
                            snippet = "${hospital.address}\nAmbulances: ${hospital.availableAmbulances}/${hospital.totalAmbulances}"
                            icon = ContextCompat.getDrawable(context, com.accidentguard.R.drawable.ic_hospital)
                        }
                        view.overlays.add(hospMarker)
                    }

                    view.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Location Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            LocationRow(label = "Latitude", value = String.format("%.6f°", state.latitude))
                            LocationRow(label = "Longitude", value = String.format("%.6f°", state.longitude))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Accuracy", fontSize = 12.sp, color = TextSecondary)
                            Text("${state.accuracy.toInt()} m", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = DividerGrey)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Updated: ${state.lastUpdatedText}", fontSize = 12.sp, color = TextSecondary)
                        }

                        Button(
                            onClick = { if (state.isTrackingActive) viewModel.stopTracking() else viewModel.startTracking() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isTrackingActive) DangerRed else SafeGreen
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (state.isTrackingActive) "Stop" else "Start", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Success/Error/Loading Feedback
            state.safetyErrorMessage?.let { msg ->
                Text(
                    text = "Error: $msg",
                    color = DangerRed,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(8.dp)
                )
            }

            if (state.isSafetyLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    color = PrimaryBlue
                )
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }

            // Diagnostic & Data Source Overlays
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                if (state.isUsingDemoData) {
                    Surface(
                        color = DangerRed.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "DEMO DATA ACTIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Diagnostic Info (Visible in Debug/Diagnostic mode)
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.width(160.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("MAP DIAGNOSTICS", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        DiagnosticRow("Accidents", state.accidentOverlayCount)
                        DiagnosticRow("Hospitals (API)", state.hospitalOverlayCount)
                        DiagnosticRow("Hospitals (OSM)", state.osmHospitalOverlayCount)
                        DiagnosticRow("Alerts", state.alertOverlayCount)
                        Text(
                            text = "Note: OSM tile symbols are not app-generated",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
        Text(count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LocationRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", fontSize = 14.sp, color = TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
fun StatusBadge(status: String) {
    val color = when {
        status.contains("High") -> SafeGreen
        status.contains("Locating") -> WarningOrange
        else -> TextSecondary
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(end = 16.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
