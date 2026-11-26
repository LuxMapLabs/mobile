package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxConfirmDialog
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxSnackbarHost
import com.luxmap.core.ui.components.PoleIdentificationDetails
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.ui.polephoto.lux.formatLux

@Composable
fun PoleListRoute(
    workOrderId: String,
    onCapturePole: (poleId: String, source: PolePhotoCaptureSource) -> Unit,
    onCaptureVideo: (poleId: String, source: PoleVideoCaptureSource) -> Unit,
    onViewAiResult: (serverPhotoId: String) -> Unit,
    onDone: () -> Unit,
    onOpenMap: (String, Double?, Double?) -> Unit = { _, _, _ -> },
    onSurveyCompleted: () -> Unit = onDone,
    viewModel: PoleListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.surveyCompleted.collect { onSurveyCompleted() }
    }

    PoleListScreen(
        snackbarHostState = snackbarHostState,
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
        onOpenMap = onOpenMap,
        onViewAiResult = onViewAiResult,
    )
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
    onOpenMap: (String, Double?, Double?) -> Unit = { _, _, _ -> },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    // Which pole's "Bao loi" method chooser (Chup anh / Quay video) is open, if any - matches
    // the design mock's sheet trigger, but only offers the choice itself here.
    // Khao Sat v4 mock's "Hoan tat khao sat tuyen?" confirm sheet - added 2026-10-29, this used
    // to call onComplete() and navigate away in the same tap, no confirmation and no way to tell
    // afterward that it worked (see CLAUDE.md).
    var showCompleteConfirm by rememberSaveable { mutableStateOf(false) }
    var reportMethodSheetPoleId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        snackbarHost = { LuxSnackbarHost(snackbarHostState) },
        topBar = { LuxChildTopBar(title = "Chụp ảnh cột đèn", onBack = onBack, subtitle = workOrderId) },
        bottomBar = {
            if (state is PoleListUiState.Success) {
                PoleListBottomBar(
                    left = state.unconfirmedCount,
                    canComplete = state.canComplete,
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
                    is PoleListUiState.Loading ->
                        LuxLoadingRegion(
                            description = "Đang tải danh sách cột",
                            modifier = Modifier.padding(Spacing.lg),
                        ) {
                            LuxSkeleton(Modifier.fillMaxWidth().height(24.dp), MaterialTheme.shapes.small)
                            repeat(5) {
                                LuxSkeleton(Modifier.fillMaxWidth().height(64.dp), MaterialTheme.shapes.medium)
                            }
                        }

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
                            item { LuxSectionHeader(title = "Cột trên tuyến (${state.poles.size})") }
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
                                                onOpenMap = onOpenMap,
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
                        LuxEmptyState(
                            title = "Tuyến này chưa có cột đèn nào",
                            message = "Liên hệ Quản lý nếu bạn cho rằng đây là nhầm lẫn.",
                        )

                    is PoleListUiState.Error ->
                        LuxErrorState(
                            onRetry = onRetry,
                            kind = if (state.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                            title = "Không tải được danh sách cột",
                            message =
                                if (state.isNetworkError) {
                                    "Kiểm tra kết nối rồi thử lại."
                                } else {
                                    "Vui lòng thử lại sau ít phút."
                                },
                        )
                }
            }
        }
    }

    if (showCompleteConfirm && state is PoleListUiState.Success && state.canComplete) {
        LuxConfirmDialog(
            title = "Hoàn tất khảo sát tuyến?",
            message = "Đã xác nhận kết quả của ${state.confirmedCount} cột. Gửi khảo sát để duyệt?",
            confirmLabel = "Hoàn tất",
            dismissLabel = "Kiểm tra lại",
            confirmModifier = Modifier.testTag("confirm_survey_complete"),
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
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(text = "Báo lỗi cột", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = poleId,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ReportMethodOption(icon = Icons.Rounded.PhotoCamera, label = "Chụp ảnh", onClick = onChoosePhoto)
            ReportMethodOption(icon = Icons.Rounded.PhotoLibrary, label = "Chọn từ thư viện", onClick = onChooseGallery)
            ReportMethodOption(icon = Icons.Rounded.Videocam, label = "Quay video", onClick = onChooseVideo)
            ReportMethodOption(
                icon = Icons.Rounded.VideoLibrary,
                label = "Video từ thư viện",
                onClick = onChooseVideoGallery,
            )
        }
    }
}

