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
- If the BLE lux sensor disconnects mid-session: **do not stop the session** — keep recording video/GPS/heading, set `ble_gap_detected = true`, warn with color+text+vibration (spec §11).
- No new third-party library beyond what `CLAUDE.md`'s "Ngăn xếp công nghệ" already lists — BLE and video encoding use only framework APIs.
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

app/src/main/java/com/luxmap/core/database/AppDatabase.kt      (create)
app/src/main/java/com/luxmap/core/database/InstantConverters.kt (create — Task 13)
app/src/main/java/com/luxmap/di/DatabaseModule.kt               (create)

app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt   (create)
app/src/main/java/com/luxmap/core/camera/FrameTimestampLogger.kt     (create)
app/src/main/java/com/luxmap/core/camera/SegmentRotationPolicy.kt    (create)
app/src/main/java/com/luxmap/core/camera/SegmentedVideoRecorder.kt   (create)
app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt    (create)
app/src/main/java/com/luxmap/core/location/HeadingSensor.kt          (create)
app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt              (create)
app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt          (create)

app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveyPlanEntity.kt        (create)
app/src/main/java/com/luxmap/feature/survey/data/entity/LocalRoadSegmentEntity.kt       (create)
app/src/main/java/com/luxmap/feature/survey/data/dao/SurveyPlanDao.kt                   (create)
app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt                    (create)
app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt                (create)
app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt (create)
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
app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt                 (create)
app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt                (create)
app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt             (create)
app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt                  (create)
app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt                (create)
app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt                   (create)

app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt      (create)
app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt  (create)
app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt    (create)
app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt  (create)
app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt     (create)

app/src/main/java/com/luxmap/di/RepositoryModule.kt   (modify — bind new repositories)
app/src/main/java/com/luxmap/di/CaptureModule.kt      (create — bind SurveyCaptureController)
app/src/main/java/com/luxmap/navigation/NavGraph.kt   (modify — add F03/F04/F06 routes)
app/src/main/java/com/luxmap/navigation/Routes.kt     (modify — add route constants)

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
- Create: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Create: `app/src/main/java/com/luxmap/di/DatabaseModule.kt`
- Create: `docs/contract-drift.md` (only if it does not already exist — check first, per spec §14)

**Interfaces:**
- Produces: `AppDatabase` (abstract Room `RoomDatabase`, empty entity list for now — later tasks add `@Database(entities = [...])` entries here), provided as a Hilt singleton via `DatabaseModule`.

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

- [ ] **Step 4: Create the empty `AppDatabase`**

```kotlin
// app/src/main/java/com/luxmap/core/database/AppDatabase.kt
package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

// Entities are added here task by task as each feature's Room schema lands —
// keeping the list here is the single place that shows the whole local schema.
@Database(
    entities = [],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase()
```

- [ ] **Step 5: Create `DatabaseModule`**

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
            // task in this plan (Task 9 adds v1's tables, Task 13 bumps to v2) — destructive
            // migration is acceptable pre-release. Revisit before the app ships to a real device
            // with data worth preserving across an update.
            .fallbackToDestructiveMigration()
            .build()
}
```

- [ ] **Step 6: Build to confirm the empty database compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no DAOs/entities yet, so nothing to test at runtime — this task only proves the Room toolchain is wired correctly).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
  app/src/main/java/com/luxmap/core/database/AppDatabase.kt \
  app/src/main/java/com/luxmap/di/DatabaseModule.kt docs/contract-drift.md
git commit -m "chore(fm-survey): add room to the project"
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
- Test: `app/src/test/java/com/luxmap/core/camera/ExposureLockControllerTest.kt`

**Interfaces:**
- Produces: `data class LockedCameraProfile(val isoSensitivity: Int, val exposureTimeNs: Long, val focusDistanceDiopters: Float = 0f)`; `class ExposureLockController { fun lockedRequestKeys(profile: LockedCameraProfile): Map<CaptureRequest.Key<*>, Any>; fun applyTo(builder: CaptureRequest.Builder, profile: LockedCameraProfile); fun isTimestampSourceRealtime(characteristics: CameraCharacteristics): Boolean }`.

The key-value map is kept as a pure function (`lockedRequestKeys`) so it is unit-testable without mocking Android's `CaptureRequest.Builder`; `applyTo` is a thin one-line-per-key wrapper, verified for real by the Task 2 spike and the real-device checklist (spec §16), not by this unit test.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/core/camera/ExposureLockControllerTest.kt
package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureLockControllerTest {
    private val controller = ExposureLockController()

    @Test
    fun `locked request keys disable auto exposure, focus, white balance and stabilization`() {
        val profile = LockedCameraProfile(isoSensitivity = 800, exposureTimeNs = 20_000_000L)

        val keys = controller.lockedRequestKeys(profile)

        assertEquals(CaptureRequest.CONTROL_AE_MODE_OFF, keys[CaptureRequest.CONTROL_AE_MODE])
        assertEquals(CaptureRequest.CONTROL_AF_MODE_OFF, keys[CaptureRequest.CONTROL_AF_MODE])
        assertEquals(CaptureRequest.CONTROL_AWB_MODE_OFF, keys[CaptureRequest.CONTROL_AWB_MODE])
        assertEquals(800, keys[CaptureRequest.SENSOR_SENSITIVITY])
        assertEquals(20_000_000L, keys[CaptureRequest.SENSOR_EXPOSURE_TIME])
        assertEquals(
            CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
            keys[CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE],
        )
        assertEquals(CaptureRequest.NOISE_REDUCTION_MODE_OFF, keys[CaptureRequest.NOISE_REDUCTION_MODE])
        assertEquals(CaptureRequest.EDGE_MODE_OFF, keys[CaptureRequest.EDGE_MODE])
    }

    @Test
    fun `timestamp source realtime returns true only when characteristic equals REALTIME`() {
        val realtimeCharacteristics = mockk<CameraCharacteristics>()
        every { realtimeCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) } returns
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME
        val unknownCharacteristics = mockk<CameraCharacteristics>()
        every { unknownCharacteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) } returns
            CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN

        assertTrue(controller.isTimestampSourceRealtime(realtimeCharacteristics))
        assertFalse(controller.isTimestampSourceRealtime(unknownCharacteristics))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.camera.ExposureLockControllerTest"`
