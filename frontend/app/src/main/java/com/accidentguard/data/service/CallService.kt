package com.accidentguard.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accidentguard.MainActivity
import com.accidentguard.R
import com.accidentguard.data.local.TokenManager
import com.accidentguard.data.manager.LiveKitConnectionState
import com.accidentguard.data.manager.LiveKitManager
import com.accidentguard.data.repository.CallRepository
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.*

class CallService : Service() {

    private val binder = CallServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private lateinit var liveKitManager: LiveKitManager
    private var currentSessionId: Int = -1
    private var victimName: String = "Emergency Contact"
    
    // Injected manually or via Lazy for now, ideally use Hilt if added later
    private val tokenManager by lazy { TokenManager(applicationContext) }
    private val repository by lazy { CallRepository(com.accidentguard.data.api.RetrofitClient.apiService) }

    companion object {
        const val NOTIFICATION_ID = 999
        const val ACTION_START_CALL = "START_CALL"
        const val ACTION_STOP_SERVICE = "STOP_CALL_SERVICE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_URL = "url"
        const val EXTRA_VICTIM_NAME = "victim_name"

        const val ACTION_TOGGLE_SPEAKER = "TOGGLE_SPEAKER"
        const val ACTION_TOGGLE_MUTE = "TOGGLE_MUTE"

        fun startService(context: Context, sessionId: Int, token: String, url: String, victimName: String) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_TOKEN, token)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_VICTIM_NAME, victimName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        liveKitManager = LiveKitManager(applicationContext)

