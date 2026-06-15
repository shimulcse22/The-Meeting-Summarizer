package com.shimul.meetingsummarizer.domain.transcription

import kotlinx.coroutines.flow.Flow

/**
 * On-device speech-to-text contract. Implemented with Vosk in a later milestone.
 * Kept as an interface so the rest of the app doesn't depend on a specific engine.
 */
interface Transcriber {

    /** Streaming transcription for live recording: emits partial/final text as it's recognized. */
    fun transcribeStream(): Flow<TranscriptUpdate>

    /** Batch transcription of an imported audio file (path/uri), with progress 0f..1f. */
    fun transcribeFile(sourcePath: String): Flow<TranscriptProgress>
}

data class TranscriptUpdate(val text: String, val isFinal: Boolean)

data class TranscriptProgress(val progress: Float, val partialText: String, val done: Boolean)
