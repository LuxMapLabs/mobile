package com.luxmap.feature.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.IotStatus
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBanner
import com.luxmap.core.ui.components.LuxBannerTone
import com.luxmap.core.ui.components.LuxLoadingRegion
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.map.data.CabinetFeeder
import com.luxmap.feature.map.data.FeederMode
import com.luxmap.feature.map.data.FeederPower

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CabinetQuickViewBottomSheet(
    selection: CabinetSelection,
    communeName: String?,
    onDismiss: () -> Unit,
    onNavigate: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val cabinet = selection.cabinet

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            QuickViewHeader(title = cabinet.name, subtitle = communeName, onClose = onDismiss)
            Column {
                cabinet.feeders.forEach { FeederRow(it) }
                QuickViewFactRow(label = "Thiết bị IoT", value = "${cabinet.iotNodeCount} thiết bị IoT")
            }
            when (val topology = selection.topology) {
                TopologyState.Loading ->
                    LuxLoadingRegion(description = "Đang tải sơ đồ cấp nguồn") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator()
                        }
                    }
                is TopologyState.Error -> LuxBanner(message = topology.message, tone = LuxBannerTone.ERROR)
                is TopologyState.Loaded -> {
                    val notes =
                        listOfNotNull(
                            topology.topology.unlocatedPoleCount
                                .takeIf { it > 0 }
                                ?.let { "$it cột chưa có vị trí" },
                            topology.topology.unroutedPoleCount
                                .takeIf { it > 0 }
                                ?.let { "$it cột chưa gắn tuyến" },
                        )
                    if (notes.isNotEmpty()) {
                        LuxBanner(message = notes.joinToString("\n"), tone = LuxBannerTone.INFO)
                    }
                }
            }
            LuxSecondaryButton(
                text = "Đường đi",
                onClick = onNavigate ?: { openInMaps(context, cabinet.lat, cabinet.lng) },
                leadingIcon = Icons.Filled.Navigation,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FeederRow(feeder: CabinetFeeder) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Lộ ${feeder.feederId}", style = MaterialTheme.typography.bodyLarge)
            Text(
                text =
                    listOfNotNull(feeder.relayNo?.let { "Rơ-le $it" }, "Chế độ: ${feeder.mode.label()}")
                        .joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val (iotStatus, icon) =
            when (feeder.power) {
                FeederPower.ON -> IotStatus.ONLINE to Icons.Filled.Power
                FeederPower.OFF -> IotStatus.OFFLINE to Icons.Filled.PowerOff
                FeederPower.UNKNOWN -> IotStatus.NONE to Icons.AutoMirrored.Filled.HelpOutline
            }
        StatusBadge(text = feeder.power.label(), colors = LuxTheme.status.iot.getValue(iotStatus), icon = icon)
    }
}

private fun FeederMode.label(): String =
    when (this) {
        FeederMode.ON -> "Bật"
        FeederMode.OFF -> "Tắt"
        FeederMode.AUTO -> "Tự động"
        FeederMode.UNKNOWN -> "Chưa rõ"
    }

private fun FeederPower.label(): String =
    when (this) {
        FeederPower.ON -> "Có nguồn"
        FeederPower.OFF -> "Mất nguồn"
        FeederPower.UNKNOWN -> "Chưa rõ"
    }
