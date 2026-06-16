package com.shimul.meetingsummarizer.domain.speech

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import com.whispercpp.whisper.WhisperCpuConfig

/**
 * Transcribes decoded audio with on-device Whisper. Used by ALL flavors for file
 * import, since Google STT can only transcribe the live mic, not files.
 */
class WhisperFileTranscriber(context: Context) : FileTranscriber {

    override val isSupported: Boolean = true

    private val modelManager = WhisperModelManager(context.applicationContext)
    private var whisper: WhisperContext? = null

    override suspend fun ensureReady(onProgress: (Float) -> Unit) {
        modelManager.ensureModel(onProgress)
        if (whisper == null) {
            whisper = WhisperContext.createContextFromFile(modelManager.modelFile.absolutePath)
        }
    }

    override suspend fun transcribeChunk(samples16kMono: FloatArray, language: String): String {
        val ctx = whisper ?: return ""
        return ctx.transcribe(samples16kMono, WhisperCpuConfig.preferredThreadCount, language)
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .trim()
    }

    override fun release() {
        // Context kept loaded for reuse across imports; freed when the process ends.
    }
}
