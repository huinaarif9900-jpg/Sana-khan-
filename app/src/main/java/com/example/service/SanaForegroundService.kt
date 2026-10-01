package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.audio.SpeechRecognizerWakeWordEngine
import com.example.audio.WakeWordEngine
import com.example.data.GeminiConfigManager

class SanaForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "sana_voice_assistant_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.sana.START_SERVICE"
        const val ACTION_STOP = "com.example.sana.STOP_SERVICE"

        var isRunning = false
            private set
    }

    private var wakeWordEngine: WakeWordEngine? = null
    private lateinit var configManager: GeminiConfigManager

    override fun onCreate() {
        super.onCreate()
        configManager = GeminiConfigManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("SANA AI is Active — Listening & Ready"))
        isRunning = true

        if (configManager.isWakeWordEnabled()) {
            startWakeWordListening()
        }

        return START_STICKY
    }

    private fun startWakeWordListening() {
        wakeWordEngine = SpeechRecognizerWakeWordEngine(this)
        if (wakeWordEngine?.isAvailable() == true) {
            wakeWordEngine?.startListening(
                onWakeWordDetected = { phrase ->
                    // Open main activity when "Hey SANA" is detected
                    val launchIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("TRIGGER_VOICE_CAPTURE", true)
                    }
                    startActivity(launchIntent)
                },
                onError = { /* Log or handle wake word fallback */ }
            )
        }
    }

    private fun stopForegroundService() {
        isRunning = false
        wakeWordEngine?.stopListening()
        wakeWordEngine?.destroy()
        wakeWordEngine = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SANA Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SANA voice listener and session active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SanaForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SANA AI Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        wakeWordEngine?.destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
