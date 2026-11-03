package com.accidentguard.ui.call

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.ApiResponse
import com.accidentguard.data.model.CallAcceptResponse
import com.accidentguard.data.model.EmergencyCallSession
import com.accidentguard.data.repository.CallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flatMapLatest

import com.accidentguard.data.manager.LiveKitConnectionState
import com.accidentguard.data.manager.LiveKitManager
import com.accidentguard.data.model.CallTokenResponse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

sealed class CallUiState {
    object Idle : CallUiState()
    object Loading : CallUiState()
    data class SessionLoaded(val session: EmergencyCallSession) : CallUiState()
    data class Accepted(val response: CallAcceptResponse) : CallUiState()
    data class Active(val sessionId: Int, val token: String, val livekitUrl: String) : CallUiState()
    data class Error(val message: String) : CallUiState()
}

class CallViewModel(
    private val repository: CallRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val uiState: StateFlow<CallUiState> = _uiState

    private val _currentSessionId = MutableStateFlow<Int?>(null)
    val currentSessionId: StateFlow<Int?> = _currentSessionId

    private val _liveKitManager = MutableStateFlow<LiveKitManager?>(null)
    
    val liveKitState = _liveKitManager.flatMapLatest { it?.connectionState ?: MutableStateFlow(LiveKitConnectionState.Idle) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveKitConnectionState.Idle)

    val isMicEnabled = _liveKitManager.flatMapLatest { it?.isMicEnabled ?: MutableStateFlow(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isSpeakerphoneEnabled = _liveKitManager.flatMapLatest { it?.isSpeakerphoneEnabled ?: MutableStateFlow(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val remoteParticipantJoined = _liveKitManager.flatMapLatest { it?.remoteParticipantJoined ?: MutableStateFlow(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isAudioActive = _liveKitManager.flatMapLatest { it?.isAudioActive ?: MutableStateFlow(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isRemoteSpeaking = _liveKitManager.flatMapLatest { it?.isRemoteSpeaking ?: MutableStateFlow(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val callDurationMillis = _liveKitManager.flatMapLatest { it?.callDurationMillis ?: MutableStateFlow(0L) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun bindService(service: com.accidentguard.data.service.CallService) {
        val manager = service.getLiveKitManager()
        _liveKitManager.value = manager
        
        viewModelScope.launch {
            manager.connectionState.collect { state ->
                // Only reset UI if we explicitly reached Disconnected from an active state
                // or if the session ID has been cleared.
                if (state is LiveKitConnectionState.Disconnected && _currentSessionId.value != null) {
                    _uiState.value = CallUiState.Idle
                    _currentSessionId.value = null
                }
            }
        }
    }

    fun loadCallDetails(sessionId: Int) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = CallUiState.Loading
            try {
                val response = repository.getCallDetails("Bearer $token", sessionId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = CallUiState.SessionLoaded(response.body()!!)
                } else {
                    _uiState.value = CallUiState.Error("Failed to load call details")
                }
            } catch (e: Exception) {
                _uiState.value = CallUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun acceptCall(context: android.content.Context, sessionId: Int, victimName: String) {
        if (_uiState.value is CallUiState.Accepted || _uiState.value is CallUiState.Active) {
            return
        }

        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = CallUiState.Loading
            try {
                val response = repository.acceptCall("Bearer $token", sessionId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val acceptData = response.body()?.data
                    if (acceptData != null) {
                        _currentSessionId.value = sessionId
                        // Transition to Accepted; ActiveCallScreen will handle joinCall
                        _uiState.value = CallUiState.Accepted(acceptData)
                    } else {
                        _uiState.value = CallUiState.Error("Invalid response data")
                    }
                } else if (response.code() == 409) {
                    _uiState.value = CallUiState.Error("Call already accepted by another contact")
                } else {
                    val errorMsg = response.errorBody()?.string() ?: "Failed to accept call"
                    _uiState.value = CallUiState.Error(errorMsg)
                }
            } catch (e: Exception) {
                _uiState.value = CallUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun joinCall(context: android.content.Context, sessionId: Int, token: String, livekitUrl: String, victimName: String) {
        if (_uiState.value is CallUiState.Active && _currentSessionId.value == sessionId) {
            // Already joining/active for this session
            return
        }

        viewModelScope.launch {
            _currentSessionId.value = sessionId
            _uiState.value = CallUiState.Active(sessionId, token, livekitUrl)
            
            val intent = android.content.Intent(context, com.accidentguard.data.service.CallService::class.java).apply {
                action = com.accidentguard.data.service.CallService.ACTION_START_CALL
                putExtra(com.accidentguard.data.service.CallService.EXTRA_SESSION_ID, sessionId)
                putExtra(com.accidentguard.data.service.CallService.EXTRA_TOKEN, token)
                putExtra(com.accidentguard.data.service.CallService.EXTRA_URL, livekitUrl)
                putExtra(com.accidentguard.data.service.CallService.EXTRA_VICTIM_NAME, victimName)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Restore fetchTokenAndJoin for NavGraph compatibility
    fun fetchTokenAndJoin(context: android.content.Context, sessionId: Int, authToken: String, victimName: String) {
        viewModelScope.launch {
            _uiState.value = CallUiState.Loading
            try {
                val response = repository.getCallToken("Bearer $authToken", sessionId)
                if (response.isSuccessful && response.body() != null) {
                    val tokenData = response.body()!!
                    joinCall(context, sessionId, tokenData.token, tokenData.livekitUrl, victimName)
                } else {
                    _uiState.value = CallUiState.Error("Failed to retrieve call token")
                }
            } catch (e: Exception) {
                _uiState.value = CallUiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun toggleMicrophone() {
        _liveKitManager.value?.let { it.enableMicrophone(!isMicEnabled.value) }
    }

    fun toggleSpeakerphone() {
        _liveKitManager.value?.let { it.toggleSpeakerphone(!isSpeakerphoneEnabled.value) }
    }

    fun endCall(context: android.content.Context, sessionId: Int) {
        val token = tokenManager.getAuthToken()
        viewModelScope.launch {
            // Disconnect via Service
            val intent = android.content.Intent(context, com.accidentguard.data.service.CallService::class.java).apply {
                action = com.accidentguard.data.service.CallService.ACTION_STOP_SERVICE
            }
            context.startService(intent)
            
            // Notify backend if possible
            if (token != null) {
                try {
                    repository.endCall("Bearer $token", sessionId)
                } catch (e: Exception) {
                    // Log and continue; we want the local UI to reset anyway
                }
            }
            
            _currentSessionId.value = null
            _uiState.value = CallUiState.Idle
        }
    }

    fun rejectCall(sessionId: Int, reason: String? = null) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            try {
                repository.rejectCall("Bearer $token", sessionId, reason)
                _uiState.value = CallUiState.Idle
            } catch (e: Exception) {
                _uiState.value = CallUiState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // We DO NOT disconnect here anymore because the Service owns the lifecycle
    }
}
