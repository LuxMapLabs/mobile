package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.DueTodayColor
import com.luxmap.core.ui.components.EvidencePicker
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.LifecycleTracker
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.OutcomeSegment
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.PriorityDot
import com.luxmap.core.ui.components.Requirement
import com.luxmap.core.ui.components.RequirementList
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onCaptureEvidence: () -> Unit,
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
        onReportNoteChanged = viewModel::onReportNoteChanged,
        onMaterialsUsedChanged = viewModel::onMaterialsUsedChanged,
        onFaultOutcomeSelected = viewModel::onFaultOutcomeSelected,
        onSubmit = viewModel::submit,
        onCaptureEvidence = onCaptureEvidence,
        onRetakeEvidence = { viewModel.retakeEvidence(onCaptureEvidence) },
        modifier = modifier,
    )
}

@Composable
fun WorkOrderDetailScreen(
    uiState: WorkOrderDetailUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
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
        bottomBar = {
            if (uiState is WorkOrderDetailUiState.Success && uiState.showsInspectionBottomBar()) {
                val recordedCount = uiState.detail.faults.count { uiState.faultOutcomes.containsKey(it.faultId) }
                BottomActionBar(
                    ctaText = "Nộp báo cáo kiểm tra",
                    onCtaClick = onSubmit,
                    ctaEnabled = uiState.canSubmit() && !uiState.isSubmitting,
                    progressText = "$recordedCount/${uiState.detail.faults.size} sự cố đã có kết quả",
                )
            }
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
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onFaultOutcomeSelected = onFaultOutcomeSelected,
                        onSubmit = onSubmit,
                        onCaptureEvidence = onCaptureEvidence,
                        onRetakeEvidence = onRetakeEvidence,
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

private fun submitButtonLabel(kind: WorkOrderTaskKind): String =
    if (kind == WorkOrderTaskKind.INSPECTION) "Nộp báo cáo kiểm tra" else "Hoàn thành lệnh"

@Composable
private fun WorkOrderDetailContent(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val canComplete = "complete" in detail.allowedActions
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        WorkOrderHeaderSection(detail)
        state.syncStatus()?.let { status ->
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        if (kind == WorkOrderTaskKind.SURVEY) {
            Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
        } else {
            FaultListSection(
                kind = kind,
                faults = detail.faults,
                onNavigateFault = onNavigateFault,
                showOutcomePicker = kind == WorkOrderTaskKind.INSPECTION && canComplete,
                faultOutcomes = state.faultOutcomes,
                onFaultOutcomeSelected = onFaultOutcomeSelected,
            )
            if (canComplete) {
                when (kind) {
                    WorkOrderTaskKind.REPAIR ->
                        RepairCompletionSection(
                            state = state,
                            onReportNoteChanged = onReportNoteChanged,
                            onMaterialsUsedChanged = onMaterialsUsedChanged,
                            onCaptureEvidence = onCaptureEvidence,
                            onRetakeEvidence = onRetakeEvidence,
                        )
                    WorkOrderTaskKind.INSPECTION ->
                        InspectionCompletionSection(
                            state = state,
                            onReportNoteChanged = onReportNoteChanged,
                        )
                    WorkOrderTaskKind.SURVEY -> Unit
                }
            }
        }
        if (state.startError != null) {
            Text(
                text = state.startError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.submitError != null) {
            InlineBanner(message = state.submitError, tone = InlineBannerTone.ERROR)
        }
        if ("start" in detail.allowedActions) {
            PrimaryButton(
                text = "Bắt đầu",
                onClick = onStart,
                enabled = !state.isStarting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (canComplete && !state.showsInspectionBottomBar()) {
            PrimaryButton(
                text = submitButtonLabel(kind),
                onClick = onSubmit,
                enabled = state.canSubmit() && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (workOrderStatusFromWire(detail.woStatus) == WorkOrderStatus.DONE &&
            "start" !in detail.allowedActions &&
            !canComplete
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
    // Highest severity among the work order's own faults - severity is set by the Manager
    // (reliable), unlike priority_score which CV-16 was never wired up to compute (confirmed
    // against luxmap_backend's contract-drift.md). No fault (repair with none, or a survey
    // order) means no rank to show, so the dot is simply left out.
    val overallPriority = detail.faults.maxOfOrNull { severityFromWire(it.severity) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = kind.label(), colors = kind.badgeColors())
            StatusBadge(text = status.label(), colors = status.badgeColors())
            if (overallPriority != null) {
                PriorityDot(text = overallPriority.label(), dotColor = overallPriority.color())
            }
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
// "task" screen) - "Sự cố cần sửa" for a repair order, "Sự cố cần kiểm tra" otherwise. While an
// inspection is being completed, the count on the right switches to the recorded-outcomes
// progress, matching the real screen's per-fault outcome picker living inline in this list.
@Composable
private fun FaultListSection(
    kind: WorkOrderTaskKind,
    faults: List<WorkOrderFaultDetail>,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    showOutcomePicker: Boolean,
    faultOutcomes: Map<String, String>,
    onFaultOutcomeSelected: (String, String) -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                faults.forEach { fault ->
                    FaultRow(
                        fault = fault,
                        onNavigate = { onNavigateFault(fault.lat, fault.lng) },
                        showOutcomePicker = showOutcomePicker,
                        selectedOutcome = faultOutcomes[fault.faultId],
                        onOutcomeSelected = { outcome -> onFaultOutcomeSelected(fault.faultId, outcome) },
                    )
                }
            }
        }
    }
}

private val NavigateDiamondSize = 32.dp
private val NavigateDiamondRotation = 45f
private val NavigateIconSize = 18.dp

@Composable
private fun FaultRow(
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
    showOutcomePicker: Boolean,
    selectedOutcome: String?,
    onOutcomeSelected: (String) -> Unit,
) {
    val priority = severityFromWire(fault.severity)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
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
                    Box(
                        modifier =
                            Modifier
                                .size(NavigateDiamondSize)
                                .rotate(NavigateDiamondRotation)
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Radius.badge)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = "Điều hướng",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(NavigateIconSize).rotate(-NavigateDiamondRotation),
                        )
                    }
                }
            } else {
                Text(
                    text = "Chưa có toạ độ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showOutcomePicker) {
            OutcomeSegment(selected = selectedOutcome, onSelected = onOutcomeSelected)
        }
    }
}

@Composable
private fun RepairCompletionSection(
    state: WorkOrderDetailUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    val hasPhoto = state.localEvidence != null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = "Nghiệm thu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EvidencePicker(
            hasPhoto = hasPhoto,
            onTileClick = if (hasPhoto) onRetakeEvidence else onCaptureEvidence,
        )
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Kết quả sửa chữa") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${state.reportNote.trim().length} ký tự · tối thiểu 10",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.materialsUsed,
            onValueChange = onMaterialsUsedChanged,
            label = { Text("Vật tư đã dùng (không bắt buộc)") },
            modifier = Modifier.fillMaxWidth(),
        )
        RequirementList(
            items =
                listOf(
                    Requirement("Có ảnh sau sửa chữa", met = hasPhoto),
                    Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
                ),
        )
    }
}

