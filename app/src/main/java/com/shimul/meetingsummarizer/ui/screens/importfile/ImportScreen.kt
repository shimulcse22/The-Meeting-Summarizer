package com.shimul.meetingsummarizer.ui.screens.importfile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ImportViewModel = viewModel()
) {
    val fileState by viewModel.state.collectAsState()
    val job by viewModel.jobState.collectAsState()

    val isWorking = job is ImportState.Preparing || job is ImportState.Running

    // Navigate to the meeting once transcription completes and is saved.
    LaunchedEffect(job) {
        (job as? ImportState.Done)?.let {
            onSaved(it.meetingId)
            viewModel.consumeDone()
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFileSelected(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import a file") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!viewModel.fileSupported) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "This build (Google STT) can't transcribe files — Android's speech " +
                            "recognizer only works on the live mic. Use the Whisper (staging) build " +
                            "to transcribe imported audio.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Text(
                text = "Pick an audio or video file to transcribe.",
                style = MaterialTheme.typography.bodyLarge
            )

            OutlinedButton(
                onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (fileState.fileName == null) "Choose file" else "Choose a different file")
            }

            // Language selector.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("auto" to "Auto", "en" to "English", "bn" to "বাংলা").forEach { (code, label) ->
                    FilterChip(
                        selected = fileState.language == code,
                        onClick = { viewModel.setLanguage(code) },
                        enabled = !isWorking,
                        label = { Text(label) }
                    )
                }
            }

            fileState.fileName?.let { name ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Job state UI.
            when (val s = job) {
                is ImportState.Preparing -> {
                    Text("Preparing model… ${(s.progress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = s.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is ImportState.Running -> {
                    Text("Transcribing… ${(s.progress * 100).toInt()}%")
                    LinearProgressIndicator(
                        progress = s.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (s.partial.isNotBlank()) {
                        Text(
                            text = s.partial,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is ImportState.Failed -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> Unit
            }

            // Primary action.
            if (!isWorking) {
                Button(
                    onClick = { viewModel.transcribe() },
                    enabled = fileState.uri != null && viewModel.fileSupported,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (job is ImportState.Failed) "Try again" else "Transcribe")
                }
            }
        }
    }
}
