package fr.schwanx.vox.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.schwanx.vox.MainActivity
import fr.schwanx.vox.R
import fr.schwanx.vox.core.audio.AudioDecoder
import fr.schwanx.vox.core.native.WhisperEngine
import fr.schwanx.vox.core.native.WhisperProgressListener
import fr.schwanx.vox.data.network.ModelDownloader
import fr.schwanx.vox.data.repository.TranscriptionRepository
import fr.schwanx.vox.domain.model.Segment
import fr.schwanx.vox.domain.model.Transcription
import java.io.File

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted private val params: WorkerParameters,
    private val audioDecoder: AudioDecoder,
    private val whisperEngine: WhisperEngine,
    private val modelDownloader: ModelDownloader,
    private val repository: TranscriptionRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TranscriptionWorker"
        const val CHANNEL_ID = "vox_transcription_channel"
        const val NOTIFICATION_ID = 2002

        const val KEY_AUDIO_PATH = "key_audio_path"
        const val KEY_MODEL_FILENAME = "key_model_filename"
        const val KEY_LANGUAGE = "key_language"
        const val KEY_TITLE = "key_title"
        const val KEY_NUM_THREADS = "key_num_threads"

        const val KEY_RESULT_TRANSCRIPTION_ID = "key_result_transcription_id"
    }

    override suspend fun doWork(): Result {
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure()
        val modelFilename = inputData.getString(KEY_MODEL_FILENAME) ?: "ggml-base-q5_1.bin"
        val language = inputData.getString(KEY_LANGUAGE) ?: "auto"
        val title = inputData.getString(KEY_TITLE) ?: File(audioPath).nameWithoutExtension
        val numThreads = inputData.getInt(KEY_NUM_THREADS, 0)

        createNotificationChannel()
        try {
            setForeground(createForegroundInfo("Décodage audio en cours...", 5))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground service", e)
        }

        val modelFile = modelDownloader.getModelFile(modelFilename)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file $modelFilename not found")
            return Result.failure()
        }

        // Step 1: Decode audio to 16kHz PCM Float
        val pcmData = audioDecoder.decodeAudioToPcmFloat(context, audioPath) { decodeProgress ->
            val progressPct = (decodeProgress * 20f).toInt().coerceIn(0, 20)
            try {
                setForegroundAsync(createForegroundInfo("Décodage audio ($progressPct%)...", progressPct))
            } catch (ignored: Exception) {}
        }

        if (pcmData.isEmpty()) {
            Log.e(TAG, "Audio decoding produced empty PCM data")
            return Result.failure()
        }

        // Step 2: Initialize Whisper engine
        val initialized = whisperEngine.init(modelFile.absolutePath)
        if (!initialized) {
            Log.e(TAG, "Failed to initialize Whisper engine")
            return Result.failure()
        }

        val segments = mutableListOf<Segment>()
        val fullTextBuilder = StringBuilder()

        val threadsToUse = if (numThreads > 0) numThreads else Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        // Step 3: Run Transcription
        val resultCode = whisperEngine.transcribe(
            pcmData = pcmData,
            numThreads = threadsToUse,
            language = language,
            translate = false,
            listener = object : WhisperProgressListener {
                override fun onProgress(progress: Int) {
                    val overallProgress = 20 + ((progress / 100f) * 75f).toInt()
                    try {
                        setForegroundAsync(createForegroundInfo("Transcription en cours ($overallProgress%)...", overallProgress))
                    } catch (ignored: Exception) {}
                }

                override fun onNewSegment(startMs: Long, endMs: Long, text: String) {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) {
                        segments.add(Segment(startMs, endMs, trimmed))
                        if (fullTextBuilder.isNotEmpty()) {
                            fullTextBuilder.append(" ")
                        }
                        fullTextBuilder.append(trimmed)
                    }
                }
            }
        )

        whisperEngine.free()

        if (resultCode != 0) {
            Log.e(TAG, "Whisper transcription failed with result code $resultCode")
            return Result.failure()
        }

        val durationMs = (pcmData.size.toDouble() / 16000.0 * 1000.0).toLong()
        val fullText = fullTextBuilder.toString().ifEmpty { "Aucun texte transcrit" }

        // Step 4: Save to Room database
        val transcription = Transcription(
            title = title,
            text = fullText,
            segments = segments,
            audioPath = audioPath,
            durationMs = durationMs,
            modelName = modelFilename,
            language = language
        )

        val newId = repository.saveTranscription(transcription)
        Log.i(TAG, "Transcription saved with ID $newId")

        return Result.success(workDataOf(KEY_RESULT_TRANSCRIPTION_ID to newId))
    }

    private fun createForegroundInfo(message: String, progress: Int): ForegroundInfo {
        val notification = createNotification(message, progress)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0

        return if (type != 0) {
            ForegroundInfo(NOTIFICATION_ID, notification, type)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(message: String, progress: Int): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Vox - Transcription en cours")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transcription Vox",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
