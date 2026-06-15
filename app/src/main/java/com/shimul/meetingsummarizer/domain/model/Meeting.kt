package com.shimul.meetingsummarizer.domain.model

/** Domain model used by the UI layer (decoupled from the Room entity). */
data class Meeting(
    val id: String,
    val title: String,
    val createdAt: Long,
    val source: MeetingSource,
    val language: String,
    val transcriptText: String
)

enum class MeetingSource { RECORDED, IMPORTED }
