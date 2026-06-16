package com.whispercpp.whisper

/** Picks a sensible thread count for whisper inference on this device. */
object WhisperCpuConfig {
    val preferredThreadCount: Int
        get() = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2)
}
