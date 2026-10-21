package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.TitleTextStyle
import com.luxmap.core.theme.WorkOrderPriority
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.color
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.severityFromWire
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.EvidencePicker
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.LifecycleTracker
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.OutcomeSegment
import com.luxmap.core.ui.components.PriorityDot
import com.luxmap.core.ui.components.Requirement
import com.luxmap.core.ui.components.RequirementList
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation
import java.io.File

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
    Scaffold(
        modifier = modifier,
        // Success renders its own navy hero with an embedded back button (see
        // WorkOrderHeroSection) instead of this plain top bar - the other 3 states have no
        // detail to put in a hero, so they keep the plain bar with a generic title.
        topBar = {
            if (uiState !is WorkOrderDetailUiState.Success) {
                AppTopBar(title = "Chi tiết lệnh", onBack = onBack)
            }
        },
        bottomBar = {
            if (uiState is WorkOrderDetailUiState.Success) {
                val cta = workOrderCtaState(state = uiState, onStart = onStart, onSubmit = onSubmit)
                if (cta != null) {
                    BottomActionBar(
                        ctaText = cta.text,
                        ctaIcon = cta.icon,
                        onCtaClick = cta.onClick,
                        ctaEnabled = cta.enabled,
                        hint = cta.hint,
                        progressText = cta.progressText,
                    )
                }
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is WorkOrderDetailUiState.Loading -> LoadingState()
                is WorkOrderDetailUiState.Success ->
                    WorkOrderDetailContent(
                        state = uiState,
                        onBack = onBack,
                        onNavigateFault = onNavigateFault,
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onFaultOutcomeSelected = onFaultOutcomeSelected,
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

private data class WorkOrderCtaState(
    val text: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val hint: String? = null,
    val progressText: String? = null,
)

// Single bottom-pinned CTA slot for every state this screen can be in - matches the F09
// redesign mockup, which always pins exactly one button (start/complete/locked/done) instead of
// the old layout's separate inline buttons for each case.
private fun workOrderCtaState(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onSubmit: () -> Unit,
): WorkOrderCtaState? {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val canSubmitNow = state.canSubmit() && !state.isSubmitting
    return when {
        "start" in detail.allowedActions ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.PlayArrow,
                enabled = !state.isStarting,
                onClick = onStart,
            )
        // A completion was just saved locally (tapping the button writes it immediately,
        // offline-first) - lock the CTA right away instead of leaving it looking tappable until
        // the screen is reopened and detail.allowedActions is refetched from the server. Without
        // this, a field engineer who taps again (since nothing visibly changed) just gets a 409
        // from the server, which is confusing and looks like the button does nothing.
        //
        // Only "pending" locks it - a manager can return a completed order for rework, which
        // puts "complete" back in allowedActions while the OLD completion row is still sitting
        // in Room as "synced". Checking localCompletion != null here would lock the CTA forever
        // after a return, since nothing ever deletes that stale row.
        "complete" in detail.allowedActions && state.localCompletion?.submitStatus == "pending" ->
            WorkOrderCtaState(
                text = "Đã nộp" + (state.syncStatus()?.label()?.let { " — $it" } ?: ""),
                icon = Icons.Filled.HourglassTop,
                enabled = false,
                onClick = {},
            )
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.REPAIR ->
            WorkOrderCtaState(
                text = "Hoàn thành lệnh",
                icon = if (canSubmitNow) Icons.Filled.Check else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                hint = missingRequirementsHint(repairRequirements(state)),
            )
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.INSPECTION -> {
            val recordedCount = detail.faults.count { state.faultOutcomes.containsKey(it.faultId) }
            WorkOrderCtaState(
                text = "Nộp báo cáo kiểm tra",
                icon = if (canSubmitNow) Icons.Filled.Send else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                progressText = "$recordedCount/${detail.faults.size} sự cố đã có kết quả",
            )
        }
        workOrderStatusFromWire(detail.woStatus) == WorkOrderStatus.DONE && "start" !in detail.allowedActions ->
            WorkOrderCtaState(
                text = "Đã nộp báo cáo",
                icon = Icons.Filled.HourglassTop,
                enabled = false,
                onClick = {},
                hint = "Chờ Kỹ sư bảo trì duyệt (cổng #2)",
            )
        else -> null
    }
}

// Same 2 requirements RequirementList shows in the "Nghiệm thu" card, reused here to build the
// "Cần ..." lock hint instead of duplicating the wording in 2 places.
private fun repairRequirements(state: WorkOrderDetailUiState.Success): List<Requirement> =
    listOf(
        Requirement("Có ít nhất 1 ảnh sau sửa chữa", met = state.localEvidence != null),
        Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
    )

private fun missingRequirementsHint(requirements: List<Requirement>): String? {
    val missing = requirements.filterNot { it.met }
    if (missing.isEmpty()) return null
    return "Cần " + missing.joinToString(" và ") { it.label.lowercase() }
}

@Composable
private fun WorkOrderDetailContent(
    state: WorkOrderDetailUiState.Success,
    onBack: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val canComplete = "complete" in detail.allowedActions
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        WorkOrderHeroSection(detail = detail, onBack = onBack)
        // Overlaps the hero's bottom edge by Spacing.xl, same -24px margin-top technique F02
        // uses, so this sheet reads as floating over the navy hero instead of a hard seam.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset(y = -Spacing.xl)
                    .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(Spacing.lg),
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
        }
    }
}

// Navy hero (BrandHeroGradient, same fixed-color pattern as F02) holding the back button,
// badges, title/id, due/schedule panel, and lifecycle tracker - everything the F09 redesign
// mockup groups into one dark block above the light content sheet. Manager note/review note
// move to WorkOrderHeaderSection below instead, since the mockup puts those in the light area.
@Composable
private fun WorkOrderHeroSection(
    detail: WorkOrderDetail,
    onBack: () -> Unit,
) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    // Highest severity among the work order's own faults - severity is set by the Manager
    // (reliable), unlike priority_score which CV-16 was never wired up to compute (confirmed
    // against luxmap_backend's contract-drift.md). No fault (repair with none, or a survey
    // order) means no rank to show, so the dot is simply left out.
    val overallPriority = detail.faults.maxOfOrNull { severityFromWire(it.severity) }
    val due = dueDateLabel(detail.dueDate)
    // Same dark-theme AssetCondition text colors F02's SyncPill reuses for an on-navy accent -
    // not inventing a new pair for "due today"/"overdue" here either.
    val dueColor =
        when {
            due.isOverdue -> AssetCondition.OUT.badgeColors(isDark = true).text
            due.isToday -> AssetCondition.DIM.badgeColors(isDark = true).text
            else -> Color.White
        }
    val scheduleLabel = if (kind == WorkOrderTaskKind.REPAIR) "Lịch sửa chữa" else "Lịch kiểm tra"
    val scheduleValue = detail.scheduledDate?.let { DateFormatUtils.formatPlainDate(it) } ?: "Chưa có lịch"
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }
            Text(text = workOrderDetailTopBarTitle(detail.taskKind), style = TitleTextStyle, color = Color.White)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusBadge(text = kind.label(), colors = kind.badgeColors(), icon = kind.icon())
            StatusBadge(text = status.label(), colors = status.badgeColors())
            if (overallPriority != null) {
                PriorityDot(
                    text = "Ưu tiên ${overallPriority.label()}",
                    dotColor = overallPriority.color(),
                    textColor = Color.White,
                    borderColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = detail.title, style = HeadlineTextStyle, color = Color.White)
            Text(text = detail.workOrderId, style = LabelTextStyle, color = Color.White.copy(alpha = 0.7f))
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(Radius.card))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(Radius.card)),
        ) {
            HeroInfoCell(
                icon = Icons.Filled.Event,
                label = "Hạn xử lý",
                value = due.text,
                valueColor = dueColor,
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f)))
            HeroInfoCell(
                icon = Icons.Filled.Schedule,
                label = scheduleLabel,
                value = scheduleValue,
                valueColor = Color.White,
                modifier = Modifier.weight(1f),
            )
        }
        if (status != WorkOrderStatus.CANCELLED) {
            LifecycleTracker(
                steps = workOrderLifecycleSteps(status, kind),
                onDarkHero = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun HeroInfoCell(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp),
            )
            Text(text = label, style = LabelTextStyle, color = Color.White.copy(alpha = 0.7f))
        }
        Text(text = value, style = BodyStrongTextStyle, color = valueColor)
    }
}

