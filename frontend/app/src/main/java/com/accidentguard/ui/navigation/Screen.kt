package com.accidentguard.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object HospitalDashboard : Screen("hospital_dashboard")
    object VehicleMonitoring : Screen("vehicle_monitoring")
    object LiveLocation : Screen("live_location")
    object CrashAlert : Screen("crash_alert")
    object AccidentHistory : Screen("accident_history")
    object EmergencyContacts : Screen("emergency_contacts")
    object Settings : Screen("settings")

    // Safety & Security Screens
    object PermissionRequired : Screen("permission_required")
    object GpsRequired : Screen("gps_required")
    object BiometricLock : Screen("biometric_lock")
    object OtpVerification : Screen("otp_verification/{email}/{role}") {
        fun createRoute(email: String, role: String) = "otp_verification/$email/$role"
    }
    object SecuritySettings : Screen("security_settings")

    // Emergency Call Screens
    object IncomingCall : Screen("incoming_call/{sessionId}?autoAccept={autoAccept}&victimName={victimName}") {
        fun createRoute(sessionId: Int, autoAccept: Boolean = false, victimName: String? = null) = 
            "incoming_call/$sessionId?autoAccept=$autoAccept${if (victimName != null) "&victimName=$victimName" else ""}"
    }
    object ActiveCall : Screen("active_call/{sessionId}/{token}/{livekitUrl}?victimName={victimName}") {
        fun createRoute(sessionId: Int, token: String, livekitUrl: String, victimName: String? = null) = 
            "active_call/$sessionId/$token/${android.util.Base64.encodeToString(livekitUrl.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)}${if (victimName != null) "?victimName=$victimName" else ""}"
    }
    
    object ActiveCallTransition : Screen("active_call_transition/{sessionId}?victimName={victimName}") {
        fun createRoute(sessionId: Int, victimName: String? = null) = "active_call_transition/$sessionId${if (victimName != null) "?victimName=$victimName" else ""}"
    }
}
