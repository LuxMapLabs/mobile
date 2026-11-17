package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import kotlinx.coroutines.launch

@Composable
fun PoleListRoute(
    workOrderId: String,
    onCapturePole: (poleId: String, source: PolePhotoCaptureSource) -> Unit,
    onCaptureVideo: (poleId: String, source: PoleVideoCaptureSource) -> Unit,
    onViewAiResult: (serverPhotoId: String) -> Unit,
    onDone: () -> Unit,
    viewModel: PoleListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Completing the survey otherwise gives no visible feedback at all (found on a real device,
    // 2026-10-29 - see CLAUDE.md). TopSlideToast (not a Material Snackbar) matches the Khao Sat
    // v4 mock's own toast, which slides down from above the content instead of appearing at the
    // bottom where it would be easy to miss.
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        viewModel.surveyCompleted.collect { pendingReviews ->
            val message =
                if (pendingReviews > 0) {
                    "Đã hoàn tất $workOrderId · $pendingReviews review đang gửi lên Kỹ sư bảo trì"
                } else {
                    "Đã hoàn tất $workOrderId · Kết quả đã gửi Kỹ sư bảo trì"
                }
            scope.launch { toastState.show(message) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PoleListScreen(
            workOrderId = workOrderId,
            state = uiState,
            onCapturePole = onCapturePole,
            onCaptureVideo = onCaptureVideo,
            // Pure navigation - this used to also silently call onComplete() (found on a real
            // device, 2026-10-29), so tapping the back arrow after capturing just 1 photo would
            // mark the whole survey complete even if the user only meant to leave.
            onBack = onDone,
            onComplete = viewModel::onComplete,
            onRetry = viewModel::onRetry,
            onViewAiResult = onViewAiResult,
        )
        TopSlideToast(
            state = toastState,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }
}

@Composable
fun PoleListScreen(
    workOrderId: String,
    state: PoleListUiState,
    onCapturePole: (poleId: String, source: PolePhotoCaptureSource) -> Unit,
    onCaptureVideo: (poleId: String, source: PoleVideoCaptureSource) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
    onRetry: () -> Unit = {},
    onViewAiResult: (serverPhotoId: String) -> Unit = {},
) {
    // Which pole's "Bao loi" method chooser (Chup anh / Quay video) is open, if any - matches
    // the design mock's sheet trigger, but only offers the choice itself here.
    // Khao Sat v4 mock's "Hoan tat khao sat tuyen?" confirm sheet - added 2026-10-29, this used
    // to call onComplete() and navigate away in the same tap, no confirmation and no way to tell
    // afterward that it worked (see CLAUDE.md).
    var showCompleteConfirm by rememberSaveable { mutableStateOf(false) }
    var reportMethodSheetPoleId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { LuxChildTopBar(title = "Chụp ảnh cột đèn", onBack = onBack, subtitle = workOrderId) },
        bottomBar = {
            if (state is PoleListUiState.Success) {
                PoleListBottomBar(
                    left = state.poles.count { it.syncStatus == null },
                    isCompleted = state.isCompleted,
                    onTapComplete = { showCompleteConfirm = true },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    is PoleListUiState.Loading -> PoleListLoadingSkeleton()

                    is PoleListUiState.Success -> {
                        val nextIndex = state.poles.indexOfFirst { it.syncStatus == null }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            if (state.isStale) {
                                item {
                                    LuxBanner(
                                        message =
                                            "Đang xem danh sách cột đã lưu trên máy, " +
                                                "có thể chưa phải bản mới nhất",
                                        tone = LuxBannerTone.INFO,
                                    )
                                }
                            }
                            item { LuxSectionHeader(title = "Cột trên tuyến · ${state.poles.size}") }
                            item {
                                LuxCard(contentPadding = PaddingValues(0.dp)) {
                                    Column {
                                        state.poles.forEachIndexed { index, pole ->
                                            if (index > 0) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            }
                                            val action = poleRowAction(pole, state.isCompleted)
                                            PoleRow(
                                                pole = pole,
                                                isNext = index == nextIndex,
                                                action = action,
                                                onClick = {
                                                    when (action) {
                                                        is PoleRowAction.OpenAiResult ->
                                                            onViewAiResult(
                                                                action.serverPhotoId,
                                                            )
                                                        PoleRowAction.OpenReportSheet ->
                                                            reportMethodSheetPoleId = pole.poleId
                                                        PoleRowAction.None -> Unit
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is PoleListUiState.Empty ->
                        MessageState(
                            icon = Icons.Filled.EventAvailable,
                            iconColors = SyncStatus.DONE.badgeColors(),
                            title = "Tuyến này chưa có cột đèn nào",
                            subtitle = "Liên hệ Kỹ sư bảo trì nếu bạn cho rằng đây là nhầm lẫn.",
                            action = {},
                        )

                    is PoleListUiState.Error ->
                        MessageState(
                            icon = Icons.Filled.ErrorOutline,
                            iconColors = SyncStatus.FAILED.badgeColors(),
                            title = "Không tải được danh sách cột",
                            subtitle = state.message,
                            action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                        )
                }
            }
        }
    }

    if (showCompleteConfirm && state is PoleListUiState.Success) {
        // A captured pole IS a reported issue in this v1 flow (there is no separate "mark
        // normal" action - matches the mock's own info line "chi bao cot co loi, con lai tinh
        // la binh thuong" and PoleListBottomBar's own "left" count below).
        val issueCount = state.poles.count { it.syncStatus != null }
        CompleteSurveyConfirmSheet(
            issueCount = issueCount,
            normalCount = state.poles.size - issueCount,
            onDismiss = { showCompleteConfirm = false },
            onConfirm = {
                showCompleteConfirm = false
                onComplete()
            },
        )
    }

    val methodSheetPoleId = reportMethodSheetPoleId
    if (methodSheetPoleId != null) {
        ReportMethodSheet(
            poleId = methodSheetPoleId,
            onDismiss = { reportMethodSheetPoleId = null },
            onChoosePhoto = {
                reportMethodSheetPoleId = null
                onCapturePole(methodSheetPoleId, PolePhotoCaptureSource.CAMERA)
            },
            onChooseGallery = {
                reportMethodSheetPoleId = null
                onCapturePole(methodSheetPoleId, PolePhotoCaptureSource.GALLERY)
            },
            onChooseVideo = {
                reportMethodSheetPoleId = null
                onCaptureVideo(methodSheetPoleId, PoleVideoCaptureSource.CAMERA)
            },
            onChooseVideoGallery = {
                reportMethodSheetPoleId = null
                onCaptureVideo(methodSheetPoleId, PoleVideoCaptureSource.GALLERY)
            },
        )
    }
}

// Mock's "Bao loi cot" sheet, trimmed to just the method choice - the mock's multi-file picker
// (up to 5 photos/videos staged before one combined submit) does not match how upload actually
// works today, where POST .../pole-photos and POST .../pole-videos each take exactly 1 file per
// call (see contract-drift.md).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportMethodSheet(
    poleId: String,
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onChooseVideo: () -> Unit,
    onChooseVideoGallery: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Báo lỗi cột",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = poleId, style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReportMethodOption(
                        icon = Icons.Filled.PhotoCamera,
                        label = "Chụp ảnh",
                        enabled = true,
                        onClick = onChoosePhoto,
                        modifier = Modifier.weight(1f),
                    )
                    ReportMethodOption(
                        icon = Icons.Filled.PhotoLibrary,
                        label = "Chọn từ thư viện",
                        enabled = true,
                        onClick = onChooseGallery,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReportMethodOption(
                        icon = Icons.Filled.Videocam,
                        label = "Quay video",
                        enabled = true,
                        onClick = onChooseVideo,
                        modifier = Modifier.weight(1f),
                    )
                    ReportMethodOption(
                        icon = Icons.Filled.VideoLibrary,
                        label = "Video từ thư viện",
                        enabled = true,
                        onClick = onChooseVideoGallery,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportMethodOption(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            modifier
                // Fixed, not just a minimum - "Chọn từ thư viện" wraps to 2 lines at this column
                // width while the other 2 labels fit on 1, so a min-height let the 3 options sit
                // at different heights in the same row (found on a real device, 2026-11-10).
                .height(96.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .border(
                    1.dp,
                    if (enabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(Radius.control),
                )
                .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor)
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (!enabled) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// "Còn lại N cột chưa chụp" + an outlined "Hoàn tất" CTA, matching the design doc's horizontal
// bottom bar - not the shared BottomActionBar component (that one stacks a centered hint above a
// full-width filled button, a different layout other screens like F03 still rely on; bespoke here
// instead of changing that shared component for every caller).
@Composable
private fun PoleListBottomBar(
    left: Int,
    isCompleted: Boolean,
    onTapComplete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.md, bottom = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Còn lại", style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "$left cột chưa chụp", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = onTapComplete,
                enabled = !isCompleted,
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                shape = RoundedCornerShape(Radius.control),
                // colorScheme.primary, not a fixed Navy700 - this screen has no theme override
                // (unlike SurveyHubScreen), so primary already flips Navy700/Green400 with the
                // theme and stays readable against this bar's own white/dark surfaceColor.
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(text = if (isCompleted) "Đã hoàn tất" else "Hoàn tất")
            }
        }
    }
}

// Khao Sat v4 mock's "Hoan tat khao sat tuyen?" confirm sheet.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteSurveyConfirmSheet(
    issueCount: Int,
    normalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = "Hoàn tất khảo sát tuyến?", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "$issueCount cột báo lỗi, $normalCount cột còn lại được tính là bình thường.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
                    contentPadding = PaddingValues(horizontal = Spacing.sm),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Kiểm tra lại", maxLines = 1)
                }
                PrimaryButton(
                    text = "Hoàn tất",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.4f).testTag("confirm_survey_complete"),
                )
            }
        }
    }
}

// Shared by the poles card and the loading skeleton's 2 placeholder blocks below - pulled out
// because the chain is too long to fit on one line once nested inside a LazyColumn item.
@Composable
private fun Modifier.cardOutline(): Modifier =
    this
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.block))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radius.block))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoleRow(
    pole: PoleListRowState,
    isNext: Boolean,
    action: PoleRowAction,
    onClick: () -> Unit,
) {
    val condition =
        when (pole.fixtureStatus) {
            "normal" -> AssetCondition.NORMAL
            "dim" -> AssetCondition.DIM
            "out" -> AssetCondition.OUT
            else -> AssetCondition.UNKNOWN
        }
    val click =
        when (action) {
            is PoleRowAction.OpenAiResult ->
                Modifier.clickable(onClickLabel = "Xem kết quả AI", role = Role.Button, onClick = onClick)
            PoleRowAction.OpenReportSheet -> Modifier.clickable(onClickLabel = "Chụp ảnh cột", onClick = onClick)
            PoleRowAction.None -> Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .then(click)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PoleNumberAvatar(position = pole.position, isNext = isNext, hasSync = pole.syncStatus != null)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = pole.poleId,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                StatusBadge(text = condition.label(), colors = condition.badgeColors(), icon = condition.rowIcon())
                poleProgressBadge(pole)?.let { progress ->
                    val (status, label) = progress.badge()
                    StatusBadge(text = label, colors = status.badgeColors(), icon = status.icon())
                }
            }
        }
        when {
            action is PoleRowAction.OpenAiResult ->
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pole_chevron_${pole.poleId}"),
                )
            action is PoleRowAction.OpenReportSheet && pole.syncStatus == null ->
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

