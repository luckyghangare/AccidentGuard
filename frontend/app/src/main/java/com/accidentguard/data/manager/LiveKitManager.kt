package com.accidentguard.data.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class LiveKitConnectionState {
    object Idle : LiveKitConnectionState()
    object Connecting : LiveKitConnectionState()
    object Reconnecting : LiveKitConnectionState()
    object Connected : LiveKitConnectionState() // Connected to room
    object WaitingForParticipant : LiveKitConnectionState() // Local connected, no remote yet
    object InCall : LiveKitConnectionState() // Both participants present
    data class Error(val message: String) : LiveKitConnectionState()
    object Disconnected : LiveKitConnectionState()
}

class LiveKitManager(private val context: Context) {
    private val TAG = "LiveKitManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var eventJob: Job? = null
    private val connectionMutex = Mutex()
    
    private var room: Room? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private val prefs = context.getSharedPreferences("livekit_manager_prefs", Context.MODE_PRIVATE)

    private var userMutePreference = false // false means Mic ON (Default)
    private var isInterrupted = false
    private var isVictim = false

    private val _connectionState = MutableStateFlow<LiveKitConnectionState>(LiveKitConnectionState.Idle)
    val connectionState: StateFlow<LiveKitConnectionState> = _connectionState

    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled

    private val _isSpeakerphoneEnabled = MutableStateFlow(true)
    val isSpeakerphoneEnabled: StateFlow<Boolean> = _isSpeakerphoneEnabled

    private val _remoteParticipantJoined = MutableStateFlow(false)
    val remoteParticipantJoined: StateFlow<Boolean> = _remoteParticipantJoined

    private val _isAudioActive = MutableStateFlow(false)
    val isAudioActive: StateFlow<Boolean> = _isAudioActive

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking: StateFlow<Boolean> = _isRemoteSpeaking

    private val _callDurationMillis = MutableStateFlow(0L)
    val callDurationMillis: StateFlow<Long> = _callDurationMillis

    private var timerJob: Job? = null

