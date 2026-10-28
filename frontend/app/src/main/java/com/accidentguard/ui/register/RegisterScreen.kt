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
import com.accidentguard.ui.theme.BackgroundLight
import com.accidentguard.ui.theme.PrimaryBlue
import com.accidentguard.ui.theme.PrimaryBlueDark
import com.accidentguard.ui.theme.TextPrimary
import com.accidentguard.ui.theme.TextSecondary
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
    val tokenManager = remember(context) { TokenManager(context) }
    
    val uiState by viewModel.uiState.collectAsState()

    var showServerConfigDialog by remember { mutableStateOf<Boolean>(false) }
    var serverUrlInput by remember { mutableStateOf<String>(tokenManager.getServerUrl()) }
    
    var selectedRole by remember { mutableStateOf<RegistrationRole>(RegistrationRole.USER) }

    // User Form Fields
    var fullName by remember { mutableStateOf<String>("") }
    var userEmail by remember { mutableStateOf<String>("") }
    var userPhone by remember { mutableStateOf<String>("") }
    var userPassword by remember { mutableStateOf<String>("") }
    var userConfirmPassword by remember { mutableStateOf<String>("") }
    var userTermsAccepted by remember { mutableStateOf<Boolean>(false) }

    // Hospital Form Fields
    var hospitalName by remember { mutableStateOf<String>("") }
    var hospitalEmail by remember { mutableStateOf<String>("") }
    var hospitalPhone by remember { mutableStateOf<String>("") }
    var hospitalCity by remember { mutableStateOf<String>("Nagpur") }
    var hospitalAddress by remember { mutableStateOf<String>("") }
    var hospitalAmbulances by remember { mutableStateOf<String>("5") }
    var hospitalPassword by remember { mutableStateOf<String>("") }
    var hospitalConfirmPassword by remember { mutableStateOf<String>("") }
    var hospitalTermsAccepted by remember { mutableStateOf<Boolean>(false) }

    val isLoading = uiState is AuthUiState.Loading

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AuthUiState.Success) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
            onRegisterSuccess()
        } else if (state is AuthUiState.Error) {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            viewModel.resetState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        IconButton(
            onClick = {
                serverUrlInput = tokenManager.getServerUrl()
                showServerConfigDialog = true
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.Gray)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

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
            Text(text = "Create Account", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "Join AccidentGuard Rescue Network", fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(20.dp))

            // Role Selector Tab
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8EEF5),
                shadowElevation = 1.dp
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedRole == RegistrationRole.USER) PrimaryBlue else Color.Transparent)
                            .clickable { selectedRole = RegistrationRole.USER }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Vehicle User",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedRole == RegistrationRole.USER) Color.White else TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedRole == RegistrationRole.HOSPITAL) Color(0xFF00695C) else Color.Transparent)
                            .clickable { selectedRole = RegistrationRole.HOSPITAL }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hospital",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedRole == RegistrationRole.HOSPITAL) Color.White else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedRole == RegistrationRole.USER) {
                // Vehicle User Form
                CustomOutlinedTextField(value = fullName, onValueChange = { input -> fullName = input }, label = "Full Name", leadingIcon = Icons.Default.Person, tintColor = PrimaryBlue)
                CustomOutlinedTextField(value = userEmail, onValueChange = { input -> userEmail = input }, label = "Email Address", leadingIcon = Icons.Default.Email, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), tintColor = PrimaryBlue)
                CustomOutlinedTextField(value = userPhone, onValueChange = { input -> userPhone = input }, label = "Phone Number", leadingIcon = Icons.Default.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), tintColor = PrimaryBlue)
                CustomOutlinedTextField(value = userPassword, onValueChange = { input -> userPassword = input }, label = "Password", leadingIcon = Icons.Default.Lock, isPassword = true, tintColor = PrimaryBlue)
                CustomOutlinedTextField(value = userConfirmPassword, onValueChange = { input -> userConfirmPassword = input }, label = "Confirm Password", leadingIcon = Icons.Default.Lock, isPassword = true, tintColor = PrimaryBlue)
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = userTermsAccepted, onCheckedChange = { checked -> userTermsAccepted = checked }, colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue))
                    Text(text = "I accept Terms & Conditions", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.registerUser(fullName, userEmail, userPhone, userPassword, userConfirmPassword, userTermsAccepted) },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("Register as User", fontWeight = FontWeight.Bold)
                }
            } else {
                // Hospital Form
                val hospTint = Color(0xFF00695C)
                CustomOutlinedTextField(value = hospitalName, onValueChange = { input -> hospitalName = input }, label = "Hospital Name", leadingIcon = Icons.Default.LocalHospital, tintColor = hospTint)
                CustomOutlinedTextField(value = hospitalEmail, onValueChange = { input -> hospitalEmail = input }, label = "Official Email", leadingIcon = Icons.Default.Email, tintColor = hospTint)
                CustomOutlinedTextField(value = hospitalPhone, onValueChange = { input -> hospitalPhone = input }, label = "Emergency Number", leadingIcon = Icons.Default.Phone, tintColor = hospTint)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hospitalCity,
                        onValueChange = { input -> hospitalCity = input },
                        label = { Text("City") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = hospitalAmbulances,
                        onValueChange = { input -> hospitalAmbulances = input },
                        label = { Text("Ambulances") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                
                CustomOutlinedTextField(value = hospitalAddress, onValueChange = { input -> hospitalAddress = input }, label = "Street Address", leadingIcon = Icons.Default.LocationOn, tintColor = hospTint)
                CustomOutlinedTextField(value = hospitalPassword, onValueChange = { input -> hospitalPassword = input }, label = "Password", leadingIcon = Icons.Default.Lock, isPassword = true, tintColor = hospTint)
                CustomOutlinedTextField(value = hospitalConfirmPassword, onValueChange = { input -> hospitalConfirmPassword = input }, label = "Confirm Password", leadingIcon = Icons.Default.Lock, isPassword = true, tintColor = hospTint)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = hospitalTermsAccepted, onCheckedChange = { checked -> hospitalTermsAccepted = checked }, colors = CheckboxDefaults.colors(checkedColor = hospTint))
                    Text(text = "I accept Dispatch Agreement", fontSize = 12.sp, color = TextSecondary)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        val ambNum = hospitalAmbulances.toIntOrNull() ?: 5
                        viewModel.registerHospital(hospitalName, hospitalEmail, hospitalPhone, hospitalAddress, hospitalCity, ambNum, hospitalPassword, hospitalConfirmPassword, hospitalTermsAccepted)
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = hospTint)
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    else Text("Register Hospital Unit", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onNavigateToLogin) {
                Text("Already registered? Sign In", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(30.dp))
        }

        // Server Settings Dialog
        if (showServerConfigDialog) {
            AlertDialog(
                onDismissRequest = { showServerConfigDialog = false },
                confirmButton = {
                    Button(onClick = { 
                        tokenManager.setServerUrl(serverUrlInput)
                        showServerConfigDialog = false 
                        Toast.makeText(context, "Server URL updated!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showServerConfigDialog = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Backend Server Settings") },
                text = {
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { input -> serverUrlInput = input },
                        label = { Text("Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            )
        }
    }
}
