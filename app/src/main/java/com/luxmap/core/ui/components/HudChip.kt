package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

// §3 HUD chip: a small pill floating directly over the full-bleed camera preview (F04) - each
// chip carries its own background instead of a shared status-bar strip, since the spec's F04
// section describes HUD chips sitting individually over the live feed, not inside one bar.
private val HudChipBackground = Color(0x991A1C22)

@Composable
fun HudChip(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = LabelTextStyle,
        color = Color.White,
        modifier =
            modifier
                .background(HudChipBackground, RoundedCornerShape(Radius.chip))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
