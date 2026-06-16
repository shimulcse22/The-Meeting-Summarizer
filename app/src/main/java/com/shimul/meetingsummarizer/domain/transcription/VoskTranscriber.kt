package com.shimul.meetingsummarizer.domain.transcription

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Feeds PCM audio (16 kHz mono short samples) into a Vosk [Recognizer] and
 * returns partial/final text as recognition progresses.
 *
 * Usage: [begin] once, [acceptSamples] for each audio chunk, [finish] at the end.
 */
class VoskTranscriber(private val model: Model) {

    private var recognizer: Recognizer? = null

    fun begin() {
        recognizer?.close()
        recognizer = Recognizer(model, SAMPLE_RATE)
    }

    /** Returns the latest recognition result for this chunk. */
    fun acceptSamples(buffer: ShortArray, length: Int): TranscriptResult {
        val r = recognizer ?: return TranscriptResult.None
        return if (r.acceptWaveForm(buffer, length)) {
            TranscriptResult.Final(extract(r.result, "text"))
        } else {
            TranscriptResult.Partial(extract(r.partialResult, "partial"))
        }
    }

    /** Flushes any remaining recognized text and releases the recognizer. */
    fun finish(): String {
        val r = recognizer ?: return ""
        val tail = extract(r.finalResult, "text")
        r.close()
        recognizer = null
        return tail
    }

    private fun extract(json: String, key: String): String =
        runCatching { JSONObject(json).optString(key).trim() }.getOrDefault("")

    companion object {
        const val SAMPLE_RATE = 16_000f
    }
}

sealed interface TranscriptResult {
    data object None : TranscriptResult
    data class Partial(val text: String) : TranscriptResult
    data class Final(val text: String) : TranscriptResult
}
