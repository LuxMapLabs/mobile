# Survey Capture (F03 + F04 core + F06 interface) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the night-survey capture pipeline for Field Engineers — an assigned-route readiness check (F03), a video+GPS+heading+BLE-lux recording session that writes a "Session package v0" to local storage (F04), and an upload interface stub (F06) — without inventing any server-side name or endpoint that still needs Backend sign-off.

**Architecture:** MVVM + Repository pattern per `CLAUDE.md`. Five independent, unit-tested core modules (`core/camera`, `core/location`, `core/ble`) write raw data during a session; a foreground service orchestrates them; a packaging use case turns the raw files into a checksummed, manifested package stored in a new Room table. F03 and the core modules can be built in parallel; F04 wiring depends on both; F06 depends on F04's packaged output.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (new to this repo — added in Task 1), Coroutines/StateFlow, Camera2 + `MediaCodec`/`MediaMuxer` (not CameraX `VideoCapture`, not `MediaRecorder`), Fused Location, `SensorManager`, plain `android.bluetooth` GATT APIs (no BLE library — none is on the approved stack), JUnit + MockK + Turbine + Room Testing + Compose UI Test.

**Spec:** `docs/superpowers/specs/2026-09-28-survey-capture-design.md` — this plan implements it section by section; executors should read both documents. Numbered section references below (e.g. "spec §8") point into that file.

## Global Constraints

