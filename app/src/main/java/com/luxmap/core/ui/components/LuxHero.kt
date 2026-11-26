package com.luxmap.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxBrandColors
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing

@Composable
fun LuxHero(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    verticalSpacing: Dp = Spacing.md,
    applyStatusBarInset: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val brand = LuxTheme.brand
    val topInsetPx = if (applyStatusBarInset) WindowInsets.statusBars.getTop(LocalDensity.current) else 0
    LightStatusBarIcons()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(brand.hero)
                .drawBehind { drawHeroArt(brand, topInsetPx.toFloat()) }
                .then(if (applyStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
                .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

private fun DrawScope.drawHeroArt(
    brand: LuxBrandColors,
    topInset: Float,
) {
    val w = size.width
    val h = size.height
    drawCircle(brand.heroShapeA, radius = h * 0.9f, center = Offset(w + h * 0.1f, -h * 0.1f))
    drawCircle(brand.heroShapeB, radius = h * 0.42f, center = Offset(w - h * 0.2f, h * 0.65f))
    val baseX = w - 84.dp.toPx()
    val armY = topInset + 14.dp.toPx()
    val armEnd = baseX + 26.dp.toPx()
    val poleBottom = minOf(h, armY + 76.dp.toPx())
    val pole =
        Path().apply {
            moveTo(baseX, poleBottom)
            lineTo(baseX, armY + 8.dp.toPx())
            quadraticTo(baseX, armY, baseX + 8.dp.toPx(), armY)
            lineTo(armEnd, armY)
        }
    drawPath(pole, brand.onHeroVariant, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    drawRoundRect(
        color = brand.lumen,
        topLeft = Offset(armEnd - 8.dp.toPx(), armY - 3.dp.toPx()),
        size = Size(16.dp.toPx(), 8.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
}

@Composable
fun LuxTabHero(
    title: String,
    unreadNotificationCount: Int,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val brand = LuxTheme.brand
    LuxHero(
        modifier = modifier,
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.lg),
        applyStatusBarInset = true,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(top = Spacing.sm, end = Spacing.xl)) {
                Text(
                    text = "LUXMAP VẬN HÀNH",
                    style = MaterialTheme.typography.labelMedium,
                    color = brand.onHeroVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = brand.onHero,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = brand.onHeroVariant,
                    )
                }
            }
            CompositionLocalProvider(LocalContentColor provides brand.onHero) {
                NotificationAction(unreadCount = unreadNotificationCount, onClick = onNotificationsClick)
            }
        }
        if (content != null) {
            Column(
                modifier = Modifier.padding(end = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}

@Composable
fun LuxStatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                    .semantics(mergeDescendants = true) {},
        ) {
            Text(text = "$value", style = MaterialTheme.typography.titleLarge, color = valueColor)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
