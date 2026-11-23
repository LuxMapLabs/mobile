package com.luxmap.feature.survey.ui.polephoto.lux

import java.time.Instant

sealed interface LuxCardUiState {
    data object Collapsed : LuxCardUiState

    data object NotPaired : LuxCardUiState

    data object PermissionMissing : LuxCardUiState

    data object BluetoothOff : LuxCardUiState

    data object Connecting : LuxCardUiState

    data class Live(
        val latestLux: Double?,
        val sampleCount: Int,
        val requiredSamples: Int,
        val stableLux: Double?,
    ) : LuxCardUiState

    data class Recorded(
        val lux: Double,
        val measuredAt: Instant,
    ) : LuxCardUiState

    data object Disconnected : LuxCardUiState

    data object Unreachable : LuxCardUiState
}
