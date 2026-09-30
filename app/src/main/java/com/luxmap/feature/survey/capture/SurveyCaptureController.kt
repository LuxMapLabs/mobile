package com.luxmap.feature.survey.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.Surface
import androidx.core.content.ContextCompat
import com.luxmap.core.location.GpsSignalState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

// bindService() in startSession() is async, so the connection can still be landing by the time
// the user taps "Dừng quay" right away. A real recording lasts minutes, so a bind that still
// hasn't landed after this long means something is actually wrong, not just a race.
private const val BIND_WAIT_TIMEOUT_MS = 5_000L

// Thin seam over SurveyCaptureService (Task 17d) so CaptureViewModel takes an interface, not a
// Context or a live Service — keeping it constructor-mockable like every other ViewModel in this
// codebase. Control goes through Intent actions (review feedback); the bound connection exists
// only to read packagingResult and gpsSignalState back out.
interface SurveyCaptureController {
    fun startSession(
        sessionId: String,
        surveySweepId: String,
        luxDeviceAddress: String,
        previewSurface: Surface,
    )

    fun updatePreviewSurface(surface: Surface?)

    fun stopSession(): Flow<PackageResult>

    val gpsSignalState: StateFlow<GpsSignalState>
    val recordingStartResult: StateFlow<RecordingStartResult?>
}

@Singleton
class RealSurveyCaptureController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SurveyCaptureController {
        // A StateFlow (not a plain var) so stopSession() can suspend until a bind that is still in
        // flight lands, instead of reading a possibly-still-null value right away (that used to
        // report a false "Đóng gói thất bại" for a session that actually packaged fine, and skipped
        // unbindService() on that early-return path, leaking the ServiceConnection registration).
        private val boundService = MutableStateFlow<SurveyCaptureService?>(null)
        private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var gpsForwardingJob: Job? = null

        private val _gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
        override val gpsSignalState: StateFlow<GpsSignalState> = _gpsSignalState.asStateFlow()

        private val _recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
        override val recordingStartResult: StateFlow<RecordingStartResult?> = _recordingStartResult.asStateFlow()

        private var recordingStartForwardingJob: Job? = null

        // Explicit type needed: onServiceDisconnected below refers to `connection` by name to
        // rebind, and without an annotation here Kotlin can't infer this property's own type from
        // an object expression that references itself ("recursive problem").
        private val connection: ServiceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val service = (binder as SurveyCaptureService.LocalBinder).service()
                    boundService.value = service
                    // Forward the service's live GPS signal state into this controller's own
                    // StateFlow, since binder connections can drop/rebind but the ViewModel holds
                    // one stable StateFlow reference for the whole screen's lifetime.
                    gpsForwardingJob?.cancel()
                    gpsForwardingJob =
                        controllerScope.launch {
                            service.gpsSignalState.collect { _gpsSignalState.value = it }
                        }
                    recordingStartForwardingJob?.cancel()
                    recordingStartForwardingJob =
                        controllerScope.launch {
                            service.recordingStartResult.collect { _recordingStartResult.value = it }
                        }
                }

                // unbindService() itself never triggers this callback (see startSession's own
                // comment) - it only fires on an unexpected binder death, which can happen in the
                // middle of a 10-30 minute recording while SurveyCaptureService (a foreground
                // service) is still alive and running on its own. Without a rebind here,
                // gpsSignalState/recordingStartResult would freeze at their last value for the
                // rest of the session instead of tracking the service again.
                //
                // No automated test for this: bindService() below needs a real android.content.
                // Intent, which throws "not mocked" under the plain JVM unit tests this codebase
                // uses (no Robolectric) - same reason VideoCaptureSession has no unit test.
                // Verify on a real device instead: added to the real-device checklist in
                // docs/superpowers/plans/2026-09-29-capture-viewfinder-implementation.md.
                override fun onServiceDisconnected(name: ComponentName?) {
                    boundService.value = null
                    gpsForwardingJob?.cancel()
                    recordingStartForwardingJob?.cancel()
                    context.bindService(
                        Intent(context, SurveyCaptureService::class.java),
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                }
            }

        override fun startSession(
            sessionId: String,
            surveySweepId: String,
            luxDeviceAddress: String,
            previewSurface: Surface,
        ) {
            val intent =
                Intent(context, SurveyCaptureService::class.java)
                    .setAction(SurveyCaptureService.ACTION_START)
                    .putExtra(SurveyCaptureService.EXTRA_SESSION_ID, sessionId)
                    .putExtra(SurveyCaptureService.EXTRA_SURVEY_SWEEP_ID, surveySweepId)
                    .putExtra(SurveyCaptureService.EXTRA_LUX_DEVICE_ADDRESS, luxDeviceAddress)
                    .putExtra(SurveyCaptureService.EXTRA_PREVIEW_SURFACE, previewSurface)
            ContextCompat.startForegroundService(context, intent)
            // Unbind any still-registered connection from a previous session before rebinding with
            // the SAME ServiceConnection instance. Android treats a bindService() call with an
            // already-registered connection as a no-op (it does not fire onServiceConnected again),
            // so without this, boundService would stay null forever after a second startSession()
            // in the same controller lifetime, and stopSession() would report a false "Service not
            // bound" for a session that actually packaged fine. Safe even if nothing was bound yet
            // (startForegroundService above keeps the service alive independently of any binding).
            runCatching { context.unbindService(connection) }
            // Drop the previous session's service reference before binding again. unbindService()
            // does NOT call onServiceDisconnected (that only fires when the process dies), so
            // without this a stopSession() for THIS session could read the old, already finished
            // service and report its result as this session's outcome. The same reason applies to
            // recordingStartResult: a Ready/Failed left over from the finished session would be read
            // as this session's result before the new bind's forwarding job starts collecting.
            boundService.value = null
            _recordingStartResult.value = null
            context.bindService(Intent(context, SurveyCaptureService::class.java), connection, Context.BIND_AUTO_CREATE)
        }

        override fun updatePreviewSurface(surface: Surface?) {
            boundService.value?.updatePreviewSurface(surface)
        }

        override fun stopSession(): Flow<PackageResult> {
            val stopIntent =
                Intent(context, SurveyCaptureService::class.java).setAction(SurveyCaptureService.ACTION_STOP)
            context.startService(stopIntent)
            return flow {
                // Wait for the bind from startSession() to land instead of reading boundService
                // right away — see the field's own comment for why.
                val service = withTimeoutOrNull(BIND_WAIT_TIMEOUT_MS) { boundService.filterNotNull().first() }
                if (service == null) {
                    emit(PackageResult.Failure("Service not bound"))
                    return@flow
                }
                emitAll(service.packagingResult.filterNotNull().take(1))
            }.onCompletion {
                // unbindService throws IllegalArgumentException ("Service not registered") when no
                // bind ever landed (the timeout path above), and a throw here would escape
                // onCompletion and crash the caller.
                runCatching { context.unbindService(connection) }
                // This session is over; the next startSession() must wait for its own bind.
                boundService.value = null
            }
        }
    }
