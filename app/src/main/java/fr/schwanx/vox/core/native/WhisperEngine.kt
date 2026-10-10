package fr.schwanx.vox.core.native

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperEngine @Inject constructor() {

    private var contextPtr: Long = 0L

    companion object {
        private const val TAG = "WhisperEngine"

        init {
            try {
                System.loadLibrary("vox_whisper")
                Log.i(TAG, "Native library vox_whisper loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library vox_whisper", e)
            }
        }
    }

    fun isInitialized(): Boolean = contextPtr != 0L

    fun init(modelPath: String): Boolean {
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file does not exist at: $modelPath")
            return false
        }
        if (contextPtr != 0L) {
            free()
        }
        contextPtr = initContext(modelPath)
        return contextPtr != 0L
    }

    fun transcribe(
        pcmData: FloatArray,
        numThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
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

    private external fun initContext(modelPath: String): Long
    private external fun fullTranscribe(
        contextPtr: Long,
        pcmData: FloatArray,
        numThreads: Int,
        language: String,
        translate: Boolean,
        listener: WhisperProgressListener?
    ): Int
    private external fun abort(contextPtr: Long)
    private external fun freeContext(contextPtr: Long)
}
