# Survey session upload (F06) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make F06 (Nộp đợt khảo sát) actually upload a recorded survey session to the real backend — `RealUploadRepository` replacing `FakeUploadRepository`, wired through a real `work_order_id` the app currently has no way to obtain.

**Architecture:** A `work_order_id` is threaded from `WorkOrderDetailScreen` (task_kind=survey) down into the local `LocalSurveySessionEntity` at recording time. Upload goes through 4 new `SyncOpHandler`s chained sequentially in the existing `sync_queue` (not fanned out — `dependsOnClientOpId` only supports one parent), with a minimal backward-compatible progress-reporting extension so `SubmitScreen` gets a live byte progress bar while the chain still survives app kill via the existing `SyncWorker`.

**Tech Stack:** Kotlin, Retrofit + kotlinx.serialization, Room, Hilt, JUnit + MockK, MockWebServer — nothing outside what's already in the project.

**Spec:** `docs/superpowers/specs/2026-10-08-survey-upload-design.md` — read it first; this plan does not repeat its reasoning, only its decisions.

## Global Constraints

- Commit size ≤ 400 changed lines (CLAUDE.md) — tasks with more than that are split into multiple commits below.
- Comments in new/changed code: English only, explain WHY not WHAT (CLAUDE.md).
- No new libraries beyond what's already in the project.
- Branch off `dev`, PR into `dev`. Never commit to `main`.
- `X-Content-SHA256` header is required only on the clip PUT, not the raw PUT (confirmed against `luxmap_backend/docs/openapi/luxmap-v1.5.json`, not guessed from `mobile.pdf` prose).
- Raw PUT content type depends on `kind`: `gps_track`/`lux_log` → `application/x-ndjson`, `capture_config` → `application/json`. Clip PUT → `video/mp4`.
- `SyncQueueEntity.dependsOnClientOpId` supports exactly one parent — the upload chain is sequential, never fanned out.
- `submitClientOpId` is generated once and persisted; every retry of `submit_survey_sweep` reuses the same value, never regenerates it.
- Error mapping (already established by `UploadWorkOrderEvidenceSyncHandler`, reused as-is): `409` → `SyncOpResult.Conflict`; `400/403/404/415` → `SyncOpResult.Failed`; `IOException`/`5xx` → `SyncOpResult.RetryLater`.
- Room migration 5→6 stays destructive (`fallbackToDestructiveMigration()`, already unconditional in `DatabaseModule.kt`) — no hand-written migration, same precedent as 4→5.
- "Resumable" means file-level resume (already-done clips/raw files are skipped on retry), not byte-range resume — the real API has no `Range` support.

## Review Focus

- A session whose `recordingState != "packaged"` must not be submittable from `SubmitScreen` — referenced files/checksums may not exist yet. Pinned in Task 10.
- `submitClientOpId` must survive a retry unchanged — a careless re-generate-on-every-call bug would silently break the server's idempotency check and risk a duplicate submit. Pinned in Task 8.
- The `onProgress` parameter added to `SyncOpHandler.handle()` must default to a no-op and not change `CompleteWorkOrderSyncHandler`/`UploadWorkOrderEvidenceSyncHandler` behavior. Pinned in Task 5.
- A `409` on clip/raw PUT must map to `Conflict`, not `Failed` or `RetryLater` — copy-pasting the generic error branch could silently violate the "never auto-overwrite on conflict" rule. Pinned in Task 7.
- `create_survey_sweep` retried after a partial chain failure (e.g., clip 2 of 3 failed) must not create a second sweep — it must reuse the already-persisted `serverSweepId` instead of calling `create()` again. Pinned in Task 6.

---

## File Structure

| File | Responsibility |
|---|---|
| `feature/survey/data/entity/LocalSurveySessionEntity.kt` | Modify: 6 new columns (`workOrderId`, `serverSweepId`, `submitClientOpId`, 3 raw-file checksums) |
| `core/database/AppDatabase.kt` | Modify: version 5→6 |
| `feature/survey/capture/SurveyCaptureController.kt` | Modify: `startSession()` gains `workOrderId` |
| `feature/survey/capture/SurveyCaptureService.kt` | Modify: `EXTRA_WORK_ORDER_ID`, threads into `insertSession` |
| `feature/survey/ui/capture/CaptureViewModel.kt` | Modify: `onStartRecording()` gains `workOrderId` |
| `feature/survey/ui/capture/CaptureScreen.kt` | Modify: new `workOrderId` param |
| `navigation/Routes.kt` | Modify: `SurveyCapture` route gains `workOrderId` segment |
| `navigation/NavGraph.kt` | Modify: extracts/passes `workOrderId`, wires `WorkOrderDetail`'s new survey-start event |
| `feature/workorder/ui/detail/WorkOrderDetailViewModel.kt` | Modify: emits a one-shot survey-start event after `start()` succeeds for `taskKind == "survey"` |
| `feature/workorder/ui/detail/WorkOrderDetailScreen.kt` | Modify: collects the event, calls new `onStartSurvey` nav callback |
| `feature/survey/capture/PackageSurveySessionUseCase.kt` | Modify: persists checksums instead of discarding them |
| `core/network/SweepsApi.kt` | Create: Retrofit interface for the 4 sweep endpoints |
| `core/network/dto/SweepDtos.kt` | Create: request/response DTOs |
| `core/sync/SyncOpHandler.kt` | Modify: `handle()` gains optional `onProgress` param |
| `core/sync/SyncQueueProcessor.kt` | Modify: `processQueuedOps()` passes progress through |
| `feature/survey/data/sync/SurveySweepSyncPayloads.kt` | Create: payload data classes for the 4 new op types |
| `feature/survey/data/sync/CreateSurveySweepSyncHandler.kt` | Create |
| `feature/survey/data/sync/UploadSurveyClipSyncHandler.kt` | Create |
| `feature/survey/data/sync/UploadSurveyRawSyncHandler.kt` | Create |
| `feature/survey/data/sync/SubmitSurveySweepSyncHandler.kt` | Create |
| `feature/survey/data/RealUploadRepository.kt` | Create |
| `feature/survey/data/UploadRepository.kt` | Modify: `UploadProgress` gains a `Conflict` case |
| `di/SyncModule.kt` | Modify: binds the 4 new handlers |
| `di/RepositoryModule.kt` | Modify: binds `RealUploadRepository` in place of `FakeUploadRepository` |
| `feature/survey/ui/submit/SubmitUiState.kt` | Modify: splits `Error` into `Failed`/`Conflict` |
| `feature/survey/ui/submit/SubmitViewModel.kt` | Modify: maps new `UploadProgress` cases, validates `recordingState == "packaged"` |
| `feature/survey/ui/submit/SubmitScreen.kt` | Modify: `StatusBadge`-based UI per state |

---

