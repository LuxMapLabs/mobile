package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.BottomActionBar
import com.luxmap.core.ui.components.CancelRequestSheet
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxBottomSheet
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxSnackbarHost
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.EvidenceKind
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.ui.detail.sections.CompletionEvidenceSection
import com.luxmap.feature.workorder.ui.detail.sections.FaultFooter
import com.luxmap.feature.workorder.ui.detail.sections.FaultListCard
import com.luxmap.feature.workorder.ui.detail.sections.InspectionOutcomeBadge
import com.luxmap.feature.workorder.ui.detail.sections.InspectionSection
import com.luxmap.feature.workorder.ui.detail.sections.PoleGpsSection
import com.luxmap.feature.workorder.ui.detail.sections.RepairCompletionSection
import com.luxmap.feature.workorder.ui.detail.sections.SurveySection
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onCaptureEvidence: (EvidenceKind) -> Unit,
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
        onCaptureEvidence = {
            val state = uiState as? WorkOrderDetailUiState.Success
            when (state?.detail?.taskKind) {
                "inspection" -> viewModel.prepareEvidenceCapture { onCaptureEvidence(EvidenceKind.OBSERVATION) }
                "repair" -> viewModel.prepareEvidenceCapture { onCaptureEvidence(EvidenceKind.AFTER) }
                else -> Unit
            }
        },
        onRemoveEvidence = viewModel::removeEvidence,
        onRecoverEvidence = viewModel::recoverEvidenceSubmission,
        onOpenPdf = onOpenPdf,
        onMeasurePole = onMeasurePole,
        onRetryAttachment = viewModel::retryAttachment,
        onRetry = viewModel::retry,
        onOpenSurvey = onStartSurvey,
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
    onRemoveEvidence: (String) -> Unit,
    onRecoverEvidence: (Boolean) -> Unit = {},
    onOpenPdf: (String) -> Unit = {},
    onMeasurePole: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenSurvey: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSubmitConfirm by rememberSaveable { mutableStateOf(false) }
    var showCancelSheet by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { LuxSnackbarHost(snackbarHostState) },
        modifier = modifier,
        topBar = {
            val title =
                if (uiState is WorkOrderDetailUiState.Success) {
                    workOrderHeaderTitle(workOrderTaskKindFromWire(uiState.detail.taskKind))
                } else {
                    "Chi tiết lệnh"
                }
            LuxChildTopBar(title = title, onBack = onBack)
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
                            !uiState.hasActiveLocalCompletion()
                    val onCtaClick = { if (cta.requiresConfirmation) showSubmitConfirm = true else cta.onClick() }
                    val actionError = uiState.startError ?: uiState.submitError
                    BottomActionBar(
                        ctaText = cta.text,
                        ctaIcon = cta.icon,
                        onCtaClick = onCtaClick,
                        ctaEnabled = cta.enabled,
                        hint = actionError ?: cta.hint,
                        hintColor = if (actionError != null) MaterialTheme.colorScheme.error else cta.hintColor,
                        showHintIcon = actionError != null || cta.hint != null,
                        progressText = cta.progressText,
                        progressSegments = cta.progressSegments,
                        secondaryText = if (showCancelRequest) "Yêu cầu hủy" else null,
                        onSecondaryClick = if (showCancelRequest) ({ showCancelSheet = true }) else null,
                    )
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
                        onOpenSurvey = onOpenSurvey,
                        onNavigateFault = onNavigateFault,
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onFaultOutcomeSelected = onFaultOutcomeSelected,
                        onCaptureEvidence = onCaptureEvidence,
                        onRemoveEvidence = onRemoveEvidence,
                        onRecoverEvidence = onRecoverEvidence,
                        onOpenPdf = onOpenPdf,
                        onMeasurePole = onMeasurePole,
                        onRetryAttachment = onRetryAttachment,
                        onAttachmentFeedback = { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        },
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

    if (showSubmitConfirm && uiState is WorkOrderDetailUiState.Success) {
        SubmitConfirmationSheet(
            state = uiState,
            onDismiss = { showSubmitConfirm = false },
            onConfirm = {
                showSubmitConfirm = false
                onSubmit()
                scope.launch {
                    snackbarHostState.showSnackbar("Nộp báo cáo thành công. Chờ Kỹ sư bảo trì duyệt")
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
                snackbarHostState.showSnackbar("Đã gửi yêu cầu hủy. Chờ Kỹ sư bảo trì duyệt")
            }
        }
    }
}

private data class SubmitSummaryRow(val icon: ImageVector, val iconColor: Color, val label: String, val value: String)

@Composable
private fun submitSummaryRows(
    kind: WorkOrderTaskKind,
    state: WorkOrderDetailUiState.Success,
): List<SubmitSummaryRow> {
    val primary = MaterialTheme.colorScheme.primary
    return if (kind == WorkOrderTaskKind.INSPECTION) {
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
            SubmitSummaryRow(
                Icons.Filled.PhotoCamera,
                primary,
                "Ảnh hiện trường",
                "${state.localEvidence.count { it.kind == "observation" }} ảnh",
            ),
        )
    } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
        val measured = state.poleMeasurements.map { it.poleId }.toSet().size
        listOf(
            SubmitSummaryRow(Icons.Filled.Navigation, primary, "Cột đã chụp", "$measured/${state.poleReferences.size}"),
            SubmitSummaryRow(Icons.Filled.StickyNote2, primary, "Tệp PDF", state.poleAttachments.size.toString()),
            SubmitSummaryRow(Icons.Filled.EditNote, primary, "Ghi chú", state.reportNote.trim().ifEmpty { "—" }),
        )
    } else {
        listOf(
            SubmitSummaryRow(
                Icons.Filled.ReportProblem,
                MaterialTheme.colorScheme.onSurfaceVariant,
                "Sự cố đã sửa",
                state.detail.faults.size.toString(),
            ),
            SubmitSummaryRow(
                Icons.Filled.PhotoCamera,
                primary,
                "Ảnh nghiệm thu",
                "${state.localEvidence.count { it.kind == "after" }} ảnh",
            ),
            SubmitSummaryRow(Icons.Filled.EditNote, primary, "Kết quả", state.reportNote.trim().ifEmpty { "—" }),
        )
    }
}

