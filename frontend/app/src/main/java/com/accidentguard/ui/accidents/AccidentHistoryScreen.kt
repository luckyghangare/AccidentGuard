package com.accidentguard.ui.accidents

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.accidentguard.data.model.Accident
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.AccidentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccidentHistoryScreen(
    navController: NavController,
    viewModel: AccidentViewModel
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event History", fontWeight = FontWeight.Bold) },
                actions = {
                    Text(
                        text = "Development Data",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
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
        ) {
            // Header
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Event History", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Review previous vehicle and system events.", fontSize = 14.sp, color = TextSecondary)

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TOTAL EVENTS", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${state.totalEventsCount}",
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Recorded safety events",
                                    fontSize = 14.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Events", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Development Data", fontSize = 12.sp, color = TextSecondary)
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(state.accidents) { accident ->
                        AccidentEventCard(accident = accident, onClick = {
                            viewModel.selectAccident(accident)
                        })
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        // Detail Bottom Sheet Dialog
        state.selectedAccident?.let { accident ->
            AlertDialog(
                onDismissRequest = { viewModel.selectAccident(null) },
                title = {
                    Text("Accident Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column {
                        DetailDialogRow("Date", accident.dateStr)
                        DetailDialogRow("Time", accident.timeStr)
                        DetailDialogRow("Location", "${accident.latitude}, ${accident.longitude}")
                        DetailDialogRow("Impact", "${accident.impactG} g")
                        DetailDialogRow("Status", formatStatus(accident.status))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.selectAccident(null) }) {
                        Text("Close", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun AccidentEventCard(accident: Accident, onClick: () -> Unit) {
    val (statusColor, statusLabel) = when (accident.status) {
        "emergency_sent" -> DangerRed to "Emergency Alert Sent"
        "cancelled" -> WarningOrange to "Cancelled"
        "false_alarm" -> SafeGreen to "False Alarm"
        else -> PrimaryBlue to "Normal"
    }

    val dotColor = when (accident.status) {
        "emergency_sent" -> DangerRed
        "cancelled" -> Color.Gray
        "false_alarm" -> SafeGreen
        else -> PrimaryBlue
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (accident.status) {
                            "emergency_sent" -> "Possible Accident"
                            "false_alarm" -> "System Check"
                            "cancelled" -> "Possible Accident"
                            else -> "System Warning"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(text = statusLabel, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${accident.dateStr} • ${accident.timeStr}",
                fontSize = 12.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 18.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = DividerGrey)
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Location", fontSize = 12.sp, color = TextSecondary)
                    Text("Available", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Impact Force", fontSize = 12.sp, color = TextSecondary)
                    Text("${accident.impactG} g", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DangerRed)
                }
            }
        }
    }
}

@Composable
fun DetailDialogRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 14.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatStatus(status: String): String = when (status) {
    "emergency_sent" -> "Emergency Alert Sent"
    "cancelled" -> "Alert Cancelled"
    "false_alarm" -> "False Alarm"
    else -> status.replaceFirstChar { it.uppercase() }
}
