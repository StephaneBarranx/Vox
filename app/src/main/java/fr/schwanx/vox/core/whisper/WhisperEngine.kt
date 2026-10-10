package fr.schwanx.vox.core.whisper

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngine @Inject constructor() {

    private var contextPtr: Long = 0L

    companion object {
        private const val TAG = "WhisperEngine"

        @Volatile
        private var libraryLoaded = false

        private fun ensureLibraryLoaded(): Boolean {
            if (libraryLoaded) return true
            synchronized(this) {
                if (!libraryLoaded) {
                    try {
                        System.loadLibrary("vox_whisper")
                        libraryLoaded = true
                        Log.i(TAG, "Native library vox_whisper loaded successfully")
                    } catch (e: UnsatisfiedLinkError) {
                        Log.e(TAG, "Failed to load native library vox_whisper", e)
                    }
                }
            }
            return libraryLoaded
        }

        /**
         * Whisper runs best on the performance cores only: using the efficiency
         * cores as well slows the whole computation down. Counts the cores whose
         * max frequency is above the slowest cluster's, capped to a sane range.
         */
        fun defaultThreadCount(): Int {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val maxFreqs = (0 until cores).mapNotNull { i ->
                try {
                    File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
                } catch (e: Exception) {
                    null
                }
            }
            val fastCores = if (maxFreqs.size == cores) {
                val slowest = maxFreqs.min()
                maxFreqs.count { it > slowest }.takeIf { it > 0 } ?: cores
            } else {
                cores / 2
            }
            return fastCores.coerceIn(1, 8)
        }
    }

    fun isInitialized(): Boolean = contextPtr != 0L

    /**
     * @param nativeLibDir the app's native library directory
     * (ApplicationInfo.nativeLibraryDir), where ggml looks for its CPU backends.
     */
    fun init(modelPath: String, nativeLibDir: String): Boolean {
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file does not exist at: $modelPath")
            return false
        }
        if (!ensureLibraryLoaded()) return false
        if (contextPtr != 0L) {
            free()
        }
        contextPtr = initContext(modelPath, nativeLibDir)
        return contextPtr != 0L
    }

    /**
     * Transcribes 16 kHz mono float PCM. Segment timestamps passed to the
     * listener are in milliseconds, relative to the start of [pcmData].
     * Returns 0 on success, -2 if cancelled, another value on error.
     */
    fun transcribe(
        pcmData: FloatArray,
        numThreads: Int = defaultThreadCount(),
        language: String = "auto",
        translate: Boolean = false,
        listener: WhisperProgressListener? = null
    ): Int {
        if (contextPtr == 0L) {
            Log.e(TAG, "Whisper context is not initialized")
            return -1
        }
        return fullTranscribe(contextPtr, pcmData, numThreads, language, translate, listener)
    }

    /** Language code (e.g. "fr") used by the last [transcribe] call, detected or forced. */
    fun detectedLanguage(): String? =
        if (contextPtr != 0L) detectedLanguage(contextPtr) else null

    fun cancel() {
        if (contextPtr != 0L) {
            abort(contextPtr)
        }
    }

    fun free() {
        if (contextPtr != 0L) {
            freeContext(contextPtr)
            contextPtr = 0L
        }
    }

    private external fun initContext(modelPath: String, nativeLibDir: String): Long
    private external fun fullTranscribe(
        contextPtr: Long,
        pcmData: FloatArray,
        numThreads: Int,
        language: String,
        translate: Boolean,
        listener: WhisperProgressListener?
    ): Int
    private external fun detectedLanguage(contextPtr: Long): String?
    private external fun abort(contextPtr: Long)
    private external fun freeContext(contextPtr: Long)
}
