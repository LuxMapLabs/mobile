package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Night
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

// §3 CaptureHudChip: a small pill floating directly over the full-bleed camera preview (F04) -
// each chip carries its own background instead of a shared status-bar strip, since the spec's
// F04 section describes HUD chips sitting individually over the live feed, not inside one bar.
// Background is exactly the spec's row: "nền Night 78%".
private val HudChipBackground = Night.copy(alpha = 0.78f)

@Composable
fun HudChip(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
) {
    Text(
        text = text,
        style = LabelTextStyle,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .background(HudChipBackground, RoundedCornerShape(Radius.chip))
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}