- Video pipeline is **Camera2 → `MediaCodec` (surface input) → `MediaMuxer`** only — never `MediaRecorder` or CameraX `VideoCapture` (spec §5).
- Video is recorded in **2–5 minute segments**, one `MediaMuxer` file per segment, rotated at a keyframe boundary (spec §5, §7).
- Every session timestamp uses `SystemClock.elapsedRealtimeNanos()` **except** `frame_timestamp_log.ndjson`'s `sensor_timestamp_ns`, which comes from Camera2's `SENSOR_TIMESTAMP` (spec §8).
- `schema_version` is a **header line** (`{"schema_version":"v0","file_role":"..."}`) on every `.ndjson` file, and a **JSON field** (not a header line) on `manifest.json` and `capture_config.json` (spec §8).
- `.ndjson` files are **flushed to disk every ~1 second**; readers must tolerate a truncated last line (spec §12).
- If `SENSOR_INFO_TIMESTAMP_SOURCE != REALTIME`, F03 **hard-blocks** entry to F04 — temporary policy, comment it as such (spec §10).
- If the BLE lux sensor disconnects mid-session: **do not stop the session** — keep recording video/GPS/heading, set `ble_gap_detected = true` **only on a Connected→Disconnected transition** (never on the connection `StateFlow`'s initial value), warn with color+text+vibration, and auto-reconnect (spec §11).
- Exposure lock sets `CONTROL_AE_MODE_OFF` with `SENSOR_SENSITIVITY`/`SENSOR_EXPOSURE_TIME`/`SENSOR_FRAME_DURATION` all explicit; **AWB is locked via `CONTROL_AWB_LOCK` once converged**, never driven to `CONTROL_AWB_MODE_OFF` without also supplying `COLOR_CORRECTION_GAINS`; `NOISE_REDUCTION_MODE`/`EDGE_MODE` are left at the camera default pending a WP4 decision (see `docs/contract-drift.md`).
- No new third-party library beyond what `CLAUDE.md`'s "Ngăn xếp công nghệ" already lists — BLE and video encoding use only framework APIs; Room DAO tests that need real SQLite run as instrumented tests (`app/src/androidTest`) rather than pulling in Robolectric.
- Every commit stays ≤400 changed lines per `CLAUDE.md`; split a task's steps across multiple commits when a single step would exceed that.
- Run `./gradlew ktlintCheck` before considering any task done; fix formatting before moving on.
- F05, F07, and F08–F10 are explicitly **out of scope** — do not add code, enums, or screens for them.
- `docs/contract-drift.md` must exist and list every temporary name/assumption introduced by this plan (Task 1 creates it if absent — check first, spec §14).

## Review Focus

- **BLE `seq` wraparound** (uint16 rolling over 65535→0) misread as total packet loss instead of a normal rollover — pinned in Task 8.
- **GPS signal lost for an extended period** mid-session with no visible warning to the driver — pinned in Task 6.
- **Storage exhausted mid-recording** (a segment write fails partway through) silently producing a truncated, unusable video file instead of a surfaced error — pinned in Task 5.
- **Crash recovery running on a session that has zero video segments at all** (killed before the first segment ever opened) — must not crash, must still transition cleanly — pinned in Task 16.
- **`PackageSurveySessionUseCase` running with a file listed on the session missing from disk** (e.g. storage cleared) — must fail loudly with a clear reason rather than writing a manifest that lies — pinned in Task 15.

---

## File Structure

```
app/build.gradle.kts                                          (modify — Room deps)
gradle/libs.versions.toml                                      (modify — Room version/libs)
app/src/main/AndroidManifest.xml                                (modify — permissions, service)

app/src/main/java/com/luxmap/core/database/AppDatabase.kt      (create — Task 9, not Task 1: Room rejects an empty @Database)
app/src/main/java/com/luxmap/core/database/InstantConverters.kt (create — Task 13)
app/src/main/java/com/luxmap/di/DatabaseModule.kt               (create — Task 9)

app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt   (create)
app/src/main/java/com/luxmap/core/camera/FrameTimestampLogger.kt     (create)
app/src/main/java/com/luxmap/core/camera/SegmentRotationPolicy.kt    (create)
app/src/main/java/com/luxmap/core/camera/SegmentedVideoRecorder.kt   (create)
app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt    (create)
app/src/main/java/com/luxmap/core/location/HeadingSensor.kt          (create)
app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt              (create)
app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt          (create — Task 8 skeleton, rewritten by Task 17c)
app/src/main/java/com/luxmap/core/ble/LuxDeviceScanner.kt            (create — Task 17c)

app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyPlanEntity.kt        (create)
app/src/main/java/com/luxmap/feature/survey/data/entity/LocalRoadSegmentEntity.kt       (create)
app/src/main/java/com/luxmap/feature/survey/data/dao/SurveyPlanDao.kt                   (create)
app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt                    (create)
app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt                (create)
app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt (create)
app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt      (create — Task 12)
app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt                (create)
app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt              (create)
app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt                 (create)

app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt       (create)
app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyVideoSegmentEntity.kt  (create)
app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt                  (create)
app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogWriter.kt                    (create)
app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogReader.kt                    (create)
app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt         (create)
app/src/main/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCase.kt        (create)
app/src/main/java/com/luxmap/feature/survey/capture/RealMuxerPort.kt                       (create — Task 17a)
app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt                 (create — Task 17a)
app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt             (create — Task 17b)
app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt                 (create — Task 17d)
app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt                (create — Task 17d)
app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt             (create — Task 18)
app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt                  (create)
app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt                (create)
app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt                   (create)

app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt      (create)
app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt  (create)
app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt    (create)
app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt  (create)
app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt     (create)

app/src/main/java/com/luxmap/di/RepositoryModule.kt   (modify — bind new repositories)
app/src/main/java/com/luxmap/di/CaptureModule.kt      (create in Task 12 for SurveyReadinessInputProvider, modified in Task 18 to add SurveyCaptureController)
app/src/main/java/com/luxmap/navigation/NavGraph.kt   (modify — add F03/F04/F06 routes)
app/src/main/java/com/luxmap/navigation/Routes.kt     (modify — add route constants)
app/src/main/java/com/luxmap/LuxMapApp.kt             (modify — Task 16, wire crash recovery)

docs/contract-drift.md   (create if absent)
CLAUDE.md                (modify — Task 0, see below)
```

---

## Task 0: Update `CLAUDE.md` before any code

Spec §15 requires this as its own commit, before any implementation code.

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Edit the "Khoảng trống đã biết" F07 entry**

In `CLAUDE.md`, find the bullet starting with `**F07 (Nhập lux) — ĐÃ CHỐT...`. Replace its entire content with:

```markdown
- **F07 (Nhập lux) — ĐÃ BỊ LOẠI BỎ khỏi đặc tả (cập nhật 2026-09-27):** không còn màn nhập lux thủ công. Không dùng `lux_reading`/`local_lux_reading`/`POST /api/v1/lux-readings`. Nguồn lux cho RQ1/CV-12 chuyển sang log lux BLE ghi trong phiên quay ở F04 (xem mục C7 của `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`), còn chờ WP4 xác nhận cách dùng lại dữ liệu này cho đối chiếu độ chính xác.
```

- [ ] **Step 2: Replace the F03/F04 naming-freeze rule**

Find the paragraph in the "Cảm biến lux qua BLE..." bullet that says *"Vẫn còn khoảng trống thật: chưa có tên bảng Room, tên field log lux/GPS, hay endpoint upload chính thức... không tự đặt tên khi code F03/F04"*. Replace that sentence with:

```markdown
**Cập nhật 2026-09-28 (mục C8.5 của đặc tả):** phân quyền đặt tên đã chốt — bảng Room cục bộ do WP6 (mobile) tự đặt tên chính thức, không cần chờ WP2/WP5; định dạng file trong "Session package" (log lux, GPS track, log timestamp frame, file cấu hình quay, manifest) do mobile soạn bản đầu (`v0`), WP4 duyệt sau; chỉ endpoint upload và tên field phía server vẫn chờ Backend (WP2/WP5). Mọi tên tạm và điểm lệch được theo dõi ở `docs/contract-drift.md`.
```

- [ ] **Step 3: Add a pointer to `docs/contract-drift.md`**

In the same section, after the paragraph edited in Step 2, add:

```markdown
Xem `docs/contract-drift.md` để biết chính xác tên nào đã chốt, tên nào còn tạm.
```

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(fm-survey): reconcile CLAUDE.md with C8.5 naming authority and F07 removal"
```

---

## Task 1: Add Room to the project

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `docs/contract-drift.md` (only if it does not already exist — check first, per spec §14)

**Interfaces:**
- Produces: the Room Gradle dependencies (runtime, ktx, ksp compiler, testing), available for Task 9 to use.

**Correction found while executing this plan (Round-1 blocker, real):** the original version of this task also created an empty `AppDatabase` (`@Database(entities = [], ...)`) and its `DatabaseModule`. Room's KSP processor rejects an empty `entities` list at compile time (`@Database annotation must specify list of entities`) — reproduced directly, not a implementer misdiagnosis. `AppDatabase.kt` and `DatabaseModule.kt` cannot exist until there is at least one real `@Entity`, so their creation moves to Task 9 (the first task with entities to put in it). This task now only wires the Gradle dependencies and the drift log — no Room-annotated Kotlin code yet.

- [ ] **Step 1: Check whether `docs/contract-drift.md` already exists**

```bash
ls docs/contract-drift.md 2>/dev/null || echo "does not exist"
```

If it prints "does not exist", create it now with this content; otherwise skip this step and leave the existing file untouched.

```markdown
# Contract drift log

Temporary names, assumptions, and anything still pending Backend/WP2/WP5/firmware
sign-off, introduced while building the survey capture feature
(`docs/superpowers/specs/2026-09-28-survey-capture-design.md`).

Room table names below are **official** (WP6 authority per spec C8.5) — listed here
only for traceability, not because they are pending.

| Item | Current value in code | Status | Owner |
|---|---|---|---|
| `sync_queue` entity type for a survey session package | `survey_session_package` (string literal) | Internal WP6 choice, not yet reviewed by anyone else | WP6 |
| BLE service/characteristic UUID | placeholder constants in `LuxSensorBleClient` | Proposal only, not confirmed with firmware | WP6 + firmware owner |
| BLE packet byte layout (`seq`/`module_ms`/`lux` types, endianness) | uint16 LE / uint32 LE / float32 LE (proposed) | Proposal only | WP6 + firmware owner |
| BLE `boot_id` field | assumed present, format TBD with firmware | Proposal only | WP6 + firmware owner |
| "Kỹ sư bảo trì" == `Manager` role | assumed equivalent in F03 copy/comments | Assumption, not confirmed | thinh2509 |
| Upload endpoint for a session package | none — `UploadRepository` has no real implementation yet | Blocked on Backend | WP2/WP5 |
| Server-side field names for a session package | none | Blocked on Backend | WP2/WP5 |
```

- [ ] **Step 2: Add Room version and libraries to the version catalog**

In `gradle/libs.versions.toml`, add to `[versions]` (after `mockk`):

```toml
room = "2.6.1"
```

Add to `[libraries]` (after `androidx-datastore-preferences`):

```toml
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

- [ ] **Step 3: Wire the dependencies into `app/build.gradle.kts`**

Add after the `implementation(libs.androidx.datastore.preferences)` line:

```kotlin
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
```

Add after `testImplementation(libs.kotlinx.coroutines.test)`:

```kotlin
    testImplementation(libs.androidx.room.testing)
```

- [ ] **Step 4: Build to confirm the Gradle wiring resolves**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. No Room-annotated code exists yet — this only proves the dependencies resolve and nothing else in the project broke by adding them. Room's own toolchain (KSP + `AppDatabase`) is proven in Task 9, where the first real `@Entity` exists — `@Database(entities = [])` is rejected by Room's KSP processor at compile time, so an empty database cannot be built or verified before then.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts docs/contract-drift.md
git commit -m "chore(fm-survey): add room gradle dependencies"
```

---

## Task 2: Real-device spike — video pipeline feasibility (a0)

This is an **investigation task**, not TDD — there is no failing test to write first. It produces a written finding, gates every later camera task, and must run before Task 5 starts.

**Files:**
- Create: `docs/superpowers/specs/2026-09-28-survey-capture-spike-findings.md`

- [ ] **Step 1: Write a throwaway spike app or `adb`-driven test harness**

On at least 2–3 real Android devices (different models) available to the team, write a minimal Camera2 + `MediaCodec` (surface input) + `MediaMuxer` recording loop (can live in a scratch branch/module, not part of the app's production source — delete it after the spike, it is not part of this plan's File Structure). Record ≥10 minutes continuously with:
- `CONTROL_AE_MODE_OFF`, `CONTROL_AF_MODE_OFF`, `CONTROL_AWB_MODE_OFF`, fixed ISO/shutter, `CONTROL_VIDEO_STABILIZATION_MODE_OFF`, `NOISE_REDUCTION_MODE_OFF`, `EDGE_MODE_OFF`.
- A `MediaMuxer` rotation every 2–5 minutes.

- [ ] **Step 2: Record findings for each of the 4 spike questions from spec §4**

Fill in `docs/superpowers/specs/2026-09-28-survey-capture-spike-findings.md`:

```markdown
# Survey capture spike findings (2026-09-28)

Devices tested: <fill in model + Android version for each>

1. Config held for 10+ minutes? <yes/no per device, notes on any AE/AF drift observed>
2. Segment rotation at keyframe: frame loss or visible glitch at the boundary? <findings>
3. MediaCodec PTS vs Camera2 SENSOR_TIMESTAMP: <measured average offset in microseconds per device, or "not comparable" with why>
4. SENSOR_INFO_TIMESTAMP_SOURCE per device: <REALTIME / UNKNOWN, per device>

## Decision
<Camera2 -> MediaCodec -> MediaMuxer confirmed viable, or blocked — if blocked, STOP and raise
with the project owner before starting Task 5, per spec §4.>

## Chosen defaults for capture_config.json (spec §8)
- resolution: <value>
- fps: <value>
- bitrate_bps: <value>
- keyframe_interval_s: <value>
- segment_duration_s: <value, 2-5 min range>
- iso / shutter_ns: <values, or "chosen per-device at F03 checklist time" if it varies>
```

- [ ] **Step 3: Commit the findings**

```bash
git add docs/superpowers/specs/2026-09-28-survey-capture-spike-findings.md
git commit -m "docs(fm-survey): record video pipeline spike findings"
```

**If the spike found the pipeline unviable or the PTS/SENSOR_TIMESTAMP offset uncontrolled:** stop here and raise it with the project owner before proceeding to Task 5 — do not silently substitute a different pipeline.

---

## Task 3: `ExposureLockController`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/camera/ExposureLockControllerTest.kt`
- Modify: `docs/contract-drift.md`

**Interfaces:**
- Produces: `data class LockedCameraProfile(val isoSensitivity: Int, val exposureTimeNs: Long, val frameDurationNs: Long, val focusDistanceDiopters: Float = 0f)`; `class ExposureLockController { fun lockedRequestKeys(profile: LockedCameraProfile): Map<CaptureRequest.Key<*>, Any>; fun applyTo(builder: CaptureRequest.Builder, profile: LockedCameraProfile); fun isTimestampSourceRealtime(characteristics: CameraCharacteristics): Boolean; fun lockAwbIfConverged(builder: CaptureRequest.Builder, latestResult: CaptureResult): Boolean }`.

`CaptureRequest.Key` constants (`CaptureRequest.CONTROL_AE_MODE`, etc.) are real object instances backed by the Android platform, not plain compile-time constants — on a plain JVM unit test (`app/src/test`) referencing them either returns `null` or throws under the stub `android.jar`, with no reliable way to assert real values without Robolectric (not on the approved test stack). This test therefore runs as an **instrumented test** (`app/src/androidTest`, on an emulator/device) instead.

Two corrections from the original review of this task:
- **AWB is not forced `OFF` blind.** `CONTROL_AWB_MODE_OFF` requires the app to also supply `COLOR_CORRECTION_GAINS`/`COLOR_CORRECTION_TRANSFORM`, which this class does not compute. Instead, `lockedRequestKeys` leaves AWB in `CONTROL_AWB_MODE_AUTO` (the Camera2 default) and `lockAwbIfConverged` locks it via `CONTROL_AWB_LOCK = true` once the running session's `CaptureResult.CONTROL_AWB_STATE` reports `CONVERGED` — the standard "let it settle, then lock" idiom.
- **`SENSOR_FRAME_DURATION` must be set whenever `CONTROL_AE_MODE_OFF` is set**, or the frame rate becomes undefined — added to `LockedCameraProfile` and `lockedRequestKeys`.
- **`NOISE_REDUCTION_MODE`/`EDGE_MODE` are deliberately NOT forced `OFF` here** — whether disabling in-camera noise reduction/edge enhancement helps or hurts the CV pipeline is a WP4 (CV-Analytics) call, not a mobile-side default to unilaterally bake in. Tracked in `docs/contract-drift.md` (Step 1 below) instead of decided here.

- [ ] **Step 1: Record the pending WP4 decision in `docs/contract-drift.md`**

Add a row to the table in `docs/contract-drift.md` (created in Task 1):

```markdown
| NOISE_REDUCTION_MODE / EDGE_MODE for video capture | left at camera default (neither forced OFF) | Pending WP4 decision — see ExposureLockController | WP4 |
```

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/androidTest/java/com/luxmap/core/camera/ExposureLockControllerTest.kt
package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExposureLockControllerTest {
    private val controller = ExposureLockController()

    @Test
    fun lockedRequestKeysFixIsoExposureFrameDurationAndDisableAfAndStabilization() {
        val profile = LockedCameraProfile(isoSensitivity = 800, exposureTimeNs = 20_000_000L, frameDurationNs = 33_333_333L)

        val keys = controller.lockedRequestKeys(profile)

        assertEquals(CaptureRequest.CONTROL_AE_MODE_OFF, keys[CaptureRequest.CONTROL_AE_MODE])
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, keys[CaptureRequest.CONTROL_AF_MODE])
        assertEquals(800, keys[CaptureRequest.SENSOR_SENSITIVITY])
        assertEquals(20_000_000L, keys[CaptureRequest.SENSOR_EXPOSURE_TIME])
        assertEquals(33_333_333L, keys[CaptureRequest.SENSOR_FRAME_DURATION])
        assertEquals(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            keys[CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE],
        )
    }

    @Test
    fun lockedRequestKeysDoNotForceNoiseReductionOrEdgeMode() {
        val profile = LockedCameraProfile(isoSensitivity = 800, exposureTimeNs = 20_000_000L, frameDurationNs = 33_333_333L)

        val keys = controller.lockedRequestKeys(profile)

        assertNull(keys[CaptureRequest.NOISE_REDUCTION_MODE])
        assertNull(keys[CaptureRequest.EDGE_MODE])
    }

    @Test
    fun timestampSourceRealtimeReturnsTrueOnlyWhenCharacteristicEqualsRealtime() {
        val realtimeCharacteristics = mockk<CameraCharacteristics>()
        every { realtimeCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) } returns
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
        val unknownCharacteristics = mockk<CameraCharacteristics>()
        every { unknownCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) } returns
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN

        assertTrue(controller.isTimestampSourceRealtime(realtimeCharacteristics))
        assertFalse(controller.isTimestampSourceRealtime(unknownCharacteristics))
    }

    @Test
    fun lockAwbIfConvergedLocksOnlyOnceAwbStateIsConverged() {
        val builder = mockk<CaptureRequest.Builder>(relaxed = true)
        val notConverged = mockk<CaptureResult>()
        every { notConverged.get(CaptureResult.CONTROL_AWB_STATE) } returns CaptureResult.CONTROL_AWB_STATE_SEARCHING
        val converged = mockk<CaptureResult>()
        every { converged.get(CaptureResult.CONTROL_AWB_STATE) } returns CaptureResult.CONTROL_AWB_STATE_CONVERGED

        assertFalse(controller.lockAwbIfConverged(builder, notConverged))
        assertTrue(controller.lockAwbIfConverged(builder, converged))
        verify { builder.set(CaptureRequest.CONTROL_AWB_LOCK, true) }
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.core.camera.ExposureLockControllerTest"` (needs a connected emulator or device)
Expected: FAIL — `ExposureLockController` does not exist yet.

- [ ] **Step 4: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt
package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import javax.inject.Inject

// Held across a whole recording session (spec: "áp dụng và giữ suốt phiên quay"), not just one shot.
data class LockedCameraProfile(
    val isoSensitivity: Int,
    val exposureTimeNs: Long,
    // Required whenever CONTROL_AE_MODE_OFF is set — otherwise the frame rate is undefined.
    val frameDurationNs: Long,
    val focusDistanceDiopters: Float = 0f,
)

class ExposureLockController
    @Inject
    constructor() {
        // Pure map so this is testable without a real CaptureRequest.Builder — applyTo() below
        // does the actual mutation and is only meaningfully verified on a real device (spike, spec §16).
        // AWB and NOISE_REDUCTION_MODE/EDGE_MODE are intentionally absent — see lockAwbIfConverged
        // and docs/contract-drift.md.
        fun lockedRequestKeys(profile: LockedCameraProfile): Map<CaptureRequest.Key<*>, Any> =
            mapOf(
                CaptureRequest.CONTROL_AE_MODE to CaptureRequest.CONTROL_AE_MODE_OFF,
                CaptureRequest.CONTROL_AF_MODE to CaptureRequest.CONTROL_AF_MODE_OFF,
                CaptureRequest.SENSOR_SENSITIVITY to profile.isoSensitivity,
                CaptureRequest.SENSOR_EXPOSURE_TIME to profile.exposureTimeNs,
                CaptureRequest.SENSOR_FRAME_DURATION to profile.frameDurationNs,
                CaptureRequest.LENS_FOCUS_DISTANCE to profile.focusDistanceDiopters,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE to CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            )

        fun applyTo(
            builder: CaptureRequest.Builder,
            profile: LockedCameraProfile,
        ) {
            lockedRequestKeys(profile).forEach { (key, value) ->
                @Suppress("UNCHECKED_CAST")
                builder.set(key as CaptureRequest.Key<Any>, value)
            }
        }

        fun isTimestampSourceRealtime(characteristics: CameraCharacteristics): Boolean =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
                CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME

        // Called on every CaptureResult from the repeating request until it returns true; the
        // caller (Task 17a) then stops calling it and keeps reusing the now-locked builder.
        fun lockAwbIfConverged(
            builder: CaptureRequest.Builder,
            latestResult: CaptureResult,
        ): Boolean {
            if (latestResult.get(CaptureResult.CONTROL_AWB_STATE) != CaptureResult.CONTROL_AWB_STATE_CONVERGED) {
                return false
            }
            builder.set(CaptureRequest.CONTROL_AWB_LOCK, true)
            return true
        }
    }
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.core.camera.ExposureLockControllerTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt \
  app/src/androidTest/java/com/luxmap/core/camera/ExposureLockControllerTest.kt \
  docs/contract-drift.md
git commit -m "feat(fm-survey): add ExposureLockController with awb-lock-on-convergence"
```

---

## Task 4: `SegmentRotationPolicy` and `FrameTimestampLogger`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/camera/SegmentRotationPolicy.kt`
- Create: `app/src/main/java/com/luxmap/core/camera/FrameTimestampLogger.kt`
- Test: `app/src/test/java/com/luxmap/core/camera/SegmentRotationPolicyTest.kt`
- Test: `app/src/test/java/com/luxmap/core/camera/FrameTimestampLoggerTest.kt`

**Interfaces:**
- Produces: `class SegmentRotationPolicy(private val targetDurationMs: Long) { fun shouldRotate(currentSegmentDurationMs: Long, isKeyFrame: Boolean): Boolean }`; `data class FrameTimestampEntry(val frameIndex: Int, val sensorTimestampNs: Long, val videoPtsUs: Long, val segment: Int)`; `class FrameTimestampLogger { fun buildEntry(frameIndex: Int, sensorTimestampNs: Long, videoPtsUs: Long, segment: Int): FrameTimestampEntry }`.
- Consumes (later, Task 5): `SegmentRotationPolicy.shouldRotate`.
- Consumes (later, Task 14): `FrameTimestampEntry` is serialized by `NdjsonLogWriter`.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/luxmap/core/camera/SegmentRotationPolicyTest.kt
package com.luxmap.core.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentRotationPolicyTest {
    private val policy = SegmentRotationPolicy(targetDurationMs = 180_000L) // 3 minutes

    @Test
    fun `does not rotate before target duration even at a keyframe`() {
        assertFalse(policy.shouldRotate(currentSegmentDurationMs = 60_000L, isKeyFrame = true))
    }

    @Test
    fun `does not rotate at target duration if not a keyframe`() {
        assertFalse(policy.shouldRotate(currentSegmentDurationMs = 180_000L, isKeyFrame = false))
    }

    @Test
    fun `rotates once duration has passed target and frame is a keyframe`() {
        assertTrue(policy.shouldRotate(currentSegmentDurationMs = 181_000L, isKeyFrame = true))
    }
}
```

```kotlin
// app/src/test/java/com/luxmap/core/camera/FrameTimestampLoggerTest.kt
package com.luxmap.core.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameTimestampLoggerTest {
    private val logger = FrameTimestampLogger()

    @Test
    fun `builds an entry carrying the segment index alongside both timestamp sources`() {
        val entry = logger.buildEntry(frameIndex = 42, sensorTimestampNs = 123_456_789L, videoPtsUs = 5_000_000L, segment = 1)

        assertEquals(42, entry.frameIndex)
        assertEquals(123_456_789L, entry.sensorTimestampNs)
        assertEquals(5_000_000L, entry.videoPtsUs)
        assertEquals(1, entry.segment)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.camera.SegmentRotationPolicyTest" --tests "com.luxmap.core.camera.FrameTimestampLoggerTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/camera/SegmentRotationPolicy.kt
package com.luxmap.core.camera

// Rotation must land on a keyframe (spec §4/§5) so MediaMuxer can close cleanly without corrupting
// the segment — waiting past targetDurationMs for the next keyframe if none lands exactly on it.
class SegmentRotationPolicy(
    private val targetDurationMs: Long,
) {
    fun shouldRotate(
        currentSegmentDurationMs: Long,
        isKeyFrame: Boolean,
    ): Boolean = currentSegmentDurationMs >= targetDurationMs && isKeyFrame
}
```

```kotlin
// app/src/main/java/com/luxmap/core/camera/FrameTimestampLogger.kt
package com.luxmap.core.camera

import javax.inject.Inject

// sensorTimestampNs comes from Camera2's SENSOR_TIMESTAMP, not SystemClock.elapsedRealtimeNanos()
// directly — kept as its own field name per spec §8 because the two only coincide when
// SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME (confirmed per device by the Task 2 spike).
data class FrameTimestampEntry(
    val frameIndex: Int,
    val sensorTimestampNs: Long,
    val videoPtsUs: Long,
    val segment: Int,
)

class FrameTimestampLogger
    @Inject
    constructor() {
        fun buildEntry(
            frameIndex: Int,
            sensorTimestampNs: Long,
            videoPtsUs: Long,
            segment: Int,
        ): FrameTimestampEntry = FrameTimestampEntry(frameIndex, sensorTimestampNs, videoPtsUs, segment)
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.camera.SegmentRotationPolicyTest" --tests "com.luxmap.core.camera.FrameTimestampLoggerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/camera/SegmentRotationPolicy.kt \
  app/src/main/java/com/luxmap/core/camera/FrameTimestampLogger.kt \
  app/src/test/java/com/luxmap/core/camera/SegmentRotationPolicyTest.kt \
  app/src/test/java/com/luxmap/core/camera/FrameTimestampLoggerTest.kt
git commit -m "feat(fm-survey): add segment rotation policy and frame timestamp logger"
```

---

## Task 5: `SegmentedVideoRecorder`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/camera/SegmentedVideoRecorder.kt`
- Test: `app/src/test/java/com/luxmap/core/camera/SegmentedVideoRecorderTest.kt`

**Interfaces:**
- Consumes: `SegmentRotationPolicy.shouldRotate`.
- Produces: `data class VideoSegmentResult(val segmentIndex: Int, val filePath: String, val sizeBytes: Long)`; `interface SegmentedVideoRecorder { fun startSegment(segmentIndex: Int, outputFilePath: String); fun onEncodedFrame(isKeyFrame: Boolean, presentationTimeUs: Long, sensorTimestampNs: Long): VideoSegmentResult?; fun stop(): VideoSegmentResult }`. `onEncodedFrame` returns a non-null `VideoSegmentResult` exactly when a rotation just closed a segment, so the caller (Task 17a's `VideoCaptureSession`) knows to open the next `local_survey_video_segment` row.

Real `MediaCodec`/`MediaMuxer` I/O is not unit-testable without a device or Robolectric (neither is on this project's test stack) — this task unit-tests the **bookkeeping logic** (when to rotate, what gets reported, how a write failure surfaces) against a fake encoder/muxer pair, and leaves the real encoder wiring to be verified by the Task 2 spike and the real-device checklist (spec §16).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/core/camera/SegmentedVideoRecorderTest.kt
package com.luxmap.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

private class FakeMuxerPort : MuxerPort {
    var closedSizeBytes: Long = 0
    var written = 0
    var failNextWrite = false

    override fun writeSample(presentationTimeUs: Long) {
        if (failNextWrite) throw IOException("disk full")
        written++
    }

    override fun close(): Long = closedSizeBytes
}

class SegmentedVideoRecorderTest {
    private val policy = SegmentRotationPolicy(targetDurationMs = 180_000L)

    @Test
    fun `rotating a segment reports the closed segment's result and starts the next index`() {
        val muxer = FakeMuxerPort().apply { closedSizeBytes = 4_096L }
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        val result =
            recorder.onEncodedFrame(
                isKeyFrame = true,
                presentationTimeUs = 181_000_000L,
                sensorTimestampNs = 181_000_000_000L,
            )

        assertEquals(VideoSegmentResult(segmentIndex = 0, filePath = "/tmp/segment_0.mp4", sizeBytes = 4_096L), result)
    }

    @Test
    fun `does not rotate before the target duration`() {
        val muxer = FakeMuxerPort()
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        val result =
            recorder.onEncodedFrame(isKeyFrame = true, presentationTimeUs = 1_000_000L, sensorTimestampNs = 1_000_000_000L)

        assertNull(result)
    }

    @Test
    fun `a write failure surfaces as an exception instead of a silently truncated file`() {
        val muxer = FakeMuxerPort().apply { failNextWrite = true }
        val recorder = SegmentedVideoRecorder(policy) { _ -> muxer }
        recorder.startSegment(segmentIndex = 0, outputFilePath = "/tmp/segment_0.mp4")

        assertThrows(IOException::class.java) {
            recorder.onEncodedFrame(isKeyFrame = false, presentationTimeUs = 1_000L, sensorTimestampNs = 1_000L)
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.camera.SegmentedVideoRecorderTest"`
Expected: FAIL — `SegmentedVideoRecorder` and `MuxerPort` do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/camera/SegmentedVideoRecorder.kt
package com.luxmap.core.camera

// Thin seam over MediaMuxer so the rotation bookkeeping above is testable without a real encoder.
// The real implementation wraps android.media.MediaMuxer.writeSampleData/stop+release.
interface MuxerPort {
    fun writeSample(presentationTimeUs: Long)

    // Returns the final file size once closed.
    fun close(): Long
}

data class VideoSegmentResult(
    val segmentIndex: Int,
    val filePath: String,
    val sizeBytes: Long,
)

// openMuxer builds the MuxerPort for a given output file path — injected so tests can substitute
// a fake without touching android.media.MediaMuxer.
class SegmentedVideoRecorder(
    private val rotationPolicy: SegmentRotationPolicy,
    private val openMuxer: (outputFilePath: String) -> MuxerPort,
) {
    private var currentSegmentIndex = -1
    private var currentFilePath = ""
    private var currentMuxer: MuxerPort? = null
    private var segmentStartUs = 0L

    fun startSegment(
        segmentIndex: Int,
        outputFilePath: String,
    ) {
        currentSegmentIndex = segmentIndex
        currentFilePath = outputFilePath
        currentMuxer = openMuxer(outputFilePath)
        segmentStartUs = -1L
    }

    // Returns the just-closed segment's result when this frame triggers a rotation, null otherwise.
    fun onEncodedFrame(
        isKeyFrame: Boolean,
        presentationTimeUs: Long,
        sensorTimestampNs: Long,
    ): VideoSegmentResult? {
        if (segmentStartUs < 0) segmentStartUs = presentationTimeUs
        val muxer = requireNotNull(currentMuxer) { "startSegment() must be called before onEncodedFrame()" }
        muxer.writeSample(presentationTimeUs)

        val currentDurationMs = (presentationTimeUs - segmentStartUs) / 1000
        if (!rotationPolicy.shouldRotate(currentDurationMs, isKeyFrame)) return null

        val closedResult = closeCurrentSegment()
        startSegment(currentSegmentIndex + 1, nextSegmentPath(currentFilePath, currentSegmentIndex + 1))
        return closedResult
    }

    fun stop(): VideoSegmentResult = closeCurrentSegment()

    private fun closeCurrentSegment(): VideoSegmentResult {
        val muxer = requireNotNull(currentMuxer) { "No active segment to close" }
        val sizeBytes = muxer.close()
        return VideoSegmentResult(currentSegmentIndex, currentFilePath, sizeBytes)
    }

    private fun nextSegmentPath(
        previousPath: String,
        nextIndex: Int,
    ): String = previousPath.replaceAfterLast("segment_", "$nextIndex.mp4")
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.camera.SegmentedVideoRecorderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/camera/SegmentedVideoRecorder.kt \
  app/src/test/java/com/luxmap/core/camera/SegmentedVideoRecorderTest.kt
git commit -m "feat(fm-survey): add SegmentedVideoRecorder with rotation and write-failure handling"
```

---

## Task 6: `SurveyTrackRecorder`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt`
- Test: `app/src/test/java/com/luxmap/core/location/SurveyTrackRecorderTest.kt`

**Interfaces:**
- Produces: `data class TrackPoint(val elapsedRealtimeNs: Long, val lat: Double, val lng: Double, val accuracyM: Float, val gpsBearingDeg: Float?, val speedMps: Float?)`; `sealed interface GpsSignalState { data object Ok : GpsSignalState; data object Lost : GpsSignalState }`; `class SurveyTrackRecorder @Inject constructor() { fun onLocationUpdate(location: android.location.Location): TrackPoint; fun onTick(nowElapsedRealtimeNs: Long): GpsSignalState }` — Hilt-injectable (no constructor parameters to bind), so `SurveyCaptureService` (Task 17b) can `@Inject` it directly. The 10s signal-lost threshold is a fixed internal constant, not a constructor parameter, precisely so the class stays trivially injectable.
- Consumes (later, Task 14): `TrackPoint` is serialized by `NdjsonLogWriter`.

`onTick` is how the caller (Task 17b's `LocationHeadingRecorder`) polls, on its own timer, whether the last fix is older than the threshold — this is what pins the "GPS lost for an extended period" Review Focus item without needing a live location provider in the test.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/core/location/SurveyTrackRecorderTest.kt
package com.luxmap.core.location

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class SurveyTrackRecorderTest {
    private fun fakeLocation(
        elapsedRealtimeNs: Long,
        lat: Double = 10.0,
        lng: Double = 106.0,
        accuracy: Float = 5.0f,
        bearing: Float = 90.0f,
        hasBearing: Boolean = true,
        speed: Float = 8.3f,
    ): Location =
        mockk<Location>().apply {
            every { this@apply.elapsedRealtimeNanos } returns elapsedRealtimeNs
            every { this@apply.latitude } returns lat
            every { this@apply.longitude } returns lng
            every { this@apply.accuracy } returns accuracy
            every { this@apply.hasBearing() } returns hasBearing
            every { this@apply.bearing } returns bearing
            every { this@apply.speed } returns speed
        }

    @Test
    fun `maps a location fix to a track point carrying its own elapsed realtime timestamp`() {
        val recorder = SurveyTrackRecorder()

        val point = recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 1_000_000_000L))

        assertEquals(TrackPoint(1_000_000_000L, 10.0, 106.0, 5.0f, 90.0f, 8.3f), point)
    }

    @Test
    fun `reports GPS signal ok right after a fresh fix`() {
        val recorder = SurveyTrackRecorder()
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))

        val state = recorder.onTick(nowElapsedRealtimeNs = 5_000_000_000L) // 5s later

        assertEquals(GpsSignalState.Ok, state)
    }

    @Test
    fun `reports GPS signal lost once the last fix is older than the threshold`() {
        val recorder = SurveyTrackRecorder()
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))

        val state = recorder.onTick(nowElapsedRealtimeNs = 15_000_000_000L) // 15s later

        assertEquals(GpsSignalState.Lost, state)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.location.SurveyTrackRecorderTest"`
Expected: FAIL — class does not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt
package com.luxmap.core.location

import android.location.Location
import javax.inject.Inject

// gpsBearingDeg/speedMps share the fix's own elapsedRealtimeNs (spec §8) — they come from the
// same Location object, unlike heading_log.ndjson which is a fully independent sensor stream.
data class TrackPoint(
    val elapsedRealtimeNs: Long,
    val lat: Double,
    val lng: Double,
    val accuracyM: Float,
    val gpsBearingDeg: Float?,
    val speedMps: Float?,
)

sealed interface GpsSignalState {
    data object Ok : GpsSignalState

    data object Lost : GpsSignalState
}

// Separate from LocationTracker.kt (F12's one-shot "locate me") — this records a continuous track
// for the duration of a survey session (spec §5).
class SurveyTrackRecorder
    @Inject
    constructor() {
        private val signalLostThresholdMs: Long = 10_000L
        private var lastFixElapsedRealtimeNs: Long? = null

        fun onLocationUpdate(location: Location): TrackPoint {
            lastFixElapsedRealtimeNs = location.elapsedRealtimeNanos
            return TrackPoint(
                elapsedRealtimeNs = location.elapsedRealtimeNanos,
                lat = location.latitude,
                lng = location.longitude,
                accuracyM = location.accuracy,
                gpsBearingDeg = if (location.hasBearing()) location.bearing else null,
                speedMps = location.speed,
            )
        }

        fun onTick(nowElapsedRealtimeNs: Long): GpsSignalState {
            val lastFix = lastFixElapsedRealtimeNs ?: return GpsSignalState.Lost
            val ageMs = (nowElapsedRealtimeNs - lastFix) / 1_000_000
            return if (ageMs > signalLostThresholdMs) GpsSignalState.Lost else GpsSignalState.Ok
        }
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.location.SurveyTrackRecorderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt \
  app/src/test/java/com/luxmap/core/location/SurveyTrackRecorderTest.kt
git commit -m "feat(fm-survey): add SurveyTrackRecorder with GPS-signal-lost detection"
```

---

## Task 7: `HeadingSensor`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/location/HeadingSensor.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/location/HeadingSensorTest.kt`

**Interfaces:**
- Produces: `data class HeadingSample(val elapsedRealtimeNs: Long, val headingDeg: Float)`; `class HeadingSensor { fun headingFromRotationVector(rotationVector: FloatArray, eventElapsedRealtimeNs: Long): HeadingSample }`.
- Consumes (later, Task 17b): `HeadingSample` is serialized by `NdjsonLogWriter`.

The rotation-vector-to-degrees math calls `SensorManager.getRotationMatrixFromVector`/`getOrientation`, which are native-backed platform methods that throw or return garbage under the plain JVM unit-test stub (same reasoning as Task 3) — this runs as an **instrumented test** instead of pulling in Robolectric.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/androidTest/java/com/luxmap/core/location/HeadingSensorTest.kt
package com.luxmap.core.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadingSensorTest {
    private val sensor = HeadingSensor()

    @Test
    fun rotationVectorPointingDueNorthYieldsHeadingCloseToZeroDegrees() {
        // Identity-like rotation vector (no rotation applied) — SensorManager.getRotationMatrixFromVector
        // + getOrientation on [0,0,0] yields azimuth 0.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, 0f), eventElapsedRealtimeNs = 42L)

        assertEquals(42L, sample.elapsedRealtimeNs)
        assertEquals(0.0f, sample.headingDeg, 0.5f)
    }

    @Test
    fun headingIsNormalizedToTheZeroToThreeSixtyDegreeRange() {
        // A rotation vector representing a small negative-azimuth rotation around the Z axis
        // (sin(-5 deg / 2), 0, 0 style) should not surface as a negative heading.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, -0.0436f), eventElapsedRealtimeNs = 0L)

        assert(sample.headingDeg in 0.0f..360.0f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.core.location.HeadingSensorTest"` (needs a connected emulator or device)
Expected: FAIL — class does not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/location/HeadingSensor.kt
package com.luxmap.core.location

import android.hardware.SensorManager
import javax.inject.Inject

// Fully independent stream from GPS bearing (spec §8) — own sampling rate, own timestamp source
// (the rotation-vector sensor's own event time), logged to its own heading_log.ndjson file.
data class HeadingSample(
    val elapsedRealtimeNs: Long,
    val headingDeg: Float,
)

class HeadingSensor
    @Inject
    constructor() {
        fun headingFromRotationVector(
            rotationVector: FloatArray,
            eventElapsedRealtimeNs: Long,
        ): HeadingSample {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
            val normalizedDeg = (azimuthDeg + 360f) % 360f
            return HeadingSample(eventElapsedRealtimeNs, normalizedDeg)
        }
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.core.location.HeadingSensorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/location/HeadingSensor.kt \
  app/src/androidTest/java/com/luxmap/core/location/HeadingSensorTest.kt
git commit -m "feat(fm-survey): add HeadingSensor for continuous device orientation"
```

---

## Task 8: `LuxPacketCodec` and `LuxSensorBleClient`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt`
- Create: `app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt`
- Test: `app/src/test/java/com/luxmap/core/ble/LuxPacketCodecTest.kt`

**Interfaces:**
- Produces: `data class LuxSample(val seq: Int, val moduleMs: Long, val phoneElapsedNs: Long, val lux: Float, val bootId: Int)`; `object LuxPacketCodec { fun decode(bytes: ByteArray, receivedAtElapsedRealtimeNs: Long): LuxSample; fun gapSize(previous: LuxSample, current: LuxSample): Int }`; `sealed interface BleConnectionState { data object Disconnected : BleConnectionState; data object Connecting : BleConnectionState; data object Connected : BleConnectionState }`; `class LuxSensorBleClient` exposing `val connectionState: StateFlow<BleConnectionState>` and a first skeleton of `fun observeSamples(deviceAddress: String): Flow<LuxSample>`, which Task 17c rewrites into the real Bước 0 connect/notify flow.
- Consumes (later, Task 14): `LuxSample` is serialized by `NdjsonLogWriter`.
- Consumes (later, Task 17c/18): `connectionState` drives `CaptureUiState`; the real connect flow is built in Task 17c once a device address is known (from a BLE scan step, see Task 17c).

Byte layout (uint16 `seq` little-endian, uint32 `module_ms` little-endian, float32 `lux` little-endian, uint8 `boot_id` appended) is the **proposed** contract from spec §9 — not confirmed with firmware. `gapSize` takes full `LuxSample`s (not bare seq ints) so it can also see `bootId` — a module reboot resets `seq`/`module_ms` toward 0, which must never be read as a huge packet-loss event.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/core/ble/LuxPacketCodecTest.kt
package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LuxPacketCodecTest {
    private fun packet(
        seq: Int,
        moduleMs: Long,
        lux: Float,
        bootId: Int,
    ): ByteArray =
        ByteBuffer
            .allocate(11)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putShort(seq.toShort())
            .putInt(moduleMs.toInt())
            .putFloat(lux)
            .put(bootId.toByte())
            .array()

    private fun sample(
        seq: Int,
        bootId: Int = 1,
    ) = LuxSample(seq = seq, moduleMs = 0L, phoneElapsedNs = 0L, lux = 0f, bootId = bootId)

    @Test
    fun `decodes a packet and stamps it with the phone's receive time`() {
        val bytes = packet(seq = 10, moduleMs = 5_000L, lux = 123.5f, bootId = 1)

        val decoded = LuxPacketCodec.decode(bytes, receivedAtElapsedRealtimeNs = 999L)

        assertEquals(LuxSample(seq = 10, moduleMs = 5_000L, phoneElapsedNs = 999L, lux = 123.5f, bootId = 1), decoded)
    }

    @Test
    fun `gap size between two adjacent packets is zero`() {
        assertEquals(0, LuxPacketCodec.gapSize(sample(seq = 10), sample(seq = 11)))
    }

    @Test
    fun `gap size counts missed packets in the ordinary non-wrapping case`() {
        assertEquals(4, LuxPacketCodec.gapSize(sample(seq = 10), sample(seq = 15)))
    }

    @Test
    fun `gap size handles seq rolling over past 65535 without reporting a huge false gap`() {
        // previousSeq near the uint16 ceiling, currentSeq wrapped back to a small value just after it
        assertEquals(0, LuxPacketCodec.gapSize(sample(seq = 65535), sample(seq = 0)))
        assertEquals(2, LuxPacketCodec.gapSize(sample(seq = 65534), sample(seq = 1)))
    }

    @Test
    fun `gap size is zero across a module reboot even though seq resets toward zero`() {
        // bootId changes -> the module restarted; seq/module_ms going "backwards" is expected and
        // must not be reported as a huge packet-loss event.
        assertEquals(0, LuxPacketCodec.gapSize(sample(seq = 60_000, bootId = 1), sample(seq = 3, bootId = 2)))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.ble.LuxPacketCodecTest"`
Expected: FAIL — `LuxPacketCodec` does not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt
package com.luxmap.core.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Byte layout is a PROPOSAL (spec §9), not yet confirmed with the firmware owner — see
// docs/contract-drift.md. seq: uint16 LE, module_ms: uint32 LE, lux: float32 LE, boot_id: uint8.
private const val SEQ_MODULO = 65536

data class LuxSample(
    val seq: Int,
    val moduleMs: Long,
    val phoneElapsedNs: Long,
    val lux: Float,
    val bootId: Int,
)

object LuxPacketCodec {
    fun decode(
        bytes: ByteArray,
        receivedAtElapsedRealtimeNs: Long,
    ): LuxSample {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val seq = buffer.short.toInt() and 0xFFFF
        val moduleMs = buffer.int.toLong() and 0xFFFFFFFFL
        val lux = buffer.float
        val bootId = buffer.get().toInt() and 0xFF
        return LuxSample(seq, moduleMs, receivedAtElapsedRealtimeNs, lux, bootId)
    }

    // Counts packets missed between two samples, correctly handling both the uint16 seq wraparound
    // and a module reboot (bootId change) — a naive (currentSeq - previousSeq - 1) would report a
    // false ~65000-packet gap at the rollover, and an even bigger false gap across a reboot.
    fun gapSize(
        previous: LuxSample,
        current: LuxSample,
    ): Int {
        if (previous.bootId != current.bootId) return 0
        val forwardDistance = ((current.seq - previous.seq) + SEQ_MODULO) % SEQ_MODULO
        return (forwardDistance - 1).coerceAtLeast(0)
    }
}
```

```kotlin
// app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt
package com.luxmap.core.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState

    data object Connecting : BleConnectionState

    data object Connected : BleConnectionState
}

// UUIDs are PROPOSALS pending confirmation with the firmware owner (spec §9) — see
// docs/contract-drift.md. Rename these two constants once real values are confirmed; nothing
// else in this class should need to change.
object LuxSensorBleContract {
    val SERVICE_UUID: UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
    val LUX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
}

@Singleton
class LuxSensorBleClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val connectionState = _connectionState.asStateFlow()

        private var gatt: BluetoothGatt? = null

        // Connects, subscribes to notify on LUX_CHARACTERISTIC_UUID, and emits a LuxSample per
        // packet, stamped with SystemClock.elapsedRealtimeNanos() at the moment it is received
        // (spec §8 — the module's own clock, module_ms, never substitutes for the phone's).
        fun observeSamples(deviceAddress: String): Flow<LuxSample> =
            callbackFlow {
                val callback =
                    object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(
                            connectedGatt: BluetoothGatt,
                            status: Int,
                            newState: Int,
                        ) {
                            _connectionState.value =
                                if (newState == BluetoothGatt.STATE_CONNECTED) {
                                    BleConnectionState.Connected
                                } else {
                                    BleConnectionState.Disconnected
                                }
                        }

                        override fun onCharacteristicChanged(
                            connectedGatt: BluetoothGatt,
                            characteristic: android.bluetooth.BluetoothGattCharacteristic,
                        ) {
                            val now = android.os.SystemClock.elapsedRealtimeNanos()
                            trySend(LuxPacketCodec.decode(characteristic.value, now))
                        }
                    }

                _connectionState.value = BleConnectionState.Connecting
                val device = android.bluetooth.BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
                gatt = device.connectGatt(context, false, callback)

                awaitClose {
                    gatt?.disconnect()
                    gatt?.close()
                    _connectionState.value = BleConnectionState.Disconnected
                }
            }
    }
```

- [ ] **Step 4: Run to verify the codec test passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.ble.LuxPacketCodecTest"`
Expected: PASS

`LuxSensorBleClient` itself needs a real BLE peripheral and is verified on a real device with the ESP32+BH1750 module per spec §16 — no unit test is written for the `BluetoothGatt` plumbing. This is a first skeleton only: connect and sample-receiving are still one `observeSamples()` call — Task 17c rewrites this file to split them apart, add the CCCD write, auto-reconnect, and the dual `onCharacteristicChanged` overrides needed for devices on both sides of API 33.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt \
  app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt \
  app/src/test/java/com/luxmap/core/ble/LuxPacketCodecTest.kt
git commit -m "feat(fm-survey): add lux BLE packet codec with seq wraparound handling"
```

---

## Task 9: F03 Room schema — `local_survey_plan` and `local_road_segment`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyPlanEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalRoadSegmentEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/dao/SurveyPlanDao.kt`
- Create: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Create: `app/src/main/java/com/luxmap/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveyPlanDaoTest.kt`

**Interfaces:**
- Produces: `LocalSurveyPlanEntity` (table `local_survey_plan`), `LocalRoadSegmentEntity` (table `local_road_segment`), `SurveyPlanDao` with `suspend fun upsertPlans(plans: List<LocalSurveyPlanEntity>)`, `suspend fun upsertRoadSegments(segments: List<LocalRoadSegmentEntity>)`, `fun observePlans(): Flow<List<LocalSurveyPlanEntity>>`, `suspend fun roadSegmentsFor(surveySweepId: String): List<LocalRoadSegmentEntity>`; `AppDatabase` (Room's `@Database`, provided as a Hilt singleton via `DatabaseModule`).

**Correction found during execution (Task 1's original scope moved here):** Room's KSP processor rejects `@Database(entities = [])` at compile time — an empty database cannot exist. `AppDatabase.kt` and `DatabaseModule.kt` are created here instead, in the first task that has real entities to put in the `@Database` annotation. Task 1 only added the Gradle dependencies.

A Room DAO test needs a real SQLite implementation, which a plain JVM unit test does not have.
`CLAUDE.md`'s approved test stack lists "Room Testing" but not Robolectric, so this runs as an
**instrumented test** (`app/src/androidTest`, on an emulator/device) instead of adding a new
library — no further `libs.versions.toml`/`build.gradle.kts` change needed for this task.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
// app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveyPlanDaoTest.kt
package com.luxmap.feature.survey.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SurveyPlanDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SurveyPlanDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.surveyPlanDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertingAPlanTwiceByItsNaturalKeyDoesNotCreateADuplicateRow() =
        runTest {
            val plan =
                LocalSurveyPlanEntity(
                    surveySweepId = "SWEEP-1",
                    assignedByName = "Kỹ sư bảo trì A",
                    plannedDate = "2026-10-01",
                    status = "planned",
                    cachedAt = "2026-09-28T00:00:00Z",
                )

            dao.upsertPlans(listOf(plan))
            dao.upsertPlans(listOf(plan.copy(status = "in_progress")))

            dao.observePlans().test {
                val plans = awaitItem()
                assertEquals(1, plans.size)
                assertEquals("in_progress", plans.first().status)
            }
        }

    @Test
    fun roadSegmentsForASweepAreFilteredBySurveySweepId() =
        runTest {
            dao.upsertRoadSegments(
                listOf(
                    LocalRoadSegmentEntity(roadSegmentId = "RS-1", surveySweepId = "SWEEP-1", name = "Đường A", lengthMeters = 500.0),
                    LocalRoadSegmentEntity(roadSegmentId = "RS-2", surveySweepId = "SWEEP-2", name = "Đường B", lengthMeters = 300.0),
                ),
            )

            val segments = dao.roadSegmentsFor("SWEEP-1")

            assertEquals(1, segments.size)
            assertEquals("RS-1", segments.first().roadSegmentId)
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveyPlanDaoTest"` (needs a connected emulator or device)
Expected: FAIL — entities/DAO do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyPlanEntity.kt
package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Cache of a route assigned to this Field Engineer by "Kỹ sư bảo trì" (F03) — read from
// GET /api/v1/survey-sweeps/planned, no create/edit on mobile (spec §10).
@Entity(tableName = "local_survey_plan")
data class LocalSurveyPlanEntity(
    @PrimaryKey val surveySweepId: String,
    val assignedByName: String,
    val plannedDate: String,
    // planned / in_progress / pending_upload / submitted / processed — per CLAUDE.md C4 survey_sweep.status
    val status: String,
    val cachedAt: String,
)
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/entity/LocalRoadSegmentEntity.kt
package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_road_segment")
data class LocalRoadSegmentEntity(
    @PrimaryKey val roadSegmentId: String,
    val surveySweepId: String,
    val name: String,
    val lengthMeters: Double?,
)
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/dao/SurveyPlanDao.kt
package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlans(plans: List<LocalSurveyPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoadSegments(segments: List<LocalRoadSegmentEntity>)

    @Query("SELECT * FROM local_survey_plan")
    fun observePlans(): Flow<List<LocalSurveyPlanEntity>>

    @Query("SELECT * FROM local_road_segment WHERE surveySweepId = :surveySweepId")
    suspend fun roadSegmentsFor(surveySweepId: String): List<LocalRoadSegmentEntity>
}
```

Create `AppDatabase.kt` (the first entities Room can compile against):

```kotlin
// app/src/main/java/com/luxmap/core/database/AppDatabase.kt
package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luxmap.feature.survey.data.dao.SurveyPlanDao
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity

// Entities are added here task by task as each feature's Room schema lands —
// keeping the list here is the single place that shows the whole local schema.
@Database(
    entities = [LocalSurveyPlanEntity::class, LocalRoadSegmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyPlanDao(): SurveyPlanDao
}
```

Create `DatabaseModule.kt` (Task 1's original content, moved here since it references `AppDatabase`):

```kotlin
// app/src/main/java/com/luxmap/di/DatabaseModule.kt
package com.luxmap.di

import android.content.Context
import androidx.room.Room
import com.luxmap.core.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, "luxmap.db")
            // No shipped release yet (versionCode = 1) and the schema is still changing task by
            // task in this plan (this task adds v1's tables, Task 13 bumps to v2) — destructive
            // migration is acceptable pre-release. Revisit before the app ships to a real device
            // with data worth preserving across an update.
            .fallbackToDestructiveMigration()
            .build()
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveyPlanDaoTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyPlanEntity.kt \
  app/src/main/java/com/luxmap/feature/survey/data/entity/LocalRoadSegmentEntity.kt \
  app/src/main/java/com/luxmap/feature/survey/data/dao/SurveyPlanDao.kt \
  app/src/main/java/com/luxmap/core/database/AppDatabase.kt \
  app/src/main/java/com/luxmap/di/DatabaseModule.kt \
  app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveyPlanDaoTest.kt
git commit -m "feat(fm-survey): add local_survey_plan and local_road_segment room tables"
```

---

## Task 10: `SurveyRepository` and `FakeSurveyRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt`

**Interfaces:**
- Consumes: `SurveyPlanDao` (Task 9).
- Produces: `enum class SurveySweepStatus { PLANNED, IN_PROGRESS, PENDING_UPLOAD, SUBMITTED, PROCESSED }`; `data class AssignedRoadSegment(val roadSegmentId: String, val name: String, val lengthMeters: Double?)`; `data class AssignedSurveyRoute(val surveySweepId: String, val assignedByName: String, val plannedDate: String, val status: SurveySweepStatus, val roadSegments: List<AssignedRoadSegment>)`; `interface SurveyRepository { fun observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>> }`.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt
package com.luxmap.feature.survey.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSurveyRepositoryTest {
    @Test
    fun `emits at least one assigned route with its road segments`() =
        runTest {
            val repository = FakeSurveyRepository()

            repository.observeAssignedRoutes().test {
                val routes = awaitItem()
                assertTrue(routes.isNotEmpty())
                assertTrue(routes.first().roadSegments.isNotEmpty())
            }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeSurveyRepositoryTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt
package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow

enum class SurveySweepStatus { PLANNED, IN_PROGRESS, PENDING_UPLOAD, SUBMITTED, PROCESSED }

data class AssignedRoadSegment(
    val roadSegmentId: String,
    val name: String,
    val lengthMeters: Double?,
)

data class AssignedSurveyRoute(
    val surveySweepId: String,
    val assignedByName: String,
    val plannedDate: String,
    val status: SurveySweepStatus,
    val roadSegments: List<AssignedRoadSegment>,
)

// Field Engineer never creates/edits a route on mobile (spec §10) — this only observes what
// "Kỹ sư bảo trì" assigned, from GET /api/v1/survey-sweeps/planned (already used by F02).
interface SurveyRepository {
    fun observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>>
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt
package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSurveyRepository
    @Inject
    constructor() : SurveyRepository {
        override fun observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>> =
            flow {
                emit(
                    listOf(
                        AssignedSurveyRoute(
                            surveySweepId = "SWEEP-2031",
                            assignedByName = "Kỹ sư bảo trì Trần Văn A",
                            plannedDate = "2026-10-01",
                            status = SurveySweepStatus.PLANNED,
                            roadSegments =
                                listOf(
                                    AssignedRoadSegment(roadSegmentId = "RS-1001", name = "Đường liên thôn 3", lengthMeters = 1200.0),
                                    AssignedRoadSegment(roadSegmentId = "RS-1002", name = "Đường liên thôn 4", lengthMeters = 800.0),
                                ),
                        ),
                    ),
                )
            }
    }
```

Add to `RepositoryModule.kt`:

```kotlin
    @Binds
    abstract fun bindSurveyRepository(impl: FakeSurveyRepository): SurveyRepository
```

(with the matching import added alongside the existing ones).

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeSurveyRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt \
  app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt \
  app/src/main/java/com/luxmap/di/RepositoryModule.kt \
  app/src/test/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt
git commit -m "feat(fm-survey): add SurveyRepository for assigned routes"
```

---

## Task 11: `CheckSurveyReadinessUseCase`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCaseTest.kt`

**Interfaces:**
- Consumes: `ExposureLockController.isTimestampSourceRealtime` (Task 3).
- Produces: `data class SurveyReadinessResult(val cameraPermissionGranted: Boolean, val exposureLockSupported: Boolean, val timestampSourceRealtime: Boolean, val gpsAvailable: Boolean, val freeStorageBytes: Long, val requiredStorageBytes: Long, val batteryPercent: Int) { val isReady: Boolean }`; `class CheckSurveyReadinessUseCase { operator fun invoke(input: SurveyReadinessInput): SurveyReadinessResult }` where `SurveyReadinessInput` bundles the raw probe values so the decision logic itself is a pure function, independently testable from the Android system-service calls that gather those values.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCaseTest.kt
package com.luxmap.feature.survey.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckSurveyReadinessUseCaseTest {
    private val useCase = CheckSurveyReadinessUseCase()

    private fun readyInput() =
        SurveyReadinessInput(
            cameraPermissionGranted = true,
            exposureLockSupported = true,
            timestampSourceRealtime = true,
            gpsAvailable = true,
            freeStorageBytes = 2_000_000_000L,
            requiredStorageBytes = 1_000_000_000L,
            batteryPercent = 80,
        )

    @Test
    fun `is ready when every check passes`() {
        assertTrue(useCase(readyInput()).isReady)
    }

    @Test
    fun `blocks hard when timestamp source is not REALTIME even if everything else passes`() {
        val result = useCase(readyInput().copy(timestampSourceRealtime = false))

        assertFalse(result.isReady)
        assertFalse(result.timestampSourceRealtime)
    }

    @Test
    fun `blocks when free storage is below the route's estimated requirement`() {
        val result = useCase(readyInput().copy(freeStorageBytes = 500_000_000L, requiredStorageBytes = 1_000_000_000L))

        assertFalse(result.isReady)
    }

    @Test
    fun `blocks when battery is below the minimum threshold`() {
        val result = useCase(readyInput().copy(batteryPercent = 10))

        assertFalse(result.isReady)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCaseTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt
package com.luxmap.feature.survey.domain.usecase

import javax.inject.Inject

// Raw values gathered by the caller (CameraManager, StorageManager, BatteryManager, GPS permission
// check) — kept separate from the decision below so the decision itself needs no Android mocking.
data class SurveyReadinessInput(
    val cameraPermissionGranted: Boolean,
    val exposureLockSupported: Boolean,
    val timestampSourceRealtime: Boolean,
    val gpsAvailable: Boolean,
    val freeStorageBytes: Long,
    val requiredStorageBytes: Long,
    val batteryPercent: Int,
)

data class SurveyReadinessResult(
    val cameraPermissionGranted: Boolean,
    val exposureLockSupported: Boolean,
    val timestampSourceRealtime: Boolean,
    val gpsAvailable: Boolean,
    val freeStorageBytes: Long,
    val requiredStorageBytes: Long,
    val batteryPercent: Int,
) {
    // TEMPORARY policy (spec §10): timestampSourceRealtime == false is a hard block, treated the
    // same as an unsupported device, until C8's open point #8 is resolved with the project owner.
    val isReady: Boolean
        get() =
            cameraPermissionGranted &&
                exposureLockSupported &&
                timestampSourceRealtime &&
                gpsAvailable &&
                freeStorageBytes >= requiredStorageBytes &&
                batteryPercent >= MIN_BATTERY_PERCENT

    companion object {
        const val MIN_BATTERY_PERCENT = 20
    }
}

class CheckSurveyReadinessUseCase
    @Inject
    constructor() {
        operator fun invoke(input: SurveyReadinessInput): SurveyReadinessResult =
            SurveyReadinessResult(
                cameraPermissionGranted = input.cameraPermissionGranted,
                exposureLockSupported = input.exposureLockSupported,
                timestampSourceRealtime = input.timestampSourceRealtime,
                gpsAvailable = input.gpsAvailable,
                freeStorageBytes = input.freeStorageBytes,
                requiredStorageBytes = input.requiredStorageBytes,
                batteryPercent = input.batteryPercent,
            )
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCaseTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt \
  app/src/test/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCaseTest.kt
git commit -m "feat(fm-survey): add CheckSurveyReadinessUseCase with hard timestamp-source block"
```

---

## Task 12: F03 UI — `SurveyPlanScreen` with the readiness checklist wired in

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt`
- Create: `app/src/main/java/com/luxmap/di/CaptureModule.kt` (Task 18 later adds `SurveyCaptureController`'s binding to this same file)
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt`

**Interfaces:**
- Consumes: `SurveyRepository.observeAssignedRoutes()` (Task 10); `CheckSurveyReadinessUseCase` (Task 11).
- Produces: `interface SurveyReadinessInputProvider { suspend fun gather(route: AssignedSurveyRoute): SurveyReadinessInput }` (the real Android-system-service gathering that Task 11 deliberately kept out of the pure decision function); `sealed interface SurveyPlanUiState { Loading; data class Success(val routes: List<AssignedSurveyRoute>, val selectedSurveySweepId: String? = null, val readiness: SurveyReadinessResult? = null); Empty; data class Error(val message: String) }` — the 4 mandatory states per `CLAUDE.md`, with the readiness result nested inside `Success` rather than as a 5th state.

**Correction from the original review of this task: the readiness checklist is wired into this screen now, not deferred as a fast-follow** — it is the one place that hard-blocks entry to F04 on `TIMESTAMP_SOURCE`, so a version of F03 without it does not actually enforce spec §10. The map preview of the assigned route (spec's "chỉ xem") still reuses `feature/map`'s existing `RoadSegmentGeoJsonRenderer` as a fast-follow — that part is a visual nice-to-have, not a safety gate, so it stays out of this task's scope.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt
package com.luxmap.feature.survey.ui.plan

import app.cash.turbine.test
import com.luxmap.feature.survey.data.AssignedRoadSegment
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.data.SurveySweepStatus
import com.luxmap.feature.survey.domain.SurveyReadinessInputProvider
import com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCase
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessInput
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyPlanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val route =
        AssignedSurveyRoute(
            surveySweepId = "SWEEP-1",
            assignedByName = "Kỹ sư bảo trì A",
            plannedDate = "2026-10-01",
            status = SurveySweepStatus.PLANNED,
            roadSegments = listOf(AssignedRoadSegment("RS-1", "Đường A", 500.0)),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Empty when there are no assigned routes`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(emptyList())
            val viewModel =
                SurveyPlanViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                assertEquals(SurveyPlanUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with the assigned routes when there is at least one`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val viewModel =
                SurveyPlanViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                val success = awaitItem() as SurveyPlanUiState.Success
                assertEquals(listOf(route), success.routes)
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flow { throw IllegalStateException("offline") }
            val viewModel =
                SurveyPlanViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                val error = awaitItem() as SurveyPlanUiState.Error
                assertEquals("offline", error.message)
            }
        }

    @Test
    fun `selecting a route with a non-REALTIME timestamp source surfaces a not-ready readiness result`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val readinessInputProvider = mockk<SurveyReadinessInputProvider>()
            coEvery { readinessInputProvider.gather(route) } returns
                SurveyReadinessInput(
                    cameraPermissionGranted = true,
                    exposureLockSupported = true,
                    timestampSourceRealtime = false,
                    gpsAvailable = true,
                    freeStorageBytes = 2_000_000_000L,
                    requiredStorageBytes = 1_000_000_000L,
                    batteryPercent = 80,
                )
            val viewModel = SurveyPlanViewModel(repository, readinessInputProvider, CheckSurveyReadinessUseCase())

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success, no selection yet
                viewModel.onRouteSelected("SWEEP-1")
                val selected = awaitItem() as SurveyPlanUiState.Success // selectedSurveySweepId set, readiness null
                assertEquals("SWEEP-1", selected.selectedSurveySweepId)
                val withReadiness = awaitItem() as SurveyPlanUiState.Success
                assertFalse(requireNotNull(withReadiness.readiness).isReady)
                assertFalse(withReadiness.readiness!!.timestampSourceRealtime)
            }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanViewModelTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt
package com.luxmap.feature.survey.domain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.luxmap.core.camera.ExposureLockController
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessInput
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// Task 11 kept CheckSurveyReadinessUseCase a pure decision function on purpose — this is where the
// actual CameraManager/StatFs/BatteryManager calls happen, verified on a real device (spec §16),
// not unit tested here (there is nothing pure left to assert once real system services are involved).
interface SurveyReadinessInputProvider {
    suspend fun gather(route: AssignedSurveyRoute): SurveyReadinessInput
}

class RealSurveyReadinessInputProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val exposureLockController: ExposureLockController,
        private val locationTracker: LocationTracker,
    ) : SurveyReadinessInputProvider {
        override suspend fun gather(route: AssignedSurveyRoute): SurveyReadinessInput {
            val cameraManager = context.getSystemService(CameraManager::class.java)
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            val characteristics = cameraId?.let { cameraManager.getCameraCharacteristics(it) }
            val timestampSourceRealtime =
                characteristics?.let { exposureLockController.isTimestampSourceRealtime(it) } ?: false
            val statFs = StatFs(context.filesDir.path)
            val batteryManager = context.getSystemService(BatteryManager::class.java)

            return SurveyReadinessInput(
                cameraPermissionGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED,
                exposureLockSupported = characteristics != null,
                timestampSourceRealtime = timestampSourceRealtime,
                gpsAvailable = locationTracker.hasLocationPermission(),
                freeStorageBytes = statFs.availableBytes,
                requiredStorageBytes = estimateRequiredStorageBytes(route),
                batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            )
        }

        // Rough estimate (average survey speed x an assumed bitrate) — refine both constants
        // against the Task 2 spike's chosen defaults once real numbers exist.
        private fun estimateRequiredStorageBytes(route: AssignedSurveyRoute): Long {
            val totalLengthMeters = route.roadSegments.sumOf { it.lengthMeters ?: 0.0 }
            val estimatedDurationSeconds = totalLengthMeters / AVERAGE_SPEED_METERS_PER_SECOND
            return (estimatedDurationSeconds * ASSUMED_BITRATE_BYTES_PER_SECOND).toLong()
        }

        private companion object {
            const val AVERAGE_SPEED_METERS_PER_SECOND = 8.3 // ~30 km/h
            const val ASSUMED_BITRATE_BYTES_PER_SECOND = 1_000_000L // 8 Mbps placeholder pending Task 2
        }
    }
```

```kotlin
// app/src/main/java/com/luxmap/di/CaptureModule.kt
package com.luxmap.di

import com.luxmap.feature.survey.domain.RealSurveyReadinessInputProvider
import com.luxmap.feature.survey.domain.SurveyReadinessInputProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Bindings for the survey capture feature that are not data Repositories (per CLAUDE.md,
// RepositoryModule.kt is Fake/Real repository bindings only) — Task 18 adds
// SurveyCaptureController's binding to this same file.
@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    abstract fun bindSurveyReadinessInputProvider(impl: RealSurveyReadinessInputProvider): SurveyReadinessInputProvider
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt
package com.luxmap.feature.survey.ui.plan

import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

sealed interface SurveyPlanUiState {
    data object Loading : SurveyPlanUiState

    data class Success(
        val routes: List<AssignedSurveyRoute>,
        val selectedSurveySweepId: String? = null,
        val readiness: SurveyReadinessResult? = null,
    ) : SurveyPlanUiState

    data object Empty : SurveyPlanUiState

    data class Error(val message: String) : SurveyPlanUiState
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt
package com.luxmap.feature.survey.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.domain.SurveyReadinessInputProvider
import com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SurveyPlanViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
        private val readinessInputProvider: SurveyReadinessInputProvider,
        private val checkSurveyReadinessUseCase: CheckSurveyReadinessUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SurveyPlanUiState>(SurveyPlanUiState.Loading)
        val uiState: StateFlow<SurveyPlanUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .observeAssignedRoutes()
                    .catch { e ->
                        _uiState.value = SurveyPlanUiState.Error(e.message ?: "Không tải được tuyến khảo sát")
                    }.collect { routes ->
                        _uiState.value =
                            if (routes.isEmpty()) {
                                SurveyPlanUiState.Empty
                            } else {
                                SurveyPlanUiState.Success(routes)
                            }
                    }
            }
        }

        fun onRouteSelected(surveySweepId: String) {
            val current = _uiState.value as? SurveyPlanUiState.Success ?: return
            _uiState.value = current.copy(selectedSurveySweepId = surveySweepId, readiness = null)

            viewModelScope.launch {
                val route = current.routes.first { it.surveySweepId == surveySweepId }
                val input = readinessInputProvider.gather(route)
                val result = checkSurveyReadinessUseCase(input)

                // Only apply the result if the user has not since picked a different route.
                val latest = _uiState.value
                if (latest is SurveyPlanUiState.Success && latest.selectedSurveySweepId == surveySweepId) {
                    _uiState.value = latest.copy(readiness = result)
                }
            }
        }
    }
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt
package com.luxmap.feature.survey.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

@Composable
fun SurveyPlanScreen(
    onEnterCaptureMode: (surveySweepId: String) -> Unit,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is SurveyPlanUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is SurveyPlanUiState.Success ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.routes) { route ->
                    SurveyRouteCard(
                        route = route,
                        isSelected = route.surveySweepId == state.selectedSurveySweepId,
                        readiness = if (route.surveySweepId == state.selectedSurveySweepId) state.readiness else null,
                        onClick = { viewModel.onRouteSelected(route.surveySweepId) },
                        onEnterCaptureMode = { onEnterCaptureMode(route.surveySweepId) },
                    )
                }
            }

        is SurveyPlanUiState.Empty ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có tuyến khảo sát nào được giao", style = MaterialTheme.typography.bodyLarge)
            }

        is SurveyPlanUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
    }
}

@Composable
private fun SurveyRouteCard(
    route: AssignedSurveyRoute,
    isSelected: Boolean,
    readiness: SurveyReadinessResult?,
    onClick: () -> Unit,
    onEnterCaptureMode: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        Text(route.surveySweepId, style = MaterialTheme.typography.titleMedium)
        Text("Giao bởi: ${route.assignedByName}", style = MaterialTheme.typography.bodyMedium)
        Text("Ngày dự kiến: ${route.plannedDate}", style = MaterialTheme.typography.bodyMedium)
        Text("${route.roadSegments.size} đoạn đường", style = MaterialTheme.typography.bodySmall)
        if (!isSelected) {
            Button(onClick = onClick) { Text("Kiểm tra sẵn sàng") }
        } else if (readiness == null) {
            Text("Đang kiểm tra thiết bị...", style = MaterialTheme.typography.bodySmall)
        } else {
            ReadinessChecklist(readiness)
            Button(onClick = onEnterCaptureMode, enabled = readiness.isReady) { Text("Vào chế độ khảo sát") }
        }
    }
}

@Composable
private fun ReadinessChecklist(readiness: SurveyReadinessResult) {
    Column {
        ReadinessRow("Quyền camera", readiness.cameraPermissionGranted)
        ReadinessRow("Khoá exposure hỗ trợ", readiness.exposureLockSupported)
        ReadinessRow("Đồng hồ cảm biến REALTIME", readiness.timestampSourceRealtime)
        ReadinessRow("GPS", readiness.gpsAvailable)
        ReadinessRow("Đủ dung lượng trống", readiness.freeStorageBytes >= readiness.requiredStorageBytes)
        ReadinessRow("Pin đủ (>= 20%)", readiness.batteryPercent >= SurveyReadinessResult.MIN_BATTERY_PERCENT)
        if (!readiness.timestampSourceRealtime) {
            // Hard block, not just a warning (spec §10) — the label makes clear this is a device
            // support issue, not a transient check that will pass on retry.
            Text(
                "Thiết bị này không được hỗ trợ khảo sát (đồng hồ cảm biến camera không đạt yêu cầu)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReadinessRow(
    label: String,
    passed: Boolean,
) {
    Text(
        text = "${if (passed) "✓" else "✗"} $label",
        color = if (passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
```

Add a route constant to `Routes.kt` (e.g. `const val SURVEY_PLAN = "survey_plan"`) and a `composable(Routes.SURVEY_PLAN) { SurveyPlanScreen(onEnterCaptureMode = { surveySweepId -> navController.navigate(Routes.surveyCapture(surveySweepId)) }) }` entry to `NavGraph.kt`, following the existing pattern used for the home/map routes already in that file. `Routes.surveyCapture(surveySweepId)` and the receiving route are added in Task 18.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt
package com.luxmap.feature.survey.ui.plan

import app.cash.turbine.test
import com.luxmap.feature.survey.data.AssignedRoadSegment
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.data.SurveySweepStatus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyPlanViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Empty when there are no assigned routes`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(emptyList())
            val viewModel = SurveyPlanViewModel(repository)

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                assertEquals(SurveyPlanUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with the assigned routes when there is at least one`() =
        runTest {
            val route =
                AssignedSurveyRoute(
                    surveySweepId = "SWEEP-1",
                    assignedByName = "Kỹ sư bảo trì A",
                    plannedDate = "2026-10-01",
                    status = SurveySweepStatus.PLANNED,
                    roadSegments = listOf(AssignedRoadSegment("RS-1", "Đường A", 500.0)),
                )
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val viewModel = SurveyPlanViewModel(repository)

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                val success = awaitItem() as SurveyPlanUiState.Success
                assertEquals(listOf(route), success.routes)
            }
        }

    @Test
    fun `emits Error when the repository flow fails`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flow { throw IllegalStateException("offline") }
            val viewModel = SurveyPlanViewModel(repository)

            viewModel.uiState.test {
                assertEquals(SurveyPlanUiState.Loading, awaitItem())
                val error = awaitItem() as SurveyPlanUiState.Error
                assertEquals("offline", error.message)
            }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanViewModelTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt
package com.luxmap.feature.survey.ui.plan

import com.luxmap.feature.survey.data.AssignedSurveyRoute

sealed interface SurveyPlanUiState {
    data object Loading : SurveyPlanUiState

    data class Success(val routes: List<AssignedSurveyRoute>) : SurveyPlanUiState

    data object Empty : SurveyPlanUiState

    data class Error(val message: String) : SurveyPlanUiState
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt
package com.luxmap.feature.survey.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SurveyPlanViewModel
    @Inject
    constructor(
        repository: SurveyRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SurveyPlanUiState>(SurveyPlanUiState.Loading)
        val uiState: StateFlow<SurveyPlanUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .observeAssignedRoutes()
                    .catch { e ->
                        _uiState.value = SurveyPlanUiState.Error(e.message ?: "Không tải được tuyến khảo sát")
                    }.collect { routes ->
                        _uiState.value =
                            if (routes.isEmpty()) {
                                SurveyPlanUiState.Empty
                            } else {
                                SurveyPlanUiState.Success(routes)
                            }
                    }
            }
        }
    }
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt
package com.luxmap.feature.survey.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.feature.survey.data.AssignedSurveyRoute

@Composable
fun SurveyPlanScreen(
    onRouteSelected: (surveySweepId: String) -> Unit,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is SurveyPlanUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is SurveyPlanUiState.Success ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.routes) { route ->
                    SurveyRouteCard(route = route, onClick = { onRouteSelected(route.surveySweepId) })
                }
            }

        is SurveyPlanUiState.Empty ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có tuyến khảo sát nào được giao", style = MaterialTheme.typography.bodyLarge)
            }

        is SurveyPlanUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
    }
}

@Composable
private fun SurveyRouteCard(
    route: AssignedSurveyRoute,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.md)) {
        Text(route.surveySweepId, style = MaterialTheme.typography.titleMedium)
        Text("Giao bởi: ${route.assignedByName}", style = MaterialTheme.typography.bodyMedium)
        Text("Ngày dự kiến: ${route.plannedDate}", style = MaterialTheme.typography.bodyMedium)
        Text("${route.roadSegments.size} đoạn đường", style = MaterialTheme.typography.bodySmall)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt \
  app/src/main/java/com/luxmap/di/CaptureModule.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/plan/ \
  app/src/main/java/com/luxmap/navigation/Routes.kt \
  app/src/main/java/com/luxmap/navigation/NavGraph.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt
git commit -m "feat(fm-survey): add F03 survey plan screen with readiness checklist wired in"
```

---

## Task 13: F04 Room schema — `local_survey_session` and `local_survey_video_segment`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyVideoSegmentEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt`

**Interfaces:**
- Produces: `LocalSurveySessionEntity` (table `local_survey_session`, fields exactly as spec §7) and `LocalSurveyVideoSegmentEntity` (table `local_survey_video_segment`, fields exactly as spec §7); `SurveySessionDao` with `suspend fun insertSession(session: LocalSurveySessionEntity)`, `suspend fun updateSession(session: LocalSurveySessionEntity)`, `suspend fun sessionById(sessionId: String): LocalSurveySessionEntity?`, `suspend fun sessionsInRecordingState(): List<LocalSurveySessionEntity>`, `suspend fun insertSegment(segment: LocalSurveyVideoSegmentEntity)`, `suspend fun updateSegment(segment: LocalSurveyVideoSegmentEntity)`, `suspend fun segmentsFor(sessionId: String): List<LocalSurveyVideoSegmentEntity>`, `suspend fun unfinalizedSegmentFor(sessionId: String): LocalSurveyVideoSegmentEntity?`, `suspend fun deleteSegment(segmentId: String)`.

Same reasoning as Task 9: this is an instrumented test (`app/src/androidTest`), not a JVM unit test, so it needs no new library.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
// app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt
package com.luxmap.feature.survey.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SurveySessionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SurveySessionDao

    private fun session(
        id: String,
        recordingState: String = "recording",
    ) = LocalSurveySessionEntity(
        sessionId = id,
        surveySweepId = "SWEEP-1",
        recordingState = recordingState,
        syncState = null,
        startedAtUtc = Instant.parse("2026-09-28T20:00:00Z"),
        startedAtElapsedNs = 0L,
        endedAtUtc = null,
        durationSeconds = null,
        distanceMeters = null,
        gpsTrackFilePath = null,
        luxLogFilePath = null,
        headingLogFilePath = null,
        frameTimestampLogFilePath = null,
        captureConfigFilePath = null,
        manifestFilePath = null,
        packageSchemaVersion = "v0",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        createdAt = Instant.parse("2026-09-28T20:00:00Z"),
        updatedAt = Instant.parse("2026-09-28T20:00:00Z"),
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.surveySessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sessionsInRecordingStateFindsASessionLeftMidRecording() =
        runTest {
            dao.insertSession(session("SESSION-1", recordingState = "recording"))
            dao.insertSession(session("SESSION-2", recordingState = "packaged"))

            val stuck = dao.sessionsInRecordingState()

            assertEquals(1, stuck.size)
            assertEquals("SESSION-1", stuck.first().sessionId)
        }

    @Test
    fun unfinalizedSegmentIsFoundByANullEndedAtElapsedNs() =
        runTest {
            dao.insertSession(session("SESSION-1"))
            dao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = "/data/segment_0.mp4",
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = null,
                    sizeBytes = null,
                    checksumSha256 = null,
                ),
            )

            val unfinalized = dao.unfinalizedSegmentFor("SESSION-1")

            assertNotNull(unfinalized)
            assertEquals("SEG-0", unfinalized?.segmentId)
        }

    @Test
    fun noUnfinalizedSegmentOnceItHasBeenClosed() =
        runTest {
            dao.insertSession(session("SESSION-1"))
            dao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = "/data/segment_0.mp4",
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = 180_000_000_000L,
                    sizeBytes = 4096L,
                    checksumSha256 = "abc",
                ),
            )

            assertNull(dao.unfinalizedSegmentFor("SESSION-1"))
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest"` (needs a connected emulator or device)
Expected: FAIL — entities/DAO do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt
package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

// recordingState tracks capture lifecycle (recording/stopped/packaged); syncState is null until
// packaged, then follows P6.1 exactly (queued/syncing/conflict/failed/done) — the two are kept
// separate per review feedback so recording progress is never confused with upload progress.
// No road_segment_id: one session covers a whole survey_sweep, which can span many segments.
@Entity(tableName = "local_survey_session")
data class LocalSurveySessionEntity(
    @PrimaryKey val sessionId: String,
    val surveySweepId: String,
    val recordingState: String,
    val syncState: String?,
    val startedAtUtc: Instant,
    val startedAtElapsedNs: Long,
    val endedAtUtc: Instant?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val gpsTrackFilePath: String?,
    val luxLogFilePath: String?,
    val headingLogFilePath: String?,
    val frameTimestampLogFilePath: String?,
    val captureConfigFilePath: String?,
    val manifestFilePath: String?,
    val packageSchemaVersion: String,
    val timestampSourceRealtime: Boolean,
    val bleGapDetected: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyVideoSegmentEntity.kt
package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// endedAtElapsedNs == null means this segment is still open — used by both crash recovery
// (Task 16) and the packaging use case (Task 15) to find a segment that never closed.
@Entity(tableName = "local_survey_video_segment")
data class LocalSurveyVideoSegmentEntity(
    @PrimaryKey val segmentId: String,
    val sessionId: String,
    val segmentIndex: Int,
    val filePath: String,
    val startedAtElapsedNs: Long,
    val endedAtElapsedNs: Long?,
    val sizeBytes: Long?,
    val checksumSha256: String?,
)
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt
package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity

@Dao
interface SurveySessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: LocalSurveySessionEntity)

    @Update
    suspend fun updateSession(session: LocalSurveySessionEntity)

    @Query("SELECT * FROM local_survey_session WHERE sessionId = :sessionId")
    suspend fun sessionById(sessionId: String): LocalSurveySessionEntity?

    @Query("SELECT * FROM local_survey_session WHERE recordingState = 'recording'")
    suspend fun sessionsInRecordingState(): List<LocalSurveySessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSegment(segment: LocalSurveyVideoSegmentEntity)

    @Update
    suspend fun updateSegment(segment: LocalSurveyVideoSegmentEntity)

    @Query("SELECT * FROM local_survey_video_segment WHERE sessionId = :sessionId ORDER BY segmentIndex ASC")
    suspend fun segmentsFor(sessionId: String): List<LocalSurveyVideoSegmentEntity>

    @Query("SELECT * FROM local_survey_video_segment WHERE sessionId = :sessionId AND endedAtElapsedNs IS NULL LIMIT 1")
    suspend fun unfinalizedSegmentFor(sessionId: String): LocalSurveyVideoSegmentEntity?

    @Query("DELETE FROM local_survey_video_segment WHERE segmentId = :segmentId")
    suspend fun deleteSegment(segmentId: String)
}
```

Room needs a type converter for `java.time.Instant`:

```kotlin
// app/src/main/java/com/luxmap/core/database/InstantConverters.kt
package com.luxmap.core.database

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverters {
    @TypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()
}
```

Replace `AppDatabase.kt` in full (keep Task 9's two entities/DAO, add this task's two entities/DAO, bump to version 2, register the converter):

```kotlin
// app/src/main/java/com/luxmap/core/database/AppDatabase.kt
package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.luxmap.feature.survey.data.dao.SurveyPlanDao
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity

@Database(
    entities = [
        LocalSurveyPlanEntity::class,
        LocalRoadSegmentEntity::class,
        LocalSurveySessionEntity::class,
        LocalSurveyVideoSegmentEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyPlanDao(): SurveyPlanDao

    abstract fun surveySessionDao(): SurveySessionDao
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt \
  app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyVideoSegmentEntity.kt \
  app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt \
  app/src/main/java/com/luxmap/core/database/AppDatabase.kt \
  app/src/main/java/com/luxmap/core/database/InstantConverters.kt \
  app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt
git commit -m "feat(fm-survey): add local_survey_session and local_survey_video_segment room tables"
```

---

## Task 14: `NdjsonLogWriter` and `NdjsonLogReader`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogWriter.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogReader.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/NdjsonLogWriterTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/NdjsonLogReaderTest.kt`

**Interfaces:**
- Produces: `class NdjsonLogWriter(private val file: java.io.File, fileRole: String, flushIntervalMs: Long = 1_000L) { fun appendLine(json: String); fun close() }` (writes the `{"schema_version":"v0","file_role":"..."}` header on first open, flushes on a periodic timer); `object NdjsonLogReader { fun readDataLines(file: java.io.File): List<String> }` (skips the header line and tolerates a truncated last line).

This is the shared file I/O used by `SurveyTrackRecorder`, `HeadingSensor`, and `LuxSensorBleClient`'s output during a session, and by `PackageSurveySessionUseCase` when reading them back.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/capture/NdjsonLogWriterTest.kt
package com.luxmap.feature.survey.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NdjsonLogWriterTest {
    @Test
    fun `writes a schema header line first, then each appended line`() {
        val file = File.createTempFile("gps_track", ".ndjson")
        val writer = NdjsonLogWriter(file, fileRole = "gps_track")

        writer.appendLine("""{"elapsed_realtime_ns":1,"lat":10.0,"lng":106.0}""")
        writer.appendLine("""{"elapsed_realtime_ns":2,"lat":10.1,"lng":106.1}""")
        writer.close()

        val lines = file.readLines()
        assertEquals("""{"schema_version":"v0","file_role":"gps_track"}""", lines[0])
        assertEquals(3, lines.size)
    }
}
```

```kotlin
// app/src/test/java/com/luxmap/feature/survey/capture/NdjsonLogReaderTest.kt
package com.luxmap.feature.survey.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class NdjsonLogReaderTest {
    @Test
    fun `skips the header line and returns only data lines`() {
        val file = File.createTempFile("lux_log", ".ndjson")
        file.writeText(
            """
            {"schema_version":"v0","file_role":"lux_log"}
            {"seq":1,"lux":10.0}
            {"seq":2,"lux":11.0}
            """.trimIndent() + "\n",
        )

        val lines = NdjsonLogReader.readDataLines(file)

        assertEquals(listOf("""{"seq":1,"lux":10.0}""", """{"seq":2,"lux":11.0}"""), lines)
    }

    @Test
    fun `drops a truncated last line left by a crash mid-flush`() {
        val file = File.createTempFile("lux_log", ".ndjson")
        file.writeText(
            """
            {"schema_version":"v0","file_role":"lux_log"}
            {"seq":1,"lux":10.0}
            {"seq":2,"lux":1
            """.trimIndent(),
        )

        val lines = NdjsonLogReader.readDataLines(file)

        assertEquals(listOf("""{"seq":1,"lux":10.0}"""), lines)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.NdjsonLogWriterTest" --tests "com.luxmap.feature.survey.capture.NdjsonLogReaderTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogWriter.kt
package com.luxmap.feature.survey.capture

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Timer
import java.util.TimerTask

// schema_version is a header LINE on .ndjson files, unlike manifest.json/capture_config.json
// where it is a JSON field (spec §8). flushIntervalMs periodic flush (spec §12) bounds data loss
// on a crash to at most that interval.
//
// The periodic flush runs on the Timer's own thread while appendLine() is called from a recorder's
// thread/coroutine — BufferedWriter is not thread-safe, so every write AND every flush must take
// the same lock, or a flush landing mid-write can persist a torn line. lock (not `this`) is used
// explicitly so the intent is not hidden behind a bare @Synchronized on a class with a Timer field.
class NdjsonLogWriter(
    file: File,
    fileRole: String,
    flushIntervalMs: Long = 1_000L,
) {
    private val lock = Any()
    private val writer: BufferedWriter = BufferedWriter(FileWriter(file, true))
    private val flushTimer = Timer(/* isDaemon = */ true)

    init {
        synchronized(lock) {
            writer.write("""{"schema_version":"v0","file_role":"$fileRole"}""")
            writer.newLine()
            writer.flush()
        }
        flushTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() = synchronized(lock) { writer.flush() }
            },
            flushIntervalMs,
            flushIntervalMs,
        )
    }

    fun appendLine(json: String) {
        synchronized(lock) {
            writer.write(json)
            writer.newLine()
        }
    }

    fun close() {
        flushTimer.cancel()
        synchronized(lock) {
            writer.flush()
            writer.close()
        }
    }
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogReader.kt
package com.luxmap.feature.survey.capture

import java.io.File

object NdjsonLogReader {
    // Skips the schema header (line 0) and drops the last line if it does not parse as a
    // complete JSON object — the periodic flush in NdjsonLogWriter can leave a partial line on
    // an app crash (spec §12).
    fun readDataLines(file: File): List<String> {
        val allLines = file.readLines()
        if (allLines.isEmpty()) return emptyList()
        val dataLines = allLines.drop(1)
        return if (dataLines.isNotEmpty() && !isCompleteJsonObject(dataLines.last())) {
            dataLines.dropLast(1)
        } else {
            dataLines
        }
    }

    private fun isCompleteJsonObject(line: String): Boolean =
        line.trim().let { it.startsWith("{") && it.endsWith("}") }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.NdjsonLogWriterTest" --tests "com.luxmap.feature.survey.capture.NdjsonLogReaderTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogWriter.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/NdjsonLogReader.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/NdjsonLogWriterTest.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/NdjsonLogReaderTest.kt
git commit -m "feat(fm-survey): add ndjson log writer/reader with periodic flush and tolerant read"
```

---

## Task 15: `PackageSurveySessionUseCase`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt`

**Interfaces:**
- Consumes: `SurveySessionDao` (Task 13), `NdjsonLogReader` (Task 14).
- Produces: `sealed interface PackageResult { data class Success(val manifestFilePath: String) : PackageResult; data class Failure(val reason: String) : PackageResult }`; `class PackageSurveySessionUseCase { suspend fun invoke(sessionId: String): PackageResult }`.

Computes each file's SHA-256 checksum, writes `manifest.json` and updates `local_survey_session.recordingState = "packaged"`. Fails loudly (does not write a manifest) if any file the session references is missing — pinning the Review Focus item about a manifest that lies. Before checksumming, every `.ndjson` file (not `capture_config.json`, not the video segments) is rewritten through `NdjsonLogReader.readDataLines()` — this is the actual use of the "Consumes: NdjsonLogReader" relationship: a session recovered after a crash (Task 16) can carry a truncated last line in a log file, and packaging that torn line as-is would ship it to the server instead of cleaning it once, here, where the tolerant reader already exists.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt
package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class PackageSurveySessionUseCaseTest {
    private fun sessionWithFiles(tempDir: File): LocalSurveySessionEntity {
        val gpsTrack = File(tempDir, "gps_track.ndjson").apply { writeText("{}\n") }
        return LocalSurveySessionEntity(
            sessionId = "SESSION-1",
            surveySweepId = "SWEEP-1",
            recordingState = "stopped",
            syncState = null,
            startedAtUtc = Instant.parse("2026-09-28T20:00:00Z"),
            startedAtElapsedNs = 0L,
            endedAtUtc = Instant.parse("2026-09-28T20:30:00Z"),
            durationSeconds = 1_800L,
            distanceMeters = 5_000.0,
            gpsTrackFilePath = gpsTrack.absolutePath,
            luxLogFilePath = null,
            headingLogFilePath = null,
            frameTimestampLogFilePath = null,
            captureConfigFilePath = null,
            manifestFilePath = null,
            packageSchemaVersion = "v0",
            timestampSourceRealtime = true,
            bleGapDetected = false,
            createdAt = Instant.parse("2026-09-28T20:00:00Z"),
            updatedAt = Instant.parse("2026-09-28T20:30:00Z"),
        )
    }

    @Test
    fun `packages successfully and marks the session as packaged`() =
        runTest {
            val tempDir = createTempDir()
            val session = sessionWithFiles(tempDir)
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            val result = useCase.invoke("SESSION-1")

            assertTrue(result is PackageResult.Success)
            coVerify { dao.updateSession(match { it.recordingState == "packaged" }) }
        }

    @Test
    fun `fails loudly instead of writing a manifest when a referenced file is missing`() =
        runTest {
            val tempDir = createTempDir()
            val session =
                sessionWithFiles(tempDir).copy(luxLogFilePath = File(tempDir, "does_not_exist.ndjson").absolutePath)
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            val result = useCase.invoke("SESSION-1")

            assertTrue(result is PackageResult.Failure)
            coVerify(exactly = 0) { dao.updateSession(match { it.recordingState == "packaged" }) }
        }

    @Test
    fun `strips a truncated last line from an ndjson file before packaging it`() =
        runTest {
            val tempDir = createTempDir()
            val session = sessionWithFiles(tempDir)
            // Simulate a crash-recovered session (Task 16): the last line of gps_track.ndjson is
            // cut off mid-write.
            File(session.gpsTrackFilePath!!).writeText(
                """
                {"schema_version":"v0","file_role":"gps_track"}
                {"elapsed_realtime_ns":1,"lat":10.0}
                {"elapsed_realtime_ns":2,"lat":1
                """.trimIndent(),
            )
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            useCase.invoke("SESSION-1")

            val cleanedLines = File(session.gpsTrackFilePath!!).readLines()
            assertEquals(2, cleanedLines.size) // header + the one complete data line
            assertTrue(cleanedLines[1].endsWith("}"))
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.PackageSurveySessionUseCaseTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt
package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject

sealed interface PackageResult {
    data class Success(val manifestFilePath: String) : PackageResult

    data class Failure(val reason: String) : PackageResult
}

// Runs on "Dừng quay" or after crash recovery (Task 16) closes out the remaining segments.
// Fails loudly rather than writing a manifest that references a file that is not actually there
// (spec §14 Review Focus) — a missing file is a real data-loss event, not something to paper over.
// Deliberate v0 simplification vs spec §8's example: the "device" block is not duplicated in
// manifest.json since capture_config.json (Task 17d) already carries it and both files are always
// packaged together — flagged here, not silently dropped, so a reviewer can override it later.
class PackageSurveySessionUseCase
    @Inject
    constructor(
        private val dao: SurveySessionDao,
    ) {
        suspend fun invoke(sessionId: String): PackageResult {
            val session = dao.sessionById(sessionId) ?: return PackageResult.Failure("Session $sessionId not found")
            val segments = dao.segmentsFor(sessionId)

            // (path, role, segmentIndex) — role/segmentIndex are what let the manifest tell a video
            // segment apart from the four log files (spec §8's files[] entries).
            val referencedFiles =
                listOfNotNull(
                    session.gpsTrackFilePath?.let { Triple(it, "gps_track", null) },
                    session.luxLogFilePath?.let { Triple(it, "lux_log", null) },
                    session.headingLogFilePath?.let { Triple(it, "heading_log", null) },
                    session.frameTimestampLogFilePath?.let { Triple(it, "frame_timestamp_log", null) },
                    session.captureConfigFilePath?.let { Triple(it, "capture_config", null) },
                ) + segments.map { Triple(it.filePath, "video_segment", it.segmentIndex) }

            val missing = referencedFiles.filterNot { (path, _, _) -> File(path).exists() }
            if (missing.isNotEmpty()) {
                return PackageResult.Failure("Missing file(s) at packaging time: ${missing.map { it.first }}")
            }

            referencedFiles.forEach { (path, _, _) -> cleanIfNdjson(File(path)) }

            val manifestFile = File(File(referencedFiles.first().first).parentFile, "manifest.json")
            manifestFile.writeText(
                buildManifestJson(
                    sessionId = session.sessionId,
                    surveySweepId = session.surveySweepId,
                    startedAtUtc = session.startedAtUtc.toString(),
                    endedAtUtc = session.endedAtUtc?.toString(),
                    files = referencedFiles,
                ),
            )

            dao.updateSession(
                session.copy(
                    recordingState = "packaged",
                    manifestFilePath = manifestFile.absolutePath,
                    updatedAt = Instant.now(),
                ),
            )
            return PackageResult.Success(manifestFile.absolutePath)
        }

        // A session recovered after a crash (Task 16) can carry a truncated last line in a log
        // file (spec §12) — rewrite it here, once, using the same tolerant reader, instead of
        // shipping a torn JSON line in the package.
        private fun cleanIfNdjson(file: File) {
            if (!file.name.endsWith(".ndjson")) return
            val header = file.readLines().firstOrNull() ?: return
            val dataLines = NdjsonLogReader.readDataLines(file)
            file.writeText((listOf(header) + dataLines).joinToString("\n", postfix = "\n"))
        }

        private fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream -> stream.copyTo(DigestOutputStream(digest)) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun buildManifestJson(
            sessionId: String,
            surveySweepId: String,
            startedAtUtc: String,
            endedAtUtc: String?,
            files: List<Triple<String, String, Int?>>,
        ): String {
            val filesJson =
                files.joinToString(",") { (path, role, segmentIndex) ->
                    val file = File(path)
                    val checksum = sha256Of(file)
                    val segmentField = if (segmentIndex != null) ""","segment_index":$segmentIndex""" else ""
                    """{"name":"${file.name}","role":"$role"$segmentField,"checksum_sha256":"$checksum","size_bytes":${file.length()}}"""
                }
            val endedAtField = if (endedAtUtc != null) """"ended_at_utc":"$endedAtUtc"""" else """"ended_at_utc":null"""
            return """{"schema_version":"v0","session_id":"$sessionId","survey_sweep_id":"$surveySweepId","started_at_utc":"$startedAtUtc",$endedAtField,"files":[$filesJson]}"""
        }
    }

private class DigestOutputStream(
    private val digest: MessageDigest,
) : java.io.OutputStream() {
    override fun write(b: Int) = digest.update(b.toByte())

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) = digest.update(b, off, len)
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.PackageSurveySessionUseCaseTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt
git commit -m "feat(fm-survey): add PackageSurveySessionUseCase with missing-file guard"
```

---

## Task 16: `SurveySessionRecoveryUseCase`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCase.kt`
- Modify: `app/src/main/java/com/luxmap/LuxMapApp.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt`

**Interfaces:**
- Consumes: `SurveySessionDao` (Task 13), `PackageSurveySessionUseCase` (Task 15).
- Produces: `class SurveySessionRecoveryUseCase { suspend fun recoverAny() }`, called from exactly one place: `LuxMapApp.onCreate()` — not from the capture feature's entry point, so it always runs once per process start regardless of which screen the user opens first.

Two corrections from the original review of this task:
- **Deletes the actual dangling video file, not just its DB row** — a segment left unfinalized by a crash is disk space and potentially corrupt data; leaving the file behind while deleting only the `local_survey_video_segment` row would silently leak storage.
- **A packaging failure during recovery is not silently swallowed.** Since this runs at app startup with no UI attached, "surface an error to the user" means persisting a state a later screen can query and show — `recordingState` gets a new value, `"package_failed"`, distinct from `"stopped"`/`"packaged"`, when `packager.invoke()` returns `PackageResult.Failure`. Showing this in an actual UI (e.g. surfaced on F02's sync banner or a dedicated list) is a real product gap for a fast-follow, not built in this plan — flagged, not hidden.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt
package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.time.Instant

class SurveySessionRecoveryUseCaseTest {
    private fun stuckSession() =
        LocalSurveySessionEntity(
            sessionId = "SESSION-1",
            surveySweepId = "SWEEP-1",
            recordingState = "recording",
            syncState = null,
            startedAtUtc = Instant.parse("2026-09-28T20:00:00Z"),
            startedAtElapsedNs = 0L,
            endedAtUtc = null,
            durationSeconds = null,
            distanceMeters = null,
            gpsTrackFilePath = "/data/gps_track.ndjson",
            luxLogFilePath = null,
            headingLogFilePath = null,
            frameTimestampLogFilePath = null,
            captureConfigFilePath = null,
            manifestFilePath = null,
            packageSchemaVersion = "v0",
            timestampSourceRealtime = true,
            bleGapDetected = false,
            createdAt = Instant.parse("2026-09-28T20:00:00Z"),
            updatedAt = Instant.parse("2026-09-28T20:00:00Z"),
        )

    @Test
    fun `drops the unfinalized segment's row and file, then marks the session stopped before packaging`() =
        runTest {
            val danglingFile = File.createTempFile("segment_1", ".mp4")
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns
                LocalSurveyVideoSegmentEntity("SEG-1", "SESSION-1", 1, danglingFile.absolutePath, 0L, null, null, null)
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Success("/data/manifest.json")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify { dao.deleteSegment("SEG-1") }
            assertFalseFileExists(danglingFile)
            coVerify { dao.updateSession(match { it.sessionId == "SESSION-1" && it.recordingState == "stopped" }) }
            coVerify { packager.invoke("SESSION-1") }
        }

    @Test
    fun `handles a session with zero video segments without crashing`() =
        runTest {
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns null
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Success("/data/manifest.json")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify(exactly = 0) { dao.deleteSegment(any()) }
            coVerify { dao.updateSession(match { it.sessionId == "SESSION-1" && it.recordingState == "stopped" }) }
            coVerify { packager.invoke("SESSION-1") }
        }

    @Test
    fun `marks the session package_failed instead of packaged when packaging fails`() =
        runTest {
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns null
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Failure("missing file")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify { dao.updateSession(match { it.sessionId == "SESSION-1" && it.recordingState == "package_failed" }) }
        }

    private fun assertFalseFileExists(file: File) {
        org.junit.Assert.assertFalse(file.exists())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCaseTest"`
Expected: FAIL — class does not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCase.kt
package com.luxmap.feature.survey.capture

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import java.io.File
import java.time.Instant
import javax.inject.Inject

// Called exactly once, from LuxMapApp.onCreate() (Step 4 below) — not from the capture feature's
// own entry point, so recovery runs on every process start regardless of which screen opens first.
// A session stuck in "recording" means the app was killed mid-session without going through the
// normal "Dừng quay" path — its last segment is unfinalized and MediaMuxer likely never closed it
// cleanly, so both its DB row and its file are dropped rather than trusted.
class SurveySessionRecoveryUseCase
    @Inject
    constructor(
        private val dao: SurveySessionDao,
        private val packager: PackageSurveySessionUseCase,
    ) {
        suspend fun recoverAny() {
            dao.sessionsInRecordingState().forEach { session ->
                // A session can be stuck with no segment at all if the app died before the first
                // one ever opened — nothing to drop in that case, just move the session forward.
                dao.unfinalizedSegmentFor(session.sessionId)?.let { unfinalized ->
                    File(unfinalized.filePath).delete()
                    dao.deleteSegment(unfinalized.segmentId)
                }

                dao.updateSession(session.copy(recordingState = "stopped", updatedAt = Instant.now()))

                when (packager.invoke(session.sessionId)) {
                    is PackageResult.Success -> Unit // PackageSurveySessionUseCase already set recordingState = "packaged"
                    is PackageResult.Failure ->
                        dao.updateSession(session.copy(recordingState = "package_failed", updatedAt = Instant.now()))
                }
            }
        }
    }
```

- [ ] **Step 4: Wire it into `LuxMapApp.onCreate()`**

```kotlin
// app/src/main/java/com/luxmap/LuxMapApp.kt
package com.luxmap

import android.app.Application
import com.luxmap.core.map.initMapLibre
import com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LuxMapApp : Application() {
    // Hilt injects fields declared directly on a @HiltAndroidApp Application before onCreate() runs.
    @Inject lateinit var surveySessionRecoveryUseCase: SurveySessionRecoveryUseCase

    override fun onCreate() {
        super.onCreate()
        initMapLibre(this)
        CoroutineScope(Dispatchers.Default).launch { surveySessionRecoveryUseCase.recoverAny() }
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCaseTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCase.kt \
  app/src/main/java/com/luxmap/LuxMapApp.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt
git commit -m "feat(fm-survey): add crash recovery, wired into app startup"
```

---

## Task 17a: Real video pipeline — Camera2 session + `MediaCodec` + `RealMuxerPort`

**Done when:** starting a session opens the camera, applies the locked profile via `ExposureLockController.applyTo`, locks AWB once converged, encodes through `MediaCodec` into `RealMuxerPort`-backed segments, writes one line per encoded frame to `frame_timestamp_log.ndjson`, and every segment open/close is reflected in `local_survey_video_segment` (a row is inserted with `endedAtElapsedNs = null` the moment a segment starts, and updated with the real end time/size the moment it closes).

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/RealMuxerPort.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt`

**Interfaces:**
- Consumes: `MuxerPort`/`SegmentedVideoRecorder`/`SegmentRotationPolicy` (Task 5), `ExposureLockController`/`LockedCameraProfile` (Task 3), `FrameTimestampLogger` (Task 4), `NdjsonLogWriter` (Task 14), `SurveySessionDao`/`LocalSurveyVideoSegmentEntity` (Task 13).
- Produces: `class RealMuxerPort(outputFilePath: String, outputFormat: MediaFormat) : MuxerPort { fun setPendingSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) }`; `data class FinalizedVideoCapture(val lastSegment: VideoSegmentResult, val actualProfile: LockedCameraProfile, val cameraManufacturer: String, val cameraModel: String, val cameraId: String)`; `class VideoCaptureSession { suspend fun start(scope: CoroutineScope, sessionId: String, sessionDir: File, profile: LockedCameraProfile, segmentDurationMs: Long); suspend fun stop(): FinalizedVideoCapture }`.
- Produces (used by Task 17d): `FinalizedVideoCapture.actualProfile` carries the **real applied** ISO/exposure/frame-duration read back from the last `CaptureResult` — Task 17d writes `capture_config.json` from this, not from the requested `LockedCameraProfile`.

**No automated test** — same reasoning as the rest of this task's Camera2/`MediaCodec` glue (spec §16): a real camera and encoder cannot run under the JVM stub, Robolectric is not on the approved stack, and an instrumented test would only prove the pipeline runs on whatever emulator CI happens to use, not that it holds exposure/AWB correctly. Verified instead by the Task 2 spike having already de-risked the exact same pipeline, and by the real-device checklist in spec §16 (record ≥10 min, confirm no dropped/glitched frames at segment boundaries, confirm `capture_config.json`'s written values match what the device actually applied).

`RealMuxerPort.writeSample(presentationTimeUs)` (the `MuxerPort` method Task 5's bookkeeping already calls) only carries a timestamp because that is all the **pure** rotation-decision unit test needed — the real encoded bytes for that exact call are staged via `setPendingSample()` immediately before invoking `SegmentedVideoRecorder.onEncodedFrame()` below. This keeps Task 5 unchanged while still muxing real data; it is a deliberate adapter, not an accidental mismatch.

- [ ] **Step 1: `RealMuxerPort`**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/RealMuxerPort.kt
package com.luxmap.feature.survey.capture

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import com.luxmap.core.camera.MuxerPort
import java.io.File
import java.nio.ByteBuffer

class RealMuxerPort(
    private val outputFilePath: String,
    outputFormat: MediaFormat,
) : MuxerPort {
    private val muxer = MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val trackIndex = muxer.addTrack(outputFormat)
    private var started = false
    private var pendingBuffer: ByteBuffer? = null
    private var pendingBufferInfo: MediaCodec.BufferInfo? = null

    fun setPendingSample(
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        pendingBuffer = buffer
        pendingBufferInfo = bufferInfo
    }

    override fun writeSample(presentationTimeUs: Long) {
        if (!started) {
            muxer.start()
            started = true
        }
        val buffer = requireNotNull(pendingBuffer) { "setPendingSample() must be called before writeSample()" }
        val info = requireNotNull(pendingBufferInfo) { "setPendingSample() must be called before writeSample()" }
        muxer.writeSampleData(trackIndex, buffer, info)
    }

    override fun close(): Long {
        if (started) muxer.stop()
        muxer.release()
        return File(outputFilePath).length()
    }
}
```

- [ ] **Step 2: `VideoCaptureSession`**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt
package com.luxmap.feature.survey.capture

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
        private var currentMuxerPort: RealMuxerPort? = null
        private var requestBuilder: CaptureRequest.Builder? = null
        private var awbLocked = false
        private var frameIndex = 0
        private var currentSegmentIndex = 0
        private val pendingSensorTimestamps = ArrayDeque<Long>()
        private var lastCaptureResult: TotalCaptureResult? = null
        private var drainJob: Job? = null

        suspend fun start(
            scope: CoroutineScope,
            sessionId: String,
            sessionDir: File,
            profile: LockedCameraProfile,
            segmentDurationMs: Long,
        ) {
            this.sessionId = sessionId
            this.sessionDir = sessionDir

            val cameraManager = context.getSystemService(CameraManager::class.java)
            cameraId = cameraManager.cameraIdList.first()
            cameraDevice = openCamera(cameraManager, cameraId)

            mediaCodec = createEncoder(profile)
            val inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            recorder =
                SegmentedVideoRecorder(SegmentRotationPolicy(segmentDurationMs)) { path ->
                    RealMuxerPort(path, mediaCodec.outputFormat).also { currentMuxerPort = it }
                }
            frameTimestampWriter =
                NdjsonLogWriter(File(sessionDir, "frame_timestamp_log.ndjson"), fileRole = "frame_timestamp_log")

            val firstSegmentPath = File(sessionDir, "segment_0.mp4").absolutePath
            persistNewSegment(0, firstSegmentPath)
            recorder.startSegment(0, firstSegmentPath)

            captureSession = createCaptureSession(cameraDevice, inputSurface)
            val builder =
                cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(inputSurface)
                    exposureLockController.applyTo(this, profile)
                }
            requestBuilder = builder
            captureSession.setRepeatingRequest(builder.build(), captureCallback, null)

            drainJob = scope.launch { drainEncoderOutput() }
        }

        private val captureCallback =
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    lastCaptureResult = result
                    result.get(CaptureResult.SENSOR_TIMESTAMP)?.let { pendingSensorTimestamps.addLast(it) }
                    if (!awbLocked) {
                        val builder = requestBuilder ?: return
                        awbLocked = exposureLockController.lockAwbIfConverged(builder, result)
                        if (awbLocked) captureSession.setRepeatingRequest(builder.build(), this, null)
                    }
                }
            }

        private suspend fun drainEncoderOutput() {
            val bufferInfo = MediaCodec.BufferInfo()
            while (currentCoroutineContext().isActive) {
                val outputIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
                if (outputIndex < 0) continue
                val outputBuffer = requireNotNull(mediaCodec.getOutputBuffer(outputIndex))
                val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                // Camera-fed encoder input preserves frame order, so the Nth SENSOR_TIMESTAMP seen in
                // onCaptureCompleted corresponds to the Nth encoded output frame here (confirmed by
                // the Task 2 spike's PTS-vs-SENSOR_TIMESTAMP measurement).
                val sensorTimestampNs = pendingSensorTimestamps.removeFirstOrNull() ?: (bufferInfo.presentationTimeUs * 1000)

                currentMuxerPort?.setPendingSample(outputBuffer, bufferInfo)
                val closedSegment = recorder.onEncodedFrame(isKeyFrame, bufferInfo.presentationTimeUs, sensorTimestampNs)

                val entry = frameTimestampLogger.buildEntry(frameIndex++, sensorTimestampNs, bufferInfo.presentationTimeUs, currentSegmentIndex)
                frameTimestampWriter.appendLine(
                    """{"frame_index":${entry.frameIndex},"sensor_timestamp_ns":${entry.sensorTimestampNs},"video_pts_us":${entry.videoPtsUs},"segment":${entry.segment}}""",
                )

                mediaCodec.releaseOutputBuffer(outputIndex, false)

                if (closedSegment != null) {
                    persistClosedSegment(closedSegment)
                    currentSegmentIndex = closedSegment.segmentIndex + 1
                    persistNewSegment(currentSegmentIndex, File(sessionDir, "segment_$currentSegmentIndex.mp4").absolutePath)
                }
            }
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

            val result = requireNotNull(lastCaptureResult) { "No CaptureResult observed before stop()" }
            val actualProfile =
                LockedCameraProfile(
                    isoSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0,
                    exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
                    frameDurationNs = result.get(CaptureResult.SENSOR_FRAME_DURATION) ?: 0L,
                )
            return FinalizedVideoCapture(finalSegment, actualProfile, Build.MANUFACTURER, Build.MODEL, cameraId)
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
            sessionDao.updateSegment(open.copy(endedAtElapsedNs = SystemClock.elapsedRealtimeNanos(), sizeBytes = result.sizeBytes))
        }

        private suspend fun openCamera(
            cameraManager: CameraManager,
            cameraId: String,
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
                    null,
                )
            }

        private suspend fun createCaptureSession(
            camera: CameraDevice,
            surface: Surface,
        ): CameraCaptureSession =
            suspendCancellableCoroutine { continuation ->
                @Suppress("DEPRECATION")
                camera.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) = continuation.resume(session)

                        override fun onConfigureFailed(session: CameraCaptureSession) =
                            continuation.cancel(IllegalStateException("Camera session configuration failed"))
                    },
                    null,
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
        }
    }
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/RealMuxerPort.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt
git commit -m "feat(fm-survey): add real Camera2 + MediaCodec video capture session"
```

---

## Task 17b: GPS + heading recording

**Done when:** starting a session registers a continuous `FusedLocationProviderClient` location callback and a `SensorManager` rotation-vector listener, each appending to its own `NdjsonLogWriter` (`gps_track.ndjson`, `heading_log.ndjson`); a 1s timer calls `SurveyTrackRecorder.onTick` and exposes the resulting `GpsSignalState` so the UI (Task 18) can warn when GPS is lost.

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt`

**Interfaces:**
- Consumes: `SurveyTrackRecorder`/`HeadingSensor` (Tasks 6–7), `NdjsonLogWriter` (Task 14).
- Produces: `class LocationHeadingRecorder { fun start(gpsWriter: NdjsonLogWriter, headingWriter: NdjsonLogWriter); fun stop(); val gpsSignalState: StateFlow<GpsSignalState> }`.

No automated test, same reasoning as Task 17a — `FusedLocationProviderClient` and `SensorManager` registration only do anything meaningful against real hardware/Play Services. `SurveyTrackRecorder`'s and `HeadingSensor`'s own logic is already unit-/instrumented-tested in Tasks 6–7; this class is pure plumbing on top.

- [ ] **Step 1: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt
package com.luxmap.feature.survey.capture

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.luxmap.core.location.GpsSignalState
import com.luxmap.core.location.HeadingSensor
import com.luxmap.core.location.SurveyTrackRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

class LocationHeadingRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackRecorder: SurveyTrackRecorder,
        private val headingSensor: HeadingSensor,
    ) {
        private val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        private val sensorManager = context.getSystemService(SensorManager::class.java)
        private var locationCallback: LocationCallback? = null
        private var sensorListener: SensorEventListener? = null
        private var tickTimer: Timer? = null

        private val _gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
        val gpsSignalState: StateFlow<GpsSignalState> = _gpsSignalState.asStateFlow()

        @SuppressLint("MissingPermission")
        fun start(
            gpsWriter: NdjsonLogWriter,
            headingWriter: NdjsonLogWriter,
        ) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS).build()
            val callback =
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val location = result.lastLocation ?: return
                        val point = trackRecorder.onLocationUpdate(location)
                        gpsWriter.appendLine(
                            """{"elapsed_realtime_ns":${point.elapsedRealtimeNs},"lat":${point.lat},"lng":${point.lng},""" +
                                """"accuracy_m":${point.accuracyM},"gps_bearing_deg":${point.gpsBearingDeg ?: "null"},""" +
                                """"speed_mps":${point.speedMps ?: "null"}}""",
                        )
                    }
                }
            locationCallback = callback
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())

            // NOTE for the real-device checklist (spec §16): confirm SensorEvent.timestamp for
            // TYPE_ROTATION_VECTOR is in the same elapsedRealtimeNanos timebase on every supported
            // device — documented as true since API 26, but device-specific drivers have been known
            // to diverge from spec.
            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val listener =
                object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val sample = headingSensor.headingFromRotationVector(event.values, event.timestamp)
                        headingWriter.appendLine("""{"elapsed_realtime_ns":${sample.elapsedRealtimeNs},"heading_deg":${sample.headingDeg}}""")
                    }

                    override fun onAccuracyChanged(
                        sensor: Sensor,
                        accuracy: Int,
                    ) = Unit
                }
            sensorListener = listener
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)

            tickTimer =
                Timer(/* isDaemon = */ true).apply {
                    scheduleAtFixedRate(
                        object : TimerTask() {
                            override fun run() {
                                _gpsSignalState.value = trackRecorder.onTick(SystemClock.elapsedRealtimeNanos())
                            }
                        },
                        GPS_SIGNAL_CHECK_INTERVAL_MS,
                        GPS_SIGNAL_CHECK_INTERVAL_MS,
                    )
                }
        }

        fun stop() {
            locationCallback?.let { fusedClient.removeLocationUpdates(it) }
            sensorListener?.let { sensorManager.unregisterListener(it) }
            tickTimer?.cancel()
        }

        private companion object {
            const val LOCATION_INTERVAL_MS = 1_000L
            const val GPS_SIGNAL_CHECK_INTERVAL_MS = 1_000L
        }
    }
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt
git commit -m "feat(fm-survey): add GPS and heading recording with signal-lost detection"
```

---

## Task 17c: BLE Bước 0 — scan, connect, and the real notify flow

**Done when:** the BLE device address is no longer hardcoded (a scan step picks one); `connect(address)` establishes the GATT link, discovers services, enables notify with a real CCCD write, and is a separate call from the `samples` flow that keeps emitting whatever arrives after that; a dropped connection auto-reconnects; both the pre- and post-API-33 `onCharacteristicChanged` overrides are implemented; every parsed sample's `boot_id` reaches `lux_log.ndjson`.

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ble/LuxDeviceScanner.kt`
- Modify: `app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt` (rewrites the Task 8 skeleton)

**Interfaces:**
- Consumes: `LuxPacketCodec` (Task 8, with the `bootId`-aware `gapSize`).
- Produces: `data class LuxDevice(val name: String?, val address: String)`; `class LuxDeviceScanner { fun scan(timeoutMs: Long = 10_000L): Flow<LuxDevice> }`; `class LuxSensorBleClient { fun connect(deviceAddress: String); fun disconnect(); val connectionState: StateFlow<BleConnectionState>; val samples: SharedFlow<LuxSample> }` — `connect`/`disconnect` no longer take part in producing the samples flow; `samples` just keeps emitting whatever arrives while connected, independent of how many times `connect()`/`disconnect()` is called.

No automated test — `BluetoothLeScanner`/`BluetoothGatt` only do anything against a real BLE peripheral (spec §16: pair with the actual ESP32+BH1750 module, pull its power mid-session to confirm auto-reconnect and the `boot_id` change are both observed).

- [ ] **Step 1: `LuxDeviceScanner`**

```kotlin
// app/src/main/java/com/luxmap/core/ble/LuxDeviceScanner.kt
package com.luxmap.core.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

data class LuxDevice(
    val name: String?,
    val address: String,
)

// Replaces Bước 0's old hardcoded device address (spec's own open point) with a real scan the
// user picks from — filtered to LuxSensorBleContract.SERVICE_UUID so unrelated BLE devices nearby
// do not clutter the picker.
class LuxDeviceScanner
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun scan(timeoutMs: Long = 10_000L): Flow<LuxDevice> =
            callbackFlow {
                val adapter = context.getSystemService(BluetoothAdapter::class.java)
                val scanner = adapter.bluetoothLeScanner
                val callback =
                    object : ScanCallback() {
                        override fun onScanResult(
                            callbackType: Int,
                            result: ScanResult,
                        ) {
                            trySend(LuxDevice(result.device.name, result.device.address))
                        }
                    }
                scanner.startScan(callback)
                awaitClose { scanner.stopScan(callback) }
            }.distinctUntilChanged()
    }
```

- [ ] **Step 2: Rewrite `LuxSensorBleClient`**

```kotlin
// app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt
package com.luxmap.core.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState

    data object Connecting : BleConnectionState

    data object Connected : BleConnectionState
}

// UUIDs are PROPOSALS pending confirmation with the firmware owner (spec §9) — see
// docs/contract-drift.md. Rename these two constants once real values are confirmed; nothing
// else in this class should need to change.
object LuxSensorBleContract {
    val SERVICE_UUID: UUID = UUID.fromString("0000fee0-0000-1000-8000-00805f9b34fb")
    val LUX_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

// connect()/disconnect() are separate from `samples` (review feedback) — Bước 0 calls connect()
// once a device is picked from LuxDeviceScanner; `samples` just keeps emitting for as long as the
// client is connected, and auto-reconnects on an unexpected drop without the caller doing anything.
@Singleton
class LuxSensorBleClient
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
        val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

        private val _samples = MutableSharedFlow<LuxSample>(extraBufferCapacity = 64)
        val samples: SharedFlow<LuxSample> = _samples.asSharedFlow()

        private var gatt: BluetoothGatt? = null
        private var lastDeviceAddress: String? = null
        private var userInitiatedDisconnect = false

        fun connect(deviceAddress: String) {
            userInitiatedDisconnect = false
            lastDeviceAddress = deviceAddress
            _connectionState.value = BleConnectionState.Connecting
            val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(deviceAddress)
            gatt = device.connectGatt(context, false, gattCallback)
        }

        fun disconnect() {
            userInitiatedDisconnect = true
            gatt?.disconnect()
            gatt?.close()
            gatt = null
            _connectionState.value = BleConnectionState.Disconnected
        }

        private val gattCallback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    connectedGatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        connectedGatt.discoverServices()
                    } else {
                        _connectionState.value = BleConnectionState.Disconnected
                        if (!userInitiatedDisconnect) {
                            // Auto-reconnect (spec §11 — a session must not stop on a BLE drop).
                            lastDeviceAddress?.let { connect(it) }
                        }
                    }
                }

                override fun onServicesDiscovered(
                    connectedGatt: BluetoothGatt,
                    status: Int,
                ) {
                    val characteristic =
                        connectedGatt
                            .getService(LuxSensorBleContract.SERVICE_UUID)
                            ?.getCharacteristic(LuxSensorBleContract.LUX_CHARACTERISTIC_UUID)
                            ?: return
                    connectedGatt.setCharacteristicNotification(characteristic, true)
                    // setCharacteristicNotification() only flips a local flag — the peripheral is
                    // not told to start sending until the CCCD descriptor is written (a common BLE
                    // gotcha this review feedback specifically called out).
                    val descriptor = characteristic.getDescriptor(LuxSensorBleContract.CLIENT_CHARACTERISTIC_CONFIG_UUID)
                    descriptor?.let {
                        @Suppress("DEPRECATION")
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        connectedGatt.writeDescriptor(it)
                    }
                    _connectionState.value = BleConnectionState.Connected
                }

                // Pre-API-33 callback — still invoked on those devices; characteristic.value holds the payload.
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    connectedGatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    emitSample(characteristic.value)
                }

                // API 33+ callback — the value is passed directly instead of read from the characteristic.
                override fun onCharacteristicChanged(
                    connectedGatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    emitSample(value)
                }
            }

        private fun emitSample(bytes: ByteArray) {
            val now = SystemClock.elapsedRealtimeNanos()
            _samples.tryEmit(LuxPacketCodec.decode(bytes, now))
        }
    }
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ble/LuxDeviceScanner.kt \
  app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt
git commit -m "feat(fm-survey): split BLE connect from the samples flow, add scan and auto-reconnect"
```

---

## Task 17d: Service lifecycle — Intent START/STOP, ordered stop, real `capture_config.json`

**Done when:** the service is controlled by `ACTION_START`/`ACTION_STOP` intents through `onStartCommand`, calling `startForeground()` as the very first line regardless of which action arrived; stopping runs in the exact order — cancel the GPS/heading/lux jobs, close their `NdjsonLogWriter`s, close the final video segment, write `capture_config.json` from `VideoCaptureSession`'s real applied values, set `recordingState = stopped` with `endedAtUtc`/`durationSeconds`, run `PackageSurveySessionUseCase`, then `stopSelf()`; `onDestroy()` cancels `serviceScope` so nothing leaks if the process is killed anyway.

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt`

**Interfaces:**
- Consumes: `VideoCaptureSession` (Task 17a), `LocationHeadingRecorder` (Task 17b), `LuxSensorBleClient` (Task 17c), `SurveySessionDao` (Task 13), `PackageSurveySessionUseCase` (Task 15).
- Produces: `object CaptureConfigWriter { fun toJson(config: CaptureConfig): String }` (pure function, unit-tested); `val SurveyCaptureService.packagingResult: StateFlow<PackageResult?>`, read by `SurveyCaptureController` (Task 18) after sending `ACTION_STOP`; `val SurveyCaptureService.gpsSignalState: StateFlow<GpsSignalState>` (proxies `LocationHeadingRecorder.gpsSignalState`, Task 17b), read the same bound-service way for the GPS-lost warning in `CaptureUiState.Recording`.

- [ ] **Step 1: Add manifest permissions and service declaration**

```xml
<!-- app/src/main/AndroidManifest.xml, inside <manifest> alongside the existing uses-permission entries -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

Inside `<application>`, alongside the existing `<activity>`:

```xml
<service
    android:name=".feature.survey.capture.SurveyCaptureService"
    android:exported="false"
    android:foregroundServiceType="camera|location|connectedDevice" />
```

- [ ] **Step 2: Write the failing `CaptureConfigWriter` test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt
package com.luxmap.feature.survey.capture

import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureConfigWriterTest {
    @Test
    fun `serializes every capture_config field as a JSON field, not a header line`() {
        val config =
            CaptureConfig(
                utcAnchorIso = "2026-09-28T20:00:00Z",
                elapsedAnchorNs = 123_456_789L,
                resolution = "1920x1080",
                fps = 30,
                isoSensitivity = 800,
                shutterNs = 20_000_000L,
                frameDurationNs = 33_333_333L,
                codec = "video/avc",
                bitrateBps = 8_000_000,
                keyframeIntervalS = 2,
                segmentDurationS = 180,
                cameraManufacturer = "Samsung",
                cameraModel = "Galaxy A54",
                cameraId = "0",
                appVersion = "1.0",
                luxModuleId = "LUX-001",
                luxModuleFirmware = "1.2.0",
            )

        val json = CaptureConfigWriter.toJson(config)

        assertTrue(json.contains(""""schema_version":"v0""""))
        assertTrue(json.contains(""""codec":"video/avc""""))
        assertTrue(json.contains(""""bitrate_bps":8000000"""))
        assertTrue(json.contains(""""keyframe_interval_s":2"""))
        assertTrue(json.contains(""""segment_duration_s":180"""))
        assertTrue(json.contains(""""app_version":"1.0""""))
        assertTrue(json.contains(""""lux_module_firmware":"1.2.0""""))
        assertTrue(json.contains(""""sensor_timestamp_source":"REALTIME""""))
        // Not a header line like the .ndjson files (spec §8) — schema_version sits inside the object.
        assertTrue(json.trim().startsWith("{") && json.contains(""""schema_version""""))
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.CaptureConfigWriterTest"`
Expected: FAIL — `CaptureConfig`/`CaptureConfigWriter` do not exist yet.

- [ ] **Step 4: Implement `CaptureConfigWriter`**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt
package com.luxmap.feature.survey.capture

// Values (except sensor/exposure numbers, which come from the real applied CaptureResult in
// Task 17a) are the Task 2 spike's findings, not invented here. sensorTimestampSource is always
// "REALTIME" because CheckSurveyReadinessUseCase (Task 11) already hard-blocked any device that
// is not.
data class CaptureConfig(
    val utcAnchorIso: String,
    val elapsedAnchorNs: Long,
    val resolution: String,
    val fps: Int,
    val isoSensitivity: Int,
    val shutterNs: Long,
    val frameDurationNs: Long,
    val codec: String,
    val bitrateBps: Int,
    val keyframeIntervalS: Int,
    val segmentDurationS: Int,
    val cameraManufacturer: String,
    val cameraModel: String,
    val cameraId: String,
    val appVersion: String,
    val luxModuleId: String,
    val luxModuleFirmware: String,
)

object CaptureConfigWriter {
    // schema_version is a JSON FIELD here, unlike the .ndjson files' header line (spec §8) —
    // capture_config.json is a single object, not a line-delimited log.
    fun toJson(config: CaptureConfig): String =
        """
        {"schema_version":"v0",
        "utc_elapsed_anchor":{"utc_iso":"${config.utcAnchorIso}","elapsed_realtime_ns":${config.elapsedAnchorNs}},
        "camera":{"resolution":"${config.resolution}","fps":${config.fps},"iso":${config.isoSensitivity},
        "shutter_ns":${config.shutterNs},"frame_duration_ns":${config.frameDurationNs},"codec":"${config.codec}",
        "bitrate_bps":${config.bitrateBps},"keyframe_interval_s":${config.keyframeIntervalS},
        "af_locked":true,"eis_disabled":true,"hdr_disabled":true,"night_mode_disabled":true,
        "sensor_timestamp_source":"REALTIME"},
        "segment_duration_s":${config.segmentDurationS},
        "device":{"manufacturer":"${config.cameraManufacturer}","model":"${config.cameraModel}","camera_id":"${config.cameraId}"},
        "app_version":"${config.appVersion}","lux_module_id":"${config.luxModuleId}","lux_module_firmware":"${config.luxModuleFirmware}"}
        """.trimIndent().replace("\n", "")
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.CaptureConfigWriterTest"`
Expected: PASS

- [ ] **Step 6: Implement the service**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt
package com.luxmap.feature.survey.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.camera.LockedCameraProfile
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import dagger.hilt.android.AndroidEntryPoint
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

private const val NOTIFICATION_CHANNEL_ID = "survey_capture"
private const val NOTIFICATION_ID = 1001

@AndroidEntryPoint
class SurveyCaptureService : Service() {
    @Inject lateinit var videoCaptureSession: VideoCaptureSession

    @Inject lateinit var locationHeadingRecorder: LocationHeadingRecorder

    @Inject lateinit var luxClient: LuxSensorBleClient

    @Inject lateinit var sessionDao: SurveySessionDao

    @Inject lateinit var packager: PackageSurveySessionUseCase

    private val serviceScope = CoroutineScope(SupervisorJob())
    private val jobs = mutableListOf<Job>()
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
                    recordingState = "recording",
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
                    timestampSourceRealtime = true, // already verified by CheckSurveyReadinessUseCase before F04 was entered
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
                            """"phone_elapsed_ns":${sample.phoneElapsedNs},"lux":${sample.lux},"boot_id":${sample.bootId}}""",
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

        serviceScope.launch {
            videoCaptureSession.start(
                scope = serviceScope,
                sessionId = sessionId,
                sessionDir = sessionDir,
                // ISO/exposure/frame duration here are the requested profile — Task 17a reads back
                // the actual applied values at stop() for capture_config.json.
                profile = LockedCameraProfile(isoSensitivity = 800, exposureTimeNs = 20_000_000L, frameDurationNs = 33_333_333L),
                segmentDurationMs = SEGMENT_TARGET_DURATION_MS,
            )
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

            val finalized = videoCaptureSession.stop()

            val captureConfigFile = File(sessionDir, "capture_config.json")
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
                        luxModuleId = "LUX-001", // real value comes from Bước 0's device pick, wired in Task 18
                        luxModuleFirmware = "unknown", // pending firmware contract (Task 17c / docs/contract-drift.md)
                    ),
                ),
            )

            val session = sessionDao.sessionById(currentSessionId)
            val endedAtUtc = Instant.now()
            val durationSeconds = (SystemClock.elapsedRealtimeNanos() - startedAtElapsedNs) / 1_000_000_000L
            if (session != null) {
                sessionDao.updateSession(
                    session.copy(
                        recordingState = "stopped",
                        endedAtUtc = endedAtUtc,
                        durationSeconds = durationSeconds,
                        captureConfigFilePath = captureConfigFile.absolutePath,
                        updatedAt = Instant.now(),
                    ),
                )
            }

            _packagingResult.value = packager.invoke(currentSessionId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
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
```

- [ ] **Step 7: Build to confirm the service compiles and is registered**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
  app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt
git commit -m "feat(fm-survey): drive SurveyCaptureService by intent actions, write real capture_config"
```

---

## Task 18: F04 UI — `CaptureScreen` with GPS/BLE warnings and F06 handoff

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt`
- Modify: `app/src/main/java/com/luxmap/di/CaptureModule.kt` (adds `SurveyCaptureController`'s binding to the file Task 12 created)
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `LuxSensorBleClient.connectionState`/`LuxDeviceScanner` (Task 17c); `SurveyCaptureService`'s `ACTION_START`/`ACTION_STOP`/`packagingResult` (Task 17d), reached through `SurveyCaptureController` so the ViewModel never binds a `Service` or holds a `Context` directly.
- Produces: `interface SurveyCaptureController { fun startSession(sessionId: String, surveySweepId: String, luxDeviceAddress: String); fun stopSession(): Flow<PackageResult>; val gpsSignalState: StateFlow<GpsSignalState> }`; `sealed interface CaptureUiState { data object AwaitingBleConnection; data object Ready; data class Recording(val gpsSignalLost: Boolean = false, val bleGapDetected: Boolean = false); data object Packaging; data class Packaged(val sessionId: String); data class PackagingFailed(val reason: String) }`.

Two corrections from the original review of this task:
- **`CaptureUiState.Recording` now carries GPS/BLE warning flags** instead of being a bare state — spec §16's real-device checklist and P9's F04 states both call for a visible (color+text+vibration) warning while still recording, not a screen that just says "Recording" through a GPS dropout.
- **Navigation to F06 happens via `LaunchedEffect` reacting to `Packaged`**, carrying the `sessionId` the ViewModel itself generated when the user pressed "Bắt đầu quay" — not as a side effect fired directly inside the `when` block during composition. A failed packaging attempt surfaces `PackagingFailed` instead of silently proceeding.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
package com.luxmap.feature.survey.ui.capture

import app.cash.turbine.test
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.location.GpsSignalState
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import io.mockk.every
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

    @Test
    fun `starts awaiting BLE connection and becomes Ready once connected`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                assertEquals(CaptureUiState.AwaitingBleConnection, awaitItem())
                connectionState.value = BleConnectionState.Connected
                assertEquals(CaptureUiState.Ready, awaitItem())
            }
        }

    @Test
    fun `starting a recording generates a session id and tells the controller to start`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                assertEquals(CaptureUiState.AwaitingBleConnection, awaitItem())
                assertEquals(CaptureUiState.Ready, awaitItem())
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)
            }
            verify { controller.startSession(any(), "SWEEP-1", "AA:BB:CC:DD:EE:FF") }
        }

    @Test
    fun `stopping a recording moves through Packaging to Packaged on success`() =
        runTest {
            val connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Connected)
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.stopSession() } returns flowOf(PackageResult.Success("/data/manifest.json"))
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                awaitItem() // AwaitingBleConnection
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
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
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.stopSession() } returns flowOf(PackageResult.Failure("disk full"))
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                awaitItem() // AwaitingBleConnection
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
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
            val luxClient = mockk<LuxSensorBleClient>()
            every { luxClient.connectionState } returns connectionState
            val gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
            val controller = mockk<SurveyCaptureController>(relaxed = true)
            every { controller.gpsSignalState } returns gpsSignalState
            val viewModel = CaptureViewModel(luxClient, controller)

            viewModel.uiState.test {
                awaitItem() // AwaitingBleConnection
                awaitItem() // Ready
                viewModel.onStartRecording(surveySweepId = "SWEEP-1", luxDeviceAddress = "AA:BB:CC:DD:EE:FF")
                val recording = awaitItem() as CaptureUiState.Recording
                assertEquals(false, recording.gpsSignalLost)

                gpsSignalState.value = GpsSignalState.Lost

                val warned = awaitItem() as CaptureUiState.Recording
                assertEquals(true, warned.gpsSignalLost)
            }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt
package com.luxmap.feature.survey.ui.capture

// Bước 0-4 of F04 (spec §8/C7) as explicit states. Recording carries its own warning flags
// (spec §16, P9) instead of the screen just reading "Recording" through a GPS dropout or BLE gap.
sealed interface CaptureUiState {
    data object AwaitingBleConnection : CaptureUiState

    data object Ready : CaptureUiState

    data class Recording(
        val gpsSignalLost: Boolean = false,
        val bleGapDetected: Boolean = false,
    ) : CaptureUiState

    data object Packaging : CaptureUiState

    data class Packaged(val sessionId: String) : CaptureUiState

    data class PackagingFailed(val reason: String) : CaptureUiState
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt
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
                    gpsForwardingJob = controllerScope.launch { service.gpsSignalState.collect { _gpsSignalState.value = it } }
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
            context.startService(Intent(context, SurveyCaptureService::class.java).setAction(SurveyCaptureService.ACTION_STOP))
            val service = boundService ?: return flowOf(PackageResult.Failure("Service not bound"))
            return service.packagingResult
                .filterNotNull()
                .take(1)
                .onCompletion { context.unbindService(connection) }
        }
    }
```

Add `SurveyCaptureController`'s binding to `CaptureModule.kt` (created in Task 12):

```kotlin
// app/src/main/java/com/luxmap/di/CaptureModule.kt — add alongside bindSurveyReadinessInputProvider
    @Binds
    abstract fun bindSurveyCaptureController(impl: RealSurveyCaptureController): SurveyCaptureController
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt
package com.luxmap.feature.survey.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.location.GpsSignalState
import com.luxmap.feature.survey.capture.PackageResult
import com.luxmap.feature.survey.capture.SurveyCaptureController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CaptureViewModel
    @Inject
    constructor(
        private val luxClient: LuxSensorBleClient,
        private val captureController: SurveyCaptureController,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.AwaitingBleConnection)
        val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

        private var sessionId: String = ""

        init {
            viewModelScope.launch {
                luxClient.connectionState.collect { state ->
                    when (val current = _uiState.value) {
                        // Only advances AwaitingBleConnection -> Ready.
                        is CaptureUiState.AwaitingBleConnection ->
                            if (state == BleConnectionState.Connected) _uiState.value = CaptureUiState.Ready

                        // Does not regress out of Recording on a mid-session drop (spec §11) — just
                        // raises the warning flag on the existing Recording state.
                        is CaptureUiState.Recording ->
                            _uiState.value = current.copy(bleGapDetected = state == BleConnectionState.Disconnected)

                        else -> Unit
                    }
                }
            }
            viewModelScope.launch {
                captureController.gpsSignalState.collect { state ->
                    val current = _uiState.value
                    if (current is CaptureUiState.Recording) {
                        _uiState.value = current.copy(gpsSignalLost = state == GpsSignalState.Lost)
                    }
                }
            }
        }

        fun onStartRecording(
            surveySweepId: String,
            luxDeviceAddress: String,
        ) {
            if (_uiState.value != CaptureUiState.Ready) return
            sessionId = UUID.randomUUID().toString()
            captureController.startSession(sessionId, surveySweepId, luxDeviceAddress)
            _uiState.value = CaptureUiState.Recording()
        }

        fun onStopRecording() {
            if (_uiState.value !is CaptureUiState.Recording) return
            _uiState.value = CaptureUiState.Packaging
            viewModelScope.launch {
                when (val result = captureController.stopSession().first()) {
                    is PackageResult.Success -> _uiState.value = CaptureUiState.Packaged(sessionId)
                    is PackageResult.Failure -> _uiState.value = CaptureUiState.PackagingFailed(result.reason)
                }
            }
        }
    }
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt
package com.luxmap.feature.survey.ui.capture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CaptureScreen(
    surveySweepId: String,
    onSessionPackaged: (sessionId: String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Side effect lives here, reacting to state, instead of being called inline inside the `when`
    // branch during composition (review feedback) — LaunchedEffect only fires once per new sessionId.
    val packagedSessionId = (uiState as? CaptureUiState.Packaged)?.sessionId
    if (packagedSessionId != null) {
        LaunchedEffect(packagedSessionId) { onSessionPackaged(packagedSessionId) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (val state = uiState) {
                is CaptureUiState.AwaitingBleConnection ->
                    Text("Đang kết nối cảm biến ánh sáng...", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.Ready ->
                    Button(onClick = {
                        // luxDeviceAddress hardcoded pending a BLE scan/pairing UI (LuxDeviceScanner,
                        // Task 17c, is not wired into this screen yet — tracked in docs/contract-drift.md).
                        viewModel.onStartRecording(surveySweepId, luxDeviceAddress = KNOWN_LUX_DEVICE_ADDRESS)
                    }) { Text("Bắt đầu quay") }

                is CaptureUiState.Recording -> {
                    if (state.gpsSignalLost) {
                        Text(
                            "Mất tín hiệu GPS",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (state.bleGapDetected) {
                        Text(
                            "Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Button(onClick = viewModel::onStopRecording) { Text("Dừng quay") }
                }

                is CaptureUiState.Packaging ->
                    Text("Đang đóng gói phiên khảo sát...", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.Packaged ->
                    Text("Đã đóng gói xong", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.PackagingFailed ->
                    Text(
                        "Đóng gói thất bại: ${state.reason}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
            }
        }
    }
}

private const val KNOWN_LUX_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
```

Add `Routes.SURVEY_CAPTURE` (carrying `surveySweepId`) and `Routes.SURVEY_SUBMIT` (carrying `sessionId`) to `Routes.kt`, and wire both into `NavGraph.kt`; update Task 12's `onEnterCaptureMode` callback to navigate to `Routes.SURVEY_CAPTURE`, and `CaptureScreen`'s `onSessionPackaged` callback to navigate to `Routes.SURVEY_SUBMIT` with the packaged `sessionId`. Add the BLE-address and GPS-warning-wiring gaps above to `docs/contract-drift.md` (Task 1) — both are real product gaps, not just naming placeholders.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt \
  app/src/main/java/com/luxmap/di/CaptureModule.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/capture/ \
  app/src/main/java/com/luxmap/navigation/Routes.kt \
  app/src/main/java/com/luxmap/navigation/NavGraph.kt \
  docs/contract-drift.md \
  app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(fm-survey): add F04 capture screen with GPS/BLE warnings and F06 handoff"
```

---

## Task 19: `UploadRepository` and `FakeUploadRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/FakeUploadRepositoryTest.kt`

**Interfaces:**
- Produces: `sealed interface UploadProgress { data class InProgress(val bytesSent: Long, val totalBytes: Long) : UploadProgress; data object Done : UploadProgress; data class Failed(val reason: String) : UploadProgress }`; `interface UploadRepository { fun uploadSession(sessionId: String): Flow<UploadProgress> }` (no `suspend` — it returns a `Flow`, per spec §13).

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/FakeUploadRepositoryTest.kt
package com.luxmap.feature.survey.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeUploadRepositoryTest {
    @Test
    fun `emits increasing progress then Done`() =
        runTest {
            val repository = FakeUploadRepository()

            repository.uploadSession("SESSION-1").test {
                var lastBytesSent = -1L
                var sawDone = false
                while (!sawDone) {
                    when (val progress = awaitItem()) {
                        is UploadProgress.InProgress -> {
                            assertTrue(progress.bytesSent > lastBytesSent)
                            lastBytesSent = progress.bytesSent
                        }
                        is UploadProgress.Done -> sawDone = true
                        is UploadProgress.Failed -> error("unexpected failure: ${progress.reason}")
                    }
                }
                assertTrue(sawDone)
            }
        }

    @Test
    fun `resuming with the same sessionId does not restart from zero`() =
        runTest {
            val repository = FakeUploadRepository()
            repository.markInterruptedAt(sessionId = "SESSION-1", bytesSent = 5_000L)

            repository.uploadSession("SESSION-1").test {
                val first = awaitItem() as UploadProgress.InProgress
                assertTrue(first.bytesSent >= 5_000L)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeUploadRepositoryTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt
package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow

sealed interface UploadProgress {
    data class InProgress(val bytesSent: Long, val totalBytes: Long) : UploadProgress

    data object Done : UploadProgress

    data class Failed(val reason: String) : UploadProgress
}

// No real endpoint yet (spec §13/§14) — swapped for RealUploadRepository once Backend
// confirms one, without touching UI/ViewModel (RepositoryModule is the only wiring point).
interface UploadRepository {
    fun uploadSession(sessionId: String): Flow<UploadProgress>
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt
package com.luxmap.feature.survey.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeUploadRepository
    @Inject
    constructor() : UploadRepository {
        private val resumePoints = mutableMapOf<String, Long>()
        private val totalBytes = 50_000_000L
        private val chunkBytes = 10_000_000L

        // Simulates an interrupted upload for tests/manual QA of the resume path.
        fun markInterruptedAt(
            sessionId: String,
            bytesSent: Long,
        ) {
            resumePoints[sessionId] = bytesSent
        }

        override fun uploadSession(sessionId: String): Flow<UploadProgress> =
            flow {
                var bytesSent = resumePoints[sessionId] ?: 0L
                while (bytesSent < totalBytes) {
                    delay(10)
                    bytesSent = (bytesSent + chunkBytes).coerceAtMost(totalBytes)
                    resumePoints[sessionId] = bytesSent
                    emit(UploadProgress.InProgress(bytesSent, totalBytes))
                }
                emit(UploadProgress.Done)
            }
    }
```

Add to `RepositoryModule.kt`:

```kotlin
    @Binds
    abstract fun bindUploadRepository(impl: FakeUploadRepository): UploadRepository
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeUploadRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt \
  app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt \
  app/src/main/java/com/luxmap/di/RepositoryModule.kt \
  app/src/test/java/com/luxmap/feature/survey/data/FakeUploadRepositoryTest.kt
git commit -m "feat(fm-survey): add UploadRepository interface with resumable fake"
```

---

## Task 20: F06 UI — `SubmitScreen`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt`

**Interfaces:**
- Consumes: `UploadRepository.uploadSession()` (Task 19).
- Produces: `sealed interface SubmitUiState { data object Idle; data class Uploading(val bytesSent: Long, val totalBytes: Long); data object Done; data class Error(val message: String) }`.

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt
package com.luxmap.feature.survey.ui.submit

import app.cash.turbine.test
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `moves from Idle to Uploading to Done as the repository flow progresses`() =
        runTest {
            val repository = mockk<UploadRepository>()
            every { repository.uploadSession("SESSION-1") } returns
                flowOf(UploadProgress.InProgress(10_000L, 50_000L), UploadProgress.Done)
            val viewModel = SubmitViewModel(repository)

            viewModel.uiState.test {
                assertEquals(SubmitUiState.Idle, awaitItem())
                viewModel.onSubmit("SESSION-1")
                assertEquals(SubmitUiState.Uploading(10_000L, 50_000L), awaitItem())
                assertEquals(SubmitUiState.Done, awaitItem())
            }
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: FAIL — classes do not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt
package com.luxmap.feature.survey.ui.submit

sealed interface SubmitUiState {
    data object Idle : SubmitUiState

    data class Uploading(val bytesSent: Long, val totalBytes: Long) : SubmitUiState

    data object Done : SubmitUiState

    data class Error(val message: String) : SubmitUiState
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt
package com.luxmap.feature.survey.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        fun onSubmit(sessionId: String) {
            viewModelScope.launch {
                repository
                    .uploadSession(sessionId)
                    .catch { e -> _uiState.value = SubmitUiState.Error(e.message ?: "Tải lên thất bại") }
                    .collect { progress ->
                        _uiState.value =
                            when (progress) {
                                is UploadProgress.InProgress -> SubmitUiState.Uploading(progress.bytesSent, progress.totalBytes)
                                is UploadProgress.Done -> SubmitUiState.Done
                                is UploadProgress.Failed -> SubmitUiState.Error(progress.reason)
                            }
                    }
            }
        }
    }
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt
package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SubmitScreen(
    sessionId: String,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (val state = uiState) {
                is SubmitUiState.Idle -> Button(onClick = { viewModel.onSubmit(sessionId) }) { Text("Nộp ngay") }

                is SubmitUiState.Uploading -> {
                    val fraction = state.bytesSent.toFloat() / state.totalBytes.toFloat()
                    LinearProgressIndicator(progress = { fraction })
                    Text("${state.bytesSent} / ${state.totalBytes} bytes")
                }

                is SubmitUiState.Done -> Text("Đã nộp thành công", style = MaterialTheme.typography.bodyLarge)

                is SubmitUiState.Error -> Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
```

Add `Routes.SURVEY_SUBMIT` and wire it into `NavGraph.kt`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/submit/ \
  app/src/main/java/com/luxmap/navigation/Routes.kt \
  app/src/main/java/com/luxmap/navigation/NavGraph.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt
git commit -m "feat(fm-survey): add F06 submit screen"
```

---

## After all tasks: full verification

- [ ] Run the full unit test suite: `./gradlew :app:testDebugUnitTest` — expect all green.
- [ ] Run the instrumented test suite on an emulator/device: `./gradlew :app:connectedDebugAndroidTest` — covers Tasks 3, 7, 9, and 13's Room/Camera2/SensorManager tests that cannot run on the plain JVM stub.
- [ ] Run ktlint: `./gradlew ktlintCheck` — fix any formatting issues before calling this plan done.
- [ ] Manually run the spec §16 real-device checklist (10+ minute recording, segment rotation check, PTS/sensor-timestamp offset measurement, BLE disconnect/reconnect with a real ESP32+BH1750 module, low-light test) — this cannot be automated and is not covered by any task above.
