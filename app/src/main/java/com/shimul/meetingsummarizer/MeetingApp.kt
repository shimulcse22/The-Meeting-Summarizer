package com.shimul.meetingsummarizer

import android.app.Application

/**
 * Application entry point. A good place to initialize app-wide singletons
 * (database, transcriber, summarizer) as the project grows.
 */
class MeetingApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: initialize database / engines here in later milestones.
    }
}