@Composable
private fun ReportMethodOption(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PoleListBottomBar(
    left: Int,
    canComplete: Boolean,
    isCompleted: Boolean,
    onTapComplete: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Còn lại",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = "$left cột có kết quả chưa xác nhận", style = MaterialTheme.typography.titleMedium)
                }
                PrimaryButton(
                    text = if (isCompleted) "Đã hoàn tất" else "Hoàn tất",
                    onClick = onTapComplete,
                    enabled = canComplete,
                    branded = true,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PoleRow(
    pole: PoleListRowState,
    isNext: Boolean,
    action: PoleRowAction,
    onClick: () -> Unit,
    onOpenMap: (String, Double?, Double?) -> Unit,
) {
    val click =
        when (action) {
            is PoleRowAction.OpenAiResult ->
                Modifier.clickable(onClickLabel = "Xem kết quả AI", role = Role.Button, onClick = onClick)
            PoleRowAction.OpenReportSheet ->
                Modifier.clickable(
                    onClickLabel = if (pole.syncStatus == null) "Chụp ảnh cột" else "Chụp lại cột",
                    role = Role.Button,
                    onClick = onClick,
                )
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
            pole.identification?.let { item ->
                PoleIdentificationDetails(
                    poleId = item.poleId,
                    position = item.position,
                    segmentId = item.segmentId,
                    note = item.note,
                    fixtureType = item.fixtureType,
                    lampWatt = item.lampWatt,
                    lampCount = item.lampCount,
                    fixtureCount = item.fixtureCount,
                    nearSensitivePoi = item.nearSensitivePoi,
                    lat = item.lat,
                    lng = item.lng,
                    onOpenMap = { onOpenMap(item.poleId, item.lat, item.lng) },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                pole.aiVerdict?.let { verdict ->
                    val (condition, label, icon) = verdict.badge()
                    StatusBadge(text = label, colors = condition.badgeColors(), icon = icon)
                }
                poleProgressBadge(pole)?.let { progress ->
                    val (colors, label, icon) = progress.badge()
                    StatusBadge(text = label, colors = colors, icon = icon)
                }
                pole.lux?.let { lux ->
                    val value = "${formatLux(lux.luxValue)} lux"
                    StatusBadge(
                        text = if (lux.isSyncFailed) "$value · chưa gửi được" else value,
                        colors = if (lux.isSyncFailed) SyncStatus.FAILED.badgeColors() else LuxTheme.status.kind,
                        icon = Icons.Rounded.WbSunny,
                        modifier = Modifier.testTag("pole_lux_${pole.poleId}"),
                    )
                }
            }
        }
        when {
            action is PoleRowAction.OpenAiResult ->
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pole_chevron_${pole.poleId}"),
                )
            action is PoleRowAction.OpenReportSheet && pole.syncStatus == null ->
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            action is PoleRowAction.OpenReportSheet ->
                Icon(
                    imageVector = Icons.Rounded.Replay,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("pole_retake_${pole.poleId}"),
                )
        }
    }
}

private fun PoleAiVerdict.badge(): Triple<AssetCondition, String, ImageVector> =
    when (this) {
        PoleAiVerdict.ON -> Triple(AssetCondition.NORMAL, "Đèn sáng", Icons.Rounded.Lightbulb)
        PoleAiVerdict.OFF -> Triple(AssetCondition.OUT, "Đèn tắt", Icons.Outlined.Lightbulb)
        PoleAiVerdict.INCONCLUSIVE -> Triple(AssetCondition.UNKNOWN, "Không xác định", Icons.Rounded.Help)
    }

@Composable
private fun PoleProgress.badge(): Triple<BadgeColors, String, ImageVector> {
    @Composable
    fun sync(
        status: SyncStatus,
        label: String = status.label(),
    ) = Triple(status.badgeColors(), label, status.icon())
    return when (this) {
        PoleProgress.SYNC_FAILED -> sync(SyncStatus.FAILED, "Đồng bộ lỗi")
        PoleProgress.SYNC_CONFLICT -> sync(SyncStatus.CONFLICT, "Xung đột")
        PoleProgress.SYNCING -> sync(SyncStatus.SYNCING)
        PoleProgress.QUEUED_OFFLINE -> sync(SyncStatus.QUEUED_OFFLINE)
        PoleProgress.QUEUED_ONLINE -> sync(SyncStatus.QUEUED_ONLINE)
        PoleProgress.REVIEW_SAVED -> Triple(LuxTheme.status.kind, "Đã xác nhận", Icons.Rounded.EditNote)
        PoleProgress.AWAITING_DECISION -> Triple(LuxTheme.status.kind, "Chờ duyệt", Icons.Rounded.HourglassEmpty)
        PoleProgress.UPLOADED -> sync(SyncStatus.DONE, "Đã tải lên")
    }
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
