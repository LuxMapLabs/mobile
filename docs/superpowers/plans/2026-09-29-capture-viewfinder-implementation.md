# Capture Mode Viewfinder Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Field Engineer a live camera preview on F04's Capture screen, so they can see what is being recorded instead of a blank screen with just text and buttons.

**Architecture:** Add a `TextureView` preview surface as a second, simultaneous target on the SAME Camera2 capture session that already feeds the video encoder (`VideoCaptureSession`) — one physical recording session, two outputs. Recording starts only once that preview `Surface` exists (Cách A, agreed in brainstorming): pressing "Bắt đầu quay" moves to a new `StartingRecording` state while the camera opens with both targets; a new `recordingStartResult` signal (mirroring the existing `gpsSignalState`/`packagingResult` bound-service pattern) tells the UI when the camera is actually ready, at which point the same `when` branch and the same live `TextureView` continue into `Recording` — no session swap, no preview flicker.

**Tech Stack:** Camera2 (`CameraCaptureSession` with 2 targets), `android.view.TextureView` via Jetpack Compose `AndroidView` interop, `android.view.Surface` (already `Parcelable`, passed through the existing `Intent`-based Service control channel) — no new dependency.

**Spec:** No separate spec file — this is a Bounded-path change (an existing, working flow: F04 Capture Mode). The full design was agreed in conversation during brainstorming (Cách A: preview only appears once recording starts, not before) and is captured in this plan's Architecture section and task-by-task interfaces below.

## Global Constraints

- Code comments in English only, simple common words (CLAUDE.md) — no exceptions, including comments explaining a workaround or a hardware limitation.
- No new third-party dependency (`TextureView`/`AndroidView`/`Surface` are all already-approved Android SDK / Jetpack Compose APIs, nothing to add to `build.gradle.kts`).
- Each commit ≤ 400 changed lines (CLAUDE.md) — none of this plan's tasks are expected to approach that on their own, but split further if a task's diff grows unexpectedly.
- `VideoCaptureSession.kt` is the most heavily reviewed file in the whole FM-08 plan (3 review rounds, several real bugs found in Task 17a). Task 1 touches it — review that task with the same level of scrutiny (most capable available model, hand-traced reasoning, no rubber-stamping), not a lighter pass because the change looks small.
- Camera2/Service/View code in this codebase has no automated test anywhere (established precedent across the whole FM-08 plan) — every task touching `VideoCaptureSession.kt`, `SurveyCaptureService.kt`, or `CaptureScreen.kt` verifies with a compile check only; real behavior is confirmed on a real device per the Review Focus section below. Only `CaptureViewModel.kt`'s changes (Task 3) get real unit tests, matching the existing `CaptureViewModelTest.kt`.

## Review Focus

