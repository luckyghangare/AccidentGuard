
package com.accidentguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.accidentguard.data.service.AccidentGuardMessagingService
import com.accidentguard.ui.navigation.AccidentGuardNavGraph
import com.accidentguard.ui.navigation.Screen
import com.accidentguard.ui.theme.AccidentGuardTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val EXTRA_NAVIGATE_TO = "navigate_to"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_AUTO_ACCEPT = "auto_accept"
        private const val EXTRA_VICTIM_NAME = "victim_name"
    }

    // Delivers both initial and new notification intents to Compose.
    private val activityIntentFlow = MutableStateFlow<Intent?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d(TAG, "Notification permission granted")
            } else {
                Log.w(TAG, "Notification permission denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activityIntentFlow.value = intent

        createNotificationChannel()
        askNotificationPermission()
        logFcmToken()

        setContent {
            AccidentGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val latestIntent by activityIntentFlow.collectAsState()

                    LaunchedEffect(latestIntent) {
                        val newIntent = latestIntent ?: return@LaunchedEffect

                        // Consume before navigation to prevent duplicate handling.
                        activityIntentFlow.value = null

                        handleIntent(newIntent, navController)
                    }

                    AccidentGuardNavGraph(
                        navController = navController
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        setIntent(intent)
        Log.d(TAG, "New intent received: ${intent.action}")

        activityIntentFlow.value = intent
    }


    private fun handleIntent(
        intent: Intent?,
        navController: NavHostController
    ) {
        if (intent == null) return

        val navigateTo = intent.getStringExtra(EXTRA_NAVIGATE_TO)
        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val autoAccept = intent.getBooleanExtra(EXTRA_AUTO_ACCEPT, false)
        val victimName = intent.getStringExtra(EXTRA_VICTIM_NAME)

        Log.d(
            TAG,
            "Handling intent: destination=$navigateTo, " +
                    "sessionId=$sessionId, autoAccept=$autoAccept"
        )

        if (sessionId <= 0) {
            Log.w(TAG, "Invalid session ID: $sessionId")
            return
        }

        val currentEntry = navController.currentBackStackEntry
        val currentRoute = currentEntry?.destination?.route
        val currentSessionId =
            currentEntry?.arguments?.getInt("sessionId", -1) ?: -1

        when (navigateTo) {

            "incoming_call" -> {

                if (
                    currentRoute?.startsWith("incoming_call/") == true &&
                    currentSessionId == sessionId
                ) {
                    Log.d(TAG, "Incoming call already open")
                    return
                }

                try {
                    navController.navigate(
                        Screen.IncomingCall.createRoute(
                            sessionId,
                            autoAccept,
                            victimName
                        )
                    ) {
                        launchSingleTop = true
                    }

                    Log.d(TAG, "Navigated to incoming call: $sessionId")

                } catch (e: Exception) {
                    Log.e(TAG, "Incoming call navigation failed", e)
                }
            }

            "active_call" -> {

                if (
                    (
                            currentRoute?.startsWith("active_call/") == true ||
                                    currentRoute?.startsWith("active_call_transition/") == true
                            ) &&
                    currentSessionId == sessionId
                ) {
                    Log.d(TAG, "Active call already open")
                    return
                }

                try {
                    navController.navigate(
                        Screen.ActiveCallTransition.createRoute(
                            sessionId,
                            victimName
                        )
                    ) {
                        launchSingleTop = true
                    }

                    Log.d(
                        TAG,
                        "Navigated to active call transition: $sessionId"
                    )

                } catch (e: Exception) {
                    Log.e(TAG, "Active call navigation failed", e)
                }
            }

            else -> {
                Log.w(TAG, "Unknown notification destination: $navigateTo")
            }
        }
    }


    private fun logFcmToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->

                if (!task.isSuccessful) {
                    Log.w(
                        TAG,
                        "Fetching FCM registration token failed",
                        task.exception
                    )
                    return@addOnCompleteListener
                }

                // Do not print production FCM tokens in logs.
                Log.d(TAG, "FCM registration token fetched successfully")
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                AccidentGuardMessagingService.CHANNEL_ID,
                "Accident Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "Notifications for crash detection and emergency responses"
            }

            val notificationManager =
                getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permission = Manifest.permission.POST_NOTIFICATIONS

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Notification permission already granted")
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
    }
}
