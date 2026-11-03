package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

sealed class UnifiedLoginResult {
    data class UserSuccess(val data: LoginData) : UnifiedLoginResult()
    data class HospitalSuccess(val data: HospitalLoginData) : UnifiedLoginResult()
    data class Error(val message: String) : UnifiedLoginResult()
}

class AuthRepository {
    private val api = RetrofitClient.apiService

    suspend fun loginUnified(email: String, pass: String): UnifiedLoginResult = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim()
        
        // 1. Try regular User login
        val userResp: Response<ApiResponse<LoginData>>? = try {
            api.login(LoginRequest(trimmedEmail, pass))
        } catch (e: Exception) {
            null
        }

        if (userResp != null) {
            if (userResp.isSuccessful && userResp.body()?.success == true && userResp.body()?.data != null) {
                return@withContext UnifiedLoginResult.UserSuccess(userResp.body()!!.data!!)
            }
            
            // If user login failed with 401/404, it might be a hospital account.
            // If it failed with a 500 or network error, we should probably report that instead of blindly trying hospital.
            if (userResp.code() != 401 && userResp.code() != 404) {
                val errorMsg = userResp.body()?.error?.message ?: "Server error (${userResp.code()})"
                return@withContext UnifiedLoginResult.Error(errorMsg)
            }
        } else {
            // Network error during user login attempt
            return@withContext UnifiedLoginResult.Error("Network error. Please check your connection.")
        }

        // 2. Try Hospital login (Fallback only if user login was 401/404)
        try {
            val hospResp = api.hospitalLogin(HospitalLoginRequest(trimmedEmail, pass))
            if (hospResp.isSuccessful && hospResp.body()?.success == true && hospResp.body()?.data != null) {
                return@withContext UnifiedLoginResult.HospitalSuccess(hospResp.body()!!.data!!)
            } else {
                val errorMsg = hospResp.body()?.error?.message ?: "Invalid email or password."
                return@withContext UnifiedLoginResult.Error(errorMsg)
            }
        } catch (e: Exception) {
            return@withContext UnifiedLoginResult.Error("Network error during hospital authentication.")
        }
    }

    suspend fun registerUser(fullName: String, email: String, phone: String, pass: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.register(RegisterRequest(fullName.trim(), email.trim(), phone.trim(), pass))
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.data
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Registration failed: Empty response data"))
                }
            } else {
                val errorMsg = response.body()?.error?.message ?: "Registration failed (${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerHospital(
        name: String,
        email: String,
        phone: String,
        pass: String,
        address: String,
        city: String,
        totalAmbulances: Int
    ): Result<HospitalLoginData> = withContext(Dispatchers.IO) {
        try {
            val request = HospitalRegisterRequest(
                name = name.trim(),
                email = email.trim(),
                phoneNumber = phone.trim(),
                password = pass,
                address = address.trim(),
                city = city.trim(),
                totalAmbulances = totalAmbulances
            )
            val response = api.hospitalRegister(request)
            if (response.isSuccessful && response.body()?.success == true && response.body()?.data != null) {
                Result.success(response.body()!!.data!!)
            } else {
                val errorMsg = response.body()?.error?.message ?: "Hospital registration failed (${response.code()})"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
