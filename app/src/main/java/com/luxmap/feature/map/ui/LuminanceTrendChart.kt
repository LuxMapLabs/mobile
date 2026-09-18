package com.luxmap.feature.map.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Amber500
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Rose600
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.Success600
import com.luxmap.feature.map.data.PoleDetail
import com.luxmap.feature.map.data.PoleLuminancePoint

// Design System v3.0.1 "Xu hướng độ sáng" card — real Canvas line chart, replaces the plain
// list of luminance points the previous version showed. Tách khỏi PoleDetailScreen.kt vì vẽ
// biểu đồ là logic khác hẳn phần layout còn lại (cùng lý do PoleQuickViewBottomSheet.kt tách
// khỏi MapScreen.kt).
@Composable
fun LuminanceTrendCard(
    detail: PoleDetail,
    onViewFullHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Trim once here so the chart and the caption always agree on the same window — the chart
    // used to draw only the last 7 points while the caption read the full history, so a caption
    // could say "trong 30 ngày" over a chart that only showed 7.
    val points = detail.luminanceHistory.takeLast(MAX_TREND_POINTS)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.lg),
    ) {
        Text(text = "Xu hướng độ sáng", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        LuminanceTrendChart(history = points, status = detail.fixtureStatus)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = trendCaption(points),
            style = MaterialTheme.typography.labelLarge,
        )
        TextButton(onClick = onViewFullHistory) {
            Text(text = "Xem lịch sử đầy đủ →", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LuminanceTrendChart(
    history: List<PoleLuminancePoint>,
    status: AssetCondition,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(
                text = "Chưa có dữ liệu độ sáng",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Caller (LuminanceTrendCard) already trims to at most MAX_TREND_POINTS — draw exactly
    // what's passed in, never assume exactly 7.
    val points = history
    val values = points.map { it.baselineRatio * 100.0 }
    val latestPercent = values.last().toInt()
    val lineColor = status.trendColor()

    // Baseline (100%) and threshold (80%) are always part of the scale, so max-min is at
    // least 20 even when every data point is identical or there's only 1 point — no need to
    // special-case "divide by zero" for the vertical scale. coerceAtLeast is just a defensive
    // floor in case that invariant ever changes.
    val maxVal = maxOf(values.max(), BASELINE_PERCENT)
    val minVal = minOf(values.min(), THRESHOLD_PERCENT)
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .semantics { contentDescription = "Độ sáng gần nhất: $latestPercent%" },
    ) {
        fun yFor(percent: Double): Float = (size.height * (1f - ((percent - minVal) / range))).toFloat()

        drawLine(
            color = GridLineColor,
            start = Offset(0f, yFor(BASELINE_PERCENT)),
            end = Offset(size.width, yFor(BASELINE_PERCENT)),
            strokeWidth = GRID_LINE_WIDTH.toPx(),
            pathEffect = dashEffect,
        )
        drawLine(
            color = Amber500,
            start = Offset(0f, yFor(THRESHOLD_PERCENT)),
            end = Offset(size.width, yFor(THRESHOLD_PERCENT)),
            strokeWidth = GRID_LINE_WIDTH.toPx(),
            pathEffect = dashEffect,
        )

        if (points.size == 1) {
            // A single point has no line to draw (0 segments) — just show where it sits.
            drawCircle(color = lineColor, radius = END_DOT_RADIUS.toPx(), center = Offset(size.width, yFor(values[0])))
        } else {
            val stepX = size.width / (points.size - 1)
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = Offset(i * stepX, yFor(values[i])),
                    end = Offset((i + 1) * stepX, yFor(values[i + 1])),
                    strokeWidth = TREND_LINE_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            val lastX = (points.size - 1) * stepX
            drawCircle(color = lineColor, radius = END_DOT_RADIUS.toPx(), center = Offset(lastX, yFor(values.last())))
        }
    }
}

// Computed straight from the data (first vs. last point in the visible window) instead of a
// separate hardcoded string per status — a pole trending back up would say "Tăng", not "Giảm",
// even though only DIM/OUT show this card in practice.
private fun trendCaption(history: List<PoleLuminancePoint>): String {
    if (history.size < 2) return "Chưa đủ dữ liệu để tính xu hướng"
    val deltaPercent = ((history.last().baselineRatio - history.first().baselineRatio) * 100).toInt()
    return when {
        deltaPercent <= -TREND_FLAT_THRESHOLD_PERCENT -> "Giảm ${-deltaPercent}% trong ${history.size} ngày gần nhất"
        deltaPercent >= TREND_FLAT_THRESHOLD_PERCENT -> "Tăng $deltaPercent% trong ${history.size} ngày gần nhất"
        else -> "Ổn định, không có dấu hiệu suy giảm"
    }
}

private fun AssetCondition.trendColor(): Color =
    when (this) {
        AssetCondition.NORMAL -> Success600
        AssetCondition.DIM -> Amber500
        AssetCondition.OUT -> Rose600
        AssetCondition.UNKNOWN -> Gray500
    }

private val GridLineColor = Color(0xFFCBD5E1)
private val CHART_HEIGHT = 96.dp
private val GRID_LINE_WIDTH = 1.dp
private val TREND_LINE_WIDTH = 2.5.dp
private val END_DOT_RADIUS = 4.5.dp
private const val MAX_TREND_POINTS = 7
private const val BASELINE_PERCENT = 100.0
private const val THRESHOLD_PERCENT = 80.0
private const val TREND_FLAT_THRESHOLD_PERCENT = 5
