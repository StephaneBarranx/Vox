package fr.schwanx.vox.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.schwanx.vox.data.local.db.TranscriptionDao
import fr.schwanx.vox.data.local.db.VoxDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVoxDatabase(
        @ApplicationContext context: Context
    ): VoxDatabase {
        return Room.databaseBuilder(
            context,
            VoxDatabase::class.java,
            "vox_database.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideTranscriptionDao(database: VoxDatabase): TranscriptionDao {
        return database.transcriptionDao()
    }
}
