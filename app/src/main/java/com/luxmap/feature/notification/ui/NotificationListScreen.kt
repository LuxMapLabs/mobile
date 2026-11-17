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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxChildTopBar
import com.luxmap.core.ui.components.LuxEmptyState
import com.luxmap.core.ui.components.LuxErrorKind
import com.luxmap.core.ui.components.LuxErrorState
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.LuxSegment
import com.luxmap.core.ui.components.LuxSegmented
import com.luxmap.core.ui.components.LuxSkeleton
import com.luxmap.core.ui.components.LuxTextButton
import com.luxmap.feature.notification.data.NotificationType
import com.luxmap.feature.notification.data.notificationTypeFromWire
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

@Composable
fun NotificationListRoute(
    onBack: () -> Unit,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
    onOpenSurveyHub: (workOrderId: String) -> Unit = {},
    viewModel: NotificationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigate.collect { event ->
            when (event) {
                is NotificationNavigationEvent.OpenWorkOrderDetail -> onOpenWorkOrder(event.workOrderId)
                is NotificationNavigationEvent.OpenSurveyHub -> onOpenSurveyHub(event.workOrderId)
            }
        }
    }

    NotificationListScreen(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onMarkAllRead = viewModel::onMarkAllRead,
        onFilterSelected = viewModel::onFilterSelected,
        onItemClick = viewModel::onItemClick,
    )
}

