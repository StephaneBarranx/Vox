package fr.schwanx.vox.data.local.db

import androidx.room.TypeConverter
import fr.schwanx.vox.domain.model.Segment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromSegmentsList(value: List<Segment>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toSegmentsList(value: String?): List<Segment> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
