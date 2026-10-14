package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.color
import com.luxmap.core.theme.label
import com.luxmap.core.theme.severityFromWire
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.DueTodayColor
import com.luxmap.core.ui.components.LifecycleTracker
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onStartSurvey: (workOrderId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startedSurveyWorkOrderId.collect { workOrderId -> onStartSurvey(workOrderId) }
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

@Composable
fun WorkOrderDetailScreen(
    uiState: WorkOrderDetailUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBarTitle =
        if (uiState is WorkOrderDetailUiState.Success) {
            workOrderDetailTopBarTitle(uiState.detail.taskKind)
        } else {
            "Chi tiết lệnh"
        }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = topBarTitle, onBack = onBack) },
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
                is WorkOrderDetailUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.Assignment,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Không tìm thấy lệnh này",
                        subtitle = "Lệnh có thể đã bị xoá hoặc không thuộc phạm vi của bạn.",
                        action = {},
                    )
                is WorkOrderDetailUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = uiState.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        action = {},
                    )
            }
        }
    }
}

private fun workOrderDetailTopBarTitle(taskKindWire: String): String =
    when (workOrderTaskKindFromWire(taskKindWire)) {
        WorkOrderTaskKind.INSPECTION -> "Lệnh kiểm tra"
        WorkOrderTaskKind.REPAIR -> "Lệnh sửa chữa"
        WorkOrderTaskKind.SURVEY -> "Lệnh khảo sát"
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
        val kind = workOrderTaskKindFromWire(detail.taskKind)
        if (kind == WorkOrderTaskKind.SURVEY) {
            Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
        } else {
            FaultListSection(kind = kind, faults = detail.faults, onNavigateFault = onNavigateFault)
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
        if (workOrderStatusFromWire(detail.woStatus) == WorkOrderStatus.DONE &&
            "start" !in detail.allowedActions &&
            "complete" !in detail.allowedActions
        ) {
            PrimaryButton(text = "Đã nộp báo cáo", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            Text(
                text = "Chờ Kỹ sư bảo trì duyệt (cổng #2)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkOrderHeaderSection(detail: WorkOrderDetail) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = kind.label(), colors = kind.badgeColors())
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Text(text = detail.title, style = HeadlineTextStyle)
        Text(text = detail.workOrderId, style = MaterialTheme.typography.bodyMedium)
        val due = dueDateLabel(detail.dueDate)
        Text(
            text = due.text,
            style = MaterialTheme.typography.bodyMedium,
            color =
                when {
                    due.isOverdue -> MaterialTheme.colorScheme.error
                    due.isToday -> DueTodayColor
                    else -> MaterialTheme.colorScheme.onSurface
                },
        )
        if (!detail.scheduledDate.isNullOrBlank()) {
            Text(
                text = "Lịch khảo sát/sửa chữa: ${DateFormatUtils.formatPlainDate(detail.scheduledDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!detail.note.isNullOrBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    imageVector = Icons.Filled.StickyNote2,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = detail.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!detail.reviewNote.isNullOrBlank()) {
            Text(
                text = "Manager yêu cầu bổ sung: ${detail.reviewNote}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (status != WorkOrderStatus.CANCELLED) {
            LifecycleTracker(
                steps = workOrderLifecycleSteps(status, kind),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

// Header wording/count matches the real screen's faultHeader/faultCount (LuxMap App v3.dc.html,
// "task" screen) - "Sự cố cần sửa" for a repair order, "Sự cố cần kiểm tra" otherwise.
@Composable
private fun FaultListSection(
    kind: WorkOrderTaskKind,
    faults: List<WorkOrderFaultDetail>,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = if (kind == WorkOrderTaskKind.REPAIR) "Sự cố cần sửa" else "Sự cố cần kiểm tra",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${faults.size} sự cố",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (faults.isEmpty()) {
            Text(
                text = "Không có sự cố nào trong lệnh này",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card)),
            ) {
                faults.forEachIndexed { index, fault ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                    FaultRow(fault = fault, onNavigate = { onNavigateFault(fault.lat, fault.lng) })
                }
            }
        }
    }
}

@Composable
private fun FaultRow(
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
) {
    val priority = severityFromWire(fault.severity)
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .height(36.dp)
                    .background(priority.color(), RoundedCornerShape(Radius.badge)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = fault.faultId, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(fault.poleId, faultTypeLabel(fault.faultType)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fault.inspectionOutcome != null) {
                Text(
                    text = inspectionOutcomeLabel(fault.inspectionOutcome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (fault.hasLocation()) {
            IconButton(onClick = onNavigate, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(imageVector = Icons.Filled.Navigation, contentDescription = "Điều hướng")
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
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag("work_order_detail_loading"))
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
