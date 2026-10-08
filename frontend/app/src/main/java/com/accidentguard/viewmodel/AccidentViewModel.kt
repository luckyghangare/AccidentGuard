package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.Accident
import com.accidentguard.data.repository.AccidentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AccidentHistoryState(
    val accidents: List<Accident> = emptyList(),
    val totalEventsCount: Int = 0,
    val selectedAccident: Accident? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AccidentViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repo = AccidentRepository()

    private val _uiState = MutableStateFlow(AccidentHistoryState())
    val uiState: StateFlow<AccidentHistoryState> = _uiState

    init {
        fetchAccidentHistory()
    }

    fun fetchAccidentHistory() {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repo.getAccidentHistory(token)

            result.onSuccess { list ->
                _uiState.value = _uiState.value.copy(
                    accidents = list,
                    totalEventsCount = list.size,
                    isLoading = false
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.message,
                    isLoading = false
                )
            }
        }
    }

    fun selectAccident(accident: Accident?) {
        _uiState.value = _uiState.value.copy(selectedAccident = accident)
    }
}
