package fr.schwanx.vox.data.network

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.schwanx.vox.domain.model.ModelDownloadState
import fr.schwanx.vox.domain.model.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8192
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getModelsDirectory(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getModelFile(filename: String): File {
        return File(getModelsDirectory(), filename)
    }

    fun isModelDownloaded(filename: String): Boolean {
        val file = getModelFile(filename)
        return file.exists() && file.length() > 0
    }

    fun deleteModel(filename: String): Boolean {
        val file = getModelFile(filename)
        if (file.exists()) {
            return file.delete()
        }
        return false
    }

    fun downloadModel(model: WhisperModel): Flow<ModelDownloadState> = flow {
        val targetFile = getModelFile(model.filename)
        if (targetFile.exists() && targetFile.length() >= model.sizeBytes * 0.9) {
            emit(ModelDownloadState.Downloaded)
            return@flow
        }

        emit(ModelDownloadState.Downloading(0, 0, model.sizeBytes))

        val tempFile = File(getModelsDirectory(), "${model.filename}.tmp")

        val request = Request.Builder()
            .url(model.downloadUrl)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(ModelDownloadState.Error("Erreur serveur HTTP: ${response.code}"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(ModelDownloadState.Error("Corps de réponse HTTP vide"))
                return@flow
            }

            val contentLength = if (body.contentLength() > 0) body.contentLength() else model.sizeBytes
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            var lastEmittedPercent = -1

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val percent = if (contentLength > 0) {
                    ((totalBytesRead.toDouble() / contentLength.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                } else 0

                if (percent != lastEmittedPercent) {
                    lastEmittedPercent = percent
                    emit(ModelDownloadState.Downloading(percent, totalBytesRead, contentLength))
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            if (tempFile.exists() && tempFile.length() > 0) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val renamed = tempFile.renameTo(targetFile)
                if (renamed || targetFile.exists()) {
                    Log.i(TAG, "Model ${model.filename} downloaded successfully (${targetFile.length()} bytes)")
                    emit(ModelDownloadState.Downloaded)
                } else {
                    emit(ModelDownloadState.Error("Impossible de renommer le fichier temporaire du modèle"))
                }
            } else {
                emit(ModelDownloadState.Error("Fichier de modèle téléchargé invalide ou corrompu"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model ${model.filename}", e)
            tempFile.delete()
            emit(ModelDownloadState.Error("Échec du téléchargement: ${e.localizedMessage ?: "Erreur réseau"}"))
        }
    }.flowOn(Dispatchers.IO)
}