- **Adding a second simultaneous 1920x1080 stream to the capture session can fail on lower Camera2 hardware levels (LEGACY/some LIMITED devices), and nothing in this plan adds a readiness check for it.** A reasonable person expects "the app already told me my camera works" (the existing exposure-lock readiness check) to mean pressing "Bắt đầu quay" reliably starts a recording — a `createCaptureSession` failure from adding the preview target would currently surface as `CaptureUiState.PackagingFailed` (the existing fail-loud path already covers it), not a hang or a crash. Task 1 and Task 2's steps end with "verify on a real device"; confirm this specific failure mode is at least reachable-and-reported, not silently swallowed, even if it cannot be forced on the device available for testing.
- **The preview is not orientation-corrected against `CameraCharacteristics.SENSOR_ORIENTATION`.** A reasonable person expects the live preview to look right-side-up and matching what the recorded video will show. This plan deliberately does not solve that (agreed as out of scope in brainstorming, Cách A was about *when* the preview appears, not its orientation correctness) — Task 4's own step says so explicitly so nobody "fixes" it as an afterthought without review, and it is listed again here so it is not lost.
- **`recordingStartResult` must never leak a previous session's value into a new session.** The FM-08 final review found and fixed this exact bug class for `boundService`/`_packagingResult` (a stale StateFlow value from a finished session being read as the new session's result). Task 2's steps reset `_recordingStartResult` to `null` in BOTH `SurveyCaptureService.startSessionInternal` (service-side, alongside the other per-session resets) AND `RealSurveyCaptureController.startSession` (controller-side, alongside the existing `boundService` reset) — verify both resets are present, not just one.
- **A `TextureView` torn down while its `Surface` is still an active Camera2 capture-session target is not specially handled for every case — only rotation is.** Task 1's implementer found this risk during self-review and confirmed the concrete trigger (this app does not lock orientation or declare `configChanges`, so a physical rotation recreates the Activity mid-recording); Task 4 Step 2 closes exactly that trigger by locking to portrait for the `StartingRecording`/`Recording` window. What remains genuinely unhandled: process death, an aggressive OS-level recreation unrelated to rotation, or the user somehow navigating away despite the `BackHandler` (already an accepted, narrow risk elsewhere in this codebase). This plan relies on `VideoCaptureSession`'s existing `releaseCaptureResources()`/`stop()` cleanup paths for those remaining cases, unmodified — no task adds new handling for them. Also verify on a real device that `requestedOrientation = SCREEN_ORIENTATION_PORTRAIT` actually prevents recreation on that device (some OEM multi-window/split-screen/foldable modes can still change the Activity's configuration despite an orientation lock) — if it does not, the fallback discussed with the user was a manifest-wide `android:screenOrientation="portrait"` on `MainActivity`, not a second scoped mechanism.
- **Every existing `CaptureViewModelTest.kt` test that calls `onStartRecording` and then expects `Recording` next now breaks**, because `onStartRecording` no longer transitions to `Recording` directly — it goes through `StartingRecording` first. Task 3's own step rewrites the whole test file; the risk here is a task reviewer accepting a diff that only adds new tests without checking the existing ones were actually updated (a stale existing test that still expects the old direct transition would fail to compile or fail at runtime — check for it explicitly, do not assume "existing tests still there" means "existing tests still correct").

---

### Task 1: Add a preview surface target to `VideoCaptureSession`'s Camera2 session

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt`

**Interfaces:**
- Consumes: nothing new — `Surface` (`android.view.Surface`) is already imported in this file.
- Produces: `suspend fun start(scope: CoroutineScope, sessionId: String, sessionDir: File, profile: LockedCameraProfile, segmentDurationMs: Long, previewSurface: Surface)` — one new required parameter, `previewSurface`, added at the end of the existing parameter list. Every other public member of this class (`stop()`, `failure`) is unchanged.

No automated test — real Camera2 hardware only (same reasoning as every other task that has ever touched this file). Verify with a compile check, then the real-device checklist after Task 4 (this task alone produces no user-visible change, since nothing calls the new parameter yet).

- [ ] **Step 1: Add the `previewSurface` parameter to `start()` and thread it through**

Change the `start()` function (currently at line 134) to:

```kotlin
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
```

- [ ] **Step 2: Add the parameter to `startCapture()` and pass both surfaces into the capture session and the request builder**

Change `startCapture()` (currently at line 155) to:

```kotlin
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

    captureSession = createCaptureSession(cameraDevice, inputSurface, previewSurface, handler)
    val builder =
        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(inputSurface)
            addTarget(previewSurface)
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
```

(The only changes from the current file: the new `previewSurface: Surface` parameter, passing it into `createCaptureSession(...)`, and the new `addTarget(previewSurface)` line. Everything else in this function is unchanged — copy it exactly so nothing is accidentally altered.)

- [ ] **Step 3: Add the second surface to `createCaptureSession()`**

Change `createCaptureSession()` (currently at line 517) to:

```kotlin
private suspend fun createCaptureSession(
    camera: CameraDevice,
    encoderSurface: Surface,
    previewSurface: Surface,
    handler: Handler,
): CameraCaptureSession =
    suspendCancellableCoroutine { continuation ->
        @Suppress("DEPRECATION")
        camera.createCaptureSession(
            listOf(encoderSurface, previewSurface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) = continuation.resume(session)

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    continuation.cancel(IllegalStateException("Camera session configuration failed"))
                }
            },
            handler,
        )
    }
```

(Renamed the first parameter from `surface` to `encoderSurface` for clarity now that there are two — this is a private function, so the rename has no effect on any caller outside this file.)

- [ ] **Step 4: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD FAILED — `SurveyCaptureService.kt` calls `videoSession.start(...)` without the new `previewSurface` parameter yet. This is expected; Task 2 fixes the caller. Confirm the ONLY error is that missing argument (not something else you introduced) before moving on.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt
git commit -m "feat(fm-08): add a preview surface target to the Camera2 capture session"
```

---

### Task 2: Add a `recordingStartResult` signal and thread the preview surface through the Service and Controller

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt`

**Interfaces:**
- Consumes: `VideoCaptureSession.start(..., previewSurface: Surface)` (Task 1).
- Produces: `sealed interface RecordingStartResult { data object Ready; data class Failed(val reason: String) }` (new, lives in `SurveyCaptureService.kt`); `SurveyCaptureService.recordingStartResult: StateFlow<RecordingStartResult?>` (new, null = still starting); `SurveyCaptureService.EXTRA_PREVIEW_SURFACE: String` (new Intent extra key); `SurveyCaptureController.startSession(sessionId: String, surveySweepId: String, luxDeviceAddress: String, previewSurface: Surface)` (adds the `previewSurface` parameter — interface AND `RealSurveyCaptureController` both change); `SurveyCaptureController.recordingStartResult: StateFlow<RecordingStartResult?>` (new, forwarded from the bound service the same way `gpsSignalState` already is).

No automated test — real Service/Camera2 only. Verify with a compile check.

- [ ] **Step 1: Add `RecordingStartResult` and the `recordingStartResult` field to `SurveyCaptureService`**

Add this new sealed interface right before the `SurveyCaptureService` class declaration (after the existing `private const val` lines, before `@AndroidEntryPoint`):

```kotlin
sealed interface RecordingStartResult {
    data object Ready : RecordingStartResult

