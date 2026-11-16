package com.luxmap.feature.workorder.ui.detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.TitleTextStyle
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.CancelRequestButton
import com.luxmap.core.ui.components.CancelRequestSheet
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.ui.detail.sections.FaultFooter
import com.luxmap.feature.workorder.ui.detail.sections.FaultListCard
import com.luxmap.feature.workorder.ui.detail.sections.InspectionOutcomeBadge
import com.luxmap.feature.workorder.ui.detail.sections.InspectionSection
import com.luxmap.feature.workorder.ui.detail.sections.RepairCompletionSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onStartSurvey: (workOrderId: String) -> Unit,
    onOpenPdf: (attachmentId: String) -> Unit = {},
    onMeasurePole: (poleId: String) -> Unit = {},
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
        onOpenPdf = onOpenPdf,
        onMeasurePole = onMeasurePole,
        onRetryAttachment = viewModel::retryAttachment,
        onRetry = viewModel::retry,
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
    onOpenPdf: (String) -> Unit = {},
    onMeasurePole: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSubmitConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelSheet by rememberSaveable { mutableStateOf(false) }
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (uiState is WorkOrderDetailUiState.Success) {
                    val kind = workOrderTaskKindFromWire(uiState.detail.taskKind)
                    LuxChildTopBar(
                        title = uiState.detail.title,
                        subtitle = "${kind.label()} · ${uiState.detail.workOrderId}",
                        onBack = onBack,
                    )
                } else {
                    LuxChildTopBar(title = "Chi tiết lệnh", onBack = onBack)
                }
            },
            bottomBar = {
                if (uiState is WorkOrderDetailUiState.Success) {
                    val cta = workOrderCtaState(state = uiState, onStart = onStart, onSubmit = onSubmit)
                    if (cta != null) {
                        val scheduleWindow =
                            workOrderScheduleWindow(uiState.detail.scheduledDate, today = LocalDate.now())
                        val showCancelRequest =
                            "request_cancellation" in uiState.detail.allowedActions &&
                                scheduleWindow.canActOnSchedule &&
                                (uiState.localCompletion == null || isReturnedForRework(uiState))
                        val onCtaClick = { if (cta.requiresConfirmation) showSubmitConfirm = true else cta.onClick() }
                        if (showCancelRequest) {
                            SideBySideBottomBar(
                                hint = cta.hint,
                                hintColor = cta.hintColor,
                                primaryText = cta.text,
                                primaryIcon = cta.icon,
                                primaryEnabled = cta.enabled,
                                onPrimaryClick = onCtaClick,
                                onCancelClick = { showCancelSheet = true },
                            )
                        } else {
                            BottomActionBar(
                                ctaText = cta.text,
                                ctaIcon = cta.icon,
                                onCtaClick = onCtaClick,
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
                    is WorkOrderDetailUiState.Loading -> WorkOrderDetailLoading()
                    is WorkOrderDetailUiState.Success ->
                        WorkOrderDetailContent(
                            state = uiState,
                            onNavigateFault = onNavigateFault,
                            onReportNoteChanged = onReportNoteChanged,
                            onMaterialsUsedChanged = onMaterialsUsedChanged,
                            onFaultOutcomeSelected = onFaultOutcomeSelected,
                            onCaptureEvidence = onCaptureEvidence,
                            onRetakeEvidence = onRetakeEvidence,
                            onOpenPdf = onOpenPdf,
                            onMeasurePole = onMeasurePole,
                            onRetryAttachment = onRetryAttachment,
                        )
                    is WorkOrderDetailUiState.Empty ->
                        LuxEmptyState(
                            title = "Không tìm thấy lệnh này",
                            message = "Lệnh có thể đã bị huỷ hoặc không còn thuộc địa bàn của bạn.",
                            actionLabel = "Quay lại",
                            onAction = onBack,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    is WorkOrderDetailUiState.Error ->
                        LuxErrorState(
                            onRetry = onRetry,
                            kind = if (uiState.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                            secondaryActionLabel = "Quay lại",
                            onSecondaryAction = onBack,
                            modifier = Modifier.align(Alignment.Center),
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

@Composable
private fun SideBySideBottomBar(
    hint: String?,
    hintColor: Color?,
    primaryText: String,
    primaryIcon: ImageVector,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md, start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hint != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(bottom = Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = hintColor ?: MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = hintColor ?: MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(PrimaryButtonHeight),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CancelRequestButton(
                    onClick = onCancelClick,
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = Spacing.sm),
                )
                PrimaryButton(
                    text = primaryText,
                    onClick = onPrimaryClick,
                    enabled = primaryEnabled,
                    trailingIcon = primaryIcon,
                    modifier = Modifier.weight(0.6f).fillMaxHeight(),
                )
            }
        }
    }
}

private data class SubmitSummaryRow(val icon: ImageVector, val iconColor: Color, val label: String, val value: String)

@Composable
private fun submitSummaryRows(
    kind: WorkOrderTaskKind,
    state: WorkOrderDetailUiState.Success,
): List<SubmitSummaryRow> =
    if (kind == WorkOrderTaskKind.INSPECTION) {
        val outcomes = state.faultOutcomes.values
        listOf(
            SubmitSummaryRow(
                Icons.Filled.Error,
                AssetCondition.OUT.badgeColors().text,
                "Có sự cố",
                outcomes.count { it == "fault_present" }.toString(),
            ),
            SubmitSummaryRow(
                Icons.Filled.CheckCircle,
                AssetCondition.NORMAL.badgeColors().text,
                "Không có sự cố",
                outcomes.count { it == "fault_absent" }.toString(),
            ),
            SubmitSummaryRow(
                Icons.Filled.Help,
                AssetCondition.DIM.badgeColors().text,
                "Chưa xác định",
                outcomes.count { it == "inconclusive" }.toString(),
            ),
        )
    } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
        val measured = state.poleMeasurements.map { it.poleId }.toSet().size
        listOf(
            SubmitSummaryRow(Icons.Filled.Navigation, Navy700, "Cột đã chụp", "$measured/${state.poleReferences.size}"),
            SubmitSummaryRow(Icons.Filled.StickyNote2, Navy700, "Tệp PDF", state.poleAttachments.size.toString()),
            SubmitSummaryRow(Icons.Filled.EditNote, Navy700, "Ghi chú", state.reportNote.trim().ifEmpty { "—" }),
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
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onMeasurePole: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val status = workOrderStatusFromWire(detail.woStatus)
    val hasActiveLocalCompletion = state.localCompletion != null && !isReturnedForRework(state)
    val canComplete = "complete" in detail.allowedActions && !hasActiveLocalCompletion
    // "complete" in allowedActions implies wo_status = in_progress (WorkOrderRules on the
    // backend), so this already means "started but overdue to submit", not "overdue to start".
    val isFormLocked = workOrderScheduleWindow(detail.scheduledDate, today = LocalDate.now()).isOverdueToComplete
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = Spacing.lg, top = Spacing.md, end = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            val messages =
                workOrderMessages(
                    detail = detail,
                    hasActiveLocalCompletion = hasActiveLocalCompletion,
                    syncStatus = state.syncStatus(),
                    isOnline = state.isOnline,
                    today = LocalDate.now(),
                )
            WorkOrderSummaryCard(
                detail = detail,
                showSubmittedBadge = hasActiveLocalCompletion,
                syncStatus = state.syncStatus(),
            )
            if (messages.showOffline) {
                LuxBanner(
                    title = "Đang ngoại tuyến",
                    message = "Thao tác sẽ được lưu trên thiết bị và đồng bộ khi có mạng.",
                    tone = LuxBannerTone.NEUTRAL,
                )
            }
            messages.prominent?.let { message ->
                LuxBanner(
                    title = message.title,
                    message = message.body.orEmpty(),
                    tone = message.tone.toBannerTone(),
                )
            }
            if (messages.notes.isNotEmpty()) {
                WorkOrderNotesCard(notes = messages.notes)
            }
            if (kind == WorkOrderTaskKind.SURVEY) {
                Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
            } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
                PoleGpsOverviewSection(
                    state = state,
                    canCapture = canComplete && !isFormLocked,
                    onOpenPdf = onOpenPdf,
                    onMeasurePole = onMeasurePole,
                    onRetryAttachment = onRetryAttachment,
                )
                if (canComplete) {
                    PoleGpsReportSection(
                        state = state,
                        locked = isFormLocked,
                        onReportNoteChanged = onReportNoteChanged,
                    )
                }
            } else if (kind == WorkOrderTaskKind.INSPECTION) {
                InspectionSection(
                    faults = detail.faults,
                    selectedOutcomes = state.faultOutcomes,
                    reportNote = state.reportNote,
                    editable = canComplete,
                    locked = isFormLocked,
                    onNavigateFault = onNavigateFault,
                    onFaultOutcomeSelected = onFaultOutcomeSelected,
                    onReportNoteChanged = onReportNoteChanged,
                )
                if (status == WorkOrderStatus.ASSIGNED) {
                    PreStartRequirementsPreview(kind = kind)
                }
            } else {
                FaultListCard(
                    title = "Sự cố cần sửa",
                    faults = detail.faults,
                    onNavigateFault = onNavigateFault,
                    footer = { fault ->
                        fault.inspectionOutcome?.let { outcome ->
                            FaultFooter { InspectionOutcomeBadge(outcome) }
                        }
                    },
                )
                if (status == WorkOrderStatus.ASSIGNED) {
                    PreStartRequirementsPreview(kind = kind)
                }
                if (canComplete) {
                    RepairCompletionSection(
                        evidenceFilePath = state.localEvidence?.filePath,
                        reportNote = state.reportNote,
                        materialsUsed = state.materialsUsed,
                        locked = isFormLocked,
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onCaptureEvidence = onCaptureEvidence,
                        onRetakeEvidence = onRetakeEvidence,
                    )
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

private fun WorkOrderMessageTone.toBannerTone(): LuxBannerTone =
    when (this) {
        WorkOrderMessageTone.ERROR -> LuxBannerTone.ERROR
        WorkOrderMessageTone.WARNING -> LuxBannerTone.WARNING
        WorkOrderMessageTone.NEUTRAL -> LuxBannerTone.NEUTRAL
        WorkOrderMessageTone.INFO -> LuxBannerTone.INFO
    }

@Composable
private fun WorkOrderSummaryCard(
    detail: WorkOrderDetail,
    showSubmittedBadge: Boolean,
    syncStatus: SyncStatus?,
) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val due = dueDateLabel(detail.dueDate)
    val dueColor =
        when {
            due.isOverdue -> LuxTheme.colors.dueOverdue
            due.isToday -> LuxTheme.colors.dueToday
            else -> MaterialTheme.colorScheme.onSurface
        }
    val scheduleLabel =
        when (kind) {
            WorkOrderTaskKind.REPAIR -> "Lịch sửa chữa"
            WorkOrderTaskKind.POLE_GPS_UPDATE -> "Lịch đo GPS"
            WorkOrderTaskKind.SURVEY -> "Lịch khảo sát"
            WorkOrderTaskKind.INSPECTION -> "Lịch kiểm tra"
        }
    val scheduleValue = detail.scheduledDate?.let { DateFormatUtils.formatPlainDate(it) } ?: "Chưa có lịch"
    LuxCard(modifier = Modifier.fillMaxWidth().testTag("work_order_summary")) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (showSubmittedBadge) {
                StatusBadge(
                    text = "Đã nộp",
                    colors = WorkOrderStatus.DONE.badgeColors(),
                    icon = WorkOrderStatus.DONE.icon(),
                )
            } else {
                StatusBadge(text = status.label(), colors = status.badgeColors(), icon = status.icon())
            }
            syncStatus?.let { sync ->
                StatusBadge(text = sync.label(), colors = sync.badgeColors(), icon = sync.icon())
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SummaryMetric(
                label = "Hạn xử lý",
                value = due.text,
                valueColor = dueColor,
                showAlert = due.isOverdue || due.isToday,
                modifier = Modifier.weight(1f),
            )
            SummaryMetric(
                label = scheduleLabel,
                value = scheduleValue,
                valueColor = MaterialTheme.colorScheme.onSurface,
                showAlert = false,
                modifier = Modifier.weight(1f),
            )
        }
        if (status != WorkOrderStatus.CANCELLED) {
            Spacer(Modifier.height(Spacing.md))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Giai đoạn hiện tại: ${kind.label()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryMetric(
    label: String,
    value: String,
    valueColor: Color,
    showAlert: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (showAlert) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = valueColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val NOTE_CLAMP_THRESHOLD = 110
private const val NOTE_CLAMP_LINES = 3

@Composable
private fun WorkOrderNotesCard(notes: List<WorkOrderNote>) {
    LuxCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Ghi chú & lịch sử",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        notes.forEachIndexed { index, note ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Spacing.md),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            } else {
                Spacer(Modifier.height(Spacing.md))
            }
            WorkOrderNoteRow(note = note)
        }
    }
}

@Composable
private fun WorkOrderNoteRow(note: WorkOrderNote) {
    var expanded by rememberSaveable(note.label) { mutableStateOf(false) }
    val canToggle = note.text.length > NOTE_CLAMP_THRESHOLD
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = note.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (note.text.isNotBlank()) {
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (canToggle && !expanded) NOTE_CLAMP_LINES else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canToggle) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (expanded) "Thu gọn" else "Xem thêm")
            }
        }
    }
}

private val FaultSectionAvatarSize = 32.dp

private val StepNumberSize = 24.dp
private val StepConnectorWidth = 2.dp

@Composable
private fun WorkflowStepRow(
    number: Int,
    title: String,
    required: Boolean,
    done: Boolean,
    detail: String? = null,
    isLast: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val tagColors =
        when {
            !required -> WorkOrderStatus.OPEN.badgeColors()
            done -> WorkOrderStatus.VERIFIED.badgeColors()
            else -> AssetCondition.OUT.badgeColors()
        }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.fillMaxHeight().width(StepNumberSize),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(StepNumberSize)
                        .clip(CircleShape)
                        .background(if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (done) 0.dp else 2.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(text = "$number", style = BadgeTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isLast) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .width(StepConnectorWidth)
                            .padding(vertical = Spacing.xs)
                            .background(
                                if (done) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = Spacing.md, bottom = if (isLast) 0.dp else Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = title,
                    style = BodyStrongTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(text = if (required) "Bắt buộc" else "Không bắt buộc", colors = tagColors)
            }
            if (detail != null) {
                Text(text = detail, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

private data class PreStartItem(
    val icon: ImageVector,
    val title: String,
    val hint: String,
    val required: Boolean,
)

private val PRE_START_ITEMS =
    mapOf(
        WorkOrderTaskKind.REPAIR to
            listOf(
                PreStartItem(Icons.Filled.PhotoCamera, "Ảnh sau sửa chữa", "Chụp rõ chóa đèn sau khi sửa", true),
                PreStartItem(Icons.Filled.EditNote, "Kết quả sửa chữa", "Tối thiểu 10 ký tự", true),
                PreStartItem(Icons.Filled.Inventory2, "Vật tư đã thay", "Ghi lại nếu có thay vật tư", false),
            ),
        WorkOrderTaskKind.INSPECTION to
            listOf(
                PreStartItem(
                    Icons.Filled.FactCheck,
                    "Kết quả từng sự cố",
                    "Có sự cố / Không có / Chưa rõ cho từng sự cố",
                    true,
                ),
                PreStartItem(Icons.Filled.EditNote, "Ghi chú kiểm tra", "Tối thiểu 10 ký tự", true),
            ),
    )

@Composable
private fun PreStartRequirementsPreview(kind: WorkOrderTaskKind) {
    val items = PRE_START_ITEMS[kind] ?: return
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.block))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.block)),
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Cần chuẩn bị để nghiệm thu",
                style = BodyStrongTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Theo cấu hình của Kỹ sư bảo trì · nhập sau khi bắt đầu",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items.forEach { item ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = item.hint, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(
                    text = if (item.required) "Bắt buộc" else "Không bắt buộc",
                    colors =
                        if (item.required) {
                            AssetCondition.OUT.badgeColors()
                        } else {
                            WorkOrderStatus.OPEN.badgeColors()
                        },
                )
            }
        }
    }
}

