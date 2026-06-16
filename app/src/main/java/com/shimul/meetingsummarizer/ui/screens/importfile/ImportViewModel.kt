package com.shimul.meetingsummarizer.ui.screens.importfile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ImportUiState(
    val uri: Uri? = null,
    val fileName: String? = null
)

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ImportUiState())
    val state = _state.asStateFlow()

    fun onFileSelected(uri: Uri) {
        // Hold onto read access so the file can be transcribed later (MS-7).
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        _state.update { it.copy(uri = uri, fileName = queryDisplayName(uri)) }
    }

    fun clear() {
        _state.update { ImportUiState() }
    }

    private fun queryDisplayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        var name = "Selected file"
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val index = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = c.getString(index)
                }
            }
        }
        return name
    }
}
