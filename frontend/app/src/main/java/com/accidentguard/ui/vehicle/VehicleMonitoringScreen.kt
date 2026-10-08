package com.accidentguard.ui.vehicle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.VehicleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleMonitoringScreen(
    navController: NavController,
    viewModel: VehicleViewModel
) {
    val state by viewModel.uiState.collectAsState()

    val (statusColor, statusBg) = when (state.obstacleStatus) {
        "CRITICAL" -> DangerRed to DangerRedBg
        "DANGER" -> WarningOrange to Color(0xFFFFF3E0)
        "WARNING" -> WarningOrange to Color(0xFFFFF8E1)
        else -> SafeGreen to Color(0xFFE8F5E9)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Monitoring", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = { BottomNavigationBar(navController = navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. Obstacle Detection Hero Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(statusBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = statusColor, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Obstacle Detection", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Ultrasonic Sensor", fontSize = 12.sp, color = TextSecondary)
                            }
                        }

                        Surface(
                            color = statusBg,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = state.obstacleStatus,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text("Distance to Obstacle", fontSize = 12.sp, color = TextSecondary)
                            Text("${state.obstacleDistanceCm.toInt()} cm", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LinearProgressIndicator(
                        progress = (state.obstacleDistanceCm / 100f).coerceIn(0f, 1f),
                        color = statusColor,
                        trackColor = Color.LightGray.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Thresholds: >50cm (Safe) | 20-50cm (Warning) | 10-20cm (Danger) | <10cm (Critical)",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Speed & MPU6050 Motion Sensors Grid
            Row(modifier = Modifier.fillMaxWidth()) {
                // Speed Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Vehicle Speed", fontSize = 12.sp, color = TextSecondary)
                        Text("${state.vehicleSpeedKmh.toInt()} km/h", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // MPU6050 Motion Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = SecondaryTeal, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("MPU6050 Sensor", fontSize = 12.sp, color = TextSecondary)
                        Text("${state.accelerationG} g", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Gyro: ${state.gyroscopeRadS} rad/s", fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. GPS Coordinates Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GPS Location", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }

                        Surface(
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = state.gpsStatus,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Latitude", fontSize = 12.sp, color = TextSecondary)
                            Text("${state.latitude}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column {
                            Text("Longitude", fontSize = 12.sp, color = TextSecondary)
                            Text("${state.longitude}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Hardware Connection (ESP32) Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SafeGreen.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("ESP32 Hardware Unit", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Status: ${state.esp32Status} • Last update: ${state.lastUpdatedText}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
