package com.luxmap.feature.survey.capture

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import com.luxmap.core.camera.CameraSelector
import com.luxmap.core.camera.ExposureLockController
import com.luxmap.core.camera.FrameTimestampLogger
import com.luxmap.core.camera.LockedCameraProfile
import com.luxmap.core.camera.SegmentRotationPolicy
import com.luxmap.core.camera.SegmentedVideoRecorder
import com.luxmap.core.camera.VideoSegmentResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

data class FinalizedVideoCapture(
    val lastSegment: VideoSegmentResult,
    val actualProfile: LockedCameraProfile,
    val cameraManufacturer: String,
    val cameraModel: String,
    val cameraId: String,
)

class VideoCaptureSession
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exposureLockController: ExposureLockController,
        private val frameTimestampLogger: FrameTimestampLogger,
        private val sessionDao: SurveySessionDao,
    ) {
        private lateinit var cameraDevice: CameraDevice
        private lateinit var captureSession: CameraCaptureSession

        // The preview's own OutputConfiguration, kept apart from the encoder's, so the real
        // preview Surface can be attached and detached while the session runs. Only set on the
        // API 28+ path (see supportsLivePreviewSwap).
        private lateinit var previewOutputConfig: OutputConfiguration

        // The base surface of previewOutputConfig. A shared OutputConfiguration cannot have its
        // base surface removed (OutputConfiguration.removeSurface throws for it), so an app-owned
        // SurfaceTexture is used as the base and the real preview Surface is added on top of it.
        // That way detaching the real Surface never leaves the output slot empty.
        //
        // A SurfaceTexture, not an ImageReader: enableSurfaceSharing() only GUARANTEES that
        // surfaces of the same size, format, dataSpace AND "Surface source class" can share one
        // output. Two surfaces of different source classes "are generally not compatible" and only
        // work on some devices - the sole way to find out is the session failing to configure on
        // that device. The TextureView preview is SurfaceTexture-backed, so making this base a
        // SurfaceTexture too keeps both in the guaranteed tier instead of the device-dependent one.
        //
        // SurfaceTexture(false) is the detached (no GL context) constructor, added in API 26. It
        // is never a capture-request target, so nothing is ever produced into it and nothing has
        // to drain it.
        private var placeholderPreviewTexture: SurfaceTexture? = null
        private var placeholderPreviewSurface: Surface? = null

        // API 28 is where CameraCaptureSession.updateOutputConfiguration() and
        // OutputConfiguration.removeSurface() were added - both are needed to attach or detach a
        // preview Surface while the session runs. minSdk here is 26, so some real devices take
        // the pre-28 path where updatePreviewSurface() can only be a no-op.
        private val supportsLivePreviewSwap = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        // The real preview Surface attached to previewOutputConfig right now, or null when only
        // the placeholder base is attached. Set once while the session is being built, then only
        // touched on the camera thread - @Volatile for that first hand-off, same reason as
        // requestBuilder below.
        @Volatile
        private var currentSharedPreviewSurface: Surface? = null

        private lateinit var mediaCodec: MediaCodec
        private lateinit var recorder: SegmentedVideoRecorder
        private lateinit var frameTimestampWriter: NdjsonLogWriter
        private lateinit var cameraId: String
        private lateinit var sessionId: String
        private lateinit var sessionDir: File

        // All Camera2 callbacks run on this thread. openCamera/createCaptureSession/
        // setRepeatingRequest need a real Handler: passing null makes Camera2 use the CALLING
        // thread's Looper, and start() runs on a coroutine dispatcher with no Looper, so null
        // would throw "No handler given, and current thread has no looper!".
        private var cameraThread: HandlerThread? = null
        private var cameraHandler: Handler? = null

        // Only touched from the drain coroutine after start() returns.
        private var currentMuxerPort: RealMuxerPort? = null
        private var frameIndex = 0
        private var currentSegmentIndex = 0

        // The frame the drain loop is handling right now. Kept here, not only on the port, because
        // a rotation builds a NEW RealMuxerPort in the middle of onEncodedFrame() and writes this
        // same frame to it -- the new port has to be staged with this data before that write.
        private var pendingBuffer: ByteBuffer? = null
        private var pendingBufferInfo: MediaCodec.BufferInfo? = null

        // Written in start(), read on the camera thread.
        @Volatile
        private var requestBuilder: CaptureRequest.Builder? = null

        // Only touched on the camera thread.
        private var awbLocked = false

        @Volatile
        private var lastCaptureResult: TotalCaptureResult? = null

        // Filled on the camera thread, drained on the encoder coroutine. ArrayDeque is not
        // thread-safe and a lost or torn entry here silently breaks frame-to-GPS correlation for
        // the whole session, so every access takes this lock.
        private val timestampLock = Any()
        private val pendingSensorTimestamps = ArrayDeque<Long>()

        // PTS and SENSOR_TIMESTAMP run at the same rate with a fixed offset (Task 2 spike,
        // section 3: zero drift over 12.4 minutes). Learned from the first paired frame and used
        // only to spot a queue head that is far too old to belong to the current frame.
        private var ptsToSensorOffsetUs: Long? = null

        // How many frames in a row the staleness guard has dropped something. A guard that fires
        // on every frame means the anchor itself is probably wrong, not the queue — see
        // takeSensorTimestampFor().
        private var consecutiveGuardDrops = 0

        // Cached from the ONE-TIME INFO_OUTPUT_FORMAT_CHANGED event. MediaCodec fires it once per
        // encoder lifetime, not once per segment, but every new segment's MediaMuxer still needs
        // it for addTrack() — the spike hit exactly this bug on its first rotation.
        private var encoderOutputFormat: MediaFormat? = null

        private var drainJob: Job? = null
        private val firstSegmentReady = CompletableDeferred<Unit>()

        // The drain loop runs in the caller's scope, so an exception there would otherwise cancel
        // that scope with nothing left to read. Kept here so Task 17d's service can tell the user
        // the recording is broken instead of the failure vanishing. Callers must check it.
        @Volatile
        private var drainFailure: Throwable? = null

        val failure: Throwable?
            get() = drainFailure

        suspend fun start(
            scope: CoroutineScope,
            sessionId: String,
            sessionDir: File,
            profile: LockedCameraProfile,
            segmentDurationMs: Long,
            previewSurface: Surface,
        ) {
            this.sessionId = sessionId
            this.sessionDir = sessionDir
            try {
                startCapture(scope, profile, segmentDurationMs, previewSurface)
            } catch (failed: Throwable) {
                // One failed attempt must not leave the camera device open: every later attempt
                // would then fail with CAMERA_IN_USE for the rest of the process's life.
                Log.e(TAG, "Failed to start capture session; releasing camera", failed)
                cancelDrainJob()
                releaseCaptureResources()
                throw failed
            }
        }

        private suspend fun startCapture(
            scope: CoroutineScope,
            profile: LockedCameraProfile,
            segmentDurationMs: Long,
            previewSurface: Surface,
        ) {
            val thread = HandlerThread("luxmap-camera").apply { start() }
            cameraThread = thread
            val handler = Handler(thread.looper)
            cameraHandler = handler

            val cameraManager = context.getSystemService(CameraManager::class.java)
            // Same selection as the readiness check (CameraSelector), so the camera the checklist
            // passed is the camera we open here. Still fails loudly on a device with no camera at
            // all, like the old cameraIdList.first() did.
            cameraId = CameraSelector.pickBackCameraId(cameraManager) ?: error("No camera available")

            // Worked out once, here, and then used by every segment below. It only needs the camera
            // characteristics and how the screen is turned right now - neither can change later,
            // because CaptureScreen locks the screen orientation for the whole session. Doing it
            // per segment would just repeat the same work.
            val orientationHint = resolveOrientationHint(cameraManager)

            cameraDevice = openCamera(cameraManager, cameraId, handler)

            mediaCodec = createEncoder(profile)
            val inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            recorder =
                SegmentedVideoRecorder(SegmentRotationPolicy(segmentDurationMs)) { path ->
                    val format = requireNotNull(encoderOutputFormat) { "Encoder output format is not known yet" }
                    RealMuxerPort(path, format, orientationHint).also { port ->
                        currentMuxerPort = port
                        // A rotation builds this port inside onEncodedFrame() and writes the frame
                        // in flight to it right away, before the drain loop can stage anything on
                        // it. Copy that frame over now. Null on the very first segment, where no
                        // frame has been drained yet -- nothing to stage, and nothing writes to
                        // this port until the normal per-frame path reaches it.
                        val buffer = pendingBuffer
                        val info = pendingBufferInfo
                        if (buffer != null && info != null) port.setPendingSample(buffer, info)
                    }
                }
            frameTimestampWriter =
                NdjsonLogWriter(File(sessionDir, "frame_timestamp_log.ndjson"), fileRole = "frame_timestamp_log")

            captureSession = createCaptureSession(cameraDevice, inputSurface, previewSurface, handler)

            // Give the user a moment of real live preview to aim at the pole before the scans
            // below measure and lock anything - see startLivePreviewWarmup() for why.
            startLivePreviewWarmup(previewSurface, handler)

            // Locking AF_MODE_OFF (below) freezes the lens at whatever LENS_FOCUS_DISTANCE the
            // profile carries. The caller has no way to know the right distance for the actual
            // scene, so find it here with a real AF scan before locking - otherwise the profile's
            // 0-diopter default (infinity focus) locks in and the whole recording comes out
            // blurry unless the subject really is at infinity.
            //
            // The ISO and shutter time the profile carries have the same problem for the same
            // reason, so a short AE scan measures them here too - see resolveExposure() for why.
            // Focus first, then exposure, both before the recording request below is built.
            val resolvedFocusDistance = resolveFocusDistance(cameraManager, profile, previewSurface, handler)
            val resolvedExposure = resolveExposure(cameraManager, profile, previewSurface, handler)
            val lockedProfile =
                profile.copy(
                    focusDistanceDiopters = resolvedFocusDistance,
                    isoSensitivity = resolvedExposure.isoSensitivity,
                    exposureTimeNs = resolvedExposure.exposureTimeNs,
                )

            val builder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(inputSurface)
                    // The real preview Surface, on both paths. On the API 28+ path it is a shared
                    // member of previewOutputConfig, and a target has to name the exact surface
                    // that should receive frames - naming the placeholder base instead would leave
                    // the preview black. updatePreviewSurface() swaps this target later.
                    addTarget(previewSurface)
                    exposureLockController.applyTo(this, lockedProfile)
                }
            requestBuilder = builder
            captureSession.setRepeatingRequest(builder.build(), captureCallback, handler)

            // The drain loop blocks its thread inside dequeueOutputBuffer() and writes files, so
            // it must not run on whatever dispatcher the caller's scope uses.
            drainJob = scope.launch(Dispatchers.IO) { drainEncoderOutput() }

            // Segment 0 is opened by the drain loop, not here (see openFirstSegment). start()
            // still only returns once that happened, so callers keep the guarantee that an open
            // local_survey_video_segment row exists as soon as start() returns. start() releases
            // everything if this throws.
            try {
                withTimeout(FIRST_SEGMENT_TIMEOUT_MS) { firstSegmentReady.await() }
            } catch (timeout: TimeoutCancellationException) {
                throw IllegalStateException("Encoder produced no output within $FIRST_SEGMENT_TIMEOUT_MS ms", timeout)
            }
        }

        // Camera2 hands the encoder pixels in the sensor's own orientation, which on a phone is
        // landscape, no matter how the user holds the device. The encoder is also fixed at
        // 1920x1080, so a clip shot in portrait still ends up as a landscape file and plays back
        // sideways. Rotating every frame would cost CPU for a whole 10-30 minute survey, so the
        // rotation is written into the MP4 as metadata instead (see RealMuxerPort) and the player
        // applies it. This returns the angle the player has to turn the picture clockwise.
        //
        // This is the back-camera formula. Only the back camera is ever opened here
        // (CameraSelector.pickBackCameraId), so the mirrored front-camera case is left out.
        private fun resolveOrientationHint(cameraManager: CameraManager): Int {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            // DisplayManager, not Context.getDisplay(): this is the application context, and on API
            // 30+ Context.getDisplay() throws UnsupportedOperationException for a context that is
            // not a UI context. DisplayManager has no such limit and works down to minSdk 26.
            val display =
                context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            val deviceRotationDegrees =
                when (display?.rotation) {
                    Surface.ROTATION_0 -> 0
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
            val hint = (sensorOrientation - deviceRotationDegrees + 360) % 360
            // Permanent diagnostic: a recording that still plays back sideways on some device can
            // only be told apart from a wrong sensor value or a wrong screen rotation from here.
            Log.i(
                TAG,
                "Orientation hint: hint=$hint sensorOrientation=$sensorOrientation " +
                    "deviceRotationDegrees=$deviceRotationDegrees",
            )
            return hint
        }

        // Until this runs, no repeating request exists on the session at all, so the preview is
        // plain black and the user cannot see what the camera is pointed at. The first live frames
        // they ever saw were the AF/AE scan already measuring - so they were often still raising or
        // turning the phone while focus locked. A real-device test recorded three focus locks at
        // ~34cm, ~8.9cm and ~12.3cm for a scene several meters away. This shows live frames first,
        // so the user can aim before anything is measured or locked.
        private suspend fun startLivePreviewWarmup(
            previewSurface: Surface,
            handler: Handler,
        ) {
            val previewBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    // CONTINUOUS_VIDEO, not CONTINUOUS_PICTURE: it keeps the scene in focus
                    // smoothly instead of stepping through scan-and-lock cycles the user would see.
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
            // No callback: this only needs to put frames on the screen. resolveFocusDistance() and
            // resolveExposure() measure on their own requests right after. No "stop" step either -
            // setRepeatingRequest() always replaces the active repeating request, so their own
            // calls take over cleanly.
            captureSession.setRepeatingRequest(previewBuilder.build(), null, handler)
            delay(PRE_SCAN_PREVIEW_MS)
        }

        // The Field Engineer points the camera at the actual pole before pressing record, so a
        // real AF scan converging on whatever is in frame at that moment is the right target
        // distance - this is primary. LENS_INFO_HYPERFOCAL_DISTANCE is only a fallback for when
        // the scan itself cannot converge (e.g. a very low-contrast scene): it is a static value
        // from the device's reported characteristics, not verified against this device's real
        // optics, and a real-device check found it locking focus far past where it should for a
        // close, high-contrast test target - so a GENUINE AF lock (CONTROL_AF_STATE_FOCUSED_LOCKED)
        // always wins over it. Everything else does not count as a real AF result: a timeout and
        // CONTROL_AF_STATE_NOT_FOCUSED_LOCKED (AF finished, found nothing sharp) both leave the lens
        // parked wherever the sweep stopped, so both take the hyperfocal fallback.
        private suspend fun resolveFocusDistance(
            cameraManager: CameraManager,
            requestedProfile: LockedCameraProfile,
            previewSurface: Surface,
            handler: Handler,
        ): Float {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val minFocusDistance = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
            // A fixed-focus lens reports 0 here and has no LENS_FOCUS_DISTANCE control at all -
            // nothing to scan for, keep whatever the caller asked for.
            if (minFocusDistance == null || minFocusDistance <= 0f) return requestedProfile.focusDistanceDiopters

            // Used whenever the scan below does not end in a real focus lock: it never reaches a
            // locked AF state (timeout), or it reaches NOT_FOCUSED_LOCKED (AF gave up). Hyperfocal
            // keeps everything from some near distance out to infinity acceptably sharp, which is
            // the best guess for the kind of dim, far, low-contrast scene that defeats AF.
            val fallbackDistance =
                characteristics.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)
                    ?: requestedProfile.focusDistanceDiopters
            // Diagnostic only, not used for any decision - kept so a real-device test can tell
            // whether AF truly locked focus or gave up (CONTROL_AF_STATE_NOT_FOCUSED_LOCKED),
            // something a plain "it recorded a distance" check cannot distinguish on its own.
            var lastSeenAfState: Int? = null
            // Diagnostic only too, and for the same reason: it records where the lens really was on
            // the last capture result, so a log from a blurry recording shows whether the lens was
            // anywhere near the distance we locked. Never read as a decision input - it is
            // overwritten by every frame of an in-progress scan, so its value at any given moment is
            // just wherever the scan happened to be sweeping through.
            var lastSeenFocusDistance: Float? = null

            // Standard Camera2 "tap to focus" sequence, run once on the preview surface before the
            // locked recording request is ever submitted: a preview-only repeating request in AUTO
            // mode, one explicit AF_TRIGGER_START, then back to idle while polling CONTROL_AF_STATE
            // on the same repeating request until it reaches a locked state (found or not found).
            // Doing this before the recording request means the recording request is only ever
            // submitted once here - no later resubmit like the AWB lock needs, so no new risk to
            // the SENSOR_TIMESTAMP pairing (see takeSensorTimestampFor()).

            // Completed once the AF scan settles. A non-null value means AF really locked onto
            // something sharp and this is where it locked; null means AF finished without finding
            // focus, so the caller must use the fallback instead.
            val converged = CompletableDeferred<Float?>()
            val afCallback =
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        result.get(CaptureResult.LENS_FOCUS_DISTANCE)?.let { lastSeenFocusDistance = it }
                        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                        if (afState != null) lastSeenAfState = afState
                        when (afState) {
                            // The only case where the lens position is a real focus decision, so the
                            // only case we trust it. A device that somehow reports no
                            // LENS_FOCUS_DISTANCE here completes with null and takes the fallback.
                            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ->
                                converged.complete(result.get(CaptureResult.LENS_FOCUS_DISTANCE))
                            // AF finished and found nothing sharp - common for a dim, far,
                            // low-contrast night scene, which is exactly what a street-light pole
                            // looks like. The lens just stopped wherever the sweep ended, so that
                            // position means nothing. Report failure and let the fallback decide.
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> converged.complete(null)
                            else -> Unit
                        }
                    }
                }

            val meteringBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                }
            captureSession.setRepeatingRequest(meteringBuilder.build(), null, handler)
            meteringBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            captureSession.capture(meteringBuilder.build(), afCallback, handler)
            meteringBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
            captureSession.setRepeatingRequest(meteringBuilder.build(), afCallback, handler)

            // A low-contrast scene (very dark, blank wall) can leave AF_STATE scanning forever -
            // never block the recording on it, fall back to the hyperfocal distance instead.
            // Wrapping the awaited value is what keeps the two null cases apart: withTimeoutOrNull()
            // returns null only when AF never answered, while a wrapper holding null means AF did
            // answer and the answer was "no focus found".
            val scanOutcome = withTimeoutOrNull(AF_CONVERGENCE_TIMEOUT_MS) { AfScanOutcome(converged.await()) }
            val finalFocusDistance = scanOutcome?.focusDistance ?: fallbackDistance
            val outcomeLabel =
                when {
                    scanOutcome?.focusDistance != null -> "AF_LOCKED (scanned distance used)"
                    scanOutcome != null -> "AF_GAVE_UP (hyperfocal fallback used)"
                    else -> "AF_TIMED_OUT (hyperfocal fallback used)"
                }
            // Kept as a permanent diagnostic log (not a "remove before commit" line): this is the
            // only way to tell apart a real focus lock from one that only looks locked, across a
            // real-device test done later. See docs/superpowers plan notes on the night-survey
            // focus complaint for what outcome=AF_GAVE_UP or outcome=AF_TIMED_OUT would mean.
            Log.i(
                TAG,
                "Focus lock result: outcome=$outcomeLabel afState=${afStateLabel(lastSeenAfState)} " +
                    "lockedDistance=$finalFocusDistance lastSeenLensDistance=$lastSeenFocusDistance " +
                    "hyperfocalDistance=$fallbackDistance minFocusDistance=$minFocusDistance",
            )
            return finalFocusDistance
        }

        // Tells "AF answered" apart from "AF never answered" around withTimeoutOrNull(), which
        // reports a timeout with the same null the AF-failed case already uses. Only resolveFocusDistance() uses it.
        private data class AfScanOutcome(val focusDistance: Float?)

        // Locking CONTROL_AE_MODE_OFF (see ExposureLockController) freezes the recording at whatever
        // ISO and shutter time the profile carries. Those two numbers come from the Task 2 spike,
        // which measured them INDOORS and says so itself (see "Chosen defaults for
        // capture_config.json" in docs/superpowers/specs/2026-09-28-survey-capture-spike-findings.md:
        // "not validated in actual night/outdoor low-light survey conditions"). A rural road at night
        // is much darker than that, and how dark it is changes from route to route, so one fixed pair
        // cannot be right everywhere. Measure the real scene here with a short AE scan and use what
        // the sensor actually settled on, the same way resolveFocusDistance() does for focus.
        //
        // This is a separate scan from the AF one on purpose: two short single-purpose requests are
        // easier to read and to review than one combined AF+AE request, and the focus scan is
        // already working and reviewed. They run one after the other, both before the recording
        // request is ever built.
        private suspend fun resolveExposure(
            cameraManager: CameraManager,
            requestedProfile: LockedCameraProfile,
            previewSurface: Surface,
            handler: Handler,
        ): ResolvedExposure {
            // Whatever the caller asked for is the fallback. This function does not care where those
            // numbers came from, same as resolveFocusDistance() just passing the requested focus
            // distance back when there is nothing to scan.
            val fallback = ResolvedExposure(requestedProfile.isoSensitivity, requestedProfile.exposureTimeNs)

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
            val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            // Both missing means the device reports no manual exposure control at all, so the later
            // CONTROL_AE_MODE_OFF lock could not use a measured value anyway - nothing to scan for,
            // keep what the caller asked for. Only ONE of them missing still gives a useful
            // measurement, so that case goes on with the scan.
            if (sensitivityRange == null && exposureTimeRange == null) return fallback

            // Diagnostic only, never a decision input: it records the last AE state we saw, so a log
            // from a badly exposed recording shows whether AE was still scanning when we gave up.
            var lastSeenAeState: Int? = null

            // Completed once AE settles. A non-null value means AE really converged and these are the
            // values it measured; null means AE settled but the result did not carry readable values,
            // so the caller must use the fallback instead.
            val converged = CompletableDeferred<ResolvedExposure?>()
            val aeCallback =
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult,
                    ) {
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (aeState != null) lastSeenAeState = aeState
                        // FLASH_REQUIRED means AE finished metering and decided the scene needs
                        // flash. This app never fires the flash, but AE has settled all the same, so
                        // the measured values are just as good to read out as from CONVERGED.
                        val settled =
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                        if (!settled) return
                        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        val exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        // Some devices do not report these result keys, and a zero or negative value
                        // would be rejected by the locked request later. Treat either as a failed
                        // read and let the fallback decide.
                        if (iso == null || iso <= 0 || exposureTimeNs == null || exposureTimeNs <= 0L) {
                            converged.complete(null)
                            return
                        }
                        converged.complete(ResolvedExposure(iso, exposureTimeNs))
                    }
                }

            // A plain AE_MODE_ON preview request is enough: AE meters the scene continuously on a
            // repeating request, so there is no trigger to fire like AF needs
            // (CONTROL_AE_PRECAPTURE_TRIGGER exists for flash metering before a still shot, which is
            // not what this is).
            //
            // CONTROL_AF_MODE is deliberately NOT set here. This is a brand new builder, so it only
            // carries the TEMPLATE_PREVIEW defaults; the focus scan ran on its own separate builder
            // and its result is already just a float in hand, not a live AF state this request could
            // spoil. The final recording request sets AF_MODE_OFF plus LENS_FOCUS_DISTANCE anyway,
            // which puts the lens back where the focus scan asked for.
            val meteringBuilder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
            captureSession.setRepeatingRequest(meteringBuilder.build(), aeCallback, handler)

            // Never block the recording on AE: a scene that keeps AE searching must not hold up the
            // start of a capture. Wrapping the awaited value keeps the two null cases apart, same
            // trick as the AF scan: withTimeoutOrNull() returns null only when AE never answered,
            // while a wrapper holding null means AE answered but its values could not be read.
            val scanOutcome = withTimeoutOrNull(AE_CONVERGENCE_TIMEOUT_MS) { AeScanOutcome(converged.await()) }
            val measured = scanOutcome?.exposure
            // When CONTROL_AE_MODE_OFF is locked later, SENSOR_EXPOSURE_TIME must not be longer than
            // SENSOR_FRAME_DURATION or the request is invalid. A genuinely very dark scene can make
            // AE ask for a longer exposure than the fixed 30 fps frame duration allows, so cap it
            // instead of returning a value that cannot be locked.
            val maxExposureTimeNs = requestedProfile.frameDurationNs
            val resolved =
                measured?.copy(exposureTimeNs = measured.exposureTimeNs.coerceAtMost(maxExposureTimeNs))
                    ?: fallback
            val outcomeLabel =
                when {
                    measured != null -> "AE_CONVERGED (measured values used)"
                    scanOutcome != null -> "AE_UNREADABLE (requested values used)"
                    else -> "AE_TIMED_OUT (requested values used)"
                }
            // Kept as a permanent diagnostic log, same as the focus one: a real low-light field test
            // is the only way to confirm the measured values make sense for a night survey, and this
            // line is what that test reads. clampedExposure=true means the scene was dark enough that
            // AE wanted a longer exposure than 30 fps allows.
            Log.i(
                TAG,
                "Exposure lock result: outcome=$outcomeLabel lockedIso=${resolved.isoSensitivity} " +
                    "lockedExposureNs=${resolved.exposureTimeNs} measuredIso=${measured?.isoSensitivity} " +
                    "measuredExposureNs=${measured?.exposureTimeNs} " +
                    "clampedExposure=${measured != null && measured.exposureTimeNs > maxExposureTimeNs} " +
                    "maxExposureNs=$maxExposureTimeNs fallbackIso=${fallback.isoSensitivity} " +
                    "fallbackExposureNs=${fallback.exposureTimeNs} lastSeenAeState=$lastSeenAeState " +
                    "sensitivityRange=$sensitivityRange exposureTimeRange=$exposureTimeRange",
            )
            return resolved
        }

        // The real ISO and shutter time to lock for the recording. A data class, not a Pair, because
        // two plain numbers of the same shape are easy to mix up at the call site.
        private data class ResolvedExposure(
            val isoSensitivity: Int,
            val exposureTimeNs: Long,
        )

        // Tells "AE answered" apart from "AE never answered" around withTimeoutOrNull(), which
        // reports a timeout with the same null the unreadable-result case already uses. Same reason
        // as AfScanOutcome above. Only resolveExposure() uses it.
        private data class AeScanOutcome(val exposure: ResolvedExposure?)

        // Readable names for CONTROL_AF_STATE, used only by the diagnostic log above - Camera2 has
        // no built-in toString() for these int constants.
        private fun afStateLabel(state: Int?): String =
            when (state) {
                null -> "null"
                CaptureResult.CONTROL_AF_STATE_INACTIVE -> "INACTIVE"
                CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "PASSIVE_SCAN"
                CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "PASSIVE_FOCUSED"
                CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "ACTIVE_SCAN"
                CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "FOCUSED_LOCKED"
                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "NOT_FOCUSED_LOCKED"
                CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "PASSIVE_UNFOCUSED"
                else -> "UNKNOWN($state)"
            }

        private val captureCallback =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    lastCaptureResult = result
                    result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { timestamp ->
                        synchronized(timestampLock) { pendingSensorTimestamps.addLast(timestamp) }
                    }
                    if (awbLocked) return
                    val builder = requestBuilder ?: return
                    if (!exposureLockController.lockAwbIfConverged(builder, result)) return
                    awbLocked = true
                    // A CaptureRequest is immutable and Camera2 has no way to change one key on an
                    // in-flight repeating request, so locking AWB needs a full resubmit. The spike
                    // saw this exact resubmit desync the SENSOR_TIMESTAMP-to-frame FIFO pairing for
                    // the rest of a session, which is why takeSensorTimestampFor() checks every
                    // pairing against the learned offset instead of trusting queue order blindly.
                    // Guarded because this builder now has a second writer: updatePreviewSurface()
                    // adds and removes the preview target on this same camera thread. An uncaught
                    // throw here would run on the camera HandlerThread and kill the process, and
                    // with it the whole recording. Losing the AWB lock only costs colour
                    // consistency, so log it and keep recording.
                    runCatching { session.setRepeatingRequest(builder.build(), this, cameraHandler) }
                        .onFailure { failed -> Log.w(TAG, "Failed to resubmit the request for the AWB lock", failed) }
                }
            }

        // Any failure in here (MediaMuxer, a full disk from appendLine, Room) would otherwise
        // escape scope.launch and cancel the caller's scope with no error left to read. Keep it,
        // so stop() and Task 17d can see what went wrong.
        private suspend fun drainEncoderOutput() {
            try {
                drainUntilEndOfStream()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Throwable) {
                drainFailure = failed
                Log.e(TAG, "Encoder drain loop failed; the recording is broken from this point", failed)
                // Unblock a start() still waiting for segment 0 instead of making it wait out the
                // whole timeout.
                firstSegmentReady.completeExceptionally(failed)
            }
        }

        private suspend fun drainUntilEndOfStream() {
            val bufferInfo = MediaCodec.BufferInfo()
            while (currentCoroutineContext().isActive) {
                val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    openFirstSegment(mediaCodec.outputFormat)
                    continue
                }
                if (outputIndex < 0) continue

                // stop() signals end of input and then waits for this flag, so the frames still
                // held inside the encoder are muxed instead of being thrown away with the job.
                val isEndOfStream = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (isCodecConfig || bufferInfo.size == 0) {
                    // SPS/PPS header, already inside the MediaFormat given to MediaMuxer.addTrack().
                    // Muxing it as a sample corrupts the track, and counting it as a frame would
                    // steal one SENSOR_TIMESTAMP from the real first frame. The end-of-stream
                    // buffer is usually empty too, so it lands here.
                    mediaCodec.releaseOutputBuffer(outputIndex, false)
                    if (isEndOfStream) return
                    continue
                }

                // Fallback for a device that hands out a real buffer without ever returning
                // INFO_OUTPUT_FORMAT_CHANGED: the encoder HAS produced output by now, so
                // outputFormat is complete and safe to give to MediaMuxer.addTrack().
                if (encoderOutputFormat == null) openFirstSegment(mediaCodec.outputFormat)

                val outputBuffer = requireNotNull(mediaCodec.getOutputBuffer(outputIndex))
                val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                val sensorTimestampNs = takeSensorTimestampFor(bufferInfo.presentationTimeUs)

                pendingBuffer = outputBuffer
                pendingBufferInfo = bufferInfo
                currentMuxerPort?.setPendingSample(outputBuffer, bufferInfo)
                val closedSegment =
                    recorder.onEncodedFrame(isKeyFrame, bufferInfo.presentationTimeUs, sensorTimestampNs)

                // The frame that triggers a rotation is written to the NEW segment, so bump the
                // index before logging it: the server uses this `segment` field plus video_pts_us
                // to find the frame in the right file, and it must name the file it really is in.
                if (closedSegment != null) currentSegmentIndex = closedSegment.segmentIndex + 1

                val entry =
                    frameTimestampLogger.buildEntry(
                        frameIndex++,
                        sensorTimestampNs,
                        bufferInfo.presentationTimeUs,
                        currentSegmentIndex,
                    )
                frameTimestampWriter.appendLine(
                    """{"frame_index":${entry.frameIndex},"sensor_timestamp_ns":${entry.sensorTimestampNs},""" +
                        """"video_pts_us":${entry.videoPtsUs},"segment":${entry.segment}}""",
                )

                mediaCodec.releaseOutputBuffer(outputIndex, false)
                // The buffer belongs to the codec again, so drop our reference: a port built later
                // must never be staged with data that no longer exists.
                pendingBuffer = null
                pendingBufferInfo = null

                if (closedSegment != null) {
                    persistClosedSegment(closedSegment)
                    val path = File(sessionDir, "segment_$currentSegmentIndex.mp4").absolutePath
                    persistNewSegment(currentSegmentIndex, path)
                }

                if (isEndOfStream) return
            }
        }

        // Segment 0's MediaMuxer.addTrack() needs the encoder's real output format, and for H.264
        // that format only carries csd-0/csd-1 (SPS/PPS) once the encoder has produced output.
        // Opening it here instead of in start() is the point: in start() the encoder has not run a
        // single frame, so addTrack() would either fail or write a segment_0.mp4 with no codec
        // header. Idempotent, because the drain loop calls it from two places.
        private suspend fun openFirstSegment(format: MediaFormat) {
            if (encoderOutputFormat != null) return
            encoderOutputFormat = format
            val firstSegmentPath = File(sessionDir, "segment_0.mp4").absolutePath
            recorder.startSegment(0, firstSegmentPath)
            persistNewSegment(0, firstSegmentPath)
            firstSegmentReady.complete(Unit)
        }

        // FIFO pairing (the Nth capture result belongs to the Nth encoded frame) is correct while
        // the repeating request is never swapped, but the spike measured it going permanently wrong
        // right after the AWB-lock resubmit. What a swap leaves behind is capture results whose
        // frames never reached the encoder, so the queue head becomes far too old. Drop those.
        // Two rules keep this safe: never drain the queue empty, and never invent a timestamp while
        // a real one is still queued — dropping a stale head is a correction, making up a value is
        // not. Only a truly empty queue falls back to the learned offset.
        private fun takeSensorTimestampFor(presentationTimeUs: Long): Long =
            synchronized(timestampLock) {
                val offsetUs = ptsToSensorOffsetUs
                var dropped = 0
                if (offsetUs != null) {
                    val oldestAcceptableUs = presentationTimeUs + offsetUs - MAX_PAIRING_SKEW_US
                    while (pendingSensorTimestamps.size > 1 &&
                        pendingSensorTimestamps.first() / 1000 < oldestAcceptableUs
                    ) {
                        pendingSensorTimestamps.removeFirst()
                        dropped++
                    }
                }
                consecutiveGuardDrops = if (dropped > 0) consecutiveGuardDrops + 1 else 0
                if (dropped > 0) {
                    Log.w(
                        TAG,
                        "Timestamp guard dropped $dropped stale entries at pts=$presentationTimeUs " +
                            "(offset=${offsetUs}us, $consecutiveGuardDrops frames in a row)",
                    )
                }

                val paired = pendingSensorTimestamps.removeFirstOrNull()
                if (paired == null) {
                    Log.w(TAG, "No queued SENSOR_TIMESTAMP for pts=$presentationTimeUs; using offset=${offsetUs}us")
                    return@synchronized (presentationTimeUs + (offsetUs ?: 0L)) * 1000
                }
                if (offsetUs == null) {
                    ptsToSensorOffsetUs = paired / 1000 - presentationTimeUs
                    Log.i(TAG, "Learned pts->sensor offset: ${ptsToSensorOffsetUs}us")
                } else if (consecutiveGuardDrops >= MAX_CONSECUTIVE_GUARD_DROPS) {
                    // Dropping on every frame for this long means the anchor is wrong, not the
                    // queue. Re-learn it here; without this the guard would mis-pair forever with
                    // no way back, because the anchor is normally learned only once.
                    ptsToSensorOffsetUs = paired / 1000 - presentationTimeUs
                    consecutiveGuardDrops = 0
                    Log.w(TAG, "Re-anchored pts->sensor offset to ${ptsToSensorOffsetUs}us (was ${offsetUs}us)")
                }
                paired
            }

        // Called whenever the real preview Surface is destroyed (an ordinary Activity stop - screen
        // off, Home, an incoming call, not only rotation) or attached again. Only the preview
        // output is touched; the encoder output and the recording itself are never interrupted.
        //
        // The repeating request HAS to be resubmitted on both paths. A capture request routes to
        // one exact surface inside a shared OutputConfiguration (the framework resolves each
        // target to a stream id plus the surface's index in that config), so a newly added surface
        // gets no frames until a request targets it, and a surface still targeted by the repeating
        // request cannot be removed at all - CameraCaptureSession.updateOutputConfiguration says
        // removed surfaces "must not be part of any active repeating or single/burst request".
        // This is the same kind of resubmit the AWB lock does, so the same SENSOR_TIMESTAMP FIFO
        // desync can happen here; takeSensorTimestampFor()'s guard is what corrects it.
        //
        // Below API 28 this is a no-op: updateOutputConfiguration()/removeSurface() do not exist
        // there, so no live swap is possible and CaptureScreen's FLAG_KEEP_SCREEN_ON is the only
        // mitigation on those devices.
        fun updatePreviewSurface(surface: Surface?) {
            if (!supportsLivePreviewSwap) return
            val handler = cameraHandler ?: return
            if (!::captureSession.isInitialized || !::previewOutputConfig.isInitialized) return
            handler.post {
                val builder = requestBuilder ?: return@post
                runCatching {
                    val previous = currentSharedPreviewSurface
                    if (previous != null) {
                        // Stop targeting it first, otherwise removeSurface() below is rejected.
                        builder.removeTarget(previous)
                        captureSession.setRepeatingRequest(builder.build(), captureCallback, handler)
                        previewOutputConfig.removeSurface(previous)
                        currentSharedPreviewSurface = null
                    }
                    if (surface != null) previewOutputConfig.addSurface(surface)
                    captureSession.updateOutputConfiguration(previewOutputConfig)
                    if (surface != null) {
                        // Only now can a request name the new surface.
                        builder.addTarget(surface)
                        captureSession.setRepeatingRequest(builder.build(), captureCallback, handler)
                        currentSharedPreviewSurface = surface
                    }
                }.onFailure { failed ->
                    // The recording keeps going; only the on-screen preview is affected.
                    Log.w(TAG, "Failed to update the preview surface target; the preview may go blank", failed)
                    // A swap that failed halfway can leave this field disagreeing with what the
                    // config really holds, and the next swap would then remove or add the wrong
                    // Surface. Read the truth back instead of trusting the bookkeeping: the base
                    // is index 0, so anything past it is the attached real Surface, if any.
                    currentSharedPreviewSurface =
                        runCatching { previewOutputConfig.surfaces.getOrNull(1) }.getOrNull()
                }
            }
        }

        // Called from SurveyCaptureService (Task 17d) when the user stops recording. Returns the
        // REAL applied capture values (from the last CaptureResult) for capture_config.json, per
        // review feedback — not the requested LockedCameraProfile, which may not be exactly what
        // the sensor settled on.
        suspend fun stop(): FinalizedVideoCapture {
            try {
                // Stop the camera, tell the encoder no more input is coming, then let the drain
                // loop finish on its own so the frames still inside the encoder are muxed. Ending
                // the job first would simply throw them away.
                runCatching { captureSession.stopRepeating() }
                runCatching { mediaCodec.signalEndOfInputStream() }
                awaitDrainTail()

                // Make a mid-session drain failure visible even if nothing reads `failure`:
                // without this, stop() returns a normal-looking result for a broken recording.
                drainFailure?.let { Log.w(TAG, "The drain loop failed earlier; this recording is incomplete", it) }

                val finalSegment = recorder.stop()
                persistClosedSegment(finalSegment)

                val result = requireNotNull(lastCaptureResult) { "No CaptureResult observed before stop()" }
                val actualProfile =
                    LockedCameraProfile(
                        isoSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0,
                        exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
                        frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION) ?: 0L,
                        focusDistanceDiopters = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f,
                    )
                return FinalizedVideoCapture(finalSegment, actualProfile, Build.MANUFACTURER, Build.MODEL, cameraId)
            } finally {
                // Runs even when a step above throws, so the camera and the NDJSON tail are never
                // left behind by a failing stop().
                releaseCaptureResources()
            }
        }

        // Bounded so a stuck encoder cannot hang shutdown forever; after the timeout we give up on
        // the tail frames rather than never returning from stop().
        private suspend fun awaitDrainTail() {
            val job = drainJob ?: return
            val finished = withTimeoutOrNull(DRAIN_TAIL_TIMEOUT_MS) { job.join() }
            if (finished == null) {
                Log.w(TAG, "Encoder did not report end of stream within $DRAIN_TAIL_TIMEOUT_MS ms; dropping tail")
                job.cancelAndJoin()
            }
        }

        private suspend fun cancelDrainJob() {
            runCatching { drainJob?.cancelAndJoin() }
        }

        // Every step is guarded on its own: one failing release must not skip the rest, and some
        // of these fields are lateinit, so a failure early in start() leaves them unset.
        private fun releaseCaptureResources() {
            runCatching { frameTimestampWriter.close() }.onFailure { failed ->
                // Skip the case where start() failed before this field was ever set.
                if (failed is UninitializedPropertyAccessException) return@onFailure
                // A failing close (a full disk, for example) means the tail of
                // frame_timestamp_log.ndjson never reached the file. stop() would otherwise return
                // a successful-looking result, so record it like any other capture failure.
                Log.e(TAG, "Failed to close the frame timestamp log; its tail may be missing", failed)
                if (drainFailure == null) drainFailure = failed
            }
            runCatching { mediaCodec.stop() }
            runCatching { mediaCodec.release() }
            runCatching { captureSession.close() }
            runCatching { cameraDevice.close() }
            // Only after the session and the device are closed, so the camera can no longer be
            // holding this output. Releasing it while the session was still open logged
            // "BufferQueue has been abandoned" during normal teardown - harmless in itself (the
            // placeholder is never a capture-request target), but it is the same line the
            // real-device check greps for to catch the screen-off detach race, and a false
            // positive there would make that signal useless.
            // The Surface first, then the SurfaceTexture behind it.
            runCatching { placeholderPreviewSurface?.release() }
            runCatching { placeholderPreviewTexture?.release() }
            cameraThread?.quitSafely()
            cameraThread = null
            cameraHandler = null
        }

        private suspend fun persistNewSegment(
            index: Int,
            path: String,
        ) {
            sessionDao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    segmentIndex = index,
                    filePath = path,
                    startedAtElapsedNs = SystemClock.elapsedRealtimeNanos(),
                    endedAtElapsedNs = null,
                    sizeBytes = null,
                    checksumSha256 = null,
                ),
            )
        }

        private suspend fun persistClosedSegment(result: VideoSegmentResult) {
            val open = sessionDao.unfinalizedSegmentFor(sessionId) ?: return
            sessionDao.updateSegment(
                open.copy(endedAtElapsedNs = SystemClock.elapsedRealtimeNanos(), sizeBytes = result.sizeBytes),
            )
        }

        private suspend fun openCamera(
            cameraManager: CameraManager,
            cameraId: String,
            handler: Handler,
        ): CameraDevice =
            suspendCancellableCoroutine { continuation ->
                cameraManager.openCamera(
                    cameraId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) = continuation.resume(camera)

                        override fun onDisconnected(camera: CameraDevice) {
                            camera.close()
                            continuation.cancel()
                        }

                        override fun onError(
                            camera: CameraDevice,
                            error: Int,
                        ) {
                            camera.close()
                            continuation.cancel(IllegalStateException("Camera error $error"))
                        }
                    },
                    handler,
                )
            }

        private suspend fun createCaptureSession(
            camera: CameraDevice,
            encoderSurface: Surface,
            previewSurface: Surface,
            handler: Handler,
        ): CameraCaptureSession {
            if (!supportsLivePreviewSwap) {
                // Below API 28 no public Camera2 API can attach or detach a preview surface while
                // the session runs, so build the session the plain way and let
                // updatePreviewSurface() be a no-op. CaptureScreen's FLAG_KEEP_SCREEN_ON is the
                // only thing that helps on these devices.
                return awaitCaptureSession { callback ->
                    @Suppress("DEPRECATION")
                    camera.createCaptureSession(listOf(encoderSurface, previewSurface), callback, handler)
                }
            }

            val placeholderTexture =
                SurfaceTexture(false).apply { setDefaultBufferSize(VIDEO_WIDTH, VIDEO_HEIGHT) }
            val placeholderSurface = Surface(placeholderTexture)
            placeholderPreviewTexture = placeholderTexture
            placeholderPreviewSurface = placeholderSurface
            val previewConfig =
                OutputConfiguration(placeholderSurface).apply {
                    enableSurfaceSharing()
                    addSurface(previewSurface)
                }
            previewOutputConfig = previewConfig
            currentSharedPreviewSurface = previewSurface
            return awaitCaptureSession { callback ->
                @Suppress("DEPRECATION")
                camera.createCaptureSessionByOutputConfigurations(
                    listOf(OutputConfiguration(encoderSurface), previewConfig),
                    callback,
                    handler,
                )
            }
        }

        // Both session-creation paths report through the same StateCallback, so the callback is
        // written once here instead of twice.
        private suspend fun awaitCaptureSession(
            create: (CameraCaptureSession.StateCallback) -> Unit,
        ): CameraCaptureSession =
            suspendCancellableCoroutine { continuation ->
                create(
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) = continuation.resume(session)

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            continuation.cancel(IllegalStateException("Camera session configuration failed"))
                        }
                    },
                )
            }

        private fun createEncoder(profile: LockedCameraProfile): MediaCodec {
            val format =
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE_BPS)
                    setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, VIDEO_KEYFRAME_INTERVAL_S)
                }
            return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
        }

        // Finalized against the Task 2 spike's findings — do not change these without re-running it.
        private companion object {
            const val TAG = "VideoCaptureSession"
            const val DEQUEUE_TIMEOUT_US = 10_000L
            const val VIDEO_WIDTH = 1920
            const val VIDEO_HEIGHT = 1080
            const val VIDEO_BITRATE_BPS = 8_000_000
            const val VIDEO_FPS = 30
            const val VIDEO_KEYFRAME_INTERVAL_S = 2

            // At 30 fps the first encoded frame lands in well under a second; 10 s only exists so a
            // camera that never delivers a frame fails with a clear error instead of hanging.
            const val FIRST_SEGMENT_TIMEOUT_MS = 10_000L

            // Long enough to raise the phone and point it at the pole, short enough that the user
            // does not think the start button failed. Picked in the middle of that range; adjust
            // after a real-device run if it feels too short or too slow.
            const val PRE_SCAN_PREVIEW_MS = 1_800L

            // Generous for a one-shot AF scan (typically well under 1s) - only exists so a
            // low-contrast scene that never converges cannot delay the start of a recording for long.
            const val AF_CONVERGENCE_TIMEOUT_MS = 3_000L

            // AE usually settles faster than AF because it has no lens to move, but a very dark
            // scene needs a few long frames to meter, so keep a real window instead of a tight one.
            const val AE_CONVERGENCE_TIMEOUT_MS = 2_000L

            // ~7 frames at 30 fps. Deliberately loose: it must never fire on normal jitter or on a
            // learned offset that is a frame or two off, only on the kind of gross desync the spike
            // measured after a repeating-request swap (tens of seconds).
            const val MAX_PAIRING_SKEW_US = 250_000L

            // ~1 second at 30 fps. A real desync is corrected within a frame or two, so the guard
            // still firing after this long points at the anchor, not at the queue.
            const val MAX_CONSECUTIVE_GUARD_DROPS = 30

            // Long enough for the encoder to flush a couple of seconds of buffered frames.
            const val DRAIN_TAIL_TIMEOUT_MS = 5_000L
        }
    }
