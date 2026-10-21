package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.Green400
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.MetricTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.ui.components.DueTodayColor
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SurveyHubRoute(
    onCheckReadiness: (workOrderId: String) -> Unit,
    onCapturePolePhotos: (workOrderId: String) -> Unit = {},
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SurveyHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // Not in CLAUDE.md's forced-dark table (only F03/F04 are) - this hub screen follows the
    // system theme, same as F02 Việc hôm nay, so its Navy700 cards pop against a light page
    // instead of blending into an all-dark one.
    LuxMapTheme {
        // Dark mode's accent token (colorScheme.primary) is Green400, not Navy700 - this screen
        // wants the same navy accent F02 Việc hôm nay uses, so override just primary/onPrimary
        // for this subtree instead of changing the app-wide dark palette. Harmless in Light mode
        // (primary is already Navy700 there), only matters if the device itself is in dark mode.
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(primary = Navy700, onPrimary = Color.White),
            typography = MaterialTheme.typography,
        ) {
            SurveyHubScreen(
                state = uiState,
                onCheckReadiness = onCheckReadiness,
                onCapturePolePhotos = onCapturePolePhotos,
                modifier = modifier,
                onRetry = viewModel::onRetry,
                onStartRoute = viewModel::onStartRoute,
                onReviewAiConfirmation = onReviewAiConfirmation,
            )
        }
    }
}

@Composable
fun SurveyHubScreen(
    state: SurveyHubUiState,
    onCheckReadiness: (workOrderId: String) -> Unit,
    onCapturePolePhotos: (workOrderId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onStartRoute: (workOrderId: String) -> Unit = {},
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    onResurvey: (surveySweepId: String) -> Unit = {},
) {
    val metrics = hubMetricsOf(state)
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SurveyHubHeader(metrics = metrics)
        // Overlaps the hero's bottom edge by Spacing.xl, same as HomeScreen's HomeHeader overlap -
        // reads as a sheet floating over the navy hero instead of a hard seam between the two.
        // The clip (not just background) matters once the list scrolls - background alone paints
        // the rounded shape but never cuts the LazyColumn's own square corners, so a scrolled
        // row's edge shows past the rounding at the top instead of following it.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset(y = -Spacing.xl)
                    .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                    .background(MaterialTheme.colorScheme.background),
        ) {
            when (state) {
                is SurveyHubUiState.Loading -> HubLoadingSkeleton()

                is SurveyHubUiState.Success ->
                    SurveyHubContent(
                        state = state,
                        onCheckReadiness = onCheckReadiness,
                        onCapturePolePhotos = onCapturePolePhotos,
                        onStartRoute = onStartRoute,
                        onReviewAiConfirmation = onReviewAiConfirmation,
                        onResurvey = onResurvey,
                    )

                is SurveyHubUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì",
                        subtitle = "Khi có tuyến mới được giao, danh sách sẽ hiển thị tại đây.",
                        action = {},
                    )

                is SurveyHubUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = state.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                        ) {
                            Text("Thử lại")
                        }
                    }
            }
        }
    }
}

// Shaped like the real content below it (a thin section-label bar + 3 route-card-shaped rows)
// instead of a spinner, so the loading state doesn't jump/resize once real data replaces it -
// same pattern as HomeScreen's LoadingSkeletonState.
@Composable
private fun HubLoadingSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBar(width = 120.dp, height = 14.dp, color = MaterialTheme.colorScheme.outline)
        repeat(3) { RouteCardSkeleton() }
    }
}

@Composable
private fun RouteCardSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
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
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.chip)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBar(widthFraction = 0.4f, height = 12.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                SkeletonBar(widthFraction = 0.6f, height = 14.dp, color = MaterialTheme.colorScheme.outline)
            }
        }
        SkeletonBar(
            widthFraction = 1f,
            height = Dimens.minTouchTarget,
            color = WorkOrderStatus.ASSIGNED.badgeColors().background,
            shape = RoundedCornerShape(Radius.control),
        )
    }
}

@Composable
private fun SkeletonBar(
    height: Dp,
    color: Color,
    width: Dp? = null,
    widthFraction: Float? = null,
    shape: RoundedCornerShape = RoundedCornerShape(Dimens.radiusSmall),
) {
    val widthModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth(widthFraction ?: 1f)
    Box(modifier = widthModifier.height(height).background(color, shape))
}

private data class HubMetrics(val routes: Int?, val attention: Int?, val uploading: Int?)

// Loading/Error show "—" for all 3 cells (same convention as HomeScreen's metrics ?: null),
// Empty is all-zero since every section is confirmed empty by then.
private fun hubMetricsOf(state: SurveyHubUiState): HubMetrics =
    when (state) {
        is SurveyHubUiState.Success ->
            HubMetrics(
                routes = state.assignedRoutes.size,
                attention = state.rejectedSweeps.size + state.pendingAiConfirmation.size,
                uploading = state.uploadingSessions.size,
            )
        is SurveyHubUiState.Empty -> HubMetrics(routes = 0, attention = 0, uploading = 0)
        is SurveyHubUiState.Loading, is SurveyHubUiState.Error -> HubMetrics(null, null, null)
    }

