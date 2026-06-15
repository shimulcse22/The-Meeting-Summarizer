package com.shimul.meetingsummarizer.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Captures microphone audio as 16 kHz mono PCM — the format Vosk expects (M2).
 *
 * For MS-1 we only need to start/stop capture and keep the mic active. Each chunk
 * is delivered via [onChunk]; later milestones feed those chunks to the
 * transcriber. Audio is never written to disk (privacy: transcript-text only).
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile
    private var recording = false

    /**
     * Suspends while recording. Reads audio in a loop until [stop] is called or
     * the coroutine is cancelled. Must be called only after RECORD_AUDIO is granted.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(onChunk: (ShortArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        val bufferSize = if (minBuffer > 0) minBuffer * 2 else SAMPLE_RATE
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize
        )

        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }

        val buffer = ShortArray(bufferSize)
        try {
            audioRecord.startRecording()
            recording = true
            while (recording) {
                coroutineContext.ensureActive()
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) onChunk(buffer, read)
            }
        } finally {
            recording = false
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
    }

    /** Signals the recording loop to finish. */
    fun stop() {
        recording = false
    }
}
