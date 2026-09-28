package com.luxmap.feature.survey.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.luxmap.BuildConfig
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.camera.LockedCameraProfile
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject

private const val TAG = "SurveyCaptureService"
private const val NOTIFICATION_CHANNEL_ID = "survey_capture"
private const val NOTIFICATION_ID = 1001

// Recording states, same vocabulary as PackageSurveySessionUseCase (Task 15) and
// SurveySessionRecoveryUseCase (Task 16). No new state is added here.
private const val STATE_RECORDING = "recording"
private const val STATE_STOPPED = "stopped"
private const val STATE_PACKAGE_FAILED = "package_failed"

@AndroidEntryPoint
class SurveyCaptureService : Service() {
    @Inject lateinit var videoCaptureSession: VideoCaptureSession

    @Inject lateinit var locationHeadingRecorder: LocationHeadingRecorder

    @Inject lateinit var luxClient: LuxSensorBleClient

    @Inject lateinit var sessionDao: SurveySessionDao

    @Inject lateinit var packager: PackageSurveySessionUseCase

    // Last-resort net for anything thrown inside serviceScope. SupervisorJob only keeps one
    // child's failure from cancelling its siblings; without a handler the failure still reaches
    // the thread's default handler, which kills the app. A night survey must not lose the whole
    // process because one log write or one collector failed.
    private val serviceScope =
        CoroutineScope(
            SupervisorJob() +
                CoroutineExceptionHandler { _, error ->
                    Log.e(TAG, "Uncaught failure in the capture service scope", error)
                },
        )
    private val jobs = mutableListOf<Job>()

    // Kept apart from `jobs`: starting the camera must never be cancelled halfway, it is waited
    // for instead (see stopSessionInternal).
    private var videoStartJob: Job? = null
    private lateinit var luxWriter: NdjsonLogWriter
    private lateinit var gpsWriter: NdjsonLogWriter
    private lateinit var headingWriter: NdjsonLogWriter
    private lateinit var sessionDir: File
    private var currentSessionId: String = ""
    private var currentSurveySweepId: String = ""
    private var startedAtElapsedNs: Long = 0L
    private var utcAnchorIso: String = ""

    private val _packagingResult = MutableStateFlow<PackageResult?>(null)
    val packagingResult: StateFlow<PackageResult?> = _packagingResult.asStateFlow()

    // Exposed for CaptureViewModel's GPS-lost warning (Task 18) via the same bound-service path
    // packagingResult uses.
    val gpsSignalState: StateFlow<com.luxmap.core.location.GpsSignalState>
        get() = locationHeadingRecorder.gpsSignalState

