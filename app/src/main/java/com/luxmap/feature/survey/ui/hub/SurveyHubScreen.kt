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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
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
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Navy900
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
import com.luxmap.feature.assetimport.data.AssignedImportArea
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SurveyHubRoute(
    onCapturePolePhotos: (workOrderId: String) -> Unit = {},
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    onOpenImportCapture: (tripId: String) -> Unit = {},
    highlightWorkOrderId: String? = null,
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

    // After a successful start, follow through into the pole-capture screen - same pattern as
    // F09's own startedSurveyWorkOrderId collection in WorkOrderDetailRoute.
    LaunchedEffect(Unit) {
        viewModel.startedRoute.collect { workOrderId -> onCapturePolePhotos(workOrderId) }
    }
    LaunchedEffect(Unit) {
        viewModel.startedImportTrip.collect { tripId -> onOpenImportCapture(tripId) }
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
                onCapturePolePhotos = onCapturePolePhotos,
                highlightWorkOrderId = highlightWorkOrderId,
                modifier = modifier,
                onRetry = viewModel::onRetry,
                onStartRoute = viewModel::onStartRoute,
                onReviewAiConfirmation = onReviewAiConfirmation,
                onRequestCancellation = viewModel::onRequestCancellation,
                onStartImportTrip = viewModel::onStartImportTrip,
            )
        }
    }
}

