package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.CancelRequestSheet
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxSnackbarHost
import com.luxmap.core.ui.components.LuxTabHero
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
    // Refetch on every ON_RESUME after the first one. The ViewModel counts the resumes: this
    // composable is disposed while another screen is on top, so a local flag would reset and swallow
    // the refetch on return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { LuxSnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LuxTabHero(
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
                is SurveyHubUiState.Loading ->
                    LuxLoadingRegion(
                        description = "Đang tải việc khảo sát",
                        modifier = Modifier.padding(Spacing.lg),
                    ) {
                        LuxSkeleton(modifier = Modifier.fillMaxWidth(0.4f).height(20.dp))
                        repeat(3) {
                            LuxSkeleton(
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }

                is SurveyHubUiState.Success ->
                    SurveyHubContent(
                        state = state,
                        onCapturePolePhotos = onCapturePolePhotos,
                        onStartRoute = onStartRoute,
                        onOpenCancelSheet = { cancelSheetWorkOrderId = it },
                        highlightWorkOrderId = highlightWorkOrderId,
                        onOpenWorkOrderDetail = onOpenWorkOrderDetail,
                    )

                is SurveyHubUiState.OfflineEmpty ->
                    LuxEmptyState(
                        title = "Chưa có dữ liệu đã lưu",
                        message = "Kết nối mạng để tải danh sách. Mở lệnh và tải PDF trước khi đi hiện trường.",
                        icon = Icons.Rounded.EventAvailable,
                        actionLabel = "Thử lại",
                        onAction = onRetry,
                    )

                is SurveyHubUiState.Empty ->
                    LuxEmptyState(
                        title = "Chưa có việc khảo sát được giao",
                        message = "Khi Quản lý giao tuyến khảo sát hoặc lệnh đo GPS cột, lệnh sẽ hiện ở đây.",
                        icon = Icons.Rounded.EventAvailable,
                        actionLabel = "Làm mới",
                        onAction = onRetry,
                    )

                is SurveyHubUiState.Error ->
                    LuxErrorState(
                        onRetry = onRetry,
                        kind = if (state.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                        title = "Không tải được việc khảo sát",
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
            scope.launch { snackbarHostState.showSnackbar("Đã gửi yêu cầu hủy. Chờ Quản lý duyệt") }
            cancelSheetWorkOrderId = null
        }
    }
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
    var scrolledForHighlight by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(keys, highlightWorkOrderId) {
        val index = keys.indexOf(highlightWorkOrderId)
        if (highlightWorkOrderId != null && index >= 0 && scrolledForHighlight != highlightWorkOrderId) {
            scrolledForHighlight = highlightWorkOrderId
            listState.animateScrollToItem(index)
        }
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
                    message =
                        "Danh sách có thể chưa đầy đủ hoặc mới nhất. " +
                            "Mở lệnh và tải PDF khi có mạng trước khi đi hiện trường.",
                    tone = LuxBannerTone.INFO,
                )
            }
        }
        state.startError?.let { error ->
            item(key = "start_error") { LuxBanner(message = error, tone = LuxBannerTone.ERROR) }
        }
        groups.forEach { group ->
            item(key = "header_${group.section}") {
                LuxSectionHeader(title = "${group.section.title} (${group.items.size})")
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