    suspend fun connect(url: String, token: String, isVictim: Boolean = false) = connectionMutex.withLock {
        this.isVictim = isVictim
        if (_connectionState.value == LiveKitConnectionState.Connected || 
            _connectionState.value == LiveKitConnectionState.Connecting ||
            _connectionState.value == LiveKitConnectionState.InCall ||
            _connectionState.value == LiveKitConnectionState.WaitingForParticipant) {
            Log.d(TAG, "Already connected or connecting to a room.")
            return@withLock
        }

        // Fresh start for a new connection attempt
        prefs.edit().remove("call_start_time").apply()
        _callDurationMillis.value = 0L

        _connectionState.value = LiveKitConnectionState.Connecting
        eventJob?.cancel()
        
        try {
            val roomOptions = RoomOptions(
                adaptiveStream = true,
                dynacast = true
            )

            // Release previous room if it exists
            room?.release()
            val newRoom = LiveKit.create(context, roomOptions)
            room = newRoom
            
            eventJob = scope.launch {
                newRoom.events.events.collect { event: RoomEvent ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.d(TAG, "Connected to room")
                            // LiveKit 2.1.0 uses audioHandler to manage routing.
                            // However, speakerphone state is managed via AudioDevice.
                            
                            // Explicitly publish local audio
                            scope.launch {
                                try {
                                    val shouldEnableMic = !userMutePreference && !isInterrupted
                                    newRoom.localParticipant.setMicrophoneEnabled(shouldEnableMic)
                                    _isMicEnabled.value = shouldEnableMic
                                    Log.d(TAG, "Local microphone initialized. Enabled: $shouldEnableMic")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to publish local audio", e)
                                }
                            }
                            updateCallState(newRoom)
                        }
                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "Disconnected from room")
                            cleanup(LiveKitConnectionState.Disconnected)
                        }
                        is RoomEvent.ParticipantConnected -> {
                            Log.d(TAG, "Remote participant joined")
                            _remoteParticipantJoined.value = true
                            updateCallState(newRoom)
                        }
                        is RoomEvent.ParticipantDisconnected -> {
                            Log.d(TAG, "Remote participant left")
                            _remoteParticipantJoined.value = newRoom.remoteParticipants.isNotEmpty()
                            _isAudioActive.value = false
                            updateCallState(newRoom)
                        }
                        is RoomEvent.TrackSubscribed -> {
                            if (event.track.kind == Track.Kind.AUDIO) {
                                Log.d(TAG, "Remote audio track subscribed")
                                _isAudioActive.value = true
                            }
                        }
                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track.kind == Track.Kind.AUDIO) {
                                _isAudioActive.value = false
                            }
                        }
                        is RoomEvent.ActiveSpeakersChanged -> {
                            val remoteSpeaking = event.speakers.any {
                                it.identity != newRoom.localParticipant.identity
                            }
                            _isRemoteSpeaking.value = remoteSpeaking
                            Log.d(TAG, "Remote participant speaking: $remoteSpeaking")
                        }
                        is RoomEvent.FailedToConnect -> {
                            Log.e(TAG, "Failed to connect: ${event.error}")
                            cleanup(LiveKitConnectionState.Error(event.error.message ?: "Failed to connect"))
                        }
                        is RoomEvent.Reconnecting -> {
                            Log.d(TAG, "Reconnecting...")
                            _connectionState.value = LiveKitConnectionState.Reconnecting
                        }
                        is RoomEvent.Reconnected -> {
                            Log.d(TAG, "Reconnected")
                            _connectionState.value = LiveKitConnectionState.Connected
                        }
                        else -> {
                            // Catching other events like TrackPublished which are represented by 
                            // RoomEvent.LocalTrackPublished in newer SDKs, but let's be safe.
                            Log.v(TAG, "Room event: ${event.javaClass.simpleName}")
                        }
                    }
                }
            }

            newRoom.connect(url, token)
            setupAudioMode(true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            cleanup(LiveKitConnectionState.Error(e.message ?: "Unknown error"))
        }
    }

    private fun updateCallState(currentRoom: Room) {
        val hasRemote = currentRoom.remoteParticipants.isNotEmpty()
        val newState = if (hasRemote) {
            LiveKitConnectionState.InCall
        } else {
            LiveKitConnectionState.WaitingForParticipant
        }
        
        _connectionState.value = newState
        
        if (newState == LiveKitConnectionState.InCall) {
            startTimer()
            // Re-enforce speakerphone when call goes active to ensure it wasn't reset by SDK during connection
            scope.launch {
                delay(500) // Give SDK time to settle routing
                if (_isSpeakerphoneEnabled.value) {
                    toggleSpeakerphone(true)
                }
            }
        } else {
            stopTimer()
        }
    }

    private fun startTimer() {
        if (timerJob != null) return
        
        var startTime = prefs.getLong("call_start_time", 0L)
        if (startTime == 0L) {
            startTime = System.currentTimeMillis()
            prefs.edit().putLong("call_start_time", startTime).apply()
        }

        timerJob = scope.launch {
            while (isActive) {
                _callDurationMillis.value = System.currentTimeMillis() - startTime
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun enableMicrophone(enabled: Boolean) {
        userMutePreference = !enabled // Manual Mute persistence
        Log.d(TAG, "Manual mute updated: userMutePreference=$userMutePreference")
        updateMicrophoneState()
    }

    private fun updateMicrophoneState() {
        val shouldBeEnabled = !userMutePreference && !isInterrupted
        scope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(shouldBeEnabled)
                _isMicEnabled.value = shouldBeEnabled
                Log.d(TAG, "Microphone state updated: enabled=$shouldBeEnabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating microphone state", e)
            }
        }
    }

    fun toggleSpeakerphone(enabled: Boolean) {
        _isSpeakerphoneEnabled.value = enabled
        // In LiveKit 2.1.0, audio routing is handled internally. 
        // We set the mode to communication and let the system handle it, 
        // or we use the AudioManager directly if the SDK doesn't expose a simple toggle.
        try {
            audioManager.isSpeakerphoneOn = enabled
            Log.d(TAG, "Speakerphone toggled via AudioManager: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speakerphone", e)
        }
    }

    private fun setupAudioMode(enable: Boolean) {
        // We keep minimal AudioManager setup but avoid conflicting with WebRTC's internal mode switching
        try {
            if (enable) {
                // For VoIP calls, MODE_IN_COMMUNICATION is standard and required for speakerphone
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                requestAudioFocus()
                toggleSpeakerphone(true)
                Log.d(TAG, "Audio mode set to IN_COMMUNICATION and speakerphone enabled")
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                abandonAudioFocus()
                Log.d(TAG, "Audio mode reset to NORMAL")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting audio mode", e)
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost: $focusChange")
                isInterrupted = true
                updateMicrophoneState()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained")
                isInterrupted = false
                updateMicrophoneState()
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                    handleAudioFocusChange(focusChange)
                }
                .build()
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun cleanup(targetState: LiveKitConnectionState) {
        stopTimer()
        _callDurationMillis.value = 0L
        prefs.edit().remove("call_start_time").apply()

        eventJob?.cancel()
        eventJob = null
        setupAudioMode(false)
        _connectionState.value = targetState
        _remoteParticipantJoined.value = false
        _isAudioActive.value = false
        _isRemoteSpeaking.value = false
        
        try {
            room?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing room", e)
        }
        room = null
    }

    suspend fun disconnect() = connectionMutex.withLock {
        Log.d(TAG, "Disconnecting LiveKit...")
        try {
            room?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error during room disconnect", e)
        } finally {
            cleanup(LiveKitConnectionState.Disconnected)
        }
    }
}