    data class Failed(val reason: String) : RecordingStartResult
}
```

Add the new field next to the existing `_packagingResult`/`packagingResult` pair:

```kotlin
    private val _packagingResult = MutableStateFlow<PackageResult?>(null)
    val packagingResult: StateFlow<PackageResult?> = _packagingResult.asStateFlow()

    // null = still starting (or no session started yet). Reset to null at the top of every new
    // startSessionInternal call - see Review Focus on why a stale value from a finished session
    // must never leak into a new one.
    private val _recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
    val recordingStartResult: StateFlow<RecordingStartResult?> = _recordingStartResult.asStateFlow()
```

- [ ] **Step 2: Read the preview surface extra in `onStartCommand`**

Add the import (alongside the other `android.*`/`androidx.*` imports at the top of the file):

```kotlin
import android.view.Surface
import androidx.core.content.IntentCompat
```

Change the `ACTION_START` branch inside `onStartCommand` (currently at line 146) to:

```kotlin
            ACTION_START -> {
                if (isRecording) {
                    Log.w(TAG, "ACTION_START arrived while a session is already recording; ignoring it")
                } else {
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                    val surveySweepId = intent.getStringExtra(EXTRA_SURVEY_SWEEP_ID) ?: return START_NOT_STICKY
                    val luxDeviceAddress = intent.getStringExtra(EXTRA_LUX_DEVICE_ADDRESS) ?: return START_NOT_STICKY
                    val previewSurface =
                        IntentCompat.getParcelableExtra(intent, EXTRA_PREVIEW_SURFACE, Surface::class.java)
                            ?: return START_NOT_STICKY
                    isRecording = true
                    startSessionInternal(sessionId, surveySweepId, luxDeviceAddress, previewSurface)
                }
            }
```

- [ ] **Step 3: Add the parameter to `startSessionInternal`, reset `_recordingStartResult`, and pass the surface into `videoSession.start(...)`**

Change `startSessionInternal`'s signature (currently at line 162) to add `previewSurface: Surface,` as the fourth parameter (after `luxDeviceAddress: String,`).

Add `_recordingStartResult.value = null` to the existing block of per-session resets at the top of the function:

```kotlin
        val videoSession = videoCaptureSessionProvider.get()
        videoCaptureSession = videoSession
        isStopping = false
        brokenReason = null
        _packagingResult.value = null
        _recordingStartResult.value = null
```

Change the `videoStartJob` block (currently at line 251) to pass the surface through and set `_recordingStartResult` on both the success and failure paths:

```kotlin
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
                        previewSurface = previewSurface,
                    )
                    _recordingStartResult.value = RecordingStartResult.Ready
                } catch (error: Throwable) {
                    // Camera or encoder setup can still fail in the field after the readiness
                    // checklist passed (another app holding the camera, an encoder that cannot be
                    // allocated, or the new preview surface making the stream combination
                    // unsupported on this device - see Review Focus). VideoCaptureSession.start()
                    // rethrows those after releasing the camera, and an uncaught throw here would
                    // kill the app in the middle of a night survey.
                    //
                    // A CancellationException here means one of two very different things.
                    // openCamera's onDisconnected cancels its own continuation with no cause when
                    // another app takes the camera over - a real capture failure, and this
                    // coroutine is still active when it arrives. If serviceScope itself was
                    // cancelled (onDestroy), this coroutine is no longer active and the
                    // cancellation must keep propagating instead of being turned into a result.
                    if (error is CancellationException && !currentCoroutineContext().isActive) throw error
                    Log.e(TAG, "Video capture failed to start; this session has no video", error)
                    val reason = "Video capture failed to start: ${error.message}"
                    failSession(reason)
                    _recordingStartResult.value = RecordingStartResult.Failed(reason)
                }
            }
```

- [ ] **Step 4: Add the `EXTRA_PREVIEW_SURFACE` constant**

Add it next to the other `EXTRA_*` constants in the `companion object` (currently around line 479):

```kotlin
        const val EXTRA_PREVIEW_SURFACE = "preview_surface"
