package com.luxmap.feature.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Navy900
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
            notificationNavigationTarget(row)?.let(onOpenWorkOrder)
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
        // Success renders its own navy hero with an embedded back button and "Đọc tất cả"
        // pill (see NotificationHeroSection) instead of this plain top bar - the other 3
        // states have no unread count to show a mark-all action for, so they keep it.
        topBar = {
            if (state !is NotificationListUiState.Success) {
                AppTopBar(title = "Thông báo", onBack = onBack)
            }
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            when (state) {
                is NotificationListUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.testTag("notification_list_loading"))

                is NotificationListUiState.Success ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        val unreadCount = state.items.count { !it.isRead }
                        NotificationHeroSection(
                            unreadCount = unreadCount,
                            onBack = onBack,
                            onMarkAllRead = onMarkAllRead,
                        )
                        // Overlaps the hero's bottom edge by Spacing.xl, same -24px margin-top
                        // technique F02/F09 use, so this sheet reads as floating over the navy
                        // hero instead of a hard seam.
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .offset(y = -Spacing.xl)
                                    .background(
                                        MaterialTheme.colorScheme.background,
                                        RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet),
                                    ),
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            items(state.items) { row ->
                                NotificationRow(row = row, onClick = { onItemClick(row) })
                            }
                            // v1 has no load-more (see CLAUDE.md F14 scope) - when the account has
                            // more than the single page fetched (page_size=50), say so instead of
                            // silently hiding older notifications with no indication anything is
                            // missing.
                            if (state.items.size < state.total) {
                                item {
                                    Text(
                                        text = "Đang hiện ${state.items.size}/${state.total} thông báo gần nhất",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(Spacing.md)
                                                .testTag("notification_list_more_footer"),
                                    )
                                }
                            }
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

// 2-stop navy gradient (Navy900 -> Navy700), distinct from the 3-stop BrandHeroGradient F02/F09
// use - this screen's own mockup only has 2 stops, no Blue600 tail. Reuses the same primitives,
// just a different composition for this screen.
private val NotificationHeroGradient = Brush.verticalGradient(0f to Navy900, 1f to Navy700)

@Composable
private fun NotificationHeroSection(
    unreadCount: Int,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(NotificationHeroGradient)
                .statusBarsPadding()
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.xs, bottom = Spacing.xxxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
        }
        Text(
            text = "Thông báo",
            style = HeadlineTextStyle,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        if (unreadCount > 0) {
            Row(
                modifier =
                    Modifier
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(Radius.control))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(Radius.control))
                        .clickable(onClick = onMarkAllRead)
                        .padding(horizontal = Spacing.md)
                        .heightIn(min = Dimens.minTouchTarget),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = "Đọc tất cả", style = BadgeTextStyle, color = Color.White)
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