@Composable
private fun PoleGpsOverviewSection(
    state: WorkOrderDetailUiState.Success,
    canCapture: Boolean,
    onOpenPdf: (String) -> Unit,
    onMeasurePole: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingDownload by remember {
        mutableStateOf<com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment?>(null)
    }
    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            val localPath = pendingDownload?.localPath
            pendingDownload = null
            if (uri != null && localPath != null) {
                scope.launch {
                    val saved =
                        withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openOutputStream(uri)?.use { output ->
                                    File(localPath).inputStream().use { it.copyTo(output) }
                                }
                            }.isSuccess
                        }
                    val message = if (saved) "Đã lưu file vào thiết bị" else "Không lưu được file"
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CompletionSectionHeader(
            icon = Icons.Filled.StickyNote2,
            title = "Tệp PDF",
            pillText = "${state.poleAttachments.size} file",
            pillColors = WorkOrderStatus.ASSIGNED.badgeColors(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.poleAttachments.forEach { attachment ->
                PdfAttachmentRow(
                    attachment = attachment,
                    onOpen = { onOpenPdf(attachment.attachmentId) },
                    onRetry = { onRetryAttachment(attachment.attachmentId) },
                    onDownload = {
                        pendingDownload = attachment
                        saveLauncher.launch(attachment.fileName)
                    },
                )
            }
        }
        val referenceIds = state.poleReferences.map { it.poleId }.toSet()
        val measuredIds = state.poleMeasurements.map { it.poleId }.toSet().intersect(referenceIds)
        CompletionSectionHeader(
            icon = Icons.Filled.Navigation,
            title = "Cột đèn",
            pillText = "${measuredIds.size}/${state.poleReferences.size} đã đo",
            pillColors = WorkOrderStatus.ASSIGNED.badgeColors(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            state.poleReferences.forEach { reference ->
                PoleGpsRow(
                    reference = reference,
                    measurement = state.poleMeasurements.firstOrNull { it.poleId == reference.poleId },
                    enabled = canCapture,
                    onClick = { onMeasurePole(reference.poleId) },
                )
            }
        }
    }
}

@Composable
private fun PdfAttachmentRow(
    attachment: com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
) {
    val (canTap, onTap) =
        when (attachment.status) {
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.READY -> true to onOpen
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.FAILED -> true to onRetry
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.DOWNLOADING -> false to {}
            com.luxmap.feature.workorder.polegps.data.AttachmentStatus.PENDING -> false to {}
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.card))
                .clickable(onClick = onTap, enabled = canTap)
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Filled.StickyNote2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = attachment.fileName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (attachment.status == com.luxmap.feature.workorder.polegps.data.AttachmentStatus.READY) {
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Tải file ${attachment.fileName} về máy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PoleGpsRow(
    reference: com.luxmap.feature.workorder.polegps.data.PoleReference,
    measurement: com.luxmap.feature.workorder.polegps.data.PoleMeasurement?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val oldPosition =
        if (reference.lat != null && reference.lng != null) {
            "%.6f, %.6f".format(reference.lat, reference.lng)
        } else {
            "Chưa có"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.card))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = reference.poleId, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "GPS cũ: $oldPosition",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // No minimum-accuracy warning here: WorkOrderService.cs's Report() only validates
        // lat in [-90, 90], lng in [-180, 180] and accuracy_m >= 0 and finite - there is no
        // BE-enforced "good enough" threshold to warn against (the 8m figure removed from here
        // belonged to the now-deleted continuous-video-survey flow, a different feature).
        when {
            measurement != null -> StatusBadge(text = "Đã đo", colors = WorkOrderStatus.VERIFIED.badgeColors())
            !enabled -> StatusBadge(text = "Chưa bắt đầu", colors = WorkOrderStatus.OPEN.badgeColors())
            else -> StatusBadge(text = "Chưa chụp", colors = WorkOrderStatus.OPEN.badgeColors())
        }
    }
}

