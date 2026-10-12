package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.SessionFileStatus
import com.luxmap.feature.survey.data.SessionUploadSummary

@Composable
fun SubmitScreen(
    sessionId: String,
    onBackToSurveyHub: () -> Unit,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fileStatuses by viewModel.fileStatuses.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val wifiOnlyUpload by viewModel.wifiOnlyUpload.collectAsState()

    LaunchedEffect(sessionId) { viewModel.onSubmit(sessionId) }

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        Text(text = "Nộp đợt khảo sát", style = MaterialTheme.typography.titleLarge)
        SubmitStatusBadge(uiState)
        summary?.let { SubmitSummaryGrid(it) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Chỉ tải lên qua Wi-Fi", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = wifiOnlyUpload, onCheckedChange = viewModel::onWifiOnlyToggled)
        }
        when (val state = uiState) {
            is SubmitUiState.Uploading -> {
                val fraction =
                    if (state.totalBytes == 0L) 0f else state.bytesSent.toFloat() / state.totalBytes.toFloat()
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            is SubmitUiState.Failed -> PrimaryButton(text = "Nộp lại", onClick = { viewModel.onSubmit(sessionId) })
            else -> Unit
        }
        SubmitFileStatusList(fileStatuses)
        PrimaryButton(text = "Về Khảo sát", onClick = onBackToSurveyHub, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SubmitStatusBadge(state: SubmitUiState) {
    when (state) {
        is SubmitUiState.Idle ->
            StatusBadge(text = "Đã lưu trên thiết bị", colors = SyncStatus.QUEUED_ONLINE.badgeColors())

        is SubmitUiState.Uploading ->
            StatusBadge(
                text = if (state.isOffline) SyncStatus.QUEUED_OFFLINE.label() else SyncStatus.SYNCING.label(),
                colors =
                    if (state.isOffline) SyncStatus.QUEUED_OFFLINE.badgeColors() else SyncStatus.SYNCING.badgeColors(),
            )

        is SubmitUiState.WaitingForWifi ->
            StatusBadge(text = "Chờ Wi-Fi để tải lên", colors = SyncStatus.QUEUED_OFFLINE.badgeColors())

        is SubmitUiState.Done -> StatusBadge(text = SyncStatus.DONE.label(), colors = SyncStatus.DONE.badgeColors())

        is SubmitUiState.Failed ->
            StatusBadge(text = SyncStatus.FAILED.label(), colors = SyncStatus.FAILED.badgeColors())

        is SubmitUiState.Conflict ->
            StatusBadge(text = SyncStatus.CONFLICT.label(), colors = SyncStatus.CONFLICT.badgeColors())

        is SubmitUiState.NotPackagedYet ->
            Text("Phiên khảo sát chưa đóng gói xong, chưa thể nộp", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SubmitSummaryGrid(summary: SessionUploadSummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SummaryCell("Thời lượng", summary.durationSeconds?.let { "${it / 60} phút" } ?: "—")
            SummaryCell("Số đoạn video", summary.segmentCount.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SummaryCell("Quãng đường", summary.distanceMeters?.let { "%.1f km".format(it / 1000) } ?: "—")
            SummaryCell("Dung lượng", "${summary.totalBytes / 1_000_000} MB")
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
) {
    Column(Modifier.padding(Spacing.sm)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SubmitFileStatusList(files: List<SessionFileStatus>) {
    Column {
        files.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(file.label, style = MaterialTheme.typography.bodyMedium)
                val syncStatus = syncQueueStatusToSyncStatus(file.status)
                StatusBadge(text = syncStatus.label(), colors = syncStatus.badgeColors())
            }
        }
    }
}

// A bare "queued" row always resolves to QUEUED_ONLINE, not an online/offline split - the
// screen's one top-level SubmitStatusBadge already carries that distinction for the whole
// submission, and duplicating ConnectivityObserver onto every file row was judged not worth the
// complexity for this phase (see docs/contract-drift.md).
private fun syncQueueStatusToSyncStatus(status: String): SyncStatus =
    when (status) {
        "syncing" -> SyncStatus.SYNCING
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        "done" -> SyncStatus.DONE
        else -> SyncStatus.QUEUED_ONLINE
    }
