package com.luxmap.feature.survey.capture

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
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
        ) {
            this.sessionId = sessionId
            this.sessionDir = sessionDir
            try {
                startCapture(scope, profile, segmentDurationMs)
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
            cameraDevice = openCamera(cameraManager, cameraId, handler)

            mediaCodec = createEncoder(profile)
            val inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            recorder =
                SegmentedVideoRecorder(SegmentRotationPolicy(segmentDurationMs)) { path ->
                    val format = requireNotNull(encoderOutputFormat) { "Encoder output format is not known yet" }
                    RealMuxerPort(path, format).also { port ->
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

            captureSession = createCaptureSession(cameraDevice, inputSurface, handler)
            val builder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(inputSurface)
                    exposureLockController.applyTo(this, profile)
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
                    session.setRepeatingRequest(builder.build(), this, cameraHandler)
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
            surface: Surface,
            handler: Handler,
        ): CameraCaptureSession =
            suspendCancellableCoroutine { continuation ->
                @Suppress("DEPRECATION")
                camera.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) = continuation.resume(session)

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            continuation.cancel(IllegalStateException("Camera session configuration failed"))
                        }
                    },
                    handler,
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
