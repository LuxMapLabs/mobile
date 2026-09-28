package com.luxmap.feature.survey.ui.capture

// Bước 0-4 of F04 (spec §8/C7) as explicit states. Recording carries its own warning flags
// (spec §16, P9) instead of the screen just reading "Recording" through a GPS dropout or BLE gap.
sealed interface CaptureUiState {
    data object AwaitingBleConnection : CaptureUiState

    data object Ready : CaptureUiState

    data class Recording(
        val gpsSignalLost: Boolean = false,
        val bleGapDetected: Boolean = false,
    ) : CaptureUiState

    data object Packaging : CaptureUiState

    data class Packaged(val sessionId: String) : CaptureUiState

    data class PackagingFailed(val reason: String) : CaptureUiState
}
