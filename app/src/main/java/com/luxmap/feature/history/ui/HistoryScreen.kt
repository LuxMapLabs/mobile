package com.luxmap.feature.history.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxTabHero
import com.luxmap.feature.history.data.HistoryItem
import java.time.Instant

@Composable
fun HistoryRoute(
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HistoryScreen(
        uiState = uiState,
        onOpenWorkOrder = onOpenWorkOrder,
        onRetry = viewModel::retry,
        unreadNotificationCount = unreadNotificationCount,
        onOpenNotifications = onOpenNotifications,
        modifier = modifier,
    )
}

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    onRetry: () -> Unit,
    unreadNotificationCount: Int,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LuxTabHero(
                title = "Lịch sử",
                subtitle = "Các lệnh đã hoàn tất hoặc đã huỷ",
                unreadNotificationCount = unreadNotificationCount,
                onNotificationsClick = onOpenNotifications,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is HistoryUiState.Loading -> HistoryLoading()
                is HistoryUiState.Success ->
                    HistoryContent(
                        items = uiState.items,
                        isStale = uiState.isStale,
                        lastSyncedAt = uiState.lastSyncedAt,
                        onOpenWorkOrder = onOpenWorkOrder,
                    )
                is HistoryUiState.Empty -> HistoryEmpty(onRetry = onRetry)
                is HistoryUiState.Error ->
                    LuxErrorState(
                        onRetry = onRetry,
                        kind = if (uiState.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                    )
            }
        }
    }
}

@Composable
private fun HistoryContent(
    items: List<HistoryItem>,
    isStale: Boolean,
    lastSyncedAt: Instant?,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    val filters = remember(items) { items.availableFilters() }
    val current = selected.takeIf { it in filters } ?: HistoryFilter.ALL
    val visible = remember(items, current) { items.filteredBy(current) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            filters.forEach { filter ->
                FilterChip(
                    selected = filter == current,
                    onClick = { selected = filter },
                    label = { Text("${filter.label} (${items.countFor(filter)})") },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, top = Spacing.xs, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (isStale) {
                item {
                    val syncedAt = DateFormatUtils.formatInstant(lastSyncedAt)
                    LuxBanner(
                        message = "Đang xem dữ liệu đã lưu, cập nhật lúc $syncedAt",
                        tone = LuxBannerTone.INFO,
                    )
                }
            }
            items(visible, key = { it.workOrderId }) { item ->
                HistoryCard(item = item, onClick = { onOpenWorkOrder(item.workOrderId) })
            }
        }
    }
}

@Composable
private fun HistoryLoading() {
    LuxLoadingRegion(description = "Đang tải lịch sử", modifier = Modifier.padding(Spacing.lg)) {
        LuxSkeleton(modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small)
        repeat(3) {
            LuxSkeleton(modifier = Modifier.fillMaxWidth().height(120.dp), shape = MaterialTheme.shapes.medium)
        }
    }
}

@Composable
private fun HistoryEmpty(onRetry: () -> Unit) {
    LuxEmptyState(
        title = "Chưa có lịch sử",
        message = "Các lệnh bạn đã hoàn tất hoặc đã huỷ sẽ hiện ở đây.",
        icon = Icons.Rounded.History,
        actionLabel = "Làm mới",
        onAction = onRetry,
    )
}
