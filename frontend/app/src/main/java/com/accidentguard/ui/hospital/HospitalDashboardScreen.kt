package com.accidentguard.ui.hospital

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.NavController
import com.accidentguard.data.model.EmergencyAlert
import com.accidentguard.ui.navigation.Screen
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.HospitalUiState
import com.accidentguard.viewmodel.HospitalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HospitalDashboardScreen(
    navController: NavController,
    viewModel: HospitalViewModel
) {
    val context = LocalContext.current
    val alerts by viewModel.alerts.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showCustomDispatchDialog by remember { mutableStateOf<EmergencyAlert?>(null) }
    var showFleetManageDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is HospitalUiState.Success -> {
                Toast.makeText(context, (uiState as HospitalUiState.Success).message, Toast.LENGTH_SHORT).show()
                viewModel.resetState()
            }
            is HospitalUiState.Error -> {
                Toast.makeText(context, (uiState as HospitalUiState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.tokenManager.getUserName().ifBlank { "Hospital Emergency Care" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "📍 ${viewModel.tokenManager.getHospitalCity()} • Hospital Operations Hub",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard(isUserInitiated = true) }) {
                        if (uiState is HospitalUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlueDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState is HospitalUiState.Loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = SecondaryTeal,
                    trackColor = PrimaryBlueDark
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BackgroundLight),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Interactive Hospital Ambulance Fleet Status Banner ──
                item {
                    val availAmb = stats?.availableAmbulances ?: viewModel.tokenManager.getHospitalAvailableAmbulances()
                    val totalAmb = stats?.totalAmbulances ?: viewModel.tokenManager.getHospitalTotalAmbulances()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF004D40))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00796B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🚑", fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            "Ambulance Fleet Status",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "$availAmb / $totalAmb Units Ready",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Button(
                                    onClick = { showFleetManageDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Manage Fleet", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color.White.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Fleet Adjuster Controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Quick Ready Units Adjuster:",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Decrement button
                                    FilledIconButton(
                                        onClick = { viewModel.adjustAvailableFleet(-1) },
                                        enabled = availAmb > 0,
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFF00796B),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    ) {
                                        Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Text(
                                        text = "$availAmb Ready",
                                        color = Color(0xFF64FFDA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // Increment button
                                    FilledIconButton(
                                        onClick = { viewModel.adjustAvailableFleet(1) },
                                        enabled = availAmb < totalAmb,
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFF00796B),
                                            contentColor = Color.White,
                                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                                        )
                                    ) {
                                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── KPI Statistics Cards ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatBox(
                            title = "Pending",
                            count = stats?.pendingEmergencies?.toString() ?: "0",
                            color = DangerRed,
                            icon = "🚨",
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            title = "Dispatched",
                            count = stats?.activeDispatches?.toString() ?: "0",
                            color = WarningOrange,
                            icon = "🚑",
                            modifier = Modifier.weight(1f)
                        )
                        StatBox(
                            title = "Admitted",
                            count = stats?.admittedPatients?.toString() ?: "0",
                            color = SafeGreen,
                            icon = "✅",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ── Section Title ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "🚨 Emergency Crash Incidents",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            "${alerts.size} incidents",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }

                // ── Emergency Alerts List ──
                if (alerts.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("🛡️", fontSize = 42.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "No Active Emergency Alerts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "When a vehicle crash is detected, real-time victim details and GPS location will appear here instantly.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(alerts, key = { it.id }) { alert ->
                        HospitalAlertCard(
                            alert = alert,
                            onCallVictim = { phone ->
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            },
                            onOpenMap = { lat, lng ->
                                val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Crash+Location)")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            },
                            onDirectDispatch = {
                                viewModel.directDispatchAmbulance(alert.id)
                            },
                            onCustomDispatch = {
                                showCustomDispatchDialog = alert
                            },
                            onAdmit = {
                                viewModel.admitPatient(alert.id)
                            }
                        )
                    }
                }
            }

            // ── Custom Dispatch Ambulance Dialog (Optional Details) ──
            showCustomDispatchDialog?.let { alert ->
                CustomDispatchAmbulanceDialog(
                    alert = alert,
                    onDismiss = { showCustomDispatchDialog = null },
                    onConfirm = { ambNo, driverName, driverPhone, eta, notes ->
                        viewModel.dispatchAmbulance(
                            alertId = alert.id,
                            ambulanceNumber = ambNo,
                            driverName = driverName,
                            driverPhone = driverPhone,
                            etaMinutes = eta,
                            notes = notes
                        )
                        showCustomDispatchDialog = null
                    }
                )
            }

            // ── Fleet Management Dialog ──
            if (showFleetManageDialog) {
                val currentTotal = stats?.totalAmbulances ?: viewModel.tokenManager.getHospitalTotalAmbulances()
                val currentAvail = stats?.availableAmbulances ?: viewModel.tokenManager.getHospitalAvailableAmbulances()
                var editTotal by remember { mutableStateOf(currentTotal.toString()) }
                var editAvail by remember { mutableStateOf(currentAvail.toString()) }

                AlertDialog(
                    onDismissRequest = { showFleetManageDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚑", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Ambulance Fleet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Configure your hospital's total ambulance units and ready units count:",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )

                            OutlinedTextField(
                                value = editTotal,
                                onValueChange = { editTotal = it },
                                label = { Text("Total Ambulance Fleet Count") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = editAvail,
                                onValueChange = { editAvail = it },
                                label = { Text("Available / Ready Units Now") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val t = editTotal.toIntOrNull() ?: currentTotal
                                val a = editAvail.toIntOrNull() ?: currentAvail
                                viewModel.updateFleet(total = t, available = a)
                                showFleetManageDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                        ) {
                            Text("Save Fleet Status")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFleetManageDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // ── Logout Confirmation Dialog ──
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Logout Hospital Unit") },
                    text = { Text("Are you sure you want to disconnect this hospital session?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.logout()
                                showLogoutDialog = false
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                        ) {
                            Text("Logout")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    count: String,
    color: Color,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(count, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun HospitalAlertCard(
    alert: EmergencyAlert,
    onCallVictim: (String) -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onDirectDispatch: () -> Unit,
    onCustomDispatch: () -> Unit,
    onAdmit: () -> Unit
) {
    val isPending = alert.status.equals("PENDING", ignoreCase = true)
    val isDispatched = alert.status.equals("DISPATCHED", ignoreCase = true)
    val isAdmitted = alert.status.equals("ADMITTED", ignoreCase = true)

    val borderColor = when {
        isPending -> DangerRed
        isDispatched -> WarningOrange
        else -> SafeGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Status Badge & Severity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isPending -> DangerRedBg
                        isDispatched -> Color(0xFFFFF3E0)
                        else -> Color(0xFFE8F5E9)
                    }
                ) {
                    Text(
                        text = when {
                            isPending -> "🚨 PENDING DISPATCH"
                            isDispatched -> "🚑 AMBULANCE ON WAY"
                            else -> "✅ PATIENT ADMITTED"
                        },
                        color = borderColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF3E5F5)
                ) {
                    Text(
                        text = "⚡ ${alert.impactG}G Impact",
                        color = Color(0xFF7B1FA2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Severity description
            Text(
                text = alert.damageSeverity,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DangerRed
            )

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = DividerGrey)
            Spacer(modifier = Modifier.height(8.dp))

            // Driver & Vehicle Details
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("👤 DRIVER / VICTIM", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(alert.victim.userName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(alert.victim.userPhone, fontSize = 12.sp, color = PrimaryBlue)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🚗 VEHICLE DETAILS", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(alert.victim.vehicleName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(alert.victim.vehicleNumber, fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Incident Time and Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🕒 ${alert.dateStr}, ${alert.timeStr}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "📍 (${String.format("%.4f", alert.latitude)}, ${String.format("%.4f", alert.longitude)})",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            // Dispatched ambulance info if already dispatched
            if (isDispatched && !alert.ambulanceNumber.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF8E1),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚑", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Ambulance: ${alert.ambulanceNumber} • ETA: ${alert.etaMinutes ?: 8} mins",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFE65100)
                            )
                            Text(
                                "Driver: ${alert.driverName ?: "Emergency Response Unit"} (${alert.driverPhone ?: "108"})",
                                fontSize = 11.sp,
                                color = Color(0xFFBF360C)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons (Call & Map)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Call Button
                OutlinedButton(
                    onClick = { onCallVictim(alert.victim.userPhone) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call Victim", fontSize = 12.sp)
                }

                // Map Button
                OutlinedButton(
                    onClick = { onOpenMap(alert.latitude, alert.longitude) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Map", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── PRIMARY ACTION BUTTONS ──
            if (isPending) {
                // DIRECT DISPATCH BUTTON (Single Tap, No form required)
                Button(
                    onClick = onDirectDispatch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ DIRECT DISPATCH AMBULANCE", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Optional Custom Dispatch link
                Text(
                    text = "⚙️ Custom Dispatch Details (Optional)",
                    fontSize = 11.sp,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onCustomDispatch() }
                        .padding(vertical = 4.dp)
                )
            } else if (isDispatched) {
                // DIRECT MARK PATIENT ADMITTED BUTTON (Single Tap)
                Button(
                    onClick = onAdmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("✅ MARK PATIENT ADMITTED / TREATED", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            } else if (isAdmitted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Patient Admitted • Ambulance Returned to Fleet",
                            color = SafeGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomDispatchAmbulanceDialog(
    alert: EmergencyAlert,
    onDismiss: () -> Unit,
    onConfirm: (ambNo: String, driverName: String, driverPhone: String, eta: Int, notes: String) -> Unit
) {
    var ambulanceNo by remember { mutableStateOf("AMB-${(alert.id % 15) + 1}") }
    var driverName by remember { mutableStateOf("Rajesh Sharma") }
    var driverPhone by remember { mutableStateOf("+91 9822114477") }
    var etaMinutes by remember { mutableStateOf("8") }
    var notes by remember { mutableStateOf("Emergency trauma unit ready with oxygen support") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚑", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Ambulance Dispatch", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Assign custom unit for victim: ${alert.victim.userName} (${alert.victim.vehicleNumber})",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                val dialogFieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color(0xFF1A1A1A),
                    unfocusedTextColor = Color(0xFF1A1A1A),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = DangerRed,
                    unfocusedBorderColor = Color(0xFFBDBDBD),
                    focusedLabelColor = DangerRed,
                    unfocusedLabelColor = Color(0xFF555555)
                )

                OutlinedTextField(
                    value = ambulanceNo,
                    onValueChange = { ambulanceNo = it },
                    label = { Text("Ambulance Vehicle Number") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                    colors = dialogFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = { Text("Driver / Paramedic Name") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                    colors = dialogFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driverPhone,
                    onValueChange = { driverPhone = it },
                    label = { Text("Driver Contact Number") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                    colors = dialogFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = etaMinutes,
                    onValueChange = { etaMinutes = it },
                    label = { Text("Estimated Arrival Time (ETA mins)") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                    colors = dialogFieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Dispatch Notes / Instructions") },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                    colors = dialogFieldColors,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val eta = etaMinutes.toIntOrNull() ?: 8
                    onConfirm(ambulanceNo, driverName, driverPhone, eta, notes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text("Confirm Dispatch")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
