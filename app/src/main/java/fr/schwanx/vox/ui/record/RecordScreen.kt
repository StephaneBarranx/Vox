package fr.schwanx.vox.ui.record

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fr.schwanx.vox.ui.components.WaveformCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTranscribe: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    LaunchedEffect(uiState.status) {
        if (uiState.status == RecordingStatus.FINISHED) {
            uiState.outputFile?.let { file ->
                onNavigateToTranscribe(file.absolutePath)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enregistrement vocal", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelRecording()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Timer Display
            val totalSec = uiState.elapsedTimeMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (uiState.elapsedTimeMs % 1000) / 10
            val timerFormatted = String.format("%02d:%02d.%02d", min, sec, ms)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timerFormatted,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (uiState.status == RecordingStatus.RECORDING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                val statusText = when (uiState.status) {
                    RecordingStatus.IDLE -> "Appuyez sur le micro pour enregistrer"
                    RecordingStatus.RECORDING -> "Enregistrement en cours..."
                    RecordingStatus.PAUSED -> "En pause"
                    RecordingStatus.FINISHED -> "Terminé"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Animated Waveform Canvas
            WaveformCanvas(
                amplitudes = uiState.amplitudes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            // Central Pulsating Button and Action Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!hasMicPermission) {
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Autoriser l'accès au microphone", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel button
                        if (uiState.status != RecordingStatus.IDLE) {
                            OutlinedButton(
                                onClick = { viewModel.cancelRecording() },
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Annuler", tint = MaterialTheme.colorScheme.error)
                            }
                        }

                        // Main Big Record/Pause Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .scale(if (uiState.status == RecordingStatus.RECORDING) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    when (uiState.status) {
                                        RecordingStatus.RECORDING -> MaterialTheme.colorScheme.primary
                                        RecordingStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primaryContainer
                                    }
                                )
                        ) {
                            IconButton(
                                onClick = {
                                    when (uiState.status) {
                                        RecordingStatus.IDLE -> viewModel.startRecording()
                                        RecordingStatus.RECORDING -> viewModel.pauseRecording()
                                        RecordingStatus.PAUSED -> viewModel.resumeRecording()
                                        else -> {}
                                    }
                                },
                                modifier = Modifier.size(96.dp)
                            ) {
                                val icon = when (uiState.status) {
                                    RecordingStatus.RECORDING -> Icons.Rounded.Pause
                                    RecordingStatus.PAUSED -> Icons.Rounded.PlayArrow
                                    else -> Icons.Rounded.Mic
                                }
                                val tint = when (uiState.status) {
                                    RecordingStatus.RECORDING, RecordingStatus.PAUSED -> MaterialTheme.colorScheme.onPrimary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        // Stop / Transcribe button
                        if (uiState.status != RecordingStatus.IDLE) {
                            Button(
                                onClick = { viewModel.stopRecording() },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(Icons.Rounded.Done, contentDescription = "Terminer", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (uiState.status != RecordingStatus.IDLE) {
                        FilledTonalButton(
                            onClick = { viewModel.stopRecording() },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Transcrire l'enregistrement", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
