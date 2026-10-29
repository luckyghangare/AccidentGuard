package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.model.EmergencyContact
import com.accidentguard.data.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ContactUiState(
    val contacts: List<EmergencyContact> = emptyList(),
    val receivedInvitations: List<EmergencyContact> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ContactViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenManager = TokenManager(application)
    private val repo = ContactRepository()

    private val _uiState = MutableStateFlow(ContactUiState())
    val uiState: StateFlow<ContactUiState> = _uiState

    init {
        refreshAll()
    }

    fun refreshAll() {
        fetchContacts()
        fetchReceivedInvitations()
    }

    fun fetchContacts() {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            repo.getContacts(token)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(contacts = list, isLoading = false)
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun fetchReceivedInvitations() {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            repo.getReceivedInvitations(token)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(receivedInvitations = list)
                }
        }
    }

    fun acceptInvitation(invitationId: Long) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.acceptInvitation(token, invitationId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(successMessage = "Invitation accepted")
                    refreshAll()
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun rejectInvitation(invitationId: Long) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.rejectInvitation(token, invitationId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(successMessage = "Invitation rejected")
                    refreshAll()
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun addContact(name: String, phone: String, rel: String, isPrimary: Boolean) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.addContact(token, name, phone, rel, isPrimary)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(successMessage = "Contact added successfully")
                    fetchContacts()
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun updateContact(contactId: Long, name: String, phone: String, rel: String, isPrimary: Boolean) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.updateContact(token, contactId, name, phone, rel, isPrimary)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(successMessage = "Contact updated successfully")
                    fetchContacts()
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun deleteContact(contactId: Long) {
        val token = tokenManager.getAuthToken() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repo.deleteContact(token, contactId)
                .onSuccess { fetchContacts() }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(errorMessage = err.message, isLoading = false)
                }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