### Task 1: Add the 6 new Room columns

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt:212-233` (construction site — passes a literal placeholder for `workOrderId` for now; Task 2 wires the real value)
- Modify: `app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt:17-37`
- Modify: `app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt:18-38`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt:45-65`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt:27-47`

**Interfaces:**
- Produces: `LocalSurveySessionEntity` with `workOrderId: String` (required), `serverSweepId: String?`, `submitClientOpId: String?`, `gpsTrackChecksumSha256: String?`, `luxLogChecksumSha256: String?`, `captureConfigChecksumSha256: String?` — every later task reads/writes these exact names.

This task only touches the schema and every existing construction site — no new behavior yet. `SurveyCaptureService` gets a placeholder `workOrderId = ""` here on purpose (Task 2 threads the real value); this keeps Task 1 a pure, independently-compiling schema change.

- [ ] **Step 1: Add the 6 columns to the entity**

Edit `LocalSurveySessionEntity.kt` — insert `workOrderId` right after `surveySweepId`, and the 5 nullable columns right after `bleGapDetected`:

```kotlin
@Entity(tableName = "local_survey_session")
data class LocalSurveySessionEntity(
    @PrimaryKey val sessionId: String,
    val surveySweepId: String,
    // The work order (taskKind == "survey") this session belongs to - required because the real
    // upload flow needs it for POST /work-orders/{id}/start and POST /api/v1/sweeps. Added fm-40.
    val workOrderId: String,
    val recordingState: String,
    val syncState: String?,
    val startedAtUtc: Instant,
    val startedAtElapsedNs: Long,
    val endedAtUtc: Instant?,
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val gpsTrackFilePath: String?,
    val luxLogFilePath: String?,
    val frameTimestampLogFilePath: String?,
    val captureConfigFilePath: String?,
    val manifestFilePath: String?,
    val packageSchemaVersion: String,
    val timestampSourceRealtime: Boolean,
    val bleGapDetected: Boolean,
    // Null until "create_survey_sweep" succeeds; the real sweep_id the server assigned.
    val serverSweepId: String? = null,
    // Generated once on the first submit attempt, reused on every retry - regenerating it would
    // break the server's idempotency check on client_op_id (fm-40).
    val submitClientOpId: String? = null,
    val gpsTrackChecksumSha256: String? = null,
    val luxLogChecksumSha256: String? = null,
    val captureConfigChecksumSha256: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

The 5 new nullable columns have a default (`= null`) so existing positional/named constructions that don't mention them still compile; `workOrderId` has no default since it is required going forward.

- [ ] **Step 2: Bump the database version**

Edit `AppDatabase.kt:30`:

```kotlin
    version = 6,
```

- [ ] **Step 3: Fix the 4 existing test/androidTest construction sites**

In each of the 4 files below, add `workOrderId = "WO-1",` right after the existing `surveySweepId = "SWEEP-1",` line (the 5 new nullable columns need no change — they default to `null`):

`SurveySessionRecoveryUseCaseTest.kt:19`:
```kotlin
            surveySweepId = "SWEEP-1",
            workOrderId = "WO-1",
```

`PackageSurveySessionUseCaseTest.kt:20`:
```kotlin
            surveySweepId = "SWEEP-1",
            workOrderId = "WO-1",
```

`FakeSurveyRepositoryTest.kt:47`:
```kotlin
                    surveySweepId = "SWEEP-1",
                    workOrderId = "WO-1",
```

`SurveySessionDaoTest.kt:29`:
```kotlin
        surveySweepId = "SWEEP-1",
        workOrderId = "WO-1",
```

- [ ] **Step 4: Add the placeholder to `SurveyCaptureService`'s construction site**

Edit `SurveyCaptureService.kt:214` (right after `surveySweepId = surveySweepId,`):

```kotlin
                    surveySweepId = surveySweepId,
                    workOrderId = "", // Task 2 of this plan replaces this with the real threaded value
```

- [ ] **Step 5: Compile and run the existing survey test suite to confirm nothing else broke**

Run: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest --tests "com.luxmap.feature.survey.*"`
Expected: PASS (same tests as before, now against the new schema).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/entity/LocalSurveySessionEntity.kt \
        app/src/main/java/com/luxmap/core/database/AppDatabase.kt \
        app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt \
        app/src/test/java/com/luxmap/feature/survey/capture/SurveySessionRecoveryUseCaseTest.kt \
        app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt \
        app/src/androidTest/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt \
        app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt
git commit -m "feat(fm-40): add work_order_id and sweep upload columns to survey session"
```

---

### Task 2: Thread `workOrderId` from Work Order entry to the capture session

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt:41-46,152-157`
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt:161-233`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt:62-64,180-186,213-224`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt:48-53,280-283`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt:18-22`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt:153-173,223-231`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt:58-75`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt:180,281,309,335,364,394,422`

**Interfaces:**
- Consumes: `LocalSurveySessionEntity.workOrderId` (Task 1).
- Produces: `SurveyCaptureController.startSession(sessionId, surveySweepId, workOrderId, luxDeviceAddress, previewSurface)`; `CaptureViewModel.onStartRecording(surveySweepId, workOrderId)`; `Routes.SurveyCapture.createRoute(workOrderId, surveySweepId)`; `WorkOrderDetailViewModel.startedSurveySession: SharedFlow<SurveySessionStart>`.

No automated test for the Intent/Service/NavGraph wiring in this task — same reason the rest of `SurveyCaptureController`/`SurveyCaptureService` has none (needs a real Android `Intent`/`bindService`, throws "not mocked" under plain JVM unit tests, no Robolectric in this project). `CaptureViewModelTest` is updated only to keep compiling.

- [ ] **Step 1: `SurveyCaptureController` — add `workOrderId` to `startSession()`**

Edit the interface (`SurveyCaptureController.kt:41-46`):

```kotlin
    fun startSession(
        sessionId: String,
        surveySweepId: String,
        workOrderId: String,
        luxDeviceAddress: String,
        previewSurface: Surface,
    )
```

Edit the implementation (`SurveyCaptureController.kt:152-165`):

```kotlin
        override fun startSession(
            sessionId: String,
            surveySweepId: String,
            workOrderId: String,
            luxDeviceAddress: String,
            previewSurface: Surface,
        ) {
            val intent =
                Intent(context, SurveyCaptureService::class.java)
                    .setAction(SurveyCaptureService.ACTION_START)
                    .putExtra(SurveyCaptureService.EXTRA_SESSION_ID, sessionId)
                    .putExtra(SurveyCaptureService.EXTRA_SURVEY_SWEEP_ID, surveySweepId)
                    .putExtra(SurveyCaptureService.EXTRA_WORK_ORDER_ID, workOrderId)
                    .putExtra(SurveyCaptureService.EXTRA_LUX_DEVICE_ADDRESS, luxDeviceAddress)
                    .putExtra(SurveyCaptureService.EXTRA_PREVIEW_SURFACE, previewSurface)
```

- [ ] **Step 2: `SurveyCaptureService` — read the new extra and thread it into `insertSession`**

Edit `SurveyCaptureService.kt:168-179`:

```kotlin
                if (isRecording) {
                    Log.w(TAG, "ACTION_START arrived while a session is already recording; ignoring it")
                } else {
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                    val surveySweepId = intent.getStringExtra(EXTRA_SURVEY_SWEEP_ID) ?: return START_NOT_STICKY
                    val workOrderId = intent.getStringExtra(EXTRA_WORK_ORDER_ID) ?: return START_NOT_STICKY
                    val luxDeviceAddress = intent.getStringExtra(EXTRA_LUX_DEVICE_ADDRESS) ?: return START_NOT_STICKY
                    val previewSurface =
                        IntentCompat.getParcelableExtra(intent, EXTRA_PREVIEW_SURFACE, Surface::class.java)
                            ?: return START_NOT_STICKY
                    isRecording = true
                    startSessionInternal(sessionId, surveySweepId, workOrderId, luxDeviceAddress, previewSurface)
                }
```

Edit `startSessionInternal`'s signature and the `insertSession` call (`SurveyCaptureService.kt:187-214`):

```kotlin
    private fun startSessionInternal(
        sessionId: String,
        surveySweepId: String,
        workOrderId: String,
        luxDeviceAddress: String,
        previewSurface: Surface,
    ) {
```

...(body unchanged until the `insertSession` call)...

```kotlin
                    surveySweepId = surveySweepId,
                    workOrderId = workOrderId,
```

(replacing the `workOrderId = "",` placeholder Task 1 left behind.)

Add the new extra constant next to `EXTRA_SURVEY_SWEEP_ID` (`SurveyCaptureService.kt:556`):

```kotlin
        const val EXTRA_WORK_ORDER_ID = "work_order_id"
```

- [ ] **Step 3: `CaptureViewModel` — carry `workOrderId` from `onStartRecording` to `startSession`**

Edit `CaptureViewModel.kt:62-64` (add a field next to `pendingSurveySweepId`):

```kotlin
        // The surveySweepId passed to onStartRecording, held until the preview surface is ready
        // and startSession() can actually be called (Cach A - see the plan this came from).
        private var pendingSurveySweepId: String = ""
        private var pendingWorkOrderId: String = ""
```

Edit `onStartRecording` (`CaptureViewModel.kt:180-186`):

```kotlin
        fun onStartRecording(
            surveySweepId: String,
            workOrderId: String,
        ) {
            val current = _uiState.value
            if (current !is CaptureUiState.Ready || !current.gpsReadyToRecord) return
            stopPreRecordGpsTracking()
            pendingSurveySweepId = surveySweepId
            pendingWorkOrderId = workOrderId
            _uiState.value = CaptureUiState.StartingRecording
        }
```

Edit the `startSession` call inside `onPreviewSurfaceReady` (`CaptureViewModel.kt:220-223`):

```kotlin
                    val luxDeviceAddress = pendingDevice?.address ?: return
                    startingSessionRequested = true
                    sessionId = UUID.randomUUID().toString()
                    captureController.startSession(sessionId, pendingSurveySweepId, pendingWorkOrderId, luxDeviceAddress, surface)
```

- [ ] **Step 4: `CaptureScreen` — accept and forward `workOrderId`**

Edit the composable signature (`CaptureScreen.kt:48-53`):

```kotlin
@Composable
fun CaptureScreen(
    surveySweepId: String,
    workOrderId: String,
    onSessionPackaged: (sessionId: String) -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
```

Edit the "Bắt đầu quay" button (`CaptureScreen.kt:280-283`):

```kotlin
                        Button(
                            onClick = { viewModel.onStartRecording(surveySweepId, workOrderId) },
                            enabled = state.gpsReadyToRecord,
                        ) { Text("Bắt đầu quay") }
```

- [ ] **Step 5: `Routes.SurveyCapture` — add the `workOrderId` segment**

Edit `Routes.kt:18-22`:

```kotlin
    data object SurveyCapture : Routes {
        override val route = "survey/capture/{workOrderId}/{surveySweepId}"

        fun createRoute(
            workOrderId: String,
            surveySweepId: String,
        ) = "survey/capture/$workOrderId/$surveySweepId"
    }
```

- [ ] **Step 6: `NavGraph` — extract `workOrderId`, pass it through, wire the new survey-start event**

Edit the `SurveyCapture` composable destination (`NavGraph.kt:160-173`):

```kotlin
            composable(
                route = Routes.SurveyCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("surveySweepId") { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                val surveySweepId = backStackEntry.arguments?.getString("surveySweepId") ?: return@composable
                CaptureScreen(
                    surveySweepId = surveySweepId,
                    workOrderId = workOrderId,
                    onSessionPackaged = { sessionId ->
                        navController.navigate(Routes.SurveyReview.createRoute(sessionId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
                )
            }
```

The `onRedo` callback inside the `SurveyReview` destination (`NavGraph.kt:186-190`) also calls `Routes.SurveyCapture.createRoute(surveySweepId)` with the old 1-arg signature — it does not have a `workOrderId` in scope at that point. Fix it by reading the same session's `workOrderId` is out of scope for this plan (F05/review redo flow is untouched per the spec's non-goals); instead, thread a `workOrderId` parameter through `CoverageScreen`'s `onRedo` the same shallow way — but since that would expand this task's scope into F05, leave the compiler error as a signal and resolve it minimally: redo re-enters the SAME session's capture, so reuse the `workOrderId` already known on that screen's current survey sweep. Concretely, change `onRedo` to also receive `workOrderId: String` from `CoverageScreen` (which itself does not have one today - it only has `sessionId`). **Stop here and treat this as a real blocker for this step**, not a style choice:

- [ ] **Step 6a: Resolve the `onRedo` gap minimally**

