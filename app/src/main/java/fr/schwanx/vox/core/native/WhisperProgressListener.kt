package fr.schwanx.vox.core.native

interface WhisperProgressListener {
    fun onProgress(progress: Int)
    fun onNewSegment(startMs: Long, endMs: Long, text: String)
}