// Manager note / review note only now - badges/title/due/schedule/lifecycle moved into
// WorkOrderHeroSection above. Review note first, then the note card - same order the mockup
// stacks them in, since a review request is the more urgent of the two.
@Composable
private fun WorkOrderHeaderSection(detail: WorkOrderDetail) {
    if (detail.note.isNullOrBlank() && detail.reviewNote.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (!detail.reviewNote.isNullOrBlank()) {
            InlineBanner(
                message = "Manager yêu cầu bổ sung: ${detail.reviewNote}",
                tone = InlineBannerTone.ERROR,
            )
        }
        if (!detail.note.isNullOrBlank()) {
            ManagerNoteCard(note = detail.note)
        }
    }
}

@Composable
private fun ManagerNoteCard(note: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.StickyNote2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Ghi chú từ Manager",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = note, style = MaterialTheme.typography.bodyMedium)
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
    val recordedCount = faults.count { faultOutcomes.containsKey(it.faultId) }
    val allRecorded = faults.isNotEmpty() && recordedCount == faults.size
    val pillText =
        when {
            !showOutcomePicker -> "${faults.size} sự cố"
            else -> "$recordedCount/${faults.size}"
        }
    // Same green-vs-navy pill convention the "Nghiệm thu" requirement count uses - green only
    // once every fault has a recorded outcome, navy otherwise.
    val pillColors =
        if (showOutcomePicker && allRecorded) {
            WorkOrderStatus.VERIFIED.badgeColors()
        } else {
            WorkOrderStatus.ASSIGNED.badgeColors()
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CompletionSectionHeader(
            icon = Icons.Filled.ReportProblem,
            title = if (kind == WorkOrderTaskKind.REPAIR) "Sự cố cần sửa" else "Sự cố cần kiểm tra",
            pillText = pillText,
            pillColors = pillColors,
        )
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
                        kind = kind,
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

private val FaultSectionAvatarSize = 32.dp
private val FaultNavigateButtonSize = 48.dp

// Card border highlights navy once the FE has picked an outcome for this fault (inspection
// only) - matches the mockup's per-fault "answered" signal, same idea as the kind/status
// badges already in use elsewhere, not a new color.
@Composable
private fun FaultRow(
    kind: WorkOrderTaskKind,
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
    showOutcomePicker: Boolean,
    selectedOutcome: String?,
    onOutcomeSelected: (String) -> Unit,
) {
    val priority = severityFromWire(fault.severity)
    val borderColor =
        if (showOutcomePicker && selectedOutcome != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, borderColor, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = fault.faultId, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(fault.poleId, faultTypeLabel(fault.faultType)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PriorityChip(priority = priority)
                    // Only a repair order already carries a past outcome worth showing here -
                    // an inspection order's outcome is what OutcomeSegment below is collecting.
                    if (kind == WorkOrderTaskKind.REPAIR && fault.inspectionOutcome != null) {
                        StatusBadge(
                            text = inspectionOutcomeLabel(fault.inspectionOutcome),
                            colors = WorkOrderStatus.VERIFIED.badgeColors(),
                        )
                    }
                }
            }
            if (fault.hasLocation()) {
                NavigateButton(onClick = onNavigate)
            } else {
                Text(
                    text = "Chưa có toạ độ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showOutcomePicker) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "Kết quả tại hiện trường",
                    style = LabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutcomeSegment(selected = selectedOutcome, onSelected = onOutcomeSelected)
            }
        }
    }
}

@Composable
private fun PriorityChip(priority: WorkOrderPriority) {
    Row(
        modifier =
            Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.badge))
                .padding(horizontal = Spacing.sm, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(priority.color(), CircleShape))
        Text(text = priority.label(), style = BadgeTextStyle)
    }
}