@Composable
fun NotificationListScreen(
    state: NotificationListUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onFilterSelected: (NotificationFilter) -> Unit = {},
    onItemClick: (NotificationListRowState) -> Unit = {},
) {
    val unreadCount = (state as? NotificationListUiState.Success)?.items?.count { !it.isRead } ?: 0
    Scaffold(
        topBar = {
            LuxChildTopBar(
                title = "Thông báo",
                onBack = onBack,
                actions = {
                    if (unreadCount > 0) {
                        LuxTextButton(text = "Đọc tất cả", onClick = onMarkAllRead)
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (state) {
                    is NotificationListUiState.Loading ->
                        LuxLoadingRegion(
                            description = "Đang tải thông báo",
                            modifier = Modifier.padding(Spacing.lg),
                        ) {
                            LuxSkeleton(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.small,
                            )
                            repeat(5) {
                                LuxSkeleton(
                                    modifier = Modifier.fillMaxWidth().height(72.dp),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        }

                    is NotificationListUiState.Success ->
                        NotificationSuccessContent(
                            state = state,
                            onFilterSelected = onFilterSelected,
                            onItemClick = onItemClick,
                        )

                    is NotificationListUiState.Empty ->
                        LuxEmptyState(
                            title = "Chưa có thông báo nào",
                            message = "Thông báo về việc được giao, khảo sát và sự cố sẽ xuất hiện ở đây.",
                            icon = Icons.Filled.NotificationsNone,
                        )

                    is NotificationListUiState.Error ->
                        LuxErrorState(
                            onRetry = onRetry,
                            kind = if (state.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                            title = "Không tải được thông báo",
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
    }
}

@Composable
private fun NotificationSuccessContent(
    state: NotificationListUiState.Success,
    onFilterSelected: (NotificationFilter) -> Unit,
    onItemClick: (NotificationListRowState) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val unreadCount = state.items.count { !it.isRead }
        LuxSegmented(
            segments =
                listOf(
                    LuxSegment(NotificationFilter.ALL, "Tất cả (${state.items.size})"),
                    LuxSegment(NotificationFilter.UNREAD, "Chưa đọc ($unreadCount)"),
                ),
            selected = state.filter,
            onSelect = onFilterSelected,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        val filteredItems =
            if (state.filter == NotificationFilter.UNREAD) {
                state.items.filter { !it.isRead }
            } else {
                state.items
            }
        if (filteredItems.isEmpty()) {
            LuxEmptyState(title = "Đã đọc hết thông báo", message = "Không còn thông báo chưa đọc.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                groupByDay(filteredItems).forEach { (dayLabel, rows) ->
                    item(key = "group_$dayLabel") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            LuxSectionHeader(title = dayLabel)
                            LuxCard(contentPadding = PaddingValues(0.dp)) {
                                Column {
                                    rows.forEachIndexed { index, row ->
                                        if (index > 0) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                        NotificationRow(
                                            row = row,
                                            dayLabel = dayLabel,
                                            onClick = { onItemClick(row) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                // v1 has no load-more (see CLAUDE.md F14 scope) - when the account has more
                // than the single page fetched (page_size=50), say so instead of silently
                // hiding older notifications with no indication anything is missing.
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
    }
}

private val NotificationTileSize = 40.dp

@Composable
private fun NotificationRow(
    row: NotificationListRowState,
    dayLabel: String,
    onClick: () -> Unit,
) {
    val type = notificationTypeFromWire(row.type)
    val kindColors = LuxTheme.status.kind
    val isNavigable = notificationNavigationTarget(row) != null
    val timeLabel =
        if (dayLabel == DAY_GROUP_OLDER) {
            DateFormatUtils.formatIsoDateOnly(row.createdAt)
        } else {
            DateFormatUtils.formatIsoTimeOnly(row.createdAt)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .then(
                    if (isNavigable || !row.isRead) {
                        Modifier.clickable(
                            onClickLabel = if (isNavigable) "Mở" else "Đánh dấu đã đọc",
                            role = if (isNavigable) Role.Button else null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ).semantics { if (!row.isRead) stateDescription = "chưa đọc" }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier.size(
                    NotificationTileSize,
                ).background(kindColors.background, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = type.icon(), contentDescription = null, tint = kindColors.text)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = "${type.label()}\n$timeLabel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (row.isRead) FontWeight.Normal else FontWeight.SemiBold,
            )
            Text(
                text = row.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (!row.isRead) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("notification_unread_dot_${row.notificationId}"),
                )
            }
            if (isNavigable) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("notification_chevron_${row.notificationId}"),
                )
            }
        }
    }
}

// type -> icon (mockup section 6a). Each subtype gets its own icon now instead of grouping
// several wire values onto one generic icon - matches the mockup's KIND table exactly.
private fun NotificationType.icon(): ImageVector =
    when (this) {
        NotificationType.WORK_ORDER_ASSIGNED -> Icons.Filled.Assignment
        NotificationType.WORK_ORDER_RESCHEDULED -> Icons.Filled.Event
        NotificationType.WORK_ORDER_OVERDUE -> Icons.Filled.Schedule
        NotificationType.WORK_ORDER_RETURNED -> Icons.Filled.ErrorOutline
        NotificationType.WORK_ORDER_UNASSIGNED -> Icons.Filled.Autorenew
        NotificationType.WORK_ORDER_CANCELLED -> Icons.Filled.Cancel
        NotificationType.WORK_ORDER_COMPLETED -> Icons.Filled.CheckCircle
        NotificationType.WORK_ORDER_VERIFIED -> Icons.Filled.Verified
        NotificationType.SURVEY_RETURNED,
        NotificationType.SURVEY_READY_FOR_REVIEW,
        NotificationType.SURVEY_PROCESSING_FAILED,
        -> Icons.Filled.Videocam
        NotificationType.FAULT_REPORTED -> Icons.Filled.ReportProblem
        NotificationType.CITIZEN_REPORT_RECEIVED -> Icons.Filled.Campaign
        NotificationType.UNKNOWN -> Icons.Filled.Notifications
    }

// type -> short Vietnamese category label shown next to the time (mockup's "kind" field) -
// a category, not the notification's own title/body.
private fun NotificationType.label(): String =
    when (this) {
        NotificationType.WORK_ORDER_ASSIGNED -> "Giao việc"
        NotificationType.WORK_ORDER_RESCHEDULED -> "Đổi lịch"
        NotificationType.WORK_ORDER_OVERDUE -> "Quá hạn"
        NotificationType.WORK_ORDER_RETURNED -> "Trả lại"
        NotificationType.WORK_ORDER_UNASSIGNED -> "Thu hồi"
        NotificationType.WORK_ORDER_CANCELLED -> "Đã huỷ"
        NotificationType.WORK_ORDER_COMPLETED -> "Hoàn thành"
        NotificationType.WORK_ORDER_VERIFIED -> "Đã nghiệm thu"
        NotificationType.SURVEY_RETURNED,
        NotificationType.SURVEY_READY_FOR_REVIEW,
        NotificationType.SURVEY_PROCESSING_FAILED,
        -> "Khảo sát"
        NotificationType.FAULT_REPORTED -> "Sự cố"
        NotificationType.CITIZEN_REPORT_RECEIVED -> "Phản ánh"
        NotificationType.UNKNOWN -> "Thông báo"
    }

private const val DAY_GROUP_TODAY = "Hôm nay"
private const val DAY_GROUP_YESTERDAY = "Hôm qua"
private const val DAY_GROUP_OLDER = "Trước đó"
private val DAY_GROUP_ORDER = listOf(DAY_GROUP_TODAY, DAY_GROUP_YESTERDAY, DAY_GROUP_OLDER)

// Buckets rows by calendar day relative to "now" (mockup section 6a groups by day, not by
// elapsed time) - a row with an unparseable createdAt falls into "Trước đó" rather than
// crashing the screen, same safety convention as DateFormatUtils' own parse functions.
private fun groupByDay(items: List<NotificationListRowState>): List<Pair<String, List<NotificationListRowState>>> {
    val today = LocalDate.now()

    fun dayLabel(createdAt: String): String {
        val date =
            try {
                Instant.parse(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (e: DateTimeParseException) {
                return DAY_GROUP_OLDER
            }
        return when (date) {
            today -> DAY_GROUP_TODAY
            today.minusDays(1) -> DAY_GROUP_YESTERDAY
            else -> DAY_GROUP_OLDER
        }
    }
    val grouped = items.groupBy { dayLabel(it.createdAt) }
    return DAY_GROUP_ORDER.mapNotNull { label -> grouped[label]?.let { label to it } }
}