private fun AssetCondition.rowIcon(): ImageVector =
    when (this) {
        AssetCondition.NORMAL -> Icons.Filled.CheckCircle
        AssetCondition.DIM -> Icons.Filled.Warning
        AssetCondition.OUT -> Icons.Filled.Error
        AssetCondition.UNKNOWN -> Icons.Filled.Help
    }

private fun PoleProgress.badge(): Pair<SyncStatus, String> =
    when (this) {
        PoleProgress.SYNC_FAILED -> SyncStatus.FAILED to "Đồng bộ lỗi"
        PoleProgress.SYNC_CONFLICT -> SyncStatus.CONFLICT to "Xung đột"
        PoleProgress.SYNCING -> SyncStatus.SYNCING to SyncStatus.SYNCING.label()
        PoleProgress.QUEUED_OFFLINE -> SyncStatus.QUEUED_OFFLINE to SyncStatus.QUEUED_OFFLINE.label()
        PoleProgress.QUEUED_ONLINE -> SyncStatus.QUEUED_ONLINE to SyncStatus.QUEUED_ONLINE.label()
        PoleProgress.REVIEW_SAVED -> SyncStatus.QUEUED_ONLINE to "Đã xác nhận"
        PoleProgress.AWAITING_DECISION -> SyncStatus.QUEUED_ONLINE to "Chờ duyệt"
        PoleProgress.UPLOADED -> SyncStatus.DONE to "Đã tải lên"
    }

