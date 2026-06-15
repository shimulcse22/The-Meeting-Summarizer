package com.shimul.meetingsummarizer.ui.screens.record

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shimul.meetingsummarizer.data.audio.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RecordUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val error: String? = null
)

class RecordViewModel : ViewModel() {

    private val recorder = AudioRecorder()

    private val _state = MutableStateFlow(RecordUiState())
    val state = _state.asStateFlow()

    private var timerJob: Job? = null
    private var recordJob: Job? = null

    /** Begin recording. Caller must ensure RECORD_AUDIO permission is granted. */
    fun start() {
        if (_state.value.isRecording) return
        _state.update { it.copy(isRecording = true, elapsedMs = 0L, error = null) }

        val startedAt = SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch {
            while (isActive) {
                _state.update { it.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt) }
                delay(250)
            }
        }

        recordJob = viewModelScope.launch {
            try {
                // MS-2 will consume these chunks for live transcription.
                recorder.record { _, _ -> }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(isRecording = false, error = t.message ?: "Recording failed")
                }
                timerJob?.cancel()
            }
        }
    }

    fun stop() {
        recorder.stop()
        recordJob?.cancel()
        timerJob?.cancel()
        _state.update { it.copy(isRecording = false) }
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }
}
