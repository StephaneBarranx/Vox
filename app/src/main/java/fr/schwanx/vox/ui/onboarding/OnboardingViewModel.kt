package fr.schwanx.vox.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.schwanx.vox.data.local.preferences.SettingsRepository
import fr.schwanx.vox.data.repository.ModelRepository
import fr.schwanx.vox.domain.model.ModelDownloadState
import fr.schwanx.vox.domain.model.WhisperModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val selectedModel: WhisperModel = WhisperModel.AVAILABLE_MODELS.first { it.isDefault },
    val downloadState: ModelDownloadState = ModelDownloadState.NotDownloaded,
    val isCompleted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        checkIfAlreadyDownloaded()
    }

    private fun checkIfAlreadyDownloaded() {
        val model = _uiState.value.selectedModel
        if (modelRepository.isModelDownloaded(model.filename)) {
            _uiState.value = _uiState.value.copy(downloadState = ModelDownloadState.Downloaded)
        }
    }

    fun selectModel(model: WhisperModel) {
        val isDownloaded = modelRepository.isModelDownloaded(model.filename)
        val state = if (isDownloaded) ModelDownloadState.Downloaded else ModelDownloadState.NotDownloaded
        _uiState.value = _uiState.value.copy(
            selectedModel = model,
            downloadState = state
        )
    }

    fun startDownload() {
        val model = _uiState.value.selectedModel
        viewModelScope.launch {
            modelRepository.downloadModel(model).collect { state ->
                _uiState.value = _uiState.value.copy(downloadState = state)
                if (state is ModelDownloadState.Downloaded) {
                    settingsRepository.setSelectedModelId(model.id)
                    settingsRepository.setOnboardingCompleted(true)
                    _uiState.value = _uiState.value.copy(isCompleted = true)
                }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            val model = _uiState.value.selectedModel
            settingsRepository.setSelectedModelId(model.id)
            settingsRepository.setOnboardingCompleted(true)
            _uiState.value = _uiState.value.copy(isCompleted = true)
        }
    }
}
