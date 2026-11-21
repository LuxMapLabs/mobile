package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.PoleIdentificationDetails
import com.luxmap.feature.workorder.ui.detail.OrderPolesUiState

@Composable
internal fun OrderPolesSection(
    state: OrderPolesUiState,
    onRetry: () -> Unit,
    onOpenMap: (String, Double?, Double?, Boolean) -> Unit,
) {
    LuxCard {
        val count = (state as? OrderPolesUiState.Success)?.poles?.size ?: 0
        Text("Cột cần đến ($count)", style = MaterialTheme.typography.titleMedium)
        when (state) {
            OrderPolesUiState.Loading -> Text("Đang tải danh sách cột…")
            OrderPolesUiState.Empty -> Text("Chưa có cột trong lệnh này")
            is OrderPolesUiState.Error -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("Thử lại") }
            }
            is OrderPolesUiState.Success -> {
                if (state.isStale) Text("Danh sách đã lưu · chưa cập nhật", style = MaterialTheme.typography.bodySmall)
                state.poles.forEachIndexed { index, pole ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(pole.poleId, style = MaterialTheme.typography.titleSmall)
                        if (pole.lat != null && pole.lng != null) {
                            Text("${pole.lat}, ${pole.lng}", style = MaterialTheme.typography.bodySmall)
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
                            onOpenMap = { onOpenMap(pole.poleId, pole.lat, pole.lng, false) },
                        )
                    }
                }
            }
        }
    }
}
