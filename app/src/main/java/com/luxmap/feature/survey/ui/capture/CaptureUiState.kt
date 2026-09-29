package com.luxmap.feature.survey.ui.capture

import com.luxmap.core.ble.LuxDevice

// Bước 0-4 of F04 (spec §8/C7) as explicit states. Recording carries its own warning flags
// (spec §16, P9) instead of the screen just reading "Recording" through a GPS dropout or BLE gap.
sealed interface CaptureUiState {
    // Trying a remembered device from a past session, or a device the user just picked from the
    // list. deviceName is null only for a remembered device saved before it had a name.
    data class Connecting(val deviceName: String?) : CaptureUiState

    // devices grows as the scan finds more of them - the list is shown live, not after the whole
    // scan window finishes.
    data class Scanning(val devices: List<LuxDevice> = emptyList()) : CaptureUiState

    // The scan window ended with an empty list - not the same as still scanning.
    data object ScanTimedOut : CaptureUiState

    data object Ready : CaptureUiState

    data class Recording(
        val gpsSignalLost: Boolean = false,
        val bleGapDetected: Boolean = false,
    ) : CaptureUiState

    data object Packaging : CaptureUiState

    data class Packaged(val sessionId: String) : CaptureUiState

    data class PackagingFailed(val reason: String) : CaptureUiState
}
