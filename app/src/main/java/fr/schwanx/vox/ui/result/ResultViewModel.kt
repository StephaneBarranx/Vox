package fr.schwanx.vox.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.schwanx.vox.core.audio.AudioPlayer
import fr.schwanx.vox.core.util.SrtGenerator
import fr.schwanx.vox.core.util.TxtGenerator
import fr.schwanx.vox.data.repository.TranscriptionRepository
import fr.schwanx.vox.domain.model.Transcription
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ResultUiState(
    val transcription: Transcription? = null,
    val isEditing: Boolean = false,
    val editedTitle: String = "",
    val editedText: String = "",
    val activeTab: Int = 0 // 0 = Texte complet, 1 = Segments horodatés
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TranscriptionRepository,
    val audioPlayer: AudioPlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    fun loadTranscription(id: Long) {
        viewModelScope.launch {
            repository.observeTranscriptionById(id).collect { item ->
                if (item != null) {
                    _uiState.value = _uiState.value.copy(
                        transcription = item,
                        editedTitle = item.title,
                        editedText = item.text
                    )
                    item.audioPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            audioPlayer.prepareAndPlay(file)
                            audioPlayer.pause() // Prepared, paused by default
                        }
                    }
                }
            }
        }
    }

    fun setTab(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setEditing(editing: Boolean) {
        _uiState.value = _uiState.value.copy(isEditing = editing)
    }

    fun onTitleChanged(newTitle: String) {
        _uiState.value = _uiState.value.copy(editedTitle = newTitle)
    }

    fun onTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(editedText = newText)
    }

    fun saveChanges() {
        val current = _uiState.value.transcription ?: return
        viewModelScope.launch {
            val updated = current.copy(
                title = _uiState.value.editedTitle,
                text = _uiState.value.editedText
            )
            repository.updateTranscription(updated)
            _uiState.value = _uiState.value.copy(isEditing = false)
        }
    }

    fun copyToClipboard() {
        val text = _uiState.value.transcription?.text ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcription Vox", text)
        clipboard.setPrimaryClip(clip)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText() {
        val text = _uiState.value.transcription?.text ?: return
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Partager la transcription Vox")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun exportAsTxt() {
        val transcription = _uiState.value.transcription ?: return
        val exportsDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportsDir, "${transcription.title.replace(" ", "_")}.txt")

        file.writeText(TxtGenerator.generateTxt(transcription))
        shareFile(file, "text/plain")
    }

    fun exportAsSrt() {
        val transcription = _uiState.value.transcription ?: return
        val exportsDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportsDir, "${transcription.title.replace(" ", "_")}.srt")

        file.writeText(SrtGenerator.generateSrt(transcription.segments))
        shareFile(file, "application/x-subrip")
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Exporter le fichier Vox").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun deleteTranscription(onDeleted: () -> Unit) {
        val current = _uiState.value.transcription ?: return
        viewModelScope.launch {
            audioPlayer.release()
            repository.deleteTranscription(current.id)
            onDeleted()
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
