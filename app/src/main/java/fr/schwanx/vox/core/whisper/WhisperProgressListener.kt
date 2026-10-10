package fr.schwanx.vox.core.whisper

interface WhisperProgressListener {
    fun onProgress(progress: Int)
    fun onNewSegment(startMs: Long, endMs: Long, text: String)
}
