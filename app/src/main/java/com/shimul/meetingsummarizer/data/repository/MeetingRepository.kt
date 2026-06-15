package com.shimul.meetingsummarizer.data.repository

import com.shimul.meetingsummarizer.data.local.dao.MeetingDao
import com.shimul.meetingsummarizer.data.local.entity.MeetingEntity
import com.shimul.meetingsummarizer.domain.model.Meeting
import com.shimul.meetingsummarizer.domain.model.MeetingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single source of truth for meetings; maps between Room entity and domain model. */
class MeetingRepository(private val dao: MeetingDao) {

    fun observeMeetings(): Flow<List<Meeting>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getMeeting(id: String): Meeting? = dao.getById(id)?.toDomain()

    suspend fun save(meeting: Meeting) = dao.insert(meeting.toEntity())

    suspend fun delete(meeting: Meeting) = dao.delete(meeting.toEntity())
}

private fun MeetingEntity.toDomain() = Meeting(
    id = id,
    title = title,
    createdAt = createdAt,
    source = MeetingSource.valueOf(source),
    language = language,
    transcriptText = transcriptText
)

private fun Meeting.toEntity() = MeetingEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    source = source.name,
    language = language,
    transcriptText = transcriptText
)
