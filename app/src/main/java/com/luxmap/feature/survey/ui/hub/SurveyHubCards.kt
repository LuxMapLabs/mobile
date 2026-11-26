package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.ui.components.LuxActionPair
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxDestructiveButton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleLabel
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleWindow
import java.time.LocalDate

private fun HubItemKind.taskKind(): WorkOrderTaskKind =
    if (this == HubItemKind.SURVEY_ROUTE) WorkOrderTaskKind.SURVEY else WorkOrderTaskKind.POLE_GPS_UPDATE

private fun HubItemKind.label(): String = if (this == HubItemKind.SURVEY_ROUTE) "Tuyến khảo sát" else "Đo GPS cột"

private val SYNC =
    mapOf(
        CompletionSyncState.PENDING to SyncStatus.QUEUED_ONLINE,
        CompletionSyncState.FAILED to SyncStatus.FAILED,
        CompletionSyncState.CONFLICT to SyncStatus.CONFLICT,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardHeader(item: HubItem) {
    val kind = item.kind.taskKind()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(
                imageVector = kind.icon(),
                contentDescription = null,
                tint = kind.badgeColors().text,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "${item.kind.label()} ${item.order.workOrderId}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val sync = item.syncState?.let { SYNC.getValue(it) }
        if (sync != null) {
            StatusBadge(text = sync.label(), colors = sync.badgeColors(), icon = sync.icon())
        } else {
            val status = workOrderStatusFromWire(item.order.woStatus)
            StatusBadge(text = status.label(), colors = status.badgeColors(), icon = status.icon())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardBody(
    item: HubItem,
    today: LocalDate,
) {
    Text(
        text = item.order.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = workOrderScheduleLabel(item.kind.taskKind()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.md),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = scheduleValue(item.order.scheduledDate, today),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (workOrderScheduleWindow(item.order.scheduledDate, today).isOverdueToComplete) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = LuxTheme.colors.dueOverdue,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Đã qua lịch",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuxTheme.colors.dueOverdue,
                    )
                }
            }
        }
    }
}

@Composable
fun SurveyRouteCard(
    item: HubItem,
    today: LocalDate,
    isStarting: Boolean,
    isCancelled: Boolean,
    isHighlighted: Boolean,
    onStart: () -> Unit,
    onCapture: () -> Unit,
    onRequestCancellation: () -> Unit,
) {
    LuxCard(selected = isHighlighted) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardHeader(item)
            CardBody(item, today)
            when {
                item.order.woStatus == "in_progress" ->
                    PrimaryButton(
                        text = "Chụp ảnh/video từng cột",
                        onClick = onCapture,
                        modifier = Modifier.fillMaxWidth(),
                    )
                item.order.woStatus != "assigned" -> Unit
                isCancelled ->
                    Text(
                        text = "Đã gửi yêu cầu hủy, chờ duyệt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> {
                    val gate = startGate(item.order.scheduledDate, today)
                    gate.hint()?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LuxActionPair(
                        secondary = { actionModifier ->
                            LuxDestructiveButton(
                                text = "Yêu cầu hủy",
                                onClick = onRequestCancellation,
                                filled = false,
                                enabled = gate == StartGate.Open,
                                modifier = actionModifier,
                            )
                        },
                        primary = { actionModifier ->
                            PrimaryButton(
                                text = if (isStarting) "Đang bắt đầu…" else "Bắt đầu",
                                onClick = onStart,
                                enabled = gate == StartGate.Open && !isStarting,
                                modifier = actionModifier,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun PoleGpsCard(
    item: HubItem,
    today: LocalDate,
    isHighlighted: Boolean,
    onOpen: () -> Unit,
) {
    LuxCard(onClick = onOpen, selected = isHighlighted) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardHeader(item)
            CardBody(item, today)
        }
    }
}

@Composable
fun PendingSyncCard(
    item: HubItem,
    today: LocalDate,
) {
    LuxCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardHeader(item)
            CardBody(item, today)
        }
    }
}
