package com.shimul.meetingsummarizer.domain.speech

/**
 * Transcribes a pre-decoded audio file (16 kHz mono float chunks). Provided per
 * flavor via [FileTranscriberFactory]: Whisper supports it; Google STT does not
 * (SpeechRecognizer only works on the live mic), so it reports [isSupported] = false.
 */
interface FileTranscriber {
    val isSupported: Boolean

    /** Prepare the engine (e.g. download/load the model). */
    suspend fun ensureReady(onProgress: (Float) -> Unit)

    /** Transcribe one window of 16 kHz mono samples. */
    suspend fun transcribeChunk(samples16kMono: FloatArray, language: String): String

    fun release()
}