`CoverageScreen`'s `onRedo: (surveySweepId: String) -> Unit` has no `workOrderId` to forward, and `NavGraph` has no session lookup at that call site either — threading a real value through would mean extending `CoverageScreen`'s own callback shape, which is out of scope here (F05 review flow, see the design spec's non-goals). Pass an empty string instead of silently guessing a real value, and document why with a comment so the gap is visible at the call site, not just in a doc nobody reads before touching this code:

```kotlin
                    // "Redo" re-enters capture for a session that already exists in Room with a
                    // real workOrderId - CoverageScreen only passes surveySweepId today, so this
                    // path cannot forward the real value without changing CoverageScreen's own
                    // callback shape, which is out of scope here (F05 review flow, see the design
                    // spec's non-goals). SurveyCaptureService only uses workOrderId at session
                    // CREATION (Task 1/2 of this plan) - redo starts a brand new local session via
                    // CaptureViewModel, so this empty value is a real gap, not a cosmetic one: a
                    // session started via "redo" will not be upload-able until this is fixed.
                    // Tracked in docs/contract-drift.md, not silently left unflagged.
                    onRedo = { surveySweepId ->
                        navController.navigate(Routes.SurveyCapture.createRoute(workOrderId = "", surveySweepId = surveySweepId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
```

Add a row to `docs/contract-drift.md` right after writing this: "`onRedo` survey capture re-entry has no `workOrderId`" / "Known gap (fm-40) — a session re-recorded via F05's 'Quay lại' loses its work order linkage, cannot be uploaded until `CoverageScreen`'s `onRedo` callback is extended to carry `workOrderId` too" / owner WP6.

- [ ] **Step 7: `WorkOrderDetailViewModel` — emit a one-shot event after a successful survey start**

Edit `WorkOrderDetailViewModel.kt` — add the event infrastructure and emit it from `start()`:

```kotlin
package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class SurveySessionStart(val workOrderId: String, val surveySweepId: String)

@HiltViewModel
class WorkOrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: WorkOrderDetailRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderDetailUiState>(WorkOrderDetailUiState.Loading)
        val uiState: StateFlow<WorkOrderDetailUiState> = _uiState.asStateFlow()

        // One-shot: a survey work order's "Bắt đầu" should navigate into F04 exactly once per tap,
        // not replay on every recomposition the way a StateFlow would.
        private val _startedSurveySession = MutableSharedFlow<SurveySessionStart>(extraBufferCapacity = 1)
        val startedSurveySession: SharedFlow<SurveySessionStart> = _startedSurveySession.asSharedFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = WorkOrderDetailUiState.Loading
                repository
                    .observeWorkOrderDetail(workOrderId)
                    .catch { e ->
                        _uiState.value = WorkOrderDetailUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                    }.collect { detail ->
                        _uiState.value =
                            if (detail == null) {
                                WorkOrderDetailUiState.Empty
                            } else {
                                WorkOrderDetailUiState.Success(detail = detail)
                            }
                    }
            }
        }

        fun start() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || current.isStarting) return
            _uiState.value = current.copy(isStarting = true, startError = null)
            viewModelScope.launch {
                repository.start(workOrderId).fold(
                    onSuccess = { detail ->
                        _uiState.value = WorkOrderDetailUiState.Success(detail = detail)
                        if (detail.taskKind == "survey") {
                            _startedSurveySession.tryEmit(
                                SurveySessionStart(workOrderId = workOrderId, surveySweepId = UUID.randomUUID().toString()),
                            )
                        }
                    },
                    onFailure = { e ->
                        val afterFailure = _uiState.value
                        if (afterFailure is WorkOrderDetailUiState.Success) {
                            _uiState.value =
                                afterFailure.copy(
                                    isStarting = false,
                                    startError = startErrorMessage(e),
                                )
                        }
                    },
                )
            }
        }

        private fun startErrorMessage(e: Throwable): String =
            if (e is IOException) "Mất kết nối. Vui lòng thử lại." else "Không bắt đầu được lệnh này"

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
```

- [ ] **Step 8: `WorkOrderDetailScreen`/`Route` — collect the event, add `onStartSurvey`**

Edit `WorkOrderDetailRoute` (`WorkOrderDetailScreen.kt:58-75`):

```kotlin
@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onStartSurvey: (workOrderId: String, surveySweepId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startedSurveySession.collect { event ->
            onStartSurvey(event.workOrderId, event.surveySweepId)
        }
    }

    WorkOrderDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onStart = viewModel::start,
        onNavigateFault = { lat, lng -> openInMaps(context, lat, lng) },
        onComplete = onComplete,
        modifier = modifier,
    )
}
```

Add the `LaunchedEffect` import at the top of the file:

```kotlin
import androidx.compose.runtime.LaunchedEffect
```

- [ ] **Step 9: `NavGraph` — wire `onStartSurvey`**

Edit the `WorkOrderDetail` composable (`NavGraph.kt:223-231`):

```kotlin
            composable(
                route = Routes.WorkOrderDetail.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                WorkOrderDetailRoute(
                    onBack = { navController.popBackStack() },
                    onComplete = { navController.navigate(Routes.WorkOrderCompletion.createRoute(workOrderId)) },
                    onStartSurvey = { startedWorkOrderId, surveySweepId ->
                        navController.navigate(Routes.SurveyCapture.createRoute(startedWorkOrderId, surveySweepId))
                    },
                )
            }
```

- [ ] **Step 10: Fix the 7 `CaptureViewModelTest` call sites**

In `CaptureViewModelTest.kt`, update each `onStartRecording` call to pass a `workOrderId` too:

Line 180: `viewModel.onStartRecording(surveySweepId = "SWEEP-1", workOrderId = "WO-1")`
Line 281: `viewModel.onStartRecording(surveySweepId = "SWEEP-1", workOrderId = "WO-1")`
Line 309: `viewModel.onStartRecording(surveySweepId = "SWEEP-1", workOrderId = "WO-1")`
Line 335: `viewModel.onStartRecording(surveySweepId = "SWEEP-1", workOrderId = "WO-1")`
Line 364: `viewModel.onStartRecording(surveySweepId = "SWEEP-1", workOrderId = "WO-1")`
Line 394: `viewModel.onStartRecording(surveySweepId = "SWEEP-1", workOrderId = "WO-1")`
Line 422: `viewModel.onStartRecording("SWEEP-1", "WO-1")`

- [ ] **Step 11: Compile**

Run: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin`
Expected: PASS. (Do not run the full `CaptureViewModelTest` suite — per `docs/contract-drift.md`, 5 of its 9 tests hang on a pre-existing, unrelated issue; this step only needs to confirm compilation, not a green run. See the note in Step 12.)

- [ ] **Step 12: Note the pre-existing test hang, don't try to fix it here**

`CaptureViewModelTest` has a documented pre-existing hang on 5 of 9 tests (any test reaching `CaptureUiState.Recording`), confirmed in `docs/contract-drift.md` as unrelated to any specific change. If running this file hangs, that is the known issue, not something this task introduced — do not spend time debugging it here.

- [ ] **Step 13: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt \
        app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt \
        app/src/main/java/com/luxmap/navigation/Routes.kt \
        app/src/main/java/com/luxmap/navigation/NavGraph.kt \
        app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt \
        app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt \
        app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt \
        docs/contract-drift.md
git commit -m "feat(fm-40): thread work_order_id from work order start into capture session"
```

---

### Task 3: Persist checksums instead of discarding them

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt`

**Interfaces:**
- Consumes: `LocalSurveyVideoSegmentEntity.checksumSha256` (existing field, Task 1 untouched), `LocalSurveySessionEntity.{gpsTrackChecksumSha256,luxLogChecksumSha256,captureConfigChecksumSha256}` (Task 1).
- Produces: after `PackageSurveySessionUseCase.invoke()` succeeds, every video segment row and the session's 3 raw-file checksum columns are populated — later tasks (6, 7) read them instead of recomputing SHA-256 on files up to 300 MiB on every upload attempt.

- [ ] **Step 1: Write the failing test**

Add to `PackageSurveySessionUseCaseTest.kt`:

```kotlin
    @Test
    fun `persists the sha256 checksum onto the session row for each raw file, computed once`() =
        runTest {
            val tempDir = createTempDir()
            val session = sessionWithFiles(tempDir)
            File(session.luxLogFilePath!!).let { it.parentFile?.mkdirs(); it.writeText("{}\n") }
                .takeIf { false } // no-op guard, luxLogFilePath is null in sessionWithFiles() by default
            val sessionWithRaw =
                session.copy(
                    luxLogFilePath = File(tempDir, "lux_log.ndjson").apply { writeText("lux\n") }.absolutePath,
                    captureConfigFilePath = File(tempDir, "capture_config.json").apply { writeText("{}") }.absolutePath,
                )
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns sessionWithRaw
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val useCase = PackageSurveySessionUseCase(dao)

            useCase.invoke("SESSION-1")

            coVerify {
                dao.updateSession(
                    match {
                        it.gpsTrackChecksumSha256 != null &&
                            it.luxLogChecksumSha256 != null &&
                            it.captureConfigChecksumSha256 != null
                    },
                )
            }
        }

    @Test
    fun `persists the sha256 checksum onto each video segment row`() =
        runTest {
            val tempDir = createTempDir()
            val session = sessionWithFiles(tempDir)
            val clip = File(tempDir, "clip_0.mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = clip.absolutePath,
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 3L,
                        checksumSha256 = null,
                    ),
                )
            val useCase = PackageSurveySessionUseCase(dao)

            useCase.invoke("SESSION-1")

            coVerify { dao.updateSegment(match { it.segmentId == "SEG-1" && it.checksumSha256 != null }) }
        }
```

Add the needed import at the top of the test file:

```kotlin
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.capture.PackageSurveySessionUseCaseTest"`
Expected: FAIL — both new tests fail because `dao.updateSession`/`dao.updateSegment` are never called with a non-null checksum today.

- [ ] **Step 3: Persist the checksums**

Edit `PackageSurveySessionUseCase.kt` — replace the `buildManifestJson` call site and the final `dao.updateSession` to also persist checksums, and add segment-checksum persistence. Checksums are keyed by file **path**, not role — every video segment shares the role `"video_segment"`, so a role-keyed map would return the same (first) segment's checksum for every segment:

```kotlin
        suspend fun invoke(sessionId: String): PackageResult {
            val session = dao.sessionById(sessionId) ?: return PackageResult.Failure("Session $sessionId not found")
            val segments = dao.segmentsFor(sessionId)

            val referencedFiles =
                listOfNotNull(
                    session.gpsTrackFilePath?.let { Triple(it, "gps_track", null) },
                    session.luxLogFilePath?.let { Triple(it, "lux_log", null) },
                    session.frameTimestampLogFilePath?.let { Triple(it, "frame_timestamp_log", null) },
                    session.captureConfigFilePath?.let { Triple(it, "capture_config", null) },
                ) + segments.map { Triple(it.filePath, "video_segment", it.segmentIndex) }

            if (referencedFiles.isEmpty()) {
                return PackageResult.Failure("Session $sessionId has no referenced files to package")
            }

            val missing = referencedFiles.filterNot { (path, _, _) -> File(path).exists() }
            if (missing.isNotEmpty()) {
                return PackageResult.Failure("Missing file(s) at packaging time: ${missing.map { it.first }}")
            }

            referencedFiles.forEach { (path, _, _) -> cleanIfNdjson(File(path)) }

            // Computed once here, not re-hashed on every upload attempt later - clips can be up to
            // 300 MiB, so hashing them again on every retry would be wasteful (fm-40). Keyed by
            // path, not role, because multiple video segments all share the "video_segment" role.
            val checksumsByPath = referencedFiles.associate { (path, _, _) -> path to sha256Of(File(path)) }

            segments.forEach { segment ->
                dao.updateSegment(segment.copy(checksumSha256 = checksumsByPath.getValue(segment.filePath)))
            }

            val manifestFile = File(File(referencedFiles.first().first).parentFile, "manifest.json")
            manifestFile.writeText(
                buildManifestJson(
                    sessionId = session.sessionId,
                    surveySweepId = session.surveySweepId,
                    startedAtUtc = session.startedAtUtc.toString(),
                    endedAtUtc = session.endedAtUtc?.toString(),
                    files = referencedFiles,
                    checksumsByPath = checksumsByPath,
                ),
            )

            dao.updateSession(
                session.copy(
                    recordingState = "packaged",
                    manifestFilePath = manifestFile.absolutePath,
                    gpsTrackChecksumSha256 = session.gpsTrackFilePath?.let { checksumsByPath[it] },
                    luxLogChecksumSha256 = session.luxLogFilePath?.let { checksumsByPath[it] },
                    captureConfigChecksumSha256 = session.captureConfigFilePath?.let { checksumsByPath[it] },
                    updatedAt = Instant.now(),
                ),
            )
            return PackageResult.Success(manifestFile.absolutePath)
        }
```

Edit `buildManifestJson` to take and reuse `checksumsByPath` instead of calling `sha256Of` again per file:

