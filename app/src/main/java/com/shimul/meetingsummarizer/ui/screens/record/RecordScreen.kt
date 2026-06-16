package com.shimul.meetingsummarizer.ui.screens.record

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shimul.meetingsummarizer.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: RecordViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Navigate to the meeting detail once it has been saved.
    LaunchedEffect(state.savedMeetingId) {
        val id = state.savedMeetingId
        if (id != null) {
            onSaved(id)
            viewModel.consumeSavedId()
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.start()
    }

    // Ask for notification permission once (Android 13+) so the background
    // recording notification can be shown.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best effort — recording works regardless */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun onRecordClick() {
        when {
            state.isRecording -> viewModel.stop()
            hasMicPermission -> viewModel.start()
            else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.record)) },
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RecordingIndicator(
                    isRecording = state.isRecording,
                    isPaused = state.isPaused
                )
                Text(
                    text = formatElapsed(state.elapsedMs),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Engine: ${state.engineName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Language selector (disabled while recording).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("en" to "English", "bn" to "বাংলা", "auto" to "Auto").forEach { (code, label) ->
                    FilterChip(
                        selected = state.language == code,
                        onClick = { viewModel.setLanguage(code) },
                        enabled = !state.isRecording,
                        label = { Text(label) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Model download progress (first use).
            if (state.modelStatus == ModelStatus.Preparing) {
                Text(
                    "Preparing language model… ${(state.prepareProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = state.prepareProgress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            // Live transcript area.
            TranscriptArea(
                transcript = state.transcript,
                isRecording = state.isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if ((state.isRecording && !state.isPaused) || state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        state.isSaving -> "Saving…"
                        state.modelStatus == ModelStatus.Preparing -> "Getting ready…"
                        state.isPaused -> "Paused"
                        state.isRecording -> "Listening — transcribing as you speak"
                        else -> "Tap to start recording"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pause / Resume — only while a session is active.
                if (state.isRecording) {
                    FilledIconButton(
                        onClick = { if (state.isPaused) viewModel.resume() else viewModel.pause() },
                        modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (state.isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Start (idle) / Stop (recording or paused).
                FilledIconButton(
                    onClick = ::onRecordClick,
                    modifier = Modifier.size(88.dp),
                    enabled = state.modelStatus != ModelStatus.Preparing && !state.isSaving,
                    colors = if (state.isRecording) {
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        IconButtonDefaults.filledIconButtonColors()
                    }
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (state.isRecording) "Stop recording" else "Start recording",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            if (permissionDenied && !hasMicPermission) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Microphone permission is required to record. " +
                        "Please allow it in system settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            state.error?.let { err ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TranscriptArea(
    transcript: String,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Auto-scroll to the bottom as the transcript grows.
    LaunchedEffect(transcript) {
        if (transcript.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        if (transcript.isEmpty()) {
            Text(
                text = if (isRecording) "…" else "Your transcript will appear here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun RecordingIndicator(isRecording: Boolean, isPaused: Boolean) {
    val active = isRecording && !isPaused
    val transition = rememberInfiniteTransition(label = "rec")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val dotColor = when {
        active -> MaterialTheme.colorScheme.error
        isPaused -> MaterialTheme.colorScheme.tertiary
        else -> Color.Gray
    }
    val label = when {
        isPaused -> "PAUSED"
        isRecording -> "REC"
        else -> "IDLE"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .scale(if (active) scale else 1f)
                .size(14.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