Expected: FAIL — `ExposureLockController` does not exist yet.

- [ ] **Step 3: Implement**

```kotlin
// app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt
package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import javax.inject.Inject

// Held across a whole recording session (spec: "áp dụng và giữ suốt phiên quay"), not just one shot.
data class LockedCameraProfile(
    val isoSensitivity: Int,
    val exposureTimeNs: Long,
    val focusDistanceDiopters: Float = 0f,
)

class ExposureLockController
    @Inject
    constructor() {
        // Pure map so this is testable without a real CaptureRequest.Builder — applyTo() below
        // does the actual mutation and is only meaningfully verified on a real device (spike, spec §16).
        fun lockedRequestKeys(profile: LockedCameraProfile): Map<CaptureRequest.Key<*>, Any> =
            mapOf(
                CaptureRequest.CONTROL_AE_MODE to CaptureRequest.CONTROL_AE_MODE_OFF,
                CaptureRequest.CONTROL_AF_MODE to CaptureRequest.CONTROL_AF_MODE_OFF,
                CaptureRequest.CONTROL_AWB_MODE to CaptureRequest.CONTROL_AWB_MODE_OFF,
                CaptureRequest.SENSOR_SENSITIVITY to profile.isoSensitivity,
                CaptureRequest.SENSOR_EXPOSURE_TIME to profile.exposureTimeNs,
                CaptureRequest.LENS_FOCUS_DISTANCE to profile.focusDistanceDiopters,
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE to CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF,
                CaptureRequest.NOISE_REDUCTION_MODE to CaptureRequest.NOISE_REDUCTION_MODE_OFF,
                CaptureRequest.EDGE_MODE to CaptureRequest.EDGE_MODE_OFF,
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
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.camera.ExposureLockControllerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt \
  app/src/test/java/com/luxmap/core/camera/ExposureLockControllerTest.kt
git commit -m "feat(fm-survey): add ExposureLockController for session-long exposure lock"
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
- Produces: `data class VideoSegmentResult(val segmentIndex: Int, val filePath: String, val sizeBytes: Long)`; `interface SegmentedVideoRecorder { fun startSegment(segmentIndex: Int, outputFilePath: String); fun onEncodedFrame(isKeyFrame: Boolean, presentationTimeUs: Long, sensorTimestampNs: Long): VideoSegmentResult?; fun stop(): VideoSegmentResult }`. `onEncodedFrame` returns a non-null `VideoSegmentResult` exactly when a rotation just closed a segment, so the caller (Task 17's service) knows to open the next `local_survey_video_segment` row.

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
- Produces: `data class TrackPoint(val elapsedRealtimeNs: Long, val lat: Double, val lng: Double, val accuracyM: Float, val gpsBearingDeg: Float?, val speedMps: Float?)`; `sealed interface GpsSignalState { data object Ok : GpsSignalState; data object Lost : GpsSignalState }`; `class SurveyTrackRecorder(private val signalLostThresholdMs: Long = 10_000L) { fun onLocationUpdate(location: android.location.Location): TrackPoint; fun onTick(nowElapsedRealtimeNs: Long): GpsSignalState }`.
- Consumes (later, Task 14): `TrackPoint` is serialized by `NdjsonLogWriter`.

`onTick` is how the caller (Task 17's service) polls, on its own timer, whether the last fix is older than the threshold — this is what pins the "GPS lost for an extended period" Review Focus item without needing a live location provider in the test.

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
        val recorder = SurveyTrackRecorder(signalLostThresholdMs = 10_000L)
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))

        val state = recorder.onTick(nowElapsedRealtimeNs = 5_000_000_000L) // 5s later

        assertEquals(GpsSignalState.Ok, state)
    }

    @Test
    fun `reports GPS signal lost once the last fix is older than the threshold`() {
        val recorder = SurveyTrackRecorder(signalLostThresholdMs = 10_000L)
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
class SurveyTrackRecorder(
    private val signalLostThresholdMs: Long = 10_000L,
) {
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
- Test: `app/src/test/java/com/luxmap/core/location/HeadingSensorTest.kt`

**Interfaces:**
- Produces: `data class HeadingSample(val elapsedRealtimeNs: Long, val headingDeg: Float)`; `class HeadingSensor { fun headingFromRotationVector(rotationVector: FloatArray, eventElapsedRealtimeNs: Long): HeadingSample }`.
- Consumes (later, Task 14): `HeadingSample` is serialized by `NdjsonLogWriter`.

The rotation-vector-to-degrees math is pure and is what's unit-tested; the actual `SensorManager` registration/callback plumbing is thin and verified on a real device.

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/core/location/HeadingSensorTest.kt
package com.luxmap.core.location

import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingSensorTest {
    private val sensor = HeadingSensor()

    @Test
    fun `a rotation vector pointing due north yields a heading close to 0 degrees`() {
        // Identity-like rotation vector (no rotation applied) — SensorManager.getRotationMatrixFromVector
        // + getOrientation on [0,0,0] yields azimuth 0.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, 0f), eventElapsedRealtimeNs = 42L)

        assertEquals(42L, sample.elapsedRealtimeNs)
        assertEquals(0.0f, sample.headingDeg, 0.5f)
    }

    @Test
    fun `heading is normalized to the 0 to 360 degree range`() {
        // A rotation vector representing a small negative-azimuth rotation around the Z axis
        // (sin(-5 deg / 2), 0, 0 style) should not surface as a negative heading.
        val sample = sensor.headingFromRotationVector(floatArrayOf(0f, 0f, -0.0436f), eventElapsedRealtimeNs = 0L)

        assert(sample.headingDeg in 0.0f..360.0f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.location.HeadingSensorTest"`
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

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.location.HeadingSensorTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/location/HeadingSensor.kt \
  app/src/test/java/com/luxmap/core/location/HeadingSensorTest.kt
