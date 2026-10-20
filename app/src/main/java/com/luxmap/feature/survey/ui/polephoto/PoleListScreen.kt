package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge

@Composable
fun PoleListRoute(
    workOrderId: String,
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    viewModel: PoleListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val aiResultState by viewModel.aiResultState.collectAsState()
    // Remembered so the bottom sheet's "Thử lại" button can re-fetch the same photo on error -
    // the sheet itself only knows the current PolePhotoResultUiState, not which photo it's for.
    var lastViewedServerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    // Remembered the same way, for the sheet's header (pole number + id) and "Chụp lại" button -
    // PolePhotoResultUiState stays free of pole identity on purpose (it only describes the AI
    // result itself), so the Route looks the pole up from the already-loaded list instead.
    var lastViewedPoleId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastViewedPosition by rememberSaveable { mutableStateOf<Int?>(null) }

    PoleListScreen(
        workOrderId = workOrderId,
        state = uiState,
        aiResultState = aiResultState,
        aiResultPoleId = lastViewedPoleId.orEmpty(),
        aiResultPosition = lastViewedPosition ?: 0,
        onCapturePole = onCapturePole,
        onDone = onDone,
        onRetry = viewModel::onRetry,
        onViewAiResult = { serverPhotoId ->
            val pole = (uiState as? PoleListUiState.Success)?.poles?.firstOrNull { it.serverPhotoId == serverPhotoId }
            lastViewedServerPhotoId = serverPhotoId
            lastViewedPoleId = pole?.poleId
            lastViewedPosition = pole?.position
            viewModel.onViewAiResult(serverPhotoId)
        },
        onDismissAiResult = viewModel::onDismissAiResult,
        onRetryAiResult = { lastViewedServerPhotoId?.let(viewModel::onViewAiResult) },
        onRetake = {
            viewModel.onDismissAiResult()
            lastViewedPoleId?.let(onCapturePole)
        },
        onSubmitReview = { finalCvState, reviewNote ->
            lastViewedServerPhotoId?.let { viewModel.onSubmitReview(it, finalCvState, reviewNote) }
        },
    )
}

@Composable
fun PoleListScreen(
    workOrderId: String,
    state: PoleListUiState,
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit = {},
    aiResultState: PolePhotoResultUiState? = null,
    aiResultPoleId: String = "",
    aiResultPosition: Int = 0,
    onViewAiResult: (serverPhotoId: String) -> Unit = {},
    onDismissAiResult: () -> Unit = {},
    onRetryAiResult: () -> Unit = {},
    onRetake: () -> Unit = {},
    onSubmitReview: (finalCvState: String, reviewNote: String?) -> Unit = { _, _ -> },
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PoleListHeader(workOrderId = workOrderId, onBack = onDone)
        // Overlaps the hero's bottom edge, same pattern as SurveyHubScreen's own sheet - reads as
        // a sheet floating over the navy hero instead of a hard seam between the two. The clip
        // (not just background) matters once the list scrolls - background alone paints the
        // rounded shape but never cuts the LazyColumn's own square corners, so a scrolled row's
        // edge shows past the rounding at the top instead of following it.
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .offset(y = -Spacing.xl)
                    .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    is PoleListUiState.Loading -> PoleListLoadingSkeleton()

                    is PoleListUiState.Success -> {
                        val nextIndex = state.poles.indexOfFirst { it.syncStatus == null }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            if (state.isStale) {
                                item {
                                    InlineBanner(
                                        message =
                                            "Đang xem danh sách cột đã lưu trên máy, " +
                                                "có thể chưa phải bản mới nhất",
                                        tone = InlineBannerTone.OFFLINE,
                                    )
                                }
                            }
                            item { PoleListSectionHeader(count = state.poles.size) }
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth().cardOutline(),
                                ) {
                                    state.poles.forEachIndexed { index, pole ->
                                        if (index > 0) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                                        }
                                        PoleRow(
                                            pole = pole,
                                            isNext = index == nextIndex,
                                            onClick = { onCapturePole(pole.poleId) },
                                            onViewAiResult = { pole.serverPhotoId?.let(onViewAiResult) },
                                        )
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
        if (state is PoleListUiState.Success) {
            PoleListBottomBar(left = state.poles.count { it.syncStatus == null }, onDone = onDone)
        }
    }

    if (aiResultState != null) {
        PolePhotoResultBottomSheet(
            poleId = aiResultPoleId,
            position = aiResultPosition,
            state = aiResultState,
            onDismiss = onDismissAiResult,
            onRetry = onRetryAiResult,
            onRetake = onRetake,
            onSubmitReview = onSubmitReview,
        )
    }
}

