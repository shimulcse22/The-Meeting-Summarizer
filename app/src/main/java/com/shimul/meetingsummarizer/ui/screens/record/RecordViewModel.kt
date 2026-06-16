package com.shimul.meetingsummarizer.ui.screens.record

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimul.meetingsummarizer.data.local.AppDatabase
import com.shimul.meetingsummarizer.data.repository.MeetingRepository
import com.shimul.meetingsummarizer.domain.model.Meeting
import com.shimul.meetingsummarizer.domain.model.MeetingSource
import com.shimul.meetingsummarizer.domain.speech.SpeechEngine
import com.shimul.meetingsummarizer.domain.speech.SpeechEngineFactory
import com.shimul.meetingsummarizer.recording.RecordingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ModelStatus { Idle, Preparing, Ready }

data class RecordUiState(
    val engineName: String = "",
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isSaving: Boolean = false,
    val elapsedMs: Long = 0L,
    val modelStatus: ModelStatus = ModelStatus.Idle,
    val prepareProgress: Float = 0f,
    val transcript: String = "",
    val language: String = "en",
    val savedMeetingId: String? = null,
    val error: String? = null
)

class RecordViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application

    // The engine implementation depends on the active build flavor.
    private val engine: SpeechEngine = SpeechEngineFactory.create(application)

    private val repository = MeetingRepository(AppDatabase.get(application).meetingDao())

    private val _state = MutableStateFlow(RecordUiState(engineName = engine.name))
    val state = _state.asStateFlow()

    private var timerJob: Job? = null
    private var sessionJob: Job? = null

    // Timer accounting that excludes paused time.
    private var accumulatedMs = 0L
    private var segmentStartedAt = 0L

    /** Begin recording + live transcription. Caller ensures RECORD_AUDIO is granted. */
    fun start() {
        if (_state.value.isRecording || sessionJob?.isActive == true) return

        sessionJob = viewModelScope.launch {
            try {
                // 1) Prepare the engine (e.g. download a model on first use).
                _state.update {
                    it.copy(modelStatus = ModelStatus.Preparing, prepareProgress = 0f, error = null)
                }
                engine.ensureReady { p -> _state.update { it.copy(prepareProgress = p) } }

                // 2) Start the session.
                accumulatedMs = 0L
                _state.update {
                    it.copy(
                        isRecording = true,
                        isPaused = false,
                        modelStatus = ModelStatus.Ready,
                        elapsedMs = 0L,
                        transcript = "",
                        error = null
                    )
                }
                startTimer()

                // Keep recording alive in the background (mic + notification).
                RecordingService.start(appContext, paused = false)

                // 3) Engine owns audio capture; it pushes cumulative transcript updates.
                engine.start(
                    language = _state.value.language,
                    onTranscript = { full -> _state.update { it.copy(transcript = full) } },
                    onError = { msg -> onEngineError(msg) }
                )
            } catch (t: Throwable) {
                timerJob?.cancel()
                _state.update {
                    it.copy(
                        isRecording = false,
                        modelStatus = ModelStatus.Idle,
                        error = t.message ?: "Recording failed"
                    )
                }
            }
        }
    }

    /** Pause the current session (keeps transcript and freezes the timer). */
    fun pause() {
        if (!_state.value.isRecording || _state.value.isPaused) return
        engine.pause()
        timerJob?.cancel()
        accumulatedMs += SystemClock.elapsedRealtime() - segmentStartedAt
        _state.update { it.copy(isPaused = true, elapsedMs = accumulatedMs) }
        RecordingService.update(appContext, paused = true)
    }

    /** Resume a paused session. */
    fun resume() {
        if (!_state.value.isRecording || !_state.value.isPaused) return
        engine.resume()
        _state.update { it.copy(isPaused = false) }
        startTimer()
        RecordingService.update(appContext, paused = false)
    }

    fun stop() {
        if (!_state.value.isRecording) return
        engine.stop()
        timerJob?.cancel()
        RecordingService.stop(appContext)
        _state.update { it.copy(isRecording = false, isPaused = false, isSaving = true) }
        viewModelScope.launch { finalizeAndSave() }
    }

    /** Clear the navigation event after the screen has consumed it. */
    fun consumeSavedId() {
        _state.update { it.copy(savedMeetingId = null) }
    }

    private suspend fun finalizeAndSave() {
        // The last transcription may still be arriving (Whisper flushes its final
        // chunk asynchronously), so wait until the transcript stops changing.
        var lastText = _state.value.transcript
        var stableMs = 0L
        var waitedMs = 0L
        while (stableMs < SETTLE_MS && waitedMs < MAX_SETTLE_MS) {
            delay(POLL_MS)
            waitedMs += POLL_MS
            val current = _state.value.transcript
            if (current == lastText) {
                stableMs += POLL_MS
            } else {
                stableMs = 0
                lastText = current
            }
        }

        val transcript = lastText.trim()
        if (transcript.isBlank()) {
            // Nothing was captured — don't save an empty meeting.
            _state.update { it.copy(isSaving = false) }
            return
        }

        val meeting = Meeting(
            id = UUID.randomUUID().toString(),
            title = defaultTitle(),
            createdAt = System.currentTimeMillis(),
            source = MeetingSource.RECORDED,
            language = _state.value.language,
            transcriptText = transcript
        )
        runCatching { repository.save(meeting) }
            .onSuccess { _state.update { it.copy(isSaving = false, savedMeetingId = meeting.id) } }
            .onFailure { t ->
                _state.update { it.copy(isSaving = false, error = t.message ?: "Couldn't save the meeting") }
            }
    }

    private fun defaultTitle(): String {
        val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
        return "Meeting • ${formatter.format(Date())}"
    }

    /** Set the transcription language ("en", "bn", or "auto"). Ignored while recording. */
    fun setLanguage(code: String) {
        if (_state.value.isRecording) return
        _state.update { it.copy(language = code) }
    }

    private fun onEngineError(message: String) {
        engine.stop()
        timerJob?.cancel()
        RecordingService.stop(appContext)
        // If recording fails mid-session, still save whatever was transcribed.
        if (_state.value.transcript.isNotBlank()) {
            _state.update { it.copy(isRecording = false, isPaused = false, isSaving = true, error = message) }
            viewModelScope.launch { finalizeAndSave() }
        } else {
            _state.update { it.copy(isRecording = false, isPaused = false, error = message) }
        }
    }

    private fun startTimer() {
        segmentStartedAt = SystemClock.elapsedRealtime()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = accumulatedMs + (SystemClock.elapsedRealtime() - segmentStartedAt)
                _state.update { it.copy(elapsedMs = elapsed) }
                delay(250)
            }
        }
    }

    override fun onCleared() {
        engine.stop()
        engine.release()
        timerJob?.cancel()
        sessionJob?.cancel()
        RecordingService.stop(appContext)
        super.onCleared()
    }

    companion object {
        private const val POLL_MS = 250L
        private const val SETTLE_MS = 1000L
        private const val MAX_SETTLE_MS = 8000L
    }
}
