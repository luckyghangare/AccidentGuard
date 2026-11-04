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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

sealed class LiveKitConnectionState {
    object Idle : LiveKitConnectionState()
    object Connecting : LiveKitConnectionState()
    object Reconnecting : LiveKitConnectionState()
    object Connected : LiveKitConnectionState()
    object WaitingForParticipant : LiveKitConnectionState()
    object InCall : LiveKitConnectionState()
    data class Error(val message: String) : LiveKitConnectionState()
    object Disconnected : LiveKitConnectionState()
}

data class LiveKitConnectionFailure(
    val sessionId: Int?,
    val message: String
)

class LiveKitManager(private val context: Context) {

    private val TAG = "LiveKitManager"

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val connectionMutex = Mutex()

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioFocusRequest: AudioFocusRequest? = null

    private val prefs = context.getSharedPreferences(
        "livekit_manager_prefs",
        Context.MODE_PRIVATE
    )

    private var userMutePreference = false
    private var isInterrupted = false
    private var isVictim = false

    private var generationCounter = 0L
    private var activeSession: RoomSession? = null
    private var timerJob: Job? = null

    private class RoomSession(
        val room: Room,
        val generation: Long,
        val sessionId: Int?
    ) {
        var eventJob: Job? = null
        val released = AtomicBoolean(false)
        val failureReported = AtomicBoolean(false)
    }

    private val _connectionState =
        MutableStateFlow<LiveKitConnectionState>(
            LiveKitConnectionState.Idle
        )
    val connectionState: StateFlow<LiveKitConnectionState> =
        _connectionState.asStateFlow()

    private val _isMicEnabled = MutableStateFlow(true)
    val isMicEnabled: StateFlow<Boolean> = _isMicEnabled.asStateFlow()

    private val _isSpeakerphoneEnabled = MutableStateFlow(true)
    val isSpeakerphoneEnabled: StateFlow<Boolean> =
        _isSpeakerphoneEnabled.asStateFlow()

    private val _remoteParticipantJoined = MutableStateFlow(false)
    val remoteParticipantJoined: StateFlow<Boolean> =
        _remoteParticipantJoined.asStateFlow()

    private val _isAudioActive = MutableStateFlow(false)
    val isAudioActive: StateFlow<Boolean> =
        _isAudioActive.asStateFlow()

    private val _isRemoteSpeaking = MutableStateFlow(false)
    val isRemoteSpeaking: StateFlow<Boolean> =
        _isRemoteSpeaking.asStateFlow()

    private val _callDurationMillis = MutableStateFlow(0L)
    val callDurationMillis: StateFlow<Long> =
        _callDurationMillis.asStateFlow()

    private val _connectionFailures =
        MutableSharedFlow<LiveKitConnectionFailure>(
            replay = 0,
            extraBufferCapacity = 8
        )

    val connectionFailures: SharedFlow<LiveKitConnectionFailure> =
        _connectionFailures.asSharedFlow()

