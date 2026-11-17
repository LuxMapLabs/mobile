package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
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
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            QuickViewHeader(title = segment.name, subtitle = segment.segmentId, onClose = onDismiss)
            LuxBanner(
                message = if (segment.hasActiveSegmentFault) "Tuyến đang có sự cố" else "Tuyến bình thường",
                tone = if (segment.hasActiveSegmentFault) LuxBannerTone.ERROR else LuxBannerTone.SUCCESS,
            )
            Column {
                QuickViewFactRow(label = "Cột đang hiển thị", value = "$visiblePoleCount/${segment.poleCount}")
                QuickViewFactRow(label = "Chiều dài", value = "${segment.lengthM} m")
            }
        }
    }
}
