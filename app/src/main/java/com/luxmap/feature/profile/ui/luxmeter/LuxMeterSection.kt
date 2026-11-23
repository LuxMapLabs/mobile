package com.luxmap.feature.profile.ui.luxmeter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxBottomSheet
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxDestructiveButton
import com.luxmap.core.ui.components.LuxSecondaryButton
import com.luxmap.core.ui.components.LuxSectionHeader
import com.luxmap.core.ui.components.PrimaryButton

@Composable
fun LuxMeterSection(
    pairedDevice: LuxDevice?,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        LuxSectionHeader(title = "Thiết bị đo")
        LuxCard {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouchTarget)
                        .clickable(role = Role.Button, onClick = onOpen)
                        .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Sensors,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Cảm biến lux", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = pairedDevice?.displayName() ?: "Chưa ghép nối",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LuxMeterPairingSheet(
    state: LuxMeterPairingUiState,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onSelect: (LuxDevice) -> Unit,
    onUnpair: () -> Unit,
) {
    LuxBottomSheet(title = "Cảm biến lux", onDismiss = onDismiss) {
        when {
            !state.hasPermission -> {
                Text(
                    text = "Cần quyền Bluetooth để tìm và kết nối máy đo lux.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(text = "Cấp quyền", onClick = onRequestPermission, modifier = Modifier.fillMaxWidth())
            }
            !state.isBluetoothOn -> {
                Text(
                    text = "Bluetooth đang tắt. Bật Bluetooth để tìm máy đo lux.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                PrimaryButton(text = "Bật Bluetooth", onClick = onEnableBluetooth, modifier = Modifier.fillMaxWidth())
            }
            else -> DeviceLists(state = state, onScan = onScan, onSelect = onSelect, onUnpair = onUnpair)
        }
    }
}

@Composable
private fun DeviceLists(
    state: LuxMeterPairingUiState,
    onScan: () -> Unit,
    onSelect: (LuxDevice) -> Unit,
    onUnpair: () -> Unit,
) {
    Text(
        text = "Chọn máy đo LuxMap_ESP32. Máy đo cần được bật và ở gần điện thoại.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    DeviceGroup(
        title = "Đã ghép nối với điện thoại",
        devices = state.bondedDevices,
        selected = state.pairedDevice,
        onSelect = onSelect,
    )
    val newDevices = state.foundDevices.filterNot { found -> state.bondedDevices.any { it.address == found.address } }
    DeviceGroup(title = "Tìm thấy gần đây", devices = newDevices, selected = state.pairedDevice, onSelect = onSelect)
    if (state.bondedDevices.isEmpty() && newDevices.isEmpty() && !state.isScanning) {
        Text(
            text = "Chưa thấy thiết bị nào. Bật máy đo rồi bấm Tìm thiết bị.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.isScanning) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(text = "Đang tìm thiết bị…", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LuxSecondaryButton(
            text = "Tìm thiết bị",
            onClick = onScan,
            leadingIcon = Icons.Filled.BluetoothSearching,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (state.pairedDevice != null) {
        LuxDestructiveButton(
            text = "Bỏ ghép nối",
            onClick = onUnpair,
            filled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeviceGroup(
    title: String,
    devices: List<LuxDevice>,
    selected: LuxDevice?,
    onSelect: (LuxDevice) -> Unit,
) {
    if (devices.isEmpty()) return
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        devices.forEachIndexed { index, device ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow(device = device, isSelected = device.address == selected?.address, onClick = { onSelect(device) })
        }
    }
}

@Composable
private fun DeviceRow(
    device: LuxDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.displayName(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Đang dùng",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun LuxDevice.displayName(): String = name?.takeIf { it.isNotBlank() } ?: "Thiết bị không tên"
