package com.shimul.meetingsummarizer.domain.transcription

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Locates the on-device Vosk model and, if missing, downloads + unzips it.
 *
 * This is a minimal first-use download; the full model-management UI is MS-22.
 * Uses the small English model (~40 MB) so it stays light and works offline
 * once downloaded.
 */
class VoskModelManager(private val context: Context) {

    private val modelDir = File(context.filesDir, "vosk-model-en")

    /** True once the model is present and ready to load. */
    fun isReady(): Boolean =
        File(modelDir, "am").exists() || (modelDir.list()?.isNotEmpty() == true)

    /** Loads the Vosk model into memory. Call only when [isReady] is true. */
    suspend fun loadModel(): Model = withContext(Dispatchers.IO) {
        Model(modelDir.absolutePath)
    }

    /**
     * Ensures the model exists locally, downloading it if needed.
     * [onProgress] reports 0f..1f (download is ~80% of the work, unzip the rest).
     */
    suspend fun ensureModel(onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        if (isReady()) {
            onProgress(1f)
            return@withContext
        }

        val tmpZip = File(context.cacheDir, "vosk-model.zip")
        downloadTo(MODEL_URL, tmpZip) { p -> onProgress(p * 0.8f) }

        val tmpDir = File(context.cacheDir, "vosk-unzip").apply {
            deleteRecursively(); mkdirs()
        }
        unzip(tmpZip, tmpDir)
        onProgress(0.95f)

        // The archive contains a single top-level folder; promote it to modelDir.
        val extracted = tmpDir.listFiles()?.firstOrNull { it.isDirectory }
            ?: error("Unexpected model archive layout")
        if (modelDir.exists()) modelDir.deleteRecursively()
        if (!extracted.renameTo(modelDir)) {
            extracted.copyRecursively(modelDir, overwrite = true)
        }

        tmpZip.delete()
        tmpDir.deleteRecursively()
        onProgress(1f)
    }

    private fun downloadTo(urlStr: String, dest: File, onProgress: (Float) -> Unit) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            connect()
        }
        try {
            val total = conn.contentLength.toLong()
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
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
    }

    private fun unzip(zip: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                // Zip-slip protection
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                    throw SecurityException("Bad zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    companion object {
        private const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    }
}
