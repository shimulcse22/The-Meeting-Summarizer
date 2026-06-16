package com.shimul.meetingsummarizer.ui.screens.importfile

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import com.shimul.meetingsummarizer.domain.speech.FileTranscriberFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ImportUiState(
    val uri: Uri? = null,
    val fileName: String? = null,
    val language: String = "auto"
)

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application

    private val _state = MutableStateFlow(ImportUiState())
    val state = _state.asStateFlow()

    /** Whether this build can transcribe files at all (false for the Google STT flavor). */
    val fileSupported: Boolean = FileTranscriberFactory.create(application).isSupported

    val jobState: StateFlow<ImportState> = ImportTranscriptionController.state

    fun onFileSelected(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        ImportTranscriptionController.reset()
        _state.update { it.copy(uri = uri, fileName = queryDisplayName(uri)) }
    }

    fun setLanguage(code: String) {
        if (ImportTranscriptionController.isRunning()) return
        _state.update { it.copy(language = code) }
    }

    fun transcribe() {
        val current = _state.value
        val uri = current.uri ?: return
        ImportTranscriptionController.start(
            appContext = appContext,
            uri = uri,
            fileName = current.fileName ?: "Imported meeting",
            language = current.language
        )
    }

    fun consumeDone() {
        ImportTranscriptionController.reset()
    }

    private fun queryDisplayName(uri: Uri): String {
        var name = "Selected file"
        runCatching {
            appContext.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) name = c.getString(index)
                    }
                }
        }
        return name
    }
}
