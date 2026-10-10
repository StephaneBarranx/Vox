package fr.schwanx.vox.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.schwanx.vox.domain.model.Segment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    transcriptionId: Long,
    viewModel: ResultViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPlaying by viewModel.audioPlayer.isPlaying.collectAsState()
    val currentPositionMs by viewModel.audioPlayer.currentPositionMs.collectAsState()
    val durationMs by viewModel.audioPlayer.durationMs.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(transcriptionId) {
        viewModel.loadTranscription(transcriptionId)
    }

    val transcription = uiState.transcription

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer la transcription ?") },
            text = { Text("Cette action est irréversible.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteTranscription {
                        onNavigateBack()
                    }
                }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isEditing) {
                        OutlinedTextField(
                            value = uiState.editedTitle,
                            onValueChange = { viewModel.onTitleChanged(it) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.9f)
                        )
                    } else {
                        Text(
                            text = transcription?.title ?: "Résultat",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { viewModel.saveChanges() }) {
                            Icon(Icons.Rounded.Save, contentDescription = "Sauvegarder", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        IconButton(onClick = { viewModel.setEditing(true) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Éditer")
                        }
                        IconButton(onClick = { viewModel.copyToClipboard() }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copier")
                        }
                        IconButton(onClick = { viewModel.shareText() }) {
                            Icon(Icons.Rounded.Share, contentDescription = "Partager")
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exporter en TXT") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.exportAsTxt()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exporter en SRT (Sous-titres)") },
                                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.exportAsSrt()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menuExpanded = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (transcription == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Text("Chargement...")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats Header Chip
            val wordCount = transcription.text.split("\\s+".toRegex()).count { it.isNotBlank() }
            val totalSec = transcription.durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val durationStr = String.format("%02d:%02d", min, sec)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wordCount mots",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Durée : $durationStr",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Modèle : ${transcription.modelName.replace("ggml-", "").replace(".bin", "")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tabs (Texte complet / Segments)
            TabRow(selectedTabIndex = uiState.activeTab) {
                Tab(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.setTab(0) },
                    text = { Text("Texte complet") }
                )
                Tab(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.setTab(1) },
                    text = { Text("Segments (${transcription.segments.size})") }
                )
            }

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (uiState.activeTab == 0) {
                    if (uiState.isEditing) {
                        OutlinedTextField(
                            value = uiState.editedText,
                            onValueChange = { viewModel.onTextChanged(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = transcription.text,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    lineHeight = 26.sp,
                                    fontSize = 17.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(transcription.segments) { segment ->
                            SegmentCard(
                                segment = segment,
                                isHighlighted = currentPositionMs in segment.startMs..segment.endMs,
                                onClick = {
                                    viewModel.audioPlayer.seekTo(segment.startMs)
                                    viewModel.audioPlayer.resume()
                                }
                            )
                        }
                    }
                }
            }

            // Synced Audio Player Bar
            if (transcription.audioPath != null) {
                Card(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) viewModel.audioPlayer.pause() else viewModel.audioPlayer.resume()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Jouer",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            val maxRange = durationMs.coerceAtLeast(1L).toFloat()
                            val currentPos = currentPositionMs.coerceIn(0L, durationMs).toFloat()

                            Slider(
                                value = currentPos,
                                onValueChange = { viewModel.audioPlayer.seekTo(it.toLong()) },
                                valueRange = 0f..maxRange,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val curSec = currentPositionMs / 1000
                        val durSec = durationMs / 1000
                        val posFormatted = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, durSec / 60, durSec % 60)

                        Text(
                            text = posFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: Segment,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val startSec = segment.startMs / 1000
    val endSec = segment.endMs / 1000
    val timeLabel = String.format("[%02d:%02d - %02d:%02d]", startSec / 60, startSec % 60, endSec / 60, endSec % 60)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
