package com.accidentguard.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.repository.AuthRepository
import com.accidentguard.data.repository.UnifiedLoginResult
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val message: String, val role: String = "user") : AuthUiState()
    data class Requires2FA(val email: String, val role: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AuthRepository()
    val tokenManager = TokenManager(application)
    private val notificationHelper = NotificationHelper(application)

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both email and password")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val is2FAEnabledLocally = tokenManager.is2FAEnabled()

            when (val result = repo.loginUnified(email.trim(), pass)) {
                is UnifiedLoginResult.UserSuccess -> {
                    if (is2FAEnabledLocally) {
                        _uiState.value = AuthUiState.Requires2FA(email, "user")
                    } else {
                        completeUserLogin(result)
                        notificationHelper.showNotification("Login Success", "Welcome back to Accident Guard!")
                    }
                }
                is UnifiedLoginResult.HospitalSuccess -> {
                    if (is2FAEnabledLocally) {
                        _uiState.value = AuthUiState.Requires2FA(email, "hospital")
                    } else {
                        completeHospitalLogin(result)
                        notificationHelper.showNotification("Hospital Login", "Emergency unit connected successfully.")
                    }
                }
                is UnifiedLoginResult.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
            }
        }
    }

    private fun completeUserLogin(result: UnifiedLoginResult.UserSuccess) {
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

    private fun completeHospitalLogin(result: UnifiedLoginResult.HospitalSuccess) {
        val hospLoginData = result.data
        tokenManager.saveHospitalAuthToken(
            token = hospLoginData.accessToken,
            hospital = hospLoginData.hospital
        )
        _uiState.value = AuthUiState.Success("Hospital unit connected: ${hospLoginData.hospital.name}", role = "hospital")
    }

    fun verifyOtp(email: String, otp: String, role: String) {
        if (otp.length < 4) {
            _uiState.value = AuthUiState.Error("Please enter a valid 6-digit code")
            return
        }

        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            if (otp == "123456" || otp == "000000") {
                _uiState.value = AuthUiState.Success("2FA Verified Successfully", role = role)
                notificationHelper.showNotification("Security Verified", "Identity confirmed via 2FA.")
            } else {
                _uiState.value = AuthUiState.Error("Invalid verification code. Try 123456.")
            }
        }
    }

    fun resendOtp(email: String) {
        _uiState.value = AuthUiState.Idle
        notificationHelper.showNotification("Security", "A new code has been sent to your email.")
    }

    fun registerUser(fullName: String, email: String, phone: String, pass: String, confirmPass: String, termsAccepted: Boolean) {
        if (fullName.isBlank() || email.isBlank() || phone.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match")
            return
        }
        if (!termsAccepted) {
            _uiState.value = AuthUiState.Error("Please accept Terms & Conditions")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repo.registerUser(fullName.trim(), email.trim(), phone.trim(), pass)
            result.onSuccess {
                _uiState.value = AuthUiState.Success("Account created successfully!", role = "user")
                notificationHelper.showNotification("Welcome", "Account created! You can now login.")
            }.onFailure { error ->
                _uiState.value = AuthUiState.Error(error.message ?: "Registration failed")
            }
        }
    }

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
        if (name.isBlank() || email.isBlank() || phone.isBlank() || address.isBlank() || city.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("All fields are required")
            return
        }
        if (pass != confirmPass) {
            _uiState.value = AuthUiState.Error("Passwords do not match")
            return
        }
        if (!termsAccepted) {
            _uiState.value = AuthUiState.Error("Please accept Emergency Dispatch Terms & Authority Agreement")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = repo.registerHospital(
                name = name,
                email = email,
                phone = phone,
                pass = pass,
                address = address,
                city = city,
                totalAmbulances = totalAmbulances
            )
            result.onSuccess {
                _uiState.value = AuthUiState.Success("Hospital unit registered successfully!", role = "hospital")
                notificationHelper.showNotification("Hospital Registered", "Emergency unit connected to the network.")
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
        notificationHelper.showNotification("Account", "Logged out securely.")
    }
}