```kotlin
        private fun buildManifestJson(
            sessionId: String,
            surveySweepId: String,
            startedAtUtc: String,
            endedAtUtc: String?,
            files: List<Triple<String, String, Int?>>,
            checksumsByPath: Map<String, String>,
        ): String {
            val filesJson =
                files.joinToString(",") { (path, role, segmentIndex) ->
                    val file = File(path)
                    val checksum = checksumsByPath.getValue(path)
                    val segmentField = if (segmentIndex != null) ""","segment_index":$segmentIndex""" else ""
                    """{"name":"${file.name}","role":"$role"$segmentField,""" +
                        """"checksum_sha256":"$checksum","size_bytes":${file.length()}}"""
                }
            val endedAtField = if (endedAtUtc != null) """"ended_at_utc":"$endedAtUtc"""" else """"ended_at_utc":null"""
            return """{"schema_version":"v1","session_id":"$sessionId","survey_sweep_id":"$surveySweepId",""" +
                """"started_at_utc":"$startedAtUtc",$endedAtField,"files":[$filesJson]}"""
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.capture.PackageSurveySessionUseCaseTest"`
Expected: PASS, all 6 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCase.kt \
        app/src/test/java/com/luxmap/feature/survey/capture/PackageSurveySessionUseCaseTest.kt
git commit -m "fix(fm-40): persist sha256 checksums instead of discarding them after packaging"
```

---

### Task 4: `SweepsApi` + DTOs

**Files:**
- Create: `app/src/main/java/com/luxmap/core/network/SweepsApi.kt`
- Create: `app/src/main/java/com/luxmap/core/network/dto/SweepDtos.kt`
- Test: `app/src/test/java/com/luxmap/core/network/SweepsApiTest.kt` (new, MockWebServer)

**Interfaces:**
- Produces: `SweepsApi.create/uploadClip/uploadRaw/submit`, `CreateSweepRequestDto`, `SweepResponseDto`, `SubmitSweepRequestDto`, `SweepManifestDto`, `ClipManifestDto`, `SurveyClipResponseDto`, `SurveyRawResponseDto` — Tasks 6, 7, 8 call these by exact name.

Field names below come directly from `luxmap_backend/docs/openapi/luxmap-v1.5.json` (`CreateSweepRequest`, `SweepResponse`, `SubmitSweepRequest`, `SweepManifest`, `ClipManifest`, `SurveyClipResponse`, `SurveyRawResponse`), not guessed.

- [ ] **Step 1: Write the DTOs**

```kotlin
// app/src/main/java/com/luxmap/core/network/dto/SweepDtos.kt
package com.luxmap.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Checked directly against luxmap_backend/docs/openapi/luxmap-v1.5.json schemas
// CreateSweepRequest/SweepResponse/SubmitSweepRequest/SweepManifest/ClipManifest/
// SurveyClipResponse/SurveyRawResponse - field names are NOT guessed.
@Serializable
data class CreateSweepRequestDto(
    @SerialName("work_order_id") val workOrderId: String,
    @SerialName("client_op_id") val clientOpId: String,
    @SerialName("boot_session_id") val bootSessionId: String,
    @SerialName("elapsed_anchor_ns") val elapsedAnchorNs: String,
    @SerialName("utc_anchor") val utcAnchor: String,
    @SerialName("utc_uncertainty_ms") val utcUncertaintyMs: Double,
    @SerialName("data_source") val dataSource: String,
    @SerialName("started_elapsed_ns") val startedElapsedNs: String,
)

@Serializable
data class SweepResponseDto(
    @SerialName("sweep_id") val sweepId: String,
)

@Serializable
data class ClipManifestDto(
    @SerialName("clip_no") val clipNo: Int,
    val sha256: String,
)

@Serializable
data class SweepManifestDto(
    val clips: List<ClipManifestDto>,
    @SerialName("gps_hash") val gpsHash: String,
    @SerialName("lux_hash") val luxHash: String,
    @SerialName("config_hash") val configHash: String,
)

@Serializable
data class SubmitSweepRequestDto(
    @SerialName("client_op_id") val clientOpId: String,
    @SerialName("ended_elapsed_ns") val endedElapsedNs: String,
    val manifest: SweepManifestDto,
)

@Serializable
data class SurveyClipResponseDto(
    @SerialName("clip_no") val clipNo: Int,
    val sha256: String,
)

@Serializable
data class SurveyRawResponseDto(
    val kind: String,
    val sha256: String,
)
```

- [ ] **Step 2: Write `SweepsApi`**

```kotlin
// app/src/main/java/com/luxmap/core/network/SweepsApi.kt
package com.luxmap.core.network

import com.luxmap.core.network.dto.CreateSweepRequestDto
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SurveyClipResponseDto
import com.luxmap.core.network.dto.SurveyRawResponseDto
import com.luxmap.core.network.dto.SweepResponseDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Route checked against luxmap_backend/docs/openapi/luxmap-v1.5.json paths /api/v1/sweeps,
// /api/v1/sweeps/{id}/clips/{clipNo}, /api/v1/sweeps/{id}/raw/{kind}, /api/v1/sweeps/{id}/submit.
interface SweepsApi {
    @POST("api/v1/sweeps")
    suspend fun create(
        @Body body: CreateSweepRequestDto,
    ): SweepResponseDto

    // X-Content-SHA256 is declared only on this endpoint in the openapi spec, not on uploadRaw -
    // do not add it there without re-checking the spec first.
    @PUT("api/v1/sweeps/{id}/clips/{clipNo}")
    suspend fun uploadClip(
        @Path("id") sweepId: String,
        @Path("clipNo") clipNo: Int,
        @Header("X-Content-SHA256") sha256: String,
        @Body body: RequestBody,
    ): SurveyClipResponseDto

    @PUT("api/v1/sweeps/{id}/raw/{kind}")
    suspend fun uploadRaw(
        @Path("id") sweepId: String,
        @Path("kind") kind: String,
        @Body body: RequestBody,
    ): SurveyRawResponseDto

    @POST("api/v1/sweeps/{id}/submit")
    suspend fun submit(
        @Path("id") sweepId: String,
        @Body body: SubmitSweepRequestDto,
    ): SweepResponseDto
}
```

- [ ] **Step 3: Write a MockWebServer test pinning the request shape**

```kotlin
// app/src/test/java/com/luxmap/core/network/SweepsApiTest.kt
package com.luxmap.core.network

import com.luxmap.core.network.dto.ClipManifestDto
import com.luxmap.core.network.dto.CreateSweepRequestDto
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SweepManifestDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SweepsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: SweepsApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SweepsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `create posts work_order_id and client_op_id as given`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"sweep_id":"SWEEP-SERVER-1"}"""))

            val response =
                api.create(
                    CreateSweepRequestDto(
                        workOrderId = "WO-1",
                        clientOpId = "SESSION-1",
                        bootSessionId = "BOOT-1",
                        elapsedAnchorNs = "1",
                        utcAnchor = "2026-10-08T00:00:00Z",
                        utcUncertaintyMs = 50.0,
                        dataSource = "field",
                        startedElapsedNs = "1",
                    ),
                )

            assertEquals("SWEEP-SERVER-1", response.sweepId)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/sweeps", request.path)
            val body = request.body.readUtf8()
            assertEquals(true, body.contains(""""work_order_id":"WO-1""""))
            assertEquals(true, body.contains(""""client_op_id":"SESSION-1""""))
        }

    @Test
    fun `uploadClip sends the X-Content-SHA256 header and the path params`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"clip_no":0,"sha256":"abc"}"""))

            api.uploadClip(
                sweepId = "SWEEP-1",
                clipNo = 0,
                sha256 = "abc123",
                body = byteArrayOf(1, 2, 3).toRequestBody("video/mp4".toMediaType()),
            )

            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/sweeps/SWEEP-1/clips/0", request.path)
            assertEquals("abc123", request.getHeader("X-Content-SHA256"))
        }

    @Test
    fun `uploadRaw does not require X-Content-SHA256`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"kind":"gps_track","sha256":"abc"}"""))

            api.uploadRaw(
                sweepId = "SWEEP-1",
                kind = "gps_track",
                body = "line\n".toRequestBody("application/x-ndjson".toMediaType()),
            )

            val request = server.takeRequest()
            assertEquals("/api/v1/sweeps/SWEEP-1/raw/gps_track", request.path)
            assertNull(request.getHeader("X-Content-SHA256"))
        }

    @Test
    fun `submit posts the manifest body`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"sweep_id":"SWEEP-1"}"""))

            api.submit(
                sweepId = "SWEEP-1",
                body =
                    SubmitSweepRequestDto(
                        clientOpId = "SUBMIT-1",
                        endedElapsedNs = "2",
                        manifest =
                            SweepManifestDto(
                                clips = listOf(ClipManifestDto(clipNo = 0, sha256 = "a")),
                                gpsHash = "g",
                                luxHash = "l",
                                configHash = "c",
                            ),
                    ),
            )

            val request = server.takeRequest()
            assertEquals("/api/v1/sweeps/SWEEP-1/submit", request.path)
            val body = request.body.readUtf8()
            assertEquals(true, body.contains(""""clip_no":0"""))
            assertEquals(true, body.contains(""""gps_hash":"g""""))
        }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.network.SweepsApiTest"`
Expected: PASS, all 4 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/network/SweepsApi.kt \
        app/src/main/java/com/luxmap/core/network/dto/SweepDtos.kt \
        app/src/test/java/com/luxmap/core/network/SweepsApiTest.kt
git commit -m "feat(fm-40): add SweepsApi and sweep upload DTOs"
```

---

### Task 5: `SyncOpHandler` progress-reporting extension

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncOpHandler.kt`
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandler.kt` (signature only, no behavior change)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt` (signature only, no behavior change)
- Modify: `app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt`

**Interfaces:**
- Produces: `SyncOpHandler.handle(payloadJson, onProgress: (Long, Long) -> Unit = { _, _ -> }): SyncOpResult`; `SyncQueueProcessor.processQueuedOps(onRowProgress: (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit = { _, _, _ -> }): Boolean` — Tasks 6-8 call these exact names.

**Review Focus pin:** the two existing handlers' behavior must not change — this task's tests assert that explicitly.

- [ ] **Step 1: Write the failing test — processor forwards progress to the right handler by clientOpId**

Add to `SyncQueueProcessorTest.kt`:

```kotlin
    @Test
    fun `onRowProgress is called with the row's own clientOpId while the handler reports progress`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(1, "OP-1"))
            val reported = mutableListOf<Triple<String, Long, Long>>()
            val progressHandler =
                object : SyncOpHandler {
                    override val opType = "upload_work_order_evidence"

                    override suspend fun handle(
                        payloadJson: String,
                        onProgress: (Long, Long) -> Unit,
                    ): SyncOpResult {
                        onProgress(50L, 100L)
                        return SyncOpResult.Done
                    }
                }
            val processor = SyncQueueProcessor(dao, setOf(progressHandler))

            processor.processQueuedOps(onRowProgress = { clientOpId, sent, total -> reported.add(Triple(clientOpId, sent, total)) })

            assertEquals(listOf(Triple("OP-1", 50L, 100L)), reported)
        }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueProcessorTest"`
