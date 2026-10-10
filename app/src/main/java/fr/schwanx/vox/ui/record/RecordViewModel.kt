package fr.schwanx.vox.ui.record

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.schwanx.vox.core.audio.AudioRecorder
import fr.schwanx.vox.service.AudioRecordService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class RecordingStatus {
    IDLE, RECORDING, PAUSED, FINISHED
}

data class RecordUiState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val elapsedTimeMs: Long = 0L,
    val amplitudes: List<Float> = emptyList(),
    val outputFile: File? = null
)

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val audioRecorder: AudioRecorder
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val recentAmplitudes = mutableListOf<Float>()

    init {
        viewModelScope.launch {
            audioRecorder.elapsedTimeMs.collect { ms ->
                _uiState.value = _uiState.value.copy(elapsedTimeMs = ms)
            }
        }

        viewModelScope.launch {
            audioRecorder.amplitudeFlow.collect { amp ->
                recentAmplitudes.add(amp)
                if (recentAmplitudes.size > 50) {
                    recentAmplitudes.removeAt(0)
                }
                _uiState.value = _uiState.value.copy(amplitudes = recentAmplitudes.toList())
            }
        }
    }

    fun startRecording() {
        val file = File(context.cacheDir, "vox_rec_${System.currentTimeMillis()}.wav")
        _uiState.value = _uiState.value.copy(
            status = RecordingStatus.RECORDING,
            outputFile = file
        )
        recentAmplitudes.clear()
        AudioRecordService.startService(context, file)
    }

    fun pauseRecording() {
        audioRecorder.pauseRecording()
        _uiState.value = _uiState.value.copy(status = RecordingStatus.PAUSED)
    }

    fun resumeRecording() {
        audioRecorder.resumeRecording()
        _uiState.value = _uiState.value.copy(status = RecordingStatus.RECORDING)
    }

    fun stopRecording() {
        viewModelScope.launch {
            AudioRecordService.stopService(context)
            _uiState.value = _uiState.value.copy(status = RecordingStatus.FINISHED)
        }
    }

    fun cancelRecording() {
        viewModelScope.launch {
            AudioRecordService.stopService(context)
            audioRecorder.cancelRecording()
            recentAmplitudes.clear()
            _uiState.value = RecordUiState()
        }
    }
}
