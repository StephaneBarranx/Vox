package fr.schwanx.vox.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.schwanx.vox.data.local.preferences.SettingsRepository
import fr.schwanx.vox.data.repository.TranscriptionRepository
import fr.schwanx.vox.domain.model.Transcription
import fr.schwanx.vox.domain.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val searchQuery: String = "",
    val transcriptions: List<Transcription> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val lastDeletedTranscription: Transcription? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transcriptionRepository: TranscriptionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        _searchQuery,
        transcriptionRepository.getAllTranscriptions(),
        settingsRepository.userSettingsFlow
    ) { query, allTranscriptions, settings ->
        val filtered = if (query.isBlank()) {
            allTranscriptions
        } else {
            allTranscriptions.filter {
                it.title.contains(query, ignoreCase = true) || it.text.contains(query, ignoreCase = true)
            }
        }
        HomeUiState(
            searchQuery = query,
            transcriptions = filtered,
            settings = settings
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteTranscription(transcription: Transcription) {
        viewModelScope.launch {
            transcriptionRepository.deleteTranscription(transcription.id)
        }
    }

    fun restoreTranscription(transcription: Transcription) {
        viewModelScope.launch {
            transcriptionRepository.saveTranscription(transcription)
        }
    }
}