@Composable
fun SurveyHubScreen(
    state: SurveyHubUiState,
    onCapturePolePhotos: (workOrderId: String) -> Unit = {},
    highlightWorkOrderId: String? = null,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onStartRoute: (workOrderId: String) -> Unit = {},
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    onResurvey: (surveySweepId: String) -> Unit = {},
    onRequestCancellation: (workOrderId: String, reason: String) -> Unit = { _, _ -> },
    onStartImportTrip: (areaCode: String) -> Unit = {},
) {
    // Which route's "Yeu cau huy" sheet is open, if any - lives here (not in the ViewModel) same
    // as F09's own showCancelSheet, since it is pure navigation/presentation state.
    var cancelSheetWorkOrderId by remember { mutableStateOf<String?>(null) }
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SurveyHubHeader()
            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    is SurveyHubUiState.Loading -> HubLoadingSkeleton()

                    is SurveyHubUiState.Success ->
                        SurveyHubContent(
                            state = state,
                            onCapturePolePhotos = onCapturePolePhotos,
                            onStartRoute = onStartRoute,
                            onReviewAiConfirmation = onReviewAiConfirmation,
                            onResurvey = onResurvey,
                            onOpenCancelSheet = { cancelSheetWorkOrderId = it },
                            highlightWorkOrderId = highlightWorkOrderId,
                            onStartImportTrip = onStartImportTrip,
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

// Compact flat bar instead of a tall hero (matches the mockup's own header, not stretched to fit
// a metric block). The notification bell+badge seen in the mockup is not drawn here - the real
// app already renders one globally in NavGraph's Scaffold across all 4 tabs, not per-screen.
@Composable
private fun SurveyHubHeader() {
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
                .background(Navy900)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = "Khảo sát", style = HeadlineTextStyle, color = Color.White)
        Text(
            text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
            style = LabelTextStyle,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    onCapturePolePhotos: (workOrderId: String) -> Unit,
    onStartRoute: (workOrderId: String) -> Unit,
    onReviewAiConfirmation: (surveySweepId: String) -> Unit,
    onResurvey: (surveySweepId: String) -> Unit,
    onOpenCancelSheet: (workOrderId: String) -> Unit,
    highlightWorkOrderId: String? = null,
    onStartImportTrip: (areaCode: String) -> Unit = {},
) {
    val hasAttentionCards =
        state.rejectedSweeps.isNotEmpty() ||
            state.pendingAiConfirmation.isNotEmpty() ||
            state.uploadingSessions.isNotEmpty()
    val listState = rememberLazyListState()
    val visibleRoutes = state.assignedRoutes.filterNot { it.workOrderId in state.locallyCompletedWorkOrderIds }
    val inProgressRoutes =
        visibleRoutes.filter {
            workOrderStatusFromWire(it.woStatus) == WorkOrderStatus.IN_PROGRESS
        }
    val todoRoutes = visibleRoutes - inProgressRoutes.toSet()
    LaunchedEffect(state.assignedRoutes, highlightWorkOrderId) {
        val highlighted =
            visibleRoutes.firstOrNull { it.workOrderId == highlightWorkOrderId } ?: return@LaunchedEffect
        var index = 0
        if (state.isStale) index++
        if (state.startError != null) index++
        if (hasAttentionCards) {
            index += 1 + state.rejectedSweeps.size + state.pendingAiConfirmation.size + state.uploadingSessions.size
        }
        val inProgressHeaderOffset = if (inProgressRoutes.isNotEmpty()) 1 + inProgressRoutes.size else 0
        index +=
            if (highlighted in inProgressRoutes) {
                1 + inProgressRoutes.indexOf(highlighted)
            } else {
                inProgressHeaderOffset + 1 + todoRoutes.indexOf(highlighted)
            }
        listState.animateScrollToItem(index)
    }
    LazyColumn(
        state = listState,
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
        items(state.assignedImportAreas) { area ->
            AssignedImportAreaCard(area, onStartTrip = { onStartImportTrip(area.areaCode) })
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
        listOf("Đang thực hiện" to inProgressRoutes, "Chưa bắt đầu" to todoRoutes).forEach { (title, routes) ->
            if (routes.isNotEmpty()) {
                item { SurveyHubSectionHeader(title = title, count = routes.size) }
                items(routes) { route ->
                    AssignedRouteCard(
                        route,
                        isStarting = state.startingWorkOrderId == route.workOrderId,
                        isCancelled = route.workOrderId in state.cancelledWorkOrderIds,
                        isHighlighted = route.workOrderId == highlightWorkOrderId,
                        onCapturePolePhotos = { onCapturePolePhotos(route.workOrderId) },
                        onStartForPoleCapture = { onStartRoute(route.workOrderId) },
                        onRequestCancellation = { onOpenCancelSheet(route.workOrderId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AssignedImportAreaCard(
    area: AssignedImportArea,
    onStartTrip: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier.size(40.dp).background(ImportAreaTileBackground, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.AddLocationAlt,
                    contentDescription = null,
                    tint = ImportAreaTileForeground,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "${area.areaCode} · Khu vực mới",
                        style = LabelTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(text = "Đã giao", colors = WorkOrderStatus.ASSIGNED.badgeColors())
                }
                Text(text = area.title, style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = "Chưa có bản đồ số", colors = SyncStatus.QUEUED_OFFLINE.badgeColors())
            StatusBadge(text = "Hạn ${area.dueDateLabel}", colors = SyncStatus.QUEUED_ONLINE.badgeColors())
        }
        Button(
            onClick = onStartTrip,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
            colors = ButtonDefaults.buttonColors(containerColor = Navy700, contentColor = Color.White),
        ) {
            Text(text = "Bắt đầu chuyến")
        }
    }
}

private val ImportAreaTileBackground = Color(0xFFF1EEFB)
private val ImportAreaTileForeground = Color(0xFF5B3FB4)

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
private fun SurveyHubSectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = title, style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
        StatusBadge(text = "$count", colors = WorkOrderStatus.OPEN.badgeColors())
    }
}

// "Ngày dự kiến" (scheduled date) is a different field than "hạn" (due date) - dueDateLabel's
// wording ("Hạn hôm nay"/"Quá hạn...") doesn't fit here, so this uses its own plain
// today-or-formatted-date text instead of reusing that component.
private fun scheduledDateIsToday(scheduledDate: String?): Boolean =
    scheduledDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } == LocalDate.now()

private fun scheduledDateText(scheduledDate: String?): String =
    if (scheduledDateIsToday(scheduledDate)) "Hôm nay" else DateFormatUtils.formatPlainDate(scheduledDate)

private val RouteTileBackground = Color(0xFFEEF3FB)
private val RouteTileForeground = Color(0xFF1A4FA0)

@Composable
private fun AssignedRouteCard(
    route: AssignedSurveyRoute,
    isStarting: Boolean,
    isCancelled: Boolean,
    onCapturePolePhotos: () -> Unit,
    onStartForPoleCapture: () -> Unit,
    onRequestCancellation: () -> Unit,
    isHighlighted: Boolean = false,
) {
    val status = workOrderStatusFromWire(route.woStatus)
    // Same rule WorkOrderDetailScreen (F09) uses (CLAUDE.md WO-CANCEL-REQUEST): only while
    // still assigned, not yet started. The list endpoint has no allowed_actions field to read
    // this from (unlike WorkOrderDetail), so this applies the documented rule directly instead
    // of guessing a different one.
    val canRequestCancellation = status == WorkOrderStatus.ASSIGNED && !isCancelled
    val dateColor =
        if (scheduledDateIsToday(route.scheduledDate)) DueTodayColor else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isHighlighted) Navy700 else MaterialTheme.colorScheme.outline
    val borderWidth = if (isHighlighted) 2.dp else 1.dp
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(borderWidth, borderColor, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier.size(40.dp).background(RouteTileBackground, RoundedCornerShape(Radius.chip)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Route, contentDescription = null, tint = RouteTileForeground)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = "${route.workOrderId} · Tuyến khảo sát",
                        style = LabelTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    StatusBadge(text = status.label(), colors = status.badgeColors())
                }
                Text(text = route.title, style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
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
        // sent, hide the action row - the manager still needs to decide, so starting or
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
            // Only reachable way to call the start API for a survey work order - Home
            // deliberately hides survey orders (see HomeScreen.kt). Tapping this while still
            // "assigned" starts the route first (POST /sweeps needs wo_status = in_progress
            // server-side), then SurveyHubViewModel's startedRoute flow navigates to the
            // pole list on success.
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
                            status == WorkOrderStatus.IN_PROGRESS -> "Chụp ảnh/video từng cột"
                            else -> "Đã hoàn tất"
                        },
                )
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
