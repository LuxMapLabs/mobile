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
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import javax.inject.Singleton

// Separate from LocationHeadingRecorder (which only starts once SurveyCaptureService begins
// recording) - BE's GPS-accuracy gate (mobile.pdf, 2026-10-08) needs live fixes WHILE still on
// F04's "Ready" screen, before "Bắt đầu quay" is pressed. Same raw GPS_PROVIDER choice as
// LocationHeadingRecorder, kept intentionally separate rather than shared: this tracker's only
// job is the pre-record gate, it writes nothing to disk and is not part of the session package.
//
// Owns the GpsAccuracyGate + SystemClock reads itself (not left to CaptureViewModel) so the
// ViewModel only ever sees plain Boolean/Float? - same reasoning as ExposureLockController/
// HeadingSensor not being unit tested in isolation: this class is a thin Android system wrapper,
// verified on a real device, not with JVM unit tests (SystemClock calls are not mockable there
// without Robolectric, which this project does not use).
@Singleton
class PreRecordGpsAccuracyTracker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val locationManager = context.getSystemService(LocationManager::class.java)
        private var listener: LocationListener? = null
        private var staleWatchdog: Timer? = null

        // Written from the LocationListener (main looper) and from the stale watchdog's own
        // Timer thread - both read/write it, so it needs @Volatile for cross-thread visibility,
        // same pattern SurveyTrackRecorder already uses for the same kind of field.
        @Volatile
        private var gate = newGate()

        @Volatile
        private var lastFixElapsedRealtimeNs: Long? = null

        private val _readyToRecord = MutableStateFlow(false)
        val readyToRecord: StateFlow<Boolean> = _readyToRecord.asStateFlow()

        // Exposed so the UI can show the live number while waiting (review feedback, 2026-10-08) -
        // a disabled button with no visible reason looks broken, not just "not ready yet".
        private val _accuracyMeters = MutableStateFlow<Float?>(null)
        val accuracyMeters: StateFlow<Float?> = _accuracyMeters.asStateFlow()

        @SuppressLint("MissingPermission")
        fun start() {
            if (listener != null) return
            // Fresh gate every start() - a "good since" timestamp from a previous Ready visit
            // must not carry over and make the very next fix look already-held-long-enough.
            gate = newGate()
            lastFixElapsedRealtimeNs = null
            _readyToRecord.value = false
            _accuracyMeters.value = null

            val newListener =
                LocationListener { location ->
                    val nowNs = SystemClock.elapsedRealtimeNanos()
                    lastFixElapsedRealtimeNs = nowNs
                    // A fix with no accuracy estimate must never count as a perfect reading -
                    // Location.accuracy defaults to 0.0f when hasAccuracy() is false, which would
                    // otherwise satisfy any threshold (review feedback, 2026-10-08).
                    val accuracyM = if (location.hasAccuracy()) location.accuracy else null
                    _accuracyMeters.value = accuracyM
                    _readyToRecord.value = accuracyM?.let { gate.onAccuracyUpdate(it, nowNs) } ?: false
                }
            listener = newListener
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                0f,
                newListener,
                Looper.getMainLooper(),
            )

            // A fix stream that stops entirely (walked indoors, lost the sky - exactly what raw
            // GPS_PROVIDER does more than fused did) must not leave readyToRecord sitting on a
            // stale true forever: nothing else re-evaluates it once fixes stop arriving (review
            // feedback, 2026-10-08). Same 10s staleness idea as SurveyTrackRecorder's GPS-lost check.
            staleWatchdog =
                Timer(true).apply {
                    scheduleAtFixedRate(
                        object : TimerTask() {
                            override fun run() {
                                val lastFix = lastFixElapsedRealtimeNs
                                val staleMs = lastFix?.let { (SystemClock.elapsedRealtimeNanos() - it) / 1_000_000 }
                                if (lastFix == null || staleMs!! > STALE_THRESHOLD_MS) {
                                    _readyToRecord.value = false
                                    _accuracyMeters.value = null
                                    // A fresh gate so the fix that eventually breaks a stale
                                    // streak has to hold for the full duration again, not get
                                    // credited for the silent gap in between.
                                    gate = newGate()
                                }
                            }
                        },
                        STALE_CHECK_INTERVAL_MS,
                        STALE_CHECK_INTERVAL_MS,
                    )
                }
        }

        fun stop() {
            listener?.let { locationManager.removeUpdates(it) }
            listener = null
            staleWatchdog?.cancel()
            staleWatchdog = null
            lastFixElapsedRealtimeNs = null
            _readyToRecord.value = false
            _accuracyMeters.value = null
        }

        private fun newGate() =
            GpsAccuracyGate(
                thresholdMeters = GpsAccuracyGate.PROPOSED_THRESHOLD_METERS,
                holdDurationMs = GpsAccuracyGate.PROPOSED_HOLD_DURATION_MS,
            )

        private companion object {
            const val LOCATION_INTERVAL_MS = 1_000L
            const val STALE_CHECK_INTERVAL_MS = 1_000L
            const val STALE_THRESHOLD_MS = 10_000L
        }
    }
