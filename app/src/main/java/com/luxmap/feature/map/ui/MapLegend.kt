package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import com.luxmap.core.map.markerColor
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
    }
}
