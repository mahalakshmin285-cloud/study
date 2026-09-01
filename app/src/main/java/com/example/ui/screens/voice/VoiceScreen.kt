package com.example.ui.screens.voice

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.speech.VoiceState
import com.example.ui.components.FormattedAiText
import com.example.ui.localization.LocalAppStrings
import com.example.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    viewModel: MainViewModel
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val micPermissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO)
    val voiceState by viewModel.speechAssistant.voiceState.collectAsState()
    val spokenText by viewModel.speechAssistant.spokenText.collectAsState()
    val isMuted by viewModel.speechAssistant.isMuted.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.voiceAssistantTitle, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                actions = {
                    IconButton(onClick = { viewModel.speechAssistant.toggleMute() }) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Speech" else "Mute Speech",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val statusText = when (voiceState) {
                            is VoiceState.Listening -> strings.listening
                            is VoiceState.Recognized -> "Recognized Query"
                            is VoiceState.Thinking -> "AI is Processing..."
                            is VoiceState.Speaking -> "AI Answer"
                            is VoiceState.Error -> (voiceState as VoiceState.Error).message
                            else -> strings.tapToSpeak
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = when (voiceState) {
                                is VoiceState.Listening -> MaterialTheme.colorScheme.error
                                is VoiceState.Thinking -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Main Mic Pulse Animation Button
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(170.dp)
                    ) {
                        if (voiceState is VoiceState.Listening) {
                            Box(
                                modifier = Modifier
                                    .size(150.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            )
                        }

                        Surface(
                            onClick = {
                                if (micPermissionState.status.isGranted) {
                                    if (voiceState is VoiceState.Listening) {
                                        viewModel.stopVoiceListening()
                                    } else {
                                        viewModel.startVoiceListening()
                                    }
                                } else {
                                    micPermissionState.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape),
                            color = when (voiceState) {
                                is VoiceState.Listening -> MaterialTheme.colorScheme.error
                                is VoiceState.Speaking -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = when (voiceState) {
                                        is VoiceState.Listening -> Icons.Default.Stop
                                        is VoiceState.Speaking -> Icons.Default.VolumeUp
                                        else -> Icons.Default.Mic
                                    },
                                    contentDescription = "Mic",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Recognized Speech Box
            if (spokenText.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "You Said:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = spokenText,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.processVoiceQuery(spokenText) },
                                enabled = voiceState !is VoiceState.Thinking,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Send to AI")
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // AI Answer Box (Smoothly & independently scrollable/swipeable)
            if (voiceState is VoiceState.Speaking) {
                val answerText = (voiceState as VoiceState.Speaking).answer
                item {
                    var noteSaved by remember(answerText) { mutableStateOf(false) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "AI Answer:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(answerText))
                                            Toast.makeText(context, "Copied AI answer to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.speechAssistant.toggleMute() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = if (isMuted) "Unmute" else "Mute",
                                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val heading = spokenText.trim().ifBlank { "Voice AI Note" }
                                            viewModel.saveNote(heading, answerText)
                                            noteSaved = true
                                            Toast.makeText(context, "Note saved successfully!", Toast.LENGTH_SHORT).show()
                                        },
                                        enabled = !noteSaved,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (noteSaved) "Saved" else "Save Note", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 80.dp, max = 380.dp)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                FormattedAiText(text = answerText)
                            }
                        }
                    }
                }
            }
        }
    }
}