@Composable
private fun SubmitConfirmationSheet(
    state: WorkOrderDetailUiState.Success,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val rows = submitSummaryRows(kind, state)
    LuxBottomSheet(title = "Nộp báo cáo?", onDismiss = onDismiss) {
        Text(
            text = "${kind.label()} ${detail.workOrderId}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.md),
        ) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .padding(vertical = Spacing.xs)
                            .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = row.icon,
                        contentDescription = null,
                        tint = row.iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
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
        LuxActionPair(
            secondary = { actionModifier ->
                LuxSecondaryButton(
                    text = "Kiểm tra lại",
                    onClick = onDismiss,
                    modifier = actionModifier,
                )
            },
            primary = { actionModifier ->
                PrimaryButton(
                    text = "Nộp báo cáo",
                    onClick = onConfirm,
                    trailingIcon = Icons.Filled.Send,
                    modifier = actionModifier,
                )
            },
        )
    }
}

@Composable
private fun WorkOrderDetailContent(
    state: WorkOrderDetailUiState.Success,
    onOpenSurvey: (String) -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRemoveEvidence: (String) -> Unit,
    onRecoverEvidence: (Boolean) -> Unit,
    onOpenPdf: (String) -> Unit,
    onMeasurePole: (String) -> Unit,
    onRetryAttachment: (String) -> Unit,
    onAttachmentFeedback: (String) -> Unit,
) {
    val detail = state.detail
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    val status = workOrderStatusFromWire(detail.woStatus)
    val hasActiveLocalCompletion = state.hasActiveLocalCompletion()
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
                SurveySection(
                    started = status == WorkOrderStatus.IN_PROGRESS,
                    canStart = "start" in detail.allowedActions,
                    onOpenSurvey = { onOpenSurvey(detail.workOrderId) },
                )
            } else if (kind == WorkOrderTaskKind.POLE_GPS_UPDATE) {
                PoleGpsSection(
                    attachments = state.poleAttachments,
                    references = state.poleReferences,
                    measurements = state.poleMeasurements,
                    canCapture = canComplete && !isFormLocked,
                    showReport = canComplete,
                    reportNote = state.reportNote,
                    locked = isFormLocked,
                    onOpenPdf = onOpenPdf,
                    onMeasurePole = onMeasurePole,
                    onRetryAttachment = onRetryAttachment,
                    onReportNoteChanged = onReportNoteChanged,
                    onAttachmentFeedback = onAttachmentFeedback,
                )
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
                val photos = state.localEvidence.filter { it.kind == "observation" }
                if (canComplete || photos.isNotEmpty()) {
                    LuxCard {
                        CompletionEvidenceSection(
                            title = "Ảnh hiện trường",
                            required = true,
                            photos = photos,
                            editable = canComplete && !isFormLocked && !state.isSubmitting,
                            onAdd = onCaptureEvidence,
                            onRemove = onRemoveEvidence,
                        )
                    }
                }
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
                val photos = state.localEvidence.filter { it.kind == "after" }
                if (canComplete || photos.isNotEmpty()) {
                    RepairCompletionSection(
                        photos = photos,
                        reportNote = state.reportNote,
                        materialsUsed = state.materialsUsed,
                        locked = !canComplete || isFormLocked || state.isSubmitting,
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onCaptureEvidence = onCaptureEvidence,
                        onRemoveEvidence = onRemoveEvidence,
                    )
                }
            }
            if ("complete" in detail.allowedActions && state.localCompletion?.submitStatus == "failed") {
                EvidenceRecoveryActions(
                    state = state,
                    allowReplacement = !isFormLocked,
                    onRecover = onRecoverEvidence,
                )
            }
        }
    }
}

