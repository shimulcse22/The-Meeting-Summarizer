package com.whispercpp.whisper

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Kotlin handle to a native whisper.cpp context. All native calls run on a single
 * dedicated thread (whisper contexts are not safe to call concurrently).
 */
class WhisperContext private constructor(private var ptr: Long) {

    suspend fun transcribe(audio: FloatArray, numThreads: Int, language: String): String =
        withContext(dispatcher) {
            check(ptr != 0L) { "WhisperContext has been released" }
            WhisperLib.fullTranscribe(ptr, numThreads, audio, language)
            val count = WhisperLib.getTextSegmentCount(ptr)
            buildString {
                for (i in 0 until count) append(WhisperLib.getTextSegment(ptr, i))
            }
        }

    suspend fun release() = withContext(dispatcher) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    companion object {
        private val dispatcher =
            Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        fun createContextFromFile(modelPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromFile(modelPath)
            require(ptr != 0L) { "Failed to load Whisper model: $modelPath" }
            return WhisperContext(ptr)
        }
    }
}

/** Native methods implemented in jni.cpp. */
private object WhisperLib {
    init {
        System.loadLibrary("whisper_jni")
    }

    external fun initContextFromFile(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String)
    external fun getTextSegmentCount(contextPtr: Long): Int
    external fun getTextSegment(contextPtr: Long, index: Int): String
}
