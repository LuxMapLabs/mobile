package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BrandHeroGradient
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.ui.components.CancelRequestSheet
import com.luxmap.core.ui.components.DueTodayColor
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import kotlinx.coroutines.launch
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

    // The Hub's own ViewModel survives navigating into another screen (pole-photo list,
    // F03 readiness check) and back - its data would otherwise go stale after an action there
    // changes a route's status (e.g. "Bat dau tuyen", or "Hoan tat" completing a survey).
    // Refetch on every ON_RESUME after the first one, same pattern as HomeScreen.kt.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFirstResume by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (isFirstResume) isFirstResume = false else viewModel.refresh()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // After a successful start from either Hub button, follow through into the right screen -
    // same pattern as F09's own startedSurveyWorkOrderId collection in WorkOrderDetailRoute. The
    // 2 survey methods stay parallel (CLAUDE.md 2026-10-29), so this only picks a destination,
    // it never starts one flow from the other.
    LaunchedEffect(Unit) {
        viewModel.startedRoute.collect { navigation ->
            when (navigation.destination) {
                SurveyRouteStartDestination.POLE_CAPTURE -> onCapturePolePhotos(navigation.workOrderId)
                SurveyRouteStartDestination.READINESS_CHECK -> onCheckReadiness(navigation.workOrderId)
            }
        }
    }

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
                onRequestCancellation = viewModel::onRequestCancellation,
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
    onStartRoute: (workOrderId: String, destination: SurveyRouteStartDestination) -> Unit = { _, _ -> },
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    onResurvey: (surveySweepId: String) -> Unit = {},
    onRequestCancellation: (workOrderId: String, reason: String) -> Unit = { _, _ -> },
) {
    val routeCount = assignedRouteCountOf(state)
    // Which route's "Yeu cau huy" sheet is open, if any - lives here (not in the ViewModel) same
    // as F09's own showCancelSheet, since it is pure navigation/presentation state.
    var cancelSheetWorkOrderId by remember { mutableStateOf<String?>(null) }
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SurveyHubHeader(routeCount = routeCount)
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
                            onOpenCancelSheet = { cancelSheetWorkOrderId = it },
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
        TopSlideToast(
            state = toastState,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }

    val sheetWorkOrderId = cancelSheetWorkOrderId
    if (sheetWorkOrderId != null && state is SurveyHubUiState.Success) {
        CancelRequestSheet(
            isSubmitting = state.requestingCancellationFor == sheetWorkOrderId,
            errorMessage = state.cancellationError,
            onDismiss = { cancelSheetWorkOrderId = null },
            onSend = { reason -> onRequestCancellation(sheetWorkOrderId, reason) },
        )
    }
    // Auto-close once this route's request actually lands in cancelledWorkOrderIds - matches
    // F09's own LaunchedEffect(uiState) pattern for cancellationRequestStatus == "pending".
    LaunchedEffect(state) {
        val success = state as? SurveyHubUiState.Success
        if (sheetWorkOrderId != null && success?.cancelledWorkOrderIds?.contains(sheetWorkOrderId) == true) {
            scope.launch { toastState.show("Đã gửi yêu cầu hủy · Chờ Kỹ sư bảo trì duyệt") }
            cancelSheetWorkOrderId = null
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

// Loading/Error show null (hero falls back to a neutral line), Empty is 0 since every section
// is confirmed empty by then.
private fun assignedRouteCountOf(state: SurveyHubUiState): Int? =
    when (state) {
        is SurveyHubUiState.Success -> state.assignedRoutes.size
        is SurveyHubUiState.Empty -> 0
        is SurveyHubUiState.Loading, is SurveyHubUiState.Error -> null
    }

// Matches the Khao Sat v4 mock's hero exactly (CLAUDE.md 2026-10-29) - no metric block, just the
// date, title, and a plain "N tuyen duoc giao cho ban" line. The notification bell+badge seen in
// the mock is not drawn here - the real app already renders one globally in NavGraph's Scaffold
// across all 4 tabs, not per-screen.
@Composable
private fun SurveyHubHeader(routeCount: Int?) {
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
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Column {
            Text(
                text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
                style = LabelTextStyle,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(text = "Khảo sát", style = DisplayTextStyle, color = Color.White)
        }
        Text(
            text = if (routeCount != null) "$routeCount tuyến được giao cho bạn" else "Đang tải tuyến…",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    onCheckReadiness: (workOrderId: String) -> Unit,
    onCapturePolePhotos: (workOrderId: String) -> Unit,
    onStartRoute: (workOrderId: String, destination: SurveyRouteStartDestination) -> Unit,
    onReviewAiConfirmation: (surveySweepId: String) -> Unit,
    onResurvey: (surveySweepId: String) -> Unit,
    onOpenCancelSheet: (workOrderId: String) -> Unit,
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
                    isCancelled = route.workOrderId in state.cancelledWorkOrderIds,
                    isLocallyCompleted = route.workOrderId in state.locallyCompletedWorkOrderIds,
                    onCheckReadiness = { onCheckReadiness(route.workOrderId) },
                    onCapturePolePhotos = { onCapturePolePhotos(route.workOrderId) },
                    onStartForPoleCapture = {
                        onStartRoute(route.workOrderId, SurveyRouteStartDestination.POLE_CAPTURE)
                    },
                    onStartForReadinessCheck = {
                        onStartRoute(route.workOrderId, SurveyRouteStartDestination.READINESS_CHECK)
                    },
                    onRequestCancellation = { onOpenCancelSheet(route.workOrderId) },
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
    isCancelled: Boolean,
    isLocallyCompleted: Boolean,
    onCheckReadiness: () -> Unit,
    onCapturePolePhotos: () -> Unit,
    onStartForPoleCapture: () -> Unit,
    onStartForReadinessCheck: () -> Unit,
    onRequestCancellation: () -> Unit,
) {
    val status = workOrderStatusFromWire(route.woStatus)
    // Same rule WorkOrderDetailScreen (F09) uses (CLAUDE.md WO-CANCEL-REQUEST): only while
    // still assigned, not yet started. The list endpoint has no allowed_actions field to read
    // this from (unlike WorkOrderDetail), so this applies the documented rule directly instead
    // of guessing a different one.
    val canRequestCancellation = status == WorkOrderStatus.ASSIGNED && !isCancelled
    val dateColor =
        if (scheduledDateIsToday(route.scheduledDate)) DueTodayColor else MaterialTheme.colorScheme.onSurface
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.card))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        // Navy header block (route id + commune, status, title) - matches the mock's card
        // header, which groups the "which route, what state" info on its own dark band instead
        // of mixing it into the white body with the action buttons.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Navy700)
                    .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${route.workOrderId} · ${route.communeId}",
                    style = OverlineTextStyle,
                    color = Color.White.copy(alpha = 0.8f),
                )
                StatusBadge(text = status.label(), colors = status.badgeColors())
            }
            Text(
                text = route.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            RouteInfoCell(
                icon = Icons.Filled.Event,
                label = "Ngày dự kiến",
                value = scheduledDateText(route.scheduledDate),
                valueColor = dateColor,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Dimens.radiusMedium))
                        .padding(Spacing.md),
            )
            // Mirrors F09's own rule (CLAUDE.md WO-CANCEL-REQUEST): once a cancellation request is
            // sent, hide every action button - the manager still needs to decide, so starting or
            // capturing anything for this route in the meantime would just be wasted work.
            if (isCancelled) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .background(
                                AssetCondition.OUT.badgeColors(isDark = isSystemInDarkTheme()).background,
                                RoundedCornerShape(Radius.control),
                            ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Đã gửi yêu cầu hủy · chờ duyệt",
                        color = AssetCondition.OUT.badgeColors(isDark = isSystemInDarkTheme()).text,
                    )
                }
            } else if (isLocallyCompleted) {
                // Found on a real device 2026-10-29: this route's card looked completely
                // unchanged after completing its survey at the pole list - wo_status itself only
                // catches up once sync_queue actually uploads the completion, same local-state
                // gap WorkOrderDetailScreen already guards against for repair/inspection
                // ("localCompletion?.submitStatus == pending").
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouchTarget)
                            .background(
                                WorkOrderStatus.ASSIGNED.badgeColors().background,
                                RoundedCornerShape(Radius.control),
                            ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Đã nộp kết quả khảo sát · chờ đồng bộ",
                        color = WorkOrderStatus.ASSIGNED.badgeColors().text,
                    )
                }
            } else {
                if (canRequestCancellation) {
                    OutlinedButton(
                        onClick = onRequestCancellation,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(text = "Yêu cầu hủy")
                    }
                }
                // "Quay video toan tuyen" (CLAUDE.md 2026-10-29, renamed from "Kiem tra san
                // sang" for clarity - that name described only the first screen it leads to).
                // Runs in parallel with the pole-capture CTA below, never merged (CLAUDE.md
                // "song song, khong gop"). Same as that CTA, tapping this while still "assigned"
                // starts the route first (via SurveyHubViewModel.startedRoute), then continues
                // into the readiness check - previously this button skipped straight to the
                // checklist without starting the route, which could lose an entire recorded
                // video at upload time (POST /sweeps requires wo_status = in_progress server-side).
                Button(
                    onClick = if (status == WorkOrderStatus.ASSIGNED) onStartForReadinessCheck else onCheckReadiness,
                    enabled = !isStarting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
                ) {
                    Text(text = if (isStarting) "Đang bắt đầu…" else "Quay video toàn tuyến")
                }
                // Merged CTA (Khao Sat v4 mock, CLAUDE.md 2026-10-29): a freshly-created work
                // order starts "assigned" - pole-photo uploads need in_progress server-side
                // (409 WORK_ORDER_NOT_IN_PROGRESS otherwise). Home deliberately hides survey
                // orders, so this is the only reachable place to call the start API for one.
                // Tapping this while still "assigned" starts the route, then
                // SurveyHubViewModel's startedRoute flow (observed by SurveyHubRoute) navigates
                // to the pole list on success - the button itself only ever calls one of the two
                // callbacks, never both.
                val canAct = status == WorkOrderStatus.ASSIGNED || status == WorkOrderStatus.IN_PROGRESS
                Button(
                    onClick = if (status == WorkOrderStatus.ASSIGNED) onStartForPoleCapture else onCapturePolePhotos,
                    enabled = canAct && !isStarting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
                    shape = RoundedCornerShape(Radius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
                ) {
                    Text(
                        text =
                            when {
                                isStarting -> "Đang bắt đầu…"
                                status == WorkOrderStatus.ASSIGNED -> "Bắt đầu"
                                status == WorkOrderStatus.IN_PROGRESS -> "Tiếp tục"
                                else -> "Đã hoàn tất"
                            },
                    )
                }
            }
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
