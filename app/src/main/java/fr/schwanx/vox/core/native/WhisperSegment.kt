package fr.schwanx.vox.core.native

data class WhisperSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)
