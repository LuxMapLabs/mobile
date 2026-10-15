package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import java.time.Instant

@Composable
fun SyncPill(
    status: SyncStatus,
    lastSyncedAt: Instant?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = status.badgeColors()
    val icon =
        when (status) {
            SyncStatus.DONE -> Icons.Filled.CloudDone
            SyncStatus.SYNCING -> Icons.Filled.CloudSync
            SyncStatus.FAILED, SyncStatus.CONFLICT -> Icons.Filled.ErrorOutline
            SyncStatus.QUEUED_OFFLINE, SyncStatus.QUEUED_ONLINE -> Icons.Filled.CloudOff
        }
    val text = if (lastSyncedAt != null) DateFormatUtils.formatTimeOnly(lastSyncedAt) else status.label()
    Row(
        modifier =
            modifier
                .background(colors.background, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = LabelTextStyle, color = colors.text)
    }
}
