package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.repository.AuthRepository
import com.accidentguard.data.repository.UnifiedLoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val message: String, val role: String = "user") : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AuthRepository()
    val tokenManager = TokenManager(application)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    // ── Unified Login for both User and Hospital ──
    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            when (val result = repo.loginUnified(email.trim(), pass)) {
                is UnifiedLoginResult.UserSuccess -> {
                    val user = result.data.user
                    tokenManager.saveAuthToken(
                        token = result.data.accessToken,
                        userName = user.fullName,
                        email = user.email,
                        phone = user.phoneNumber,
                        userId = user.id
                    )
                    _uiState.value = AuthUiState.Success("Welcome back, ${user.fullName}!", role = "user")
                }
                is UnifiedLoginResult.HospitalSuccess -> {
                    val hosp = result.data.hospital
                    tokenManager.saveHospitalAuthToken(
                        token = result.data.accessToken,
                        hospital = hosp
                    )
                    _uiState.value = AuthUiState.Success("Hospital unit connected: ${hosp.name}", role = "hospital")
                }
                is UnifiedLoginResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
            }
        }
    }

    // ── User Registration ──
    fun registerUser(
        fullName: String,
        email: String,
        phone: String,
        pass: String,
        confirmPass: String,
        termsAccepted: Boolean
    ) {
        if (fullName.isBlank() || email.isBlank() || phone.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match")
            return
        }
        if (!termsAccepted) {
            _uiState.value = AuthUiState.Error("Please accept Terms & Conditions and Privacy Policy")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repo.registerUser(fullName.trim(), email.trim(), phone.trim(), pass)
            result.onSuccess {
                _uiState.value = AuthUiState.Success("User account created successfully! Please login.", role = "user")
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "Registration failed")
            }
        }
    }

    // ── Hospital Registration ──
    fun registerHospital(
        name: String,
        email: String,
        phone: String,
        address: String,
        city: String,
        totalAmbulances: Int,
        pass: String,
        confirmPass: String,
        termsAccepted: Boolean
    ) {
        if (name.isBlank() || email.isBlank() || phone.isBlank() || address.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("All hospital fields are required")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = AuthUiState.Error("Please enter a valid official email address")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match")
            return
        }
        if (!termsAccepted) {
            _uiState.value = AuthUiState.Error("Please accept Terms & Emergency Dispatch Service Agreement")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repo.registerHospital(
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                pass = pass,
                address = address.trim(),
                city = city.ifBlank { "Nagpur" }.trim(),
                totalAmbulances = if (totalAmbulances > 0) totalAmbulances else 5
            )
            result.onSuccess { hospData ->
                tokenManager.saveHospitalAuthToken(
                    token = hospData.accessToken,
                    hospital = hospData.hospital
                )
                _uiState.value = AuthUiState.Success("Hospital unit registered successfully! Please login with your credentials.", role = "hospital")
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "Hospital registration failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun logout() {
        tokenManager.clear()
    }
}