git commit -m "feat(fm-survey): add HeadingSensor for continuous device orientation"
```

---

## Task 8: `LuxPacketCodec` and `LuxSensorBleClient`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt`
- Create: `app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt`
- Test: `app/src/test/java/com/luxmap/core/ble/LuxPacketCodecTest.kt`

**Interfaces:**
- Produces: `data class LuxSample(val seq: Int, val moduleMs: Long, val phoneElapsedNs: Long, val lux: Float, val bootId: Int)`; `object LuxPacketCodec { fun decode(bytes: ByteArray, receivedAtElapsedRealtimeNs: Long): LuxSample; fun gapSize(previousSeq: Int, currentSeq: Int): Int }`; `sealed interface BleConnectionState { data object Disconnected : BleConnectionState; data object Connecting : BleConnectionState; data object Connected : BleConnectionState }`; `class LuxSensorBleClient` exposing `val connectionState: StateFlow<BleConnectionState>` and `fun observeSamples(deviceAddress: String): Flow<LuxSample>`.
- Consumes (later, Task 14): `LuxSample` is serialized by `NdjsonLogWriter`.
- Consumes (later, Task 17/18): `connectionState` drives `CaptureUiState`; `observeSamples(deviceAddress)` is called once a device address is known (from a BLE scan step out of this plan's unit-testable scope, resolved on a real device per spec §16).

Byte layout (uint16 `seq` little-endian, uint32 `module_ms` little-endian, float32 `lux` little-endian, uint8 `boot_id` appended) is the **proposed** contract from spec §9 — not confirmed with firmware. `gapSize` is what pins the seq-wraparound Review Focus item.

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

    @Test
    fun `decodes a packet and stamps it with the phone's receive time`() {
        val bytes = packet(seq = 10, moduleMs = 5_000L, lux = 123.5f, bootId = 1)

        val sample = LuxPacketCodec.decode(bytes, receivedAtElapsedRealtimeNs = 999L)

        assertEquals(LuxSample(seq = 10, moduleMs = 5_000L, phoneElapsedNs = 999L, lux = 123.5f, bootId = 1), sample)
    }

    @Test
    fun `gap size between two adjacent packets is zero`() {
        assertEquals(0, LuxPacketCodec.gapSize(previousSeq = 10, currentSeq = 11))
    }

    @Test
    fun `gap size counts missed packets in the ordinary non-wrapping case`() {
        assertEquals(4, LuxPacketCodec.gapSize(previousSeq = 10, currentSeq = 15))
    }

    @Test
    fun `gap size handles seq rolling over past 65535 without reporting a huge false gap`() {
        // previousSeq near the uint16 ceiling, currentSeq wrapped back to a small value just after it
        assertEquals(0, LuxPacketCodec.gapSize(previousSeq = 65535, currentSeq = 0))
        assertEquals(2, LuxPacketCodec.gapSize(previousSeq = 65534, currentSeq = 1))
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

    // Counts packets missed between two seq values, correctly handling the uint16 wraparound —
    // a naive (currentSeq - previousSeq - 1) would report a false ~65000-packet gap at the rollover.
    fun gapSize(
        previousSeq: Int,
        currentSeq: Int,
    ): Int {
        val forwardDistance = ((currentSeq - previousSeq) + SEQ_MODULO) % SEQ_MODULO
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

`LuxSensorBleClient` itself needs a real BLE peripheral and is verified on a real device with the ESP32+BH1750 module per spec §16 — no unit test is written for the `BluetoothGatt` plumbing.

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
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveyPlanDaoTest.kt`

**Interfaces:**
- Produces: `LocalSurveyPlanEntity` (table `local_survey_plan`), `LocalRoadSegmentEntity` (table `local_road_segment`), `SurveyPlanDao` with `suspend fun upsertPlans(plans: List<LocalSurveyPlanEntity>)`, `suspend fun upsertRoadSegments(segments: List<LocalRoadSegmentEntity>)`, `fun observePlans(): Flow<List<LocalSurveyPlanEntity>>`, `suspend fun roadSegmentsFor(surveySweepId: String): List<LocalRoadSegmentEntity>`.

A Room DAO test needs a real SQLite implementation, which a plain JVM unit test does not have.
`CLAUDE.md`'s approved test stack lists "Room Testing" but not Robolectric, so this runs as an
**instrumented test** (`app/src/androidTest`, on an emulator/device) instead of adding a new
library — no `libs.versions.toml`/`build.gradle.kts` change needed for this task.

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
    fun `upserting a plan twice by its natural key does not create a duplicate row`() =
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
    fun `road segments for a sweep are filtered by surveySweepId`() =
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

Update `AppDatabase.kt`:

```kotlin
// app/src/main/java/com/luxmap/core/database/AppDatabase.kt
package com.luxmap.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luxmap.feature.survey.data.dao.SurveyPlanDao
import com.luxmap.feature.survey.data.entity.LocalRoadSegmentEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyPlanEntity

@Database(
    entities = [LocalSurveyPlanEntity::class, LocalRoadSegmentEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun surveyPlanDao(): SurveyPlanDao
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

## Task 12: F03 UI — `SurveyPlanScreen`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt`

**Interfaces:**
- Consumes: `SurveyRepository.observeAssignedRoutes()` (Task 10).
- Produces: `sealed interface SurveyPlanUiState { Loading; data class Success(val routes: List<AssignedSurveyRoute>); Empty; data class Error(val message: String) }` — the 4 mandatory states per `CLAUDE.md`.

This task covers the route list and its 4 UI states; the readiness checklist result (Task 11) is surfaced once a route is selected, wired the same way `HomeViewModel` wires `HomeRepository` — the checklist UI itself follows the existing `WorkOrderCard`/`OfflineBanner` component patterns and is not re-derived here in full to keep this task bounded to the list + state machine. The map preview of the assigned route (spec's "chỉ xem") reuses `feature/map`'s existing `RoadSegmentGeoJsonRenderer` and is a fast-follow, not built in this task.

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

Add a route constant to `Routes.kt` (e.g. `const val SURVEY_PLAN = "survey_plan"`) and a `composable(Routes.SURVEY_PLAN) { SurveyPlanScreen(onRouteSelected = { /* Task 18 wires this to Routes.SURVEY_CAPTURE */ }) }` entry to `NavGraph.kt`, following the existing pattern used for the home/map routes already in that file.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanViewModelTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/plan/ \
  app/src/main/java/com/luxmap/navigation/Routes.kt \
  app/src/main/java/com/luxmap/navigation/NavGraph.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt
git commit -m "feat(fm-survey): add F03 survey plan screen with 4 ui states"
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
    fun `sessions in recording state finds a session left mid-recording`() =
        runTest {
            dao.insertSession(session("SESSION-1", recordingState = "recording"))
            dao.insertSession(session("SESSION-2", recordingState = "packaged"))

            val stuck = dao.sessionsInRecordingState()

            assertEquals(1, stuck.size)
            assertEquals("SESSION-1", stuck.first().sessionId)
        }

    @Test
    fun `unfinalized segment is found by a null endedAtElapsedNs`() =
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
    fun `no unfinalized segment once it has been closed`() =
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

Update `AppDatabase.kt` to add both entities to the `entities` array, bump `version = 2`, and add `abstract fun surveySessionDao(): SurveySessionDao`. Room needs a type converter for `java.time.Instant`; add:

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

and add `@TypeConverters(InstantConverters::class)` on the `AppDatabase` class.

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
class NdjsonLogWriter(
    file: File,
    fileRole: String,
    flushIntervalMs: Long = 1_000L,
) {
    private val writer: BufferedWriter = BufferedWriter(FileWriter(file, true))
    private val flushTimer = Timer(/* isDaemon = */ true)

    init {
        writer.write("""{"schema_version":"v0","file_role":"$fileRole"}""")
        writer.newLine()
        writer.flush()
        flushTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() = writer.flush()
            },
            flushIntervalMs,
            flushIntervalMs,
        )
    }

    @Synchronized
    fun appendLine(json: String) {
        writer.write(json)
        writer.newLine()
    }

    fun close() {
        flushTimer.cancel()
        writer.flush()
        writer.close()
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

Computes each file's SHA-256 checksum, writes `manifest.json` and updates `local_survey_session.recordingState = "packaged"`. Fails loudly (does not write a manifest) if any file the session references is missing — pinning the Review Focus item about a manifest that lies.

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
// manifest.json since capture_config.json (Task 17) already carries it and both files are always
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
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt`

**Interfaces:**
- Consumes: `SurveySessionDao` (Task 13), `PackageSurveySessionUseCase` (Task 15).
- Produces: `class SurveySessionRecoveryUseCase { suspend fun recoverAny() }` — call once at app start (e.g. from `LuxMapApp.onCreate` or the capture feature's entry point).

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
    fun `drops the unfinalized segment and marks the session stopped before packaging`() =
        runTest {
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionsInRecordingState() } returns listOf(stuckSession())
            coEvery { dao.unfinalizedSegmentFor("SESSION-1") } returns
                LocalSurveyVideoSegmentEntity("SEG-1", "SESSION-1", 1, "/data/segment_1.mp4", 0L, null, null, null)
            val packager = mockk<PackageSurveySessionUseCase>()
            coEvery { packager.invoke("SESSION-1") } returns PackageResult.Success("/data/manifest.json")

            SurveySessionRecoveryUseCase(dao, packager).recoverAny()

            coVerify { dao.deleteSegment("SEG-1") }
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
import java.time.Instant
import javax.inject.Inject

// Called once at app start (spec §12). A session stuck in "recording" means the app was killed
// mid-session without going through the normal "Dừng quay" path — its last segment is unfinalized
// and MediaMuxer likely never closed it cleanly, so it is dropped rather than trusted.
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
                    dao.deleteSegment(unfinalized.segmentId)
                }

                dao.updateSession(session.copy(recordingState = "stopped", updatedAt = Instant.now()))
                packager.invoke(session.sessionId)
            }
        }
    }
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCaseTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCase.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt
git commit -m "feat(fm-survey): add crash recovery for sessions stuck in recording state"
```

---

## Task 17: `CaptureConfigWriter`, `SurveyCaptureService`, and manifest/permissions wiring

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt`

**Interfaces:**
- Consumes: `SegmentedVideoRecorder` (Task 5), `SurveyTrackRecorder`/`HeadingSensor` (Tasks 6–7), `LuxSensorBleClient` (Task 8), `SurveySessionDao`/`NdjsonLogWriter`/`NdjsonLogReader` (Tasks 13–14), `PackageSurveySessionUseCase` (Task 15).
- Produces: `data class CaptureConfig(val utcAnchorIso: String, val elapsedAnchorNs: Long, val resolution: String, val fps: Int, val isoSensitivity: Int, val shutterNs: Long, val codec: String, val bitrateBps: Int, val keyframeIntervalS: Int, val segmentDurationS: Int, val cameraManufacturer: String, val cameraModel: String, val cameraId: String, val appVersion: String, val luxModuleId: String, val luxModuleFirmware: String)`; `object CaptureConfigWriter { fun toJson(config: CaptureConfig): String }`. A bound/started `Service` with `fun startSession(surveySweepId: String, luxDeviceAddress: String)` and `fun stopSession()` entry points that Task 18's `CaptureViewModel` calls.

`CaptureConfigWriter.toJson` is a pure function (known inputs → JSON string) and is unit-tested here; the actual Camera2/`SensorManager`/`BluetoothGatt` object wiring inside `SurveyCaptureService` is not meaningfully unit-testable — it is verified through the Task 2 spike having already de-risked the pipeline, and through the real-device checklist in spec §16. The service itself has no test; that is intentional, not an oversight (documented here so a reviewer does not flag it as a gap). The exact numeric defaults (`resolution`, `fps`, `bitrateBps`, `keyframeIntervalS`, `segmentDurationS`, `isoSensitivity`, `shutterNs`) come from the Task 2 spike's findings file, not invented here.

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

// Values populated from the Task 2 spike's findings (docs/superpowers/specs/2026-09-28-survey-capture-spike-findings.md),
// not invented here. sensorTimestampSource is always "REALTIME" because CheckSurveyReadinessUseCase
// (Task 11) already hard-blocked any device that is not (spec §10).
data class CaptureConfig(
    val utcAnchorIso: String,
    val elapsedAnchorNs: Long,
    val resolution: String,
    val fps: Int,
    val isoSensitivity: Int,
    val shutterNs: Long,
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
        "shutter_ns":${config.shutterNs},"codec":"${config.codec}","bitrate_bps":${config.bitrateBps},
        "keyframe_interval_s":${config.keyframeIntervalS},"af_locked":true,"awb_locked":true,
        "eis_disabled":true,"hdr_disabled":true,"night_mode_disabled":true,
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
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.camera.ExposureLockController
import com.luxmap.core.camera.SegmentRotationPolicy
import com.luxmap.core.camera.SegmentedVideoRecorder
import com.luxmap.core.location.HeadingSensor
import com.luxmap.core.location.SurveyTrackRecorder
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "survey_capture"
private const val NOTIFICATION_ID = 1001

// Orchestrates the three independent streams (spec §5) — each stream's collector runs in its own
// serviceScope.launch{} so a failure in one (e.g. BLE disconnect, spec §11) never cancels the
// others: SupervisorJob is what makes that isolation hold. Camera2/SensorManager/BluetoothGatt
// object wiring itself is verified on real devices (Task 2 spike, spec §16 checklist), not here.
@AndroidEntryPoint
class SurveyCaptureService : Service() {
    @Inject lateinit var exposureLockController: ExposureLockController

    @Inject lateinit var trackRecorder: SurveyTrackRecorder

    @Inject lateinit var headingSensor: HeadingSensor

    @Inject lateinit var luxClient: LuxSensorBleClient

    @Inject lateinit var sessionDao: SurveySessionDao

    @Inject lateinit var packager: PackageSurveySessionUseCase

    private val serviceScope = CoroutineScope(SupervisorJob())
    private val jobs = mutableListOf<Job>()
    private lateinit var videoRecorder: SegmentedVideoRecorder
    private lateinit var sessionDir: File
    private var currentSessionId: String = ""

    inner class LocalBinder : Binder() {
        fun service(): SurveyCaptureService = this@SurveyCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        videoRecorder =
            SegmentedVideoRecorder(SegmentRotationPolicy(targetDurationMs = SEGMENT_TARGET_DURATION_MS)) { path ->
                RealMuxerPort(path) // wraps android.media.MediaMuxer; finalized against the Task 2 spike
            }
    }

    fun startSession(
        surveySweepId: String,
        luxDeviceAddress: String,
    ) {
        currentSessionId = UUID.randomUUID().toString()
        sessionDir = File(getExternalFilesDir(null), "survey/$currentSessionId").apply { mkdirs() }
        val startedAtElapsedNs = SystemClock.elapsedRealtimeNanos()

        startForeground(NOTIFICATION_ID, buildNotification())

        serviceScope.launch {
            sessionDao.insertSession(
                LocalSurveySessionEntity(
                    sessionId = currentSessionId,
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

        videoRecorder.startSegment(segmentIndex = 0, outputFilePath = File(sessionDir, "segment_0.mp4").absolutePath)

        // Each stream writes to its own NdjsonLogWriter independently — one failing (e.g. an
        // IOException from a full disk) does not cancel the others, since each runs in its own job.
        val luxWriter = NdjsonLogWriter(File(sessionDir, "lux_log.ndjson"), fileRole = "lux_log")
        jobs +=
            serviceScope.launch {
                luxClient.observeSamples(luxDeviceAddress).collect { sample ->
                    luxWriter.appendLine(
                        """{"seq":${sample.seq},"module_ms":${sample.moduleMs},"phone_elapsed_ns":${sample.phoneElapsedNs},"lux":${sample.lux}}""",
                    )
                }
            }
        jobs +=
            serviceScope.launch {
                luxClient.connectionState.collect { state ->
                    if (state is BleConnectionState.Disconnected && currentSessionId.isNotEmpty()) {
                        // Do not stop the session (spec §11) — just flag the gap for later review.
                        sessionDao.sessionById(currentSessionId)?.let { session ->
                            sessionDao.updateSession(session.copy(bleGapDetected = true, updatedAt = Instant.now()))
                        }
                    }
                }
            }

        // GPS track and heading log wiring follow the same NdjsonLogWriter pattern as lux above,
        // fed by SurveyTrackRecorder.onLocationUpdate()/HeadingSensor.headingFromRotationVector()
        // callbacks registered against FusedLocationProviderClient/SensorManager on a real device.
    }

    fun stopSession() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        videoRecorder.stop()
        serviceScope.launch { packager.invoke(currentSessionId) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
        private const val SEGMENT_TARGET_DURATION_MS = 180_000L // overridden with the Task 2 spike's chosen value
    }
}
```

Note: `RealMuxerPort` (the real `android.media.MediaMuxer` wrapper behind `MuxerPort`, Task 5) and the actual `FusedLocationProviderClient`/`SensorManager` registration feeding `SurveyTrackRecorder`/`HeadingSensor` are Android framework glue verified on a real device (Task 2 spike, spec §16 checklist) rather than by a unit test — the wiring pattern above (one `NdjsonLogWriter` per stream, one independent `serviceScope.launch` per stream) is what every stream follows.

- [ ] **Step 7: Build to confirm the service compiles and is registered**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/AndroidManifest.xml \
  app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt \
  app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt
git commit -m "feat(fm-survey): add SurveyCaptureService orchestration and capture_config writer"
```

---

## Task 18: F04 UI — `CaptureScreen`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt`
- Create: `app/src/main/java/com/luxmap/di/CaptureModule.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `LuxSensorBleClient.connectionState` (Task 8); `SurveyCaptureService.startSession(surveySweepId, luxDeviceAddress)`/`stopSession()` (Task 17), reached through the `SurveyCaptureController` interface below so the ViewModel never binds a `Service` or holds a `Context` directly, keeping it constructor-mockable like every other ViewModel in this codebase.
- Produces: `interface SurveyCaptureController { fun startSession(surveySweepId: String, luxDeviceAddress: String); fun stopSession() }`; `sealed interface CaptureUiState { data object AwaitingBleConnection; data object Ready; data object Recording; data object Packaging }` (spec's Bước 0–4 as explicit states, matching the P9 states for F04: BLE not connected/disconnected, recording, storage low, packaging).

- [ ] **Step 1: Write the failing ViewModel test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
package com.luxmap.feature.survey.ui.capture

import app.cash.turbine.test
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.feature.survey.capture.SurveyCaptureController
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `starting a recording tells the controller to start the service session`() =
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
                assertEquals(CaptureUiState.Recording, awaitItem())
            }
            verify { controller.startSession("SWEEP-1", "AA:BB:CC:DD:EE:FF") }
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

// Bước 0-4 of F04 (spec §8/C7) as explicit states — the "Nút Bắt đầu quay" only enables in Ready.
sealed interface CaptureUiState {
    data object AwaitingBleConnection : CaptureUiState

    data object Ready : CaptureUiState

    data object Recording : CaptureUiState

    data object Packaging : CaptureUiState
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Thin seam over binding SurveyCaptureService (Task 17) so CaptureViewModel takes an interface,
// not a Context or a live Service — keeping it constructor-mockable like every other ViewModel
// in this codebase (see HomeViewModel/HomeRepository).
interface SurveyCaptureController {
    fun startSession(
        surveySweepId: String,
        luxDeviceAddress: String,
    )

    fun stopSession()
}

@Singleton
class RealSurveyCaptureController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SurveyCaptureController {
        private var boundService: SurveyCaptureService? = null
        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    boundService = (binder as SurveyCaptureService.LocalBinder).service()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    boundService = null
                }
            }

        override fun startSession(
            surveySweepId: String,
            luxDeviceAddress: String,
        ) {
            val intent = Intent(context, SurveyCaptureService::class.java)
            context.startForegroundService(intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            boundService?.startSession(surveySweepId, luxDeviceAddress)
        }

        override fun stopSession() {
            boundService?.stopSession()
            context.unbindService(connection)
        }
    }
```

`SurveyCaptureController` is not a data Repository, so it does not belong in `RepositoryModule.kt` (per `CLAUDE.md`, that file is for Fake/Real repository bindings only) — create a small dedicated module instead:

```kotlin
// app/src/main/java/com/luxmap/di/CaptureModule.kt
package com.luxmap.di

import com.luxmap.feature.survey.capture.RealSurveyCaptureController
import com.luxmap.feature.survey.capture.SurveyCaptureController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {
    @Binds
    abstract fun bindSurveyCaptureController(impl: RealSurveyCaptureController): SurveyCaptureController
}
```

```kotlin
// app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt
package com.luxmap.feature.survey.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.feature.survey.capture.SurveyCaptureController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

        init {
            viewModelScope.launch {
                luxClient.connectionState.collect { state ->
                    // Only advances AwaitingBleConnection -> Ready; does not regress Recording back
                    // to AwaitingBleConnection on a mid-session drop (spec §11 — session keeps going).
                    if (_uiState.value == CaptureUiState.AwaitingBleConnection && state == BleConnectionState.Connected) {
                        _uiState.value = CaptureUiState.Ready
                    }
                }
            }
        }

        fun onStartRecording(
            surveySweepId: String,
            luxDeviceAddress: String,
        ) {
            if (_uiState.value != CaptureUiState.Ready) return
            captureController.startSession(surveySweepId, luxDeviceAddress)
            _uiState.value = CaptureUiState.Recording
        }

        fun onStopRecording() {
            if (_uiState.value != CaptureUiState.Recording) return
            captureController.stopSession()
            _uiState.value = CaptureUiState.Packaging
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun CaptureScreen(
    surveySweepId: String,
    onSessionPackaged: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (uiState) {
                is CaptureUiState.AwaitingBleConnection ->
                    Text("Đang kết nối cảm biến ánh sáng...", style = MaterialTheme.typography.bodyLarge)

                is CaptureUiState.Ready ->
                    Button(onClick = {
                        // luxDeviceAddress hardcoded pending a BLE scan/pairing step — that step
                        // is real-device integration work, out of this plan's unit-testable scope
                        // (spec §16), and is deliberately not guessed at here.
                        viewModel.onStartRecording(surveySweepId, luxDeviceAddress = KNOWN_LUX_DEVICE_ADDRESS)
                    }) { Text("Bắt đầu quay") }

                is CaptureUiState.Recording ->
                    Button(onClick = viewModel::onStopRecording) { Text("Dừng quay") }

                is CaptureUiState.Packaging -> {
                    Text("Đang đóng gói phiên khảo sát...", style = MaterialTheme.typography.bodyLarge)
                    onSessionPackaged()
                }
            }
        }
    }
}

// Placeholder until Bước 0's BLE scan/pairing UI exists — tracked in docs/contract-drift.md,
// not invented as a real address here.
private const val KNOWN_LUX_DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF"
```

Add `Routes.SURVEY_CAPTURE` (carrying `surveySweepId` as a nav argument) and wire it into `NavGraph.kt`, and update Task 12's `onRouteSelected` callback to navigate to it. Add a line to `docs/contract-drift.md` (Task 1) noting that the BLE device address is hardcoded pending a scan/pairing UI — this is a real product gap (F04's Bước 0 needs a device picker, not just a connection status panel), not just a naming placeholder, so flag it for the project owner rather than treating it as done.

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
git commit -m "feat(fm-survey): add F04 capture screen state machine and capture controller"
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
- [ ] Run ktlint: `./gradlew ktlintCheck` — fix any formatting issues before calling this plan done.
- [ ] Manually run the spec §16 real-device checklist (10+ minute recording, segment rotation check, PTS/sensor-timestamp offset measurement, BLE disconnect/reconnect with a real ESP32+BH1750 module, low-light test) — this cannot be automated and is not covered by any task above.