// Fixed Navy-gradient hero regardless of Light/Dark theme, same BrandHeroGradient as F02's
// HomeHeader - reads as a distinct brand surface rather than blending into colorScheme.background.
@Composable
private fun SurveyHubHeader(metrics: HubMetrics) {
    val today =
        remember {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi")),
            )
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(BrandHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg)
                // Same top offset as HomeHeader's own title, to line up with the shared bell bar.
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
    ) {
        Text(
            text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
            style = LabelTextStyle,
            color = Color.White.copy(alpha = 0.75f),
        )
        Text(text = "Khảo sát", style = DisplayTextStyle, color = Color.White)
        Spacer(Modifier.height(Spacing.lg))
        HubMetricRow(metrics = metrics)
    }
}

// Same 1-block-3-column translucent row as HomeScreen's MetricRow, with Survey Hub's own 3
// metrics (routes/attention/uploading) and colors matching the design mock.
@Composable
private fun HubMetricRow(metrics: HubMetrics) {
    val dividerColor = Color.White.copy(alpha = 0.12f)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(Radius.card))
                .border(1.dp, dividerColor, RoundedCornerShape(Radius.card)),
    ) {
        val cells =
            listOf(
                Triple("Tuyến được giao", metrics.routes, Color.White),
                Triple("Cần xử lý", metrics.attention, SurveyAttentionAmber),
                Triple("Chờ tải lên", metrics.uploading, Green400),
            )
        cells.forEachIndexed { index, (label, value, valueColor) ->
            if (index > 0) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
            }
            HubMetricCell(label = label, value = value, valueColor = valueColor, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HubMetricCell(
    label: String,
    value: Int?,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = Spacing.md, horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value?.toString() ?: "—",
            style = MetricTextStyle,
            color = if (value != null) valueColor else Color.White.copy(alpha = 0.5f),
        )
        Text(text = label, style = LabelTextStyle, color = Color.White.copy(alpha = 0.75f))
    }
}

// Mock-specific amber (#F7C66D), not one of Color.kt's named tokens - it's the same value as
// AssetCondition.DIM's dark badge text, but that pairing is about asset condition, not a general
// "attention" accent, so it's kept as its own local constant instead of reusing that token name.
private val SurveyAttentionAmber = Color(0xFFF7C66D)

@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    onCheckReadiness: (workOrderId: String) -> Unit,
    onCapturePolePhotos: (workOrderId: String) -> Unit,
    onStartRoute: (workOrderId: String) -> Unit,
    onReviewAiConfirmation: (surveySweepId: String) -> Unit,
    onResurvey: (surveySweepId: String) -> Unit,
) {
    val hasAttentionCards =
        state.rejectedSweeps.isNotEmpty() ||
            state.pendingAiConfirmation.isNotEmpty() ||
            state.uploadingSessions.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.isStale) {
            item {
                InlineBanner(
                    message = "Đang xem danh sách tuyến đã lưu trên máy, có thể chưa phải bản mới nhất",
                    tone = InlineBannerTone.OFFLINE,
                )
            }
        }
        if (state.startError != null) {
            item { InlineBanner(message = state.startError, tone = InlineBannerTone.ERROR) }
        }
        if (hasAttentionCards) {
            item {
                Text(
                    text = "CẦN BẠN XỬ LÝ",
                    style = OverlineTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.rejectedSweeps) { rejected ->
                RejectedSweepCard(rejected, onResurvey = { onResurvey(rejected.surveySweepId) })
            }
            items(state.pendingAiConfirmation) { pending ->
                AiPendingCard(pending, onReview = { onReviewAiConfirmation(pending.surveySweepId) })
            }
            items(state.uploadingSessions) { session -> UploadingSessionRow(session) }
        }
        if (state.assignedRoutes.isNotEmpty()) {
            item { AssignedRoutesSectionHeader(count = state.assignedRoutes.size) }
            items(state.assignedRoutes) { route ->
                AssignedRouteCard(
                    route,
                    isStarting = state.startingWorkOrderId == route.workOrderId,
                    onCheckReadiness = { onCheckReadiness(route.workOrderId) },
                    onCapturePolePhotos = { onCapturePolePhotos(route.workOrderId) },
                    onStartRoute = { onStartRoute(route.workOrderId) },
                )
            }
        }
    }
}