@Composable
private fun EvidenceRecoveryActions(
    state: WorkOrderDetailUiState.Success,
    allowReplacement: Boolean,
    onRecover: (Boolean) -> Unit,
) {
    val replace = state.localEvidence.any { it.uploadStatus == "replaceable_error" }
    val retry =
        state.localEvidence.any { it.uploadStatus == "retryable_error" } &&
            state.localEvidence.none { it.uploadStatus in setOf("replaceable_error", "failed", "conflict") }
    val reopenGps = state.detail.taskKind == "pole_gps_update" && !retry
    val canReplace = replace || reopenGps
    var confirmReplacement by rememberSaveable { mutableStateOf(false) }
    if (canReplace && allowReplacement) {
        LuxSecondaryButton(
            text = if (reopenGps) "Mở lại báo cáo GPS" else "Thay ảnh lỗi",
            enabled = !state.isSubmitting,
            onClick = { confirmReplacement = true },
        )
    } else if (retry) {
        LuxSecondaryButton(text = "Thử lại ảnh lỗi", enabled = !state.isSubmitting, onClick = { onRecover(false) })
    }
    if (confirmReplacement && canReplace && allowReplacement) {
        LuxConfirmDialog(
            title = "Mở lại bản nộp?",
            message =
                if (reopenGps) {
                    "App giữ lại ảnh, tọa độ và ghi chú. Hãy kiểm tra các cột, " +
                        "chụp và đo lại cột thiếu dữ liệu trước khi nộp."
                } else {
                    "App sẽ bỏ các ảnh hỏng hoặc bị từ chối và giữ lại nội dung báo cáo cùng các ảnh còn lại."
                },
            confirmLabel = if (reopenGps) "Mở lại báo cáo" else "Mở lại để thay ảnh",
            dismissLabel = "Quay lại",
            onConfirm = {
                confirmReplacement = false
                onRecover(true)
            },
            onDismiss = { confirmReplacement = false },
        )
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
    val today = LocalDate.now()
    val due = dueSummary(detail.dueDate, today)
    val isScheduleOverdue = workOrderScheduleWindow(detail.scheduledDate, today).isOverdueToComplete
    val dueColor =
        when (dueTone(due, isScheduleOverdue)) {
            DueTone.ALERT -> LuxTheme.colors.dueOverdue
            DueTone.WARNING -> LuxTheme.colors.dueToday
            DueTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val scheduleValue = detail.scheduledDate?.let { DateFormatUtils.formatPlainDate(it) } ?: "Chưa có lịch"
    LuxCard(modifier = Modifier.fillMaxWidth().testTag("work_order_summary")) {
        Text(
            text = detail.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryRow(label = "Hạn xử lý") {
                SummaryValue(text = due.dateText)
                due.statusText?.let { SummaryAlertLine(text = it, color = dueColor) }
            }
            SummaryRow(label = workOrderScheduleLabel(kind)) {
                SummaryValue(text = scheduleValue)
                if (isScheduleOverdue) {
                    SummaryAlertLine(text = "Đã qua lịch", color = LuxTheme.colors.dueOverdue)
                }
            }
            if (status != WorkOrderStatus.CANCELLED) {
                SummaryRow(label = "Giai đoạn hiện tại") { SummaryValue(text = kind.label()) }
            }
            SummaryRow(label = "Mã lệnh") {
                Text(
                    text = detail.workOrderId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryRow(
    label: String,
    value: @Composable ColumnScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.md),
        )
        Column(horizontalAlignment = Alignment.End, content = value)
    }
}

@Composable
private fun SummaryAlertLine(
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(
            imageVector = Icons.Filled.Schedule,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun SummaryValue(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
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
                PreStartItem(Icons.Filled.PhotoCamera, "Ảnh hiện trường", "Thêm ít nhất 1 ảnh", true),
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
                text = "Theo cấu hình của Kỹ sư bảo trì. Nhập sau khi bắt đầu.",
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
        onRemoveEvidence = {},
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
        onRemoveEvidence = {},
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
        onRemoveEvidence = {},
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
        onRemoveEvidence = {},
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
        onRemoveEvidence = {},
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
        onRemoveEvidence = {},
    )
}

@Preview(name = "IoT - Inspection", showBackground = true, heightDp = 1000)
@Preview(name = "IoT - Inspection large text", showBackground = true, fontScale = 2f, widthDp = 360)
@Preview(name = "IoT - Inspection night", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkOrderDetailScreenIotInspectionPreview() {
    com.luxmap.core.theme.LuxMapTheme {
        WorkOrderDetailScreen(
            uiState = WorkOrderDetailUiState.Success(detail = sampleIotWorkOrder("inspection")),
            onBack = {},
            onStart = {},
            onNavigateFault = { _, _ -> },
            onReportNoteChanged = {},
            onMaterialsUsedChanged = {},
            onFaultOutcomeSelected = { _, _ -> },
            onSubmit = {},
            onCaptureEvidence = {},
            onRemoveEvidence = {},
        )
    }
}

@Preview(name = "IoT - Repair", showBackground = true, heightDp = 1000)
@Preview(name = "IoT - Repair large text", showBackground = true, fontScale = 2f, widthDp = 360)
@Preview(name = "IoT - Repair night", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkOrderDetailScreenIotRepairPreview() {
    com.luxmap.core.theme.LuxMapTheme {
        WorkOrderDetailScreen(
            uiState = WorkOrderDetailUiState.Success(detail = sampleIotWorkOrder("repair")),
            onBack = {},
            onStart = {},
            onNavigateFault = { _, _ -> },
            onReportNoteChanged = {},
            onMaterialsUsedChanged = {},
            onFaultOutcomeSelected = { _, _ -> },
            onSubmit = {},
            onCaptureEvidence = {},
            onRemoveEvidence = {},
        )
    }
}

private fun sampleIotWorkOrder(kind: String): WorkOrderDetail =
    sampleRepairWithFaults().let { detail ->
        detail.copy(
            taskKind = kind,
            woStatus = "in_progress",
            allowedActions = listOf("complete"),
            faults =
                listOf(
                    detail.faults.single().copy(
                        poleId = null,
                        faultType = "voltage_anomaly",
                        cabinetId = "CAB-001",
                        originNodeId = "NODE-004-LONG-DEVICE-IDENTIFIER",
                        feederId = "FDR-001",
                        iotAnomalyCode = "voltage_loss",
                    ),
                    detail.faults.single().copy(
                        faultId = "FAULT-2",
                        poleId = null,
                        faultType = "iot_hardware_fault",
                        originNodeId = "NODE-005",
                    ),
                ),
        )
    }
