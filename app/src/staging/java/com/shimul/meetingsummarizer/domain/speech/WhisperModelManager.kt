package com.shimul.meetingsummarizer.domain.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads + stores the Whisper GGML model on first use (offline afterwards).
 * Default: base.en (~142 MB) — a good accuracy/size balance for English.
 */
class WhisperModelManager(context: Context) {

    val modelFile = File(context.applicationContext.filesDir, MODEL_NAME)

    fun isReady(): Boolean = modelFile.exists() && modelFile.length() > 0

    suspend fun ensureModel(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady()) {
            onProgress(1f)
            return@withContext
        }

        val tmp = File(modelFile.parentFile, "$MODEL_NAME.part")
        val conn = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MeetingSummarizer")
            connect()
        }
        try {
            val total = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(8 * 1024)
                    var readTotal = 0L
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        readTotal += read
                        if (total > 0) onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (!tmp.renameTo(modelFile)) {
            tmp.copyTo(modelFile, overwrite = true)
            tmp.delete()
        }
        onProgress(1f)
    }

    companion object {
        // Multilingual model (handles English AND Bangla). "small" is the realistic
        // minimum for usable Bangla. Lighter/faster: "ggml-base.bin". Best quality
        // (heavy): "ggml-medium.bin".
        private const val MODEL_NAME = "ggml-small.bin"
        private const val MODEL_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin"
    }
}
