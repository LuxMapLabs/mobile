# Survey feature (F03/F04/F06) UI completion — design spec

Date: 2026-10-01
Status: approved in chat brainstorming, written up for record and handoff to writing-plans.

## 1. Context and purpose

F03 (`SurveyPlanScreen`), F04 (`CaptureScreen`), and F06 (`SubmitScreen`) currently exist only
as placeholder UI built to exercise the F03→F04→F06 capture pipeline (merged via PR #24-#26,
see `docs/superpowers/plans/2026-09-28-survey-capture-implementation.md` and
`2026-09-29-capture-viewfinder-implementation.md`). They use plain Material3 `Text`/`Button` with
no design tokens beyond `Spacing`, no Dark Mode enforcement, and none of the 3 shared components
CLAUDE.md's folder structure already names (`CameraReadinessPanel`, `GpsAccuracyIndicator`,
`SyncIndicator`) exist yet.

This spec covers finishing the real UI for these 3 screens: correct design tokens, Dark Mode
per context, the missing shared components, and the live state data each screen's UI actually
needs to show per `LuxMap_Mobile_DacTaChiTiet_v2.2.docx` (F03/F04/F06 sections) and
`LuxMap_Mobile_Design_System_v2.0.md` (§6.6-6.12, §8 traceability table).

**Explicitly out of scope:**
- F05 (Kiểm tra độ phủ & chất lượng) — its spec text is self-contradictory after the
  2026-09-25/27 video-only capture decision (see `project_f05_coverage_spec_stale` memory and
  CLAUDE.md's "Khoảng trống đã biết"). Not touched here.
- F13 (Hàng đợi đồng bộ) / `core/sync` (WorkManager, `sync_queue`) — does not exist yet (0 files).
  F06's "Đưa vào hàng đợi, nộp khi có mạng" button is deferred until F13 exists; only the
  "Nộp ngay" foreground-upload path is built in this round.
- F03's read-only map of the assigned route (part of the spec's "Thành phần giao diện" for F03)
  — deferred; list + Camera Readiness Panel only this round. `core/map/MapLibreConfig.kt` and
  `RoadSegmentLine` (built for F12) are the reuse path when this is picked up later.

## 2. Approach

Screen-by-screen, in order **F03 → F04 → F06** (simplest/foundation-setting first, then the
most complex/critical-path screen, then submit). Each screen is split into 3 sub-steps, matching
CLAUDE.md's "làm theo từng bước nhỏ" rule and the 400-line/commit limit:

1. Extend ViewModel/UiState/UseCase with whatever live data the UI needs, with unit tests first.
2. Build/extract the screen's master component(s) into `core/ui/components` (even when only one
   screen uses it in this round, since Design System v2.0 names these as official Master
   Components, not incidental extractions).
3. Wire the screen's Composable to the new state + components + correct Dark Mode + design tokens.

Components that are **not** extracted to `core/ui/components` (kept screen-local, YAGNI — no
second consumer yet): the F04 camera-overlay layout composition itself (only the atomic
indicators are reusable, not their arrangement — F09 may need a different arrangement later),
and F06's Upload Summary/Progress/Network Constraint pieces (Design System v2.0 does not list
these as detailed Master Components the way it does Sync Indicator for F13).

## 3. Shared infrastructure

