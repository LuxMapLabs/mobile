package com.luxmap.feature.map.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.luxmap.R
import com.luxmap.core.map.MapMarkerColors
import com.luxmap.core.map.routeColor
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing

@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = Dimens.minTouchTarget)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .semantics { stateDescription = if (expanded) "Đang mở" else "Đang đóng" }
                        .padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(text = "Chú giải", style = MaterialTheme.typography.labelLarge)
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    MAP_STATUS_ORDER.forEach { IconLegendRow(it.markerIconResId(), it.mapLabel()) }
                    IconLegendRow(R.drawable.ic_poi_badge, "Gần trường, cầu")
                    RouteLegendRow(hasActiveSegmentFault = false, label = "Tuyến bình thường")
                    RouteLegendRow(hasActiveSegmentFault = true, label = "Tuyến đang có sự cố")
                    IconLegendRow(R.drawable.ic_cabinet_normal, "Tủ điện điều khiển")
                    LineLegendRow(color = MapMarkerColors.FeederPowered, label = "Sơ đồ cấp nguồn (Tủ → Cột)")
                    LineLegendRow(
                        color = MapMarkerColors.FeederUnpowered,
                        label = "Lộ ngắt điện / Mất nguồn",
                        dashed = true,
                    )
                    LineLegendRow(color = MapMarkerColors.CommuneOutline, label = "Ranh giới xã")
                }
            }
        }
    }
}

@Composable
private fun IconLegendRow(
    iconResId: Int,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(modifier = Modifier.size(LEGEND_ICON_SIZE), contentAlignment = Alignment.Center) {
            Image(painter = painterResource(iconResId), contentDescription = null)
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RouteLegendRow(
    hasActiveSegmentFault: Boolean,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(modifier = Modifier.size(LEGEND_ICON_SIZE), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .width(LEGEND_ICON_SIZE)
                        .height(4.dp)
                        .background(routeColor(hasActiveSegmentFault)),
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LineLegendRow(
    color: Color,
    label: String,
    dashed: Boolean = false,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Canvas(modifier = Modifier.size(LEGEND_ICON_SIZE)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 3.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

private val LEGEND_ICON_SIZE = 24.dp
