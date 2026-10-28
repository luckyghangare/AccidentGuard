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
import com.accidentguard.data.model.HospitalStats
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

    // Loading overlay for critical actions (dispatch/admit)
    val isActionLoading = uiState is HospitalUiState.ActionLoading

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
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Hospital Operations Center • ${viewModel.tokenManager.getHospitalCity()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadDashboard(isUserInitiated = true) }) {
                        if (uiState is HospitalUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showLogoutDialog = true }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlueDark)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
                
                // Dashboard Summary Header
                HospitalSummaryBanner(
                    stats = stats,
                    onManageFleet = { showFleetManageDialog = true }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "Active Emergency Incidents",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    if (alerts.isEmpty() && uiState !is HospitalUiState.Loading) {
                        item { EmptyIncidentsState() }
                    } else {
                        items(alerts, key = { it.id }) { alert ->
                            HospitalAlertCard(
                                alert = alert,
                                onCallVictim = { phone ->
                                    if (phone.isNotBlank() && phone != "N/A") {
                                        try {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Victim phone number unavailable", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenMap = { lat, lng ->
                                    if (lat != 0.0 && lng != 0.0) {
                                        try {
                                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Accident+Location)")
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Cannot open maps app", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Accident location unknown", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDirectDispatch = { viewModel.directDispatchAmbulance(alert.id) },
                                onCustomDispatch = { showCustomDispatchDialog = alert },
                                onAdmit = { viewModel.admitPatient(alert.id) }
                            )
                        }
                    }
                }
            }

            // Action Loading Overlay
            if (isActionLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(12.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 4.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Processing Action...", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Updating operations center...", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }

            // ── Custom Dispatch Ambulance Dialog ──
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
                FleetManageDialog(
                    stats = stats,
                    onDismiss = { showFleetManageDialog = false },
                    onUpdate = { total, avail ->
                        viewModel.updateFleet(total = total, available = avail)
                        showFleetManageDialog = false
                    }
                )
            }

            // ── Logout Confirmation Dialog ──
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Operations Logout") },
                    text = { Text("Are you sure you want to disconnect this hospital session? You will stop receiving live alerts.") },
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
                            Text("Disconnect")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text("Stay Connected")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HospitalSummaryBanner(
    stats: HospitalStats?,
    onManageFleet: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLEET STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${stats?.availableAmbulances ?: 0} / ${stats?.totalAmbulances ?: 0} Units Ready",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
                IconButton(
                    onClick = onManageFleet,
                    modifier = Modifier.background(PrimaryBlue.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Manage Fleet", tint = PrimaryBlue)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryChip(
                    label = "Pending", 
                    count = stats?.pendingEmergencies ?: 0, 
                    color = DangerRed,
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "En Route", 
                    count = stats?.activeDispatches ?: 0, 
                    color = WarningOrange,
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "Admitted", 
                    count = stats?.admittedPatients ?: 0, 
                    color = SafeGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SummaryChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f),
        modifier = modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$count $label", 
                color = color, 
                style = MaterialTheme.typography.labelSmall, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyIncidentsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(SafeGreen.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("No Active Incidents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("System status: Standby. All calls handled.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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

    val statusColor = when {
        isPending -> DangerRed
        isDispatched -> WarningOrange
        else -> SafeGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Status & Impact
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = when {
                            isPending -> "🚨 PENDING DISPATCH"
                            isDispatched -> "🚑 AMBULANCE EN ROUTE"
                            else -> "✅ PATIENT ADMITTED"
                        },
                        color = statusColor,
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

            Spacer(modifier = Modifier.height(12.dp))

            // Severity description
            Text(
                text = alert.damageSeverity,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPending) DangerRed else TextPrimary
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = DividerGrey.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Victim & Vehicle Details
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("👤 VICTIM INFO", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(alert.victim.userName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(alert.victim.userPhone, fontSize = 12.sp, color = PrimaryBlue, modifier = Modifier.clickable { onCallVictim(alert.victim.userPhone) })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🚗 VEHICLE", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(alert.victim.vehicleName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(alert.victim.vehicleNumber, fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Time and Coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${alert.dateStr}, ${alert.timeStr}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format("%.4f", alert.latitude)}, ${String.format("%.4f", alert.longitude)}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            // Dispatched ambulance info
            if (isDispatched && !alert.ambulanceNumber.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarningOrange.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🚑", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Unit: ${alert.ambulanceNumber} • ETA: ${alert.etaMinutes ?: 8}m",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = WarningOrange
                            )
                            Text(
                                "Driver: ${alert.driverName ?: "Emergency Unit"} (${alert.driverPhone ?: "108"})",
                                fontSize = 11.sp,
                                color = WarningOrange.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onCallVictim(alert.victim.userPhone) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call Victim", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { onOpenMap(alert.latitude, alert.longitude) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Live Map", fontSize = 12.sp)
                }
            }

            if (isPending || isDispatched) {
                Spacer(modifier = Modifier.height(12.dp))
                
                if (isPending) {
                    Button(
                        onClick = onDirectDispatch,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("DIRECT DISPATCH NOW", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    TextButton(
                        onClick = onCustomDispatch,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Assign Specific Unit (Advanced)", fontSize = 12.sp, color = PrimaryBlue)
                    }
                } else {
                    Button(
                        onClick = onAdmit,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("MARK PATIENT ADMITTED", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = DangerRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Custom Unit Dispatch", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Assigning for victim: ${alert.victim.userName}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = ambulanceNo,
                    onValueChange = { ambulanceNo = it },
                    label = { Text("Ambulance Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = { Text("Driver Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = driverPhone,
                    onValueChange = { driverPhone = it },
                    label = { Text("Driver Contact") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = etaMinutes,
                    onValueChange = { etaMinutes = it },
                    label = { Text("ETA (Minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
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

@Composable
private fun FleetManageDialog(
    stats: HospitalStats?,
    onDismiss: () -> Unit,
    onUpdate: (Int, Int) -> Unit
) {
    var editTotal by remember { mutableStateOf(stats?.totalAmbulances?.toString() ?: "5") }
    var editAvail by remember { mutableStateOf(stats?.availableAmbulances?.toString() ?: "5") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Update Fleet Status", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Update your hospital's total and currently ready ambulance units.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = editTotal,
                    onValueChange = { editTotal = it },
                    label = { Text("Total Fleet Count") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = editAvail,
                    onValueChange = { editAvail = it },
                    label = { Text("Available Units Now") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val t = editTotal.toIntOrNull() ?: stats?.totalAmbulances ?: 5
                    val a = editAvail.toIntOrNull() ?: stats?.availableAmbulances ?: 5
                    onUpdate(t, a)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Update Fleet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
