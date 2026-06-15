package com.shimul.meetingsummarizer.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * The single stored entity. Per the spec, only transcript text is persisted —
 * no audio is kept, and the summary is regenerated on demand.
 */
@Entity(tableName = "meetings")
data class MeetingEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String, // "RECORDED" or "IMPORTED"
    val language: String, // e.g. "en", "bn"
    val transcriptText: String
)
