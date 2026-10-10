package fr.schwanx.vox.data.repository

import fr.schwanx.vox.data.network.ModelDownloader
import fr.schwanx.vox.domain.model.ModelDownloadState
import fr.schwanx.vox.domain.model.WhisperModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    private val modelDownloader: ModelDownloader
) {

    fun getAvailableModelsWithState(): List<Pair<WhisperModel, ModelDownloadState>> {
        return WhisperModel.AVAILABLE_MODELS.map { model ->
            val isDownloaded = modelDownloader.isModelDownloaded(model.filename)
            val state = if (isDownloaded) ModelDownloadState.Downloaded else ModelDownloadState.NotDownloaded
            model to state
        }
    }

    fun downloadModel(model: WhisperModel): Flow<ModelDownloadState> {
        return modelDownloader.downloadModel(model)
    }

    fun isModelDownloaded(filename: String): Boolean {
        return modelDownloader.isModelDownloaded(filename)
    }

    fun getModelFile(filename: String): File {
        return modelDownloader.getModelFile(filename)
    }

    fun deleteModel(filename: String): Boolean {
        return modelDownloader.deleteModel(filename)
    }

    fun getTotalModelsDiskUsageBytes(): Long {
        val dir = modelDownloader.getModelsDirectory()
        return dir.listFiles()?.sumOf { it.length() } ?: 0L
    }
}