    /**
     * sessionId is optional to preserve compatibility with existing callers.
     */
    suspend fun connect(
        url: String,
        token: String,
        isVictim: Boolean = false,
        sessionId: Int? = null
    ) {
        connectionMutex.withLock {
            this.isVictim = isVictim

            val existing = activeSession

            if (existing != null) {
                if (existing.sessionId == sessionId) {
                    Log.d(TAG, "Room already active for session $sessionId")
                    return
                }

                Log.d(
                    TAG,
                    "Replacing previous room: ${existing.sessionId}"
                )

                disconnectSessionLocked(existing)
            }

            val generation = ++generationCounter

            prefs.edit()
                .remove("call_start_time")
                .apply()

            _callDurationMillis.value = 0L
            _connectionState.value = LiveKitConnectionState.Connecting

            val roomOptions = RoomOptions(
                adaptiveStream = true,
                dynacast = true
            )

            val newRoom = LiveKit.create(context, roomOptions)

            val session = RoomSession(
                room = newRoom,
                generation = generation,
                sessionId = sessionId
            )

            activeSession = session

            session.eventJob = scope.launch {
                newRoom.events.events.collect { event ->
                    if (!isCurrent(session)) {
                        return@collect
                    }

                    when (event) {
                        is RoomEvent.Connected -> {
                            Log.d(TAG, "Connected: generation=$generation")

                            scope.launch {
                                if (!isCurrent(session)) return@launch

                                try {
                                    val shouldEnableMic =
                                        !userMutePreference && !isInterrupted

                                    newRoom.localParticipant
                                        .setMicrophoneEnabled(shouldEnableMic)

                                    if (isCurrent(session)) {
                                        _isMicEnabled.value = shouldEnableMic
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.e(
                                        TAG,
                                        "Failed to initialize microphone",
                                        e
                                    )
                                }
                            }

                            updateCallState(session)
                        }

                        is RoomEvent.Disconnected -> {
                            Log.d(TAG, "Room disconnected: generation=$generation")
                            finishSession(
                                session,
                                LiveKitConnectionState.Disconnected
                            )
                        }

                        is RoomEvent.ParticipantConnected -> {
                            _remoteParticipantJoined.value = true
                            updateCallState(session)
                        }

                        is RoomEvent.ParticipantDisconnected -> {
                            _remoteParticipantJoined.value =
                                newRoom.remoteParticipants.isNotEmpty()

                            _isAudioActive.value = false
                            _isRemoteSpeaking.value = false

                            updateCallState(session)
                        }

                        is RoomEvent.TrackSubscribed -> {
                            if (event.track.kind == Track.Kind.AUDIO) {
                                _isAudioActive.value = true
                            }
                        }

                        is RoomEvent.TrackUnsubscribed -> {
                            if (event.track.kind == Track.Kind.AUDIO) {
                                _isAudioActive.value = false
                            }
                        }

                        is RoomEvent.ActiveSpeakersChanged -> {
                            _isRemoteSpeaking.value =
                                event.speakers.any {
                                    it.identity !=
                                            newRoom.localParticipant.identity
                                }
                        }

                        is RoomEvent.FailedToConnect -> {
                            val message =
                                event.error.message ?: "Failed to connect"

                            Log.e(TAG, "FailedToConnect: $message")

                            reportFailure(session, message)

                            finishSession(
                                session,
                                LiveKitConnectionState.Error(message)
                            )
                        }

                        is RoomEvent.Reconnecting -> {
                            _connectionState.value =
                                LiveKitConnectionState.Reconnecting
                        }

                        is RoomEvent.Reconnected -> {
                            updateCallState(session)
                        }

                        else -> {
                            Log.v(
                                TAG,
                                "Room event: ${event.javaClass.simpleName}"
                            )
                        }
                    }
                }
            }

            try {
                newRoom.connect(url, token)

                if (isCurrent(session)) {
                    setupAudioMode(true)
                }
            } catch (e: CancellationException) {
                finishSession(
                    session,
                    LiveKitConnectionState.Disconnected
                )
                throw e
            } catch (e: Exception) {
                val message = e.message ?: "Unknown LiveKit connection error"

                Log.e(TAG, "Connection failed", e)

                reportFailure(session, message)

                finishSession(
                    session,
                    LiveKitConnectionState.Error(message)
                )

                // Do not swallow the failure.
                throw e
            }
        }
    }

    /**
     * An old room's event must never update the current room's state.
     */
    private fun isCurrent(session: RoomSession): Boolean {
        return activeSession === session &&
                activeSession?.generation == session.generation &&
                !session.released.get()
    }

    private fun reportFailure(
        session: RoomSession,
        message: String
    ) {
        if (!session.failureReported.compareAndSet(false, true)) {
            return
        }

        _connectionFailures.tryEmit(
            LiveKitConnectionFailure(
                sessionId = session.sessionId,
                message = message
            )
        )
    }

    private fun updateCallState(session: RoomSession) {
        if (!isCurrent(session)) return

        val hasRemote =
            session.room.remoteParticipants.isNotEmpty()

        _remoteParticipantJoined.value = hasRemote

        if (hasRemote) {
            _connectionState.value = LiveKitConnectionState.InCall
            startTimer()

            scope.launch {
                delay(500)

                if (
                    isCurrent(session) &&
                    _isSpeakerphoneEnabled.value
                ) {
                    toggleSpeakerphone(true)
                }
            }
        } else {
            _connectionState.value =
                LiveKitConnectionState.WaitingForParticipant

            stopTimer()
        }
    }

    private fun startTimer() {
        if (timerJob?.isActive == true) return

        var startTime = prefs.getLong("call_start_time", 0L)

        if (startTime == 0L) {
            startTime = System.currentTimeMillis()

            prefs.edit()
                .putLong("call_start_time", startTime)
                .apply()
        }

        timerJob = scope.launch {
            while (isActive) {
                _callDurationMillis.value =
                    System.currentTimeMillis() - startTime

                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun enableMicrophone(enabled: Boolean) {
        userMutePreference = !enabled

        Log.d(
            TAG,
            "Manual mute preference: $userMutePreference"
        )

        updateMicrophoneState()
    }

    private fun updateMicrophoneState() {
        val session = activeSession ?: return
        val shouldBeEnabled = !userMutePreference && !isInterrupted

        scope.launch {
            if (!isCurrent(session)) return@launch

            try {
                session.room.localParticipant
                    .setMicrophoneEnabled(shouldBeEnabled)

                if (isCurrent(session)) {
                    _isMicEnabled.value = shouldBeEnabled
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Microphone update failed", e)
            }
        }
    }

    fun toggleSpeakerphone(enabled: Boolean) {
        _isSpeakerphoneEnabled.value = enabled

        try {
            audioManager.isSpeakerphoneOn = enabled
            Log.d(TAG, "Speakerphone: $enabled")
        } catch (e: Exception) {
            Log.e(TAG, "Speakerphone update failed", e)
        }
    }

    private fun setupAudioMode(enable: Boolean) {
        try {
            if (enable) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                requestAudioFocus()
                toggleSpeakerphone(true)
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                abandonAudioFocus()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio mode update failed", e)
        }
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isInterrupted = true
                updateMicrophoneState()
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                isInterrupted = false
                updateMicrophoneState()
            }
        }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        handleAudioFocusChange(focusChange)
                    }
                    .build()

            audioFocusRequest?.let {
                audioManager.requestAudioFocus(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    /**
     * Finalizes only the supplied room session.
     * Atomic release prevents duplicate Room.release() calls.
     */
    private fun finishSession(
        session: RoomSession,
        targetState: LiveKitConnectionState
    ) {
        if (activeSession !== session) {
            releaseRoomOnce(session)
            return
        }

        activeSession = null

        session.eventJob?.cancel()
        session.eventJob = null

        stopTimer()

        _callDurationMillis.value = 0L

        prefs.edit()
            .remove("call_start_time")
            .apply()

        _remoteParticipantJoined.value = false
        _isAudioActive.value = false
        _isRemoteSpeaking.value = false

        setupAudioMode(false)

        _connectionState.value = targetState

        releaseRoomOnce(session)
    }

    private fun releaseRoomOnce(session: RoomSession) {
        if (!session.released.compareAndSet(false, true)) {
            return
        }

        try {
            session.room.release()
        } catch (e: Exception) {
            Log.e(TAG, "Room release failed", e)
        }
    }

    private suspend fun disconnectSessionLocked(
        session: RoomSession
    ) {
        if (activeSession !== session) return

        try {
            session.room.disconnect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Room disconnect failed", e)
        } finally {
            finishSession(
                session,
                LiveKitConnectionState.Disconnected
            )
        }
    }

    /**
     * expectedSessionId protects a newer room from an old stop request.
     * Null preserves compatibility with existing disconnect() callers.
     */
    suspend fun disconnect(expectedSessionId: Int? = null) {
        connectionMutex.withLock {
            val session = activeSession

            if (session == null) {
                if (_connectionState.value !is LiveKitConnectionState.Error) {
                    _connectionState.value =
                        LiveKitConnectionState.Disconnected
                }
                return
            }

            if (
                expectedSessionId != null &&
                session.sessionId != expectedSessionId
            ) {
                Log.w(
                    TAG,
                    "Ignoring stale disconnect. Expected=$expectedSessionId " +
                            "Actual=${session.sessionId}"
                )
                return
            }

            Log.d(TAG, "Disconnecting session ${session.sessionId}")

            disconnectSessionLocked(session)
        }
    }

    /**
     * Non-suspending cleanup for unexpected Service destruction.
     */
    fun dispose() {
        val session = activeSession

        if (session != null) {
            finishSession(
                session,
                LiveKitConnectionState.Disconnected
            )
        }

        scope.coroutineContext[Job]?.cancel()
    }
}