package com.shimul.meetingsummarizer.domain.summarization

/**
 * On-device summarization contract. Implemented with Gemma via MediaPipe in a
 * later milestone. Kept as an interface to decouple the UI from the engine.
 */
interface Summarizer {
    /** Produce a structured summary from transcript text. Runs fully offline. */
    suspend fun summarize(transcript: String): MeetingSummary
}

data class MeetingSummary(
    val tldr: String,
    val keyPoints: List<String>,
    val decisions: List<String>,
    val actionItems: List<ActionItem>
)

data class ActionItem(
    val task: String,
    val owner: String? = null,
    val due: String? = null
)
