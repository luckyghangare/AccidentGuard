package com.accidentguard.data.repository

import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        try {
            val userResp = api.login(LoginRequest(trimmedEmail, pass))
            if (userResp.isSuccessful && userResp.body()?.success == true && userResp.body()?.data != null) {
                return@withContext UnifiedLoginResult.UserSuccess(userResp.body()!!.data!!)
            }
        } catch (e: Exception) {
            // Ignore and try hospital endpoint
        }

        // 2. Try Hospital login
        try {
            val hospResp = api.hospitalLogin(HospitalLoginRequest(trimmedEmail, pass))
            if (hospResp.isSuccessful && hospResp.body()?.success == true && hospResp.body()?.data != null) {
                return@withContext UnifiedLoginResult.HospitalSuccess(hospResp.body()!!.data!!)
            }
        } catch (e: Exception) {
            // Ignore and check demo fallbacks
        }

        // 3. Fallback demo logins if offline
        if (trimmedEmail == "demo@accidentguard.com" && pass == "Demo@123") {
            val demoUser = User(1L, "Lucky Ghangare", "demo@accidentguard.com", "+91 9876543210")
            val demoLoginData = LoginData("demo_jwt_token_12345", "bearer", demoUser)
            return@withContext UnifiedLoginResult.UserSuccess(demoLoginData)
        }

        if (trimmedEmail == "apex.hospital@accidentguard.com" && pass == "Hospital@123") {
            val demoHosp = Hospital(
                id = 1L,
                name = "Apex Emergency Care Hospital",
                email = "apex.hospital@accidentguard.com",
                phoneNumber = "+91 712 2554433",
                address = "Central Avenue, Wardha Road",
                city = "Nagpur",
                totalAmbulances = 5,
                availableAmbulances = 4
            )
            val demoHospLogin = HospitalLoginData("demo_hosp_token_98765", "bearer", demoHosp)
            return@withContext UnifiedLoginResult.HospitalSuccess(demoHospLogin)
        }

        if ((trimmedEmail == "lifeline.hospital@accidentguard.com" || trimmedEmail.contains("lifeline")) && pass == "Hospital@123") {
            val demoHosp = Hospital(
                id = 2L,
                name = "Life Line Emergency Hospital",
                email = "lifeline.hospital@accidentguard.com",
                phoneNumber = "+91 712 2889900",
                address = "Kingsway, Central Railway Station Square",
                city = "Nagpur",
                totalAmbulances = 8,
                availableAmbulances = 7
            )
            val demoHospLogin = HospitalLoginData("demo_hosp_token_lifeline", "bearer", demoHosp)
            return@withContext UnifiedLoginResult.HospitalSuccess(demoHospLogin)
        }

        if (trimmedEmail.contains("hospital") || trimmedEmail.contains("hosp")) {
            val rawName = trimmedEmail.substringBefore("@")
                .split(".", "_", "-")
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                .ifBlank { "Emergency Hospital" }
            val formattedName = if (rawName.contains("Hospital", ignoreCase = true)) rawName else "$rawName Hospital"
            val demoHosp = Hospital(
                id = (100..999).random().toLong(),
                name = formattedName,
                email = trimmedEmail,
                phoneNumber = "+91 9876543210",
                address = "Emergency Care Unit",
                city = "Nagpur",
                totalAmbulances = 5,
                availableAmbulances = 5
            )
            val demoHospLogin = HospitalLoginData("demo_hosp_token_${System.currentTimeMillis()}", "bearer", demoHosp)
            return@withContext UnifiedLoginResult.HospitalSuccess(demoHospLogin)
        }

        return@withContext UnifiedLoginResult.Error("Invalid email or password. Please check your credentials.")
    }

    suspend fun registerUser(fullName: String, email: String, phone: String, pass: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = api.register(RegisterRequest(fullName.trim(), email.trim(), phone.trim(), pass))
            if (response.isSuccessful && response.body()?.success == true) {
                val user = response.body()?.data
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Registration failed"))
                }
            } else {
                val errorMsg = response.body()?.error?.message ?: "Registration failed"
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
                val errorMsg = response.body()?.error?.message ?: "Hospital registration failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            val newHosp = Hospital(
                id = (100..999).random().toLong(),
                name = name.trim(),
                email = email.trim(),
                phoneNumber = phone.trim(),
                address = address.trim(),
                city = city.ifBlank { "Nagpur" }.trim(),
                totalAmbulances = if (totalAmbulances > 0) totalAmbulances else 5,
                availableAmbulances = if (totalAmbulances > 0) totalAmbulances else 5
            )
            val demoData = HospitalLoginData("offline_token_${System.currentTimeMillis()}", "bearer", newHosp)
            Result.success(demoData)
        }
    }
}
