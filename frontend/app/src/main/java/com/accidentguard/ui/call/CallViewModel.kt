package com.accidentguard.ui.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.manager.LiveKitConnectionState
import com.accidentguard.data.manager.LiveKitManager
import com.accidentguard.data.model.CallAcceptResponse
import com.accidentguard.data.model.EmergencyCallSession
import com.accidentguard.data.repository.CallRepository
import com.accidentguard.data.service.CallService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class CallUiState {
    object Idle : CallUiState()
    object Loading : CallUiState()

    data class SessionLoaded(
        val session: EmergencyCallSession
    ) : CallUiState()

    data class Accepted(
        val response: CallAcceptResponse
    ) : CallUiState()

    // Token is ready; the service has NOT started yet.
    data class TokenReady(
        val sessionId: Int,
        val token: String,
        val livekitUrl: String
    ) : CallUiState()

    data class Active(
        val sessionId: Int,
        val token: String,
        val livekitUrl: String
    ) : CallUiState()

    data class Error(
        val message: String
    ) : CallUiState()
}

class CallViewModel(
    private val repository: CallRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CallUiState>(CallUiState.Idle)

    val uiState: StateFlow<CallUiState> = _uiState

    private val _currentSessionId =
        MutableStateFlow<Int?>(null)

    val currentSessionId: StateFlow<Int?> = _currentSessionId

    private val _liveKitManager =
        MutableStateFlow<LiveKitManager?>(null)

    private var connectionStateJob: Job? = null

    // These guards are set synchronously before starting coroutines/services.
    private val joiningSessions = mutableSetOf<Int>()
    private val terminalSessions = mutableSetOf<Int>()

    private var loadingSessionId: Int? = null
    private var acceptingSessionId: Int? = null
    private var rejectingSessionId: Int? = null

    val liveKitState =
        _liveKitManager
            .flatMapLatest {
                it?.connectionState
                    ?: MutableStateFlow(LiveKitConnectionState.Idle)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                LiveKitConnectionState.Idle
            )

    val isMicEnabled =
        _liveKitManager
            .flatMapLatest {
                it?.isMicEnabled ?: MutableStateFlow(true)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                true
            )

    val isSpeakerphoneEnabled =
        _liveKitManager
            .flatMapLatest {
                it?.isSpeakerphoneEnabled ?: MutableStateFlow(true)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                true
            )

    val remoteParticipantJoined =
        _liveKitManager
            .flatMapLatest {
                it?.remoteParticipantJoined ?: MutableStateFlow(false)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                false
            )

    val isAudioActive =
        _liveKitManager
            .flatMapLatest {
                it?.isAudioActive ?: MutableStateFlow(false)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                false
            )

    val isRemoteSpeaking =
        _liveKitManager
            .flatMapLatest {
                it?.isRemoteSpeaking ?: MutableStateFlow(false)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                false
            )

    val callDurationMillis =
        _liveKitManager
            .flatMapLatest {
                it?.callDurationMillis ?: MutableStateFlow(0L)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                0L
            )

    // ---------------------------------------------------------
    // BIND SERVICE
    // ---------------------------------------------------------

    fun bindService(service: CallService) {

        val manager = service.getLiveKitManager()

        if (
            _liveKitManager.value === manager &&
            connectionStateJob?.isActive == true
        ) {
            return
        }

        connectionStateJob?.cancel()
        _liveKitManager.value = manager

        connectionStateJob = viewModelScope.launch {

            var callConnectionObserved = false

            manager.connectionState.collect { state ->

                when (state) {

                    is LiveKitConnectionState.Connecting,
                    is LiveKitConnectionState.Connected,
                    is LiveKitConnectionState.Reconnecting,
                    is LiveKitConnectionState.InCall -> {
                        callConnectionObserved = true
                    }

                    is LiveKitConnectionState.Disconnected -> {

                        if (
                            callConnectionObserved &&
                            _currentSessionId.value != null
                        ) {
                            callConnectionObserved = false

                            val endedSessionId = _currentSessionId.value

                            if (endedSessionId != null) {
                                joiningSessions.remove(endedSessionId)
                            }

                            _currentSessionId.value = null
                            _uiState.value = CallUiState.Idle
                        }
                    }

                    else -> Unit
                }
            }
        }
    }

    // ---------------------------------------------------------
    // LOAD CALL DETAILS
    // ---------------------------------------------------------

    fun loadCallDetails(sessionId: Int) {

        if (sessionId <= 0 || sessionId in terminalSessions) return
        if (loadingSessionId == sessionId) return

        val token = tokenManager.getAuthToken()

        if (token.isNullOrBlank()) {
            _uiState.value = CallUiState.Error("Please login again")
            return
        }

        loadingSessionId = sessionId

        viewModelScope.launch {
            _uiState.value = CallUiState.Loading

            try {
                val response = repository.getCallDetails(
                    "Bearer $token",
                    sessionId
                )

                if (response.isSuccessful && response.body() != null) {
                    _uiState.value =
                        CallUiState.SessionLoaded(response.body()!!)
                } else {
                    _uiState.value =
                        CallUiState.Error("Failed to load call details")
                }

            } catch (e: Exception) {
                _uiState.value =
                    CallUiState.Error(e.message ?: "Network error")

            } finally {
                loadingSessionId = null
            }
        }
    }

    // ---------------------------------------------------------
    // ACCEPT CALL
    // ---------------------------------------------------------

    fun acceptCall(
        context: Context,
        sessionId: Int,
        victimName: String
    ) {
        if (
            sessionId <= 0 ||
            sessionId in terminalSessions ||
            acceptingSessionId == sessionId
        ) {
            return
        }

        val token = tokenManager.getAuthToken()

        if (token.isNullOrBlank()) {
            _uiState.value = CallUiState.Error("Please login again")
            return
        }

        acceptingSessionId = sessionId

        viewModelScope.launch {
            _uiState.value = CallUiState.Loading

            try {
                val response = repository.acceptCall(
                    "Bearer $token",
                    sessionId
                )

                if (
                    response.isSuccessful &&
                    response.body()?.success == true
                ) {
                    val acceptData = response.body()?.data

                    if (acceptData != null) {
                        _currentSessionId.value = sessionId
                        _uiState.value = CallUiState.Accepted(acceptData)
                    } else {
                        _uiState.value =
                            CallUiState.Error("Invalid response data")
                    }

                } else if (response.code() == 409) {
                    _currentSessionId.value = null
                    _uiState.value = CallUiState.Error(
                        "Call already accepted by another contact"
                    )

                } else {
                    val errorMsg = response.errorBody()?.string()
                        ?: "Failed to accept call"

                    _uiState.value = CallUiState.Error(errorMsg)
                }

            } catch (e: Exception) {
                _uiState.value =
                    CallUiState.Error(e.message ?: "Network error")

            } finally {
                acceptingSessionId = null
            }
        }
    }

    // ---------------------------------------------------------
    // FETCH TOKEN ONLY
    // Transition screen must NOT start CallService.
    // ---------------------------------------------------------

    fun fetchCallToken(
        sessionId: Int,
        authToken: String
    ) {
        if (
            sessionId <= 0 ||
            sessionId in terminalSessions ||
            loadingSessionId == sessionId
        ) {
            return
        }

        loadingSessionId = sessionId

        viewModelScope.launch {
            _uiState.value = CallUiState.Loading

            try {
                if (authToken.isBlank()) {
                    _uiState.value =
                        CallUiState.Error("Please login again")
                    return@launch
                }

                val response = repository.getCallToken(
                    "Bearer $authToken",
                    sessionId
                )

                if (
                    response.isSuccessful &&
                    response.body() != null
                ) {
                    val tokenData = response.body()!!

                    if (
                        tokenData.token.isBlank() ||
                        tokenData.livekitUrl.isBlank()
                    ) {
                        _uiState.value =
                            CallUiState.Error("Invalid LiveKit token or URL")
                        return@launch
                    }

                    _currentSessionId.value = sessionId

                    // No ACTION_START_CALL here.
                    _uiState.value = CallUiState.TokenReady(
                        sessionId = sessionId,
                        token = tokenData.token,
                        livekitUrl = tokenData.livekitUrl
                    )

                } else {
                    _uiState.value = CallUiState.Error(
                        "Failed to retrieve call token"
                    )
                }

            } catch (e: Exception) {
                _uiState.value =
                    CallUiState.Error(e.message ?: "Network error")

            } finally {
                loadingSessionId = null
            }
        }
    }

    // ---------------------------------------------------------
    // JOIN CALL — ONLY ActiveCallScreen SHOULD CALL THIS
    // ---------------------------------------------------------

    fun joinCall(
        context: Context,
        sessionId: Int,
        token: String,
        livekitUrl: String,
        victimName: String
    ) {

        if (sessionId <= 0 || sessionId in terminalSessions) {
            return
        }

        if (
            token.isBlank() ||
            livekitUrl.isBlank()
        ) {
            _uiState.value =
                CallUiState.Error("Invalid LiveKit token or URL")
            return
        }

        // Synchronous duplicate protection.
        if (sessionId in joiningSessions) {
            return
        }

        if (
            _currentSessionId.value == sessionId &&
            _uiState.value is CallUiState.Active
        ) {
            return
        }

        joiningSessions.add(sessionId)
        _currentSessionId.value = sessionId

        _uiState.value = CallUiState.Active(
            sessionId = sessionId,
            token = token,
            livekitUrl = livekitUrl
        )

        try {
            // Service owns the actual LiveKit connection.
            CallService.startService(
                context = context.applicationContext,
                sessionId = sessionId,
                token = token,
                url = livekitUrl,
                victimName = victimName
            )

        } catch (e: Exception) {
            joiningSessions.remove(sessionId)
            _currentSessionId.value = null
            _uiState.value = CallUiState.Error(
                e.message ?: "Unable to start call service"
            )
        }
    }

    // ---------------------------------------------------------
    // MICROPHONE
    // ---------------------------------------------------------

    fun toggleMicrophone() {
        _liveKitManager.value?.let {
            it.enableMicrophone(!isMicEnabled.value)
        }
    }

    // ---------------------------------------------------------
    // SPEAKER
    // ---------------------------------------------------------

    fun toggleSpeakerphone() {
        _liveKitManager.value?.let {
            it.toggleSpeakerphone(!isSpeakerphoneEnabled.value)
        }
    }

    // ---------------------------------------------------------
    // END CALL
    // ---------------------------------------------------------

    fun endCall(
        context: Context,
        sessionId: Int
    ) {

        if (sessionId <= 0 || sessionId in terminalSessions) return

        // Mark terminal before asynchronous operations.
        terminalSessions.add(sessionId)
        joiningSessions.remove(sessionId)

        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = null
            _uiState.value = CallUiState.Idle
        }

        // Stop local service immediately.
        try {
            CallService.stopService(
                context.applicationContext,
                sessionId
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "CallViewModel",
                "Unable to stop call service",
                e
            )
        }

        val token = tokenManager.getAuthToken()

        viewModelScope.launch {
            if (!token.isNullOrBlank()) {
                try {
                    repository.endCall(
                        "Bearer $token",
                        sessionId
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "CallViewModel",
                        "Backend end-call request failed",
                        e
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // REJECT CALL
    // ---------------------------------------------------------

    fun rejectCall(
        sessionId: Int,
        reason: String? = null,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {

        if (sessionId <= 0 || sessionId in terminalSessions) {
            onComplete(false, "Invalid or already closed session")
            return
        }

        if (rejectingSessionId == sessionId) return

        val token = tokenManager.getAuthToken()

        if (token.isNullOrBlank()) {
            onComplete(false, "Please login again")
            return
        }

        rejectingSessionId = sessionId

        viewModelScope.launch {
            try {
                val response = repository.rejectCall(
                    "Bearer $token",
                    sessionId,
                    reason
                )

                if (response.isSuccessful) {
                    terminalSessions.add(sessionId)

                    if (_currentSessionId.value == sessionId) {
                        _currentSessionId.value = null
                    }

                    _uiState.value = CallUiState.Idle
                    onComplete(true, null)

                } else {
                    onComplete(
                        false,
                        "Unable to decline call (${response.code()})"
                    )
                }

            } catch (e: Exception) {
                onComplete(
                    false,
                    e.message ?: "Network error while declining call"
                )

            } finally {
                rejectingSessionId = null
            }
        }
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    override fun onCleared() {
        connectionStateJob?.cancel()

        // LiveKit lifecycle belongs to CallService.
        super.onCleared()
    }
}