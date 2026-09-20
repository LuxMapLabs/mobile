package com.luxmap.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luxmap.R
import com.luxmap.core.map.routeColor
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.label

private val LEGEND_ORDER =
    listOf(AssetCondition.NORMAL, AssetCondition.DIM, AssetCondition.OUT, AssetCondition.UNKNOWN)

// Every legend visual sits in the same 20dp column.  The marker resources are 24dp vectors,
// while route strokes and status badges are naturally smaller; without this fixed frame the
// text starts at different x positions and the legend looks uneven.
private val LEGEND_ICON_SIZE = 20.dp

// Collapsed by default (F12/FM-37: "legend thu gọn ở đáy trái, có thể bấm mở/đóng; không luôn
// chiếm diện tích lớn") — the header row itself (label + chevron) stays the same size whether
// expanded or not, only the detail rows below it show/hide.
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .padding(horizontal = Spacing.sm),
    ) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = Dimens.minTouchTarget)
                    .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Chú giải", style = MaterialTheme.typography.labelLarge)
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                contentDescription = if (expanded) "Thu gọn chú giải" else "Mở chú giải",
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(bottom = Spacing.sm)) {
                LEGEND_ORDER.forEach { condition ->
                    Row(
                        modifier = Modifier.defaultMinSize(minHeight = LEGEND_ICON_SIZE),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(condition.markerIconResId()),
                            contentDescription = null,
                            modifier = Modifier.size(LEGEND_ICON_SIZE),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(text = condition.label(), style = MaterialTheme.typography.bodySmall)
                    }
                }

                RouteLegendRow(hasActiveSegmentFault = false, label = "Tuyến bình thường")
                RouteLegendRow(hasActiveSegmentFault = true, label = "Tuyến đang gặp sự cố")

                BadgeLegendRow(iconResId = R.drawable.ic_poi_badge, label = "Gần khu vực nhạy cảm")
                BadgeLegendRow(iconResId = R.drawable.ic_iot_badge, label = "Có thiết bị IoT")
            }
        }
    }
}

@Composable
private fun BadgeLegendRow(
    iconResId: Int,
    label: String,
) {
    Row(
        modifier = Modifier.defaultMinSize(minHeight = LEGEND_ICON_SIZE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier.size(LEGEND_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RouteLegendRow(
    hasActiveSegmentFault: Boolean,
    label: String,
) {
    Row(
        modifier = Modifier.defaultMinSize(minHeight = LEGEND_ICON_SIZE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(LEGEND_ICON_SIZE)
                    .height(3.dp)
                    .background(routeColor(hasActiveSegmentFault)),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

private fun AssetCondition.markerIconResId(): Int =
    when (this) {
        AssetCondition.NORMAL -> R.drawable.ic_marker_normal
        AssetCondition.DIM -> R.drawable.ic_marker_dim
        AssetCondition.OUT -> R.drawable.ic_marker_out
        AssetCondition.UNKNOWN -> R.drawable.ic_marker_unknown
    }
