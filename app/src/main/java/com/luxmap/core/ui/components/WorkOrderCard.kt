package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class DueLabel(val text: String, val isToday: Boolean)

fun dueDateLabel(
    dueDate: String?,
    today: LocalDate = LocalDate.now(),
): DueLabel {
    if (dueDate.isNullOrBlank()) return DueLabel("Chưa có hạn", isToday = false)
    val parsed =
        try {
            LocalDate.parse(dueDate)
        } catch (e: DateTimeParseException) {
            null
        } ?: return DueLabel("Chưa có hạn", isToday = false)
    return if (parsed == today) {
        DueLabel("Hạn hôm nay", isToday = true)
    } else {
        DueLabel("Hạn ${DateFormatUtils.formatPlainDate(dueDate)}", isToday = false)
    }
}

private val DueTodayColor = Color(0xFFB45309)

@Composable
fun WorkOrderCard(
    workOrderId: String,
    woStatus: String,
    dueDate: String?,
    priorityScore: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = workOrderStatusFromWire(woStatus)
    val colors = status.badgeColors()
    val due = dueDateLabel(dueDate)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        StatusTile(icon = status.icon(), background = colors.background, tint = colors.text)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = workOrderId,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(text = status.label(), colors = colors)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = due.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (due.isToday) DueTodayColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (priorityScore != null) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        text = "Ưu tiên ${priorityScore.toInt()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun StatusTile(
    icon: ImageVector,
    background: Color,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .background(background, RoundedCornerShape(Dimens.radiusMedium)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