    inner class LocalBinder : Binder() {
        fun service(): SurveyCaptureService = this@SurveyCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // Android requires startForeground() within seconds of startForegroundService() (API 26+) —
    // it runs first, unconditionally, before the action/extras are even read (review feedback).
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                val surveySweepId = intent.getStringExtra(EXTRA_SURVEY_SWEEP_ID) ?: return START_NOT_STICKY
                val luxDeviceAddress = intent.getStringExtra(EXTRA_LUX_DEVICE_ADDRESS) ?: return START_NOT_STICKY
                startSessionInternal(sessionId, surveySweepId, luxDeviceAddress)
            }
            ACTION_STOP -> stopSessionInternal()
        }
        return START_NOT_STICKY
    }

    private fun startSessionInternal(
        sessionId: String,
        surveySweepId: String,
        luxDeviceAddress: String,
    ) {
        currentSessionId = sessionId
        currentSurveySweepId = surveySweepId
        sessionDir = File(getExternalFilesDir(null), "survey/$sessionId").apply { mkdirs() }
        startedAtElapsedNs = SystemClock.elapsedRealtimeNanos()
        utcAnchorIso = Instant.now().toString()

        serviceScope.launch {
            sessionDao.insertSession(
                LocalSurveySessionEntity(
                    sessionId = sessionId,
                    surveySweepId = surveySweepId,
                    recordingState = STATE_RECORDING,
                    syncState = null,
                    startedAtUtc = Instant.now(),
                    startedAtElapsedNs = startedAtElapsedNs,
                    endedAtUtc = null,
                    durationSeconds = null,
                    distanceMeters = null,
                    gpsTrackFilePath = File(sessionDir, "gps_track.ndjson").absolutePath,
                    luxLogFilePath = File(sessionDir, "lux_log.ndjson").absolutePath,
                    headingLogFilePath = File(sessionDir, "heading_log.ndjson").absolutePath,
                    frameTimestampLogFilePath = File(sessionDir, "frame_timestamp_log.ndjson").absolutePath,
                    captureConfigFilePath = File(sessionDir, "capture_config.json").absolutePath,
                    manifestFilePath = null,
                    packageSchemaVersion = "v0",
                    // already verified by CheckSurveyReadinessUseCase before F04 was entered
                    timestampSourceRealtime = true,
                    bleGapDetected = false,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )
        }

        luxWriter = NdjsonLogWriter(File(sessionDir, "lux_log.ndjson"), fileRole = "lux_log")
        gpsWriter = NdjsonLogWriter(File(sessionDir, "gps_track.ndjson"), fileRole = "gps_track")
        headingWriter = NdjsonLogWriter(File(sessionDir, "heading_log.ndjson"), fileRole = "heading_log")

        luxClient.connect(luxDeviceAddress)
        jobs +=
            serviceScope.launch {
                luxClient.samples.collect { sample ->
                    luxWriter.appendLine(
                        """{"seq":${sample.seq},"module_ms":${sample.moduleMs},""" +
                            """"phone_elapsed_ns":${sample.phoneElapsedNs},"lux":${sample.lux},""" +
                            """"boot_id":${sample.bootId}}""",
                    )
                }
            }
        var lastConnected = false
        jobs +=
            serviceScope.launch {
                luxClient.connectionState.collect { state ->
                    val isConnected = state is com.luxmap.core.ble.BleConnectionState.Connected
                    // Only flag a gap on a Connected -> Disconnected TRANSITION (review feedback) —
                    // not on the StateFlow's initial Disconnected value before the first connect.
                    if (lastConnected && !isConnected) {
                        sessionDao.sessionById(currentSessionId)?.let { session ->
                            sessionDao.updateSession(session.copy(bleGapDetected = true, updatedAt = Instant.now()))
                        }
                    }
                    lastConnected = isConnected
                }
            }

        locationHeadingRecorder.start(gpsWriter, headingWriter)

        videoStartJob =
            serviceScope.launch {
                try {
                    videoCaptureSession.start(
                        scope = serviceScope,
                        sessionId = sessionId,
                        sessionDir = sessionDir,
                        // ISO/exposure/frame duration here are the requested profile — Task 17a reads back
                        // the actual applied values at stop() for capture_config.json.
                        profile =
                            LockedCameraProfile(
                                isoSensitivity = 800,
                                exposureTimeNs = 20_000_000L,
                                frameDurationNs = 33_333_333L,
                            ),
                        segmentDurationMs = SEGMENT_TARGET_DURATION_MS,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    // Camera or encoder setup can still fail in the field after the readiness
                    // checklist passed (another app holding the camera, an encoder that cannot be
                    // allocated). VideoCaptureSession.start() rethrows those after releasing the
                    // camera, and an uncaught throw here would kill the app in the middle of a
                    // night survey. Mark the session as broken instead: without video it can never
                    // produce a valid package, and package_failed is the state Task 15/16 already
                    // use for exactly that.
                    Log.e(TAG, "Video capture failed to start; this session has no video", error)
                    markPackageFailed()
                }
            }
    }

    private fun stopSessionInternal() {
        serviceScope.launch {
            jobs.forEach { it.cancel() }
            jobs.clear()
            luxClient.disconnect()
            locationHeadingRecorder.stop()
            luxWriter.close()
            gpsWriter.close()
            headingWriter.close()

            // VideoCaptureSession is single-use and keeps its camera/encoder/muxer in lateinit
            // fields, so stop() must never run while start() is still setting them up. A quick
            // "start, then stop" (a double tap, or the wrong route picked) does exactly that,
            // because start() takes until its first encoded frame. Wait for it to finish or fail
            // first — do not cancel it, a half-opened camera would stay open.
            videoStartJob?.join()
            videoStartJob = null

            val finalized =
                try {
                    videoCaptureSession.stop()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    // start() failed earlier, or stop() itself could not finish. Keep going so the
                    // logs already on disk are still packaged (and the failure is reported) instead
                    // of losing the whole session to a crash.
                    Log.e(TAG, "Failed to stop the video capture; capture_config.json is not written", error)
                    null
                }

            val captureConfigFile = File(sessionDir, "capture_config.json")
            if (finalized != null) {
                captureConfigFile.writeText(
                    CaptureConfigWriter.toJson(
                        CaptureConfig(
                            utcAnchorIso = utcAnchorIso,
                            elapsedAnchorNs = startedAtElapsedNs,
                            resolution = "${VIDEO_WIDTH}x$VIDEO_HEIGHT",
                            fps = VIDEO_FPS,
                            isoSensitivity = finalized.actualProfile.isoSensitivity,
                            shutterNs = finalized.actualProfile.exposureTimeNs,
                            frameDurationNs = finalized.actualProfile.frameDurationNs,
                            codec = "video/avc",
                            bitrateBps = VIDEO_BITRATE_BPS,
                            keyframeIntervalS = VIDEO_KEYFRAME_INTERVAL_S,
                            segmentDurationS = (SEGMENT_TARGET_DURATION_MS / 1000).toInt(),
                            cameraManufacturer = finalized.cameraManufacturer,
                            cameraModel = finalized.cameraModel,
                            cameraId = finalized.cameraId,
                            appVersion = BuildConfig.VERSION_NAME,
                            // real value comes from Bước 0's device pick, wired in Task 18
                            luxModuleId = "LUX-001",
                            // pending firmware contract (Task 17c / docs/contract-drift.md)
                            luxModuleFirmware = "unknown",
                        ),
                    ),
                )
            }

            val session = sessionDao.sessionById(currentSessionId)
            val endedAtUtc = Instant.now()
            val durationSeconds = (SystemClock.elapsedRealtimeNanos() - startedAtElapsedNs) / 1_000_000_000L
            if (session != null) {
                sessionDao.updateSession(
                    session.copy(
                        // Only a session that is still recording moves to stopped: a failed video
                        // start already marked it package_failed, and "stopped" would hide that.
                        recordingState =
                            if (session.recordingState == STATE_RECORDING) STATE_STOPPED else session.recordingState,
                        endedAtUtc = endedAtUtc,
                        durationSeconds = durationSeconds,
                        captureConfigFilePath = captureConfigFile.absolutePath,
                        updatedAt = Instant.now(),
                    ),
                )
            }

            val result = packager.invoke(currentSessionId)
            // Same handling as crash recovery (Task 16): a session whose package could not be
            // written must not sit at "stopped" as if it were fine.
            if (result is PackageResult.Failure) markPackageFailed()
            _packagingResult.value = result
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun markPackageFailed() {
        sessionDao.sessionById(currentSessionId)?.let { session ->
            sessionDao.updateSession(session.copy(recordingState = STATE_PACKAGE_FAILED, updatedAt = Instant.now()))
        }
    }

    override fun onDestroy() {
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_ID, "Khảo sát đêm", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Đang quay khảo sát")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

    companion object {
        const val ACTION_START = "com.luxmap.survey.action.START"
        const val ACTION_STOP = "com.luxmap.survey.action.STOP"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_SURVEY_SWEEP_ID = "survey_sweep_id"
        const val EXTRA_LUX_DEVICE_ADDRESS = "lux_device_address"

        // Finalized against the Task 2 spike's findings — kept in sync with VideoCaptureSession's
        // own private constants; a fast-follow could hoist these into one shared place.
        private const val SEGMENT_TARGET_DURATION_MS = 180_000L
        private const val VIDEO_WIDTH = 1920
        private const val VIDEO_HEIGHT = 1080
        private const val VIDEO_BITRATE_BPS = 8_000_000
        private const val VIDEO_FPS = 30
        private const val VIDEO_KEYFRAME_INTERVAL_S = 2
    }
}
