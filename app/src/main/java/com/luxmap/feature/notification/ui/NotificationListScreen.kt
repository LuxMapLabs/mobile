package com.luxmap.feature.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.feature.notification.data.NotificationType
import com.luxmap.feature.notification.data.notificationTypeFromWire

@Composable
fun NotificationListRoute(
    onBack: () -> Unit,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    viewModel: NotificationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    NotificationListScreen(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onMarkAllRead = viewModel::onMarkAllRead,
        onItemClick = { row ->
            viewModel.onMarkRead(row.notificationId)
            // Only entity_type = work_order has a real destination today - see CLAUDE.md's F14
            // "Khoảng trống đã biết" entry and the design spec section 6 for why survey_sweep/
            // fault/citizen_report are deliberately not navigable in v1.
            if (row.entityType == "work_order") onOpenWorkOrder(row.entityId)
        },
    )
}

@Composable
fun NotificationListScreen(
    state: NotificationListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onItemClick: (NotificationListRowState) -> Unit = {},
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Thông báo",
                onBack = onBack,
                actions = {
                    if (state is NotificationListUiState.Success) {
                        TextButton(onClick = onMarkAllRead) { Text("Đánh dấu tất cả đã đọc") }
                    }
                },
            )
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            when (state) {
                is NotificationListUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.testTag("notification_list_loading"))

                is NotificationListUiState.Success ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.items) { row ->
                            NotificationRow(row = row, onClick = { onItemClick(row) })
                        }
                    }

                is NotificationListUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có thông báo nào",
                        subtitle = "Thông báo về việc được giao, khảo sát và sự cố sẽ xuất hiện ở đây.",
                        action = {},
                    )

                is NotificationListUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.ErrorOutline,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = "Không tải được danh sách thông báo",
                        subtitle = state.message,
                        action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                    )
            }
        }
    }
}

@Composable
private fun NotificationRow(
    row: NotificationListRowState,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = notificationTypeFromWire(row.type).icon(), contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium)
            Text(text = row.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = DateFormatUtils.formatIsoInstant(row.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!row.isRead) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("notification_unread_dot_${row.notificationId}"),
            )
        }
    }
}

// type -> icon (design spec section 6). Reuses ImageVectors already imported elsewhere in the
// app (WorkOrderStatus.icon(), WorkOrderTaskKind.icon() in core/theme/Color.kt) - no new icon.
private fun NotificationType.icon(): ImageVector =
    when (this) {
        NotificationType.WORK_ORDER_ASSIGNED,
        NotificationType.WORK_ORDER_RESCHEDULED,
        NotificationType.WORK_ORDER_OVERDUE,
        -> Icons.Filled.Assignment
        NotificationType.WORK_ORDER_UNASSIGNED -> Icons.Filled.Autorenew
        NotificationType.WORK_ORDER_RETURNED,
        NotificationType.FAULT_REPORTED,
        -> Icons.Filled.ErrorOutline
        NotificationType.WORK_ORDER_CANCELLED -> Icons.Filled.Cancel
        NotificationType.WORK_ORDER_COMPLETED -> Icons.Filled.CheckCircle
        NotificationType.WORK_ORDER_VERIFIED -> Icons.Filled.Verified
        NotificationType.SURVEY_RETURNED,
        NotificationType.SURVEY_READY_FOR_REVIEW,
        NotificationType.SURVEY_PROCESSING_FAILED,
        -> Icons.Filled.Videocam
        NotificationType.CITIZEN_REPORT_RECEIVED -> Icons.Filled.Search
        NotificationType.UNKNOWN -> Icons.Filled.Notifications
    }
