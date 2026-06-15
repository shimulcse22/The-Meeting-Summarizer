package com.shimul.meetingsummarizer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.shimul.meetingsummarizer.data.local.dao.MeetingDao
import com.shimul.meetingsummarizer.data.local.entity.MeetingEntity

@Database(entities = [MeetingEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun meetingDao(): MeetingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meeting_summarizer.db"
                ).build().also { INSTANCE = it }
            }
    }
}
