package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.SessionFileStatus
import com.luxmap.feature.survey.data.SessionUploadSummary
import java.util.Locale
import kotlin.math.roundToInt

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

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackToSurveyHub) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
            }
            Text(text = "Nộp đợt khảo sát", style = MaterialTheme.typography.titleLarge)
        }
        // A LazyColumn, not a plain Column: the file row count is 5 + segmentCount, and a session
        // recorded in 60s clips can easily have 15-35 rows.
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Column {
                    SavedOnDeviceBadge()
                    Text(
                        text = "$sessionId đã đóng gói",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
            item { SubmitStatusSection(uiState) }
            summary?.let { item { SubmitSummaryGrid(it) } }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Chỉ tải lên qua Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = wifiOnlyUpload, onCheckedChange = viewModel::onWifiOnlyToggled)
                }
            }
            (uiState as? SubmitUiState.Uploading)?.let { state ->
                item { UploadProgressSection(state) }
            }
            if (fileStatuses.isNotEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.card))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        fileStatuses.forEachIndexed { index, file ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            FileStatusRow(file)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(
                        imageVector = Icons.Filled.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text =
                            "Tải lên chạy nền qua hàng đợi đồng bộ và tiếp tục từ tệp đang dở khi mạng ngắt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val canRetry = uiState is SubmitUiState.Failed || uiState is SubmitUiState.WaitingForWifi
        PrimaryButton(
            text = if (canRetry) "Nộp lại" else "Về Khảo sát",
            onClick = if (canRetry) ({ viewModel.onSubmit(sessionId) }) else onBackToSurveyHub,
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
        )
    }
}

@Composable
private fun SavedOnDeviceBadge() {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = Icons.Filled.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Đã lưu trên thiết bị",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// Failed/WaitingForWifi get a real banner (InlineBanner already supports exactly these 2 tones) -
// the other states stay a plain StatusBadge, since InlineBanner has no tone for "in progress" or
// "done" today and adding one is out of scope for this restyle.
@Composable
private fun SubmitStatusSection(state: SubmitUiState) {
    when (state) {
        is SubmitUiState.Failed -> InlineBanner(message = state.message, tone = InlineBannerTone.ERROR)
        is SubmitUiState.WaitingForWifi ->
            InlineBanner(message = "Chờ Wi-Fi để tải lên", tone = InlineBannerTone.OFFLINE)
        else -> SubmitStatusBadge(state)
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

        is SubmitUiState.Done -> StatusBadge(text = SyncStatus.DONE.label(), colors = SyncStatus.DONE.badgeColors())

        is SubmitUiState.Conflict ->
            StatusBadge(text = SyncStatus.CONFLICT.label(), colors = SyncStatus.CONFLICT.badgeColors())

        is SubmitUiState.NotPackagedYet ->
            Text("Phiên khảo sát chưa đóng gói xong, chưa thể nộp", style = MaterialTheme.typography.bodyLarge)

        is SubmitUiState.Failed, is SubmitUiState.WaitingForWifi -> Unit // handled by SubmitStatusSection
    }
}

@Composable
private fun UploadProgressSection(state: SubmitUiState.Uploading) {
    val fraction = if (state.totalBytes == 0L) 0f else state.bytesSent.toFloat() / state.totalBytes.toFloat()
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Tải lên",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "${(fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        )
    }
}

@Composable
private fun SubmitSummaryGrid(summary: SessionUploadSummary) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth()) {
            GridCell(
                label = "Thời lượng",
                value = summary.durationSeconds?.let { "${it / 60} phút" } ?: "—",
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            GridCell(
                label = "Quãng đường",
                value = summary.distanceMeters?.let { "%.1f km".format(Locale.US, it / 1000) } ?: "—",
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(Modifier.fillMaxWidth()) {
            GridCell(
                label = "Dung lượng",
                value = "%.1f MB".format(Locale.US, summary.totalBytes / 1_000_000.0),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            GridCell(
                label = "Video · 60 giây/đoạn",
                value = "${summary.segmentCount} đoạn",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GridCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(Spacing.md)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SubmitFileStatusList(files: List<SessionFileStatus>) {
    Column {
        files.forEach { file -> FileStatusRow(file) }
    }
}

@Composable
private fun FileStatusRow(file: SessionFileStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(file.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val syncStatus = syncQueueStatusToSyncStatus(file.status)
        StatusBadge(text = syncStatus.label(), colors = syncStatus.badgeColors())
    }
}

// A bare "queued" row always resolves to QUEUED_ONLINE, not an online/offline split - the
// screen's one top-level SubmitStatusSection already carries that distinction for the whole
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
