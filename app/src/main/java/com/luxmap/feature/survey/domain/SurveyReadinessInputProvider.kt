package com.luxmap.feature.survey.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.luxmap.core.camera.ExposureLockController
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessInput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// Task 11 kept CheckSurveyReadinessUseCase a pure decision function on purpose — this is where the
// actual CameraManager/StatFs/BatteryManager calls happen, verified on a real device (spec §16),
// not unit tested here (there is nothing pure left to assert once real system services are involved).
interface SurveyReadinessInputProvider {
    suspend fun gather(route: AssignedSurveyRoute): SurveyReadinessInput
}

class RealSurveyReadinessInputProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exposureLockController: ExposureLockController,
        private val locationTracker: LocationTracker,
    ) : SurveyReadinessInputProvider {
        override suspend fun gather(route: AssignedSurveyRoute): SurveyReadinessInput {
            val cameraManager = context.getSystemService(CameraManager::class.java)
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            val characteristics = cameraId?.let { cameraManager.getCameraCharacteristics(it) }
            val timestampSourceRealtime =
                characteristics?.let { exposureLockController.isTimestampSourceRealtime(it) } ?: false
            val statFs = StatFs(context.filesDir.path)
            val batteryManager = context.getSystemService(BatteryManager::class.java)

            return SurveyReadinessInput(
                cameraPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                exposureLockSupported = characteristics != null,
                timestampSourceRealtime = timestampSourceRealtime,
                gpsAvailable = locationTracker.hasLocationPermission(),
                freeStorageBytes = statFs.availableBytes,
                requiredStorageBytes = estimateRequiredStorageBytes(route),
                batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            )
        }

        // Rough estimate (average survey speed x an assumed bitrate) — refine both constants
        // against the Task 2 spike's chosen defaults once real numbers exist.
        private fun estimateRequiredStorageBytes(route: AssignedSurveyRoute): Long {
            val totalLengthMeters = route.roadSegments.sumOf { it.lengthMeters ?: 0.0 }
            val estimatedDurationSeconds = totalLengthMeters / AVERAGE_SPEED_METERS_PER_SECOND
            return (estimatedDurationSeconds * ASSUMED_BITRATE_BYTES_PER_SECOND).toLong()
        }

        private companion object {
            const val AVERAGE_SPEED_METERS_PER_SECOND = 8.3 // ~30 km/h
            const val ASSUMED_BITRATE_BYTES_PER_SECOND = 1_000_000L // 8 Mbps placeholder pending Task 2
        }
    }
