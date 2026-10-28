package com.accidentguard.ui.location

import android.Manifest
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.LocationViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationScreen(
    navController: NavController,
    viewModel: LocationViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    
    val currentPos = LatLng(state.latitude, state.longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentPos, 15f)
    }

    // Auto-recenter when tracking starts or location moves significantly
    LaunchedEffect(state.latitude, state.longitude) {
        if (state.isTrackingActive && !state.isLoading) {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLng(currentPos)
            )
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
            FloatingActionButton(
                onClick = { cameraPositionState.position = CameraPosition.fromLatLngZoom(currentPos, 17f) },
                containerColor = Color.White,
                contentColor = PrimaryBlue,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Recenter")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
                properties = MapProperties(isMyLocationEnabled = state.hasPermission && state.isGpsEnabled)
            ) {
                if (!state.isLoading) {
                    Marker(
                        state = MarkerState(position = currentPos),
                        title = "Current Position",
                        snippet = "Accident Guard Protection Active"
                    )
                }
            }

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

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }
        }
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
