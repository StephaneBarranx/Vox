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
import fr.schwanx.vox.core.whisper.WhisperEngine
import fr.schwanx.vox.core.whisper.WhisperProgressListener
import fr.schwanx.vox.data.network.ModelDownloader
import fr.schwanx.vox.data.repository.TranscriptionRepository
import fr.schwanx.vox.domain.model.Segment
import fr.schwanx.vox.domain.model.Transcription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.channels.FileChannel

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
        const val KEY_PROGRESS = "key_progress"

        private const val SAMPLE_RATE = 16000
        private const val CHUNK_SECONDS = 5 * 60
        private const val CUT_SEARCH_SECONDS = 10
        private const val DECODE_PROGRESS_SHARE = 10

        private val engineMutex = Mutex()
    }

    override suspend fun doWork(): Result {
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return Result.failure()
        val modelFilename = inputData.getString(KEY_MODEL_FILENAME) ?: "ggml-base-q5_1.bin"
        val language = inputData.getString(KEY_LANGUAGE) ?: "auto"
        val title = inputData.getString(KEY_TITLE) ?: File(audioPath).nameWithoutExtension
        val numThreads = inputData.getInt(KEY_NUM_THREADS, 0)

        createNotificationChannel()
        try {
            setForeground(createForegroundInfo("Décodage audio en cours...", 0))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground service", e)
        }

        val modelFile = modelDownloader.getModelFile(modelFilename)
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file $modelFilename not found")
            return Result.failure()
        }

        val pcmFile = File(context.cacheDir, "vox_pcm_${id}.f32")
        try {
            // Step 1: Decode audio to a 16 kHz mono float PCM file
            var lastDecodePct = -1
            val totalSamples = audioDecoder.decodeToPcmFile(context, audioPath, pcmFile) { decodeProgress ->
                val pct = (decodeProgress * DECODE_PROGRESS_SHARE).toInt()
                if (pct != lastDecodePct) {
                    lastDecodePct = pct
                    updateProgress("Décodage audio...", pct)
                }
            }

            if (totalSamples <= 0L) {
                Log.e(TAG, "Audio decoding produced no PCM data")
                return Result.failure()
            }

            // The WhisperEngine singleton holds a single native context: run one
            // transcription at a time, the others wait for their turn.
            if (engineMutex.isLocked) {
                updateProgress("En attente d'une autre transcription...", DECODE_PROGRESS_SHARE)
            }
            return engineMutex.withLock { transcribeAndSave(pcmFile, totalSamples, audioPath, modelFile, modelFilename, language, title, numThreads) }
        } finally {
            pcmFile.delete()
        }
    }

    private suspend fun transcribeAndSave(
        pcmFile: File,
        totalSamples: Long,
        audioPath: String,
        modelFile: File,
        modelFilename: String,
        language: String,
        title: String,
        numThreads: Int
    ): Result {
        try {
            // Step 2: Initialize Whisper engine
            updateProgress("Chargement du modèle...", DECODE_PROGRESS_SHARE)
            if (!whisperEngine.init(modelFile.absolutePath, context.applicationInfo.nativeLibraryDir)) {
                Log.e(TAG, "Failed to initialize Whisper engine")
                return Result.failure()
            }

            val segments = mutableListOf<Segment>()
            val threadsToUse = if (numThreads > 0) numThreads else WhisperEngine.defaultThreadCount()

            // Step 3: Transcribe chunk by chunk so long recordings never have to fit in memory.
            // With "auto", the language detected on the first chunk is reused for
            // the next ones: consistent output and one less encoder pass per chunk.
            var chunkLanguage = language
            var lastPct = -1
            val ok = transcribeInChunks(pcmFile, totalSamples) { chunk, chunkStart ->
                val offsetMs = chunkStart * 1000L / SAMPLE_RATE
                val result = whisperEngine.transcribe(
                    pcmData = chunk,
                    numThreads = threadsToUse,
                    language = chunkLanguage,
                    translate = false,
                    listener = object : WhisperProgressListener {
                        override fun onProgress(progress: Int) {
                            if (isStopped) {
                                whisperEngine.cancel()
                                return
                            }
                            val done = chunkStart + chunk.size * (progress / 100.0)
                            val pct = DECODE_PROGRESS_SHARE +
                                ((100 - DECODE_PROGRESS_SHARE) * done / totalSamples).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                updateProgress("Transcription en cours ($pct%)...", pct)
                            }
                        }

                        override fun onNewSegment(startMs: Long, endMs: Long, text: String) {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) {
                                segments.add(Segment(startMs + offsetMs, endMs + offsetMs, trimmed))
                            }
                        }
                    }
                )
                if (result != 0) {
                    Log.e(TAG, "Whisper transcription failed with result code $result")
                } else if (chunkLanguage == "auto") {
                    whisperEngine.detectedLanguage()?.let {
                        Log.i(TAG, "Detected language: $it")
                        chunkLanguage = it
                    }
                }
                result == 0
            }

            if (!ok) return Result.failure()

            val durationMs = totalSamples * 1000L / SAMPLE_RATE
            val fullText = segments.joinToString(" ") { it.text }.ifEmpty { "Aucun texte transcrit" }

            // Step 4: Save to Room database
            val transcription = Transcription(
                title = title,
                text = fullText,
                segments = segments,
                audioPath = audioPath,
                durationMs = durationMs,
                modelName = modelFilename,
                language = chunkLanguage
            )

            val newId = repository.saveTranscription(transcription)
            Log.i(TAG, "Transcription saved with ID $newId")

            return Result.success(workDataOf(KEY_RESULT_TRANSCRIPTION_ID to newId))
        } finally {
            whisperEngine.free()
        }
    }

    /**
     * Feeds the PCM file to [transcribeChunk] in chunks of about [CHUNK_SECONDS].
     * Each chunk ends at the quietest point of its last seconds, so that words
     * are not cut in half between two chunks. Stops at the first failure.
     */
    private inline fun transcribeInChunks(
        pcmFile: File,
        totalSamples: Long,
        transcribeChunk: (chunk: FloatArray, chunkStart: Long) -> Boolean
    ): Boolean {
        val chunkSamples = CHUNK_SECONDS * SAMPLE_RATE
        val searchSamples = CUT_SEARCH_SECONDS * SAMPLE_RATE
        RandomAccessFile(pcmFile, "r").use { raf ->
            val channel = raf.channel
            var start = 0L
            while (start < totalSamples) {
                if (isStopped) return false
                val remaining = totalSamples - start
                // Avoid leaving a tiny last chunk: take everything if it nearly fits.
                val isLast = remaining <= chunkSamples + searchSamples
                val length = if (isLast) remaining.toInt() else chunkSamples
                val chunk = FloatArray(length)
                channel.map(FileChannel.MapMode.READ_ONLY, start * 4, length * 4L)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asFloatBuffer()
                    .get(chunk)

                val cut = if (isLast) length else quietestCut(chunk, searchSamples)
                val toTranscribe = if (cut == length) chunk else chunk.copyOf(cut)

                if (!transcribeChunk(toTranscribe, start)) return false
                start += cut
            }
        }
        return true
    }

    /** Returns a cut index in the middle of the lowest-energy 100 ms window within the last [searchSamples]. */
    private fun quietestCut(chunk: FloatArray, searchSamples: Int): Int {
        val window = SAMPLE_RATE / 10
        var bestCut = chunk.size
        var bestEnergy = Double.MAX_VALUE
        var windowStart = (chunk.size - searchSamples).coerceAtLeast(0)
        while (windowStart + window <= chunk.size) {
            var energy = 0.0
            for (i in windowStart until windowStart + window) {
                energy += chunk[i] * chunk[i]
            }
            if (energy < bestEnergy) {
                bestEnergy = energy
                bestCut = windowStart + window / 2
            }
            windowStart += window
        }
        return bestCut
    }

    private fun updateProgress(message: String, progress: Int) {
        try {
            setForegroundAsync(createForegroundInfo(message, progress))
        } catch (ignored: Exception) {}
        setProgressAsync(workDataOf(KEY_PROGRESS to progress))
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