@Composable
private fun PoleNumberAvatar(
    position: Int?,
    isNext: Boolean,
    hasSync: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val (background, textColor) =
        when {
            isNext -> colors.primary to colors.onPrimary
            hasSync -> colors.primaryContainer to colors.onPrimaryContainer
            else -> colors.surfaceContainerHigh to colors.onSurfaceVariant
        }
    Box(
        modifier = Modifier.size(40.dp).background(background, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = position?.toString() ?: "–", style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}

// Shaped like the real content below it (a blank summary-block placeholder + a bordered card of
// pole-row-shaped skeletons) instead of a spinner, matching the design doc's loading mock and the
// same pattern SurveyHubScreen/HomeScreen already use for their own loading states.
@Composable
private fun PoleListLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(132.dp).cardOutline())
        Column(
            modifier = Modifier.fillMaxWidth().cardOutline(),
        ) {
            repeat(4) { index ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                PoleRowSkeleton()
            }
        }
    }
}

@Composable
private fun PoleRowSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBar(widthFraction = 0.5f, height = 12.dp, color = MaterialTheme.colorScheme.outlineVariant)
            SkeletonBar(widthFraction = 0.3f, height = 10.dp, color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float,
    height: Dp,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(widthFraction)
                .height(height)
                .background(color, RoundedCornerShape(Dimens.radiusSmall)),
    )
}
