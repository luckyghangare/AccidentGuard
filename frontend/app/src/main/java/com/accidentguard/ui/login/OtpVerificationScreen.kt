package com.accidentguard.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accidentguard.ui.theme.*
import com.accidentguard.viewmodel.AuthUiState
import com.accidentguard.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    email: String,
    role: String,
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    var otp by remember { mutableStateOf("") }
    var timer by remember { mutableIntStateOf(60) }
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(Unit) {
        while (timer > 0) {
            delay(1000)
            timer--
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = PrimaryBlue.copy(alpha = 0.1f),
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Two-Factor Authentication",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "We've sent a verification code to",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Text(
                text = email,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(40.dp))

            // OTP Input
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                label = { Text("Enter 6-digit code") },
                placeholder = { Text("000000") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = BorderGrey
                ),
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    letterSpacing = 8.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.verifyOtp(email, otp, role) },
                enabled = otp.length >= 4 && !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Verify & Sign In", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (timer > 0) {
                Text(
                    text = "Resend code in ${timer}s",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            } else {
                TextButton(onClick = { 
                    timer = 60
                    viewModel.resendOtp(email)
                }) {
                    Text("Resend Verification Code", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Tip: Use 123456 for testing purpose",
                fontSize = 12.sp,
                color = Color.Gray,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}
