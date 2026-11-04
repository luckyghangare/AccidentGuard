
package com.accidentguard.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import com.accidentguard.data.local.TokenManager
import com.accidentguard.ui.accidents.AccidentHistoryScreen
import com.accidentguard.ui.call.ActiveCallScreen
import com.accidentguard.ui.call.CallUiState
import com.accidentguard.ui.call.CallViewModel
import com.accidentguard.ui.call.IncomingCallScreen
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
import com.accidentguard.ui.security.BiometricLockScreen
import com.accidentguard.ui.settings.SecuritySettingsScreen
import com.accidentguard.ui.settings.SettingsScreen
import com.accidentguard.ui.splash.SplashScreen
import com.accidentguard.ui.vehicle.VehicleMonitoringScreen
import com.accidentguard.viewmodel.*

@Composable
fun AccidentGuardNavGraph(navController: NavHostController) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val tokenManager = remember {
        TokenManager(context)
    }

    val locationViewModel: LocationViewModel = viewModel()

    val locationState by locationViewModel.uiState.collectAsStateWithLifecycle()

    // Recheck permissions and GPS whenever the app resumes.
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

    var isAppUnlocked by remember {
        mutableStateOf(!isBiometricEnabled)
    }

    // Automatically navigate after permission/GPS requirements are satisfied.
    LaunchedEffect(
        locationState.hasPermission,
        locationState.hasAudioPermission,
        locationState.isGpsEnabled,
        hasToken
    ) {

        if (!hasToken) return@LaunchedEffect

        val currentRoute =
            navController.currentBackStackEntry?.destination?.route

        if (
            locationState.hasPermission &&
            locationState.hasAudioPermission
        ) {

            if (currentRoute == Screen.PermissionRequired.route) {

                if (locationState.isGpsEnabled) {

                    navController.navigate(
                        if (isHospital) {
                            Screen.HospitalDashboard.route
                        } else {
                            Screen.Dashboard.route
                        }
                    ) {
                        popUpTo(Screen.PermissionRequired.route) {
                            inclusive = true
                        }
                    }

                } else {

                    navController.navigate(Screen.GpsRequired.route) {
                        popUpTo(Screen.PermissionRequired.route) {
                            inclusive = true
                        }
                    }
                }

            } else if (
                currentRoute == Screen.GpsRequired.route &&
                locationState.isGpsEnabled
            ) {

                navController.navigate(
                    if (isHospital) {
                        Screen.HospitalDashboard.route
                    } else {
                        Screen.Dashboard.route
                    }
                ) {
                    popUpTo(Screen.GpsRequired.route) {
                        inclusive = true
                    }
                }
            }
        }
    }

    // Determine initial destination.
    val startDest = if (hasToken) {

        when {

            isBiometricEnabled && !isAppUnlocked ->
                Screen.BiometricLock.route

            !locationState.hasPermission ||
                    !locationState.hasAudioPermission ->
                Screen.PermissionRequired.route

            !locationState.isGpsEnabled ->
                Screen.GpsRequired.route

            isHospital ->
                Screen.HospitalDashboard.route

            else ->
                Screen.Dashboard.route
        }

    } else {
        Screen.Splash.route
    }

    NavHost(
        navController = navController,
        startDestination = startDest
    ) {

        // ---------------------------------------------------------
        // SPLASH
        // ---------------------------------------------------------

        composable(Screen.Splash.route) {

            SplashScreen(
                onSplashFinished = {

                    val destination =
                        if (tokenManager.isLoggedIn()) {

                            when {

                                isBiometricEnabled ->
                                    Screen.BiometricLock.route

                                !locationState.hasPermission ||
                                        !locationState.hasAudioPermission ->
                                    Screen.PermissionRequired.route

                                !locationState.isGpsEnabled ->
                                    Screen.GpsRequired.route

                                tokenManager.isHospital() ->
                                    Screen.HospitalDashboard.route

                                else ->
                                    Screen.Dashboard.route
                            }

                        } else {
                            Screen.Login.route
                        }

                    navController.navigate(destination) {
                        popUpTo(Screen.Splash.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // ---------------------------------------------------------
        // BIOMETRIC LOCK
        // ---------------------------------------------------------

        composable(Screen.BiometricLock.route) {

            BiometricLockScreen(

                onSuccess = {

                    isAppUnlocked = true

                    val destination =
                        when {

                            !locationState.hasPermission ||
                                    !locationState.hasAudioPermission ->
                                Screen.PermissionRequired.route

                            !locationState.isGpsEnabled ->
                                Screen.GpsRequired.route

                            isHospital ->
                                Screen.HospitalDashboard.route

                            else ->
                                Screen.Dashboard.route
                        }

                    navController.navigate(destination) {
                        popUpTo(Screen.BiometricLock.route) {
                            inclusive = true
                        }
                    }
                },

                onLogout = {

                    tokenManager.clear()

                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.BiometricLock.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // ---------------------------------------------------------
        // PERMISSION REQUIRED
        // ---------------------------------------------------------

        composable(Screen.PermissionRequired.route) {

            PermissionRequiredScreen(

                onPermissionGranted = {

                    locationViewModel.checkRequirements()

                    val nextDestination =
                        if (!locationViewModel.uiState.value.isGpsEnabled) {
                            Screen.GpsRequired.route
                        } else if (isHospital) {
                            Screen.HospitalDashboard.route
                        } else {
                            Screen.Dashboard.route
                        }

                    navController.navigate(nextDestination) {
                        popUpTo(Screen.PermissionRequired.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        // ---------------------------------------------------------
        // GPS REQUIRED
        // ---------------------------------------------------------

        composable(Screen.GpsRequired.route) {
            GpsRequiredScreen()
        }

        // ---------------------------------------------------------
        // LOGIN
        // ---------------------------------------------------------

        composable(Screen.Login.route) {

            LoginScreen(
                viewModel = viewModel(),

                onLoginSuccess = { role ->

                    isAppUnlocked = true

                    navController.navigate(
                        if (role == "hospital") {
                            Screen.HospitalDashboard.route
                        } else {
                            Screen.Dashboard.route
                        }
                    ) {
                        popUpTo(Screen.Login.route) {
                            inclusive = true
                        }
                    }
                },

                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },

                onNavigateTo2FA = { email, role ->

                    navController.navigate(
                        Screen.OtpVerification.createRoute(email, role)
                    )
                }
            )
        }

        // ---------------------------------------------------------
        // OTP VERIFICATION
        // ---------------------------------------------------------

        composable(
            route = Screen.OtpVerification.route,
            arguments = listOf(
                navArgument("email") {
                    type = NavType.StringType
                },
                navArgument("role") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->

            val email =
                backStackEntry.arguments?.getString("email") ?: ""

            val role =
                backStackEntry.arguments?.getString("role") ?: ""

            OtpVerificationScreen(
                email = email,
                role = role,
                viewModel = viewModel(),
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // DASHBOARD AND OTHER MAIN SCREENS
        // ---------------------------------------------------------

        composable(Screen.Dashboard.route) {
            DashboardScreen(navController, viewModel())
        }

        composable(Screen.HospitalDashboard.route) {
            HospitalDashboardScreen(navController, viewModel())
        }

        composable(Screen.LiveLocation.route) {
            LiveLocationScreen(navController, viewModel())
        }

        composable(Screen.SecuritySettings.route) {
            SecuritySettingsScreen(navController, tokenManager)
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                viewModel(),
                {},
                {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController, tokenManager, viewModel())
        }

        composable(Screen.EmergencyContacts.route) {
            EmergencyContactsScreen(navController, viewModel())
        }

        composable(Screen.AccidentHistory.route) {
            AccidentHistoryScreen(navController, viewModel())
        }

        composable(Screen.VehicleMonitoring.route) {
            VehicleMonitoringScreen(navController, viewModel())
        }

        composable(Screen.CrashAlert.route) {
            CrashAlertScreen(navController, viewModel())
        }

        // ---------------------------------------------------------
        // INCOMING EMERGENCY CALL
        // ---------------------------------------------------------

        composable(
            route = Screen.IncomingCall.route,
            arguments = listOf(

                navArgument("sessionId") {
                    type = NavType.IntType
                },

                navArgument("autoAccept") {
                    type = NavType.BoolType
                    defaultValue = false
                },

                navArgument("victimName") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->

            val sessionId =
                backStackEntry.arguments?.getInt("sessionId") ?: 0

            val autoAccept =
                backStackEntry.arguments?.getBoolean("autoAccept") ?: false

            val victimName =
                backStackEntry.arguments?.getString("victimName")

            val factory = remember {
                ViewModelFactory(context)
            }

            val callViewModel: CallViewModel =
                viewModel(factory = factory)

            IncomingCallScreen(
                sessionId = sessionId,
                autoAccept = autoAccept,
                victimName = victimName,
                viewModel = callViewModel,

                onNavigateToActiveCall = { sid, token, url ->

                    navController.navigate(
                        Screen.ActiveCall.createRoute(
                            sid,
                            token,
                            url,
                            victimName
                        )
                    ) {
                        popUpTo(Screen.IncomingCall.route) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                },

                onDismiss = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // ACTIVE CALL
        // ---------------------------------------------------------

        composable(
            route = Screen.ActiveCall.route,
            arguments = listOf(

                navArgument("sessionId") {
                    type = NavType.IntType
                },

                navArgument("token") {
                    type = NavType.StringType
                },

                navArgument("livekitUrl") {
                    type = NavType.StringType
                },

                navArgument("victimName") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->

            val sessionId =
                backStackEntry.arguments?.getInt("sessionId") ?: 0

            val token =
                backStackEntry.arguments?.getString("token") ?: ""

            val encodedUrl =
                backStackEntry.arguments?.getString("livekitUrl") ?: ""

            val victimName =
                backStackEntry.arguments?.getString("victimName")

            // Decode the URL encoded by Screen.ActiveCall.createRoute().
            val livekitUrl = try {

                String(
                    android.util.Base64.decode(
                        encodedUrl,
                        android.util.Base64.URL_SAFE or
                                android.util.Base64.NO_WRAP
                    ),
                    Charsets.UTF_8
                )

            } catch (exception: Exception) {
                ""
            }

            val factory = remember {
                ViewModelFactory(context)
            }

            val callViewModel: CallViewModel =
                viewModel(factory = factory)

            ActiveCallScreen(
                sessionId = sessionId,
                token = token,
                livekitUrl = livekitUrl,
                victimName = victimName,
                viewModel = callViewModel,

                onCallEnded = {
                    navController.popBackStack()
                }
            )
        }

        // ---------------------------------------------------------
        // ACTIVE CALL TRANSITION
        // ---------------------------------------------------------

        composable(
            route = Screen.ActiveCallTransition.route,
            arguments = listOf(

                navArgument("sessionId") {
                    type = NavType.IntType
                },

                navArgument("victimName") {
                    type = NavType.StringType
                    nullable = true
                }
            )
        ) { backStackEntry ->

            val sessionId =
                backStackEntry.arguments?.getInt("sessionId") ?: 0

            val victimName =
                backStackEntry.arguments?.getString("victimName")

            val factory = remember {
                ViewModelFactory(context)
            }

            val callViewModel: CallViewModel =
                viewModel(factory = factory)

            val uiState by
            callViewModel.uiState.collectAsStateWithLifecycle()

            // Step 1: Fetch only the LiveKit token.
            // Do NOT start the CallService from this screen.
            LaunchedEffect(sessionId) {

                val authToken =
                    tokenManager.getAuthToken() ?: ""

                if (authToken.isBlank()) {
                    return@LaunchedEffect
                }

                callViewModel.fetchCallToken(
                    sessionId = sessionId,
                    authToken = authToken
                )
            }

            // Step 2: Navigate when the token is ready.
            // ActiveCallScreen will handle joinCall().
            LaunchedEffect(uiState) {

                if (uiState is CallUiState.TokenReady) {

                    val ready =
                        uiState as CallUiState.TokenReady

                    if (
                        ready.sessionId != sessionId ||
                        ready.token.isBlank() ||
                        ready.livekitUrl.isBlank()
                    ) {
                        return@LaunchedEffect
                    }

                    navController.navigate(
                        Screen.ActiveCall.createRoute(
                            ready.sessionId,
                            ready.token,
                            ready.livekitUrl,
                            victimName
                        )
                    ) {

                        popUpTo(
                            Screen.ActiveCallTransition.route
                        ) {
                            inclusive = true
                        }

                        launchSingleTop = true
                    }
                }
            }

            // Step 3: Loading / error UI.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {

                when (val state = uiState) {

                    is CallUiState.Error -> {

                        androidx.compose.material3.Text(
                            text = state.message
                        )
                    }

                    else -> {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
