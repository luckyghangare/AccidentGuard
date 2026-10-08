package com.accidentguard.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.accidents.AccidentHistoryScreen
import com.accidentguard.ui.contacts.EmergencyContactsScreen
import com.accidentguard.ui.crash.CrashAlertScreen
import com.accidentguard.ui.dashboard.DashboardScreen
import com.accidentguard.ui.hospital.HospitalDashboardScreen
import com.accidentguard.ui.location.LiveLocationScreen
import com.accidentguard.ui.login.LoginScreen
import com.accidentguard.ui.register.RegisterScreen
import com.accidentguard.ui.settings.SettingsScreen
import com.accidentguard.ui.splash.SplashScreen
import com.accidentguard.ui.vehicle.VehicleMonitoringScreen
import com.accidentguard.viewmodel.*

@Composable
fun AccidentGuardNavGraph(navController: NavHostController) {
    val context = LocalContext.current
    val tokenManager = TokenManager(context)
    val hasToken = tokenManager.isLoggedIn()
    val isHospital = tokenManager.isHospital()

    val startDest = if (hasToken) {
        if (isHospital) Screen.HospitalDashboard.route else Screen.Dashboard.route
    } else {
        Screen.Splash.route
    }

    // Shared ViewModels
    val authViewModel: AuthViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()
    val vehicleViewModel: VehicleViewModel = viewModel()
    val vehicleMgmtViewModel: VehicleManagementViewModel = viewModel()
    val locationViewModel: LocationViewModel = viewModel()
    val crashViewModel: CrashViewModel = viewModel()
    val accidentViewModel: AccidentViewModel = viewModel()
    val contactViewModel: ContactViewModel = viewModel()
    val hospitalViewModel: HospitalViewModel = viewModel()

    NavHost(navController = navController, startDestination = startDest) {

        composable(Screen.Splash.route) {
            SplashScreen(onSplashFinished = {
                val destination = if (tokenManager.isLoggedIn()) {
                    if (tokenManager.isHospital()) Screen.HospitalDashboard.route else Screen.Dashboard.route
                } else {
                    Screen.Login.route
                }
                navController.navigate(destination) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onLoginSuccess = { role ->
                    if (role == "hospital") {
                        hospitalViewModel.loadDashboard()
                        navController.navigate(Screen.HospitalDashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    } else {
                        // Refresh all user-isolated data for the newly logged-in user
                        vehicleMgmtViewModel.fetchVehicles()
                        dashboardViewModel.refresh()
                        locationViewModel.fetchLiveLocation()
                        vehicleViewModel.startRealtimeMonitoring()
                        accidentViewModel.fetchAccidentHistory()
                        contactViewModel.fetchContacts()
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel = authViewModel,
                onRegisterSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        // ── Regular Vehicle User Dashboard ──
        composable(Screen.Dashboard.route) {
            DashboardScreen(navController = navController, viewModel = dashboardViewModel)
        }

        // ── Hospital Authority Emergency Dashboard ──
        composable(Screen.HospitalDashboard.route) {
            HospitalDashboardScreen(navController = navController, viewModel = hospitalViewModel)
        }

        composable(Screen.VehicleMonitoring.route) {
            VehicleMonitoringScreen(navController = navController, viewModel = vehicleViewModel)
        }

        composable(Screen.LiveLocation.route) {
            LiveLocationScreen(navController = navController, viewModel = locationViewModel)
        }

        composable(Screen.CrashAlert.route) {
            CrashAlertScreen(navController = navController, viewModel = crashViewModel)
        }

        composable(Screen.AccidentHistory.route) {
            AccidentHistoryScreen(navController = navController, viewModel = accidentViewModel)
        }

        composable(Screen.EmergencyContacts.route) {
            EmergencyContactsScreen(navController = navController, viewModel = contactViewModel)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                tokenManager = tokenManager,
                vehicleViewModel = vehicleMgmtViewModel
            )
        }
    }
}
