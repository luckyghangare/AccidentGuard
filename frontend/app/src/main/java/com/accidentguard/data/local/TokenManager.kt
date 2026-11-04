package com.accidentguard.data.local

import android.content.Context
import android.content.SharedPreferences
import com.accidentguard.data.api.RetrofitClient
import com.accidentguard.data.model.Hospital

/**
 * Manages JWT tokens, user sessions, and server configuration.
 * Note: Migration to EncryptedSharedPreferences is planned for improved security.
 */
class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    init {
        // Always synchronize Retrofit with the current configured server URL.
        RetrofitClient.updateBaseUrl(getServerUrl())
    }

    companion object {
        private const val PREF_NAME = "accident_guard_prefs"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHONE = "user_phone"
        private const val KEY_USER_ID = "user_id"

        private const val KEY_HOSPITAL_CITY = "hospital_city"
        private const val KEY_HOSPITAL_ADDRESS = "hospital_address"
        private const val KEY_HOSPITAL_TOTAL_AMB = "hospital_total_amb"
        private const val KEY_HOSPITAL_AVAIL_AMB = "hospital_avail_amb"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SERVER_URL = "server_url"

        // Security Settings
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_2FA_ENABLED = "two_fa_enabled"
        private const val KEY_LAST_LOGIN_TIME = "last_login_time"
    }

    // --------------------------------------------------
    // SERVER URL MANAGEMENT
    // --------------------------------------------------

    fun getServerUrl(): String {
        val savedUrl = prefs.getString(KEY_SERVER_URL, null)

        // Migrate old temporary Cloudflare Quick Tunnel URLs.
        val isOldQuickTunnel = savedUrl
            ?.contains(".trycloudflare.com", ignoreCase = true) == true

        val normalizedSavedUrl = if (!savedUrl.isNullOrBlank() && !isOldQuickTunnel) {
            normalizeServerUrl(savedUrl)
        } else {
            ""
        }

        val serverUrl = normalizedSavedUrl.ifEmpty {
            RetrofitClient.DEFAULT_BASE_URL
        }

        // Save the migrated/default URL for future app launches.
        if (savedUrl != serverUrl) {
            prefs.edit()
                .putString(KEY_SERVER_URL, serverUrl)
                .apply()
        }

        return serverUrl
    }

    /**
     * Validates and saves a new server URL.
     * Rejects invalid formats to prevent API failures.
     */
    fun setServerUrl(url: String) {
        val formattedUrl = normalizeServerUrl(url)

        if (formattedUrl.isNotEmpty()) {
            prefs.edit()
                .putString(KEY_SERVER_URL, formattedUrl)
                .apply()

            RetrofitClient.updateBaseUrl(formattedUrl)
        }
    }

    private fun normalizeServerUrl(url: String): String {
        var formatted = url.trim()

        if (formatted.isEmpty()) {
            return ""
        }

        // Ensure scheme exists
        if (
            !formatted.startsWith("http://", ignoreCase = true) &&
            !formatted.startsWith("https://", ignoreCase = true)
        ) {
            formatted = "https://$formatted"
        }

        // Basic host validation
        val hostPart = formatted.substringAfter("://").substringBefore("/")
        if (hostPart.isEmpty()) {
            return ""
        }
        
        // Reject obviously invalid hosts without dots (except localhost/internal IPs)
        if (!hostPart.contains(".") && 
            hostPart.lowercase() != "localhost" && 
            !hostPart.startsWith("10.0.")) {
            return ""
        }

        if (!formatted.endsWith("/")) {
            formatted += "/"
        }

        return formatted
    }

    // --------------------------------------------------
    // STANDARD USER AUTHENTICATION
    // --------------------------------------------------

    fun saveAuthToken(
        token: String,
        userName: String,
        email: String,
        phone: String = "",
        userId: Long = 0
    ) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USER_ROLE, "user")
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_PHONE, phone)
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    // --------------------------------------------------
    // HOSPITAL AUTHENTICATION
    // --------------------------------------------------

    fun saveHospitalAuthToken(
        token: String,
        hospital: Hospital
    ) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USER_ROLE, "hospital")
            .putString(KEY_USER_NAME, hospital.name)
            .putString(KEY_USER_EMAIL, hospital.email)
            .putString(KEY_USER_PHONE, hospital.phoneNumber)
            .putLong(KEY_USER_ID, hospital.id)
            .putString(KEY_HOSPITAL_CITY, hospital.city)
            .putString(KEY_HOSPITAL_ADDRESS, hospital.address)
            .putInt(KEY_HOSPITAL_TOTAL_AMB, hospital.totalAmbulances)
            .putInt(KEY_HOSPITAL_AVAIL_AMB, hospital.availableAmbulances)
            .putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
            .apply()
    }

    // --------------------------------------------------
    // AUTHENTICATION GETTERS
    // --------------------------------------------------

    fun getAuthToken(): String? {
        return prefs.getString(KEY_JWT_TOKEN, null)
    }

    fun getUserRole(): String {
        return prefs.getString(KEY_USER_ROLE, "user") ?: "user"
    }

    fun isHospital(): Boolean {
        return getUserRole() == "hospital"
    }

    fun isLoggedIn(): Boolean {
        return !getAuthToken().isNullOrEmpty()
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "") ?: ""
    }

    fun getUserPhone(): String {
        return prefs.getString(KEY_USER_PHONE, "") ?: ""
    }

    fun getUserId(): Long {
        return prefs.getLong(KEY_USER_ID, 0L)
    }

    fun getLastLoginTime(): Long {
        return prefs.getLong(KEY_LAST_LOGIN_TIME, 0L)
    }

    // --------------------------------------------------
    // HOSPITAL DETAILS
    // --------------------------------------------------

    fun getHospitalCity(): String {
        return prefs.getString(KEY_HOSPITAL_CITY, "Nagpur") ?: "Nagpur"
    }

    fun getHospitalAddress(): String {
        return prefs.getString(KEY_HOSPITAL_ADDRESS, "") ?: ""
    }

    fun getHospitalTotalAmbulances(): Int {
        return prefs.getInt(KEY_HOSPITAL_TOTAL_AMB, 5)
    }

    fun getHospitalAvailableAmbulances(): Int {
        return prefs.getInt(KEY_HOSPITAL_AVAIL_AMB, 5)
    }

    fun saveHospitalFleet(
        total: Int,
        available: Int
    ) {
        prefs.edit()
            .putInt(KEY_HOSPITAL_TOTAL_AMB, total)
            .putInt(KEY_HOSPITAL_AVAIL_AMB, available)
            .apply()
    }

    // --------------------------------------------------
    // USER PROFILE
    // --------------------------------------------------

    fun updateUserProfile(
        name: String,
        phone: String
    ) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_PHONE, phone)
            .apply()
    }

    // --------------------------------------------------
    // BIOMETRIC SECURITY
    // --------------------------------------------------

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
            .apply()
    }

    // --------------------------------------------------
    // TWO-FACTOR AUTHENTICATION
    // --------------------------------------------------

    fun is2FAEnabled(): Boolean {
        return prefs.getBoolean(KEY_2FA_ENABLED, false)
    }

    fun set2FAEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_2FA_ENABLED, enabled)
            .apply()
    }

    // --------------------------------------------------
    // THEME SETTINGS
    // --------------------------------------------------

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): Int {
        return prefs.getInt(KEY_THEME_MODE, 0) // 0: System, 1: Light, 2: Dark
    }

    // --------------------------------------------------
    // LANGUAGE SETTINGS
    // --------------------------------------------------

    fun getLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, "English") ?: "English"
    }

    fun setLanguage(lang: String) {
        prefs.edit()
            .putString(KEY_LANGUAGE, lang)
            .apply()
    }

    // --------------------------------------------------
    // CLEAR SESSION
    // --------------------------------------------------

    /**
     * Clears authentication session data only, preserving user settings.
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_JWT_TOKEN)
            .remove(KEY_USER_ROLE)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_PHONE)
            .remove(KEY_USER_ID)
            .remove(KEY_HOSPITAL_CITY)
            .remove(KEY_HOSPITAL_ADDRESS)
            .remove(KEY_HOSPITAL_TOTAL_AMB)
            .remove(KEY_HOSPITAL_AVAIL_AMB)
            .remove(KEY_LAST_LOGIN_TIME)
            .apply()
    }

    // Legacy method for compatibility if needed
    fun saveToken(token: String) {
        prefs.edit().putString(KEY_JWT_TOKEN, token).apply()
    }

    fun getToken(): String? = getAuthToken()

    fun saveRole(role: String) {
        prefs.edit().putString(KEY_USER_ROLE, role).apply()
    }

    fun getRole(): String? = getUserRole()

    fun isUserLoggedIn(): Boolean = isLoggedIn()
}
