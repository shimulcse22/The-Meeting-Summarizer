package com.shimul.meetingsummarizer.domain.speech

import android.content.Context
import com.shimul.meetingsummarizer.data.audio.AudioRecorder
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.WhisperCpuConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Live transcription using on-device Whisper (whisper.cpp).
 *
 * Whisper is not streaming, so we capture audio continuously and transcribe it in
 * fixed [CHUNK_SECONDS]-second windows, appending each result. A channel decouples
 * audio capture from the (slower) inference so no audio is dropped.
 */
class WhisperEngine(context: Context) : SpeechEngine {

    override val name: String = "Whisper (small, multilingual)"

    private val appContext = context.applicationContext
    private val recorder = AudioRecorder()
    private val modelManager = WhisperModelManager(appContext)

    private var whisper: WhisperContext? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val finalized = StringBuilder()

    private var producerJob: Job? = null
    private var consumerJob: Job? = null

    // Saved from start() so pause/resume can re-create capture without losing state.
    private var language: String = "auto"
    private var onTranscript: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override suspend fun ensureReady(onProgress: (Float) -> Unit) {
        modelManager.ensureModel(onProgress)
        if (whisper == null) {
            whisper = WhisperContext.createContextFromFile(modelManager.modelFile.absolutePath)
        }
    }

    override fun start(
        language: String,
        onTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        this.language = language
        this.onTranscript = onTranscript
        this.onError = onError
        finalized.setLength(0)
        startCapture()
    }

    override fun pause() {
        // Ends the producer loop (closes the channel); finalized transcript is kept.
        recorder.stop()
    }

    override fun resume() {
        // Wait for the previous capture to fully release the mic, then continue.
        scope.launch {
            producerJob?.join()
            startCapture()
        }
    }

    override fun stop() {
        // Producer flushes the final window and closes the channel; the consumer
        // drains it and finishes, so the last words still get transcribed.
        recorder.stop()
    }

    private fun startCapture() {
        val channel = Channel<FloatArray>(Channel.UNLIMITED)

        // Consumer: transcribe each audio window and append the text.
        consumerJob = scope.launch {
            val threads = WhisperCpuConfig.preferredThreadCount
            for (chunk in channel) {
                val ctx = whisper ?: continue
                val text = runCatching { ctx.transcribe(chunk, threads, language) }
                    .onFailure { onError?.invoke(it.message ?: "Transcription failed") }
                    .getOrDefault("")
                    .stripNoise()
                if (text.isNotEmpty()) {
                    finalized.append(text).append(' ')
                    onTranscript?.invoke(finalized.toString().trim())
                }
            }
        }

        // Producer: capture audio and emit fixed-size windows into the channel.
        producerJob = scope.launch(Dispatchers.IO) {
            val samplesPerChunk = AudioRecorder.SAMPLE_RATE * CHUNK_SECONDS
            val buffer = ShortArray(samplesPerChunk)
            var filled = 0
            try {
                recorder.record { data, len ->
                    var offset = 0
                    while (offset < len) {
                        val toCopy = minOf(len - offset, samplesPerChunk - filled)
                        System.arraycopy(data, offset, buffer, filled, toCopy)
                        filled += toCopy
                        offset += toCopy
                        if (filled == samplesPerChunk) {
                            channel.trySend(buffer.toFloat(filled))
                            filled = 0
                        }
                    }
                }
            } finally {
                if (filled > 0) channel.trySend(buffer.toFloat(filled))
                channel.close()
            }
        }
    }

    override fun release() {
        recorder.stop()
        scope.launch {
            whisper?.release()
            whisper = null
            scope.cancel()
        }
    }

    private fun ShortArray.toFloat(length: Int): FloatArray {
        val out = FloatArray(length)
        for (i in 0 until length) out[i] = this[i] / 32768f
        return out
    }

    /** Whisper emits things like "[BLANK_AUDIO]" / "(music)" on silence — drop them. */
    private fun String.stripNoise(): String =
        replace(Regex("\\[.*?]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .trim()

    companion object {
        // Balance for the heavier multilingual model: enough context for accuracy
        // and throughput, while still updating every few seconds.
        private const val CHUNK_SECONDS = 5
    }
}
