package com.accidentguard.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.navigation.Screen
import com.accidentguard.ui.theme.*
import com.accidentguard.ui.notification.NotificationHelper
import com.accidentguard.viewmodel.VehicleManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    tokenManager: TokenManager,
    vehicleViewModel: VehicleManagementViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember { NotificationHelper(context) }
    
    var userName by remember { mutableStateOf(tokenManager.getUserName()) }
    var userEmail by remember { mutableStateOf(tokenManager.getUserEmail()) }
    
    var pushEnabled by remember { mutableStateOf(true) }
    var crashAlerts by remember { mutableStateOf(true) }

    // Permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            notificationHelper.showNotification("Success", "Notifications enabled for Accident Guard")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", fontWeight = FontWeight.Bold) },
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
            // ── Profile Header ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).clip(CircleShape).background(PrimaryBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(52.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(userName.ifEmpty { "User" }, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(userEmail.ifEmpty { "—" }, fontSize = 14.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Security Section ──
            SettingsSectionHeader("Security & Privacy")
            SettingsCard {
                SettingsRow(
                    icon = Icons.Default.Security, 
                    title = "Security Dashboard", 
                    subtitle = "2FA, Biometrics, and Privacy", 
                    iconTint = SafeGreen
                ) {
                    navController.navigate(Screen.SecuritySettings.route)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Notifications Section ──
            SettingsSectionHeader("Alert Notifications")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Push Notifications",
                    subtitle = "Receive real-time app updates",
                    iconTint = PrimaryBlue,
                    checked = pushEnabled
                ) { 
                    pushEnabled = it
                    if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(
                    icon = Icons.Default.Warning,
                    title = "Crash Alerts",
                    subtitle = "High priority emergency notifications",
                    iconTint = DangerRed,
                    checked = crashAlerts
                ) { crashAlerts = it }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                
                // TEST NOTIFICATION BUTTON
                SettingsRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    title = "Send Test Notification",
                    subtitle = "Tap to see how alerts appear",
                    iconTint = PrimaryBlue
                ) {
                    notificationHelper.showNotification(
                        "Accident Guard", 
                        "Hello $userName! This is a test notification from your safety system."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Account & App ──
            SettingsSectionHeader("Application")
            SettingsCard {
                SettingsRow(Icons.Default.Dns, "Backend Server", "Configure connection URL", TextSecondary) {
                    // Dialog implementation here
                }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.Info, "About Accident Guard", "v1.2.0 Stable", TextSecondary) { }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Logout ──
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth().clickable { 
                    tokenManager.clear()
                    navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
                }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = DangerRed)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Logout from Device", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String, iconTint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
    }
}

@Composable
fun SettingsToggleRow(icon: ImageVector, title: String, subtitle: String, iconTint: Color, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue))
    }
}
