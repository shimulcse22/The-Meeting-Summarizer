package com.shimul.meetingsummarizer.domain.speech

/**
 * Engine-agnostic speech-to-text contract. Each build flavor provides its own
 * implementation via [SpeechEngineFactory]:
 *  - staging → Whisper (whisper.cpp)
 *  - qa      → Google on-device SpeechRecognizer
 *
 * An engine owns its own audio capture and reports the full cumulative transcript
 * so far through the [start] callback. The UI just displays whatever it emits.
 */
interface SpeechEngine {

    /** Human-readable name shown in the UI (e.g. "Whisper", "Google on-device STT"). */
    val name: String

    /**
     * Prepare the engine before first use (e.g. download a model). Reports progress
     * 0f..1f. Default: nothing to prepare.
     */
    suspend fun ensureReady(onProgress: (Float) -> Unit) {}

    /**
     * Begin capturing audio and transcribing. [language] is a code like "en",
     * "bn", or "auto". [onTranscript] receives the full cumulative transcript text
     * each time it updates; [onError] reports a user-facing failure message.
     * Must be called after RECORD_AUDIO permission is granted.
     */
    fun start(
        language: String,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit
    )

    /** Pause capture/transcription, keeping the transcript so far. */
    fun pause()

    /** Resume capture/transcription, continuing the same session. */
    fun resume()

    /** Stop transcribing. */
    fun stop()

    /** Release any held resources. */
    fun release() {}
}