@Composable
private fun InspectionCompletionSection(
    state: WorkOrderDetailUiState.Success,
    onReportNoteChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.reportNote,
        onValueChange = onReportNoteChanged,
        label = { Text("Ghi chú kiểm tra") },
        modifier = Modifier.fillMaxWidth(),
    )
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
        onReportNoteChanged = {},
        onMaterialsUsedChanged = {},
        onFaultOutcomeSelected = { _, _ -> },
        onSubmit = {},
        onCaptureEvidence = {},
        onRetakeEvidence = {},
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
        onReportNoteChanged = {},
        onMaterialsUsedChanged = {},
        onFaultOutcomeSelected = { _, _ -> },
        onSubmit = {},
        onCaptureEvidence = {},
        onRetakeEvidence = {},
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
        onReportNoteChanged = {},
        onMaterialsUsedChanged = {},
        onFaultOutcomeSelected = { _, _ -> },
        onSubmit = {},
        onCaptureEvidence = {},
        onRetakeEvidence = {},
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
        onReportNoteChanged = {},
        onMaterialsUsedChanged = {},
        onFaultOutcomeSelected = { _, _ -> },
        onSubmit = {},
        onCaptureEvidence = {},
        onRetakeEvidence = {},
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
        onReportNoteChanged = {},
        onMaterialsUsedChanged = {},
        onFaultOutcomeSelected = { _, _ -> },
        onSubmit = {},
        onCaptureEvidence = {},
        onRetakeEvidence = {},
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
        onReportNoteChanged = {},
        onMaterialsUsedChanged = {},
        onFaultOutcomeSelected = { _, _ -> },
        onSubmit = {},
        onCaptureEvidence = {},
        onRetakeEvidence = {},
    )
}
