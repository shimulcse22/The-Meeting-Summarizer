package com.shimul.meetingsummarizer.domain.speech

import android.content.Context

/**
 * File transcription uses Whisper in EVERY flavor (Google STT can't process
 * files). The flavor split only affects the live recording engine.
 */
object FileTranscriberFactory {
    fun create(context: Context): FileTranscriber = WhisperFileTranscriber(context)
}
