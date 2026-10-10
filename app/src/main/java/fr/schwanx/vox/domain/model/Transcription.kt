package fr.schwanx.vox.domain.model

data class Transcription(
    val id: Long = 0,
    val title: String,
    val text: String,
    val segments: List<Segment>,
    val audioPath: String?,
    val durationMs: Long,
    val modelName: String,
    val language: String,
    val createdAtMs: Long = System.currentTimeMillis()
)
