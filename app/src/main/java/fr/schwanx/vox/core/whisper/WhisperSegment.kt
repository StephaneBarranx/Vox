package fr.schwanx.vox.core.whisper

data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
