package com.luxmap.feature.survey.ui.coverage

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

// F05 reinterpreted (see docs/superpowers/specs/2026-10-02-survey-video-review-design.md):
// plain video playback, no coverage percentage. Back is blocked (see BackHandler below) so a
// packaged session always gets an explicit decision instead of being silently abandoned.
@Composable
fun CoverageScreen(
    sessionId: String,
    onApprove: (String) -> Unit,
    onRedo: (String) -> Unit,
    viewModel: CoverageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showRedoConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {}

    LaunchedEffect(sessionId) { viewModel.loadSegments(sessionId) }
    LaunchedEffect(Unit) { viewModel.redoCompleted.collect { surveySweepId -> onRedo(surveySweepId) } }

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is CoverageUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is CoverageUiState.Success -> {
                val player =
                    remember(state.segmentFilePaths) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItems(state.segmentFilePaths.map { MediaItem.fromUri(Uri.parse("file://$it")) })
                            prepare()
                        }
                    }
                DisposableEffect(player) {
                    val listener =
                        object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                viewModel.onPlayerError(error.message ?: "Lỗi phát video")
                            }
                        }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                        player.release()
                    }
                }

                Column(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
                    )

                    state.playerErrorMessage?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onApprove(sessionId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Nộp")
                        }
                        OutlinedButton(onClick = { showRedoConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Quay lại")
                        }
                    }
                }
            }

            is CoverageUiState.Empty -> {
                Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Không có video nào để xem lại.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { showRedoConfirm = true }) { Text("Quay lại") }
                }
            }

            is CoverageUiState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
        }

        if (showRedoConfirm) {
            AlertDialog(
                onDismissRequest = { showRedoConfirm = false },
                title = { Text("Xoá video này và quay lại?") },
                text = { Text("Video và dữ liệu đã quay sẽ bị xoá khỏi máy, không thể hoàn tác.") },
                confirmButton = {
                    Button(onClick = {
                        showRedoConfirm = false
                        viewModel.onRedoConfirmed(sessionId)
                    }) { Text("Xoá và quay lại") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRedoConfirm = false }) { Text("Huỷ") }
                },
            )
        }
    }
}
