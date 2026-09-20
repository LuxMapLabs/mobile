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
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors

// The Design System has no separate error-banner colors yet, so this uses the "Hỏng/Tắt" (out)
// pair, which is the red pair of the system, instead of choosing new colors here.

// Short banner for an error that does not belong to one field (for example a locked account or
// no connection to the server). The icon means the error is not shown by color only. The live
// region makes TalkBack read the message when it appears.
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = AssetCondition.OUT.badgeColors(isSystemInDarkTheme())
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(Dimens.radiusLarge))
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = Icons.Filled.ErrorOutline, contentDescription = null, tint = colors.text)
        Spacer(Modifier.width(Spacing.sm))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
}

// Error text shown under a field (use it in the `supportingText` of a text field). The size is
// body (16sp) and not the small default of Material, because an error is guidance the user must
// read. Icon and text, not only color.
@Composable
fun InlineErrorText(
    message: String,
    modifier: Modifier = Modifier,
) {
    val color = AssetCondition.OUT.badgeColors(isSystemInDarkTheme()).text
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(Spacing.xl),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
