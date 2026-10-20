package com.luxmap.feature.notification.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.BadgeTextStyle
import com.luxmap.core.theme.Blue600
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.DisabledContent
import com.luxmap.core.theme.Gray50
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Navy700
import com.luxmap.core.theme.Navy900
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.DueTodayColor
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
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
    viewModel: NotificationListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    NotificationListScreen(
        state = uiState,
        onBack = onBack,
        onRetry = viewModel::onRetry,
        onMarkAllRead = viewModel::onMarkAllRead,
        onFilterSelected = viewModel::onFilterSelected,
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
    onFilterSelected: (NotificationFilter) -> Unit = {},
    onItemClick: (NotificationListRowState) -> Unit = {},
) {
    // The hero (back button, title, conditional "Đọc tất cả") is always shown, same as the
    // mockup's own structure - it sits outside every sc-if branch there, not just the Success
    // one. unreadCount is 0 for the other 3 states, which already hides the mark-all pill.
    val unreadCount = (state as? NotificationListUiState.Success)?.items?.count { !it.isRead } ?: 0
    Scaffold { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            NotificationHeroSection(unreadCount = unreadCount, onBack = onBack, onMarkAllRead = onMarkAllRead)
            // Overlaps the hero's bottom edge by Spacing.xl, same -24px margin-top technique
            // F02/F09 use, so this sheet reads as floating over the navy hero instead of a hard
            // seam - shared by every state, not just Success.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .offset(y = -Spacing.xl)
                        .clip(RoundedCornerShape(topStart = Radius.sheet, topEnd = Radius.sheet))
                        .background(MaterialTheme.colorScheme.background),
            ) {
                when (state) {
                    is NotificationListUiState.Loading -> NotificationSkeletonList()

                    is NotificationListUiState.Success ->
                        NotificationSuccessContent(
                            state = state,
                            onFilterSelected = onFilterSelected,
                            onItemClick = onItemClick,
                        )

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
                            iconColors =
                                BadgeColors(
                                    AssetCondition.OUT.badgeColors(isDark = false).background,
                                    Danger600,
                                ),
                            title = "Không tải được danh sách thông báo",
                            subtitle = state.message,
                            action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
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
        NotificationFilterRow(
            filter = state.filter,
            allCount = state.items.size,
            unreadCount = unreadCount,
            onFilterSelected = onFilterSelected,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        val filteredItems =
            if (state.filter == NotificationFilter.UNREAD) {
                state.items.filter { !it.isRead }
            } else {
                state.items
            }
        if (filteredItems.isEmpty()) {
            // Only reachable with "Chưa đọc" selected and nothing unread left - the whole-screen
            // Empty state is for "no notifications at all", this is "no notifications match
            // this filter".
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = SyncStatus.DONE.badgeColors().text,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "Đã đọc hết thông báo",
                    style = BodyStrongTextStyle,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                groupByDay(filteredItems).forEach { (dayLabel, rows) ->
                    item(key = "group_$dayLabel") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(
                                text = dayLabel,
                                style = LabelTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = Spacing.xs),
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            RoundedCornerShape(Radius.block),
                                        ).border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(Radius.block),
                                        ),
                            ) {
                                rows.forEachIndexed { index, row ->
                                    if (index > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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

private val NotificationSkeletonTileCorner = Radius.chip

@Composable
private fun NotificationSkeletonList() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg).testTag("notification_list_loading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(5) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.block))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.block))
                        .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(NotificationTileSize)
                            .background(
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(NotificationSkeletonTileCorner),
                            ),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.4f)
                                .height(10.dp)
                                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.9f)
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    )
                }
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

private val NotificationTileSize = 40.dp

@Composable
private fun NotificationFilterRow(
    filter: NotificationFilter,
    allCount: Int,
    unreadCount: Int,
    onFilterSelected: (NotificationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        NotificationFilterChip(
            label = "Tất cả",
            count = allCount,
            selected = filter == NotificationFilter.ALL,
            onClick = { onFilterSelected(NotificationFilter.ALL) },
        )
        NotificationFilterChip(
            label = "Chưa đọc",
            count = unreadCount,
            selected = filter == NotificationFilter.UNREAD,
            onClick = { onFilterSelected(NotificationFilter.UNREAD) },
        )
    }
}

@Composable
private fun NotificationFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) Navy700 else MaterialTheme.colorScheme.surface
    val contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) Navy700 else MaterialTheme.colorScheme.outline
    val badgeBg = if (selected) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier =
            Modifier
                .heightIn(min = Dimens.minTouchTarget)
                .background(containerColor, RoundedCornerShape(Dimens.radiusPill))
                .border(1.dp, borderColor, RoundedCornerShape(Dimens.radiusPill))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(text = label, style = BodyStrongTextStyle, color = contentColor)
        Box(
            modifier =
                Modifier
                    .background(badgeBg, CircleShape)
                    .padding(horizontal = Spacing.xs, vertical = 1.dp),
        ) {
            Text(text = count.toString(), style = BadgeTextStyle, color = contentColor)
        }
    }
}

