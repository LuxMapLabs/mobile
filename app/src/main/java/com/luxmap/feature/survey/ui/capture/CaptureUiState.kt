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

    // gpsReadyToRecord starts false every time Ready is (re)entered - PreRecordGpsAccuracyTracker
    // has to report a fresh fix held steady for GpsAccuracyGate.PROPOSED_HOLD_DURATION_MS before
    // this flips true (BE's gate, mobile.pdf 2026-10-08), not a one-time "GPS available" check.
    // gpsAccuracyMeters is the live reading while not yet ready, so the screen can show *why* the
    // button is still disabled instead of a silent wait (review feedback, 2026-10-08).
    data class Ready(
        val gpsReadyToRecord: Boolean = false,
        val gpsAccuracyMeters: Float? = null,
    ) : CaptureUiState

    // Between pressing "Bat dau quay" and the camera actually being open with a live preview
    // (Cach A, agreed in brainstorming) - CaptureScreen keeps the same TextureView alive through
    // this state and into Recording, so the preview never flickers or resets.
    data object StartingRecording : CaptureUiState

    data class Recording(
        val gpsSignalLost: Boolean = false,
        val bleGapDetected: Boolean = false,
        val durationSeconds: Long = 0,
        val distanceMeters: Float = 0f,
        val gpsAccuracyMeters: Float? = null,
        val headingDeg: Float? = null,
        val latestLuxValue: Float? = null,
        val freeStorageBytes: Long? = null,
    ) : CaptureUiState

    data object Packaging : CaptureUiState

    data class Packaged(val sessionId: String) : CaptureUiState

    data class PackagingFailed(val reason: String) : CaptureUiState
}
