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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import java.time.Instant

// onDarkHero switches to the style the F02 redesign mockup uses on top of its navy hero
// (translucent white chip, white text, dark-theme badge-color icon tint) instead of the
// flat light-style chip from status.badgeColors() - SyncPill has no other caller today
// (only HomeScreen.kt), so this does not affect any other screen.
@Composable
fun SyncPill(
    status: SyncStatus,
    lastSyncedAt: Instant?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDarkHero: Boolean = false,
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
    val backgroundColor = if (onDarkHero) Color.White.copy(alpha = 0.1f) else colors.background
    val iconTint = if (onDarkHero) status.onDarkHeroIconColor() else colors.text
    val textColor = if (onDarkHero) Color.White else colors.text
    Row(
        modifier =
            modifier
                .background(backgroundColor, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = LabelTextStyle, color = textColor)
    }
}

// Reuses the existing dark-theme AssetCondition badge text colors instead of inventing new
// hex values - NORMAL/DIM/OUT's dark text already are the exact teal/amber/salmon the mockup
// wants for done/syncing/failed, since both come from the same "light accent on dark" need.
private fun SyncStatus.onDarkHeroIconColor(): Color =
    when (this) {
        SyncStatus.DONE -> AssetCondition.NORMAL.badgeColors(isDark = true).text
        SyncStatus.SYNCING -> AssetCondition.DIM.badgeColors(isDark = true).text
        SyncStatus.FAILED, SyncStatus.CONFLICT -> AssetCondition.OUT.badgeColors(isDark = true).text
        SyncStatus.QUEUED_OFFLINE, SyncStatus.QUEUED_ONLINE -> Color.White.copy(alpha = 0.75f)
    }
