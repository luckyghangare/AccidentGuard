package com.accidentguard.ui.register

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.components.CustomOutlinedTextField
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.AuthUiState
import com.accidentguard.viewmodel.AuthViewModel

enum class RegistrationRole {
    USER,
    HOSPITAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(tokenManager.getServerUrl()) }
    val uiState by viewModel.uiState.collectAsState()

    // Active Role selection: USER vs HOSPITAL
    var selectedRole by remember { mutableStateOf(RegistrationRole.USER) }

    // User Fields
    var fullName by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }
    var userPhone by remember { mutableStateOf("") }
    var userPassword by remember { mutableStateOf("") }
    var userConfirmPassword by remember { mutableStateOf("") }
    var userTermsAccepted by remember { mutableStateOf(false) }

    // Hospital Fields
    var hospitalName by remember { mutableStateOf("") }
    var hospitalEmail by remember { mutableStateOf("") }
    var hospitalPhone by remember { mutableStateOf("") }
    var hospitalCity by remember { mutableStateOf("Nagpur") }
    var hospitalAddress by remember { mutableStateOf("") }
    var hospitalAmbulances by remember { mutableStateOf("5") }
    var hospitalPassword by remember { mutableStateOf("") }
    var hospitalConfirmPassword by remember { mutableStateOf("") }
    var hospitalTermsAccepted by remember { mutableStateOf(false) }

    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                val msg = (uiState as AuthUiState.Success).message
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                viewModel.resetState()
                onRegisterSuccess()
            }
            is AuthUiState.Error -> {
                val errorMsg = (uiState as AuthUiState.Error).message
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        // Top Bar settings cog
        IconButton(
            onClick = {
                serverUrlInput = tokenManager.getServerUrl()
                showServerConfigDialog = true
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Server Settings", tint = Color(0xFF444444))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── App Header ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PrimaryBlue.copy(alpha = 0.12f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedRole == RegistrationRole.USER) "🛡️" else "🏥",
                        fontSize = 32.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Create Account",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Join AccidentGuard Emergency Response Network",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── ROLE SELECTOR TAB (User vs Hospital) ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8EEF5),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Option 1: Vehicle User
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedRole == RegistrationRole.USER) PrimaryBlue else Color.Transparent
                            )
                            .clickable { selectedRole = RegistrationRole.USER }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🚗", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Vehicle User",
                                fontWeight = if (selectedRole == RegistrationRole.USER) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedRole == RegistrationRole.USER) Color.White else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Option 2: Hospital
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedRole == RegistrationRole.HOSPITAL) Color(0xFF00695C) else Color.Transparent
                            )
                            .clickable { selectedRole = RegistrationRole.HOSPITAL }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🏥", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Hospital",
                                fontWeight = if (selectedRole == RegistrationRole.HOSPITAL) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedRole == RegistrationRole.HOSPITAL) Color.White else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Info Card explaining the active role ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (selectedRole == RegistrationRole.USER) Color(0xFFE3F2FD) else Color(0xFFE0F2F1)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedRole == RegistrationRole.USER) "🚗" else "🏥",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (selectedRole == RegistrationRole.USER)
                            "Vehicle User Portal: Connect your car, add emergency contacts, and get instant rescue assistance."
                        else
                            "Hospital Emergency Hub: Receive real-time crash alerts, view victim GPS coordinates, and dispatch ambulances.",
                        fontSize = 12.sp,
                        color = if (selectedRole == RegistrationRole.USER) PrimaryBlueDark else Color(0xFF004D40),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ════════════════════════════════════════════════════
            // ── FORM 1: VEHICLE USER REGISTRATION ──
            // ════════════════════════════════════════════════════
            if (selectedRole == RegistrationRole.USER) {
                CustomOutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "Full Name",
                    leadingIcon = Icons.Default.Person,
                    tintColor = PrimaryBlue
                )

                CustomOutlinedTextField(
                    value = userEmail,
                    onValueChange = { userEmail = it },
                    label = "Email Address",
                    leadingIcon = Icons.Default.Email,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    tintColor = PrimaryBlue
                )

                CustomOutlinedTextField(
                    value = userPhone,
                    onValueChange = { userPhone = it },
                    label = "Phone Number",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    tintColor = PrimaryBlue
                )

                CustomOutlinedTextField(
                    value = userPassword,
                    onValueChange = { userPassword = it },
                    label = "Password (Min. 6 chars)",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    tintColor = PrimaryBlue
                )

                CustomOutlinedTextField(
                    value = userConfirmPassword,
                    onValueChange = { userConfirmPassword = it },
                    label = "Confirm Password",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    tintColor = PrimaryBlue
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = userTermsAccepted,
                        onCheckedChange = { userTermsAccepted = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                    )
                    Text(
                        text = "I accept Terms & Conditions and Safety Policy",
                        fontSize = 12.sp,
                        color = Color(0xFF444444)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.registerUser(
                            fullName = fullName,
                            email = userEmail,
                            phone = userPhone,
                            pass = userPassword,
                            confirmPass = userConfirmPassword,
                            termsAccepted = userTermsAccepted
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text("Register as Vehicle User", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ════════════════════════════════════════════════════
            // ── FORM 2: HOSPITAL REGISTRATION ──
            // ════════════════════════════════════════════════════
            if (selectedRole == RegistrationRole.HOSPITAL) {
                val hospTint = Color(0xFF00695C)

                CustomOutlinedTextField(
                    value = hospitalName,
                    onValueChange = { hospitalName = it },
                    label = "Hospital / Trauma Center Name",
                    leadingIcon = Icons.Default.LocalHospital,
                    tintColor = hospTint
                )

                CustomOutlinedTextField(
                    value = hospitalEmail,
                    onValueChange = { hospitalEmail = it },
                    label = "Official Hospital Email",
                    leadingIcon = Icons.Default.Email,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    tintColor = hospTint
                )

                CustomOutlinedTextField(
                    value = hospitalPhone,
                    onValueChange = { hospitalPhone = it },
                    label = "Emergency Helpline Number",
                    leadingIcon = Icons.Default.Phone,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    tintColor = hospTint
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = hospitalCity,
                        onValueChange = { hospitalCity = it },
                        label = { Text("City") },
                        textStyle = TextStyle(color = Color(0xFF1A1A1A), fontSize = 15.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1A1A1A),
                            unfocusedTextColor = Color(0xFF1A1A1A),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = hospTint,
                            focusedBorderColor = hospTint,
                            unfocusedBorderColor = Color(0xFFBDBDBD),
                            focusedLabelColor = hospTint,
                            unfocusedLabelColor = Color(0xFF555555)
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = hospitalAmbulances,
                        onValueChange = { hospitalAmbulances = it },
                        label = { Text("Ambulances") },
                        textStyle = TextStyle(color = Color(0xFF1A1A1A), fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1A1A1A),
                            unfocusedTextColor = Color(0xFF1A1A1A),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            cursorColor = hospTint,
                            focusedBorderColor = hospTint,
                            unfocusedBorderColor = Color(0xFFBDBDBD),
                            focusedLabelColor = hospTint,
                            unfocusedLabelColor = Color(0xFF555555)
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                CustomOutlinedTextField(
                    value = hospitalAddress,
                    onValueChange = { hospitalAddress = it },
                    label = "Full Street Address",
                    leadingIcon = Icons.Default.LocationOn,
                    tintColor = hospTint
                )

                CustomOutlinedTextField(
                    value = hospitalPassword,
                    onValueChange = { hospitalPassword = it },
                    label = "Hospital Portal Password",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    tintColor = hospTint
                )

                CustomOutlinedTextField(
                    value = hospitalConfirmPassword,
                    onValueChange = { hospitalConfirmPassword = it },
                    label = "Confirm Password",
                    leadingIcon = Icons.Default.Lock,
                    isPassword = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    tintColor = hospTint
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hospitalTermsAccepted,
                        onCheckedChange = { hospitalTermsAccepted = it },
                        colors = CheckboxDefaults.colors(checkedColor = hospTint)
                    )
                    Text(
                        text = "I accept Emergency Dispatch Terms & Authority Agreement",
                        fontSize = 12.sp,
                        color = Color(0xFF444444)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val numAmb = hospitalAmbulances.toIntOrNull() ?: 5
                        viewModel.registerHospital(
                            name = hospitalName,
                            email = hospitalEmail,
                            phone = hospitalPhone,
                            address = hospitalAddress,
                            city = hospitalCity,
                            totalAmbulances = numAmb,
                            pass = hospitalPassword,
                            confirmPass = hospitalConfirmPassword,
                            termsAccepted = hospitalTermsAccepted
                        )
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = hospTint)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text("Register Hospital Emergency Unit", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Already have an account? Login ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already registered? ",
                    fontSize = 14.sp,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "Sign In",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Server IP Settings Dialog
        if (showServerConfigDialog) {
            AlertDialog(
                onDismissRequest = { showServerConfigDialog = false },
                title = { Text("Backend Server URL", color = TextPrimary) },
                text = {
                    Column {
                        Text(
                            "Enter the IP and port of your PC running start_backend.bat:",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("Server URL") },
                            placeholder = { Text("http://192.168.1.35:8000/") },
                            textStyle = TextStyle(color = Color(0xFF1A1A1A), fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1A1A1A),
                                unfocusedTextColor = Color(0xFF1A1A1A),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color(0xFFBDBDBD),
                                focusedLabelColor = PrimaryBlue,
                                unfocusedLabelColor = Color(0xFF555555)
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            tokenManager.setServerUrl(serverUrlInput)
                            showServerConfigDialog = false
                            Toast.makeText(context, "Server URL updated!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showServerConfigDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
