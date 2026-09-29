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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider

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
    // A Provider, not a plain field: VideoCaptureSession is single-use (its firstSegmentReady,
    // encoderOutputFormat and lateinit camera fields are never reset), but field injection happens
    // once per Service instance, so a second ACTION_START on the same instance would reuse a spent
    // one. Every session takes a fresh instance from here instead.
    @Inject lateinit var videoCaptureSessionProvider: Provider<VideoCaptureSession>

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
                    // A dead collector means its log file stopped growing (for example appendLine
                    // hitting a full disk). Only logging that would let the stop path package a
                    // truncated file and report Success, so remember it and let the stop path
                    // report a broken session.
                    markBroken("A capture stream failed during the session: ${error.message}")
                },
        )
    private val jobs = mutableListOf<Job>()

    // Set as soon as anything makes this session's data incomplete: a camera that never started, a
    // mid-session encoder/muxer failure, or a collector that died. Read at stop time so a truncated
    // recording is never reported as a healthy package. @Volatile because it is written from the
    // exception handler's thread and read from the stop coroutine.
    @Volatile
    private var brokenReason: String? = null

    // Guards a second ACTION_STOP (a double tap): re-running the stop sequence would close already
    // closed writers and start a second packaging run over the same files, which cleanIfNdjson
    // rewrites in place.
    @Volatile
    private var isStopping = false

    // Guards a second ACTION_START arriving while a session is still live. Without this, leaving
    // CaptureScreen mid-recording and opening a new CaptureViewModel (which sees BLE already
    // Connected and shows Ready right away) lets the user press "Bắt đầu quay" again, sending a
    // second ACTION_START to this SAME service instance — overwriting sessionDir/writers/
    // videoStartJob and opening a second camera session while the first session's camera/jobs are
    // still open. Reset only once stopSessionInternal's stop sequence has fully finished.
    @Volatile
    private var isRecording = false

    // Kept apart from `jobs`: starting the camera must never be cancelled halfway, it is waited
    // for instead (see stopSessionInternal).
    private var videoStartJob: Job? = null

    // The instance for the session that is running right now. Set at the top of
    // startSessionInternal and read by the whole stop path, so start and stop always mean the same
    // instance.
    private lateinit var videoCaptureSession: VideoCaptureSession
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
                if (isRecording) {
                    Log.w(TAG, "ACTION_START arrived while a session is already recording; ignoring it")
                } else {
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                    val surveySweepId = intent.getStringExtra(EXTRA_SURVEY_SWEEP_ID) ?: return START_NOT_STICKY
                    val luxDeviceAddress = intent.getStringExtra(EXTRA_LUX_DEVICE_ADDRESS) ?: return START_NOT_STICKY
                    isRecording = true
                    startSessionInternal(sessionId, surveySweepId, luxDeviceAddress)
                }
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
        // An ACTION_START can land in the short window after stopSelf() but before the OS really
        // destroys this instance, so every per-session field has to start clean. Leaving isStopping
        // true would make this session's own STOP a no-op (camera never stops), and a leftover
        // _packagingResult would be reported right away as this session's result.
        val videoSession = videoCaptureSessionProvider.get()
        videoCaptureSession = videoSession
        isStopping = false
        brokenReason = null
        _packagingResult.value = null

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

        // CaptureViewModel (Task 18) already calls connect() when the screen is entered, so BLE is
        // normally already Connected by the time a session starts. Only connect here if that did
        // NOT happen (for example something else drives this service directly) - calling connect()
        // again on an already-live connection would tear down and reopen the GATT link right as
        // recording begins (a real BLE gap at the start of every survey).
        if (luxClient.connectionState.value != com.luxmap.core.ble.BleConnectionState.Connected) {
            luxClient.connect(luxDeviceAddress)
        }
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
                    // The local, not the field: this coroutine must always use the instance made
                    // for THIS session.
                    videoSession.start(
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
                } catch (error: Throwable) {
                    // Camera or encoder setup can still fail in the field after the readiness
                    // checklist passed (another app holding the camera, an encoder that cannot be
                    // allocated). VideoCaptureSession.start() rethrows those after releasing the
                    // camera, and an uncaught throw here would kill the app in the middle of a
                    // night survey.
                    //
                    // A CancellationException here means one of two very different things.
                    // openCamera's onDisconnected cancels its own continuation with no cause when
                    // another app takes the camera over - a real capture failure, and this
                    // coroutine is still active when it arrives. If serviceScope itself was
                    // cancelled (onDestroy), this coroutine is no longer active and the
                    // cancellation must keep propagating instead of being turned into a result.
                    if (error is CancellationException && !currentCoroutineContext().isActive) throw error
                    Log.e(TAG, "Video capture failed to start; this session has no video", error)
                    failSession("Video capture failed to start: ${error.message}")
                }
            }
    }

    private fun stopSessionInternal() {
        if (!::sessionDir.isInitialized) {
            // ACTION_STOP with no ACTION_START before it: nothing was opened, so there is nothing
            // to close. Take the service down instead of touching the lateinit fields.
            Log.w(TAG, "ACTION_STOP arrived with no session started; stopping the service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (isStopping) {
            Log.w(TAG, "ACTION_STOP arrived while the session is already stopping; ignoring it")
            return
        }
        isStopping = true

        serviceScope.launch {
            var videoStopped = false
            try {
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
                // because start() takes until its first encoded frame. Wait for it to finish or
                // fail first — do not cancel it, a half-opened camera would stay open.
                videoStartJob?.join()
                videoStartJob = null

                val finalized = stopVideoCapture()
                videoStopped = true
                writeSessionResult(finalized)
            } finally {
                // The stop path must always end the same way, even when a step above throws: a full
                // disk can throw from close(), from writeText, from the DAO or from the packager's
                // own checksum/manifest writes. Without this the camera would keep running, the
                // service would stay in the foreground and a caller bound to packagingResult would
                // wait forever. Same shape as VideoCaptureSession.stop()'s own finally block.
                if (!videoStopped) runCatching { videoCaptureSession.stop() }
                if (_packagingResult.value == null) {
                    _packagingResult.value = PackageResult.Failure("Stopping the session failed; see the log")
                }
                // Only now is a new ACTION_START safe to accept (see isRecording's own comment).
                isRecording = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun stopVideoCapture(): FinalizedVideoCapture? {
        val finalized =
            try {
                videoCaptureSession.stop()
            } catch (error: Throwable) {
                if (error is CancellationException && !currentCoroutineContext().isActive) throw error
                // start() failed earlier, or stop() itself could not finish. Keep going so the logs
                // already on disk are still packaged (and the failure is reported) instead of losing
                // the whole session to a crash.
                Log.e(TAG, "Failed to stop the video capture; capture_config.json is not written", error)
                markBroken("Failed to stop the video capture: ${error.message}")
                null
            }
        // Task 17a keeps a mid-session drain failure (a full disk, a MediaMuxer or Room error) in
        // `failure` and says callers must check it: stop() still returns a normal-looking result,
        // but the video and the frame timestamp log stopped growing at that point.
        videoCaptureSession.failure?.let { failure ->
            markBroken("The video recording broke during the session: ${failure.message}")
        }
        return finalized
    }

    private suspend fun writeSessionResult(finalized: FinalizedVideoCapture?) {
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

        // The package is still written when the recording broke: the files that did make it to disk
        // are worth keeping and looking at. What must not happen is reporting it as healthy.
        val packaged = packager.invoke(currentSessionId)
        val broken = brokenReason
        _packagingResult.value =
            when {
                broken != null -> {
                    Log.w(TAG, "Session $currentSessionId is incomplete: $broken")
                    markPackageFailed()
                    PackageResult.Failure(broken)
                }
                // Same handling as crash recovery (Task 16): a session whose package could not be
                // written must not sit at "stopped" as if it were fine.
                packaged is PackageResult.Failure -> {
                    markPackageFailed()
                    packaged
                }
                else -> packaged
            }
    }

    // Remembers that this session's data is incomplete. Called from the coroutine exception handler
    // too, so it must not suspend or touch the DAO.
    private fun markBroken(reason: String) {
        if (brokenReason == null) brokenReason = reason
    }

    // Marks the session broken AND tells a bound caller (Task 18) right away, for a failure that
    // happens while recording is still running - otherwise the UI would sit on the Recording screen
    // with nothing being recorded.
    private suspend fun failSession(reason: String) {
        markBroken(reason)
        markPackageFailed()
        _packagingResult.value = PackageResult.Failure(reason)
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