Expected: FAIL — `processQueuedOps` has no `onRowProgress` parameter yet.

- [ ] **Step 3: Extend `SyncOpHandler`**

```kotlin
// SyncOpHandler.kt
package com.luxmap.core.sync

interface SyncOpHandler {
    val opType: String

    suspend fun handle(
        payloadJson: String,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): SyncOpResult
}

sealed interface SyncOpResult {
    data object Done : SyncOpResult

    data object RetryLater : SyncOpResult

    data class Failed(val message: String) : SyncOpResult

    data class Conflict(val message: String) : SyncOpResult
}
```

- [ ] **Step 4: Thread the callback through `SyncQueueProcessor`**

Edit `SyncQueueProcessor.kt`:

```kotlin
        suspend fun processQueuedOps(
            onRowProgress: (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        ): Boolean {
            var stillPending = false
            for (row in dao.queuedRows()) {
                if (row.dependsOnClientOpId != null) {
                    when (dao.statusOf(row.dependsOnClientOpId)) {
                        "done" -> Unit
                        "failed", "conflict" ->
                            dao.updateStatus(row.id, "failed", row.attemptCount, "Dependency op failed", Instant.now())
                        else -> stillPending = true
                    }
                    if (dao.statusOf(row.dependsOnClientOpId) != "done") continue
                }

                if (row.attemptCount >= MAX_ATTEMPTS) {
                    dao.updateStatus(row.id, "failed", row.attemptCount, "Exceeded retry attempts", Instant.now())
                    continue
                }

                val handler = handlers.firstOrNull { it.opType == row.opType }
                if (handler == null) {
                    dao.updateStatus(row.id, "failed", row.attemptCount, "No handler for ${row.opType}", Instant.now())
                    continue
                }

                when (
                    val result =
                        handler.handle(row.payloadJson) { sent, total -> onRowProgress(row.clientOpId, sent, total) }
                ) {
                    is SyncOpResult.Done -> dao.updateStatus(row.id, "done", row.attemptCount, null, Instant.now())
                    is SyncOpResult.RetryLater -> {
                        dao.updateStatus(row.id, "queued", row.attemptCount + 1, null, Instant.now())
                        stillPending = true
                    }
                    is SyncOpResult.Failed ->
                        dao.updateStatus(row.id, "failed", row.attemptCount, result.message, Instant.now())
                    is SyncOpResult.Conflict ->
                        dao.updateStatus(row.id, "conflict", row.attemptCount, result.message, Instant.now())
                }
            }
            return stillPending
        }
```

- [ ] **Step 5: Update the 2 existing handlers' signatures (no behavior change)**

`UploadWorkOrderEvidenceSyncHandler.kt` and `CompleteWorkOrderSyncHandler.kt` each declare `override suspend fun handle(payloadJson: String): SyncOpResult` — add the new parameter without using it, since Kotlin requires the override to match the interface's full parameter list:

```kotlin
        override suspend fun handle(
            payloadJson: String,
            onProgress: (Long, Long) -> Unit,
        ): SyncOpResult {
```

(keep each method's existing body unchanged below this line.)

- [ ] **Step 6: Fix the other anonymous `SyncOpHandler` objects in `SyncQueueProcessorTest.kt`**

The `handler()` helper function and the inline anonymous object in `` `a row that has exceeded the retry cap...` `` both declare `override suspend fun handle(payloadJson: String): SyncOpResult` — add the parameter the same way:

```kotlin
private fun handler(
    opType: String,
    result: SyncOpResult,
) = object : SyncOpHandler {
    override val opType = opType

    override suspend fun handle(
        payloadJson: String,
        onProgress: (Long, Long) -> Unit,
    ): SyncOpResult = result
}
```

```kotlin
                        object : SyncOpHandler {
                            override val opType = "upload_work_order_evidence"

                            override suspend fun handle(
                                payloadJson: String,
                                onProgress: (Long, Long) -> Unit,
                            ): SyncOpResult {
                                handlerCalls += 1
                                return SyncOpResult.RetryLater
                            }
                        },
```

- [ ] **Step 7: Run the full sync test suite**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.*" --tests "com.luxmap.feature.workorder.data.sync.*"`
Expected: PASS, every test including the 2 existing handlers' tests unchanged.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/SyncOpHandler.kt \
        app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt \
        app/src/main/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandler.kt \
        app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt \
        app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt
git commit -m "feat(fm-40): add optional progress reporting to SyncOpHandler"
```

---

### Task 6: `create_survey_sweep` handler

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/SurveySweepSyncPayloads.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/CreateSurveySweepSyncHandler.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/sync/CreateSurveySweepSyncHandlerTest.kt`

**Interfaces:**
- Consumes: `SweepsApi.create` (Task 4), `SurveySessionDao.sessionById`/`updateSession` (existing).
- Produces: `opType = "create_survey_sweep"`; `CreateSurveySweepPayload(sessionId: String)`; after success, `LocalSurveySessionEntity.serverSweepId` is populated.

**Review Focus pin:** retried after a partial chain failure, this handler must not call `create()` again if `serverSweepId` is already set — it must short-circuit to `Done`.

- [ ] **Step 1: Write the payload data class**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/sync/SurveySweepSyncPayloads.kt
package com.luxmap.feature.survey.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSurveySweepPayload(
    @SerialName("session_id") val sessionId: String,
)

@Serializable
data class UploadSurveyClipPayload(
    @SerialName("session_id") val sessionId: String,
    @SerialName("clip_no") val clipNo: Int,
)

@Serializable
data class UploadSurveyRawPayload(
    @SerialName("session_id") val sessionId: String,
    val kind: String,
)

@Serializable
data class SubmitSurveySweepPayload(
    @SerialName("session_id") val sessionId: String,
)
```

- [ ] **Step 2: Write the failing tests**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/sync/CreateSurveySweepSyncHandlerTest.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SweepResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant

private fun httpException(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun session(serverSweepId: String? = null) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = "queued",
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 1L,
        endedAtUtc = Instant.parse("2026-10-08T10:30:00Z"),
        durationSeconds = 1800L,
        distanceMeters = 5000.0,
        gpsTrackFilePath = "/x/gps_track.ndjson",
        luxLogFilePath = "/x/lux_log.ndjson",
        frameTimestampLogFilePath = null,
        captureConfigFilePath = "/x/capture_config.json",
        manifestFilePath = "/x/manifest.json",
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        serverSweepId = serverSweepId,
        submitClientOpId = null,
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class CreateSurveySweepSyncHandlerTest {
    @Test
    fun `creates a sweep and persists the server-assigned sweep_id`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session()
            coEvery { api.create(any()) } returns SweepResponseDto(sweepId = "SWEEP-SERVER-1")
            val handler = CreateSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CreateSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateSession(match { it.serverSweepId == "SWEEP-SERVER-1" }) }
        }

    @Test
    fun `a retry after serverSweepId is already set does not call create again`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            val handler = CreateSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CreateSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify(exactly = 0) { api.create(any()) }
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session()
            coEvery { api.create(any()) } throws httpException(500)
            val handler = CreateSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CreateSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.CreateSurveySweepSyncHandlerTest"`
Expected: FAIL — `CreateSurveySweepSyncHandler` does not exist yet.

- [ ] **Step 4: Implement the handler**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/sync/CreateSurveySweepSyncHandler.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.CreateSweepRequestDto
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class CreateSurveySweepSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "create_survey_sweep"

        override suspend fun handle(
            payloadJson: String,
            onProgress: (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<CreateSurveySweepPayload>(payloadJson)
            val session =
                dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")

            // Retried after a partial chain failure downstream (e.g. clip 2 of 3 failed) - do not
            // create a second sweep, reuse the one already assigned (Review Focus item).
            if (session.serverSweepId != null) return SyncOpResult.Done

            return try {
                val response =
                    api.create(
                        CreateSweepRequestDto(
                            workOrderId = session.workOrderId,
                            clientOpId = session.sessionId,
                            bootSessionId = session.sessionId, // placeholder, see contract-drift.md entry below
                            elapsedAnchorNs = session.startedAtElapsedNs.toString(),
                            utcAnchor = session.startedAtUtc.toString(),
                            utcUncertaintyMs = 50.0,
                            dataSource = "field",
                            startedElapsedNs = session.startedAtElapsedNs.toString(),
                        ),
                    )
                dao.updateSession(session.copy(serverSweepId = response.sweepId, updatedAt = Instant.now()))
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

Add the missing import at the top (same helper `UploadWorkOrderEvidenceSyncHandler` already uses):

```kotlin
import com.luxmap.core.network.errorCodeOrNull
```

**Known gap flagged inline, not silently guessed:** `bootSessionId` is passed as `session.sessionId` here, which is wrong — `boot_session_id` should be the real per-phone-boot UUID from `BootSessionProvider` (built in fm-39, per `docs/superpowers/plans/2026-10-08-survey-package-schema-v1.md`), not the session id. `LocalSurveySessionEntity` does not currently store a `bootSessionId` column at all — `CaptureConfigWriter`'s `CaptureConfig.bootSessionId` is held only on the in-memory config object passed to `capture_config.json`, never persisted onto the session row Room keeps. Add a row to `docs/contract-drift.md` for this right after writing the handler: "`create_survey_sweep`'s `boot_session_id`" / "Known gap (fm-40) — uses `session.sessionId` as a placeholder since `LocalSurveySessionEntity` has no `bootSessionId` column; needs one added (same value `CaptureConfigWriter` already writes to `capture_config.json`) before this is correct" / owner WP6.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.CreateSurveySweepSyncHandlerTest"`
Expected: PASS, all 3 tests.

- [ ] **Step 6: Bind it in `SyncModule`**

Edit `di/SyncModule.kt`:

```kotlin
    @Binds
    @IntoSet
    abstract fun bindCreateSurveySweepSyncHandler(impl: CreateSurveySweepSyncHandler): SyncOpHandler
```

Add the import:

```kotlin
import com.luxmap.feature.survey.data.sync.CreateSurveySweepSyncHandler
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/sync/SurveySweepSyncPayloads.kt \
        app/src/main/java/com/luxmap/feature/survey/data/sync/CreateSurveySweepSyncHandler.kt \
        app/src/test/java/com/luxmap/feature/survey/data/sync/CreateSurveySweepSyncHandlerTest.kt \
        app/src/main/java/com/luxmap/di/SyncModule.kt \
        docs/contract-drift.md
git commit -m "feat(fm-40): add create_survey_sweep sync handler"
```

---

### Task 7: `upload_survey_clip` and `upload_survey_raw` handlers

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/UploadSurveyClipSyncHandler.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/UploadSurveyRawSyncHandler.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/sync/UploadSurveyClipSyncHandlerTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/sync/UploadSurveyRawSyncHandlerTest.kt`
- Modify: `app/src/main/java/com/luxmap/di/SyncModule.kt`

**Interfaces:**
- Consumes: `SweepsApi.uploadClip/uploadRaw` (Task 4), `SurveySessionDao.sessionById`/`segmentsFor` (existing).
- Produces: `opType = "upload_survey_clip"`, `opType = "upload_survey_raw"`.

**Review Focus pin:** a `409` on either endpoint maps to `Conflict`, never `Failed`/`RetryLater`.

- [ ] **Step 1: Write the failing tests for the clip handler**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/sync/UploadSurveyClipSyncHandlerTest.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SurveyClipResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.time.Instant

private fun httpException(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

// Each sync-handler test file defines its own copy of this fixture, same convention as
// PackageSurveySessionUseCaseTest's sessionWithFiles() - no shared test-helper file in this
// project, kept consistent here rather than introducing a new pattern.
private fun session(serverSweepId: String? = null) =
    com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = "queued",
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 1L,
        endedAtUtc = Instant.parse("2026-10-08T10:30:00Z"),
        durationSeconds = 1800L,
        distanceMeters = 5000.0,
        gpsTrackFilePath = "/x/gps_track.ndjson",
        luxLogFilePath = "/x/lux_log.ndjson",
        frameTimestampLogFilePath = null,
        captureConfigFilePath = "/x/capture_config.json",
        manifestFilePath = "/x/manifest.json",
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        serverSweepId = serverSweepId,
        submitClientOpId = null,
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class UploadSurveyClipSyncHandlerTest {
    @Test
    fun `uploads the clip file with its persisted checksum and returns Done`() =
        runTest {
            val clipFile = File.createTempFile("clip", ".mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = clipFile.absolutePath,
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 3L,
                        checksumSha256 = "abc123",
                    ),
                )
            coEvery { api.uploadClip("SWEEP-SERVER-1", 0, "abc123", any()) } returns
                SurveyClipResponseDto(clipNo = 0, sha256 = "abc123")
            val handler = UploadSurveyClipSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyClipPayload("SESSION-1", 0)))

            assertTrue(result is SyncOpResult.Done)
        }

    @Test
    fun `a 409 maps to Conflict, not Failed or RetryLater`() =
        runTest {
            val clipFile = File.createTempFile("clip", ".mp4").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = clipFile.absolutePath,
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 1L,
                        checksumSha256 = "abc",
                    ),
                )
            coEvery { api.uploadClip(any(), any(), any(), any()) } throws httpException(409)
            val handler = UploadSurveyClipSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyClipPayload("SESSION-1", 0)))

            assertTrue(result is SyncOpResult.Conflict)
        }
}
```

- [ ] **Step 2: Write the failing tests for the raw handler**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/sync/UploadSurveyRawSyncHandlerTest.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SurveyRawResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.time.Instant

private fun httpException(code: Int) =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

// Same per-file fixture convention as UploadSurveyClipSyncHandlerTest - see its comment.
private fun session(serverSweepId: String? = null) =
    com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = "queued",
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 1L,
        endedAtUtc = Instant.parse("2026-10-08T10:30:00Z"),
        durationSeconds = 1800L,
        distanceMeters = 5000.0,
        gpsTrackFilePath = "/x/gps_track.ndjson",
        luxLogFilePath = "/x/lux_log.ndjson",
        frameTimestampLogFilePath = null,
        captureConfigFilePath = "/x/capture_config.json",
        manifestFilePath = "/x/manifest.json",
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        serverSweepId = serverSweepId,
        submitClientOpId = null,
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class UploadSurveyRawSyncHandlerTest {
    @Test
    fun `uploads gps_track with its persisted checksum and returns Done`() =
        runTest {
            val gpsFile = File.createTempFile("gps_track", ".ndjson").apply { writeText("line\n") }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns
                session(serverSweepId = "SWEEP-SERVER-1").copy(gpsTrackFilePath = gpsFile.absolutePath)
            coEvery { api.uploadRaw("SWEEP-SERVER-1", "gps_track", any()) } returns
                SurveyRawResponseDto(kind = "gps_track", sha256 = "a")
            val handler = UploadSurveyRawSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyRawPayload("SESSION-1", "gps_track")))

            assertTrue(result is SyncOpResult.Done)
        }

    @Test
    fun `a 409 on raw upload maps to Conflict`() =
        runTest {
            val configFile = File.createTempFile("capture_config", ".json").apply { writeText("{}") }
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns
                session(serverSweepId = "SWEEP-SERVER-1").copy(captureConfigFilePath = configFile.absolutePath)
            coEvery { api.uploadRaw(any(), any(), any()) } throws httpException(409)
            val handler = UploadSurveyRawSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(UploadSurveyRawPayload("SESSION-1", "capture_config")))

            assertTrue(result is SyncOpResult.Conflict)
        }
}
```