@Composable
private fun PoleGpsReportSection(
    state: WorkOrderDetailUiState.Success,
    locked: Boolean,
    onReportNoteChanged: (String) -> Unit,
) {
    val noteValid = state.reportNote.trim().length >= 10
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CompletionSectionHeader(
            icon = Icons.Filled.FactCheck,
            title = "Báo cáo",
            pillText = if (noteValid) "Đã đủ" else "Cần ghi chú",
            pillColors =
                if (noteValid) WorkOrderStatus.VERIFIED.badgeColors() else WorkOrderStatus.ASSIGNED.badgeColors(),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
        ) {
            WorkflowStepRow(
                number = 1,
                title = "Kết quả đo GPS",
                required = true,
                done = noteValid,
                detail = "${state.reportNote.trim().length}/10 ký tự tối thiểu",
                isLast = true,
            ) {
                OutlinedTextField(
                    value = state.reportNote,
                    onValueChange = onReportNoteChanged,
                    placeholder = { Text("VD: Đã đo xong các cột, 1 cột chưa bắt được tín hiệu GPS") },
                    enabled = !locked,
                    minLines = 2,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

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
private fun WorkOrderDetailLoading() {
    LuxLoadingRegion(
        description = "Đang tải chi tiết lệnh",
        modifier = Modifier.fillMaxWidth().padding(Spacing.lg).testTag("work_order_detail_loading"),
    ) {
        LuxSkeleton(modifier = Modifier.fillMaxWidth().height(140.dp), shape = MaterialTheme.shapes.medium)
        LuxSkeleton(modifier = Modifier.width(160.dp).height(20.dp))
        repeat(3) {
            LuxSkeleton(modifier = Modifier.fillMaxWidth().height(72.dp), shape = MaterialTheme.shapes.medium)
        }
    }
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
