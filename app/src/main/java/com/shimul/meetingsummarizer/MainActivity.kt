package com.shimul.meetingsummarizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shimul.meetingsummarizer.ui.navigation.AppNavGraph
import com.shimul.meetingsummarizer.ui.theme.MeetingSummarizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeetingSummarizerTheme {
                AppNavGraph()
            }
        }
    }
}