```

- [ ] **Step 5: Add `previewSurface` and `recordingStartResult` to `SurveyCaptureController`**

Read `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt` in full before editing — it has changed since the version quoted in earlier task history (it now resets `boundService` before every rebind and wraps `unbindService` in `runCatching`, from the FM-08 final review's fix wave). Apply these changes on top of its CURRENT content, not a version you remember from an older brief.

Add the import: `import android.view.Surface`

Change the `SurveyCaptureController` interface to:

```kotlin
interface SurveyCaptureController {
    fun startSession(
        sessionId: String,
        surveySweepId: String,
        luxDeviceAddress: String,
        previewSurface: Surface,
    )

    fun stopSession(): Flow<PackageResult>

    val gpsSignalState: StateFlow<GpsSignalState>
    val recordingStartResult: StateFlow<RecordingStartResult?>
}
```

In `RealSurveyCaptureController`, add the new forwarded StateFlow next to `_gpsSignalState`/`gpsSignalState`:

```kotlin
        private val _recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
        override val recordingStartResult: StateFlow<RecordingStartResult?> = _recordingStartResult.asStateFlow()

        private var recordingStartForwardingJob: Job? = null
```

In the `connection` object, forward the new signal the same way `gpsSignalState` is forwarded, and cancel the new job in both callbacks:

```kotlin
        private val connection =
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
                    gpsForwardingJob = controllerScope.launch { service.gpsSignalState.collect { _gpsSignalState.value = it } }
                    recordingStartForwardingJob?.cancel()
                    recordingStartForwardingJob =
                        controllerScope.launch {
                            service.recordingStartResult.collect { _recordingStartResult.value = it }
                        }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    boundService.value = null
                    gpsForwardingJob?.cancel()
                    recordingStartForwardingJob?.cancel()
                }
            }
```

Change `startSession()` to accept and forward the preview surface, and to reset `_recordingStartResult` for the new session (same reasoning as `boundService`'s existing reset — see Review Focus):

```kotlin
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
            boundService.value = null
            _recordingStartResult.value = null
            context.bindService(Intent(context, SurveyCaptureService::class.java), connection, Context.BIND_AUTO_CREATE)
        }
```

(This is the current `startSession()` body with one new line added: `_recordingStartResult.value = null`, plus the new `previewSurface` parameter threaded into the Intent extra. Everything else is unchanged — copy the current file's actual `stopSession()` and the rest of the class as-is; this step only touches `startSession()`.)

- [ ] **Step 6: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD FAILED — `CaptureViewModel.kt` calls `captureController.startSession(...)` with only 3 arguments. This is expected; Task 3 fixes the caller. Confirm this is the only error.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt
git commit -m "feat(fm-08): add a recordingStartResult signal and thread the preview surface through the service"
```

---

