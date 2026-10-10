package fr.schwanx.vox.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptionDao {

    @Query("SELECT * FROM transcriptions ORDER BY createdAtMs DESC")
    fun getAllTranscriptions(): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' ORDER BY createdAtMs DESC")
    fun searchTranscriptions(query: String): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    suspend fun getTranscriptionById(id: Long): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions WHERE id = :id LIMIT 1")
    fun observeTranscriptionById(id: Long): Flow<TranscriptionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranscription(entity: TranscriptionEntity): Long

    @Update
    suspend fun updateTranscription(entity: TranscriptionEntity)

    @Delete
    suspend fun deleteTranscription(entity: TranscriptionEntity)

    @Query("DELETE FROM transcriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}
