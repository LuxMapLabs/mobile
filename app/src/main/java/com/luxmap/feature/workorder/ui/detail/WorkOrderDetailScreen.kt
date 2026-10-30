package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.ScreenMargin
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
import com.luxmap.core.ui.components.CancelRequestButton
import com.luxmap.core.ui.components.CancelRequestSheet
import com.luxmap.core.ui.components.DueTodayColor
import com.luxmap.core.ui.components.EvidencePicker
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.LifecycleTracker
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.OutcomeSegment
import com.luxmap.core.ui.components.Requirement
import com.luxmap.core.ui.components.RequirementList
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

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
        onRequestCancellation = viewModel::requestCancellation,
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
    onRequestCancellation: (reason: String) -> Unit = {},
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSubmitConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelSheet by rememberSaveable { mutableStateOf(false) }
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
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
                        val scheduleWindow =
                            workOrderScheduleWindow(uiState.detail.scheduledDate, today = LocalDate.now())
                        Column {
                            val showCancelRequest =
                                "request_cancellation" in uiState.detail.allowedActions &&
                                    scheduleWindow.canActOnSchedule
                            if (showCancelRequest) {
                                CancelRequestButton(
                                    onClick = { showCancelSheet = true },
                                    modifier = Modifier.padding(horizontal = ScreenMargin).padding(top = Spacing.sm),
                                )
                            }
                            BottomActionBar(
                                ctaText = cta.text,
                                ctaIcon = cta.icon,
                                onCtaClick = {
                                    if (cta.requiresConfirmation) showSubmitConfirm = true else cta.onClick()
                                },
                                ctaEnabled = cta.enabled,
                                hint = cta.hint,
                                hintColor = cta.hintColor,
                                showHintIcon = cta.hint != null,
                                progressText = cta.progressText,
                                progressSegments = cta.progressSegments,
                            )
                        }
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
        TopSlideToast(
            state = toastState,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }

    if (showSubmitConfirm && uiState is WorkOrderDetailUiState.Success) {
        SubmitConfirmationSheet(
            state = uiState,
            onDismiss = { showSubmitConfirm = false },
            onConfirm = {
                showSubmitConfirm = false
                onSubmit()
                scope.launch {
                    toastState.show("Nộp báo cáo thành công · Chờ Kỹ sư bảo trì duyệt")
                }
            },
        )
    }

    if (showCancelSheet && uiState is WorkOrderDetailUiState.Success) {
        CancelRequestSheet(
            isSubmitting = uiState.isRequestingCancellation,
            errorMessage = uiState.cancellationError,
            onDismiss = { showCancelSheet = false },
            onSend = onRequestCancellation,
        )
    }

    LaunchedEffect(uiState) {
        val success = uiState as? WorkOrderDetailUiState.Success
        if (showCancelSheet && success?.detail?.cancellationRequestStatus == "pending") {
            showCancelSheet = false
            scope.launch {
                toastState.show("Đã gửi yêu cầu hủy · Chờ Kỹ sư bảo trì duyệt")
            }
        }
    }
}

private data class WorkOrderCtaState(
    val text: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onClick: () -> Unit,
    val hint: String? = null,
    val hintColor: Color? = null,
    val progressText: String? = null,
    val progressSegments: List<Boolean>? = null,
    val requiresConfirmation: Boolean = false,
)

