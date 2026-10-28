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
}
