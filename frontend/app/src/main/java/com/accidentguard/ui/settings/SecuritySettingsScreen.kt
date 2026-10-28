package com.accidentguard.ui.settings

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.security.BiometricHelper
import com.accidentguard.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    navController: NavController,
    tokenManager: TokenManager
) {
    val context = LocalContext.current
    val biometricHelper = remember { BiometricHelper(context) }
    
    var is2FAEnabled by remember { mutableStateOf(tokenManager.is2FAEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(tokenManager.isBiometricEnabled()) }
    
    val lastLogin = remember {
        val timestamp = tokenManager.getLastLoginTime()
        if (timestamp == 0L) "Unknown" 
        else SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ── Status Overview ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SafeGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Account Security: ", fontSize = 16.sp, color = TextPrimary)
                        Text(
                            if (is2FAEnabled && isBiometricEnabled) "Strong" else "Moderate",
                            fontWeight = FontWeight.Bold,
                            color = if (is2FAEnabled && isBiometricEnabled) SafeGreen else WarningOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Last Login: $lastLogin", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Authentication Methods ──
            SettingsSectionHeader("Authentication Methods")
            SettingsCard {
                SettingsToggleRow(
                    icon = Icons.Default.PhonelinkLock,
                    title = "Two-Factor Authentication",
                    subtitle = "Verify your identity with an OTP code",
                    iconTint = PrimaryBlue,
                    checked = is2FAEnabled
                ) { enabled ->
                    is2FAEnabled = enabled
                    tokenManager.set2FAEnabled(enabled)
                    Toast.makeText(context, "2FA ${if (enabled) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                }
                
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                
                SettingsToggleRow(
                    icon = Icons.Default.Fingerprint,
                    title = "Fingerprint Login",
                    subtitle = "Use biometrics for faster access",
                    iconTint = SecondaryTeal,
                    checked = isBiometricEnabled,
                    enabled = biometricHelper.isBiometricAvailable()
                ) { enabled ->
                    if (enabled) {
                        // Verify current biometrics before enabling
                        biometricHelper.showBiometricPrompt(
                            activity = context as FragmentActivity,
                            title = "Confirm Fingerprint",
                            subtitle = "Please verify your biometric to enable this feature",
                            onSuccess = {
                                isBiometricEnabled = true
                                tokenManager.setBiometricEnabled(true)
                                Toast.makeText(context, "Fingerprint login enabled", Toast.LENGTH_SHORT).show()
                            },
                            onError = { _, err -> Toast.makeText(context, err.toString(), Toast.LENGTH_SHORT).show() },
                            onFailed = { }
                        )
                    } else {
                        isBiometricEnabled = false
                        tokenManager.setBiometricEnabled(false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Account Protection ──
            SettingsSectionHeader("Account Protection")
            SettingsCard {
                SettingsRow(Icons.Default.Password, "Change Password", "Update your account credential", PrimaryBlue) {
                    // Navigate to change password or show dialog
                }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.Devices, "Manage Trusted Devices", "View your active sessions", TextSecondary) {
                    Toast.makeText(context, "Cloud sync required for this feature", Toast.LENGTH_SHORT).show()
                }
                HorizontalDivider(color = DividerGrey, modifier = Modifier.padding(start = 56.dp))
                SettingsRow(Icons.Default.History, "Security Logs", "View recent login attempts", TextSecondary) {
                    Toast.makeText(context, "Cloud sync required for this feature", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            // ── Recovery ──
            SettingsSectionHeader("Recovery")
            SettingsCard {
                SettingsRow(Icons.Default.Backup, "Recovery Codes", "Generate backup codes for 2FA", WarningOrange) {
                    Toast.makeText(context, "Feature coming soon", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (enabled) iconTint else Color.Gray, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) TextPrimary else Color.Gray)
                Text(subtitle, fontSize = 12.sp, color = TextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = PrimaryBlue
            )
        )
    }
}