// Fixed Navy-gradient hero, same BrandHeroGradient as SurveyHubScreen - this screen is a
// drill-down from the Survey Hub's "Chụp ảnh cột đèn" button, not a tab root, but the design doc
// (mã 4b) still gives it the same hero treatment rather than the flat AppTopBar other drill-down
// screens (e.g. AiConfirmationScreen) use.
@Composable
private fun PoleListHeader(
    workOrderId: String,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(onClick = onBack)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(Radius.control)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }
            Column {
                Text(text = workOrderId, style = LabelTextStyle, color = Color.White.copy(alpha = 0.75f))
                Text(text = "Chụp ảnh cột đèn", style = HeadlineTextStyle, color = Color.White)
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
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                onClick = onDone,
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
                Text(text = "Hoàn tất")
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
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.block))

@Composable
private fun PoleListSectionHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Tất cả cột trên tuyến", style = MaterialTheme.typography.titleMedium)
        StatusBadge(text = "$count cột", colors = WorkOrderStatus.ASSIGNED.badgeColors())
    }
}

@Composable
private fun PoleRow(
    pole: PoleListRowState,
    isNext: Boolean,
    onClick: () -> Unit,
    onViewAiResult: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val condition =
        when (pole.fixtureStatus) {
            "normal" -> AssetCondition.NORMAL
            "dim" -> AssetCondition.DIM
            "out" -> AssetCondition.OUT
            else -> AssetCondition.UNKNOWN
        }
    val hasAi = pole.syncStatus == SyncStatus.DONE && pole.serverPhotoId != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(Spacing.md),
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
            // Condition + sync badges share one row here instead of the sync badge sitting in
            // the row's trailing slot - with the AI-result icon button also in that slot, 2
            // variable-width badges plus a button no longer fit on screen width without wrapping.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatusBadge(text = condition.label(), colors = condition.badgeColors(isDark))
                if (pole.syncStatus != null) {
                    StatusBadge(
                        text = polePhotoSyncLabel(pole.syncStatus),
                        colors = pole.syncStatus.badgeColors(),
                        icon = pole.syncStatus.icon(),
                    )
                }
            }
        }
        // Trailing slot now holds exactly one action affordance - the AI-result icon when
        // available, otherwise the not-yet-captured camera icon - not a status badge too.
        if (hasAi) {
            IconButton(onClick = onViewAiResult) {
                Icon(
                    imageVector = Icons.Filled.SmartToy,
                    contentDescription = "Xem kết quả AI",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (pole.syncStatus == null) {
            Box(
                modifier =
                    Modifier
                        .size(Dimens.minTouchTarget)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.control)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Chụp ảnh",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// "Đã tải lên" reads more naturally than the shared SyncStatus.DONE wording ("Đã đồng bộ") for a
// single uploaded pole photo - a local override for this row only, not a change to the shared
// sync badge table in core/theme/Color.kt (that wording still fits other screens using it).
private fun polePhotoSyncLabel(status: SyncStatus): String =
    if (status == SyncStatus.DONE) "Đã tải lên" else status.label()

// isNext (navy, matches SurveyHubScreen's own fixed-navy accent) takes priority over hasSync
// (a neutral "already handled" chip reusing WorkOrderStatus.ASSIGNED's pair) over the plain
// "not reached yet" surfaceVariant default.
@Composable
private fun PoleNumberAvatar(
    position: Int,
    isNext: Boolean,
    hasSync: Boolean,
) {
    val assigned = WorkOrderStatus.ASSIGNED.badgeColors()
    val (background, textColor) =
        when {
            isNext -> Navy700 to Color.White
            hasSync -> assigned.background to assigned.text
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier = Modifier.size(36.dp).background(background, RoundedCornerShape(Radius.chip)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = position.toString(), style = MaterialTheme.typography.labelLarge, color = textColor)
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
            SkeletonBar(widthFraction = 0.5f, height = 12.dp, color = MaterialTheme.colorScheme.outline)
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
