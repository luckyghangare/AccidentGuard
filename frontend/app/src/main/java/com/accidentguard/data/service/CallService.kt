package com.accidentguard.data.service

import android.app.Service
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
import com.accidentguard.data.manager.LiveKitConnectionFailure
import com.accidentguard.data.manager.LiveKitConnectionState
import com.accidentguard.data.manager.LiveKitManager
import com.accidentguard.data.repository.CallRepository
import com.accidentguard.ui.notification.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class CallService : Service() {

    companion object {
        private const val TAG = "CallService"

        const val NOTIFICATION_ID = 999

        const val ACTION_START_CALL = "START_CALL"
        const val ACTION_STOP_SERVICE = "STOP_CALL_SERVICE"

        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_URL = "url"
        const val EXTRA_VICTIM_NAME = "victim_name"

        const val ACTION_TOGGLE_SPEAKER = "TOGGLE_SPEAKER"
        const val ACTION_TOGGLE_MUTE = "TOGGLE_MUTE"

        private const val PREFS_NAME = "call_service_prefs"

        private const val KEY_SESSION_ID = "last_session_id"
        private const val KEY_VICTIM_NAME = "last_victim_name"
        private const val KEY_URL = "last_url"
        private const val KEY_TOKEN = "last_token"

        // Legacy key retained for migration/compatibility.
        private const val KEY_TERMINAL_SESSION_ID = "terminal_session_id"
        private const val KEY_TERMINAL_SESSION_IDS = "terminal_session_ids"

        private const val DISCONNECT_TIMEOUT = 5000L

        private val TERMINAL_STATUSES = setOf(
            "ENDED",
            "COMPLETED",
            "CANCELLED",
            "CANCELED",
            "EXPIRED",
            "FAILED",
            "REJECTED",
            "DECLINED",
            "ALL_CONTACTS_UNREACHABLE",
            "TERMINATED"
        )

        fun startService(
            context: Context,
            sessionId: Int,
            token: String,
            url: String,
            victimName: String
        ) {
            val intent = Intent(
                context,
                CallService::class.java
            ).apply {
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

        fun stopService(context: Context, sessionId: Int) {
            val intent = Intent(
                context,
                CallService::class.java
            ).apply {
                action = ACTION_STOP_SERVICE
                putExtra(EXTRA_SESSION_ID, sessionId)
            }

            context.startService(intent)
        }
    }

    private val binder = CallServiceBinder()

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Serializes actual LiveKit connect/disconnect operations.
     */
    private val lifecycleMutex = Mutex()

    private lateinit var liveKitManager: LiveKitManager

    private var currentSessionId = -1
    private var victimName = "Emergency Contact"

    private var startJob: Job? = null
    private var stopJob: Job? = null

    /**
     * Invalidates stale backend and connection operations.
     */
    private var lifecycleGeneration = 0

    private val tokenManager by lazy {
        TokenManager(applicationContext)
    }

    private val repository by lazy {
        CallRepository(
            com.accidentguard.data.api.RetrofitClient.apiService
        )
    }

    inner class CallServiceBinder : Binder() {
        fun getService(): CallService = this@CallService
    }

    override fun onCreate() {
        super.onCreate()

        liveKitManager = LiveKitManager(applicationContext)

        serviceScope.launch {
            combine(
                liveKitManager.isMicEnabled,
                liveKitManager.isSpeakerphoneEnabled
            ) { _, _ -> Unit }.collect {
                if (currentSessionId > 0) {
                    startCallForeground(victimName)
                }
            }
        }

        // Asynchronous LiveKit failures are reported separately from connect().
        serviceScope.launch {
            liveKitManager.connectionFailures.collect { failure ->
                handleLiveKitFailure(failure)
            }
        }

        Log.d(TAG, "CallService created")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent == null) {
            Log.w(TAG, "Service restarted with null intent")
            restoreCall()
            return START_NOT_STICKY
        }

        when (intent.action) {

            ACTION_START_CALL -> {
                handleStartCall(intent)
                return START_NOT_STICKY
            }

            ACTION_STOP_SERVICE -> {
                val requestedSessionId =
                    intent.getIntExtra(EXTRA_SESSION_ID, -1)

                handleStopRequest(requestedSessionId)
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_SPEAKER -> {
                if (currentSessionId > 0) {
                    liveKitManager.toggleSpeakerphone(
                        !liveKitManager.isSpeakerphoneEnabled.value
                    )
                }
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_MUTE -> {
                if (currentSessionId > 0) {
                    liveKitManager.enableMicrophone(
                        !liveKitManager.isMicEnabled.value
                    )
                }
                return START_NOT_STICKY
            }

            else -> {
                Log.w(TAG, "Unknown service action: ${intent.action}")
            }
        }

        return START_NOT_STICKY
    }

    // ---------------------------------------------------------
    // START CALL
    // ---------------------------------------------------------

    private fun handleStartCall(intent: Intent) {

        val sessionId = intent.getIntExtra(EXTRA_SESSION_ID, -1)
        val suppliedToken = intent.getStringExtra(EXTRA_TOKEN)
        val suppliedUrl = intent.getStringExtra(EXTRA_URL)

        val name = intent.getStringExtra(EXTRA_VICTIM_NAME)
            ?: "Emergency Contact"

        if (sessionId <= 0) {
            Log.e(TAG, "Invalid session ID")
            return
        }

        if (isSessionTerminalLocally(sessionId)) {
            Log.w(TAG, "Blocked locally terminal session $sessionId")
            return
        }

        if (
            currentSessionId > 0 &&
            currentSessionId != sessionId
        ) {
            Log.w(
                TAG,
                "Ignoring start for $sessionId; " +
                        "session $currentSessionId is active"
            )
            return
        }

        if (
            currentSessionId == sessionId &&
            startJob?.isActive == true
        ) {
            Log.d(TAG, "Start already running for $sessionId")
            return
        }

        val connectionState = liveKitManager.connectionState.value

        if (
            currentSessionId == sessionId &&
            (
                    connectionState is LiveKitConnectionState.Connected ||
                            connectionState is LiveKitConnectionState.Connecting ||
                            connectionState is LiveKitConnectionState.Reconnecting ||
                            connectionState is LiveKitConnectionState.InCall ||
                            connectionState is LiveKitConnectionState.WaitingForParticipant
                    )
        ) {
            Log.d(TAG, "Session $sessionId is already active in LiveKit")
            return
        }

        lifecycleGeneration++
        val generation = lifecycleGeneration

        /*
         * Do not cancel an existing Stop job.
         * The new Start waits for it before attempting LiveKit connect.
         */
        val previousStopJob = stopJob

        currentSessionId = sessionId
        victimName = name

        startCallForeground(name)

        startJob = serviceScope.launch {

            try {
                previousStopJob?.join()

                if (!isCurrentOperation(sessionId, generation)) {
                    return@launch
                }

                lifecycleMutex.withLock {
                    if (!isCurrentOperation(sessionId, generation)) {
                        return@withLock
                    }

                    startVerifiedSession(
                        sessionId = sessionId,
                        name = name,
                        suppliedToken = suppliedToken,
                        suppliedUrl = suppliedUrl,
                        generation = generation
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Start operation failed for session $sessionId",
                    e
                )

                handleRecoverableFailure(
                    sessionId,
                    generation,
                    e.message ?: "Unable to start call"
                )
            }
        }
    }

    private suspend fun startVerifiedSession(
        sessionId: Int,
        name: String,
        suppliedToken: String?,
        suppliedUrl: String?,
        generation: Int
    ) {

        if (!isCurrentOperation(sessionId, generation)) return

        val authToken = tokenManager.getAuthToken()

        if (authToken.isNullOrBlank()) {
            handleRecoverableFailure(
                sessionId,
                generation,
                "Authentication unavailable. Open the app and retry."
            )
            return
        }

        val detailsResponse = repository.getCallDetails(
            "Bearer $authToken",
            sessionId
        )

        if (!isCurrentOperation(sessionId, generation)) return

        if (!detailsResponse.isSuccessful) {
            handleRecoverableFailure(
                sessionId,
                generation,
                "Session verification failed (${detailsResponse.code()})"
            )
            return
        }

        val details = detailsResponse.body()

        if (details == null) {
            handleRecoverableFailure(
                sessionId,
                generation,
                "Backend returned an empty session response"
            )
            return
        }

        val backendStatus = details.status
            ?.trim()
            ?.uppercase()

        if (backendStatus in TERMINAL_STATUSES) {
            Log.w(
                TAG,
                "Backend confirms terminal session $sessionId: $backendStatus"
            )

            finishTerminalSessionFromStart(
                sessionId,
                generation
            )
            return
        }

        if (!isCurrentOperation(sessionId, generation)) return

        var finalToken = suppliedToken
        var finalUrl = suppliedUrl

        if (
            finalToken.isNullOrBlank() ||
            finalUrl.isNullOrBlank()
        ) {
            val tokenResponse = repository.getCallToken(
                "Bearer $authToken",
                sessionId
            )

            if (!isCurrentOperation(sessionId, generation)) return

            if (!tokenResponse.isSuccessful) {
                handleRecoverableFailure(
                    sessionId,
                    generation,
                    "Call token request failed (${tokenResponse.code()})"
                )
                return
            }

            val tokenBody = tokenResponse.body()

            if (tokenBody == null) {
                handleRecoverableFailure(
                    sessionId,
                    generation,
                    "Backend returned an empty call token response"
                )
                return
            }

            finalToken = tokenBody.token
            finalUrl = tokenBody.livekitUrl
        }

        if (
            finalToken.isNullOrBlank() ||
            finalUrl.isNullOrBlank()
        ) {
            handleRecoverableFailure(
                sessionId,
                generation,
                "LiveKit token or URL is unavailable"
            )
            return
        }

        if (!isCurrentOperation(sessionId, generation)) return

        persistCallData(
            sessionId,
            name,
            finalUrl,
            finalToken
        )

        Log.d(TAG, "Connecting LiveKit session $sessionId")

        liveKitManager.connect(
            url = finalUrl,
            token = finalToken,
            isVictim = true,
            sessionId = sessionId
        )

        if (isCurrentOperation(sessionId, generation)) {
            startCallForeground(name)
        }
    }

    private fun isCurrentOperation(
        sessionId: Int,
        generation: Int
    ): Boolean {
        return currentSessionId == sessionId &&
                lifecycleGeneration == generation
    }

    // ---------------------------------------------------------
    // RECOVERABLE FAILURE
    // ---------------------------------------------------------

    /**
     * A network/API/token failure does not prove that the backend
     * call session has ended. Keep persisted session data.
     */
    private fun handleRecoverableFailure(
        sessionId: Int,
        generation: Int,
        message: String
    ) {
        if (!isCurrentOperation(sessionId, generation)) {
            return
        }

        Log.w(
            TAG,
            "Recoverable failure for session $sessionId: $message"
        )

        startCallForeground(
            victimName,
            "Connection unavailable. Open app to retry."
        )
    }

    private fun handleLiveKitFailure(
        failure: LiveKitConnectionFailure
    ) {
        val failedSessionId = failure.sessionId

        if (failedSessionId == null) {
            Log.w(TAG, "LiveKit failure without session ID: ${failure.message}")
            return
        }

        if (failedSessionId != currentSessionId) {
            Log.d(
                TAG,
                "Ignoring stale LiveKit failure for $failedSessionId"
            )
            return
        }

        handleRecoverableFailure(
            failedSessionId,
            lifecycleGeneration,
            failure.message
        )
    }

    // ---------------------------------------------------------
    // STOP CALL
    // ---------------------------------------------------------

    private fun handleStopRequest(requestedSessionId: Int) {

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedSessionId = prefs.getInt(KEY_SESSION_ID, -1)

        if (requestedSessionId > 0) {

            if (
                currentSessionId > 0 &&
                currentSessionId != requestedSessionId
            ) {
                Log.w(
                    TAG,
                    "Ignoring stale stop $requestedSessionId; " +
                            "active session is $currentSessionId"
                )
                return
            }

            if (
                currentSessionId == -1 &&
                savedSessionId > 0 &&
                savedSessionId != requestedSessionId
            ) {
                Log.w(
                    TAG,
                    "Ignoring stop for $requestedSessionId; " +
                            "persisted session is $savedSessionId"
                )
                return
            }
        }

        val sessionToStop = when {
            requestedSessionId > 0 -> requestedSessionId
            currentSessionId > 0 -> currentSessionId
            savedSessionId > 0 -> savedSessionId
            else -> -1
        }

        if (sessionToStop <= 0) {
            Log.d(TAG, "No session to stop")
            return
        }

        /*
         * Explicit stop is terminal locally.
         * This is intentionally different from an API/network failure.
         */
        markSessionTerminal(sessionToStop)

        scheduleStop(
            sessionId = sessionToStop,
            markTerminal = true,
            clearPersistedData = true
        )
    }

    private fun finishTerminalSessionFromStart(
        sessionId: Int,
        expectedGeneration: Int
    ) {
        if (!isCurrentOperation(sessionId, expectedGeneration)) {
            return
        }

        markSessionTerminal(sessionId)

        scheduleStop(
            sessionId = sessionId,
            markTerminal = true,
            clearPersistedData = true,
            cancelCurrentStart = false
        )
    }

    private fun scheduleStop(
        sessionId: Int,
        markTerminal: Boolean,
        clearPersistedData: Boolean,
        cancelCurrentStart: Boolean = true
    ) {

        if (sessionId <= 0) return

        val oldStartJob = startJob

        lifecycleGeneration++
        val stoppingGeneration = lifecycleGeneration

        if (cancelCurrentStart) {
            oldStartJob?.cancel()
        }

        if (markTerminal) {
            markSessionTerminal(sessionId)
        }

        if (currentSessionId == sessionId) {
            currentSessionId = -1
        }

        if (clearPersistedData) {
            clearCallDataIfMatches(sessionId)
        }

        removeForegroundNotification()

        val previousStop = stopJob

        stopJob = serviceScope.launch {

            try {
                previousStop?.join()

                if (cancelCurrentStart) {
                    oldStartJob?.join()
                }

                lifecycleMutex.withLock {
                    withTimeout(DISCONNECT_TIMEOUT) {
                        liveKitManager.disconnect(
                            expectedSessionId = sessionId
                        )
                    }
                }

                Log.d(TAG, "Stop completed for session $sessionId")

            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Disconnect timed out for session $sessionId")

            } catch (e: CancellationException) {
                throw e

            } catch (e: Exception) {
                Log.e(TAG, "Disconnect failed for session $sessionId", e)

            } finally {
                if (
                    lifecycleGeneration == stoppingGeneration &&
                    currentSessionId == -1
                ) {
                    removeForegroundNotification()
                    stopSelf()

                    Log.d(TAG, "CallService stopped")
                } else {
                    Log.d(
                        TAG,
                        "Old Stop finished; newer lifecycle operation exists"
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------
    // TERMINAL SESSION STORAGE
    // ---------------------------------------------------------

    private fun isSessionTerminalLocally(sessionId: Int): Boolean {
        if (sessionId <= 0) return false

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val legacyId = prefs.getInt(
            KEY_TERMINAL_SESSION_ID,
            -1
        )

        if (legacyId == sessionId) return true

        val terminalIds = prefs.getStringSet(
            KEY_TERMINAL_SESSION_IDS,
            emptySet()
        ) ?: emptySet()

        return sessionId.toString() in terminalIds
    }

    private fun markSessionTerminal(sessionId: Int) {
        if (sessionId <= 0) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val terminalIds = prefs.getStringSet(
            KEY_TERMINAL_SESSION_IDS,
            emptySet()
        )?.toMutableSet() ?: mutableSetOf()

        terminalIds.add(sessionId.toString())

        // Keep the local set bounded.
        while (terminalIds.size > 50) {
            terminalIds.remove(terminalIds.first())
        }

        prefs.edit()
            .putInt(KEY_TERMINAL_SESSION_ID, sessionId)
            .putStringSet(KEY_TERMINAL_SESSION_IDS, terminalIds)
            .apply()
    }

    // ---------------------------------------------------------
    // NOTIFICATION
    // ---------------------------------------------------------

    private fun startCallForeground(
        name: String,
        statusText: String = "Talking with $name"
    ) {

        val sessionId = currentSessionId

        if (sessionId <= 0) return

        val notificationIntent = Intent(
            this,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra("navigate_to", "active_call")
            putExtra("session_id", sessionId)
            putExtra("victim_name", name)
        }

        val contentPendingIntent =
            android.app.PendingIntent.getActivity(
                this,
                sessionId,
                notificationIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

        val endIntent = Intent(
            this,
            CallActionReceiver::class.java
        ).apply {
            action = CallActionReceiver.ACTION_END_CALL
            putExtra(
                CallActionReceiver.EXTRA_SESSION_ID,
                sessionId
            )
        }

        val endPendingIntent =
            android.app.PendingIntent.getBroadcast(
                this,
                sessionId + 1000,
                endIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

        val speakerIntent = Intent(
            this,
            CallService::class.java
        ).apply {
            action = ACTION_TOGGLE_SPEAKER
        }

        val speakerPendingIntent =
            android.app.PendingIntent.getService(
                this,
                sessionId + 2000,
                speakerIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

        val muteIntent = Intent(
            this,
            CallService::class.java
        ).apply {
            action = ACTION_TOGGLE_MUTE
        }

        val mutePendingIntent =
            android.app.PendingIntent.getService(
                this,
                sessionId + 3000,
                muteIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

        val micEnabled = liveKitManager.isMicEnabled.value
        val speakerEnabled = liveKitManager.isSpeakerphoneEnabled.value

        val notification = NotificationCompat.Builder(
            this,
            NotificationHelper.CALL_CHANNEL_ID
        )
            .setContentTitle("Active Emergency Call")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_emergency)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPendingIntent)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .addAction(
                if (micEnabled) R.drawable.ic_mic
                else R.drawable.ic_mic_off,
                if (micEnabled) "Mute" else "Unmute",
                mutePendingIntent
            )
            .addAction(
                if (speakerEnabled) R.drawable.ic_volume_up
                else R.drawable.ic_volume_down,
                if (speakerEnabled) "Speaker Off" else "Speaker On",
                speakerPendingIntent
            )
            .addAction(
                R.drawable.ic_call_end,
                "End Call",
                endPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun removeForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    // ---------------------------------------------------------
    // SERVICE ACCESS
    // ---------------------------------------------------------

    fun getLiveKitManager(): LiveKitManager = liveKitManager

    // ---------------------------------------------------------
    // PERSISTENCE
    // ---------------------------------------------------------

    private fun persistCallData(
        sessionId: Int,
        name: String,
        url: String,
        token: String
    ) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putInt(KEY_SESSION_ID, sessionId)
            .putString(KEY_VICTIM_NAME, name)
            .putString(KEY_URL, url)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    private fun clearCallDataIfMatches(sessionId: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedSessionId = prefs.getInt(KEY_SESSION_ID, -1)

        if (savedSessionId != sessionId) {
            Log.d(
                TAG,
                "Preserving persisted data for session $savedSessionId"
            )
            return
        }

        prefs.edit()
            .remove(KEY_SESSION_ID)
            .remove(KEY_VICTIM_NAME)
            .remove(KEY_URL)
            .remove(KEY_TOKEN)
            .apply()

        Log.d(TAG, "Saved call data cleared for $sessionId")
    }

    // ---------------------------------------------------------
    // RESTORE CALL
    // ---------------------------------------------------------

    private fun restoreCall() {

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val sessionId = prefs.getInt(KEY_SESSION_ID, -1)

        val name = prefs.getString(
            KEY_VICTIM_NAME,
            "Emergency Contact"
        ) ?: "Emergency Contact"

        val url = prefs.getString(KEY_URL, null)
        val token = prefs.getString(KEY_TOKEN, null)

        if (sessionId <= 0) {
            Log.w(TAG, "No saved session to restore")
            stopSelf()
            return
        }

        if (isSessionTerminalLocally(sessionId)) {
            Log.w(TAG, "Saved session $sessionId is locally terminal")
            clearCallDataIfMatches(sessionId)
            stopSelf()
            return
        }

        if (currentSessionId > 0) {
            Log.w(TAG, "Cannot restore while another session is active")
            return
        }

        lifecycleGeneration++
        val generation = lifecycleGeneration

        val previousStopJob = stopJob

        currentSessionId = sessionId
        victimName = name

        startCallForeground(
            name,
            "Restoring emergency call..."
        )

        startJob = serviceScope.launch {

            try {
                previousStopJob?.join()

                if (!isCurrentOperation(sessionId, generation)) {
                    return@launch
                }

                lifecycleMutex.withLock {
                    if (!isCurrentOperation(sessionId, generation)) {
                        return@withLock
                    }

                    startVerifiedSession(
                        sessionId = sessionId,
                        name = name,
                        suppliedToken = token,
                        suppliedUrl = url,
                        generation = generation
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Restore failed for session $sessionId", e)

                handleRecoverableFailure(
                    sessionId,
                    generation,
                    e.message ?: "Unable to restore call"
                )
            }
        }
    }

    // ---------------------------------------------------------
    // DESTROY
    // ---------------------------------------------------------

    override fun onDestroy() {
        Log.d(TAG, "CallService destroyed")

        startJob?.cancel()
        stopJob?.cancel()

        if (::liveKitManager.isInitialized) {
            liveKitManager.dispose()
        }

        serviceScope.cancel()

        super.onDestroy()
    }
}