### Task 3: Add the `StartingRecording` flow to `CaptureViewModel` (TDD)

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `SurveyCaptureController.startSession(sessionId, surveySweepId, luxDeviceAddress, previewSurface: Surface)` and `.recordingStartResult: StateFlow<RecordingStartResult?>` (Task 2).
- Produces: `CaptureUiState.StartingRecording` (new `data object`); `CaptureViewModel.onPreviewSurfaceReady(surface: Surface)` (new function, called by Task 4's UI once the preview `Surface` exists).

Real unit test (MockK + Turbine), same pattern as the rest of this file.

- [ ] **Step 1: Add `StartingRecording` to `CaptureUiState`**

Read the current file first — it already has `Connecting`/`Scanning`/`ScanTimedOut` states added for the BLE device picker (a change made after this plan's brainstorming session; the version below is written against that current file, not the older version some earlier history may reference).

Add `StartingRecording` between `Ready` and `Recording`:

```kotlin
    data object Ready : CaptureUiState

    // Between pressing "Bat dau quay" and the camera actually being open with a live preview
    // (Cach A, agreed in brainstorming) - CaptureScreen keeps the same TextureView alive through
    // this state and into Recording, so the preview never flickers or resets.
    data object StartingRecording : CaptureUiState

    data class Recording(
        val gpsSignalLost: Boolean = false,
        val bleGapDetected: Boolean = false,
    ) : CaptureUiState
```

- [ ] **Step 2: Write the failing tests**

Read the CURRENT `CaptureViewModelTest.kt` in full first — do not guess its content from an older version. Replace the whole file with:

```kotlin
package com.luxmap.feature.survey.ui.capture

import android.view.Surface
import app.cash.turbine.test
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxDevice
import com.luxmap.core.ble.LuxDevicePreferences
import com.luxmap.core.ble.LuxDeviceScanner
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.location.GpsSignalState
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.RecordingStartResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val SENSOR_1 = LuxDevice(name = "LUX-001", address = "AA:AA:AA:AA:AA:AA")
private val SENSOR_2 = LuxDevice(name = "LUX-002", address = "BB:BB:BB:BB:BB:BB")

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Builds a ViewModel whose luxClient/scanner/preferences/controller behavior is fully
    // controlled by the caller - every test wires only the mocks its scenario needs.
    private fun viewModel(
        connectionState: MutableStateFlow<BleConnectionState> = MutableStateFlow(BleConnectionState.Disconnected),
        rememberedDevice: LuxDevice? = null,
        scanResults: List<LuxDevice> = emptyList(),
        gpsSignalState: MutableStateFlow<GpsSignalState> = MutableStateFlow(GpsSignalState.Ok),
        recordingStartResult: MutableStateFlow<RecordingStartResult?> = MutableStateFlow(null),
    ): Triple<CaptureViewModel, LuxSensorBleClient, SurveyCaptureController> {
        val luxClient = mockk<LuxSensorBleClient>()
        every { luxClient.connectionState } returns connectionState
        every { luxClient.connect(any()) } just Runs
        every { luxClient.disconnect() } just Runs

        val scanner = mockk<LuxDeviceScanner>()
        every { scanner.scan() } returns flowOf(*scanResults.toTypedArray())

        val preferences = mockk<LuxDevicePreferences>()
        coEvery { preferences.lastDevice() } returns rememberedDevice
        coEvery { preferences.saveLastDevice(any()) } just Runs

        val controller = mockk<SurveyCaptureController>(relaxed = true)
        every { controller.gpsSignalState } returns gpsSignalState
        every { controller.recordingStartResult } returns recordingStartResult

        val viewModel = CaptureViewModel(luxClient, scanner, preferences, controller)
        return Triple(viewModel, luxClient, controller)
    }

    @Test
    fun `with no remembered device, starts scanning and connects once a device is selected`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val (viewModel, luxClient, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = null,
                    scanResults = listOf(SENSOR_1, SENSOR_2),
                )

            viewModel.uiState.test {
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_1)), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_1, SENSOR_2)), awaitItem())

                viewModel.onDeviceSelected(SENSOR_2)
                assertEquals(CaptureUiState.Connecting(SENSOR_2.name), awaitItem())

                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready, awaitItem())
            }
            verify { luxClient.connect(SENSOR_2.address) }
        }

    @Test
    fun `a remembered device connects automatically without scanning`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val (viewModel, luxClient, _) =
                viewModel(connectionState = connectionState, rememberedDevice = SENSOR_1)

            viewModel.uiState.test {
                // Scanning() is the StateFlow's field default - it is always the first value a
                // collector sees, before init's coroutine has had a chance to check for a
                // remembered device.
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Connecting(SENSOR_1.name), awaitItem())
                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready, awaitItem())
            }
            verify { luxClient.connect(SENSOR_1.address) }
        }

    @Test
    fun `connecting to a device saves it so it is remembered next time`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val preferences = mockk<LuxDevicePreferences>()
            coEvery { preferences.lastDevice() } returns null
            coEvery { preferences.saveLastDevice(any()) } just Runs
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            every { luxClient.connect(any()) } just Runs
            val scanner = mockk<LuxDeviceScanner>()
            every { scanner.scan() } returns flowOf(SENSOR_1)
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns MutableStateFlow(GpsSignalState.Ok)
            every { controller.recordingStartResult } returns MutableStateFlow(null)
            val viewModel = CaptureViewModel(luxClient, scanner, preferences, controller)

            viewModel.uiState.test {
                awaitItem() // Scanning()
                awaitItem() // Scanning([SENSOR_1])
                viewModel.onDeviceSelected(SENSOR_1)
                awaitItem() // Connecting
                connectionState.value = BleConnectionState.Connected
                awaitItem() // Ready
            }
            coVerify { preferences.saveLastDevice(SENSOR_1) }
        }

    @Test
    fun `an empty scan times out, and retrying scans again`() =
        runTest {
            val (viewModel, _, _) = viewModel(rememberedDevice = null, scanResults = emptyList())

            viewModel.uiState.test {
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.ScanTimedOut, awaitItem())

                viewModel.onRetryScan()
                assertEquals(CaptureUiState.Scanning(), awaitItem())
            }
        }

    @Test
    fun `changing device disconnects the current one and starts a fresh scan`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val (viewModel, luxClient, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    scanResults = listOf(SENSOR_2),
                )

            viewModel.uiState.test {
                awaitItem() // Scanning() field default, before init checks for a remembered device
                awaitItem() // Connecting to the remembered device
                assertEquals(CaptureUiState.Ready, awaitItem())

                viewModel.onChangeDevice()
                assertEquals(CaptureUiState.Scanning(), awaitItem())
                assertEquals(CaptureUiState.Scanning(listOf(SENSOR_2)), awaitItem())
            }
            verify { luxClient.disconnect() }
        }

    @Test
    fun `pressing start moves to StartingRecording, and the preview becoming ready starts the session`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            val surface = mockk<Surface>()

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                assertEquals(CaptureUiState.Ready, awaitItem())

                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                assertEquals(CaptureUiState.StartingRecording, awaitItem())

                viewModel.onPreviewSurfaceReady(surface)
                recordingStartResult.value = RecordingStartResult.Ready
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)
            }
            verify { controller.startSession(any(), "SWEEP-1", SENSOR_1.address, surface) }
        }

    @Test
    fun `a failed camera start surfaces PackagingFailed`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            val surface = mockk<Surface>()

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(surface)
                recordingStartResult.value = RecordingStartResult.Failed("camera busy")
                val failed = awaitItem() as CaptureUiState.PackagingFailed
                assertEquals("camera busy", failed.reason)
            }
        }

    @Test
    fun `stopping a recording moves through Packaging to Packaged on success`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.stopSession() } returns flowOf(PackageResult.Success("/data/manifest.json"))

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(mockk())
                recordingStartResult.value = RecordingStartResult.Ready
                awaitItem() // Recording
                viewModel.onStopRecording()
                assertEquals(CaptureUiState.Packaging, awaitItem())
                val packaged = awaitItem() as CaptureUiState.Packaged
                assertTrue(packaged.sessionId.isNotBlank())
            }
        }

    @Test
    fun `stopping a recording surfaces PackagingFailed on failure`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, controller) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    recordingStartResult = recordingStartResult,
                )
            every { controller.stopSession() } returns flowOf(PackageResult.Failure("disk full"))

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(mockk())
                recordingStartResult.value = RecordingStartResult.Ready
                awaitItem() // Recording
                viewModel.onStopRecording()
                assertEquals(CaptureUiState.Packaging, awaitItem())
                val failed = awaitItem() as CaptureUiState.PackagingFailed
                assertEquals("disk full", failed.reason)
            }
        }

    @Test
    fun `GPS signal lost while recording raises the warning flag without leaving Recording`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
            val recordingStartResult = MutableStateFlow<RecordingStartResult?>(null)
            val (viewModel, _, _) =
                viewModel(
                    connectionState = connectionState,
                    rememberedDevice = SENSOR_1,
                    gpsSignalState = gpsSignalState,
                    recordingStartResult = recordingStartResult,
                )

            viewModel.uiState.test {
                awaitItem() // Scanning() field default
                awaitItem() // Connecting
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1")
                awaitItem() // StartingRecording
                viewModel.onPreviewSurfaceReady(mockk())
                recordingStartResult.value = RecordingStartResult.Ready
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)

                gpsSignalState.value = GpsSignalState.Lost

                val warned = awaitItem() as CaptureUiState.Recording
                assertEquals(true, warned.gpsSignalLost)
            }
        }
}
```

- [ ] **Step 3: Run to verify the new tests fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: FAIL — `onPreviewSurfaceReady`/`CaptureUiState.StartingRecording`/`RecordingStartResult` do not exist yet (compile error), or the existing-behavior tests fail at runtime because `onStartRecording` still jumps straight to `Recording`. Either failure shape confirms the tests exercise the not-yet-built behavior.

- [ ] **Step 4: Implement the `StartingRecording` flow in `CaptureViewModel`**

Read the CURRENT `CaptureViewModel.kt` in full first (it already has the BLE device-picker logic from this plan's earlier brainstorming session — `pendingDevice`, `scanJob`, `startScan()`, etc. — this step only adds to it, it does not replace it).

Add the import: `import android.view.Surface` and `import com.luxmap.feature.survey.capture.RecordingStartResult`.

Add a new field next to `pendingDevice`/`scanJob`:

```kotlin
        // The surveySweepId passed to onStartRecording, held until the preview surface is ready
        // and startSession() can actually be called (Cach A - see the plan this came from).
        private var pendingSurveySweepId: String = ""
```

Add a new collector inside `init`, alongside the existing `luxClient.connectionState` and `captureController.gpsSignalState` collectors:

```kotlin
            viewModelScope.launch {
                captureController.recordingStartResult.collect { result ->
                    if (_uiState.value !is CaptureUiState.StartingRecording) return@collect
                    when (result) {
                        RecordingStartResult.Ready -> _uiState.value = CaptureUiState.Recording()
                        is RecordingStartResult.Failed -> _uiState.value = CaptureUiState.PackagingFailed(result.reason)
                        null -> Unit
                    }
                }
            }
```

Change `onStartRecording` to move to `StartingRecording` instead of calling `startSession` directly:

```kotlin
        fun onStartRecording(surveySweepId: String) {
            if (_uiState.value != CaptureUiState.Ready) return
            pendingSurveySweepId = surveySweepId
            _uiState.value = CaptureUiState.StartingRecording
        }
```

Add the new function that actually starts the session, once the preview surface exists:

```kotlin
        fun onPreviewSurfaceReady(surface: Surface) {
            if (_uiState.value !is CaptureUiState.StartingRecording) return
            // pendingDevice is the device the connectionState collector already confirmed
            // Connected to reach Ready - it cannot be null here, but a session cannot start
            // without an address either way, so this is checked rather than assumed with !!.
            val luxDeviceAddress = pendingDevice?.address ?: return
            sessionId = UUID.randomUUID().toString()
            captureController.startSession(sessionId, pendingSurveySweepId, luxDeviceAddress, surface)
        }
```

- [ ] **Step 5: Add the one new `when` branch `CaptureScreen.kt` needs to compile**

**Why this step exists (a plan gap found during implementation, not part of the original brief):** `CaptureUiState` is a `sealed interface`, and `CaptureScreen.kt` (which already exists — it was NOT built by this plan; it's the F04 screen from the earlier BLE device-picker work) already switches over every `CaptureUiState` case with an exhaustive `when` and no `else` branch, per this project's own CLAUDE.md rule. Adding `StartingRecording` in Step 1 above makes that `when` non-exhaustive, which is a compile error in `CaptureScreen.kt` — a file outside this task's original file list. `:app:testDebugUnitTest` cannot run at all while any main-source file fails to compile, so this task cannot reach GREEN without this one addition. Task 1 and Task 2 had the same kind of transitional breakage in their own downstream callers, but neither of them needed to run tests to finish, so it didn't block them the way it blocks this task.

Add ONLY this one branch to the existing `when (val state = uiState) { ... }` in `CaptureScreen.kt`, in the same position Task 4 was going to add it (between the `Ready` branch and the `Recording` branch) — do not touch anything else in that file, the `TextureView`/`AndroidView`/orientation-lock work is still entirely Task 4's job:

```kotlin
                is CaptureUiState.StartingRecording ->
                    Text("Đang mở camera...", style = MaterialTheme.typography.bodyLarge)
```

- [ ] **Step 6: Run to verify the tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: PASS, all 11 tests.

- [ ] **Step 7: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL. Fix any formatting issues (`./gradlew ktlintFormat` if needed, then re-check).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(fm-08): add StartingRecording state, wait for the preview surface before recording"
```

---

### Task 4: Add the `TextureView` preview to `CaptureScreen`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`

**Note (plan amended after Task 3 ran into this):** the `is CaptureUiState.StartingRecording -> Text("Đang mở camera...", ...)` branch this task originally added in its own Step 1 was moved to **Task 3 Step 5**, because `CaptureScreen.kt`'s exhaustive `when` had to compile before Task 3's tests could run. That branch already exists in the file by the time this task starts — do not re-add it or you will get a duplicate-branch compile error. Everything else below (the `TextureView`/`AndroidView`, the imports, the orientation lock) is still this task's own, unstarted work.

**Interfaces:**
- Consumes: `CaptureUiState.StartingRecording` and `CaptureViewModel.onPreviewSurfaceReady(surface: Surface)` (Task 3).
- Produces: nothing new — no other file depends on anything from `CaptureScreen.kt`.

No automated test — real `TextureView`/Camera2 only. Verify with a compile check, then the real-device checklist below.

- [ ] **Step 1: Add the persistent `TextureView` and the `StartingRecording` branch**

Read the CURRENT `CaptureScreen.kt` in full first (it already has the BLE device-picker UI from this plan's earlier brainstorming session — `Connecting`/`Scanning`/`ScanTimedOut` branches, the `DeviceRow` composable — this step only adds to it, it does not replace those).

Add these imports:

```kotlin
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.ui.viewinterop.AndroidView
```

Inside the `Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ... }` block, add the `AndroidView` as the FIRST child, before the existing `Column { ... }`:

```kotlin
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Shown for both StartingRecording and Recording, from the SAME `if` branch, so Compose
        // keeps the same TextureView (and the same underlying Surface) alive across that
        // transition instead of tearing it down and recreating it - the Camera2 capture session
        // is built once, against this exact Surface object, when the preview becomes ready.
        if (uiState is CaptureUiState.StartingRecording || uiState is CaptureUiState.Recording) {
            AndroidView(
                factory = { context ->
                    TextureView(context).apply {
                        surfaceTextureListener =
                            object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    // Must match a size the camera can actually use as a second,
                                    // simultaneous stream alongside the 1920x1080 encoder surface -
                                    // reusing that same resolution is the one already known to
                                    // work. Whether every device accepts two streams at this size
                                    // together still needs a real-device check (see Review Focus).
                                    surfaceTexture.setDefaultBufferSize(1920, 1080)
                                    viewModel.onPreviewSurfaceReady(Surface(surfaceTexture))
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surfaceTexture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture) = true

                                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                            }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column {
```

(The `Column { ... }` and everything inside it stays exactly as it currently is in the file — this step does not change any existing `when` branch's content, it only wraps the existing `Column` with the new `AndroidView` as a sibling inside the same `Box`. The `is CaptureUiState.StartingRecording -> Text("Đang mở camera...", ...)` branch this step used to add here was moved to Task 3 Step 5 — it already exists in the file, do not add it again.)

- [ ] **Step 2: Lock the screen to portrait while a recording is starting or in progress**

**Why this step exists:** Task 1's implementer found a real risk during self-review, confirmed against this app's actual manifest: `AndroidManifest.xml` does not lock `MainActivity`'s orientation and does not declare `android:configChanges` for rotation, so a physical device rotation triggers the default Android behavior of destroying and recreating the whole Activity. The `TextureView` this step's Step 1 just added would be destroyed and recreated too — but its `Surface` is, by that point, a live target on an ACTIVE Camera2 capture session for a real, possibly 10-30 minute recording. Losing that surface out from under a running recording is a real risk to the recording itself, not just to the preview. Agreed with the user: lock orientation to portrait only while this screen is in `StartingRecording` or `Recording` — scoped to exactly the window where it matters, with no effect on any other screen's rotation behavior.

Add these imports:

```kotlin
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
```

Add this right after the existing `BackHandler(...)` call, before the `packagedSessionId`/`LaunchedEffect` block:

```kotlin
    val context = LocalContext.current
    val isDuringRecordingWindow = uiState is CaptureUiState.StartingRecording || uiState is CaptureUiState.Recording
    // The preview Surface (Step 1 above) is a live target on an active Camera2 capture session -
    // a physical rotation would otherwise destroy and recreate this Activity (no orientation lock
    // or configChanges declared for it), tearing that Surface down out from under a running
    // recording. Locking only for this window, not the whole app, keeps every other screen's
    // rotation behavior unchanged.
    DisposableEffect(isDuringRecordingWindow) {
        val activity = context as? Activity
        if (isDuringRecordingWindow) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            if (isDuringRecordingWindow) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
```

(This runs alongside the existing `BackHandler` and `LaunchedEffect` calls at the top of `CaptureScreen` — do not remove or reorder those, this is a new, independent effect added next to them.)

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — this is the last task, so with all four tasks done the whole module should compile cleanly.

- [ ] **Step 4: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full unit test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass (including the 11 `CaptureViewModelTest` tests from Task 3, unaffected by this UI-only task).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt
git commit -m "feat(fm-08): show a live camera preview while starting and during recording"
```

---

## After all tasks: real-device verification

This plan has no automated coverage for the actual camera behavior (Task 1, 2, and 4 all touch real Camera2/Service/View code with no automated test, per Global Constraints). Before treating this feature as done, install on a real device and check:

- [ ] Pressing "Bắt đầu quay" shows "Đang mở camera..." briefly, then the live camera preview appears and stays visible through the whole recording — no flicker, black flash, or the preview disappearing and reappearing at the `StartingRecording -> Recording` transition (confirms the same `TextureView`/`Surface` survives that transition, per Task 4's design).
- [ ] The recorded video (pull `segment_0.mp4` afterward, same way earlier FM-08 real-device checks did) still decodes correctly and is not visibly different in quality from before this plan (confirms adding the preview target did not disturb the encoder's own stream).
- [ ] Stopping and starting a second recording in the same screen session (without leaving `CaptureScreen`) still shows the preview correctly the second time — confirms `recordingStartResult`'s reset (Task 2, Review Focus) actually works and a stale value from the first recording does not leak into the second.
- [ ] If a way to force a camera busy/unavailable condition is available (for example, opening another camera app and switching back quickly), confirm the failure surfaces as "Đóng gói thất bại: ..." instead of the screen hanging on "Đang mở camera..." forever (confirms the existing fail-loud path still works with the added preview target).
- [ ] Note whatever the preview's orientation looks like (right-side up, sideways, mirrored) without trying to fix it — this plan deliberately does not correct it (see Review Focus); the observation just informs whether a follow-up task is worth prioritizing.
- [ ] Try physically rotating the device to landscape while `StartingRecording`/`Recording` is showing — confirm the screen stays in portrait (does not rotate) and the recording is NOT interrupted. This is the concrete real-device check for Task 4 Step 2's orientation lock; if the device still rotates or the recording drops, escalate before shipping — see Review Focus for the manifest-wide fallback discussed with the user.
