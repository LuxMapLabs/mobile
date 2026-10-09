package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.color
import com.luxmap.core.theme.label
import com.luxmap.core.theme.severityFromWire
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.TextOnlyStatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onStartSurvey: (workOrderId: String, surveySweepId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startedSurveySession.collect { event ->
            onStartSurvey(event.workOrderId, event.surveySweepId)
        }
    }

    WorkOrderDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onStart = viewModel::start,
        onNavigateFault = { lat, lng -> openInMaps(context, lat, lng) },
        onComplete = onComplete,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderDetailScreen(
    uiState: WorkOrderDetailUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết lệnh") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is WorkOrderDetailUiState.Loading -> LoadingState()
                is WorkOrderDetailUiState.Success ->
                    WorkOrderDetailContent(
                        state = uiState,
                        onStart = onStart,
                        onNavigateFault = onNavigateFault,
                        onComplete = onComplete,
                    )
                is WorkOrderDetailUiState.Empty -> MessageState("Không tìm thấy lệnh này")
                is WorkOrderDetailUiState.Error -> MessageState(uiState.message)
            }
        }
    }
}

@Composable
private fun WorkOrderDetailContent(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onComplete: () -> Unit,
) {
    val detail = state.detail
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        WorkOrderHeaderSection(detail)
        if (detail.taskKind == "survey") {
            Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
        } else {
            FaultListSection(faults = detail.faults, onNavigateFault = onNavigateFault)
        }
        if (state.startError != null) {
            Text(
                text = state.startError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if ("start" in detail.allowedActions) {
            PrimaryButton(
                text = "Bắt đầu",
                onClick = onStart,
                enabled = !state.isStarting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if ("complete" in detail.allowedActions) {
            PrimaryButton(
                text = "Hoàn thành",
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WorkOrderHeaderSection(detail: WorkOrderDetail) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = detail.title, style = MaterialTheme.typography.titleLarge)
        Text(text = detail.workOrderId, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = status.label(), colors = status.badgeColors())
            StatusBadge(text = kind.label(), colors = kind.badgeColors())
        }
        Text(text = dueDateLabel(detail.dueDate).text, style = MaterialTheme.typography.bodyMedium)
        if (!detail.scheduledDate.isNullOrBlank()) {
            Text(
                text = "Lịch khảo sát/sửa chữa: ${DateFormatUtils.formatPlainDate(detail.scheduledDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!detail.note.isNullOrBlank()) {
            Text(text = detail.note, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FaultListSection(
    faults: List<WorkOrderFaultDetail>,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (faults.isEmpty()) {
            Text(
                text = "Không có sự cố nào trong lệnh này",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            faults.forEach { fault ->
                FaultCard(fault = fault, onNavigate = { onNavigateFault(fault.lat, fault.lng) })
            }
        }
    }
}

@Composable
private fun FaultCard(
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
) {
    val priority = severityFromWire(fault.severity)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = fault.poleId ?: fault.faultId, style = MaterialTheme.typography.titleMedium)
        TextOnlyStatusBadge(text = priority.label(), color = priority.color())
        Text(text = faultTypeLabel(fault.faultType), style = MaterialTheme.typography.bodyMedium)
        Text(text = faultStatusLabel(fault.faultStatus), style = MaterialTheme.typography.bodyMedium)
        if (fault.inspectionOutcome != null) {
            Text(text = inspectionOutcomeLabel(fault.inspectionOutcome), style = MaterialTheme.typography.bodyMedium)
        }
        if (fault.hasLocation()) {
            OutlinedButton(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            ) {
                Icon(imageVector = Icons.Filled.Navigation, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Điều hướng")
            }
        } else {
            Text(
                text = "Chưa có toạ độ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun BoxScope.MessageState(text: String) {
    Text(text = text, modifier = Modifier.align(Alignment.Center).padding(Spacing.lg))
}

private fun sampleRepairWithFaults() =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = "assigned",
        taskKind = "repair",
        dueDate = "2026-10-10",
        scheduledDate = "2026-10-09",
        note = "Ưu tiên xử lý trước khi mưa lớn",
        allowedActions = listOf("start"),
        faults =
            listOf(
                WorkOrderFaultDetail(
                    faultId = "FAULT-1",
                    poleId = "POLE-0047",
                    lat = 10.97,
                    lng = 106.49,
                    faultType = "lamp_out",
                    faultStatus = "confirmed",
                    severity = "high",
                    inspectionOutcome = "fault_present",
                ),
            ),
    )

private fun sampleRepairNoFaults() = sampleRepairWithFaults().copy(workOrderId = "WO-1099", faults = emptyList())

private fun sampleSurvey() =
    sampleRepairWithFaults().copy(workOrderId = "WO-2001", taskKind = "survey", faults = emptyList())

@Preview(name = "Success - Repair with faults", showBackground = true, heightDp = 900)
@Composable
private fun WorkOrderDetailScreenRepairPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleRepairWithFaults()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(name = "Success - Repair, no faults", showBackground = true)
@Composable
private fun WorkOrderDetailScreenNoFaultsPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleRepairNoFaults()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(name = "Success - Survey", showBackground = true)
@Composable
private fun WorkOrderDetailScreenSurveyPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleSurvey()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenLoadingPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Loading,
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenEmptyPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Empty,
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenErrorPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Error(message = "Không tải được dữ liệu"),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}