@Composable
private fun NotificationRow(
    row: NotificationListRowState,
    dayLabel: String,
    onClick: () -> Unit,
) {
    val type = notificationTypeFromWire(row.type)
    val tileColors = type.tileColors()
    val isNavigable = notificationNavigationTarget(row) != null
    // "Trước đó" rows show the date (the day label alone doesn't say which day), rows already
    // grouped under "Hôm nay"/"Hôm qua" only need the time - repeating the date would be noise.
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
                .clickable(onClick = onClick)
                .background(
                    if (row.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(NotificationTileSize)
                    .background(tileColors.background, RoundedCornerShape(Radius.chip)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = type.icon(), contentDescription = null, tint = tileColors.text)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = type.label(), style = LabelTextStyle, color = tileColors.text)
                Text(text = "·", style = LabelTextStyle, color = MaterialTheme.colorScheme.outline)
                Text(text = timeLabel, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = row.title,
                style = if (row.isRead) MaterialTheme.typography.bodyLarge else BodyStrongTextStyle,
            )
            Text(
                text = row.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!row.isRead) {
                Box(
                    modifier =
                        Modifier
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .testTag("notification_unread_dot_${row.notificationId}"),
                )
            } else {
                Spacer(Modifier.size(9.dp))
            }
            if (isNavigable) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
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

// type -> tile background/icon color (mockup's KIND table). Every pair reuses an existing
// core/theme color instead of a new hex: red = AssetCondition.OUT's background + Danger600,
// neutral = Gray50 + DisabledContent, teal = SyncStatus.DONE's pair, dusty blue =
// WorkOrderStatus.ASSIGNED's background + Blue600 (the exact hex BrandHeroGradient's end stop
// already uses), amber = AssetCondition.DIM's background + DueTodayColor.
private fun NotificationType.tileColors(): BadgeColors =
    when (this) {
        NotificationType.WORK_ORDER_ASSIGNED, NotificationType.WORK_ORDER_RESCHEDULED ->
            WorkOrderStatus.ASSIGNED.badgeColors()
        NotificationType.WORK_ORDER_OVERDUE,
        NotificationType.WORK_ORDER_RETURNED,
        NotificationType.SURVEY_RETURNED,
        NotificationType.SURVEY_PROCESSING_FAILED,
        -> BadgeColors(AssetCondition.OUT.badgeColors(isDark = false).background, Danger600)
        NotificationType.WORK_ORDER_UNASSIGNED, NotificationType.WORK_ORDER_CANCELLED ->
            BadgeColors(Gray50, DisabledContent)
        NotificationType.WORK_ORDER_COMPLETED, NotificationType.WORK_ORDER_VERIFIED ->
            SyncStatus.DONE.badgeColors()
        NotificationType.SURVEY_READY_FOR_REVIEW ->
            BadgeColors(WorkOrderStatus.ASSIGNED.badgeColors().background, Blue600)
        NotificationType.FAULT_REPORTED, NotificationType.CITIZEN_REPORT_RECEIVED ->
            BadgeColors(AssetCondition.DIM.badgeColors(isDark = false).background, DueTodayColor)
        NotificationType.UNKNOWN -> AssetCondition.UNKNOWN.badgeColors(isDark = false)
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
