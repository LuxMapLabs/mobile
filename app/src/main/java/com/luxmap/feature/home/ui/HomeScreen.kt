package com.luxmap.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSegment
import com.luxmap.core.ui.components.LuxSegmented
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxTabTopBar
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.workorder.data.CompletionSyncState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Nav-graph entry point (F02/FM-16) — nối HomeViewModel, đưa HomeScreen thành stateless để dễ
// lái bằng dữ liệu cố định.
@Composable
fun HomeRoute(
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val username by viewModel.username.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Home's own ViewModel survives navigating into another screen (work order detail, survey...)
    // and back - its data would otherwise go stale after an action there changes a work order's
    // status (e.g. the engineer submits, or a manager verifies/returns it from the web). Refetch
    // every time this screen's back stack entry becomes the visible one again, not just on first
    // load - but skip the very first ON_RESUME, since init{} already covers that one and retrying
    // too would just flash the loading state right after it.
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFirstResume by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (isFirstResume) isFirstResume = false else viewModel.retry()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HomeScreen(
        uiState = uiState,
        username = username,
        onOpenWorkOrder = onOpenWorkOrder,
        onRetry = viewModel::retry,
        unreadNotificationCount = unreadNotificationCount,
        onOpenNotifications = onOpenNotifications,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

private val VI = Locale.forLanguageTag("vi")

internal fun homeGreeting(
    username: String?,
    today: LocalDate,
): String {
    val date = today.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VI)).replaceFirstChar { it.titlecase(VI) }
    val name = username?.takeIf { it.isNotBlank() }
    return if (name != null) "Xin chào, $name · $date" else "Xin chào · $date"
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    username: String?,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    onRetry: () -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val today = remember { LocalDate.now() }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LuxTabTopBar(
                title = "Việc hôm nay",
                subtitle = homeGreeting(username, today),
                unreadNotificationCount = unreadNotificationCount,
                onNotificationsClick = onOpenNotifications,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is HomeUiState.Loading -> HomeLoading()
                is HomeUiState.Success ->
                    HomeContent(
                        data = uiState.data,
                        isStale = uiState.isStale,
                        lastSyncedAt = uiState.lastSyncedAt,
                        completionSyncStates = uiState.completionSyncStates,
                        onOpenWorkOrder = onOpenWorkOrder,
                    )
                is HomeUiState.Empty -> HomeEmpty(onRetry = onRetry)
                is HomeUiState.Error -> HomeError(isNetworkError = uiState.isNetworkError, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    isStale: Boolean,
    lastSyncedAt: Instant?,
    completionSyncStates: Map<String, CompletionSyncState>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTaskTab.REPAIR) }
    val today = remember { LocalDate.now() }
    val tasks = remember(data) { data.visibleTasks() }
    val summary = remember(tasks, today) { tasks.homeSummary(today) }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SummaryStatsRow(summary = summary)
            LuxSegmented(
                segments = HomeTaskTab.entries.map { LuxSegment(it, "${it.label} · ${tasks.countFor(it)}") },
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
        val visibleItems = remember(tasks, selectedTab, today) { tasks.sortedFor(selectedTab, today) }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (isStale) {
                item {
                    LuxBanner(
                        message = "Đang xem dữ liệu đã lưu, cập nhật lúc ${DateFormatUtils.formatInstant(
                            lastSyncedAt,
                        )}",
                        tone = LuxBannerTone.INFO,
                    )
                }
            }
            if (visibleItems.isEmpty()) {
                item { TabEmptyState(tab = selectedTab) }
            } else {
                items(visibleItems, key = { it.workOrderId }) { item ->
                    HomeTaskCard(
                        item = item,
                        syncState = completionSyncStates[item.workOrderId],
                        today = today,
                        onClick = { onOpenWorkOrder(item.workOrderId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatsRow(summary: HomeSummary) {
    val overdueColor =
        if (summary.overdue > 0) LuxTheme.colors.dueOverdue else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SummaryStatCell(
            label = "Tổng",
            value = summary.total,
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
        SummaryStatDivider()
        SummaryStatCell(label = "Quá hạn", value = summary.overdue, valueColor = overdueColor)
        SummaryStatDivider()
        SummaryStatCell(
            label = "Đang làm",
            value = summary.inProgress,
            valueColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RowScope.SummaryStatCell(
    label: String,
    value: Int,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "$value", style = MaterialTheme.typography.titleMedium, color = valueColor)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryStatDivider() {
    Box(
        modifier =
            Modifier
                .padding(vertical = Spacing.xs)
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun TabEmptyState(tab: HomeTaskTab) {
    when (tab) {
        HomeTaskTab.INSPECTION ->
            LuxEmptyState(
                title = "Không có nhiệm vụ kiểm tra",
                message = "Lệnh kiểm tra cột đèn và thiết bị được giao sẽ hiện ở đây.",
                icon = WorkOrderTaskKind.INSPECTION.icon(),
            )
        HomeTaskTab.REPAIR ->
            LuxEmptyState(
                title = "Không có nhiệm vụ sửa chữa",
                message = "Lệnh sửa chữa và bảo trì được giao sẽ hiện ở đây.",
                icon = WorkOrderTaskKind.REPAIR.icon(),
            )
    }
}

@Composable
private fun HomeLoading() {
    LuxLoadingRegion(description = "Đang tải việc được giao", modifier = Modifier.padding(Spacing.lg)) {
        LuxSkeleton(modifier = Modifier.fillMaxWidth().height(24.dp))
        LuxSkeleton(modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small)
        repeat(3) {
            LuxSkeleton(modifier = Modifier.fillMaxWidth().height(120.dp), shape = MaterialTheme.shapes.medium)
        }
    }
}

@Composable
private fun HomeEmpty(onRetry: () -> Unit) {
    LuxEmptyState(
        title = "Chưa có việc được giao",
        message = "Khi Kỹ sư bảo trì giao lệnh sửa chữa hoặc kiểm tra, lệnh sẽ hiện ở đây.",
        icon = Icons.Filled.EventAvailable,
        actionLabel = "Làm mới",
        onAction = onRetry,
    )
}

@Composable
private fun HomeError(
    isNetworkError: Boolean,
    onRetry: () -> Unit,
) {
    LuxErrorState(
        onRetry = onRetry,
        kind = if (isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
        title = "Không tải được danh sách lệnh",
        message = if (isNetworkError) "Kiểm tra kết nối rồi thử lại." else "Vui lòng thử lại sau ít phút.",
    )
}
