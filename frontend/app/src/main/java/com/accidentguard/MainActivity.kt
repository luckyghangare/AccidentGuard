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
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import com.accidentguard.data.service.AccidentGuardMessagingService
import com.accidentguard.ui.navigation.AccidentGuardNavGraph
import com.accidentguard.ui.navigation.Screen
import com.accidentguard.ui.theme.AccidentGuardTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.w("MainActivity", "Notification permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        createNotificationChannel()
        askNotificationPermission()
        
        // Log FCM Token for manual console testing
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d("MainActivity", "Current FCM Token: $token")
        }

        setContent {
            AccidentGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    LaunchedEffect(intent) {
                        handleIntent(intent, navController)
                    }
                    
                    AccidentGuardNavGraph(navController = navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun handleIntent(intent: Intent?, navController: NavHostController) {
        intent?.let {
            val navigateTo = it.getStringExtra("navigate_to")
            val sessionId = it.getIntExtra("session_id", -1)
            val autoAccept = it.getBooleanExtra("auto_accept", false)
            val victimName = it.getStringExtra("victim_name")
            
            if (navigateTo == "incoming_call" && sessionId != -1) {
                // Remove the extra to prevent re-processing on recomposition if LaunchedEffect(intent) re-runs
                it.removeExtra("navigate_to")

                navController.navigate(Screen.IncomingCall.createRoute(sessionId, autoAccept, victimName)) {
                    popUpTo(Screen.Dashboard.route) { saveState = true }
                    launchSingleTop = true
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Accident Alerts"
            val descriptionText = "Notifications for crash detection and emergency responses"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(AccidentGuardMessagingService.CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is already granted
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
