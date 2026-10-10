package fr.schwanx.vox.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TranscriptionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class VoxDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao
}
