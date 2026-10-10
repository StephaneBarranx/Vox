package fr.schwanx.vox.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingResult(
    val file: File,
    val durationMs: Long
)

@Singleton
class AudioRecorder @Inject constructor() {

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = AudioUtils.SAMPLE_RATE_16K
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var recordingScope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _elapsedTimeMs = MutableStateFlow(0L)
    val elapsedTimeMs: StateFlow<Long> = _elapsedTimeMs.asStateFlow()

    private val _amplitudeFlow = MutableSharedFlow<Float>(extraBufferCapacity = 64)
    val amplitudeFlow: SharedFlow<Float> = _amplitudeFlow.asSharedFlow()

    private var outputFile: File? = null
    private var startTimeMs = 0L
    private var pausedTimeAccumulatedMs = 0L
    private var pauseStartMs = 0L

    @SuppressLint("MissingPermission")
    suspend fun startRecording(targetFile: File): Boolean = withContext(Dispatchers.IO) {
        if (_isRecording.value) {
            Log.w(TAG, "Recording is already in progress")
            return@withContext false
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize.coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioRecord", e)
            return@withContext false
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord state is not initialized")
            audioRecord?.release()
            audioRecord = null
            return@withContext false
        }

        outputFile = targetFile
        startTimeMs = System.currentTimeMillis()
        pausedTimeAccumulatedMs = 0L
        _elapsedTimeMs.value = 0L
        _isPaused.value = false

        audioRecord?.startRecording()
        _isRecording.value = true

        recordingJob = recordingScope.launch {
            recordLoop(targetFile, bufferSize)
        }

        Log.i(TAG, "Started recording to ${targetFile.absolutePath}")
        return@withContext true
    }

    private suspend fun recordLoop(file: File, bufferSize: Int) {
        var randomAccessFile: RandomAccessFile? = null
        try {
            randomAccessFile = RandomAccessFile(file, "rw")
            // Write placeholder header (44 bytes)
            AudioUtils.writeWavHeader(randomAccessFile, 0, 0)

            val buffer = ByteArray(bufferSize)
            var totalAudioLen = 0L

            while (_isRecording.value) {
                if (_isPaused.value) {
                    kotlinx.coroutines.delay(100)
                    continue
                }

                val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (bytesRead > 0) {
                    randomAccessFile.write(buffer, 0, bytesRead)
                    totalAudioLen += bytesRead

                    val amplitude = AudioUtils.calculateRmsAmplitude(buffer, bytesRead)
                    _amplitudeFlow.emit(amplitude)

                    val now = System.currentTimeMillis()
                    val duration = now - startTimeMs - pausedTimeAccumulatedMs
                    _elapsedTimeMs.value = duration
                }
            }

            // Finalize WAV header
            val totalDataLen = totalAudioLen + 36
            AudioUtils.writeWavHeader(randomAccessFile, totalAudioLen, totalDataLen)
            Log.i(TAG, "Finished writing WAV file. Total audio bytes: $totalAudioLen")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing audio recording file", e)
        } finally {
            try {
                randomAccessFile?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            _isPaused.value = true
            pauseStartMs = System.currentTimeMillis()
            Log.i(TAG, "Recording paused")
        }
    }

    fun resumeRecording() {
        if (_isRecording.value && _isPaused.value) {
            pausedTimeAccumulatedMs += System.currentTimeMillis() - pauseStartMs
            _isPaused.value = false
            Log.i(TAG, "Recording resumed")
        }
    }

    suspend fun stopRecording(): RecordingResult? = withContext(Dispatchers.IO) {
        if (!_isRecording.value) return@withContext null

        _isRecording.value = false
        _isPaused.value = false

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord?.release()
            audioRecord = null
        }

        recordingJob?.join()
        recordingJob = null

        val finalFile = outputFile ?: return@withContext null
        val duration = _elapsedTimeMs.value

        Log.i(TAG, "Recording stopped. File size: ${finalFile.length()} bytes, duration: ${duration}ms")
        return@withContext RecordingResult(finalFile, duration)
    }

    fun cancelRecording() {
        _isRecording.value = false
        _isPaused.value = false

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
        } finally {
            audioRecord?.release()
            audioRecord = null
        }

        recordingJob?.cancel()
        recordingJob = null

        outputFile?.delete()
        outputFile = null
        _elapsedTimeMs.value = 0L
        Log.i(TAG, "Recording cancelled and file deleted")
    }
}
