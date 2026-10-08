# Survey Package Schema v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the local survey session package (`gps_track.ndjson`, `lux_log.ndjson`, `capture_config.json`) and the capture pipeline that produces them up to the schema v1 / behavior BE confirmed is already live server-side, so the package `RealUploadRepository` sends later (separate plan) needs no further format changes.

**Architecture:** No new features — this plan edits existing `feature/survey/capture` writers and `domain/usecase` readiness logic in place, file by file, each task independently testable (pure JSON-building functions get unit tests; Android-system-dependent pieces follow the project's existing "no automated test possible, real-device checklist" pattern already used for `VideoCaptureSession`/`SurveyCaptureService`).

**Tech Stack:** Kotlin, JUnit + MockK (unit), existing `NdjsonLogWriter`/`CaptureConfigWriter`, no new dependencies.

**Spec:** `mobile.pdf` (leader, 2026-10-08) cross-checked against the backend repo's `docs/survey-ingest-p2a.md` (`D:\ki9\SEP490\App\luxmap_backend`) — both summarized into `docs/contract-drift.md` rows added 2026-10-08 in this mobile repo. Read that file's rows first; it has the full reasoning for each field change and flags two places where BE's feedback appears to assume a different (generic/batched BLE) lux module than the real classic-SPP `LuxMap_ESP32` already confirmed on 2026-09-30 — those two items are explicitly **excluded** from this plan (see Task 5) and need a reply to the leader instead of code.

## Global Constraints

- Every nanosecond value and `module_ms` must be serialized as a JSON **string**, not a number (BE: avoids JS bigint precision loss server-side).
- No new libraries — everything here uses what's already in the project (`NdjsonLogWriter`, `android.location.LocationManager`, `android.provider.Settings.Global`, `androidx.datastore`).
- Comments in new/changed code: English only, WHY not WHAT, per CLAUDE.md.
- Each task is its own commit, format `feat(fm-39): description` — `fm-39` was assigned by the agent (highest `fm-XX` found in `git log`/branches was `fm-38`; no entry for this work exists in `docs/LuxMap_TaskList_v2.xlsx` yet, confirmed with the user 2026-10-08 who asked the agent to pick one). Branch: `feat/fm-39-survey-schema-v1` off `dev`.
- Any field/behavior this plan does not explicitly specify (BE's firmware meeting could still "chỉnh nhẹ" the SELF-SIGNED API) is out of scope — do not extrapolate beyond what's written here or in `docs/contract-drift.md`.

## Review Focus

- A `null` heading (`gps_bearing_deg`/`heading_deg` when `location.hasBearing()` is false, e.g. the phone is stationary) must still serialize as JSON `null`, not `0` or a missing key — the existing code already does this correctly (`point.gpsBearingDeg ?: "null"`); a careless rewrite of that line is the most likely place to regress it.
- `module_epoch` must not increment on the very first lux sample of a session (no previous `module_ms` to compare against) — only on an actual backward jump.
- `sample_no` in both `gps_track.ndjson` and `lux_log.ndjson` must restart at 0 per new sweep/session, not accumulate across app restarts or across a BLE reconnect mid-session.
- A device whose `Settings.Global.BOOT_COUNT` read throws `SettingNotFoundException` (observed to exist on some OEM builds) must not crash the capture flow — needs a safe fallback, not an uncaught exception.
- `CaptureConfigWriter.toJson()` must still produce valid JSON when `focus_distance` is `0.0f` (infinity, a legitimate value) vs. absent — `0.0` is falsy-looking but correct here, do not special-case it into `null`.

---

## File Structure

| File | Responsibility |
|---|---|
| `feature/survey/capture/LocationHeadingRecorder.kt` | Modify: switch to raw `GPS_PROVIDER`, rename fields, add `sample_no`/`provider`, drop the rotation-vector heading half |
| `core/location/HeadingSensor.kt` | Delete (Task 5, after confirmation) |
| `feature/survey/capture/SurveyCaptureService.kt` | Modify: lux sample field changes, clip duration constant, `capture_config.json` wiring, `boot_session_id` plumbing |
| `core/ble/LuxPacketCodec.kt` | Modify: track `module_epoch` via `module_ms` backward-jump detection |
| `feature/survey/capture/CaptureConfigWriter.kt` | Modify: full schema v1 object shape |
| `core/common/BootSessionProvider.kt` | Create: `boot_session_id` UUID generation/persistence keyed on `Settings.Global.BOOT_COUNT`, via DataStore |
| `feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt` | Modify: n/a — threshold check moves to a new live gate (see below), this file is untouched |
| `feature/survey/ui/capture/CaptureViewModel.kt` + `CaptureUiState.kt` | Modify: live GPS-accuracy gate before "Bắt đầu quay" is enabled |
| `feature/survey/domain/GpsAccuracyGate.kt` | Create: pure function/state machine deciding "accuracy held ≤ threshold for N seconds" |

---

### Task 1: Rename `gps_track.ndjson` fields and switch to raw GPS_PROVIDER

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt`
- Modify: `app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt:11-18` (rename `elapsedRealtimeNs` → keep as-is internally; only the JSON output field name changes — see step 3)
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/LocationHeadingRecorderGpsLineTest.kt` (new)

**Interfaces:**
- Consumes: `SurveyTrackRecorder.onLocationUpdate(location: Location): TrackPoint` (unchanged signature)
- Produces: a pure `buildGpsTrackLine(point: TrackPoint, sampleNo: Int): String` function other tasks/tests can call directly without Android location APIs

- [ ] **Step 1: Write the failing test for the line-building function**

```kotlin
package com.luxmap.feature.survey.capture

import com.luxmap.core.location.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationHeadingRecorderGpsLineTest {
    @Test
    fun `serializes heading_deg and sample_no, null bearing stays null`() {
        val point = TrackPoint(
            elapsedRealtimeNs = 112157310000000L,
            lat = 10.7605505,
            lng = 106.6300307,
            accuracyM = 11.7f,
            gpsBearingDeg = null,
            speedMps = null,
        )
        val line = buildGpsTrackLine(point, sampleNo = 0)
        assertEquals(
            """{"sample_no":0,"phone_elapsed_ns":"112157310000000","lat":10.7605505,"lng":106.6300307,""" +
                """"accuracy_m":11.7,"heading_deg":null,"speed_mps":null,"provider":"gps"}""",
            line,
        )
    }

    @Test
    fun `serializes a real bearing value, not null`() {
        val point = TrackPoint(
            elapsedRealtimeNs = 1L,
            lat = 1.0,
            lng = 2.0,
            accuracyM = 5f,
            gpsBearingDeg = 87.5f,
            speedMps = 1.2f,
        )
        val line = buildGpsTrackLine(point, sampleNo = 3)
        assertEquals(
            """{"sample_no":3,"phone_elapsed_ns":"1","lat":1.0,"lng":2.0,""" +
                """"accuracy_m":5.0,"heading_deg":87.5,"speed_mps":1.2,"provider":"gps"}""",
            line,
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.capture.LocationHeadingRecorderGpsLineTest"`
Expected: FAIL — `buildGpsTrackLine` is unresolved.

- [ ] **Step 3: Add `buildGpsTrackLine` and switch the location source to raw GPS_PROVIDER**

Replace the whole file content of `LocationHeadingRecorder.kt` with:

```kotlin
package com.luxmap.feature.survey.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.luxmap.core.location.GpsSignalState
import com.luxmap.core.location.SurveyTrackRecorder
import com.luxmap.core.location.TrackPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

// BE asked for raw GNSS fixes (LocationManager.GPS_PROVIDER), not fused/blended location, so a
// fix indoors or from cell towers never looks like a real on-route position in the track BE
// matches video frames against.
fun buildGpsTrackLine(
    point: TrackPoint,
    sampleNo: Int,
): String =
    """{"sample_no":$sampleNo,"phone_elapsed_ns":"${point.elapsedRealtimeNs}",""" +
        """"lat":${point.lat},"lng":${point.lng},"accuracy_m":${point.accuracyM},""" +
        """"heading_deg":${point.gpsBearingDeg ?: "null"},"speed_mps":${point.speedMps ?: "null"},""" +
        """"provider":"gps"}"""

class LocationHeadingRecorder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackRecorder: SurveyTrackRecorder,
    ) {
        private val locationManager = context.getSystemService(LocationManager::class.java)
        private var locationListener: LocationListener? = null
        private var tickTimer: Timer? = null
        private var sampleNo = 0

        private val _gpsSignalState = MutableStateFlow<GpsSignalState>(GpsSignalState.Ok)
        val gpsSignalState: StateFlow<GpsSignalState> = _gpsSignalState.asStateFlow()

        private val _livePoint = MutableStateFlow<TrackPoint?>(null)
        val livePoint: StateFlow<TrackPoint?> = _livePoint.asStateFlow()

        val distanceMeters: StateFlow<Float> get() = trackRecorder.totalDistanceMeters

        @SuppressLint("MissingPermission")
        fun start(gpsWriter: NdjsonLogWriter) {
            sampleNo = 0
            val listener =
                LocationListener { location: Location ->
                    val point = trackRecorder.onLocationUpdate(location)
                    _livePoint.value = point
                    gpsWriter.appendLine(buildGpsTrackLine(point, sampleNo))
                    sampleNo++
                }
            locationListener = listener
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )

            tickTimer =
                Timer(true).apply {
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
            locationListener?.let { locationManager.removeUpdates(it) }
            tickTimer?.cancel()
        }

        private companion object {
            const val LOCATION_INTERVAL_MS = 1_000L
            const val GPS_SIGNAL_CHECK_INTERVAL_MS = 1_000L
        }
    }
```

Note: this removes the `headingWriter`/rotation-vector parameter from `start()` — callers are fixed in Task 5 together with the `HeadingSensor` deletion, since they are the same removal. Until Task 5 lands, `SurveyCaptureService` will fail to compile after this task; **Task 1 and Task 5 must be committed together** (see Task 5's note) or `start()` should temporarily keep accepting an unused `headingWriter: NdjsonLogWriter?` parameter if the user wants them split — ask before choosing.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.capture.LocationHeadingRecorderGpsLineTest"`
Expected: PASS

- [ ] **Step 5: Commit** (combine with Task 5 per the note above, or hold this commit until Task 5's deletion is also ready)

---

### Task 2: `lux_log.ndjson` — add `sample_no`, `module_epoch`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt:247-255` (the `luxClient.samples.collect` block)
- Test: `app/src/test/java/com/luxmap/core/ble/LuxPacketCodecTest.kt` (extend existing test file if present, else create)

**Interfaces:**
- Consumes: nothing new
- Produces: `LuxSample` gains `sampleNo: Int` and `moduleEpoch: Int`; `LuxPacketCodec` becomes stateful per connection (holds last `moduleMs` and current epoch) — callers must call `LuxPacketCodec.reset()` on every new BLE/SPP connection (already-existing `connect()` call site in `SurveyCaptureService`/`LuxSensorBleClient` — wire it there)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.ble

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LuxPacketCodecTest {
    private lateinit var codec: LuxPacketCodec

    @Before
    fun setUp() {
        codec = LuxPacketCodec()
    }

    @Test
    fun `first sample is epoch 0`() {
        val sample = codec.decode("2026-09-30 23:32:07 | Light: 69.17 lux", receivedAtElapsedRealtimeNs = 1L)
        assertEquals(0, sample?.sampleNo)
        assertEquals(0, sample?.moduleEpoch)
    }

    @Test
    fun `module_ms jumping backward bumps the epoch`() {
        codec.decode("2026-09-30 23:32:07 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 1L)
        val afterReboot = codec.decode("2000-01-01 00:00:01 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 2L)
        assertEquals(1, afterReboot?.moduleEpoch)
    }

    @Test
    fun `sample_no increments per decoded line, does not reset on a bad line`() {
        codec.decode("2026-09-30 23:32:07 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 1L)
        codec.decode("garbage", receivedAtElapsedRealtimeNs = 2L)
        val third = codec.decode("2026-09-30 23:32:08 | Light: 1.0 lux", receivedAtElapsedRealtimeNs = 3L)
        assertEquals(1, third?.sampleNo)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.ble.LuxPacketCodecTest"`
Expected: FAIL — `LuxPacketCodec()` no longer matches the current `object` declaration, `sampleNo`/`moduleEpoch` unresolved.

- [ ] **Step 3: Rewrite `LuxPacketCodec` as a stateful class**

```kotlin
package com.luxmap.core.ble

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LINE_PATTERN = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) \| Light: ([0-9.]+) lux$""")
private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

data class LuxSample(
    val sampleNo: Int,
    val moduleEpoch: Int,
    val moduleMs: Long,
    val phoneElapsedNs: Long,
    val lux: Float,
)

// Stateful per BLE/SPP connection (sample_no and module_epoch both reset on reconnect) — callers
// must create a new instance, or call reset(), per connect() (spec: docs/contract-drift.md, lux_log
// row, 2026-10-08). module_epoch only tracks module_ms going backward: this device's serial line
// has no onboard sequence number to compare, unlike what BE's generic feedback assumed.
class LuxPacketCodec {
    private var nextSampleNo = 0
    private var currentEpoch = 0
    private var lastModuleMs: Long? = null

    fun reset() {
        nextSampleNo = 0
        currentEpoch = 0
        lastModuleMs = null
    }

    fun decode(
        line: String,
        receivedAtElapsedRealtimeNs: Long,
    ): LuxSample? {
        val match = LINE_PATTERN.matchEntire(line.trim()) ?: return null
        val (timestampText, luxText) = match.destructured
        val moduleMs =
            LocalDateTime.parse(timestampText, TIMESTAMP_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        lastModuleMs?.let { previous -> if (moduleMs < previous) currentEpoch++ }
        lastModuleMs = moduleMs
        val sample = LuxSample(nextSampleNo, currentEpoch, moduleMs, receivedAtElapsedRealtimeNs, luxText.toFloat())
        nextSampleNo++
        return sample
    }
}
```

- [ ] **Step 4: Update the one call site that constructed `LuxPacketCodec.decode(line, ts, seq)` with a manually-tracked `seq`**

Find it with: `./gradlew :app:compileDebugKotlin` (will fail and point at the call site — likely `LuxSensorBleClient.kt`). Change it to hold one `LuxPacketCodec` instance per connection, call `reset()` in the same place the socket is (re)opened, and call `codec.decode(line, receivedAtElapsedRealtimeNs)` (no `seq` argument — it's now internal).

- [ ] **Step 5: Update `SurveyCaptureService.kt:250-253`'s lux line-building to the new fields**

```kotlin
luxWriter.appendLine(
    """{"sample_no":${sample.sampleNo},"module_epoch":${sample.moduleEpoch},""" +
        """"seq":${sample.sampleNo},"module_ms":"${sample.moduleMs}",""" +
        """"lux":${sample.lux},"phone_elapsed_ns":"${sample.phoneElapsedNs}"}""",
)
```

Note: BE's example keeps a `"seq"` field alongside `sample_no` — since the real device has no onboard seq, this reuses `sampleNo` for both rather than inventing a second counter. Flag this choice to the leader when replying about the batched-packet/seq-reset mismatch (same reply as the `contract-drift.md` row) rather than treating it as silently decided.

- [ ] **Step 6: Run test to verify it passes, then compile the whole module**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.ble.LuxPacketCodecTest" && ./gradlew :app:compileDebugKotlin`
Expected: PASS, BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ble/LuxPacketCodec.kt app/src/test/java/com/luxmap/core/ble/LuxPacketCodecTest.kt app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt
git commit -m "feat(fm-39): add sample_no/module_epoch to lux_log.ndjson"
```

---

### Task 3: Nanosecond and `module_ms` values as JSON strings everywhere

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt` (already strings after Task 1 — verify `"${point.elapsedRealtimeNs}"` has the quotes, it does in Task 1's version)
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt` (lux line — already done in Task 2 Step 5)
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt` (done together with Task 4's rewrite — no separate step needed)

This task is a **verification pass**, not new code — Tasks 1, 2 and 4 already produce quoted ns/`module_ms` strings by construction. Its only job is to catch anywhere this plan missed.

- [ ] **Step 1: Grep for any remaining unquoted ns/ms field in a JSON-building string**

Run: `grep -rn '_ns":\$\|_ms":\$' app/src/main/java/com/luxmap/feature/survey/capture/ app/src/main/java/com/luxmap/core/ble/`

Expected after Tasks 1/2/4 land: no matches (every `_ns`/`_ms` field is followed by `"$`, not bare `$`).

- [ ] **Step 2: If any match is found, quote it and re-run Step 1 until clean.**

- [ ] **Step 3: Commit (only if Step 2 made changes; otherwise fold this verification into Task 4's commit)**

---

### Task 4: `capture_config.json` — full schema v1 shape

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt` (full rewrite)
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt:395-420` (the `CaptureConfig(...)` construction site)
- Test: `app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt` (new)

**Interfaces:**
- Consumes: `FinalizedVideoCapture` (existing, from `VideoCaptureSession.stop()` — has `actualProfile.isoSensitivity/exposureTimeNs/frameDurationNs/focusDistanceDiopters`, `cameraManufacturer/cameraModel/cameraId`)
- Produces: `CaptureConfigWriter.toJson(config: CaptureConfig): String` matching `survey-ingest-p2a.md` §4.2 exactly

Mount values (`mount_height_m`, `angle_deg`, `sensor_position`, `camera_side`) are **not captured anywhere today** — nothing in the capture pipeline measures them. Per BE's own example and the "gắn đầu xe, nhìn thẳng ra trước" instruction, this task hardcodes them as named constants (same pattern the codebase already uses for `luxModuleId = "LUX-001"` as a dated placeholder), not a guess buried in a literal:

```kotlin
// Fixed mount values until WP4/project owner confirms per-vehicle mount measurement is needed —
// every survey rig today mounts the same way (handlebar, front-facing). Source: mobile.pdf
// (leader, 2026-10-08) description of the one mounting method currently used.
private const val MOUNT_CAMERA_SIDE = "front"
private const val MOUNT_HEIGHT_M = 1.1
private const val MOUNT_ANGLE_DEG = 10
private const val MOUNT_SENSOR_POSITION = "handlebar_top"
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.survey.capture

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class CaptureConfigWriterTest {
    @Test
    fun `produces valid json with the fields BE's schema v1 requires`() {
        val config = CaptureConfig(
            bootSessionId = "95b59f63-5083-4e5f-911d-c070e75351d5",
            elapsedAnchorNs = 112156244127385L,
            utcAnchorIso = "2026-10-02T15:31:26.432377Z",
            utcUncertaintyMs = 50,
            phoneModel = "samsung SM-A075F",
            cameraId = "0",
            appVersion = "1.0",
            iso = 1600,
            exposureTimeNs = 30_004_000L,
            aperture = 1.8,
            fps = 30,
            focusDistanceDiopters = 0.0f,
            whiteBalanceCctK = 4000,
            widthPx = 1920,
            heightPx = 1080,
            orientation = "portrait",
            profileId = 1,
            moduleFirmwareVersionId = 1,
        )
        val json = JSONObject(CaptureConfigWriter.toJson(config))
        assertEquals(1, json.getInt("schema_version"))
        assertEquals("95b59f63-5083-4e5f-911d-c070e75351d5", json.getString("boot_session_id"))
        assertEquals("REALTIME", json.getString("sensor_timestamp_source"))
        val camera = json.getJSONObject("camera")
        assertEquals("30004000", camera.getString("exposure_time_ns"))
        assertEquals(false, camera.getBoolean("ae_enabled"))
        val mount = json.getJSONObject("mount")
        assertEquals("front", mount.getString("camera_side"))
        assertEquals(1.1, mount.getDouble("mount_height_m"), 0.001)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.capture.CaptureConfigWriterTest"`
Expected: FAIL — `CaptureConfig` constructor shape does not match.

- [ ] **Step 3: Rewrite `CaptureConfigWriter.kt`**

```kotlin
package com.luxmap.feature.survey.capture

data class CaptureConfig(
    val bootSessionId: String,
    val elapsedAnchorNs: Long,
    val utcAnchorIso: String,
    val utcUncertaintyMs: Int,
    val phoneModel: String,
    val cameraId: String,
    val appVersion: String,
    // Actually-measured values from the last CaptureResult (VideoCaptureSession.stop()), not the
    // requested config — BE measured a real mismatch between the two in a reviewed sample.
    val iso: Int,
    val exposureTimeNs: Long,
    val aperture: Double,
    val fps: Int,
    val focusDistanceDiopters: Float,
    val whiteBalanceCctK: Int,
    val widthPx: Int,
    val heightPx: Int,
    val orientation: String,
    // Tạm để là 1 per BE — no registry exists yet to resolve a real ID (spec: survey-ingest-p2a.md §Tạo phiên)
    val profileId: Int,
    val moduleFirmwareVersionId: Int,
)

object CaptureConfigWriter {
    fun toJson(config: CaptureConfig): String =
        """
        {"schema_version":1,"boot_session_id":"${config.bootSessionId}",
        "profile_id":${config.profileId},"module_firmware_version_id":${config.moduleFirmwareVersionId},
        "phone_model":"${config.phoneModel}","camera_id":"${config.cameraId}","app_version":"${config.appVersion}",
        "sensor_timestamp_source":"REALTIME",
        "elapsed_anchor_ns":"${config.elapsedAnchorNs}","utc_anchor":"${config.utcAnchorIso}",
        "utc_uncertainty_ms":${config.utcUncertaintyMs},
        "camera":{"iso":${config.iso},"exposure_time_ns":"${config.exposureTimeNs}","aperture":${config.aperture},
        "fps":${config.fps},"focus_mode":"manual","focus_distance":${config.focusDistanceDiopters},
        "white_balance_mode":"manual","white_balance_value":{"cct_k":${config.whiteBalanceCctK}},
        "resolution":{"width":${config.widthPx},"height":${config.heightPx}},
        "ae_enabled":false,"eis_enabled":false,"hdr_enabled":false,"night_mode_enabled":false},
        "mount":{"camera_side":"$MOUNT_CAMERA_SIDE","mount_height_m":$MOUNT_HEIGHT_M,
        "angle_deg":$MOUNT_ANGLE_DEG,"sensor_position":"$MOUNT_SENSOR_POSITION"},
        "orientation":"${config.orientation}"}
        """.trimIndent().replace("\n", "").replace(Regex("""\s+"""), " ").trim()

    // Fixed mount values until WP4/project owner confirms per-vehicle mount measurement is needed —
    // every survey rig today mounts the same way (handlebar, front-facing). Source: mobile.pdf
    // (leader, 2026-10-08).
    private const val MOUNT_CAMERA_SIDE = "front"
    private const val MOUNT_HEIGHT_M = 1.1
    private const val MOUNT_ANGLE_DEG = 10
    private const val MOUNT_SENSOR_POSITION = "handlebar_top"
}
```

Note: this version deliberately **omits** `clips[]` (the video-PTS-mapping block) — that depends on Task 6 below (`ffprobe`-equivalent PTS extraction does not exist in the app at all yet; BE's worker needs it only for P2b-2 processing, not for upload itself, and `survey-ingest-p2a.md` confirms P2a accepts the config without it: "P2a vẫn giữ cấu hình gốc"). Flag this as a known gap for the upload plan, not silently dropped — add it to `docs/contract-drift.md` in Task 6's step.

Note: `aperture`/`whiteBalanceCctK` are not read from a live `CaptureResult` anywhere in the current code (`LENS_APERTURE` is usually fixed per lens and `CONTROL_AWB_MODE`/white balance locking does not exist yet) — wiring those through `VideoCaptureSession` is **Task 7**, not this task. Until Task 7 lands, `SurveyCaptureService`'s call site passes a hardcoded `aperture`/`whiteBalanceCctK` with a comment pointing at Task 7, same pattern as the mount constants above.

- [ ] **Step 4: Update the `CaptureConfig(...)` construction site in `SurveyCaptureService.kt:395-420`**

```kotlin
captureConfigFile.writeText(
    CaptureConfigWriter.toJson(
        CaptureConfig(
            bootSessionId = currentBootSessionId,
            elapsedAnchorNs = startedAtElapsedNs,
            utcAnchorIso = utcAnchorIso,
            utcUncertaintyMs = UTC_UNCERTAINTY_MS_SYSTEM_CLOCK,
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            cameraId = finalized.cameraId,
            appVersion = BuildConfig.VERSION_NAME,
            iso = finalized.actualProfile.isoSensitivity,
            exposureTimeNs = finalized.actualProfile.exposureTimeNs,
            // TODO(Task 7): read the real LENS_APERTURE from CaptureResult instead of this fixed value
            aperture = 1.8,
            fps = VIDEO_FPS,
            focusDistanceDiopters = finalized.actualProfile.focusDistanceDiopters,
            // TODO(Task 7): read the real white balance CCT from CaptureResult once AWB lock exists
            whiteBalanceCctK = 4000,
            widthPx = VIDEO_WIDTH,
            heightPx = VIDEO_HEIGHT,
            orientation = "portrait",
            profileId = 1,
            moduleFirmwareVersionId = 1,
        ),
    ),
)
```

`currentBootSessionId` and `UTC_UNCERTAINTY_MS_SYSTEM_CLOCK` come from Task 8 (`BootSessionProvider`) — until that task lands, use a local `private var currentBootSessionId = UUID.randomUUID().toString()` set once in `onCreate()` as a stopgap, and note it in the commit message as temporary.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.capture.CaptureConfigWriterTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt app/src/test/java/com/luxmap/feature/survey/capture/CaptureConfigWriterTest.kt app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt
git commit -m "feat(fm-39): rewrite capture_config.json to schema v1"
```

---

### Task 5: Drop the rotation-vector `HeadingSensor`/`heading_log.ndjson` pipeline

**This task starts with a confirmation checkpoint, not code** — CLAUDE.md requires not removing a used component without the project owner confirming, even though BE's direction is clear. Ask before Step 1.

**Files:**
- Delete: `app/src/main/java/com/luxmap/core/location/HeadingSensor.kt`
- Delete: any test file for it (`find app/src/test -iname "*HeadingSensor*"`)
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt` (remove `headingWriter` field, its `NdjsonLogWriter` construction, its `close()` call, the `heading_log.ndjson` path field write, and `LocationHeadingRecorder.start()`'s now-extra argument)
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt` (drop `headingLogFilePath` — check its usages first with `grep -rn headingLogFilePath app/src/main/java/`)
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt:36` (remove the `headingLogFilePath` manifest entry)
- Modify: `app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt:66-70,81` (drop the now-pointless rotation-vector-sensor-availability check — `headingAvailable` input)
- Modify: DI module wiring `HeadingSensor` (search `grep -rn HeadingSensor app/src/main/java/com/luxmap/di/`)

- [ ] **Step 1: Confirm with the user/project owner before touching any file in this task.**

- [ ] **Step 2: Delete `HeadingSensor.kt` and its test, remove all references**

Run `grep -rln HeadingSensor app/src/main/java/ app/src/test/java/` first and fix every hit, including DI modules. Re-run the grep until empty.

- [ ] **Step 3: Remove `heading_log.ndjson` from `SurveyCaptureService.kt`, `LocalSurveySessionEntity.kt`, `PackageSurveySessionUseCase.kt`**

Each removal is deleting the line(s) that mention `headingWriter`/`headingLogFilePath`/`"heading_log"` — no replacement logic needed, this data stream simply stops existing.

- [ ] **Step 4: Update `LocationHeadingRecorder.start()` call site in `SurveyCaptureService.kt` to the Task 1 signature (`start(gpsWriter)`, one argument)**

- [ ] **Step 5: Compile and run the full survey feature's existing test suite**

Run: `./gradlew :app:compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests still pass (none should have asserted on `heading_log` specifically — if one does, that test is also deleted here, not patched around).

- [ ] **Step 6: Add the clips[]-omission note to `docs/contract-drift.md` (from Task 4's note) and mark the `heading_log` gap row in that file as fully resolved/removed, not just a decision pending action**

- [ ] **Step 7: Commit together with Task 1 (same PR is fine, same commit is also fine since the two are one logical change — `headingWriter` param removal only compiles once this deletion lands)**

```bash
git add -A
git commit -m "feat(fm-39): drop rotation-vector heading_log, use GPS-derived heading only"
```

---

### Task 6: Clip segment duration 180s → 60s

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt:518`

- [ ] **Step 1: Change the constant**

```kotlin
private const val SEGMENT_TARGET_DURATION_MS = 60_000L
```

(was `180_000L`)

- [ ] **Step 2: Check for any test asserting the old value**

Run: `grep -rn "180_000\|180000" app/src/test/ app/src/androidTest/`

Fix any match to `60_000`/`60000`.

- [ ] **Step 3: Compile and run tests**

Run: `./gradlew :app:compileDebugKotlin testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt
git commit -m "feat(fm-39): cut survey clip segments to 60s"
```

---

### Task 7: Camera actual-value capture — aperture, white balance lock, AWB CaptureResult read, focus-at-infinity

**This task needs its own design pass before coding** — it touches `ExposureLockController`/`VideoCaptureSession`'s exposure-lock sequencing, which is one of the most carefully-commented, already-hardened parts of the codebase (see the CancellationException handling, the AE/AF convergence wait logic). Do not edit it inline as a side effect of this plan's other tasks.

**Files (for scoping only — do not write code yet, this is the next planning step):**
- `app/src/main/java/com/luxmap/feature/survey/capture/VideoCaptureSession.kt` (exposure/focus/AWB lock sequencing, `FinalizedVideoCapture` shape)
- `app/src/main/java/com/luxmap/core/camera/ExposureLockController.kt` (if AWB lock belongs here instead)

Known requirements for this task, gathered from `mobile.pdf`/`survey-ingest-p2a.md` but **not yet turned into steps**:
1. Lock white balance (`CONTROL_AWB_MODE` manual or locked) in addition to the existing AE/exposure lock.
2. Lock focus at infinity specifically (BE: "Khoá thêm cân bằng trắng và nét ở vô cực") — different from the current behavior, which waits for `CONTROL_AF_STATE_FOCUSED_LOCKED` convergence and reads back whatever distance that converges to (could be any distance, not infinity). Needs a decision: switch to `CONTROL_AF_MODE_OFF` + a fixed `LENS_FOCUS_DISTANCE = 0.0f` (infinity) instead of autofocus-then-lock, or keep autofocus convergence and just also request infinity as a hint — these are different behaviors with different risk (fixed infinity focus could be worse in some near-field equipment-inspection use, if that ever happens on this same screen).
3. Read `LENS_APERTURE` and the real white-balance value from `CaptureResult` for `capture_config.json` (Task 4's two `TODO(Task 7)` placeholders).
4. Block recording if `SENSOR_INFO_TIMESTAMP_SOURCE != REALTIME` — already implemented, confirm it's still correct (`CheckSurveyReadinessUseCase`/`SurveyReadinessInputProvider.kt:44-45` already hard-blocks this via `timestampSourceRealtime`).

- [ ] **Step 1: Before writing any step for this task, present the focus-at-infinity design choice (above, point 2) to the user and get a decision.**
- [ ] **Step 2 onward: written after Step 1's answer — not detailed here to avoid presenting a false choice as already decided.**

---

### Task 8: `boot_session_id` generation + reboot-mid-session handling

**Files:**
- Create: `app/src/main/java/com/luxmap/core/common/BootSessionProvider.kt`
- Test: `app/src/test/java/com/luxmap/core/common/BootSessionProviderTest.kt` (new)
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt` or a new small Hilt module — DataStore instance, if one for this purpose does not already exist (check `grep -rn "androidx.datastore" app/src/main/java/com/luxmap/di/`)
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt` (inject `BootSessionProvider`, replace the Task 4 Step 4 stopgap)

**Interfaces:**
- Produces: `interface BootSessionProvider { suspend fun currentBootSessionId(): String }` — returns the same UUID for the lifetime of one boot, a new UUID the first time it's read after `Settings.Global.BOOT_COUNT` changes

- [ ] **Step 1: Write the failing test (using a fake `BootCountReader` so no real `ContentResolver` is needed)**

```kotlin
package com.luxmap.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BootSessionProviderTest {
    private class FakeStore {
        var savedBootCount: Int? = null
        var savedUuid: String? = null
    }

    @Test
    fun `same boot count returns the same uuid`() = runTest {
        val store = FakeStore()
        val provider = InMemoryBootSessionProvider(store, bootCount = 5)
        val first = provider.currentBootSessionId()
        val second = provider.currentBootSessionId()
        assertEquals(first, second)
    }

    @Test
    fun `boot count change produces a new uuid`() = runTest {
        val store = FakeStore()
        val before = InMemoryBootSessionProvider(store, bootCount = 5).currentBootSessionId()
        val after = InMemoryBootSessionProvider(store, bootCount = 6).currentBootSessionId()
        assertNotEquals(before, after)
    }
}
```

(`InMemoryBootSessionProvider` is a small test-only implementation of the same interface, backed by the `FakeStore` instead of DataStore — written in Step 3 alongside the real one, sharing the core "compare boot count, regenerate if different" logic through a small internal function both can call.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.common.BootSessionProviderTest"`
Expected: FAIL — classes don't exist yet.

- [ ] **Step 3: Implement `BootSessionProvider.kt`**

```kotlin
package com.luxmap.core.common

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.bootSessionDataStore by preferencesDataStore(name = "boot_session")
private val KEY_BOOT_COUNT = intPreferencesKey("last_boot_count")
private val KEY_BOOT_SESSION_ID = stringPreferencesKey("boot_session_id")

interface BootSessionProvider {
    suspend fun currentBootSessionId(): String
}

// Settings.Global.BOOT_COUNT increments by one on every real device boot (not app restart) — used
// as the signal to end an in-progress sweep and start a new one if the phone reboots mid-session
// (spec: survey-ingest-p2a.md, boot_session_id). SettingNotFoundException is caught, not propagated:
// some OEM builds are known to omit this setting, and the capture flow must not crash over it.
@Singleton
class RealBootSessionProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BootSessionProvider {
        override suspend fun currentBootSessionId(): String {
            val currentBootCount =
                try {
                    Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
                } catch (error: Settings.SettingNotFoundException) {
                    -1
                }
            val prefs = context.bootSessionDataStore.data.first()
            val savedBootCount = prefs[KEY_BOOT_COUNT]
            val savedUuid = prefs[KEY_BOOT_SESSION_ID]
            if (savedBootCount == currentBootCount && savedUuid != null) return savedUuid

            val newUuid = UUID.randomUUID().toString()
            context.bootSessionDataStore.edit { store ->
                store[KEY_BOOT_COUNT] = currentBootCount
                store[KEY_BOOT_SESSION_ID] = newUuid
            }
            return newUuid
        }
    }
```

- [ ] **Step 4: Write `InMemoryBootSessionProvider` (test-only, same file as the test or a `testFixtures` source set if the project has one — check `grep -n testFixtures app/build.gradle.kts` first; if absent, put it directly in the test file as a private class)**

```kotlin
private class InMemoryBootSessionProvider(
    private val store: FakeStore,
    private val bootCount: Int,
) {
    suspend fun currentBootSessionId(): String {
        if (store.savedBootCount == bootCount && store.savedUuid != null) return store.savedUuid!!
        val newUuid = java.util.UUID.randomUUID().toString()
        store.savedBootCount = bootCount
        store.savedUuid = newUuid
        return newUuid
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.common.BootSessionProviderTest"`
Expected: PASS

- [ ] **Step 6: Wire `BootSessionProvider` into `SurveyCaptureService` (replace Task 4 Step 4's stopgap `UUID.randomUUID()`), and detect a reboot mid-session**

This needs a decision this plan does not make for you: **what should happen to an in-progress recording if `BOOT_COUNT` changes while `SurveyCaptureService` is alive?** In practice, a phone reboot kills the foreground service and the process entirely — Android does not keep a `Service` alive across a reboot. So the realistic scope is: on the **next** `ACTION_START` after a reboot, use the new `boot_session_id`, and if a session directory from before the reboot is still `recordingState = "recording"` in Room (never reached `stopped`/`packaged`), treat it the same way the existing crash-recovery path (Task 16 per `SurveyCaptureService.kt:78`'s comment — check `grep -n "crash recover" app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt`) already handles an interrupted session, rather than building a second recovery path. Confirm this reasoning with the user before writing this step's code.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/core/common/BootSessionProvider.kt app/src/test/java/com/luxmap/core/common/BootSessionProviderTest.kt app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt
git commit -m "feat(fm-39): generate boot_session_id, detect reboot via BOOT_COUNT"
```

---

### Task 9: Live GPS-accuracy gate before "Bắt đầu quay"

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/domain/GpsAccuracyGate.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/domain/GpsAccuracyGateTest.kt` (new)
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt` (feed `livePoint`/accuracy into the gate while not yet Recording, expose the gate's boolean in the pre-record UI state)
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt` (the pre-recording state needs a new field, e.g. `gpsReadyToRecord: Boolean` — check the current pre-record state's name first with `grep -n "sealed interface CaptureUiState" -A 20 app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt`)
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt:271` (disable the "Bắt đầu quay" `Button` when not ready, per Design System's disabled-state tokens — `color.disabled.container`/`disabled.content`, mục 2.2)

**Interfaces:**
- Produces: `class GpsAccuracyGate(thresholdMeters: Float, holdDurationMs: Long) { fun onAccuracyUpdate(accuracyM: Float, nowElapsedRealtimeNs: Long): Boolean }` — pure, testable without Android

This threshold (~8m held a few seconds) is explicitly **"đề xuất, chốt sau buổi quay thử"** per BE — not final. Implement it as a named, overridable constant so changing the number later is a one-line diff, and flag in the commit message that it is provisional.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.survey.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsAccuracyGateTest {
    private val gate = GpsAccuracyGate(thresholdMeters = 8f, holdDurationMs = 3_000L)

    @Test
    fun `not ready on the first good reading alone`() {
        assertFalse(gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 0L))
    }

    @Test
    fun `ready once accuracy stays under threshold for the full hold duration`() {
        gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 0L)
        assertTrue(gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 3_000_000_000L))
    }

    @Test
    fun `a bad reading mid-hold resets the timer`() {
        gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 0L)
        gate.onAccuracyUpdate(20f, nowElapsedRealtimeNs = 2_000_000_000L)
        assertFalse(gate.onAccuracyUpdate(5f, nowElapsedRealtimeNs = 4_000_000_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.domain.GpsAccuracyGateTest"`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement `GpsAccuracyGate`**

```kotlin
package com.luxmap.feature.survey.domain

// Threshold/hold duration are BE's PROPOSAL (mobile.pdf, 2026-10-08), explicitly "chốt sau buổi
// quay thử" — not final. Named constructor params, not hardcoded inline, so the real number (once
// confirmed) is a one-line change at the call site, not a search through this file.
class GpsAccuracyGate(
    private val thresholdMeters: Float,
    private val holdDurationMs: Long,
) {
    private var goodSinceElapsedNs: Long? = null

    fun onAccuracyUpdate(
        accuracyM: Float,
        nowElapsedRealtimeNs: Long,
    ): Boolean {
        if (accuracyM > thresholdMeters) {
            goodSinceElapsedNs = null
            return false
        }
        val since = goodSinceElapsedNs ?: nowElapsedRealtimeNs.also { goodSinceElapsedNs = it }
        return (nowElapsedRealtimeNs - since) >= holdDurationMs * 1_000_000L
    }

    companion object {
        const val PROPOSED_THRESHOLD_METERS = 8f
        const val PROPOSED_HOLD_DURATION_MS = 3_000L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.domain.GpsAccuracyGateTest"`
Expected: PASS

- [ ] **Step 5: Wire into `CaptureViewModel`/`CaptureUiState`/`CaptureScreen` — read the current pre-record state shape first, then stop and show the exact diff to the user before editing `CaptureScreen.kt`'s button, since this is UI-visible behavior change on a screen with a hard "cannot start without exposure lock" requirement already (CLAUDE.md: "Không cho bắt đầu khảo sát nếu chưa khoá exposure thật") that this gate must compose with, not replace.**

- [ ] **Step 6: Commit (after Step 5's review)**

```bash
git add app/src/main/java/com/luxmap/feature/survey/domain/GpsAccuracyGate.kt app/src/test/java/com/luxmap/feature/survey/domain/GpsAccuracyGateTest.kt app/src/main/java/com/luxmap/feature/survey/ui/capture/
git commit -m "feat(fm-39): gate recording start on live GPS accuracy"
```

---

## Explicitly out of scope for this plan

- **Network upload** (`RealUploadRepository`, Retrofit DTOs for `/api/v1/sweeps`) — separate plan, written after this one lands so the request bodies can be built from already-correct local file output, and because BE marked the API SELF-SIGNED pending a firmware meeting.
- **`clips[]` PTS-mapping block** in `capture_config.json` (Task 4's note) — needs ffprobe-equivalent PTS extraction that does not exist in the app; P2a accepts the config without it.
- **BLE connection-interval tuning** and **batched-packet lux parsing** — flagged in `docs/contract-drift.md` as likely BE/leader assumptions about a generic BLE module that do not match the real classic-SPP `LuxMap_ESP32`; needs a reply to the leader, not code.
- **Task 7's two sub-decisions** (focus-at-infinity vs. autofocus-converge-then-lock; where AWB lock belongs) are intentionally left as an open design question in this plan, not pre-decided.
