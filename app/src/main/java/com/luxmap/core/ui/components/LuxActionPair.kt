package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Spacing

@Composable
fun LuxActionPair(
    secondary: @Composable (Modifier) -> Unit,
    primary: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    stackActions: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val minRowWidth = 280.dp * LocalDensity.current.fontScale
        if (stackActions || maxWidth < minRowWidth) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val actionModifier = Modifier.fillMaxWidth().heightIn(min = PrimaryButtonHeight)
                secondary(actionModifier)
                primary(actionModifier)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val actionModifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = PrimaryButtonHeight)
                secondary(actionModifier)
                primary(actionModifier)
            }
        }
    }
}
