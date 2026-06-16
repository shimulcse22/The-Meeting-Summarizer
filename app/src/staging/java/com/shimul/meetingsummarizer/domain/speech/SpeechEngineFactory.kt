package com.shimul.meetingsummarizer.domain.speech

import android.content.Context

/** Staging flavor → Whisper (whisper.cpp) on-device engine. */
object SpeechEngineFactory {
    fun create(context: Context): SpeechEngine = WhisperEngine(context)
}
