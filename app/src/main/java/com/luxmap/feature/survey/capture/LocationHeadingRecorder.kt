// app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt
package com.luxmap.feature.survey.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.luxmap.core.location.GpsSignalState
import com.luxmap.core.location.HeadingSensor
import com.luxmap.core.location.SurveyTrackRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

class LocationHeadingRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackRecorder: SurveyTrackRecorder,
        private val headingSensor: HeadingSensor,
    ) {
        private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        private val sensorManager = context.getSystemService(SensorManager::class.java)
        private var locationCallback: LocationCallback? = null
        private var sensorListener: SensorEventListener? = null
        private var tickTimer: Timer? = null

        private val _gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
        val gpsSignalState: StateFlow<GpsSignalState> = _gpsSignalState.asStateFlow()

        @SuppressLint("MissingPermission")
        fun start(
            gpsWriter: NdjsonLogWriter,
            headingWriter: NdjsonLogWriter,
        ) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS).build()
            val callback =
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val location = result.lastLocation ?: return
                        val point = trackRecorder.onLocationUpdate(location)
                        val json =
                            """{"elapsed_realtime_ns":${point.elapsedRealtimeNs},""" +
                                """"lat":${point.lat},"lng":${point.lng},""" +
                                """"accuracy_m":${point.accuracyM},""" +
                                """"gps_bearing_deg":${point.gpsBearingDeg ?: "null"},""" +
                                """"speed_mps":${point.speedMps ?: "null"}}"""
                        gpsWriter.appendLine(json)
                    }
                }
            locationCallback = callback
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

            // NOTE for the real-device checklist (spec §16): confirm SensorEvent.timestamp for
            // TYPE_ROTATION_VECTOR is in the same elapsedRealtimeNanos timebase on every supported
            // device — documented as true since API 26, but device-specific drivers have been known
            // to diverge from spec.
            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val sample = headingSensor.headingFromRotationVector(event.values, event.timestamp)
                        headingWriter.appendLine(
                            """{"elapsed_realtime_ns":${sample.elapsedRealtimeNs},""" +
                                """"heading_deg":${sample.headingDeg}}""",
                        )
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
            sensorListener = listener
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)

            tickTimer =
                Timer(true).apply { // isDaemon = true
                    scheduleAtFixedRate(
                        object : TimerTask() {
                            override fun run() {
                                _gpsSignalState.value = trackRecorder.onTick(SystemClock.elapsedRealtimeNanos())
                            }
                        },
                        GPS_SIGNAL_CHECK_INTERVAL_MS,
                        GPS_SIGNAL_CHECK_INTERVAL_MS,
                    )
                }
        }

        fun stop() {
            locationCallback?.let { fusedClient.removeLocationUpdates(it) }
            sensorListener?.let { sensorManager.unregisterListener(it) }
            tickTimer?.cancel()
        }

        private companion object {
            const val LOCATION_INTERVAL_MS = 1_000L
            const val GPS_SIGNAL_CHECK_INTERVAL_MS = 1_000L
        }
    }
