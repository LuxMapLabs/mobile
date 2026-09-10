package com.luxmap.feature.map.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.luxmap.R
import com.luxmap.core.map.markerColor
import com.luxmap.core.map.routeColor
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.label

private val LEGEND_ORDER =
    listOf(AssetCondition.NORMAL, AssetCondition.DIM, AssetCondition.OUT, AssetCondition.UNKNOWN)

@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .padding(Spacing.sm),
    ) {
        LEGEND_ORDER.forEach { condition ->
            Row(modifier = Modifier.padding(vertical = Spacing.xs / 2)) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(condition.markerColor()),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(text = condition.label(), style = MaterialTheme.typography.bodySmall)
            }
        }

        RouteLegendRow(hasActiveSegmentFault = false, label = "Tuyến bình thường")
        RouteLegendRow(hasActiveSegmentFault = true, label = "Tuyến đang gặp sự cố")

        BadgeLegendRow(iconResId = R.drawable.ic_poi_badge, label = "Gần khu vực nhạy cảm")
        BadgeLegendRow(iconResId = R.drawable.ic_iot_badge, label = "Có nốt IoT")
    }
}

@Composable
private fun BadgeLegendRow(
    iconResId: Int,
    label: String,
) {
    Row(modifier = Modifier.padding(vertical = Spacing.xs / 2)) {
        Image(
            painter = painterResource(iconResId),
            contentDescription = null,
            modifier = Modifier.size(10.dp),
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
    Row(modifier = Modifier.padding(vertical = Spacing.xs / 2)) {
        Box(
            modifier =
                Modifier
                    .width(10.dp)
                    .height(3.dp)
                    .background(routeColor(hasActiveSegmentFault)),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
