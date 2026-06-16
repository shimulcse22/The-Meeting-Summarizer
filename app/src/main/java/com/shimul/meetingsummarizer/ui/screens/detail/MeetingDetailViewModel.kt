package com.shimul.meetingsummarizer.ui.screens.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shimul.meetingsummarizer.data.local.AppDatabase
import com.shimul.meetingsummarizer.data.repository.MeetingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeetingDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val title: String = "",
    val transcript: String = ""
)

class MeetingDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MeetingRepository(AppDatabase.get(application).meetingDao())

    private val _state = MutableStateFlow(MeetingDetailUiState())
    val state = _state.asStateFlow()

    private var loaded = false

    fun load(meetingId: String) {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val meeting = repository.getMeeting(meetingId)
            _state.update {
                if (meeting == null) {
                    it.copy(loading = false, notFound = true)
                } else {
                    it.copy(
                        loading = false,
                        title = meeting.title,
                        transcript = meeting.transcriptText
                    )
                }
            }
        }
    }
}
