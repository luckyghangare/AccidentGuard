package com.accidentguard.ui.login

import android.widget.Toast
import androidx.biometric.BiometricPrompt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.components.CustomOutlinedTextField
import com.accidentguard.ui.security.BiometricHelper
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.AuthUiState
import com.accidentguard.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: (role: String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateTo2FA: (email: String, role: String) -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val biometricHelper = remember { BiometricHelper(context) }
    
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var serverUrlInput by remember { mutableStateOf(tokenManager.getServerUrl()) }
    var savedServerUrl by remember { mutableStateOf(tokenManager.getServerUrl()) }
    var showServerConfigDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Success -> {
                val successState = uiState as AuthUiState.Success
                Toast.makeText(context, successState.message, Toast.LENGTH_SHORT).show()
                val role = successState.role
                viewModel.resetState()
                onLoginSuccess(role)
            }
            is AuthUiState.Requires2FA -> {
                val state = uiState as AuthUiState.Requires2FA
                onNavigateTo2FA(state.email, state.role)
                viewModel.resetState()
            }
            is AuthUiState.Error -> {
                Toast.makeText(context, (uiState as AuthUiState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    // Auto-trigger biometric if enabled
    LaunchedEffect(Unit) {
        if (tokenManager.isLoggedIn() && tokenManager.isBiometricEnabled() && biometricHelper.isBiometricAvailable()) {
            biometricHelper.showBiometricPrompt(
                activity = context as FragmentActivity,
                onSuccess = { onLoginSuccess(tokenManager.getUserRole()) },
                onError = { _, _ -> },
                onFailed = { }
            )
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
            Icon(Icons.Default.Settings, contentDescription = "Server Settings Dialog", tint = Color(0xFF444444))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(30.dp))

            // App Logo
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PrimaryBlue.copy(alpha = 0.12f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("🛡️", fontSize = 42.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "AccidentGuard",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Secure Safety Network",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Email Field
            CustomOutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email Address",
                leadingIcon = Icons.Default.Email,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Password Field
            CustomOutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Sign In Button
            Button(
                onClick = {
                    if (serverUrlInput.isNotBlank()) {
                        tokenManager.setServerUrl(serverUrlInput)
                    }
                    viewModel.login(email, password)
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = "Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (biometricHelper.isBiometricAvailable() && tokenManager.isBiometricEnabled()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        biometricHelper.showBiometricPrompt(
                            activity = context as FragmentActivity,
                            onSuccess = { onLoginSuccess(tokenManager.getUserRole()) },
                            onError = { _, err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() },
                            onFailed = { }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Login with Fingerprint")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Register Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Don't have an account? ",
                    fontSize = 14.sp,
                    color = Color(0xFF555555)
                )
                Text(
                    text = "Register Now",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Server IP Settings Dialog (Condensed)
        if (showServerConfigDialog) {
            AlertDialog(
                onDismissRequest = { showServerConfigDialog = false },
                title = { Text("Server URL", color = TextPrimary) },
                text = {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text("Backend URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        tokenManager.setServerUrl(serverUrlInput)
                        savedServerUrl = tokenManager.getServerUrl()
                        showServerConfigDialog = false
                    }) { Text("Save") }
                }
            )
        }
    }
}
