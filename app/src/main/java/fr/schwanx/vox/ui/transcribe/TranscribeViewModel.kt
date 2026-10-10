package fr.schwanx.vox.ui.transcribe

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.schwanx.vox.data.local.preferences.SettingsRepository
import fr.schwanx.vox.domain.model.Segment
import fr.schwanx.vox.service.TranscriptionWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class TranscribeUiState(
    val audioPath: String = "",
    val fileName: String = "",
    val title: String = "",
    val language: String = "auto",
    val isRunning: Boolean = false,
    val progressPercent: Int = 0,
    val liveSegments: List<Segment> = emptyList(),
    val resultTranscriptionId: Long? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class TranscribeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TranscribeUiState())
    val uiState: StateFlow<TranscribeUiState> = _uiState.asStateFlow()

    private var workId: UUID? = null

    fun initialize(audioPath: String, title: String? = null) {
        val file = File(audioPath)
        val defaultTitle = title ?: if (file.exists()) file.nameWithoutExtension else "Enregistrement"

        viewModelScope.launch {
            val settings = settingsRepository.userSettingsFlow.first()
            _uiState.value = _uiState.value.copy(
                audioPath = audioPath,
                fileName = file.name,
                title = defaultTitle,
                language = settings.defaultLanguage
            )
            startTranscription()
        }
    }

    fun setLanguage(lang: String) {
        _uiState.value = _uiState.value.copy(language = lang)
    }

    fun startTranscription() {
        viewModelScope.launch {
            val settings = settingsRepository.userSettingsFlow.first()
            val state = _uiState.value

            val inputData = workDataOf(
                TranscriptionWorker.KEY_AUDIO_PATH to state.audioPath,
                TranscriptionWorker.KEY_MODEL_FILENAME to settings.selectedModelId,
                TranscriptionWorker.KEY_LANGUAGE to state.language,
                TranscriptionWorker.KEY_TITLE to state.title,
                TranscriptionWorker.KEY_NUM_THREADS to settings.numThreads
            )

            val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(inputData)
                .build()

            workId = request.id
            val workManager = WorkManager.getInstance(context)
            workManager.enqueue(request)

            _uiState.value = _uiState.value.copy(
                isRunning = true,
                errorMessage = null,
                progressPercent = 5
            )

            observeWork(request.id)
        }
    }

    private fun observeWork(id: UUID) {
        val workManager = WorkManager.getInstance(context)
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).collect { workInfo ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            _uiState.value = _uiState.value.copy(isRunning = true)
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val idResult = workInfo.outputData.getLong(TranscriptionWorker.KEY_RESULT_TRANSCRIPTION_ID, -1L)
                            _uiState.value = _uiState.value.copy(
                                isRunning = false,
                                progressPercent = 100,
                                resultTranscriptionId = if (idResult != -1L) idResult else null
                            )
                        }
                        WorkInfo.State.FAILED -> {
                            _uiState.value = _uiState.value.copy(
                                isRunning = false,
                                errorMessage = "La transcription a échoué. Vérifiez le format audio ou le modèle."
                            )
                        }
                        WorkInfo.State.CANCELLED -> {
                            _uiState.value = _uiState.value.copy(
                                isRunning = false,
                                errorMessage = "Transcription annulée."
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun cancelTranscription() {
        workId?.let { id ->
            WorkManager.getInstance(context).cancelWorkById(id)
        }
    }
}
