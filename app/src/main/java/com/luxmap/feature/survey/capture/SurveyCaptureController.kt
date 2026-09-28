package com.luxmap.feature.survey.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// Thin seam over SurveyCaptureService (Task 17d) so CaptureViewModel takes an interface, not a
// Context or a live Service — keeping it constructor-mockable like every other ViewModel in this
// codebase. Control goes through Intent actions (review feedback); the bound connection exists
// only to read packagingResult and gpsSignalState back out.
interface SurveyCaptureController {
    fun startSession(
        sessionId: String,
        surveySweepId: String,
        luxDeviceAddress: String,
    )

    fun stopSession(): Flow<PackageResult>

    val gpsSignalState: StateFlow<GpsSignalState>
}

@Singleton
class RealSurveyCaptureController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SurveyCaptureController {
        private var boundService: SurveyCaptureService? = null
        private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var gpsForwardingJob: Job? = null

        private val _gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
        override val gpsSignalState: StateFlow<GpsSignalState> = _gpsSignalState.asStateFlow()

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val service = (binder as SurveyCaptureService.LocalBinder).service()
                    boundService = service
                    // Forward the service's live GPS signal state into this controller's own
                    // StateFlow, since binder connections can drop/rebind but the ViewModel holds
                    // one stable StateFlow reference for the whole screen's lifetime.
                    gpsForwardingJob?.cancel()
                    gpsForwardingJob =
                        controllerScope.launch {
                            service.gpsSignalState.collect { _gpsSignalState.value = it }
                        }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    boundService = null
                    gpsForwardingJob?.cancel()
                }
            }

        override fun startSession(
            sessionId: String,
            surveySweepId: String,
            luxDeviceAddress: String,
        ) {
            val intent =
                Intent(context, SurveyCaptureService::class.java)
                    .setAction(SurveyCaptureService.ACTION_START)
                    .putExtra(SurveyCaptureService.EXTRA_SESSION_ID, sessionId)
                    .putExtra(SurveyCaptureService.EXTRA_SURVEY_SWEEP_ID, surveySweepId)
                    .putExtra(SurveyCaptureService.EXTRA_LUX_DEVICE_ADDRESS, luxDeviceAddress)
            ContextCompat.startForegroundService(context, intent)
            context.bindService(Intent(context, SurveyCaptureService::class.java), connection, Context.BIND_AUTO_CREATE)
        }

        override fun stopSession(): Flow<PackageResult> {
            val stopIntent =
                Intent(context, SurveyCaptureService::class.java).setAction(SurveyCaptureService.ACTION_STOP)
            context.startService(stopIntent)
            val service = boundService ?: return flowOf(PackageResult.Failure("Service not bound"))
            return service.packagingResult
                .filterNotNull()
                .take(1)
                .onCompletion { context.unbindService(connection) }
        }
    }
