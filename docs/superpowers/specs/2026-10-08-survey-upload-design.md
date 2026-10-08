# Survey session upload (F06) — design

**Status:** Approved in chat 2026-10-08, pending written-spec review before `writing-plans`.

## Goal

Make F06 (Nộp đợt khảo sát) actually send a recorded survey session to the real
backend. Today `SubmitViewModel` only talks to `FakeUploadRepository` (a fake
50 MB progress simulation) — there is no `RealUploadRepository`, no Retrofit API
for `/api/v1/sweeps`, and no way for mobile to even know the `work_order_id` a
recorded session belongs to.

Source of truth for the real upload flow: `mobile.pdf` (leader, 2026-10-08,
read directly — see `docs/contract-drift.md` for the summarized version) and
`luxmap_backend/docs/openapi/luxmap-v1.5.json` (read directly for exact DTO
field names — this spec does not guess field names anywhere).

## Non-goals (explicitly out of scope for this plan)

- Rewriting F03's own "Khảo sát" tab (`Routes.Survey`, backed by the
  unconfirmed `GET /api/v1/survey-sweeps/planned`) — stays as-is, unrelated
  legacy UI path, not blocking this work.
- `clips[]` (video PTS-mapping block in `capture_config.json`) — still an open
  decision with WP2/WP5/project owner per `contract-drift.md`, not touched here.
- Finalizing the GPS-accuracy-gate numbers (`GpsAccuracyGate.PROPOSED_*`) — BE
  said these are confirmed after a real field test, not this plan's call.
- Original F05 coverage-% check — documented gap, out of scope (per earlier
  conversation, user said skip for now).
- Any change to `CheckSurveyReadinessUseCase`/capture-time logic beyond what's
  needed to pass `workOrderId` through.

## 1. Data model (Room)

### `LocalSurveySessionEntity` — new columns

| Column | Type | Purpose |
|---|---|---|
| `workOrderId` | `String` | Required. The work order (`taskKind == "survey"`) this session belongs to. Set once at session creation, never null after that point — a session cannot be created without it (see §4A). |
| `serverSweepId` | `String?` | Null until `create_survey_sweep` succeeds; the real `sweep_id` the server assigned. |
| `submitClientOpId` | `String?` | A UUID generated once, the first time `submit_survey_sweep` is attempted, and reused on every retry of that same submit — never regenerated, or the server's idempotency check on `client_op_id` breaks. |
| `gpsTrackChecksumSha256` | `String?` | SHA-256 of `gps_track.ndjson`, computed once at packaging time. |
| `luxLogChecksumSha256` | `String?` | SHA-256 of `lux_log.ndjson`, computed once at packaging time. |
| `captureConfigChecksumSha256` | `String?` | SHA-256 of `capture_config.json`, computed once at packaging time. |

### `LocalSurveyVideoSegmentEntity` — existing column, newly populated

`checksumSha256` already exists but is always written as `null`
(`VideoCaptureSession.kt:848`). `PackageSurveySessionUseCase` already computes
a SHA-256 per file inline (for the old local `manifest.json`) but throws it
away. Persist it instead via `dao.updateSegment(...)` so it is computed once,
not recomputed on every upload attempt/retry (clips can be up to 300 MiB).

### Migration

Room version 5→6. Follow the same approach already chosen for 4→5
(`fallbackToDestructiveMigration()`, no hand-written migration) — the project
already accepted that trade-off (no `exportSchema` history to verify a
hand-written migration against). Same data-loss caveat applies: installing
over an existing install drops `local_survey_session` and friends.

### Unchanged

`local_survey_plan` / `AssignedSurveyRoute` / `SurveyRepository` — the F03
"Khảo sát" tab's own data model is not touched by this plan (see Non-goals).

## 2. Network layer

New file `core/network/SweepsApi.kt`. Field names below are checked directly
against `luxmap_backend/docs/openapi/luxmap-v1.5.json` (`CreateSweepRequest`,
`SweepResponse`, `SubmitSweepRequest`, `SweepManifest`, `ClipManifest`,
`SurveyClipResponse`, `SurveyRawResponse`), not guessed from `mobile.pdf`'s
prose alone.

