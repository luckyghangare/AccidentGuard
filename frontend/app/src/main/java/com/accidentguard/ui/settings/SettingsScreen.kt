package com.accidentguard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.VehicleRequest
import com.accidentguard.ui.components.BottomNavigationBar
import com.accidentguard.ui.navigation.Screen
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.VehicleManagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    tokenManager: TokenManager,
    vehicleViewModel: VehicleManagementViewModel
) {
    // ── Live state from prefs ──
    var userName by remember { mutableStateOf(tokenManager.getUserName()) }
    var userEmail by remember { mutableStateOf(tokenManager.getUserEmail()) }
    var userPhone by remember { mutableStateOf(tokenManager.getUserPhone()) }

    val vehicleState by vehicleViewModel.uiState.collectAsState()

    // ── Settings state (persisted) ──
    var crashAlerts by remember { mutableStateOf(true) }
    var emergencyNotifications by remember { mutableStateOf(true) }
    var sensorWarnings by remember { mutableStateOf(true) }
    var darkMode by remember { mutableStateOf(tokenManager.isDarkMode()) }
    var selectedLanguage by remember { mutableStateOf(tokenManager.getLanguage()) }

    // ── Dialog toggles ──
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showServerIpDialog by remember { mutableStateOf(false) }
    var currentServerUrl by remember { mutableStateOf(tokenManager.getServerUrl()) }
    var serverIpInput by remember { mutableStateOf(tokenManager.getServerUrl().replace("http://", "").replace("/", "")) }


    // Edit profile temps
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }

    // Change password temps
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    // Add vehicle temps
    var vehicleName by remember { mutableStateOf("") }
    var vehicleNumber by remember { mutableStateOf("") }
    var vehicleDevice by remember { mutableStateOf("") }
    var vehicleError by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vehicleState.errorMessage) {
        vehicleState.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    val languages = listOf("English", "Hindi", "Marathi", "Gujarati", "Tamil")

    // Derived vehicle info for display
    val currentVehicle = vehicleState.vehicles.firstOrNull()
    val vehicleDisplayName = currentVehicle?.name ?: "No vehicle added"
    val vehicleDisplayNumber = currentVehicle?.vehicleNumber ?: "Tap to add"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = { BottomNavigationBar(navController = navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                    if (userPhone.isNotEmpty()) {
                        Text(userPhone, fontSize = 14.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Account Section ──
            SettingsSectionHeader("Account")
            SettingsCard {
                SettingsRow(Icons.Default.Person, "Edit Profile", "Update your name and phone", PrimaryBlue) {
                    editName = userName
                    editPhone = userPhone
                    showEditProfileDialog = true
                }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.Lock, "Change Password", "Update your password", PrimaryBlue) {
                    oldPassword = ""; newPassword = ""; confirmPassword = ""; passwordError = ""
                    showChangePasswordDialog = true
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Vehicle Section ──
            SettingsSectionHeader("Vehicle")
            SettingsCard {
                SettingsRow(Icons.Default.DirectionsCar, "Vehicle Name", vehicleDisplayName, SecondaryTeal) {
                    if (currentVehicle == null) {
                        vehicleName = ""; vehicleNumber = ""; vehicleDevice = ""; vehicleError = ""
                        showAddVehicleDialog = true
                    }
                }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.ConfirmationNumber, "Vehicle Number", vehicleDisplayNumber, SecondaryTeal) {
                    if (currentVehicle == null) {
                        vehicleName = ""; vehicleNumber = ""; vehicleDevice = ""; vehicleError = ""
                        showAddVehicleDialog = true
                    }
                }
                if (currentVehicle == null) {
                    Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Default.AddCircle, "Add Vehicle", "Register your vehicle", PrimaryBlue) {
                        vehicleName = ""; vehicleNumber = ""; vehicleDevice = ""; vehicleError = ""
                        showAddVehicleDialog = true
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Notifications Section ──
            SettingsSectionHeader("Notifications")
            SettingsCard {
                SettingsToggleRow(Icons.Default.Warning, "Crash Alerts", "Alert on impact detection", DangerRed, crashAlerts) { crashAlerts = it }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(Icons.Default.Notifications, "Emergency Notifications", "SMS to contacts", WarningOrange, emergencyNotifications) { emergencyNotifications = it }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(Icons.Default.Sensors, "Sensor Warnings", "Distance alerts", SafeGreen, sensorWarnings) { sensorWarnings = it }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Application Section ──
            SettingsSectionHeader("Application")
            SettingsCard {
                SettingsRow(Icons.Default.Settings, "Backend Server IP", currentServerUrl, PrimaryBlue) {
                    serverIpInput = tokenManager.getServerUrl().replace("http://", "").replace("/", "")
                    showServerIpDialog = true
                }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsToggleRow(Icons.Default.DarkMode, "Dark Mode", if (darkMode) "Dark theme enabled" else "Light theme active", TextSecondary, darkMode) {
                    darkMode = it
                    tokenManager.setDarkMode(it)
                }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.Language, "Language", selectedLanguage, TextSecondary) {
                    showLanguageDialog = true
                }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.Dns, "Backend Server IP", currentServerUrl, PrimaryBlue) {
                    serverIpInput = tokenManager.getServerUrl().replace("http://", "").replace("/", "")
                    showServerIpDialog = true
                }
                Divider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.Info, "About AccidentGuard", "v1.0.0", TextSecondary) {
                    showAboutDialog = true
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // ── Logout Card ──
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth().clickable { showLogoutDialog = true }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(DangerRedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, tint = DangerRed)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Logout", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = DangerRed.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── EDIT PROFILE DIALOG ──
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it },
                        label = { Text("Full Name") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = PrimaryBlue) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editPhone, onValueChange = { editPhone = it },
                        label = { Text("Phone Number") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = PrimaryBlue) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Email cannot be changed.", fontSize = 12.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            tokenManager.updateUserProfile(editName.trim(), editPhone.trim())
                            userName = editName.trim()
                            userPhone = editPhone.trim()
                            showEditProfileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Save Changes") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── CHANGE PASSWORD DIALOG ──
    if (showChangePasswordDialog) {
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (passwordError.isNotEmpty()) {
                        Text(passwordError, color = DangerRed, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("New Password") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            newPassword.length < 6 -> passwordError = "Password must be at least 6 characters"
                            newPassword != confirmPassword -> passwordError = "Passwords do not match"
                            else -> {
                                // TODO: call API to change password
                                showChangePasswordDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Change Password") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── ADD VEHICLE DIALOG ──
    if (showAddVehicleDialog) {
        AlertDialog(
            onDismissRequest = { showAddVehicleDialog = false },
            title = { Text("Add Your Vehicle", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (vehicleError.isNotEmpty()) {
                        Text(vehicleError, color = DangerRed, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = vehicleName, onValueChange = { vehicleName = it },
                        label = { Text("Vehicle Name (e.g. My Car)") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = SecondaryTeal) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vehicleNumber, onValueChange = { vehicleNumber = it },
                        label = { Text("Registration Number (e.g. MH-31-AB-1234)") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.ConfirmationNumber, null, tint = SecondaryTeal) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = vehicleDevice, onValueChange = { vehicleDevice = it },
                        label = { Text("Device ID (optional, e.g. ESP32_DEV_01)") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Memory, null, tint = SecondaryTeal) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            vehicleName.isBlank() -> vehicleError = "Vehicle name is required"
                            vehicleNumber.isBlank() -> vehicleError = "Vehicle number is required"
                            else -> {
                                vehicleViewModel.addVehicle(
                                    vehicleName.trim(),
                                    vehicleNumber.trim().uppercase(),
                                    vehicleDevice.trim().ifEmpty { null }
                                )
                                showAddVehicleDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Add Vehicle") }
            },
            dismissButton = {
                TextButton(onClick = { showAddVehicleDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── LANGUAGE DIALOG ──
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Language", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf("English", "Hindi", "Marathi", "Gujarati", "Tamil").forEach { lang ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedLanguage = lang
                                tokenManager.setLanguage(lang)
                                showLanguageDialog = false
                            }.padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLanguage == lang,
                                onClick = {
                                    selectedLanguage = lang
                                    tokenManager.setLanguage(lang)
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(lang, fontSize = 15.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Close", color = TextSecondary) }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── ABOUT DIALOG ──
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About AccidentGuard", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Version: 1.0.0", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("AccidentGuard is an intelligent vehicle safety monitoring system that detects accidents in real-time using ESP32 sensors and automatically notifies your emergency contacts.", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Built with FastAPI · Android Jetpack Compose · MySQL", fontSize = 12.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── SERVER IP CONFIGURATION DIALOG ──
    if (showServerIpDialog) {
        AlertDialog(
            onDismissRequest = { showServerIpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Backend Server IP", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Enter your PC's Wi-Fi IP address and port (from running 'ipconfig' in CMD):",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = serverIpInput,
                        onValueChange = { serverIpInput = it },
                        label = { Text("Server IP:Port") },
                        placeholder = { Text("e.g. https://accidentguard.onrender.com/") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current URL: ${tokenManager.getServerUrl()}",
                        fontSize = 11.sp,
                        color = PrimaryBlue
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = serverIpInput.trim()
                        if (input.isNotEmpty()) {
                            val formatted = if (!input.startsWith("http://") && !input.startsWith("https://")) {
                                "http://$input"
                            } else {
                                input
                            }
                            tokenManager.setServerUrl(formatted)
                            currentServerUrl = tokenManager.getServerUrl()
                        }
                        showServerIpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerIpDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // ── LOGOUT DIALOG ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out from AccidentGuard?") },
            confirmButton = {
                Button(
                    onClick = {
                        tokenManager.clear()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) { Text("Logout") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            shape = RoundedCornerShape(16.dp)
        )
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Switch(
            checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
        )
    }
}
