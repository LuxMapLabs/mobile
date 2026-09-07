package com.luxmap.feature.map.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing

// Bật/tắt lớp hiển thị (F12: "Bật/tắt lớp hiển thị") — Cột đèn theo tình trạng và Tuyến đã
// khảo sát (RoadSegment). Mỗi hàng là 1 vùng chạm tối thiểu 48dp (một tay, có thể đeo găng tay).
@Composable
fun MapLayerToggle(
    showFixtures: Boolean,
    onShowFixturesChange: (Boolean) -> Unit,
    showRoadSegments: Boolean,
    onShowRoadSegmentsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusMedium))
                .padding(vertical = Spacing.xs),
    ) {
        LayerToggleRow(
            label = "Cột đèn theo tình trạng",
            checked = showFixtures,
            onCheckedChange = onShowFixturesChange,
        )
        LayerToggleRow(
            label = "Tuyến đã khảo sát",
            checked = showRoadSegments,
            onCheckedChange = onShowRoadSegmentsChange,
        )
    }
}

@Composable
private fun LayerToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .defaultMinSize(minHeight = Dimens.minTouchTarget)
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}
