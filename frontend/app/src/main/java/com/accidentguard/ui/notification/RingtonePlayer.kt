package com.accidentguard.ui.notification

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

object RingtonePlayer {
    private const val TAG = "RingtonePlayer"
    private var ringtone: Ringtone? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    fun startRinging(context: Context, durationMs: Long = 30000) {
        stopRinging() // Ensure any previous ringtone is stopped

        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, notification)
            
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            ringtone?.play()
            Log.d(TAG, "Started ringing")

            stopRunnable = Runnable {
                Log.d(TAG, "Ringtone timeout reached")
                stopRinging()
            }
            handler.postDelayed(stopRunnable!!, durationMs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }
    }

    fun stopRinging() {
        try {
            stopRunnable?.let { handler.removeCallbacks(it) }
            stopRunnable = null
            
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
                Log.d(TAG, "Stopped ringing")
            }
            ringtone = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop ringtone", e)
        }
    }
}