@Composable
private fun RejectedSweepCard(
    sweep: LocalRejectedSweep,
    onResurvey: () -> Unit,
) {
    val colors = AssetCondition.OUT.badgeColors(isDark = isSystemInDarkTheme())
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, colors.background, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "BỊ TỪ CHỐI · ${sweep.surveySweepId}", style = OverlineTextStyle, color = colors.text)
        Text(
            text = sweep.rejectionReason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Bởi ${sweep.rejectedByName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Khảo sát lại", onClick = onResurvey, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AiPendingCard(
    pending: LocalPendingAiConfirmation,
    onReview: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "CHỜ XÁC NHẬN AI · ${pending.surveySweepId}",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(
                text = "${AssetCondition.NORMAL.label()} ${pending.normalCount}",
                colors = AssetCondition.NORMAL.badgeColors(isDark = isDark),
            )
            StatusBadge(
                text = "${AssetCondition.DIM.label()} ${pending.dimCount}",
                colors = AssetCondition.DIM.badgeColors(isDark = isDark),
            )
            StatusBadge(
                text = "${AssetCondition.OUT.label()} ${pending.outCount}",
                colors = AssetCondition.OUT.badgeColors(isDark = isDark),
            )
        }
        PrimaryButton(text = "Xem & xác nhận", onClick = onReview, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UploadingSessionRow(session: UploadingSurveySession) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Chờ tải lên · ${session.surveySweepId}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusBadge(text = session.syncStatus.label(), colors = session.syncStatus.badgeColors())
    }
}

// Navy700 icon avatar + a count badge, matching the design mock's section header treatment
// (replaces the plain "TUYẾN ĐƯỢC GIAO" overline this section used before).
@Composable
private fun AssignedRoutesSectionHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier.size(32.dp).background(Navy700, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Route,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text = "Tuyến được giao", style = MaterialTheme.typography.titleMedium)
        }
        StatusBadge(text = "$count tuyến", colors = WorkOrderStatus.ASSIGNED.badgeColors())
    }
}

// "Ngày dự kiến" (scheduled date) is a different field than "hạn" (due date) - dueDateLabel's
// wording ("Hạn hôm nay"/"Quá hạn...") doesn't fit here, so this uses its own plain
// today-or-formatted-date text instead of reusing that component.
private fun scheduledDateIsToday(scheduledDate: String?): Boolean =
    scheduledDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } == LocalDate.now()

private fun scheduledDateText(scheduledDate: String?): String =
    if (scheduledDateIsToday(scheduledDate)) "Hôm nay" else DateFormatUtils.formatPlainDate(scheduledDate)

@Composable
private fun AssignedRouteCard(
    route: AssignedSurveyRoute,
    isStarting: Boolean,
    onCheckReadiness: () -> Unit,
    onCapturePolePhotos: () -> Unit,
    onStartRoute: () -> Unit,
) {
    val status = workOrderStatusFromWire(route.woStatus)
    val dateColor =
        if (scheduledDateIsToday(route.scheduledDate)) DueTodayColor else MaterialTheme.colorScheme.onSurface
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = route.workOrderId,
                style = OverlineTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Text(
            text = route.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.radiusMedium))
                    .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RouteInfoCell(
                icon = Icons.Filled.Place,
                label = "Địa bàn",
                value = route.communeId,
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            RouteInfoCell(
                icon = Icons.Filled.Event,
                label = "Ngày dự kiến",
                value = scheduledDateText(route.scheduledDate),
                valueColor = dateColor,
                modifier = Modifier.weight(1f),
            )
        }
        // A freshly-created work order (e.g. from the web) starts "assigned" - neither the video
        // flow nor pole-photo uploads work yet (server rejects both with 409
        // WORK_ORDER_NOT_IN_PROGRESS). Home deliberately hides survey orders, so this is the
        // only reachable place to call the start API for one - "Kiem tra san sang" below does
        // NOT call it, it only checks local device readiness for the video flow.
        if (status == WorkOrderStatus.ASSIGNED) {
            Button(
                onClick = onStartRoute,
                enabled = !isStarting,
                modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                shape = RoundedCornerShape(Radius.control),
                colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
            ) {
                Text(text = if (isStarting) "Đang bắt đầu…" else "Bắt đầu tuyến")
            }
        }
        Button(
            onClick = onCheckReadiness,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
            colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
        ) {
            Text(text = "Kiểm tra sẵn sàng")
        }
        // Uploading a pole photo needs the work order to be in_progress server-side
        // (WORK_ORDER_NOT_IN_PROGRESS, 409) - without this gate, a field engineer could capture
        // a photo for a route that was never started, only to see a confusing "Xung dot" sync
        // badge later with no clue why.
        val canCapturePolePhotos = status == WorkOrderStatus.IN_PROGRESS
        OutlinedButton(
            onClick = onCapturePolePhotos,
            enabled = canCapturePolePhotos,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
            // Not Navy700 - that's a fixed dark color, unreadable against the card's dark-mode
            // surface (near-black). onSurface flips Gray900/DarkText with the theme, same as
            // every other text in this card, so it stays legible in both modes.
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(text = "Chụp ảnh cột đèn")
        }
        if (!canCapturePolePhotos) {
            Text(
                text = "Bấm \"Bắt đầu tuyến\" trước khi chụp ảnh cột đèn",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteInfoCell(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}
