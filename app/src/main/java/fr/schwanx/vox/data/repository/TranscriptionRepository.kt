package fr.schwanx.vox.data.repository

import fr.schwanx.vox.data.local.db.Converters
import fr.schwanx.vox.data.local.db.TranscriptionDao
import fr.schwanx.vox.data.local.db.toDomain
import fr.schwanx.vox.data.local.db.toEntity
import fr.schwanx.vox.domain.model.Transcription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranscriptionRepository @Inject constructor(
    private val dao: TranscriptionDao
) {
    private val converters = Converters()

    fun getAllTranscriptions(): Flow<List<Transcription>> {
        return dao.getAllTranscriptions().map { entities ->
            entities.map { entity ->
                val segments = converters.toSegmentsList(entity.segmentsJson)
                entity.toDomain(segments)
            }
        }
    }

    fun searchTranscriptions(query: String): Flow<List<Transcription>> {
        return dao.searchTranscriptions(query).map { entities ->
            entities.map { entity ->
                val segments = converters.toSegmentsList(entity.segmentsJson)
                entity.toDomain(segments)
            }
        }
    }

    fun observeTranscriptionById(id: Long): Flow<Transcription?> {
        return dao.observeTranscriptionById(id).map { entity ->
            entity?.let {
                val segments = converters.toSegmentsList(it.segmentsJson)
                it.toDomain(segments)
            }
        }
    }

    suspend fun getTranscriptionById(id: Long): Transcription? {
        val entity = dao.getTranscriptionById(id) ?: return null
        val segments = converters.toSegmentsList(entity.segmentsJson)
        return entity.toDomain(segments)
    }

    suspend fun saveTranscription(transcription: Transcription): Long {
        val segmentsJson = converters.fromSegmentsList(transcription.segments)
        val entity = transcription.toEntity(segmentsJson)
        return dao.insertTranscription(entity)
    }

    suspend fun updateTranscription(transcription: Transcription) {
        val segmentsJson = converters.fromSegmentsList(transcription.segments)
        val entity = transcription.toEntity(segmentsJson)
        dao.updateTranscription(entity)
    }

    suspend fun deleteTranscription(id: Long) {
        dao.deleteById(id)
    }
}
