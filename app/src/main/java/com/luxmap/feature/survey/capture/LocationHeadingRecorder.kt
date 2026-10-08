package com.luxmap.feature.survey.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.luxmap.core.location.GpsSignalState
import com.luxmap.core.location.SurveyTrackRecorder
import com.luxmap.core.location.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

// BE asked for raw GNSS fixes (LocationManager.GPS_PROVIDER), not fused/blended location, so a
// fix indoors or from cell towers never looks like a real on-route position in the track BE
// matches video frames against.
fun buildGpsTrackLine(
    point: TrackPoint,
    sampleNo: Int,
): String =
    """{"sample_no":$sampleNo,"phone_elapsed_ns":"${point.elapsedRealtimeNs}",""" +
        """"lat":${point.lat},"lng":${point.lng},"accuracy_m":${point.accuracyM},""" +
        """"heading_deg":${point.gpsBearingDeg ?: "null"},"speed_mps":${point.speedMps ?: "null"},""" +
        """"provider":"gps"}"""

class LocationHeadingRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackRecorder: SurveyTrackRecorder,
    ) {
        private val locationManager = context.getSystemService(LocationManager::class.java)
        private var locationListener: LocationListener? = null
        private var tickTimer: Timer? = null
        private var sampleNo = 0

        private val _gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
        val gpsSignalState: StateFlow<GpsSignalState> = _gpsSignalState.asStateFlow()

        private val _livePoint = MutableStateFlow<TrackPoint?>(null)
        val livePoint: StateFlow<TrackPoint?> = _livePoint.asStateFlow()

        val distanceMeters: StateFlow<Float> get() = trackRecorder.totalDistanceMeters

        @SuppressLint("MissingPermission")
        fun start(gpsWriter: NdjsonLogWriter) {
            sampleNo = 0
            val listener =
                LocationListener { location: Location ->
                    val point = trackRecorder.onLocationUpdate(location)
                    _livePoint.value = point
                    gpsWriter.appendLine(buildGpsTrackLine(point, sampleNo))
                    sampleNo++
                }
            locationListener = listener
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )

            tickTimer =
                Timer(true).apply {
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
            locationListener?.let { locationManager.removeUpdates(it) }
            tickTimer?.cancel()
        }

        private companion object {
            const val LOCATION_INTERVAL_MS = 1_000L
            const val GPS_SIGNAL_CHECK_INTERVAL_MS = 1_000L
        }
    }
