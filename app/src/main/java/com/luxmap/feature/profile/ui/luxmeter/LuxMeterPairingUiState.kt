package com.luxmap.feature.profile.ui.luxmeter

import com.luxmap.core.ble.LuxDevice

data class LuxMeterPairingUiState(
    val pairedDevice: LuxDevice? = null,
    val isSheetOpen: Boolean = false,
    val hasPermission: Boolean = false,
    val isBluetoothOn: Boolean = true,
    val foundDevices: List<LuxDevice> = emptyList(),
    val isScanning: Boolean = false,
)