// Single bottom-pinned CTA slot for every state this screen can be in - matches the F09
// redesign mockup, which always pins exactly one button (start/complete/locked/done) instead of
// the old layout's separate inline buttons for each case.
private fun workOrderCtaState(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onSubmit: () -> Unit,
    today: LocalDate = LocalDate.now(),
): WorkOrderCtaState? {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val canSubmitNow = state.canSubmit() && !state.isSubmitting
    val scheduleWindow = workOrderScheduleWindow(detail.scheduledDate, today)
    return when {
        "start" in detail.allowedActions && scheduleWindow.canActOnSchedule ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.PlayArrow,
                enabled = !state.isStarting,
                onClick = onStart,
            )
        // scheduleWindow.unlocksOn is only null when there is no scheduled_date, and that case
        // always has canActOnSchedule = true, so it never reaches this branch.
        "start" in detail.allowedActions ->
            WorkOrderCtaState(
                text = "Bắt đầu",
                icon = Icons.Filled.Lock,
                enabled = false,
                onClick = {},
                hint = "Mở vào " + DateFormatUtils.formatPlainDate(scheduleWindow.unlocksOn.toString()),
                hintColor = Gray500,
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
                hintColor = DueTodayColor,
                requiresConfirmation = true,
            )
        "complete" in detail.allowedActions && kind == WorkOrderTaskKind.INSPECTION -> {
            val recordedCount = detail.faults.count { state.faultOutcomes.containsKey(it.faultId) }
            val missingCount = detail.faults.size - recordedCount
            WorkOrderCtaState(
                text = "Nộp báo cáo kiểm tra",
                icon = if (canSubmitNow) Icons.Filled.Send else Icons.Filled.Lock,
                enabled = canSubmitNow,
                onClick = onSubmit,
                hint = if (missingCount > 0) "Còn $missingCount sự cố chưa có kết quả" else null,
                hintColor = DueTodayColor,
                progressText = "$recordedCount/${detail.faults.size} sự cố đã có kết quả",
                progressSegments = detail.faults.map { state.faultOutcomes.containsKey(it.faultId) },
                requiresConfirmation = true,
            )
        }
        workOrderStatusFromWire(detail.woStatus) == WorkOrderStatus.DONE && "start" !in detail.allowedActions ->
            WorkOrderCtaState(
                text = "Đã nộp báo cáo",
                icon = Icons.Filled.HourglassTop,
                enabled = false,
                onClick = {},
                hint = "Chờ Kỹ sư bảo trì duyệt (cổng #2)",
                hintColor = Gray500,
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

private data class SubmitSummaryRow(val icon: ImageVector, val iconColor: Color, val label: String, val value: String)

private fun submitSummaryRows(
    kind: WorkOrderTaskKind,
    state: WorkOrderDetailUiState.Success,
): List<SubmitSummaryRow> =
    if (kind == WorkOrderTaskKind.INSPECTION) {
        val outcomes = state.faultOutcomes.values
        listOf(
            SubmitSummaryRow(
                Icons.Filled.Error,
                AssetCondition.OUT.badgeColors(isDark = false).text,
                "Có sự cố",
                outcomes.count { it == "fault_present" }.toString(),
            ),
            SubmitSummaryRow(
                Icons.Filled.CheckCircle,
                AssetCondition.NORMAL.badgeColors(isDark = false).text,
                "Không có sự cố",
                outcomes.count { it == "fault_absent" }.toString(),
            ),
            SubmitSummaryRow(
                Icons.Filled.Help,
                AssetCondition.DIM.badgeColors(isDark = false).text,
                "Chưa xác định",
                outcomes.count { it == "inconclusive" }.toString(),
            ),
        )
    } else {
        listOf(
            SubmitSummaryRow(Icons.Filled.ReportProblem, Gray500, "Sự cố đã sửa", state.detail.faults.size.toString()),
            SubmitSummaryRow(
                Icons.Filled.PhotoCamera,
                Navy700,
                "Ảnh nghiệm thu",
                if (state.localEvidence != null) "1 ảnh" else "0 ảnh",
            ),
            SubmitSummaryRow(Icons.Filled.EditNote, Navy700, "Kết quả", state.reportNote.trim().ifEmpty { "—" }),
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubmitConfirmationSheet(
    state: WorkOrderDetailUiState.Success,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val rows = submitSummaryRows(kind, state)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Nộp báo cáo?", style = TitleTextStyle, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = detail.workOrderId,
                        style = LabelTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.card))
                        .padding(horizontal = Spacing.md),
            ) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = null,
                            tint = row.iconColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = row.value,
                            style = BodyStrongTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Text(
                text =
                    "Báo cáo sẽ chuyển cho Kỹ sư bảo trì duyệt. " +
                        "Bạn không chỉnh sửa được cho đến khi có kết quả duyệt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
                    contentPadding = PaddingValues(horizontal = Spacing.sm),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Kiểm tra lại", maxLines = 1)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.5f).heightIn(min = Dimens.minTouchTarget),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
                ) {
                    Text("Nộp báo cáo")
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(imageVector = Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
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
            WorkOrderSummaryCard(detail)
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
    val kindColors = kind.badgeColors()
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
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }
            Text(
                text = detail.workOrderId,
                style = BodyStrongTextStyle,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(HeroKindIconSize)
                        .background(kindColors.background, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = kind.icon(), contentDescription = null, tint = kindColors.text)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(text = detail.title, style = HeadlineTextStyle, color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusBadge(
                        text = kind.label(),
                        colors = BadgeColors(Color.White.copy(alpha = 0.14f), Color.White),
                    )
                    StatusBadge(text = status.label(), colors = status.badgeColors())
                }
            }
        }
    }
}

private val HeroKindIconSize = 56.dp

@Composable
private fun WorkOrderSummaryCard(detail: WorkOrderDetail) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val overallPriority = detail.faults.maxOfOrNull { severityFromWire(it.severity) }
    val scheduleLabel = if (kind == WorkOrderTaskKind.REPAIR) "Lịch sửa chữa" else "Lịch kiểm tra"
    val scheduleValue = detail.scheduledDate?.let { DateFormatUtils.formatPlainDate(it) } ?: "Chưa có lịch"
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.block))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.block))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryMetricCell(
                label = scheduleLabel,
                value = scheduleValue,
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (overallPriority != null) {
                SummaryPriorityCell(priority = overallPriority, modifier = Modifier.weight(1f))
            }
        }
        if (status != WorkOrderStatus.CANCELLED) {
            LifecycleTracker(steps = workOrderLifecycleSteps(status, kind))
        }
    }
}

