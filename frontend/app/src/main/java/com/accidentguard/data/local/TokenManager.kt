package com.accidentguard.data.local

import android.content.Context
import android.content.SharedPreferences
import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.Hospital

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("accidentguard_prefs", Context.MODE_PRIVATE)

    init {
        // Sync RetrofitClient Base URL with saved preference
        RetrofitClient.updateBaseUrl(getServerUrl())
    }

    companion object {
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_ROLE = "user_role" // "user" or "hospital"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_HOSPITAL_CITY = "hospital_city"
        private const val KEY_HOSPITAL_ADDRESS = "hospital_address"
        private const val KEY_HOSPITAL_TOTAL_AMB = "hospital_total_amb"
        private const val KEY_HOSPITAL_AVAIL_AMB = "hospital_avail_amb"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SERVER_URL = "server_url"
    }

    fun getServerUrl(): String = prefs.getString(KEY_SERVER_URL, RetrofitClient.DEFAULT_BASE_URL) ?: RetrofitClient.DEFAULT_BASE_URL

    fun setServerUrl(url: String) {
        var formatted = url.trim()
        if (formatted.isNotEmpty()) {
            if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                formatted = "http://$formatted"
            }
            if (!formatted.endsWith("/")) {
                formatted = "$formatted/"
            }
            prefs.edit().putString(KEY_SERVER_URL, formatted).apply()
            RetrofitClient.updateBaseUrl(formatted)
        }
    }

    // Save standard vehicle user session
    fun saveAuthToken(token: String, userName: String, email: String, phone: String = "", userId: Int = 0) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USER_ROLE, "user")
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_PHONE, phone)
            .putInt(KEY_USER_ID, userId)
            .apply()
    }

    // Save hospital emergency authority session
    fun saveHospitalAuthToken(token: String, hospital: Hospital) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USER_ROLE, "hospital")
            .putString(KEY_USER_NAME, hospital.name)
            .putString(KEY_USER_EMAIL, hospital.email)
            .putString(KEY_USER_PHONE, hospital.phoneNumber)
            .putInt(KEY_USER_ID, hospital.id)
            .putString(KEY_HOSPITAL_CITY, hospital.city)
            .putString(KEY_HOSPITAL_ADDRESS, hospital.address)
            .putInt(KEY_HOSPITAL_TOTAL_AMB, hospital.totalAmbulances)
            .putInt(KEY_HOSPITAL_AVAIL_AMB, hospital.availableAmbulances)
            .apply()
    }

    fun getAuthToken(): String? = prefs.getString(KEY_JWT_TOKEN, null)

    fun getUserRole(): String = prefs.getString(KEY_USER_ROLE, "user") ?: "user"

    fun isHospital(): Boolean = getUserRole() == "hospital"

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""

    fun getUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    fun getUserPhone(): String = prefs.getString(KEY_USER_PHONE, "") ?: ""

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)

    fun getHospitalCity(): String = prefs.getString(KEY_HOSPITAL_CITY, "Nagpur") ?: "Nagpur"

    fun getHospitalAddress(): String = prefs.getString(KEY_HOSPITAL_ADDRESS, "") ?: ""

    fun getHospitalTotalAmbulances(): Int = prefs.getInt(KEY_HOSPITAL_TOTAL_AMB, 5)

    fun getHospitalAvailableAmbulances(): Int = prefs.getInt(KEY_HOSPITAL_AVAIL_AMB, 5)

    fun saveHospitalFleet(total: Int, available: Int) {
        prefs.edit()
            .putInt(KEY_HOSPITAL_TOTAL_AMB, total)
            .putInt(KEY_HOSPITAL_AVAIL_AMB, available)
            .apply()
    }

    fun updateUserProfile(name: String, phone: String) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_PHONE, phone)
            .apply()
    }

    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "English") ?: "English"

    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    fun isLoggedIn(): Boolean = !getAuthToken().isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
