package com.accidentguard.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.accidents.AccidentHistoryScreen
import com.accidentguard.ui.contacts.EmergencyContactsScreen
import com.accidentguard.ui.crash.CrashAlertScreen
import com.accidentguard.ui.dashboard.DashboardScreen
import com.accidentguard.ui.hospital.HospitalDashboardScreen
import com.accidentguard.ui.location.GpsRequiredScreen
import com.accidentguard.ui.location.LiveLocationScreen
import com.accidentguard.ui.location.PermissionRequiredScreen
import com.accidentguard.ui.login.LoginScreen
import com.accidentguard.ui.login.OtpVerificationScreen
import com.accidentguard.ui.register.RegisterScreen
import com.accidentguard.ui.settings.SettingsScreen
import com.accidentguard.ui.settings.SecuritySettingsScreen
import com.accidentguard.ui.security.BiometricLockScreen
import com.accidentguard.ui.splash.SplashScreen
import com.accidentguard.ui.vehicle.VehicleMonitoringScreen
import com.accidentguard.viewmodel.*
import com.accidentguard.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@Composable
fun AccidentGuardNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokenManager = remember { TokenManager(context) }
    val locationViewModel: LocationViewModel = viewModel()
    val locationState by locationViewModel.uiState.collectAsStateWithLifecycle()

    // Re-check permissions and GPS whenever user returns to the app from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationViewModel.checkRequirements()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasToken = tokenManager.isLoggedIn()
    val isHospital = tokenManager.isHospital()
    val isBiometricEnabled = tokenManager.isBiometricEnabled()
    
    // Local state to track if app is unlocked via biometric during this session
    var isAppUnlocked by remember { mutableStateOf(!isBiometricEnabled) }

    // Auto-navigate when requirements (Permission/GPS) are met while on blocker screens
    LaunchedEffect(locationState.hasPermission, locationState.isGpsEnabled, hasToken) {
        if (hasToken) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (locationState.hasPermission) {
                if (currentRoute == Screen.PermissionRequired.route) {
                    if (locationState.isGpsEnabled) {
                        navController.navigate(if (isHospital) Screen.HospitalDashboard.route else Screen.Dashboard.route) {
                            popUpTo(Screen.PermissionRequired.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Screen.GpsRequired.route) {
                            popUpTo(Screen.PermissionRequired.route) { inclusive = true }
                        }
                    }
                } else if (currentRoute == Screen.GpsRequired.route && locationState.isGpsEnabled) {
                    navController.navigate(if (isHospital) Screen.HospitalDashboard.route else Screen.Dashboard.route) {
                        popUpTo(Screen.GpsRequired.route) { inclusive = true }
                    }
                }
            }
        }
    }

    // Determine destination
    val startDest = if (hasToken) {
        when {
            isBiometricEnabled && !isAppUnlocked -> Screen.BiometricLock.route
            !locationState.hasPermission -> Screen.PermissionRequired.route
            !locationState.isGpsEnabled -> Screen.GpsRequired.route
            isHospital -> Screen.HospitalDashboard.route
            else -> Screen.Dashboard.route
        }
    } else {
        Screen.Splash.route
    }

    NavHost(navController = navController, startDestination = startDest) {

        composable(Screen.Splash.route) {
            SplashScreen(onSplashFinished = {
                val destination = if (tokenManager.isLoggedIn()) {
                    if (isBiometricEnabled) Screen.BiometricLock.route
                    else if (!locationState.hasPermission) Screen.PermissionRequired.route
                    else if (!locationState.isGpsEnabled) Screen.GpsRequired.route
                    else if (tokenManager.isHospital()) Screen.HospitalDashboard.route 
                    else Screen.Dashboard.route
                } else {
                    Screen.Login.route
                }
                navController.navigate(destination) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.BiometricLock.route) {
            BiometricLockScreen(
                onSuccess = {
                    isAppUnlocked = true
                    val dest = if (!locationState.hasPermission) Screen.PermissionRequired.route
                              else if (!locationState.isGpsEnabled) Screen.GpsRequired.route
                              else if (isHospital) Screen.HospitalDashboard.route else Screen.Dashboard.route
                    navController.navigate(dest) {
                        popUpTo(Screen.BiometricLock.route) { inclusive = true }
                    }
                },
                onLogout = {
                    tokenManager.clear()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.PermissionRequired.route) {
            PermissionRequiredScreen(onPermissionGranted = {
                locationViewModel.checkRequirements()
                val nextDest = if (!locationViewModel.uiState.value.isGpsEnabled) Screen.GpsRequired.route
                              else if (isHospital) Screen.HospitalDashboard.route else Screen.Dashboard.route
                navController.navigate(nextDest) {
                    popUpTo(Screen.PermissionRequired.route) { inclusive = true }
                }
            })
        }

        composable(Screen.GpsRequired.route) {
            GpsRequiredScreen()
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = viewModel(),
                onLoginSuccess = { role ->
                    isAppUnlocked = true
                    navController.navigate(if (role == "hospital") Screen.HospitalDashboard.route else Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onNavigateTo2FA = { email, role ->
                    navController.navigate(Screen.OtpVerification.createRoute(email, role))
                }
            )
        }

        composable(
            route = Screen.OtpVerification.route,
            arguments = listOf(
                navArgument("email") { type = NavType.StringType },
                navArgument("role") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            val role = backStackEntry.arguments?.getString("role") ?: ""
            OtpVerificationScreen(
                email = email,
                role = role,
                viewModel = viewModel(),
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Dashboard.route) { DashboardScreen(navController, viewModel()) }
        composable(Screen.HospitalDashboard.route) { HospitalDashboardScreen(navController, viewModel()) }
        composable(Screen.LiveLocation.route) { LiveLocationScreen(navController, viewModel()) }
        composable(Screen.SecuritySettings.route) { SecuritySettingsScreen(navController, tokenManager) }
        
        // Preserve other routes
        composable(Screen.Register.route) { RegisterScreen(viewModel(), {}, { navController.popBackStack() }) }
        composable(Screen.Settings.route) { SettingsScreen(navController, tokenManager, viewModel()) }
        composable(Screen.EmergencyContacts.route) { EmergencyContactsScreen(navController, viewModel()) }
        composable(Screen.AccidentHistory.route) { AccidentHistoryScreen(navController, viewModel()) }
        composable(Screen.VehicleMonitoring.route) { VehicleMonitoringScreen(navController, viewModel()) }
        composable(Screen.CrashAlert.route) { CrashAlertScreen(navController, viewModel()) }
    }
}
