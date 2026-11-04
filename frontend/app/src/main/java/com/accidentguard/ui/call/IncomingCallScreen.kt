package com.accidentguard.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import com.accidentguard.ui.theme.ErrorRed
import com.accidentguard.ui.theme.SuccessGreen

@Composable
fun IncomingCallScreen(
    sessionId: Int,
    autoAccept: Boolean = false,
    victimName: String? = null,
    viewModel: CallViewModel,
    onNavigateToActiveCall: (Int, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val intent = (context as? Activity)?.intent
    val displayVictimName = victimName ?: intent?.getStringExtra("victim_name") ?: "Someone"

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) {
        viewModel.loadCallDetails(sessionId)
    }

    LaunchedEffect(uiState, autoAccept) {
        if (autoAccept && uiState is CallUiState.SessionLoaded) {
            viewModel.acceptCall(context, sessionId, displayVictimName)
        }

        if (uiState is CallUiState.Accepted) {
            val accepted = uiState as CallUiState.Accepted
            if (accepted.response.session.id == sessionId) {
                onNavigateToActiveCall(
                    accepted.response.session.id,
                    accepted.response.token,
                    accepted.response.livekitUrl
                )
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Emergency Call",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = ErrorRed
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState) {
                is CallUiState.Loading -> {
                    CircularProgressIndicator(color = ErrorRed)
                }
                is CallUiState.SessionLoaded -> {
                    val session = (uiState as CallUiState.SessionLoaded).session
                    Text(
                        text = "Incoming from $displayVictimName",
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "Accident reported at location.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                is CallUiState.Error -> {
                    Text(
                        text = (uiState as CallUiState.Error).message,
                        color = ErrorRed,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(64.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val isLoading = uiState is CallUiState.Loading
                
                // Reject Button
                Button(
                    onClick = {
                        viewModel.rejectCall(sessionId)
                        onDismiss()
                    },
                    enabled = !isLoading,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.White)
                }

                // Accept Button
                Button(
                    onClick = { viewModel.acceptCall(context, sessionId, displayVictimName) },
                    enabled = !isLoading,
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White)
                }
            }
            
            if (uiState is CallUiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
    }
}