**Dark Mode:** `SurveyPlanScreen` and `CaptureScreen` each wrap their own content in
`LuxMapTheme(forceDark = true) { ... }` — the parameter already exists in `Theme.kt` with a
comment naming exactly this use case, just never called. `SubmitScreen` stays unwrapped (follows
system theme, per CLAUDE.md's Dark Mode context table — F06 is not in the forced-dark list).

**New files in `core/ui/components/`:**
- `CameraReadinessPanel.kt` — one row per check: label, status (`Checking`/`Pass`/`Fail` —
  rendered with existing `Success600`/`Danger600`/`Gray500` text colors, no new color tokens),
  optional fix hint shown only on `Fail`.
- `GpsAccuracyIndicator.kt` — 3-state chip per Design System §6.9: "GPS tốt · ±Xm" /
  "GPS yếu · ±Xm" / "Không có vị trí hợp lệ". Thresholds (not specified in the design doc, which
  defers them to "đặc tả kỹ thuật cấu hình"): ≤10m = tốt, 10–30m = yếu, >30m or no fix = không
  đạt. These are this spec's own proposed defaults, confirmed with the project owner in this
  brainstorming session, not pulled from an existing document — revisit if a different number is
  ever given.

## 4. F03 — Tuyến khảo sát được giao

**Naming fix:** the spec document's own section title, "F03 · Lập kế hoạch tuyến khảo sát", is
stale — its own "Mục đích" text says Field Engineer does NOT plan, create, or choose routes
(updated 2026-09-27), only views what the Manager assigned and checks device readiness. The F03
code/traceability name stays for doc cross-reference, but the on-screen title string shown to the
user must not say "Lập kế hoạch". Confirmed with the project owner: use **"Tuyến khảo sát được
giao"**.

**Scope:** route list + Camera Readiness Panel + Dark Mode + all 4 `SurveyPlanUiState` states.
No map this round (see §1 out-of-scope) — record as a drift item against the spec's "Thành phần
giao diện" list for F03 when this work starts.

**State/ViewModel:**
- `SurveyReadinessInput`/`SurveyReadinessResult` gain `headingAvailable: Boolean`, sourced from
  `HeadingSensor` checking whether `sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)`
  is non-null. Included in `isReady`. This resolves one of the two options `contract-drift.md`
  already lists for the confirmed real bug (rotation_vector sensor missing on the test device,
  producing a silently-empty `heading_log.ndjson`) — confirmed with the project owner in this
  session as the chosen resolution, rather than a minimum-hardware-spec decision.
- `CheckSurveyReadinessUseCase` and its existing test gain a `headingAvailable = false` case.

**UI:**
- Screen title: "Tuyến khảo sát được giao" (see naming fix above).
- Wrap content in `LuxMapTheme(forceDark = true)`.
- `SurveyPlanUiState.Empty`: clear instructional copy ("Chưa có tuyến nào được phân công — liên
  hệ Kỹ sư bảo trì") instead of bare text.
- `SurveyPlanUiState.Error`: add a Retry action (Design System §6.12 requires Error states to
  have Retry; current code has none).
- `SurveyRouteCard`: switch from `MaterialTheme.typography.titleMedium`/`bodyMedium` defaults to
  the project's typography scale (H2 17sp/600 for the route name, Caption 14sp for metadata).
- Replace the current inline `ReadinessChecklist`/`ReadinessRow` (plain "✓"/"✗" text) with the new
  `CameraReadinessPanel` component.
- "Vào chế độ khảo sát" keeps its existing enable/disable logic (`readiness.isReady &&
  hasBlePermissions`), restyled to the existing `PrimaryButton`.

## 5. F04 — Chế độ chụp khảo sát (Capture Mode)

The name already matches the business flow correctly — no naming issue here.

**Scope:** full Camera Overlay per Design System §6.8 — live GPS/heading/lux/duration/distance/
storage indicators, proper warning treatment (color + text + icon + haptic, not just colored
text), larger stop button.

**State — `CaptureUiState.Recording` grows from `{gpsSignalLost, bleGapDetected}` to:**
```kotlin
data class Recording(
    val gpsSignalLost: Boolean = false,
    val bleGapDetected: Boolean = false,
    val durationSeconds: Long = 0,
    val distanceMeters: Float = 0f,
    val gpsAccuracyMeters: Float? = null,
    val headingDeg: Float? = null,
    val latestLuxValue: Float? = null,
    val freeStorageBytes: Long? = null,
) : CaptureUiState
```

Data sources (checked against current code, not assumed):
- `latestLuxValue` — already available: `LuxSensorBleClient.samples` (`SharedFlow<LuxSample>`)
  exists today; `CaptureViewModel` just needs to collect it into `Recording`.
- `durationSeconds` — new: a 1-second ticker in `CaptureViewModel`, started on entering
  `Recording`, cancelled on leaving it.
- `distanceMeters` — new: `SurveyTrackRecorder` receives every `TrackPoint` in
  `onLocationUpdate()` already but does not accumulate distance. Add a running total (via
  `Location.distanceTo()` between consecutive fixes) exposed as a new `StateFlow<Float>`, threaded
  up through `SurveyCaptureController` the same way `gpsSignalState` already is.
- `gpsAccuracyMeters`/`headingDeg` — new: `LocationHeadingRecorder` already computes
  `TrackPoint.accuracyM`/`gpsBearingDeg` per fix but only uses them to write the GPS log. Expose a
  new `StateFlow<TrackPoint?>`, threaded up the same way.
- `freeStorageBytes` — new: periodic `StatFs` poll (piggybacked on the duration ticker), reusing
  the same calculation `SurveyReadinessInputProvider` (F03) already has rather than duplicating it.
- Exposure-lock icon — no new state: always shown as a static "locked" icon in `Recording`,
  since locking is a hard gate before this state is ever reached.

**UI:**
- Keep the existing full-screen `TextureView` preview.
- New thin overlay strip at the top (replaces a full Top Bar per Design System §5.1): GPS
  Accuracy Indicator + heading, BLE connection status + latest lux value, free storage remaining.
- Existing bottom scrim panel: add REC indicator + `durationSeconds` as mm:ss, distance traveled
  in km, and enlarge "Dừng quay" to ≥64dp (matches the §6.8 "Shutter ≥64dp" sizing rule; current
  button is the smaller default `Button` size).
- GPS-lost / BLE-gap warnings: add an icon next to the existing red text, and trigger haptic
  feedback once when each flag transitions false→true (not continuously) — current code has text
  color only, missing the icon + haptic the spec requires.

## 6. F06 — Nộp đợt khảo sát

**Scope:** "Nộp ngay" path only this round (Upload Summary, byte progress, network indicator,
pause/cancel). The "Đưa vào hàng đợi, nộp khi có mạng" button is hidden (not built as a
non-functional placeholder) until F13/`core/sync` exists — confirmed with the project owner.
The Wifi-only vs. mobile-data toggle is also not built (its storage belongs to F15/F26, which
does not have it yet either); F06 only shows a passive current-network indicator.

**State:**
```kotlin
data class SessionSummary(val durationSeconds: Long, val distanceMeters: Float, val totalBytes: Long)
enum class NetworkType { WIFI, MOBILE, OFFLINE }

sealed interface SubmitUiState {
    data class Idle(val summary: SessionSummary, val networkType: NetworkType) : SubmitUiState
    data class Uploading(
        val summary: SessionSummary,
        val bytesSent: Long,
        val totalBytes: Long,
        val networkType: NetworkType,
    ) : SubmitUiState
    data object Done : SubmitUiState
    data class Error(val message: String, val bytesSent: Long, val totalBytes: Long) : SubmitUiState
}
```
`SessionSummary` is read from the session package's `manifest.json`, already written by
`PackageSurveySessionUseCase` — no new data needs to be recorded during capture. Duration/distance
replace "frame count" in the summary (frames no longer exist per C6).

**`UploadRepository` gains pause/resume support:** `uploadSession()` is currently a cold `Flow`
with no cancel/resume contract. `SubmitViewModel` holds the collecting `Job`; "Tạm dừng" cancels
it while keeping the last `bytesSent` on screen; "Tiếp tục" calls `uploadSession()` again.
`FakeUploadRepository` must simulate resuming from the prior progress (not restarting from 0) to
match the project's core "upload must resume, never restart from scratch" rule.

**UI:** `Idle` shows the summary + network indicator + "Nộp ngay". `Uploading` adds "Tạm dừng".
Paused shows "Tiếp tục"/"Huỷ". `Error` shows "Thử lại" that resumes from `bytesSent`, not a
fresh "Nộp ngay".

## 7. Testing

- Unit tests (JUnit+MockK) for every new/changed piece of logic: `CheckSurveyReadinessUseCase`
  (`headingAvailable` case), `SurveyTrackRecorder` (cumulative distance), `CaptureViewModel`
  (lux/GPS-live/duration wiring), `SubmitViewModel` (pause/resume keeps `bytesSent`).
- Compose UI test for F03's 4 states (already required by spec §16, not yet written).
- No automated test for the real Camera2/TextureView pieces touched in F04 — same established
  limit as the rest of FM-08 (no Robolectric in this project's test stack).
- `ktlintCheck` after each step, per CLAUDE.md.

## 8. Branch/commit breakdown

One branch per screen, off `dev`, named after the closest matching `FM-XX` in
`docs/LuxMap_TaskList_v2.xlsx`'s Frontend-Mobile sheet (noting that sheet predates the 2026-09-25
business-flow change and its FM-08/09/30 split does not map 1:1 to this spec's screen split — used
here only for branch-naming traceability, not as a scope source):

- `feat/fm-09-survey-plan-ui` (F03): headingAvailable + test → `CameraReadinessPanel`/
  `GpsAccuracyIndicator` → screen wiring + Dark Mode + title.
- `feat/fm-30-capture-mode-ui` (F04, heaviest — split further): distance in
  `SurveyTrackRecorder` → live GPS accuracy/heading from `LocationHeadingRecorder` → lux/
  duration/storage into `CaptureViewModel`/`CaptureUiState` → overlay UI redesign (indicator
  placement, enlarged stop button, haptic warnings).
- `feat/fm-12-submit-screen-ui` (F06): `SessionSummary` from manifest → pause/resume in
  `UploadRepository`/`FakeUploadRepository` → screen wiring.

Each commit stays ≤400 changed lines per CLAUDE.md; split further within a branch if a step
estimates over that.
