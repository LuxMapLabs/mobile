package com.luxmap.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxTheme

@Composable
fun LuxLampIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val lumen = LuxTheme.brand.lumen
    val ground = MaterialTheme.colorScheme.primaryContainer
    val pole = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(size).clearAndSetSemantics {}) {
        val s = this.size.minDimension
        drawCircle(ground, radius = s * 0.5f)
        val baseX = s * 0.40f
        val armY = s * 0.26f
        val armEnd = s * 0.64f
        val path =
            Path().apply {
                moveTo(baseX, s * 0.82f)
                lineTo(baseX, armY + s * 0.06f)
                quadraticTo(baseX, armY, baseX + s * 0.06f, armY)
                lineTo(armEnd, armY)
            }
        drawPath(path, pole, style = Stroke(width = s * 0.035f, cap = StrokeCap.Round))
        val cone =
            Path().apply {
                moveTo(armEnd - s * 0.02f, armY + s * 0.05f)
                lineTo(s * 0.50f, s * 0.72f)
                lineTo(s * 0.78f, s * 0.72f)
                close()
            }
        drawPath(cone, lumen.copy(alpha = 0.35f))
        drawRoundRect(
            color = lumen,
            topLeft = Offset(armEnd - s * 0.08f, armY - s * 0.03f),
            size = Size(s * 0.18f, s * 0.09f),
            cornerRadius = CornerRadius(s * 0.04f),
        )
    }
}