        // Observe mic/speaker changes to update notification
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                liveKitManager.isMicEnabled,
                liveKitManager.isSpeakerphoneEnabled
            ) { _, _ -> }.collect {
                if (currentSessionId != -1) {
                    startCallForeground(victimName)
                }
            }
        }
    }

    inner class CallServiceBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // System restarted the service due to START_STICKY
            restoreCall()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_STOP_SERVICE -> {
                handleStopService()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE_SPEAKER -> {
                val currentSpeaker = liveKitManager.isSpeakerphoneEnabled.value
                liveKitManager.toggleSpeakerphone(!currentSpeaker)
                // Notification will be updated via flow collector in onCreate
                return START_STICKY
            }
            ACTION_TOGGLE_MUTE -> {
                val currentMic = liveKitManager.isMicEnabled.value
                liveKitManager.enableMicrophone(!currentMic)
                // Notification will be updated via flow collector in onCreate
                return START_STICKY
            }
            ACTION_START_CALL -> {
                val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
                val token = intent.getStringExtra(EXTRA_TOKEN)
                val url = intent.getStringExtra(EXTRA_URL)
                val victim = intent.getStringExtra(EXTRA_VICTIM_NAME) ?: "Emergency Contact"
                
                if (sessionId != -1) {
                    // Idempotency check
                    val currentState = liveKitManager.connectionState.value
                    if (currentSessionId == sessionId && 
                        (currentState is LiveKitConnectionState.Connected || 
                         currentState is LiveKitConnectionState.Connecting ||
                         currentState is LiveKitConnectionState.Reconnecting ||
                         currentState is LiveKitConnectionState.InCall)) {
                        return START_STICKY
                    }

                    currentSessionId = sessionId
                    victimName = victim
                    
                    serviceScope.launch {
                        var finalToken = token
                        var finalUrl = url

                        // Fallback Flow: If token or URL is missing, fetch them
                        if (finalToken == null || finalUrl == null) {
                            Log.d("CallService", "Token or URL missing, fetching from backend for session $sessionId")
                            val authToken = tokenManager.getAuthToken()
                            if (authToken != null) {
                                try {
                                    val response = repository.getCallToken("Bearer $authToken", sessionId)
                                    if (response.isSuccessful && response.body() != null) {
                                        finalToken = response.body()?.token
                                        finalUrl = response.body()?.livekitUrl
                                    }
                                } catch (e: Exception) {
                                    Log.e("CallService", "Failed to fetch fallback token", e)
                                }
                            }
                        }

                        if (finalToken != null && finalUrl != null) {
                            persistCallData(sessionId, victimName, finalUrl, finalToken)
                            startCallForeground(victimName)
                            liveKitManager.connect(finalUrl, finalToken, isVictim = true)
                        } else {
                            Log.e("CallService", "Cannot start call: Token or URL acquisition failed")
                            handleStopService()
                        }
                    }
                }
            }
        }
        
        return START_STICKY
    }

    private fun handleStopService() {
        clearCallData()
        serviceScope.launch {
            liveKitManager.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun getLiveKitManager() = liveKitManager

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startCallForeground(victimName: String) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("navigate_to", "active_call")
            putExtra("session_id", currentSessionId)
            putExtra("victim_name", victimName)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, currentSessionId, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // End Call Action
        val endCallIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_END_CALL
            putExtra(CallActionReceiver.EXTRA_SESSION_ID, currentSessionId)
        }
        val endCallPendingIntent = PendingIntent.getBroadcast(
            this, currentSessionId + 1000, endCallIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Speaker Toggle Action
        val speakerIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_TOGGLE_SPEAKER
        }
        val speakerPendingIntent = PendingIntent.getService(
            this, currentSessionId + 2000, speakerIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isSpeakerOn = liveKitManager.isSpeakerphoneEnabled.value
        val isMicOn = liveKitManager.isMicEnabled.value

        // Mute Toggle Action
        val muteIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this, currentSessionId + 3000, muteIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationHelper.CALL_CHANNEL_ID)
            .setContentTitle("Active Emergency Call")
            .setContentText("Talking with $victimName")
            .setSmallIcon(R.drawable.ic_emergency)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .addAction(
                if (isMicOn) R.drawable.ic_mic else R.drawable.ic_mic_off,
                if (isMicOn) "Mute" else "Unmute",
                mutePendingIntent
            )
            .addAction(
                if (isSpeakerOn) R.drawable.ic_volume_up else R.drawable.ic_volume_down,
                if (isSpeakerOn) "Speaker Off" else "Speaker On",
                speakerPendingIntent
            )
            .addAction(R.drawable.ic_call_end, "End Call", endCallPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true) // Prevent sound on every update
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun persistCallData(sessionId: Int, victimName: String, url: String, token: String) {
        getSharedPreferences("call_service_prefs", Context.MODE_PRIVATE).edit().apply {
            putInt("last_session_id", sessionId)
            putString("last_victim_name", victimName)
            putString("last_url", url)
            putString("last_token", token)
            apply()
        }
    }

    private fun restoreCall() {
        val prefs = getSharedPreferences("call_service_prefs", Context.MODE_PRIVATE)
        val sessionId = prefs.getInt("last_session_id", -1)
        val victim = prefs.getString("last_victim_name", "Emergency Contact") ?: "Emergency Contact"
        val url = prefs.getString("last_url", null)
        val token = prefs.getString("last_token", null)
        
        if (sessionId != -1 && url != null && token != null) {
            currentSessionId = sessionId
            victimName = victim
            startCallForeground(victimName)
            
            serviceScope.launch {
                try {
                    // Check if session is still active on backend before attempting reconnect
                    val authToken = tokenManager.getAuthToken()
                    var shouldConnect = true
                    
                    if (authToken != null) {
                        val response = repository.getCallDetails("Bearer $authToken", sessionId)
                        val session = response.body()
                        if (response.isSuccessful && session != null) {
                            if (session.status == "ENDED" || session.status == "CANCELLED" || session.status == "EXPIRED") {
                                Log.w("CallService", "Restoration aborted: session is in state ${session.status}")
                                shouldConnect = false
                                handleStopService()
                            }
                        }
                    }
                    
                    if (shouldConnect) {
                        liveKitManager.connect(url, token)
                    }
                } catch (e: Exception) {
                    Log.e("CallService", "Failed to verify session during restoration: ${e.message}")
                    // Fallback to connection attempt if network error
                    liveKitManager.connect(url, token)
                }
            }
        } else {
            stopSelf()
        }
    }

    private fun clearCallData() {
        getSharedPreferences("call_service_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
