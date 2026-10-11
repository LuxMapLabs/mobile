package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors

// Replaces OfflineBanner + ErrorBanner with one component that only differs by tone — the
// spec's component table (§3, InlineBanner row) describes exactly these two cases (quá hạn /
// ngoại tuyến) sharing one shape, colored from the existing SyncStatus pairs (FAILED / QUEUED_
// OFFLINE) instead of generic surfaceVariant or the AssetCondition.OUT pair used before.
// InlineErrorText (field-level error, a different shape) is not part of this consolidation.
enum class InlineBannerTone { OFFLINE, ERROR }

@Composable
fun InlineBanner(
    message: String,
    tone: InlineBannerTone,
    modifier: Modifier = Modifier,
) {
    val colors =
        when (tone) {
            InlineBannerTone.OFFLINE -> SyncStatus.QUEUED_OFFLINE.badgeColors()
            InlineBannerTone.ERROR -> SyncStatus.FAILED.badgeColors()
        }
    val icon =
        when (tone) {
            InlineBannerTone.OFFLINE -> Icons.Filled.CloudOff
            InlineBannerTone.ERROR -> Icons.Filled.ErrorOutline
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(Radius.control))
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = message, style = LabelTextStyle, color = colors.text)
    }
}
