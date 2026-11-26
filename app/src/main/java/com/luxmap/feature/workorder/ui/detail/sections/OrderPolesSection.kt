package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.PoleIdentificationDetails
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.workorder.ui.detail.OrderPolesUiState

// GET /work-orders/{id}/poles returns the poles of the assigned segments plus the poles holding the
// order's faults, so for an inspection with no fault this is the whole segment — not "the poles to
// visit". The poles that hold this order's faults are badged and listed first.
internal fun List<PoleListItem>.faultPolesFirst(faultPoleIds: Set<String>): List<PoleListItem> =
    sortedBy { it.poleId !in faultPoleIds }

@Composable
internal fun OrderPolesSection(
    state: OrderPolesUiState,
    faultPoleIds: Set<String>,
    onRetry: () -> Unit,
) {
    LuxCard {
        val count = (state as? OrderPolesUiState.Success)?.poles?.size ?: 0
        Text("Cột trong phạm vi lệnh ($count)", style = MaterialTheme.typography.titleMedium)
        when (state) {
            OrderPolesUiState.Loading -> Text("Đang tải danh sách cột…")
            OrderPolesUiState.Empty -> Text("Chưa có cột trong lệnh này")
            is OrderPolesUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Thử lại") }
            }
            is OrderPolesUiState.Success -> {
                if (state.isStale) Text("Danh sách đã lưu, chưa cập nhật", style = MaterialTheme.typography.bodySmall)
                val withFault = state.poles.count { it.poleId in faultPoleIds }
                if (withFault > 0) {
                    Text("$withFault cột có sự cố của lệnh, xếp đầu", style = MaterialTheme.typography.bodySmall)
                }
                state.poles.faultPolesFirst(faultPoleIds).forEachIndexed { index, pole ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(pole.poleId, style = MaterialTheme.typography.titleSmall)
                        if (pole.poleId in faultPoleIds) {
                            StatusBadge(
                                text = "Có sự cố của lệnh",
                                colors = LuxTheme.status.asset.getValue(AssetCondition.OUT),
                            )
                        }
                        PoleIdentificationDetails(
                            poleId = pole.poleId,
                            position = pole.position,
                            segmentId = pole.segmentId,
                            note = pole.note,
                            fixtureType = pole.fixtureType,
                            lampWatt = pole.lampWatt,
                            lampCount = pole.lampCount,
                            fixtureCount = pole.fixtureCount,
                            nearSensitivePoi = pole.nearSensitivePoi,
                            lat = pole.lat,
                            lng = pole.lng,
                            onOpenMap = {},
                            showLocation = false,
                        )
                    }
                }
            }
        }
    }
}
