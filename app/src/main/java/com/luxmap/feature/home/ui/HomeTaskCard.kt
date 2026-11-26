package com.luxmap.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.communeLabel
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleWindow
import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface HomeTaskBadge {
    data class Sync(val state: CompletionSyncState) : HomeTaskBadge

    data class Locked(val text: String) : HomeTaskBadge

    data object Overdue : HomeTaskBadge

    data class Status(val woStatus: String) : HomeTaskBadge
}

fun homeTaskBadge(
    item: WorkOrderSummaryItem,
    syncState: CompletionSyncState?,
    today: LocalDate,
): HomeTaskBadge {
    val lock = homeScheduleLock(item, today)
    return when {
        syncState != null -> HomeTaskBadge.Sync(syncState)
        lock != null -> HomeTaskBadge.Locked(lock)
        dueDateLabel(item.dueDate, today).isOverdue -> HomeTaskBadge.Overdue
        else -> HomeTaskBadge.Status(item.woStatus)
    }
}

internal fun homeScheduleLock(
    item: WorkOrderSummaryItem,
    today: LocalDate,
): String? {
    if (item.woStatus !in setOf("assigned", "in_progress")) return null
    val window = workOrderScheduleWindow(item.scheduledDate, today)
    return when {
        window.isOverdueToComplete -> "Đã khoá · quá lịch ${DateFormatUtils.formatPlainDate(item.scheduledDate!!)}"
        item.woStatus == "assigned" && !window.canActOnSchedule ->
            "Đã khoá · mở ${DateFormatUtils.formatPlainDate(window.unlocksOn.toString())}"
        item.woStatus == "in_progress" && window.submitOpensOn != null ->
            "Đã khoá · nộp từ ${DateFormatUtils.formatPlainDate(window.submitOpensOn.toString())}"
        else -> null
    }
}

data class HomeDateLine(
    val scheduled: String?,
    val due: String?,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
)

private fun String?.isValidDate(): Boolean =
    !isNullOrBlank() &&
        try {
            LocalDate.parse(this)
            true
        } catch (e: DateTimeParseException) {
            false
        }

fun homeDateLine(
    item: WorkOrderSummaryItem,
    today: LocalDate,
): HomeDateLine {
    val scheduled =
        item.scheduledDate.takeIf { it.isValidDate() }?.let { "Làm: ${DateFormatUtils.formatPlainDate(it)}" }
    if (!item.dueDate.isValidDate()) return HomeDateLine(scheduled, null, isOverdue = false, isDueToday = false)
    val due = dueDateLabel(item.dueDate, today)
    return HomeDateLine(scheduled, due.text, isOverdue = due.isOverdue, isDueToday = due.isToday)
}

private val SYNC_STATUS =
    mapOf(
        CompletionSyncState.PENDING to SyncStatus.QUEUED_ONLINE,
        CompletionSyncState.FAILED to SyncStatus.FAILED,
        CompletionSyncState.CONFLICT to SyncStatus.CONFLICT,
    )

@Composable
private fun HomeTaskBadgeView(badge: HomeTaskBadge) {
    when (badge) {
        is HomeTaskBadge.Sync -> {
            val status = SYNC_STATUS.getValue(badge.state)
            val text = if (badge.state == CompletionSyncState.PENDING) "Đã nộp, chờ đồng bộ" else status.label()
            StatusBadge(text = text, colors = status.badgeColors(), icon = status.icon())
        }
        is HomeTaskBadge.Locked ->
            StatusBadge(
                text = badge.text,
                colors = BadgeColors(MaterialTheme.colorScheme.errorContainer, LuxTheme.colors.dueOverdue),
                icon = Icons.Rounded.Lock,
            )
        HomeTaskBadge.Overdue ->
            StatusBadge(
                text = "Quá hạn",
                colors = BadgeColors(MaterialTheme.colorScheme.errorContainer, LuxTheme.colors.dueOverdue),
                icon = Icons.Rounded.EventBusy,
            )
        is HomeTaskBadge.Status -> {
            val status = workOrderStatusFromWire(badge.woStatus)
            StatusBadge(text = status.label(), colors = status.badgeColors(), icon = status.icon())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeTaskCard(
    item: WorkOrderSummaryItem,
    syncState: CompletionSyncState?,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val kind = workOrderTaskKindFromWire(item.taskKind)
    val dates = homeDateLine(item, today)
    val badge = homeTaskBadge(item, syncState, today)
    val dueColor: Color =
        when {
            dates.isOverdue -> LuxTheme.colors.dueOverdue
            dates.isDueToday -> LuxTheme.colors.dueToday
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    LuxCard(
        onClick = onClick,
        modifier =
            Modifier.semantics {
                onClick(label = "Mở lệnh ${item.workOrderId}") {
                    onClick()
                    true
                }
            },
        contentPadding = PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = kind.icon(),
                        contentDescription = null,
                        tint = kind.badgeColors().text,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "${kind.label()} ${item.workOrderId}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HomeTaskBadgeView(badge)
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    if (item.faultCount > 0) {
                        "${item.faultCount} sự cố tại ${communeLabel(item.communeId, item.communeName)}"
                    } else {
                        communeLabel(item.communeId, item.communeName)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f).padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                val neutral = MaterialTheme.colorScheme.onSurfaceVariant
                if (dates.scheduled == null && dates.due == null) {
                    DateText(Icons.Rounded.EventAvailable, "Chưa có lịch", neutral)
                }
                dates.scheduled?.let { DateText(Icons.Rounded.EventAvailable, it, neutral) }
                dates.due?.takeUnless { badge is HomeTaskBadge.Locked }?.let {
                    DateText(
                        if (dates.isOverdue) Icons.Rounded.EventBusy else Icons.Rounded.Schedule,
                        it,
                        dueColor,
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateText(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
