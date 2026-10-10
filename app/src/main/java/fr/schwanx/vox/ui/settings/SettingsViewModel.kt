package fr.schwanx.vox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.schwanx.vox.data.local.preferences.SettingsRepository
import fr.schwanx.vox.data.repository.ModelRepository
import fr.schwanx.vox.domain.model.ModelDownloadState
import fr.schwanx.vox.domain.model.UserSettings
import fr.schwanx.vox.domain.model.WhisperModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelItemUiState(
    val model: WhisperModel,
    val state: ModelDownloadState,
    val isSelected: Boolean
)

data class SettingsUiState(
    val userSettings: UserSettings = UserSettings(),
    val modelsList: List<ModelItemUiState> = emptyList(),
    val totalDiskUsageDisplay: String = "0 Mo"
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _downloadingStates = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.userSettingsFlow,
        _downloadingStates
    ) { settings, downloadingMap ->
        val models = WhisperModel.AVAILABLE_MODELS.map { model ->
            val activeState = downloadingMap[model.id]
            val isDownloaded = modelRepository.isModelDownloaded(model.filename)
            val finalState = activeState ?: if (isDownloaded) ModelDownloadState.Downloaded else ModelDownloadState.NotDownloaded
            ModelItemUiState(
                model = model,
                state = finalState,
                isSelected = model.id == settings.selectedModelId
            )
        }

        val totalBytes = modelRepository.getTotalModelsDiskUsageBytes()
        val totalMb = totalBytes / (1024 * 1024)

        SettingsUiState(
            userSettings = settings,
            modelsList = models,
            totalDiskUsageDisplay = "$totalMb Mo"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedModelId(modelId)
        }
    }

    fun downloadModel(model: WhisperModel) {
        viewModelScope.launch {
            modelRepository.downloadModel(model).collect { state ->
                val updatedMap = _downloadingStates.value.toMutableMap()
                updatedMap[model.id] = state
                _downloadingStates.value = updatedMap

                if (state is ModelDownloadState.Downloaded) {
                    settingsRepository.setSelectedModelId(model.id)
                }
            }
        }
    }

    fun deleteModel(model: WhisperModel) {
        modelRepository.deleteModel(model.filename)
        val updatedMap = _downloadingStates.value.toMutableMap()
        updatedMap.remove(model.id)
        _downloadingStates.value = updatedMap
    }

    fun setDarkThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setDarkThemeMode(mode)
        }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColorEnabled(enabled)
        }
    }

    fun setNumThreads(threads: Int) {
        viewModelScope.launch {
            settingsRepository.setNumThreads(threads)
        }
    }

    fun setKeepAudioFiles(keep: Boolean) {
        viewModelScope.launch {
            settingsRepository.setKeepAudioFiles(keep)
        }
    }
}
