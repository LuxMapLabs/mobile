package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.CancelRequestSheet
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.LuxTabTopBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.TopSlideToast
import com.luxmap.core.ui.components.TopToastState
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SurveyHubRoute(
    onCapturePolePhotos: (workOrderId: String) -> Unit = {},
    onOpenWorkOrderDetail: (workOrderId: String) -> Unit = {},
    unreadNotificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
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

    SurveyHubScreen(
        state = uiState,
        onCapturePolePhotos = onCapturePolePhotos,
        highlightWorkOrderId = highlightWorkOrderId,
        modifier = modifier,
        onRetry = viewModel::onRetry,
        onStartRoute = viewModel::onStartRoute,
        onRequestCancellation = viewModel::onRequestCancellation,
        onOpenWorkOrderDetail = onOpenWorkOrderDetail,
        unreadNotificationCount = unreadNotificationCount,
        onOpenNotifications = onOpenNotifications,
    )
}

@Composable
fun SurveyHubScreen(
    state: SurveyHubUiState,
    onCapturePolePhotos: (workOrderId: String) -> Unit = {},
    highlightWorkOrderId: String? = null,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onStartRoute: (workOrderId: String) -> Unit = {},
    onRequestCancellation: (workOrderId: String, reason: String) -> Unit = { _, _ -> },
    onOpenWorkOrderDetail: (workOrderId: String) -> Unit = {},
    unreadNotificationCount: Int = 0,
    onOpenNotifications: () -> Unit = {},
) {
    // Which route's "Yeu cau huy" sheet is open, if any - lives here (not in the ViewModel) same
    // as F09's own showCancelSheet, since it is pure navigation/presentation state.
    var cancelSheetWorkOrderId by remember { mutableStateOf<String?>(null) }
    val toastState = remember { TopToastState() }
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LuxTabTopBar(
                    title = "Khảo sát",
                    subtitle = surveyHubDate(remember { LocalDate.now() }),
                    unreadNotificationCount = unreadNotificationCount,
                    onNotificationsClick = onOpenNotifications,
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    is SurveyHubUiState.Loading -> HubLoadingSkeleton()

                    is SurveyHubUiState.Success ->
                        SurveyHubContent(
                            state = state,
                            onCapturePolePhotos = onCapturePolePhotos,
                            onStartRoute = onStartRoute,
                            onOpenCancelSheet = { cancelSheetWorkOrderId = it },
                            highlightWorkOrderId = highlightWorkOrderId,
                            onOpenWorkOrderDetail = onOpenWorkOrderDetail,
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
        SkeletonBar(width = 120.dp, height = 14.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
                SkeletonBar(widthFraction = 0.6f, height = 14.dp, color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    onCapturePolePhotos: (workOrderId: String) -> Unit,
    onStartRoute: (workOrderId: String) -> Unit,
    onOpenCancelSheet: (workOrderId: String) -> Unit,
    highlightWorkOrderId: String? = null,
    onOpenWorkOrderDetail: (workOrderId: String) -> Unit = {},
) {
    val today = remember { LocalDate.now() }
    val groups =
        remember(state) {
            surveyHubGroups(
                state.assignedRoutes,
                state.assignedPoleGpsOrders,
                state.completionSyncStates,
                state.locallyCompletedWorkOrderIds,
            )
        }
    val keys =
        buildList {
            if (state.isStale) add("stale")
            if (state.startError != null) add("start_error")
            groups.forEach { group ->
                add("header_${group.section}")
                group.items.forEach { add(it.order.workOrderId) }
            }
        }
    val listState = rememberLazyListState()
    LaunchedEffect(keys, highlightWorkOrderId) {
        val index = keys.indexOf(highlightWorkOrderId)
        if (highlightWorkOrderId != null && index >= 0) listState.animateScrollToItem(index)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.isStale) {
            item(key = "stale") {
                LuxBanner(
                    message = "Đang xem danh sách đã lưu trên máy, có thể chưa phải bản mới nhất",
                    tone = LuxBannerTone.INFO,
                )
            }
        }
        state.startError?.let { error ->
            item(key = "start_error") { LuxBanner(message = error, tone = LuxBannerTone.ERROR) }
        }
        groups.forEach { group ->
            item(key = "header_${group.section}") {
                LuxSectionHeader(title = "${group.section.title} · ${group.items.size}")
            }
            items(group.items, key = { it.order.workOrderId }) { item ->
                val id = item.order.workOrderId
                when {
                    group.section == HubSection.PENDING_SYNC -> PendingSyncCard(item, today)
                    item.kind == HubItemKind.POLE_GPS ->
                        PoleGpsCard(
                            item = item,
                            today = today,
                            isHighlighted = id == highlightWorkOrderId,
                            onOpen = { onOpenWorkOrderDetail(id) },
                        )
                    else ->
                        SurveyRouteCard(
                            item = item,
                            today = today,
                            isStarting = state.startingWorkOrderId == id,
                            isCancelled = id in state.cancelledWorkOrderIds,
                            isHighlighted = id == highlightWorkOrderId,
                            onStart = { onStartRoute(id) },
                            onCapture = { onCapturePolePhotos(id) },
                            onRequestCancellation = { onOpenCancelSheet(id) },
                        )
                }
            }
        }
    }
}