```kotlin
interface SweepsApi {
    @POST("api/v1/sweeps")
    suspend fun create(@Body body: CreateSweepRequestDto): SweepResponseDto

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

Notes confirmed from the openapi spec, not from `mobile.pdf`'s prose:
- `X-Content-SHA256` header is declared **only** on the clip PUT, not on the
  raw PUT. Do not send it on raw uploads (not required, do not invent).
- Raw body content type depends on `kind`: `gps_track`/`lux_log` →
  `application/x-ndjson`; `capture_config` → `application/json`. Build the
  `RequestBody` with the right media type per kind, not a single constant.
- Clip body content type: `video/mp4`.

### DTOs

- `CreateSweepRequestDto`: `work_order_id`, `client_op_id` (= local
  `sessionId`), `boot_session_id`, `elapsed_anchor_ns`, `utc_anchor`,
  `utc_uncertainty_ms`, `data_source` (= `"field"`), `started_elapsed_ns`.
- `SweepResponseDto`: only `sweep_id` is read (global `ignoreUnknownKeys` is
  already on — no need to declare every response field).
- `SubmitSweepRequestDto`: `client_op_id` (= `submitClientOpId`, a **new**
  UUID distinct from the sweep-creation one), `ended_elapsed_ns`,
  `manifest: SweepManifestDto`.
- `SweepManifestDto`: `clips: List<ClipManifestDto>`, `gps_hash`, `lux_hash`,
  `config_hash`.
- `ClipManifestDto`: `clip_no`, `sha256`.

Errors are not special-cased at this layer — `HttpException` propagates as
usual, to be interpreted by the sync handlers (§3), same as
`UploadWorkOrderEvidenceSyncHandler` already does with `errorCodeOrNull()`.

## 3. Sync layer

### Dependency chain (linear, not fan-out)

`SyncQueueEntity.dependsOnClientOpId` only supports a single parent per row —
there is no fan-in (one row waiting on several). Rather than extend that
shared mechanism (used by 2 existing work-order handlers too), this plan
chains every step linearly:

```
create_survey_sweep → upload_survey_clip(0) → upload_survey_clip(1) → ... →
upload_survey_raw(gps_track) → upload_survey_raw(lux_log) →
upload_survey_raw(capture_config) → submit_survey_sweep
```

Sequential upload is also kinder to weak 4G than trying to fan out several
large PUTs in parallel.

### 4 new `SyncOpHandler`s, in `feature/survey/data/sync/`

Each handler's payload carries only a lookup key; the handler re-reads Room
for current data, same convention as `UploadWorkOrderEvidenceSyncHandler`
(avoids staleness if payload was built before some field was written).

| `opType` | Payload | Behavior |
|---|---|---|
| `create_survey_sweep` | `{"session_id"}` | Reads session row, calls `SweepsApi.create()`, writes `serverSweepId` back onto the session row on success. |
| `upload_survey_clip` | `{"session_id","clip_no"}` | Reads `LocalSurveyVideoSegmentEntity` by `segmentIndex == clip_no` for file path + checksum. |
| `upload_survey_raw` | `{"session_id","kind"}` | Reads the matching file path + checksum column off the session row. |
| `submit_survey_sweep` | `{"session_id"}` | Builds the manifest from persisted checksums, uses the persisted `submitClientOpId`. |

### Error mapping (same pattern as `UploadWorkOrderEvidenceSyncHandler`)

- `409` → `SyncOpResult.Conflict` — only realistically hit on clip/raw PUT
  (content differs from what the server already has at that slot). Per
  CLAUDE.md's "don't auto-overwrite on conflict" rule: mark conflict, do not
  retry with different bytes, leave resolution to Manager/Web.
- `create_survey_sweep`/`submit_survey_sweep` retried with the same
  `client_op_id` return `200` idempotently per BE's description — no special
  409 handling expected on these two, but the generic mapping still applies
  defensively if it ever happens.
- `400/403/404/415` → `SyncOpResult.Failed`.
- `IOException`/`5xx` → `SyncOpResult.RetryLater`.

### Progress reporting (minimal, backward-compatible extension)

```kotlin
interface SyncOpHandler {
    val opType: String
    suspend fun handle(
        payloadJson: String,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): SyncOpResult
}
```

`SyncQueueProcessor.processQueuedOps()` gains an optional
`onRowProgress: (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit`
parameter, passed through to whichever handler is running for each row.
`SyncWorker` (background) passes nothing, default no-op — **zero behavior
change** for the existing background path or the 2 existing handlers (they
gain an unused parameter, nothing else changes).

## 4. Repository + UI

### A. Entry point: `WorkOrderDetailScreen` (replace the dead-end text)

`WorkOrderDetailScreen.kt:130`'s `taskKind == "survey"` branch currently shows
static text. Replace with a "Bắt đầu khảo sát" button (shown when
`allowedActions` contains `"start"`, same condition pattern as the repair
work order's start button):
1. Call `workOrderDetailRepository.start(workOrderId)` (already exists,
   reused as-is).
2. Generate a local `surveySweepId` (new UUID) — not fetched from
   `GET /survey-sweeps/planned` (not a real endpoint), just a local grouping
   key as today.
3. Navigate to `SurveyCapture` with both `workOrderId` and `surveySweepId`.

### B. Route change + threading `workOrderId` down to Room

`Routes.SurveyCapture.route`: `"survey/capture/{surveySweepId}"` →
`"survey/capture/{workOrderId}/{surveySweepId}"`.

Threaded through: `CaptureScreen` (new nav arg) → `CaptureViewModel.onStartRecording(surveySweepId, workOrderId)` →
`SurveyCaptureService` (new `EXTRA_WORK_ORDER_ID` intent extra) →
`startSessionInternal(...)` → `sessionDao.insertSession(LocalSurveySessionEntity(..., workOrderId = workOrderId, ...))`.

The F03 "Khảo sát" tab (`Routes.Survey`) is unchanged (see Non-goals) — it
stays a separate, not-yet-backed-by-a-real-endpoint UI path.

### C. `RealUploadRepository`

```kotlin
class RealUploadRepository @Inject constructor(
    private val sessionDao: SurveySessionDao,
    private val syncQueueManager: SyncQueueManager,
    private val syncQueueProcessor: SyncQueueProcessor,
) : UploadRepository {
    override fun uploadSession(sessionId: String): Flow<UploadProgress> = flow {
        val session = sessionDao.sessionById(sessionId) ?: error("Session $sessionId not found")
        if (session.syncState == null) enqueueUploadChain(session) // first "Nộp ngay"
        // else: "Nộp lại" after a failure — ops already queued, just re-process them
        emitAll(observeChainProgress(sessionId))
    }
}
```

Bound in `RepositoryModule.kt` in place of `FakeUploadRepository` — one line,
no UI/ViewModel change (per CLAUDE.md's API-layer rule 4).

### D. `SubmitScreen`/`SubmitUiState` rework

Current `SubmitUiState.Error` conflates failure and conflict, which the
design system explicitly says not to do (sync-status badge table: "không gộp
`failed` và `conflict`"). New shape:

```kotlin
sealed interface SubmitUiState {
    data object Idle : SubmitUiState
    data class Uploading(val bytesSent: Long, val totalBytes: Long) : SubmitUiState
    data object Done : SubmitUiState
    data class Failed(val message: String) : SubmitUiState   // retryable
    data class Conflict(val message: String) : SubmitUiState // not auto-retryable
}
```

Use `StatusBadge` with the existing sync-status color table instead of plain
`Text`; reuse `OfflineBanner` for mid-upload connectivity loss. `Failed` gets
a "Nộp lại" button that calls `onSubmit` again (resumes via the persisted
`syncState`/queued rows, does not restart from scratch).

## 5. Error handling details

- **409 on clip/raw PUT** → `Conflict`, no auto-retry UI, surfaced to the
  Field Engineer as something to resolve via Web/Manager.
- **409 on create/submit** → not expected per BE's described idempotent
  replay behavior (`200` on identical retry); generic mapping still applies
  defensively.
- **Network loss mid-PUT** → `RetryLater`. Important clarification: the real
  API has **no byte-range resume** within a single PUT (no `Range` header
  support described). "Resumable" here means **file-level** resume — already
  `done` files/clips keep their status, only the interrupted one restarts
  from byte 0. This is a deliberate reading of CLAUDE.md's "upload phải
  resume được theo chunk" line — documented here so it is not mistaken for
  true byte-level resume, which this backend does not support.
- **App killed mid-upload** — op status lives in Room (`sync_queue`), not
  memory. `SyncWorker`'s own background run picks up any row still `queued`
  — no separate recovery code needed.
- **Validation before "Nộp ngay" is enabled** — `SubmitViewModel` must check
  `session.recordingState == "packaged"` before allowing submit (today's fake
  screen checks nothing). A session that never finished packaging must not
  reach the upload chain.

## 6. Testing

Follows the project's existing split (JUnit+MockK for pure logic,
"real-device checklist" — logged in `contract-drift.md` — for anything
Android-system-dependent that cannot be mocked):

**Unit test (JUnit + MockK):**
- DTO JSON shape (`CreateSweepRequestDto`/`SubmitSweepRequestDto`), fixed-
  string assertions, same style as `CaptureConfigWriterTest`.
- Each of the 4 `SyncOpHandler`s: mock `SweepsApi` + DAOs, verify payload
  construction and HTTP-code → `SyncOpResult` mapping (especially 409 →
  `Conflict`).
- `RealUploadRepository`'s chain-building logic: correct `opType`/
  `dependsOnClientOpId` ordering, and the "already enqueued, don't re-enqueue"
  branch.
- `PackageSurveySessionUseCase`'s new checksum-persisting behavior (extends
  the existing test file).

**MockWebServer:** `SweepsApi` request shape — headers (`X-Content-SHA256`
present only on clip PUT), path, binary body — matches a fake server.

**Real-device checklist (not automatable):** large-file PUT over real
4G/weak signal, `WorkManager` resuming correctly after the app process is
killed mid-upload, full happy path from Home → Work Order (`taskKind
= survey`) → record → submit → data visible server-side.

Room migration 5→6: same as 4→5, no migration test (destructive, accepted
risk, logged again in `contract-drift.md` when implemented).