@Composable
private fun SummaryMetricCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip))
                .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = label, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = BodyStrongTextStyle,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryPriorityCell(
    priority: WorkOrderPriority,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip))
                .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = "Ưu tiên", style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(modifier = Modifier.size(8.dp).background(priority.color(), CircleShape))
            Text(text = priority.label(), style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun WorkOrderHeaderSection(detail: WorkOrderDetail) {
    val hasCancellationBanner = detail.cancellationRequestStatus != "none"
    val isPastSchedule = workOrderScheduleWindow(detail.scheduledDate, today = LocalDate.now()).isOverdueToComplete
    val status = workOrderStatusFromWire(detail.woStatus)
    val isOverdueToStart = isPastSchedule && status == WorkOrderStatus.ASSIGNED
    val isOverdueToComplete = isPastSchedule && status == WorkOrderStatus.IN_PROGRESS
    val hasAnyBanner =
        !detail.note.isNullOrBlank() ||
            !detail.reviewNote.isNullOrBlank() ||
            hasCancellationBanner ||
            isOverdueToStart ||
            isOverdueToComplete
    if (!hasAnyBanner) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (isOverdueToStart) {
            val dueText = DateFormatUtils.formatPlainDate(detail.scheduledDate)
            InlineBanner(
                message = "Quá hạn bắt đầu (dự kiến $dueText) · vẫn bắt đầu được bình thường",
                tone = InlineBannerTone.ERROR,
            )
        }
        if (isOverdueToComplete) {
            val dueText = DateFormatUtils.formatPlainDate(detail.scheduledDate)
            InlineBanner(
                message = "Quá hạn hoàn thành (dự kiến $dueText) · vẫn nộp được bình thường",
                tone = InlineBannerTone.ERROR,
            )
        }
        if (!detail.reviewNote.isNullOrBlank()) {
            InlineBanner(
                message = "Manager yêu cầu bổ sung: ${detail.reviewNote}",
                tone = InlineBannerTone.ERROR,
            )
        }
        when (detail.cancellationRequestStatus) {
            "pending" ->
                InlineBanner(
                    message = "Đã gửi yêu cầu hủy lệnh · Chờ Kỹ sư bảo trì duyệt",
                    tone = InlineBannerTone.OFFLINE,
                )
            "rejected" ->
                InlineBanner(
                    message = "Yêu cầu hủy bị từ chối: ${detail.cancellationRejectNote}",
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
private val FaultTypeIconSize = 44.dp
private val FaultNavigateButtonSize = 48.dp

private fun faultTypeVisual(faultType: String): Pair<ImageVector, BadgeColors> =
    when (faultType) {
        "lamp_out" -> Icons.Filled.Lightbulb to AssetCondition.OUT.badgeColors(isDark = false)
        "lamp_dim" -> Icons.Filled.WbTwilight to AssetCondition.DIM.badgeColors(isDark = false)
        "segment_outage" -> Icons.Filled.PowerOff to AssetCondition.OUT.badgeColors(isDark = false)
        "node_offline" -> Icons.Filled.SensorsOff to AssetCondition.UNKNOWN.badgeColors(isDark = false)
        "runtime_decline" -> Icons.Filled.TrendingDown to AssetCondition.DIM.badgeColors(isDark = false)
        else -> Icons.Filled.ReportProblem to AssetCondition.UNKNOWN.badgeColors(isDark = false)
    }

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
    val (typeIcon, typeColors) = faultTypeVisual(fault.faultType)
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
            Box(
                modifier =
                    Modifier
                        .size(FaultTypeIconSize)
                        .background(typeColors.background, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = typeIcon, contentDescription = null, tint = typeColors.text)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(text = faultTypeLabel(fault.faultType), style = MaterialTheme.typography.titleMedium)
                    PriorityDotLabel(priority = priority)
                }
                Text(
                    text = listOfNotNull(fault.poleId, fault.faultId).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (kind == WorkOrderTaskKind.REPAIR && fault.inspectionOutcome != null) {
                    StatusBadge(
                        text = inspectionOutcomeLabel(fault.inspectionOutcome),
                        colors = WorkOrderStatus.VERIFIED.badgeColors(),
                    )
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
private fun PriorityDotLabel(priority: WorkOrderPriority) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(7.dp).background(priority.color(), CircleShape))
        Text(text = priority.label(), style = BadgeTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
