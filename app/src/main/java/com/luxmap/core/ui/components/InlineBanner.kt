package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BodyTextStyle
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors

enum class InlineBannerTone { OFFLINE, ERROR }

@Composable
fun InlineBanner(
    message: String,
    tone: InlineBannerTone,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val colors =
        when (tone) {
            InlineBannerTone.OFFLINE -> SyncStatus.QUEUED_OFFLINE.badgeColors()
            InlineBannerTone.ERROR -> AssetCondition.OUT.badgeColors(isDark)
        }
    val icon =
        when (tone) {
            InlineBannerTone.OFFLINE -> Icons.Filled.CloudOff
            InlineBannerTone.ERROR -> Icons.Filled.ErrorOutline
        }
    val textStyle =
        when (tone) {
            InlineBannerTone.OFFLINE -> LabelTextStyle
            InlineBannerTone.ERROR -> BodyTextStyle
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
        Text(text = message, style = textStyle, color = colors.text)
    }
}
