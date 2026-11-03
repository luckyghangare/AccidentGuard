package com.accidentguard.ui.call

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.accidentguard.data.manager.LiveKitConnectionState
import com.accidentguard.data.service.CallService
import com.accidentguard.ui.theme.ErrorRed

@Composable
fun ActiveCallScreen(
    sessionId: Int,
    victimName: String?,
    token: String,
    livekitUrl: String,
    viewModel: CallViewModel,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val displayVictimName = victimName ?: "Someone"
    val liveKitState by viewModel.liveKitState.collectAsState()
    val isMicEnabled by viewModel.isMicEnabled.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerphoneEnabled.collectAsState()
    val remoteParticipantJoined by viewModel.remoteParticipantJoined.collectAsState()
    val isAudioActive by viewModel.isAudioActive.collectAsState()
    val isRemoteSpeaking by viewModel.isRemoteSpeaking.collectAsState()
    val callDurationMillis by viewModel.callDurationMillis.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    val formattedTime = remember(callDurationMillis) {
        val totalSeconds = callDurationMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    // Bind service to ViewModel to get LiveKit state
    DisposableEffect(context) {
        val intent = Intent(context, CallService::class.java)
        val connection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                val binder = service as? CallService.CallServiceBinder
                binder?.getService()?.let { 
                    viewModel.bindService(it)
                }
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {}
        }
        context.bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(connection)
        }
    }

    LaunchedEffect(sessionId, token, livekitUrl) {
        // Request joining call via ViewModel which starts the service
        // Use sessionId, token, livekitUrl as keys to prevent duplicate triggers
        viewModel.joinCall(context, sessionId, token, livekitUrl, displayVictimName)
    }

    LaunchedEffect(uiState, liveKitState) {
        // Only navigate away if we were previously in a call and now reached a terminal state
        if (uiState is CallUiState.Idle) {
            onCallEnded()
        } else if (liveKitState is LiveKitConnectionState.Disconnected && uiState !is CallUiState.Active) {
            // If LiveKit disconnects and we aren't currently trying to connect/be active
            onCallEnded()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(48.dp))
                
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    color = Color.Gray.copy(alpha = 0.2f),
                    border = if (isRemoteSpeaking) androidx.compose.foundation.BorderStroke(4.dp, Color.Green) else null
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp).padding(20.dp),
                        tint = if (isRemoteSpeaking) Color.Green else if (isAudioActive) Color.White else Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Emergency Session with $displayVictimName",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = when (liveKitState) {
                        is LiveKitConnectionState.Connecting -> "Connecting..."
                        is LiveKitConnectionState.WaitingForParticipant -> "Waiting for Contact..."
                        is LiveKitConnectionState.InCall -> {
                            if (isAudioActive) "Connected • $formattedTime" else "Audio Connecting..."
                        }
                        is LiveKitConnectionState.Connected -> "Joined Session"
                        is LiveKitConnectionState.Error -> "Connection Error"
                        is LiveKitConnectionState.Disconnected -> "Disconnected"
                        else -> "Initializing..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isAudioActive) Color.Green else Color.LightGray
                )
            }

            // Bottom Section: Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (liveKitState is LiveKitConnectionState.Error) {
                    Text(
                        text = (liveKitState as LiveKitConnectionState.Error).message,
                        color = ErrorRed,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute Toggle
                    IconButton(
                        onClick = { viewModel.toggleMicrophone() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (isMicEnabled) Color.White.copy(alpha = 0.1f) else Color.White,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isMicEnabled) "Mute" else "Unmute",
                            tint = if (isMicEnabled) Color.White else Color.Black
                        )
                    }

                    // Speaker Toggle
                    IconButton(
                        onClick = { viewModel.toggleSpeakerphone() },
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "Speakerphone",
                            tint = if (isSpeakerOn) Color.Black else Color.White
                        )
                    }

                    // End Call
                    Button(
                        onClick = { viewModel.endCall(context, sessionId) },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}
