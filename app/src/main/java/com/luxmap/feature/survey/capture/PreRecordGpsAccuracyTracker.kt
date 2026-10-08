package com.luxmap.feature.survey.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.luxmap.feature.survey.domain.GpsAccuracyGate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Separate from LocationHeadingRecorder (which only starts once SurveyCaptureService begins
// recording) - BE's GPS-accuracy gate (mobile.pdf, 2026-10-08) needs live fixes WHILE still on
// F04's "Ready" screen, before "Bắt đầu quay" is pressed. Same raw GPS_PROVIDER choice as
// LocationHeadingRecorder, kept intentionally separate rather than shared: this tracker's only
// job is the pre-record gate, it writes nothing to disk and is not part of the session package.
//
// Owns the GpsAccuracyGate + SystemClock reads itself (not left to CaptureViewModel) so the
// ViewModel only ever sees a plain Boolean - same reasoning as ExposureLockController/HeadingSensor
// not being unit tested in isolation: this class is a thin Android system wrapper, verified on a
// real device, not with JVM unit tests (SystemClock calls are not mockable there without
// Robolectric, which this project does not use).
@Singleton
class PreRecordGpsAccuracyTracker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val locationManager = context.getSystemService(LocationManager::class.java)
        private var listener: LocationListener? = null
        private var gate: GpsAccuracyGate? = null

        private val _readyToRecord = MutableStateFlow(false)
        val readyToRecord: StateFlow<Boolean> = _readyToRecord.asStateFlow()

        @SuppressLint("MissingPermission")
        fun start() {
            if (listener != null) return
            // Fresh gate every start() - a "good since" timestamp from a previous Ready visit
            // must not carry over and make the very next fix look already-held-long-enough.
            val freshGate =
                GpsAccuracyGate(
                    thresholdMeters = GpsAccuracyGate.PROPOSED_THRESHOLD_METERS,
                    holdDurationMs = GpsAccuracyGate.PROPOSED_HOLD_DURATION_MS,
                )
            gate = freshGate
            val newListener =
                LocationListener { location ->
                    val nowNs = SystemClock.elapsedRealtimeNanos()
                    _readyToRecord.value = freshGate.onAccuracyUpdate(location.accuracy, nowNs)
                }
            listener = newListener
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                0f,
                newListener,
                Looper.getMainLooper(),
            )
        }

        fun stop() {
            listener?.let { locationManager.removeUpdates(it) }
            listener = null
            gate = null
            _readyToRecord.value = false
        }

        private companion object {
            const val LOCATION_INTERVAL_MS = 1_000L
        }
    }
