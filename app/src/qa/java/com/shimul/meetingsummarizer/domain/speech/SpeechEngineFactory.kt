package com.shimul.meetingsummarizer.domain.speech

import android.content.Context

/** QA flavor → Google on-device SpeechRecognizer. */
object SpeechEngineFactory {
    fun create(context: Context): SpeechEngine = GoogleSttEngine(context)
}
