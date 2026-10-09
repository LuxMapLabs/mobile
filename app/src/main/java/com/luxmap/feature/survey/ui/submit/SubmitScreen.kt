package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge

@Composable
fun SubmitScreen(
    sessionId: String,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) { viewModel.onSubmit(sessionId) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (val state = uiState) {
                is SubmitUiState.Idle ->
                    StatusBadge(
                        text = SyncStatus.QUEUED_ONLINE.label(),
                        colors = SyncStatus.QUEUED_ONLINE.badgeColors(),
                    )

                is SubmitUiState.Uploading -> {
                    val fraction =
                        if (state.totalBytes == 0L) 0f else state.bytesSent.toFloat() / state.totalBytes.toFloat()
                    LinearProgressIndicator(progress = { fraction })
                    Text("${state.bytesSent} / ${state.totalBytes} bytes")
                    if (state.isOffline) {
                        StatusBadge(
                            text = SyncStatus.QUEUED_OFFLINE.label(),
                            colors = SyncStatus.QUEUED_OFFLINE.badgeColors(),
                        )
                    } else {
                        StatusBadge(text = SyncStatus.SYNCING.label(), colors = SyncStatus.SYNCING.badgeColors())
                    }
                }

                is SubmitUiState.Done -> {
                    StatusBadge(text = SyncStatus.DONE.label(), colors = SyncStatus.DONE.badgeColors())
                    Text("Đã nộp thành công", style = MaterialTheme.typography.bodyLarge)
                }

                is SubmitUiState.Failed -> {
                    StatusBadge(text = SyncStatus.FAILED.label(), colors = SyncStatus.FAILED.badgeColors())
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(text = "Nộp lại", onClick = { viewModel.onSubmit(sessionId) })
                }

                is SubmitUiState.Conflict -> {
                    StatusBadge(text = SyncStatus.CONFLICT.label(), colors = SyncStatus.CONFLICT.badgeColors())
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                }

                is SubmitUiState.NotPackagedYet ->
                    Text("Phiên khảo sát chưa đóng gói xong, chưa thể nộp", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
