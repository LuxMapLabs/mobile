package com.luxmap.feature.survey.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.location.LocationManager
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.luxmap.core.camera.CameraSelector
import com.luxmap.core.camera.ExposureLockController
import com.luxmap.core.common.StorageMonitor
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
        private val storageMonitor: StorageMonitor,
    ) : SurveyReadinessInputProvider {
        override suspend fun gather(route: AssignedSurveyRoute): SurveyReadinessInput {
            val cameraManager = context.getSystemService(CameraManager::class.java)
            // Same selection as VideoCaptureSession, so the checklist and the recorder can never
            // mean different cameras.
            val cameraId = CameraSelector.pickBackCameraId(cameraManager)
            val characteristics = cameraId?.let { cameraManager.getCameraCharacteristics(it) }
            val timestampSourceRealtime =
                characteristics?.let { exposureLockController.isTimestampSourceRealtime(it) } ?: false

            // The exposure lock is the one hard gate against recording unusable auto-exposure video,
            // so it must check what ExposureLockController.applyTo() really needs: the MANUAL_SENSOR
            // capability (ISO, exposure time, frame duration, focus distance) plus CONTROL_AE_MODE_OFF.
            // A camera that has neither cannot hold a locked exposure at all.
            val manualSensorSupported =
                characteristics
                    ?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
            val aeModeOffSupported =
                characteristics
                    ?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                    ?.contains(CameraMetadata.CONTROL_AE_MODE_OFF) == true

            // Location permission alone is not enough: the user can grant it and still leave the
            // system Location toggle off, which would give us a gps_track.ndjson with a header and
            // no data. The server needs that track to match video frames to poles.
            val locationManager = context.getSystemService(LocationManager::class.java)
            val gpsProviderEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

            // The rotation vector sensor is used to record heading data during survey, separate from
            // GPS bearing (spec §8). Not all devices have this sensor.
            val sensorManager = context.getSystemService(SensorManager::class.java)
            val rotationVectorSensorAvailable =
                sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

            val batteryManager = context.getSystemService(BatteryManager::class.java)

            return SurveyReadinessInput(
                cameraPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                exposureLockSupported = manualSensorSupported && aeModeOffSupported,
                timestampSourceRealtime = timestampSourceRealtime,
                gpsAvailable = locationTracker.hasLocationPermission() && gpsProviderEnabled,
                headingAvailable = rotationVectorSensorAvailable,
                freeStorageBytes = storageMonitor.freeBytes(),
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
