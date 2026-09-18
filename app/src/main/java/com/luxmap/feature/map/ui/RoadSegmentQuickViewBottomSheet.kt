package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.map.routeColor
import com.luxmap.core.theme.Spacing
import com.luxmap.feature.map.data.RoadSegmentLine

// Quick view when tapping a surveyed route (F12/FM-36) — same "just enough to decide whether to
// go there" purpose as PoleQuickViewBottomSheet, no fault detail panel (that stays Web GIS scope,
// see buildRoadSegmentsLineLayer's comment in MapScreen.kt).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadSegmentQuickViewBottomSheet(
    segment: RoadSegmentLine,
    visiblePoleCount: Int,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            Text(text = segment.name, style = MaterialTheme.typography.titleLarge)
            Text(
                text = segment.segmentId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "$visiblePoleCount/${segment.poleCount} cột đang hiển thị",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Chiều dài: ${segment.lengthM} m",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (segment.hasActiveSegmentFault) "Tuyến đang gặp sự cố" else "Tuyến bình thường",
                style = MaterialTheme.typography.bodyMedium,
                color = routeColor(segment.hasActiveSegmentFault),
            )

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}
