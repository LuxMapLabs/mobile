package com.luxmap.core.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors

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
