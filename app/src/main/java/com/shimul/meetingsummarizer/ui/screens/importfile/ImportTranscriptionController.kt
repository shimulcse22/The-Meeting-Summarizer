package com.shimul.meetingsummarizer.ui.screens.importfile

import android.content.Context
import android.net.Uri
import com.shimul.meetingsummarizer.data.audio.AudioDecoder
import com.shimul.meetingsummarizer.data.local.AppDatabase
import com.shimul.meetingsummarizer.data.repository.MeetingRepository
import com.shimul.meetingsummarizer.domain.model.Meeting
import com.shimul.meetingsummarizer.domain.model.MeetingSource
import com.shimul.meetingsummarizer.domain.speech.FileTranscriberFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ImportState {
    data object Idle : ImportState
    data class Preparing(val progress: Float) : ImportState
    data class Running(val progress: Float, val partial: String) : ImportState
    data class Done(val meetingId: String) : ImportState
    data class Failed(val message: String) : ImportState
}

/**
 * Process-level owner of an in-progress file transcription, so the work continues
 * (and its result is saved) even if the user leaves the Import screen.
 */
object ImportTranscriptionController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    private var job: Job? = null

    fun isRunning(): Boolean = job?.isActive == true

    /** Reset to idle once a result has been consumed (ignored while running). */
    fun reset() {
        if (!isRunning()) _state.value = ImportState.Idle
    }

    fun start(appContext: Context, uri: Uri, fileName: String, language: String) {
        if (isRunning()) return
        _state.value = ImportState.Preparing(0f)
        job = scope.launch {
            try {
                val transcriber = FileTranscriberFactory.create(appContext)
                if (!transcriber.isSupported) {
                    _state.value = ImportState.Failed(
                        "This build can't transcribe files. Use the Whisper (staging) build to " +
                            "transcribe imported audio."
                    )
                    return@launch
                }

                transcriber.ensureReady { p -> _state.value = ImportState.Preparing(p) }

                val builder = StringBuilder()
                var progress = 0f
                _state.value = ImportState.Running(0f, "")

                AudioDecoder.decodeToMonoChunks(
                    context = appContext,
                    uri = uri,
                    onProgress = { p ->
                        progress = p
                        _state.value = ImportState.Running(p, builder.toString().trim())
                    },
                    onChunk = { chunk ->
                        val text = transcriber.transcribeChunk(chunk, language)
                        if (text.isNotBlank()) builder.append(text).append(' ')
                        _state.value = ImportState.Running(progress, builder.toString().trim())
                    }
                )

                val transcript = builder.toString().trim()
                if (transcript.isBlank()) {
                    _state.value = ImportState.Failed("No speech was detected in this file.")
                    return@launch
                }

                val repository = MeetingRepository(AppDatabase.get(appContext).meetingDao())
                val meeting = Meeting(
                    id = UUID.randomUUID().toString(),
                    title = fileName.substringBeforeLast('.').ifBlank { fileName },
                    createdAt = System.currentTimeMillis(),
                    source = MeetingSource.IMPORTED,
                    language = language,
                    transcriptText = transcript
                )
                repository.save(meeting)
                _state.value = ImportState.Done(meeting.id)
            } catch (t: Throwable) {
                _state.value = ImportState.Failed(friendlyError(t))
            }
        }
    }

    private fun friendlyError(t: Throwable): String = when {
        t is android.media.MediaCodec.CodecException ->
            "This file's audio couldn't be decoded — it may be corrupt or in an " +
                "unsupported format. Please pick a different file."
        t is SecurityException ->
            "Can't access this file anymore. Please choose it again."
        t is OutOfMemoryError ->
            "This file is too large to process on this device."
        !t.message.isNullOrBlank() -> t.message!!
        else -> "Couldn't transcribe this file. Please pick a different audio or video file."
    }
}
