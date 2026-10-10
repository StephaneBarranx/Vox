package fr.schwanx.vox.domain.model

sealed class ModelDownloadState {
    object NotDownloaded : ModelDownloadState()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : ModelDownloadState()
    object Downloaded : ModelDownloadState()
    data class Error(val message: String) : ModelDownloadState()
}

data class WhisperModel(
    val id: String, // e.g. "ggml-base-q5_1.bin"
    val filename: String,
    val name: String, // e.g. "Base (Équilibré)"
    val sizeBytes: Long,
    val sizeDisplay: String, // e.g. "57 Mo"
    val description: String,
    val downloadUrl: String,
    val isDefault: Boolean = false
) {
    companion object {
        val AVAILABLE_MODELS = listOf(
            WhisperModel(
                id = "ggml-tiny-q5_1.bin",
                filename = "ggml-tiny-q5_1.bin",
                name = "Tiny Q5_1",
                sizeBytes = 31_000_000L,
                sizeDisplay = "~31 Mo",
                description = "Très rapide, empreinte mémoire minimale, précision moyenne",
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin",
                isDefault = false
            ),
            WhisperModel(
                id = "ggml-base-q5_1.bin",
                filename = "ggml-base-q5_1.bin",
                name = "Base Q5_1",
                sizeBytes = 57_000_000L,
                sizeDisplay = "~57 Mo",
                description = "Modèle recommandé, excellent compromis entre vitesse et précision",
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin",
                isDefault = true
            ),
            WhisperModel(
                id = "ggml-small-q5_1.bin",
                filename = "ggml-small-q5_1.bin",
                name = "Small Q5_1",
                sizeBytes = 182_000_000L,
                sizeDisplay = "~182 Mo",
                description = "Haute précision pour les enregistrements plus complexes ou le vocabulaire riche",
                downloadUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
                isDefault = false
            )
        )
    }
}
