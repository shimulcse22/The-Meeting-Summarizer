package com.shimul.meetingsummarizer.domain.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Live transcription using Android's on-device SpeechRecognizer (Google).
 *
 * SpeechRecognizer is utterance-based — it stops after a pause — so for continuous
 * meeting capture we automatically restart it after each result/error while the
 * user is still recording.
 *
 * All SpeechRecognizer calls must happen on the main thread.
 */
class GoogleSttEngine(context: Context) : SpeechEngine {

    override val name: String = "Google on-device STT"

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private val finalized = StringBuilder()

    @Volatile
    private var listening = false
    private var onTranscript: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var languageCode: String = "en"
    private var localeTag: String = "en-US"

    override fun start(
        language: String,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onTranscript = onTranscript
        this.onError = onError
        this.languageCode = language
        this.localeTag = toLocale(language)
        finalized.clear()
        listening = true
        main.post { startSession() }
    }

    private fun toLocale(language: String): String = when (language) {
        "bn" -> "bn-BD"
        "en" -> "en-US"
        else -> "en-US" // "auto" isn't supported here; default to English
    }

    private fun isEnglish(): Boolean = languageCode == "en"

    private fun startSession() {
        if (!listening) return
        val rec = createRecognizer()
        recognizer = rec
        rec.setRecognitionListener(listener)
        rec.startListening(buildIntent())
    }

    private fun createRecognizer(): SpeechRecognizer {
        // On-device recognition only reliably exists for English. For Bangla (and
        // other languages) use the standard recognizer so Google's ONLINE model
        // can handle it — on-device Bangla isn't available on most phones.
        val useOnDevice = isEnglish() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

        return if (useOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Offline only for English; Bangla needs the online model.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, isEnglish())
        }

    private fun restart() {
        recognizer?.destroy()
        recognizer = null
        if (listening) main.postDelayed({ startSession() }, 50)
    }

    private fun emitFinal() {
        onTranscript?.invoke(finalized.toString().trim())
    }

    private fun emitPartial(partial: String) {
        onTranscript?.invoke((finalized.toString() + partial).trim())
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (text.isNotEmpty()) emitPartial(text)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            if (text.isNotBlank()) finalized.append(text).append(' ')
            emitFinal()
            restart() // keep listening for the next utterance
        }

        override fun onError(error: Int) {
            when (error) {
                // Expected during pauses / recognizer churn — keep listening.
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> restart()

                // Fatal for this language/session — stop and tell the user.
                else -> {
                    listening = false
                    recognizer?.destroy()
                    recognizer = null
                    onError?.invoke(messageFor(error))
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "“${localeTag}” isn't available in this device's Google speech recognizer. " +
                "Add it in Settings → System → Languages & input → On-device / Voice input, " +
                "or use the Whisper (staging) build for Bangla."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "This language needs the internet. Check your connection and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission is required."
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
            "Speech server error. Please try again."
        else -> "Speech recognition error (code $error)."
    }

    override fun pause() {
        // Stop listening but keep the accumulated transcript for resume().
        listening = false
        main.post {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    override fun resume() {
        listening = true
        main.post { startSession() }
    }

    override fun stop() {
        listening = false
        main.post {
            recognizer?.stopListening()
            recognizer?.destroy()
            recognizer = null
        }
    }

    override fun release() = stop()
}
