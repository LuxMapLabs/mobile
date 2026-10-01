package com.luxmap.feature.survey.domain.usecase

import javax.inject.Inject

// Raw values gathered by the caller (CameraManager, StorageManager, BatteryManager, GPS permission
// check) — kept separate from the decision below so the decision itself needs no Android mocking.
data class SurveyReadinessInput(
    val cameraPermissionGranted: Boolean,
    val exposureLockSupported: Boolean,
    val timestampSourceRealtime: Boolean,
    val gpsAvailable: Boolean,
    val headingAvailable: Boolean,
    val freeStorageBytes: Long,
    val requiredStorageBytes: Long,
    val batteryPercent: Int,
)

data class SurveyReadinessResult(
    val cameraPermissionGranted: Boolean,
    val exposureLockSupported: Boolean,
    val timestampSourceRealtime: Boolean,
    val gpsAvailable: Boolean,
    val headingAvailable: Boolean,
    val freeStorageBytes: Long,
    val requiredStorageBytes: Long,
    val batteryPercent: Int,
) {
    // TEMPORARY policy (spec §10): timestampSourceRealtime == false is a hard block, treated the
    // same as an unsupported device, until C8's open point #8 is resolved with the project owner.
    val isReady: Boolean
        get() =
            cameraPermissionGranted &&
                exposureLockSupported &&
                timestampSourceRealtime &&
                gpsAvailable &&
                headingAvailable &&
                freeStorageBytes >= requiredStorageBytes &&
                batteryPercent >= MIN_BATTERY_PERCENT

    companion object {
        const val MIN_BATTERY_PERCENT = 20
    }
}

class CheckSurveyReadinessUseCase
    @Inject
    constructor() {
        operator fun invoke(input: SurveyReadinessInput): SurveyReadinessResult =
            SurveyReadinessResult(
                cameraPermissionGranted = input.cameraPermissionGranted,
                exposureLockSupported = input.exposureLockSupported,
                timestampSourceRealtime = input.timestampSourceRealtime,
                gpsAvailable = input.gpsAvailable,
                headingAvailable = input.headingAvailable,
                freeStorageBytes = input.freeStorageBytes,
                requiredStorageBytes = input.requiredStorageBytes,
                batteryPercent = input.batteryPercent,
            )
    }