@Composable
private fun NavigateButton(onClick: () -> Unit) {
    val colors = WorkOrderStatus.ASSIGNED.badgeColors()
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(FaultNavigateButtonSize)
                .background(colors.background, RoundedCornerShape(Radius.control)),
    ) {
        Icon(imageVector = Icons.Filled.Navigation, contentDescription = "Điều hướng", tint = colors.text)
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
    val requirements = repairRequirements(state)
    val metCount = requirements.count { it.met }
    val pillColors =
        if (metCount == requirements.size) {
            WorkOrderStatus.VERIFIED.badgeColors()
        } else {
            WorkOrderStatus.ASSIGNED.badgeColors()
        }
    val focusManager = LocalFocusManager.current
    // Tapping the filled tile used to call onRetakeEvidence directly, silently discarding the
    // photo and reopening the camera - now it only opens a preview, which has its own explicit
    // retake action.
    var showEvidencePreview by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CompletionSectionHeader(
            icon = Icons.Filled.FactCheck,
            title = "Nghiệm thu",
            pillText = "$metCount/${requirements.size} yêu cầu",
            pillColors = pillColors,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            EvidencePicker(
                hasPhoto = hasPhoto,
                filePath = state.localEvidence?.filePath,
                onTileClick = if (hasPhoto) ({ showEvidencePreview = true }) else onCaptureEvidence,
            )
            OutlinedTextField(
                value = state.reportNote,
                onValueChange = onReportNoteChanged,
                label = { Text("Kết quả sửa chữa") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
            RequirementList(items = requirements)
        }
    }

    val evidenceFilePath = state.localEvidence?.filePath
    if (showEvidencePreview && evidenceFilePath != null) {
        EvidencePreviewDialog(
            filePath = evidenceFilePath,
            onDismiss = { showEvidencePreview = false },
            onRetake = {
                showEvidencePreview = false
                onRetakeEvidence()
            },
        )
    }
}

// Full-size look at the captured "after" photo, opened by tapping EvidencePicker's filled tile -
// retake is now an explicit choice here instead of the tile's only possible action.
@Composable
private fun EvidencePreviewDialog(
    filePath: String,
    onDismiss: () -> Unit,
    onRetake: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
                }
            }
            AsyncImage(
                model = File(filePath),
                contentDescription = "Ảnh sau sửa chữa",
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.card)),
            )
            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
            ) {
                Icon(imageVector = Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Chụp lại")
            }
        }
    }
}

// Shared by RepairCompletionSection ("Nghiệm thu") and InspectionCompletionSection ("Ghi chú
// kiểm tra") - same icon-avatar + title + requirement-count pill header FaultListSection uses.
@Composable
private fun CompletionSectionHeader(
    icon: ImageVector,
    title: String,
    pillText: String? = null,
    pillColors: BadgeColors? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(FaultSectionAvatarSize)
                        .background(Navy700, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        }
        if (pillText != null && pillColors != null) {
            StatusBadge(text = pillText, colors = pillColors)
        }
    }
}

@Composable
private fun InspectionCompletionSection(
    state: WorkOrderDetailUiState.Success,
    onReportNoteChanged: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CompletionSectionHeader(icon = Icons.Filled.EditNote, title = "Ghi chú kiểm tra")
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Ghi chú kiểm tra") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
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
