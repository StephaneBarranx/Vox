package fr.schwanx.vox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.schwanx.vox.MainActivity
import fr.schwanx.vox.R
import fr.schwanx.vox.core.audio.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AudioRecordService : Service() {

    companion object {
        const val CHANNEL_ID = "vox_audio_recording"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "fr.schwanx.vox.action.START_RECORDING"
        const val ACTION_STOP = "fr.schwanx.vox.action.STOP_RECORDING"
        const val ACTION_PAUSE = "fr.schwanx.vox.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "fr.schwanx.vox.action.RESUME_RECORDING"
        const val EXTRA_FILE_PATH = "extra_file_path"

        fun startService(context: Context, outputFile: File) {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FILE_PATH, outputFile.absolutePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AudioRecordService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var audioRecorder: AudioRecorder

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var updateTimerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
                if (filePath != null) {
                    val file = File(filePath)
                    startForegroundWithNotification()
                    serviceScope.launch {
                        audioRecorder.startRecording(file)
                    }
                    startNotificationUpdates()
                }
            }
            ACTION_PAUSE -> {
                audioRecorder.pauseRecording()
                updateNotification()
            }
            ACTION_RESUME -> {
                audioRecorder.resumeRecording()
                updateNotification()
            }
            ACTION_STOP -> {
                updateTimerJob?.cancel()
                serviceScope.launch {
                    audioRecorder.stopRecording()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = createNotification("00:00")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            if (type != 0) {
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startNotificationUpdates() {
        updateTimerJob?.cancel()
        updateTimerJob = serviceScope.launch {
            while (audioRecorder.isRecording.value) {
                val ms = audioRecorder.elapsedTimeMs.value
                val formatted = formatDuration(ms)
                updateNotification(formatted)
                delay(1000)
            }
        }
    }

    private fun updateNotification(timeStr: String? = null) {
        val ms = audioRecorder.elapsedTimeMs.value
        val formatted = timeStr ?: formatDuration(ms)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(formatted))
    }

    private fun createNotification(timeFormatted: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioRecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Enregistrement vocal Vox")
            .setContentText("En cours... $timeFormatted")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enregistrement audio Vox",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification pendant l'enregistrement de la voix"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format("%02d:%02d", min, sec)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