- [ ] **Step 3: Run to verify both fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.UploadSurveyClipSyncHandlerTest" --tests "com.luxmap.feature.survey.data.sync.UploadSurveyRawSyncHandlerTest"`
Expected: FAIL — neither handler exists yet.

- [ ] **Step 4: Implement `UploadSurveyClipSyncHandler`**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/sync/UploadSurveyClipSyncHandler.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UploadSurveyClipSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "upload_survey_clip"

        override suspend fun handle(
            payloadJson: String,
            onProgress: (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<UploadSurveyClipPayload>(payloadJson)
            val session = dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")
            val sweepId = session.serverSweepId ?: return SyncOpResult.RetryLater // create_survey_sweep not done yet
            val segment =
                dao.segmentsFor(payload.sessionId).firstOrNull { it.segmentIndex == payload.clipNo }
                    ?: return SyncOpResult.Failed("Local video segment ${payload.clipNo} missing")
            val checksum = segment.checksumSha256 ?: return SyncOpResult.Failed("Segment ${payload.clipNo} has no checksum")

            return try {
                val file = File(segment.filePath)
                onProgress(0L, file.length())
                api.uploadClip(
                    sweepId = sweepId,
                    clipNo = payload.clipNo,
                    sha256 = checksum,
                    body = file.asRequestBody("video/mp4".toMediaType()),
                )
                onProgress(file.length(), file.length())
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

**Note on `onProgress` granularity:** this reports only 0% and 100% around the single `uploadClip` call, not real mid-upload byte progress — OkHttp's `RequestBody` would need a custom wrapper (overriding `writeTo(BufferedSink)`) to report incremental progress during the actual socket write, which is a bigger, separable change. Flagged here rather than silently shipped as if it were granular: add to `docs/contract-drift.md`: "`upload_survey_clip`/`upload_survey_raw` progress granularity" / "Known gap (fm-40) — `onProgress` only fires at 0% and 100% per file, not continuously during the socket write; a real byte-by-byte progress bar needs a custom `RequestBody` wrapping `writeTo()`, not implemented here to keep this task's scope to the sync-handler/API wiring" / owner WP6.

- [ ] **Step 5: Implement `UploadSurveyRawSyncHandler`**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/sync/UploadSurveyRawSyncHandler.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UploadSurveyRawSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "upload_survey_raw"

        override suspend fun handle(
            payloadJson: String,
            onProgress: (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<UploadSurveyRawPayload>(payloadJson)
            val session = dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")
            val sweepId = session.serverSweepId ?: return SyncOpResult.RetryLater

            val (path, mediaType) =
                when (payload.kind) {
                    "gps_track" -> session.gpsTrackFilePath to "application/x-ndjson"
                    "lux_log" -> session.luxLogFilePath to "application/x-ndjson"
                    "capture_config" -> session.captureConfigFilePath to "application/json"
                    else -> return SyncOpResult.Failed("Unknown raw kind ${payload.kind}")
                }
            if (path == null) return SyncOpResult.Failed("Session has no ${payload.kind} file path")

            return try {
                val file = File(path)
                onProgress(0L, file.length())
                api.uploadRaw(
                    sweepId = sweepId,
                    kind = payload.kind,
                    body = file.asRequestBody(mediaType.toMediaType()),
                )
                onProgress(file.length(), file.length())
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

Remove the unused `LocalSurveySessionEntity` import if the compiler flags it (it is not directly referenced in this file's body).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.UploadSurveyClipSyncHandlerTest" --tests "com.luxmap.feature.survey.data.sync.UploadSurveyRawSyncHandlerTest"`
Expected: PASS, all 4 tests.

- [ ] **Step 7: Bind both in `SyncModule`**

```kotlin
    @Binds
    @IntoSet
    abstract fun bindUploadSurveyClipSyncHandler(impl: UploadSurveyClipSyncHandler): SyncOpHandler

    @Binds
    @IntoSet
    abstract fun bindUploadSurveyRawSyncHandler(impl: UploadSurveyRawSyncHandler): SyncOpHandler
```

```kotlin
import com.luxmap.feature.survey.data.sync.UploadSurveyClipSyncHandler
import com.luxmap.feature.survey.data.sync.UploadSurveyRawSyncHandler
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/sync/UploadSurveyClipSyncHandler.kt \
        app/src/main/java/com/luxmap/feature/survey/data/sync/UploadSurveyRawSyncHandler.kt \
        app/src/test/java/com/luxmap/feature/survey/data/sync/UploadSurveyClipSyncHandlerTest.kt \
        app/src/test/java/com/luxmap/feature/survey/data/sync/UploadSurveyRawSyncHandlerTest.kt \
        app/src/main/java/com/luxmap/di/SyncModule.kt \
        docs/contract-drift.md
git commit -m "feat(fm-40): add upload_survey_clip and upload_survey_raw sync handlers"
```

---

### Task 8: `submit_survey_sweep` handler

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/SubmitSurveySweepSyncHandler.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/sync/SubmitSurveySweepSyncHandlerTest.kt`
- Modify: `app/src/main/java/com/luxmap/di/SyncModule.kt`

**Interfaces:**
- Consumes: `SweepsApi.submit` (Task 4), `SurveySessionDao.sessionById`/`segmentsFor`/`updateSession` (existing).
- Produces: `opType = "submit_survey_sweep"`; on success, `session.syncState = "done"`.

**Review Focus pin:** `submitClientOpId` is generated once and reused on every retry.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/sync/SubmitSurveySweepSyncHandlerTest.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SweepResponseDto
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Same per-file fixture convention as UploadSurveyClipSyncHandlerTest - see its comment.
private fun session(serverSweepId: String? = null) =
    com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = "queued",
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 1L,
        endedAtUtc = Instant.parse("2026-10-08T10:30:00Z"),
        durationSeconds = 1800L,
        distanceMeters = 5000.0,
        gpsTrackFilePath = "/x/gps_track.ndjson",
        luxLogFilePath = "/x/lux_log.ndjson",
        frameTimestampLogFilePath = null,
        captureConfigFilePath = "/x/capture_config.json",
        manifestFilePath = "/x/manifest.json",
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        serverSweepId = serverSweepId,
        submitClientOpId = null,
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class SubmitSurveySweepSyncHandlerTest {
    @Test
    fun `submits with the persisted checksums and marks the session done`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-1",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = "/x/clip_0.mp4",
                        startedAtElapsedNs = 0L,
                        endedAtElapsedNs = 1L,
                        sizeBytes = 1L,
                        checksumSha256 = "clip-hash",
                    ),
                )
            coEvery { api.submit("SWEEP-SERVER-1", any()) } returns SweepResponseDto(sweepId = "SWEEP-SERVER-1")
            val handler = SubmitSurveySweepSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(SubmitSurveySweepPayload("SESSION-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateSession(match { it.syncState == "done" }) }
        }

    @Test
    fun `generates submitClientOpId once and persists it, a retry reuses the same value`() =
        runTest {
            val api = mockk<SweepsApi>()
            val dao = mockk<SurveySessionDao>(relaxed = true)
            coEvery { dao.sessionById("SESSION-1") } returns session(serverSweepId = "SWEEP-SERVER-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val clientOpIdSlot = slot<SubmitSweepRequestDto>()
            coEvery { api.submit("SWEEP-SERVER-1", capture(clientOpIdSlot)) } answers {
                SweepResponseDto(sweepId = "SWEEP-SERVER-1")
            }
            val handler = SubmitSurveySweepSyncHandler(api, dao)

            handler.handle(Json.encodeToString(SubmitSurveySweepPayload("SESSION-1")))
            val firstClientOpId = clientOpIdSlot.captured.clientOpId

            // Simulate a retry: the session row now has the persisted submitClientOpId from the first attempt.
            coEvery { dao.sessionById("SESSION-1") } returns
                session(serverSweepId = "SWEEP-SERVER-1").copy(submitClientOpId = firstClientOpId)
            handler.handle(Json.encodeToString(SubmitSurveySweepPayload("SESSION-1")))

            assertEquals(firstClientOpId, clientOpIdSlot.captured.clientOpId)
        }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.SubmitSurveySweepSyncHandlerTest"`
Expected: FAIL — `SubmitSurveySweepSyncHandler` does not exist yet.

- [ ] **Step 3: Implement the handler**

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/sync/SubmitSurveySweepSyncHandler.kt
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.SweepsApi
import com.luxmap.core.network.dto.ClipManifestDto
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SweepManifestDto
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SubmitSurveySweepSyncHandler
    @Inject
    constructor(
        private val api: SweepsApi,
        private val dao: SurveySessionDao,
    ) : SyncOpHandler {
        override val opType = "submit_survey_sweep"

        override suspend fun handle(
            payloadJson: String,
            onProgress: (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<SubmitSurveySweepPayload>(payloadJson)
            val session = dao.sessionById(payload.sessionId) ?: return SyncOpResult.Failed("Local session row missing")
            val sweepId = session.serverSweepId ?: return SyncOpResult.RetryLater
            val gpsHash = session.gpsTrackChecksumSha256 ?: return SyncOpResult.Failed("Missing gps_track checksum")
            val luxHash = session.luxLogChecksumSha256 ?: return SyncOpResult.Failed("Missing lux_log checksum")
            val configHash =
                session.captureConfigChecksumSha256 ?: return SyncOpResult.Failed("Missing capture_config checksum")
            val segments = dao.segmentsFor(payload.sessionId)
            val missingClipChecksum = segments.any { it.checksumSha256 == null }
            if (missingClipChecksum) return SyncOpResult.Failed("A video segment has no checksum yet")

            // Generated once, reused on every retry - regenerating this on a retry would break the
            // server's idempotency check on client_op_id (Review Focus item).
            val submitClientOpId = session.submitClientOpId ?: UUID.randomUUID().toString()
            if (session.submitClientOpId == null) {
                dao.updateSession(session.copy(submitClientOpId = submitClientOpId, updatedAt = Instant.now()))
            }

            return try {
                api.submit(
                    sweepId = sweepId,
                    body =
                        SubmitSweepRequestDto(
                            clientOpId = submitClientOpId,
                            // LocalSurveySessionEntity has no endedAtElapsedNs column (only
                            // endedAtUtc: Instant?), so the real elapsed-nanos value at recording
                            // stop is read from the last video segment's endedAtElapsedNs instead,
                            // which IS persisted.
                            endedElapsedNs =
                                (segments.maxOfOrNull { it.endedAtElapsedNs ?: it.startedAtElapsedNs } ?: session.startedAtElapsedNs)
                                    .toString(),
                            manifest =
                                SweepManifestDto(
                                    clips =
                                        segments
                                            .sortedBy { it.segmentIndex }
                                            .map { ClipManifestDto(clipNo = it.segmentIndex, sha256 = it.checksumSha256!!) },
                                    gpsHash = gpsHash,
                                    luxHash = luxHash,
                                    configHash = configHash,
                                ),
                        ),
                )
                dao.updateSession(session.copy(syncState = "done", updatedAt = Instant.now()))
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.SubmitSurveySweepSyncHandlerTest"`
Expected: PASS, both tests.

- [ ] **Step 5: Bind it in `SyncModule`**

```kotlin
    @Binds
    @IntoSet
    abstract fun bindSubmitSurveySweepSyncHandler(impl: SubmitSurveySweepSyncHandler): SyncOpHandler
```

```kotlin
import com.luxmap.feature.survey.data.sync.SubmitSurveySweepSyncHandler
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/sync/SubmitSurveySweepSyncHandler.kt \
        app/src/test/java/com/luxmap/feature/survey/data/sync/SubmitSurveySweepSyncHandlerTest.kt \
        app/src/main/java/com/luxmap/di/SyncModule.kt
git commit -m "feat(fm-40): add submit_survey_sweep sync handler"
```

---

### Task 9: `RealUploadRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt`

**Interfaces:**
- Consumes: `SyncQueueManager.enqueue` (existing), `SyncQueueProcessor.processQueuedOps(onRowProgress)` (Task 5), `SurveySessionDao` (existing), the 4 new `opType`s (Tasks 6-8).
- Produces: `RealUploadRepository: UploadRepository`, bound in `RepositoryModule` — `SubmitViewModel` needs no change to keep working, only `UploadProgress` gains a case (Task 10 reacts to it).

- [ ] **Step 1: Add `Conflict` to `UploadProgress`**

Edit `UploadRepository.kt`:

```kotlin
sealed interface UploadProgress {
    data class InProgress(val bytesSent: Long, val totalBytes: Long) : UploadProgress

    data object Done : UploadProgress

    data class Failed(val reason: String) : UploadProgress

    data class Conflict(val reason: String) : UploadProgress
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt
package com.luxmap.feature.survey.data

import app.cash.turbine.test
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun packagedSession(syncState: String? = null) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-LOCAL-1",
        workOrderId = "WO-1",
        recordingState = "packaged",
        syncState = syncState,
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 1L,
        endedAtUtc = Instant.parse("2026-10-08T10:30:00Z"),
        durationSeconds = 1800L,
        distanceMeters = 5000.0,
        gpsTrackFilePath = "/x/gps_track.ndjson",
        luxLogFilePath = "/x/lux_log.ndjson",
        frameTimestampLogFilePath = null,
        captureConfigFilePath = "/x/capture_config.json",
        manifestFilePath = "/x/manifest.json",
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        gpsTrackChecksumSha256 = "a",
        luxLogChecksumSha256 = "b",
        captureConfigChecksumSha256 = "c",
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:30:00Z"),
    )

class RealUploadRepositoryTest {
    @Test
    fun `first submit enqueues the full chain and emits Done once everything succeeds`() =
        runTest {
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession()
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "done"
            val repository = RealUploadRepository(sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                val last = expectMostRecentItem()
                assertTrue(last is UploadProgress.Done)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(atLeast = 1) { syncQueueManager.enqueue(any(), any(), any(), any()) }
        }

    @Test
    fun `a retry does not enqueue again when syncState is already set`() =
        runTest {
            val sessionDao = mockk<SurveySessionDao>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { sessionDao.sessionById("SESSION-1") } returns packagedSession(syncState = "failed")
            coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val processor = mockk<SyncQueueProcessor>()
            coEvery { processor.processQueuedOps(any()) } returns false
            coEvery { syncQueueDao.statusOf(any()) } returns "failed"
            val repository = RealUploadRepository(sessionDao, syncQueueDao, syncQueueManager, processor)

            repository.uploadSession("SESSION-1").test {
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 0) { syncQueueManager.enqueue(any(), any(), any(), any()) }
        }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealUploadRepositoryTest"`
Expected: FAIL — `RealUploadRepository` does not exist yet.

- [ ] **Step 4: Implement `RealUploadRepository`**

`sessionDao.segmentsFor(sessionId)` is fetched once at the top of `uploadSession` (it's a suspend call, so it cannot happen lazily inside a plain helper) and threaded into both `opIdsFor` and `enqueueChain`, since both need the clip count and `enqueueChain` additionally needs each clip's index:

```kotlin
// app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt
package com.luxmap.feature.survey.data

import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.sync.CreateSurveySweepPayload
import com.luxmap.feature.survey.data.sync.SubmitSurveySweepPayload
import com.luxmap.feature.survey.data.sync.UploadSurveyClipPayload
import com.luxmap.feature.survey.data.sync.UploadSurveyRawPayload
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import javax.inject.Inject

class RealUploadRepository
    @Inject
    constructor(
        private val sessionDao: SurveySessionDao,
        private val syncQueueDao: SyncQueueDao,
        private val syncQueueManager: SyncQueueManager,
        private val syncQueueProcessor: SyncQueueProcessor,
    ) : UploadRepository {
        override fun uploadSession(sessionId: String): Flow<UploadProgress> =
            callbackFlow {
                val session = sessionDao.sessionById(sessionId)
                if (session == null) {
                    trySend(UploadProgress.Failed("Session $sessionId not found"))
                    close()
                    return@callbackFlow
                }
                val segments = sessionDao.segmentsFor(sessionId).sortedBy { it.segmentIndex }

                val opIds = opIdsFor(session, segments.size)
                if (session.syncState == null) {
                    enqueueChain(session, segments.size, opIds)
                    sessionDao.updateSession(session.copy(syncState = "queued", updatedAt = Instant.now()))
                }

                val totalBytes =
                    segments.sumOf { it.sizeBytes ?: 0L } +
                        listOfNotNull(session.gpsTrackFilePath, session.luxLogFilePath, session.captureConfigFilePath)
                            .sumOf { File(it).length() }
                val sentByOp = mutableMapOf<String, Long>()
                syncQueueProcessor.processQueuedOps(
                    onRowProgress = { clientOpId, sent, _ ->
                        if (clientOpId in opIds) {
                            sentByOp[clientOpId] = sent
                            trySend(UploadProgress.InProgress(sentByOp.values.sum(), totalBytes))
                        }
                    },
                )

                val statuses = opIds.map { syncQueueDao.statusOf(it) }
                when {
                    statuses.any { it == "conflict" } -> trySend(UploadProgress.Conflict("Dữ liệu đã thay đổi trên server"))
                    statuses.any { it == "failed" } -> trySend(UploadProgress.Failed("Nộp thất bại, thử lại sau"))
                    statuses.all { it == "done" } -> trySend(UploadProgress.Done)
                    else -> trySend(UploadProgress.InProgress(sentByOp.values.sum(), totalBytes))
                }
                close()
                awaitClose { }
            }

        private fun opIdsFor(
            session: LocalSurveySessionEntity,
            clipCount: Int,
        ): List<String> {
            val clipIds = (0 until clipCount).map { "${session.sessionId}:clip:$it" }
            val rawIds = listOf("gps_track", "lux_log", "capture_config").map { "${session.sessionId}:raw:$it" }
            return listOf("${session.sessionId}:create_sweep") + clipIds + rawIds + listOf("${session.sessionId}:submit")
        }

        private suspend fun enqueueChain(
            session: LocalSurveySessionEntity,
            clipCount: Int,
            opIds: List<String>,
        ) {
            val createId = opIds.first()
            syncQueueManager.enqueue(
                opType = "create_survey_sweep",
                payloadJson = Json.encodeToString(CreateSurveySweepPayload(session.sessionId)),
                clientOpId = createId,
            )

            var previousId = createId
            (0 until clipCount).forEach { clipNo ->
                val clipOpId = "${session.sessionId}:clip:$clipNo"
                syncQueueManager.enqueue(
                    opType = "upload_survey_clip",
                    payloadJson = Json.encodeToString(UploadSurveyClipPayload(session.sessionId, clipNo)),
                    clientOpId = clipOpId,
                    dependsOnClientOpId = previousId,
                )
                previousId = clipOpId
            }

            listOf("gps_track", "lux_log", "capture_config").forEach { kind ->
                val rawOpId = "${session.sessionId}:raw:$kind"
                syncQueueManager.enqueue(
                    opType = "upload_survey_raw",
                    payloadJson = Json.encodeToString(UploadSurveyRawPayload(session.sessionId, kind)),
                    clientOpId = rawOpId,
                    dependsOnClientOpId = previousId,
                )
                previousId = rawOpId
            }

            syncQueueManager.enqueue(
                opType = "submit_survey_sweep",
                payloadJson = Json.encodeToString(SubmitSurveySweepPayload(session.sessionId)),
                clientOpId = opIds.last(),
                dependsOnClientOpId = previousId,
            )
        }
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealUploadRepositoryTest"`
Expected: PASS, both tests.

- [ ] **Step 6: Bind it in `RepositoryModule`**

Find the existing `bindUploadRepository` binding (`di/RepositoryModule.kt:46`) and change it:

```kotlin
    @Binds
    abstract fun bindUploadRepository(impl: RealUploadRepository): UploadRepository
```

Replace the `FakeUploadRepository` import with `RealUploadRepository`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt \
        app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt \
        app/src/main/java/com/luxmap/di/RepositoryModule.kt \
        app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt
git commit -m "feat(fm-40): add RealUploadRepository, bind it in place of the fake"
```

---

### Task 10: `SubmitUiState`/`SubmitScreen` rework

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt` (new)

**Interfaces:**
- Consumes: `UploadProgress.{InProgress,Done,Failed,Conflict}` (Task 9), `SurveySessionDao.sessionById` (existing), `ConnectivityObserver.isOnline` (existing).

- [ ] **Step 1: Split `SubmitUiState.Error` into `Failed`/`Conflict`**

```kotlin
// SubmitUiState.kt
package com.luxmap.feature.survey.ui.submit

sealed interface SubmitUiState {
    data object Idle : SubmitUiState

    data class Uploading(val bytesSent: Long, val totalBytes: Long, val isOffline: Boolean = false) : SubmitUiState

    data object Done : SubmitUiState

    data class Failed(val message: String) : SubmitUiState

    data class Conflict(val message: String) : SubmitUiState

    // Shown when the session has not finished PackageSurveySessionUseCase yet - "Nộp ngay" must
    // not be reachable from here (Review Focus item).
    data object NotPackagedYet : SubmitUiState
}
```

- [ ] **Step 2: Write the failing test for the validation gate and the new state mapping**

```kotlin
// app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt
package com.luxmap.feature.survey.ui.submit

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun session(recordingState: String) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-1",
        workOrderId = "WO-1",
        recordingState = recordingState,
        syncState = null,
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 0L,
        endedAtUtc = null,
        durationSeconds = null,
        distanceMeters = null,
        gpsTrackFilePath = null,
        luxLogFilePath = null,
        frameTimestampLogFilePath = null,
        captureConfigFilePath = null,
        manifestFilePath = null,
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:00:00Z"),
    )

class SubmitViewModelTest {
    @Test
    fun `a session that is not packaged yet shows NotPackagedYet and onSubmit is a no-op`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "recording")
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.NotPackagedYet)
            }
        }

    @Test
    fun `a Conflict from the repository maps to SubmitUiState Conflict`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.Conflict("x"))
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.Conflict)
            }
        }
}
```

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: FAIL — `SubmitViewModel`'s constructor doesn't take `SurveySessionDao`/`ConnectivityObserver` yet, and `NotPackagedYet` doesn't exist.

- [ ] **Step 4: Implement the new `SubmitViewModel`**

```kotlin
// SubmitViewModel.kt
package com.luxmap.feature.survey.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
        private val sessionDao: SurveySessionDao,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        fun onSubmit(sessionId: String) {
            viewModelScope.launch {
                val session = sessionDao.sessionById(sessionId)
                if (session == null || session.recordingState != "packaged") {
                    _uiState.value = SubmitUiState.NotPackagedYet
                    return@launch
                }

                repository
                    .uploadSession(sessionId)
                    .catch { e -> _uiState.value = SubmitUiState.Failed(e.message ?: "Tải lên thất bại") }
                    .collect { progress ->
                        _uiState.value =
                            when (progress) {
                                is UploadProgress.InProgress -> {
                                    val isOffline = !connectivityObserver.isOnline.first()
                                    SubmitUiState.Uploading(progress.bytesSent, progress.totalBytes, isOffline)
                                }
                                is UploadProgress.Done -> SubmitUiState.Done
                                is UploadProgress.Failed -> SubmitUiState.Failed(progress.reason)
                                is UploadProgress.Conflict -> SubmitUiState.Conflict(progress.reason)
                            }
                    }
            }
        }
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: PASS, both tests.

- [ ] **Step 6: Rework `SubmitScreen`**

```kotlin
// SubmitScreen.kt
package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge

@Composable
fun SubmitScreen(
    sessionId: String,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(sessionId) { viewModel.onSubmit(sessionId) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column {
            when (val state = uiState) {
                is SubmitUiState.Idle -> StatusBadge(text = SyncStatus.QUEUED_ONLINE.label(), colors = SyncStatus.QUEUED_ONLINE.badgeColors())

                is SubmitUiState.Uploading -> {
                    val fraction = if (state.totalBytes == 0L) 0f else state.bytesSent.toFloat() / state.totalBytes.toFloat()
                    LinearProgressIndicator(progress = { fraction })
                    Text("${state.bytesSent} / ${state.totalBytes} bytes")
                    if (state.isOffline) {
                        StatusBadge(text = SyncStatus.QUEUED_OFFLINE.label(), colors = SyncStatus.QUEUED_OFFLINE.badgeColors())
                    } else {
                        StatusBadge(text = SyncStatus.SYNCING.label(), colors = SyncStatus.SYNCING.badgeColors())
                    }
                }

                is SubmitUiState.Done -> {
                    StatusBadge(text = SyncStatus.DONE.label(), colors = SyncStatus.DONE.badgeColors())
                    Text("Đã nộp thành công", style = MaterialTheme.typography.bodyLarge)
                }

                is SubmitUiState.Failed -> {
                    StatusBadge(text = SyncStatus.FAILED.label(), colors = SyncStatus.FAILED.badgeColors())
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(text = "Nộp lại", onClick = { viewModel.onSubmit(sessionId) })
                }

                is SubmitUiState.Conflict -> {
                    StatusBadge(text = SyncStatus.CONFLICT.label(), colors = SyncStatus.CONFLICT.badgeColors())
                    Text(state.message, style = MaterialTheme.typography.bodyLarge)
                }

                is SubmitUiState.NotPackagedYet ->
                    Text("Phiên khảo sát chưa đóng gói xong, chưa thể nộp", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
```

The screen used to require an explicit "Nộp ngay" tap (`SubmitUiState.Idle` had a `Button`); this rework calls `onSubmit` automatically via `LaunchedEffect(sessionId)` on first composition instead, since Task 9's `RealUploadRepository` now safely no-ops re-enqueueing on a second call (checked by `syncState == null`) — re-entering this screen (e.g. after a process death) naturally resumes instead of requiring a manual tap again. If the original tap-to-start interaction was intentional UX (not just a placeholder), confirm with the project owner before this ships; this plan assumes auto-start is the better default since the spec's §4D error-handling section already treats "Nộp lại" as the retry affordance for `Failed`, implying "Nộp ngay" for the first attempt was never meant to require a separate idle screen.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt \
        app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt
git commit -m "feat(fm-40): split submit failed/conflict states, validate packaged before submit"
```

- [ ] **Step 8: Run ktlint and fix formatting**

Run: `./gradlew ktlintCheck`
Expected: fix any reported issues, then re-run until it passes.

---

## Real-device checklist (log in `docs/contract-drift.md`, not automatable)

- Large clip upload (~60 MB at 60s segments) over real 4G/weak signal — confirm the PUT actually completes and `X-Content-SHA256` matches what the server computes.
- Kill the app process mid-upload (after at least one clip is `done`) and confirm `SyncWorker`'s background run resumes the remaining ops without re-uploading the already-`done` ones.
- Full happy path: Home → tap a `taskKind = survey` Work Order → "Bắt đầu" → record in F04 → F06 "Nộp" → confirm the sweep and its files appear server-side.
- A `409` on a clip re-upload (re-record the same session's video after a prior partial upload, forcing a byte mismatch) — confirm `SubmitUiState.Conflict` shows and does not auto-retry.
