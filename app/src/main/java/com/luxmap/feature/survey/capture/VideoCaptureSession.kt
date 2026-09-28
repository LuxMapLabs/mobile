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
import android.view.Surface
import com.luxmap.core.camera.ExposureLockController
import com.luxmap.core.camera.FrameTimestampLogger
import com.luxmap.core.camera.LockedCameraProfile
import com.luxmap.core.camera.SegmentRotationPolicy
import com.luxmap.core.camera.SegmentedVideoRecorder
import com.luxmap.core.camera.VideoSegmentResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.io.File
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

        // Cached from the ONE-TIME INFO_OUTPUT_FORMAT_CHANGED event. MediaCodec fires it once per
        // encoder lifetime, not once per segment, but every new segment's MediaMuxer still needs
        // it for addTrack() — the spike hit exactly this bug on its first rotation.
        private var encoderOutputFormat: MediaFormat? = null

        private var drainJob: Job? = null
        private val firstSegmentReady = CompletableDeferred<Unit>()

        suspend fun start(
            scope: CoroutineScope,
            sessionId: String,
            sessionDir: File,
            profile: LockedCameraProfile,
            segmentDurationMs: Long,
        ) {
            this.sessionId = sessionId
            this.sessionDir = sessionDir

            val thread = HandlerThread("luxmap-camera").apply { start() }
            cameraThread = thread
            val handler = Handler(thread.looper)
            cameraHandler = handler

            val cameraManager = context.getSystemService(CameraManager::class.java)
            cameraId = cameraManager.cameraIdList.first()
            cameraDevice = openCamera(cameraManager, cameraId, handler)

            mediaCodec = createEncoder(profile)
            val inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            recorder =
                SegmentedVideoRecorder(SegmentRotationPolicy(segmentDurationMs)) { path ->
                    val format = requireNotNull(encoderOutputFormat) { "Encoder output format is not known yet" }
                    RealMuxerPort(path, format).also { currentMuxerPort = it }
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
            // local_survey_video_segment row exists as soon as start() returns.
            try {
                withTimeout(FIRST_SEGMENT_TIMEOUT_MS) { firstSegmentReady.await() }
            } catch (timeout: TimeoutCancellationException) {
                releaseAfterFailedStart()
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

        private suspend fun drainEncoderOutput() {
            val bufferInfo = MediaCodec.BufferInfo()
            while (currentCoroutineContext().isActive) {
                val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    openFirstSegment(mediaCodec.outputFormat)
                    continue
                }
                if (outputIndex < 0) continue

                val isCodecConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (isCodecConfig || bufferInfo.size == 0) {
                    // SPS/PPS header, already inside the MediaFormat given to MediaMuxer.addTrack().
                    // Muxing it as a sample corrupts the track, and counting it as a frame would
                    // steal one SENSOR_TIMESTAMP from the real first frame.
                    mediaCodec.releaseOutputBuffer(outputIndex, false)
                    continue
                }

                // Fallback for a device that hands out a real buffer without ever returning
                // INFO_OUTPUT_FORMAT_CHANGED: the encoder HAS produced output by now, so
                // outputFormat is complete and safe to give to MediaMuxer.addTrack().
                if (encoderOutputFormat == null) openFirstSegment(mediaCodec.outputFormat)

                val outputBuffer = requireNotNull(mediaCodec.getOutputBuffer(outputIndex))
                val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                val sensorTimestampNs = takeSensorTimestampFor(bufferInfo.presentationTimeUs)

                currentMuxerPort?.setPendingSample(outputBuffer, bufferInfo)
                val closedSegment =
                    recorder.onEncodedFrame(isKeyFrame, bufferInfo.presentationTimeUs, sensorTimestampNs)

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

                if (closedSegment != null) {
                    persistClosedSegment(closedSegment)
                    currentSegmentIndex = closedSegment.segmentIndex + 1
                    val path = File(sessionDir, "segment_$currentSegmentIndex.mp4").absolutePath
                    persistNewSegment(currentSegmentIndex, path)
                }
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
                if (offsetUs != null) {
                    val oldestAcceptableUs = presentationTimeUs + offsetUs - MAX_PAIRING_SKEW_US
                    while (pendingSensorTimestamps.size > 1 &&
                        pendingSensorTimestamps.first() / 1000 < oldestAcceptableUs
                    ) {
                        pendingSensorTimestamps.removeFirst()
                    }
                }
                val paired =
                    pendingSensorTimestamps.removeFirstOrNull()
                        ?: return@synchronized (presentationTimeUs + (offsetUs ?: 0L)) * 1000
                if (offsetUs == null) ptsToSensorOffsetUs = paired / 1000 - presentationTimeUs
                paired
            }

        // Called from SurveyCaptureService (Task 17d) when the user stops recording. Returns the
        // REAL applied capture values (from the last CaptureResult) for capture_config.json, per
        // review feedback — not the requested LockedCameraProfile, which may not be exactly what
        // the sensor settled on.
        suspend fun stop(): FinalizedVideoCapture {
            drainJob?.cancelAndJoin()
            captureSession.stopRepeating()
            mediaCodec.signalEndOfInputStream()
            val finalSegment = recorder.stop()
            persistClosedSegment(finalSegment)
            frameTimestampWriter.close()
            mediaCodec.stop()
            mediaCodec.release()
            captureSession.close()
            cameraDevice.close()
            quitCameraThread()

            val result = requireNotNull(lastCaptureResult) { "No CaptureResult observed before stop()" }
            val actualProfile =
                LockedCameraProfile(
                    isoSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0,
                    exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
                    frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION) ?: 0L,
                )
            return FinalizedVideoCapture(finalSegment, actualProfile, Build.MANUFACTURER, Build.MODEL, cameraId)
        }

        // A failed start() must not leave the camera open, or the device stays locked for every
        // later attempt. Each step is guarded because we do not know how far start() got.
        private suspend fun releaseAfterFailedStart() {
            drainJob?.cancelAndJoin()
            runCatching { captureSession.stopRepeating() }
            runCatching { captureSession.close() }
            runCatching { mediaCodec.stop() }
            runCatching { mediaCodec.release() }
            runCatching { cameraDevice.close() }
            runCatching { frameTimestampWriter.close() }
            quitCameraThread()
        }

        private fun quitCameraThread() {
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
        }
    }
