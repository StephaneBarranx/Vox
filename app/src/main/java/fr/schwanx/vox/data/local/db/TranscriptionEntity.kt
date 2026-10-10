package fr.schwanx.vox.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.schwanx.vox.domain.model.Segment
import fr.schwanx.vox.domain.model.Transcription

@Entity(tableName = "transcriptions")
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val text: String,
    val segmentsJson: String,
    val audioPath: String?,
    val durationMs: Long,
    val modelName: String,
    val language: String,
    val createdAtMs: Long
)

fun TranscriptionEntity.toDomain(segmentsList: List<Segment>): Transcription {
    return Transcription(
        id = id,
        title = title,
        text = text,
        segments = segmentsList,
        audioPath = audioPath,
        durationMs = durationMs,
        modelName = modelName,
        language = language,
        createdAtMs = createdAtMs
    )
}

fun Transcription.toEntity(segmentsJson: String): TranscriptionEntity {
    return TranscriptionEntity(
        id = id,
        title = title,
        text = text,
        segmentsJson = segmentsJson,
        audioPath = audioPath,
        durationMs = durationMs,
        modelName = modelName,
        language = language,
        createdAtMs = createdAtMs
    )
}
