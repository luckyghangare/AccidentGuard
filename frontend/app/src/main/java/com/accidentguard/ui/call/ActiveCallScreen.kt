
package com.accidentguard.ui.call

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    val context = LocalContext.current.applicationContext

    val displayVictimName = victimName ?: "Someone"

    // ---------------------------------------------------------
    // LIVEKIT STATES
    // ---------------------------------------------------------

    val liveKitState by viewModel.liveKitState.collectAsState()

    val isMicEnabled by viewModel.isMicEnabled.collectAsState()

    val isSpeakerOn by viewModel.isSpeakerphoneEnabled.collectAsState()

    val isAudioActive by viewModel.isAudioActive.collectAsState()

    val isRemoteSpeaking by viewModel.isRemoteSpeaking.collectAsState()

    val callDurationMillis by viewModel.callDurationMillis.collectAsState()

    // ---------------------------------------------------------
    // VIEWMODEL STATE
    // ---------------------------------------------------------

    val uiState by viewModel.uiState.collectAsState()

    // Prevent duplicate join requests for this session.
    var joinRequested by remember(sessionId) {
        mutableStateOf(false)
    }

    // True only after ViewModel reports Active.
    var callWasStarted by remember(sessionId) {
        mutableStateOf(false)
    }

    // Prevent duplicate navigation.
    var navigationHandled by remember(sessionId) {
        mutableStateOf(false)
    }

    // Prevent multiple End Call requests.
    var isEndingCall by remember(sessionId) {
        mutableStateOf(false)
    }

    // ---------------------------------------------------------
    // CALL DURATION
    // ---------------------------------------------------------

    val formattedTime = remember(callDurationMillis) {

        val totalSeconds = callDurationMillis / 1000

        val minutes = totalSeconds / 60

        val seconds = totalSeconds % 60

        String.format("%02d:%02d", minutes, seconds)
    }

    // ---------------------------------------------------------
    // BIND CALL SERVICE
    // ---------------------------------------------------------

    DisposableEffect(context, viewModel, sessionId) {

        val serviceIntent = Intent(
            context,
            CallService::class.java
        )

        var isBound = false

        val connection = object : ServiceConnection {

            override fun onServiceConnected(
                name: android.content.ComponentName?,
                service: IBinder?
            ) {

                val binder =
                    service as? CallService.CallServiceBinder

                binder?.getService()?.let { callService ->

                    viewModel.bindService(callService)

                    Log.d(
                        "ActiveCallScreen",
                        "CallService bound for session $sessionId"
                    )
                }
            }

            override fun onServiceDisconnected(
                name: android.content.ComponentName?
            ) {

                Log.w(
                    "ActiveCallScreen",
                    "CallService disconnected for session $sessionId"
                )
            }
        }

        try {

            isBound = context.bindService(
                serviceIntent,
                connection,
                Context.BIND_AUTO_CREATE
            )

            if (!isBound) {

                Log.e(
                    "ActiveCallScreen",
                    "Unable to bind CallService for session $sessionId"
                )
            }

        } catch (exception: Exception) {

            Log.e(
                "ActiveCallScreen",
                "Service binding failed",
                exception
            )
        }

        onDispose {

            if (isBound) {

                try {

                    context.unbindService(connection)

                    Log.d(
                        "ActiveCallScreen",
                        "CallService unbound for session $sessionId"
                    )

                } catch (exception: Exception) {

                    Log.e(
                        "ActiveCallScreen",
                        "Service unbinding failed",
                        exception
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // START / JOIN CALL
    // ---------------------------------------------------------

    LaunchedEffect(
        sessionId,
        token,
        livekitUrl
    ) {

        if (
            sessionId <= 0 ||
            token.isBlank() ||
            livekitUrl.isBlank()
        ) {

            Log.e(
                "ActiveCallScreen",
                "Invalid call parameters for session $sessionId"
            )

            return@LaunchedEffect
        }

        if (joinRequested) {
            return@LaunchedEffect
        }

        if (isEndingCall || navigationHandled) {
            return@LaunchedEffect
        }

        joinRequested = true

        Log.d(
            "ActiveCallScreen",
            "Starting emergency call for session $sessionId"
        )

        viewModel.joinCall(
            context = context,
            sessionId = sessionId,
            token = token,
            livekitUrl = livekitUrl,
            victimName = displayVictimName
        )
    }

    // ---------------------------------------------------------
    // CALL LIFECYCLE / NAVIGATION
    // ---------------------------------------------------------

    LaunchedEffect(
        sessionId,
        uiState
    ) {

        when (uiState) {

            is CallUiState.Active -> {

                callWasStarted = true

                Log.d(
                    "ActiveCallScreen",
                    "Call became active for session $sessionId"
                )
            }

            is CallUiState.Idle -> {

                // Ignore initial Idle state.
                // Navigate only after the call was started.
                if (
                    callWasStarted &&
                    !navigationHandled
                ) {

                    navigationHandled = true

                    Log.d(
                        "ActiveCallScreen",
                        "Call ended for session $sessionId"
                    )

                    onCallEnded()
                }
            }

            else -> Unit
        }
    }

    // ---------------------------------------------------------
    // PREVENT BACK BUTTON FROM UNINTENTIONALLY LEAVING CALL
    // ---------------------------------------------------------

    BackHandler(
        enabled = !navigationHandled
    ) {

        Log.d(
            "ActiveCallScreen",
            "Back button pressed for session $sessionId"
        )

        if (!isEndingCall) {

            isEndingCall = true

            viewModel.endCall(
                context,
                sessionId
            )
        }
    }

    // ---------------------------------------------------------
    // ACTIVE CALL UI
    // ---------------------------------------------------------

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

            // -------------------------------------------------
            // TOP SECTION
            // -------------------------------------------------

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(
                    modifier = Modifier.height(48.dp)
                )

                Surface(
                    modifier = Modifier.size(120.dp),

                    shape = CircleShape,

                    color = Color.Gray.copy(alpha = 0.2f),

                    border = if (isRemoteSpeaking) {
                        BorderStroke(4.dp, Color.Green)
                    } else {
                        null
                    }
                ) {

                    Icon(
                        imageVector = Icons.Default.Person,

                        contentDescription = "Contact",

                        modifier = Modifier
                            .size(60.dp)
                            .padding(20.dp),

                        tint = when {

                            isRemoteSpeaking -> Color.Green

                            isAudioActive -> Color.White

                            else -> Color.Gray
                        }
                    )
                }

                Spacer(
                    modifier = Modifier.height(24.dp)
                )

                Text(
                    text = "Emergency Session with $displayVictimName",

                    style = MaterialTheme.typography.headlineSmall,

                    color = Color.White,

                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = when (liveKitState) {

                        is LiveKitConnectionState.Connecting ->
                            "Connecting..."

                        is LiveKitConnectionState.WaitingForParticipant ->
                            "Waiting for Contact..."

                        is LiveKitConnectionState.InCall -> {

                            if (isAudioActive) {
                                "Connected • $formattedTime"
                            } else {
                                "Audio Connecting..."
                            }
                        }

                        is LiveKitConnectionState.Connected ->
                            "Joined Session"

                        is LiveKitConnectionState.Error ->
                            "Connection Error"

                        is LiveKitConnectionState.Disconnected ->
                            "Disconnected"

                        else ->
                            "Initializing..."
                    },

                    style = MaterialTheme.typography.bodyMedium,

                    color = if (isAudioActive) {
                        Color.Green
                    } else {
                        Color.LightGray
                    }
                )
            }

            // -------------------------------------------------
            // BOTTOM SECTION
            // -------------------------------------------------

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Connection error.
                if (
                    liveKitState is LiveKitConnectionState.Error
                ) {

                    Text(
                        text = (
                                liveKitState as LiveKitConnectionState.Error
                                ).message,

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

                    // -----------------------------------------
                    // MICROPHONE
                    // -----------------------------------------

                    IconButton(
                        onClick = {

                            if (!isEndingCall) {
                                viewModel.toggleMicrophone()
                            }
                        },

                        enabled = !isEndingCall,

                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (isMicEnabled) {
                                    Color.White.copy(alpha = 0.1f)
                                } else {
                                    Color.White
                                },
                                CircleShape
                            )
                    ) {

                        Icon(
                            imageVector = if (isMicEnabled) {
                                Icons.Default.Mic
                            } else {
                                Icons.Default.MicOff
                            },

                            contentDescription = if (isMicEnabled) {
                                "Mute"
                            } else {
                                "Unmute"
                            },

                            tint = if (isMicEnabled) {
                                Color.White
                            } else {
                                Color.Black
                            }
                        )
                    }

                    // -----------------------------------------
                    // SPEAKER
                    // -----------------------------------------

                    IconButton(
                        onClick = {

                            if (!isEndingCall) {
                                viewModel.toggleSpeakerphone()
                            }
                        },

                        enabled = !isEndingCall,

                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                if (isSpeakerOn) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.1f)
                                },
                                CircleShape
                            )
                    ) {

                        Icon(
                            imageVector = if (isSpeakerOn) {
                                Icons.Default.VolumeUp
                            } else {
                                Icons.Default.VolumeDown
                            },

                            contentDescription = "Speakerphone",

                            tint = if (isSpeakerOn) {
                                Color.Black
                            } else {
                                Color.White
                            }
                        )
                    }

                    // -----------------------------------------
                    // END CALL
                    // -----------------------------------------

                    Button(
                        onClick = {

                            if (!isEndingCall) {

                                isEndingCall = true

                                Log.d(
                                    "ActiveCallScreen",
                                    "End call requested for session $sessionId"
                                )

                                viewModel.endCall(
                                    context,
                                    sessionId
                                )
                            }
                        },

                        enabled = !isEndingCall,

                        modifier = Modifier.size(80.dp),

                        shape = CircleShape,

                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed
                        )
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
