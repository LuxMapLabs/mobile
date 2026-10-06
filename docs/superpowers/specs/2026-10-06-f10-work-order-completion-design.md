# F10 — Work order completion (design)

Status: approved by project owner (2026-10-06), ready for implementation planning.

## Why

F09 (`docs/superpowers/specs/2026-10-05-work-order-detail-design.md`) lets a Field Engineer open an
Inspection or Repair work order and start it, but `"complete" in allowed_actions` has no screen behind it
yet. The project owner asked to design F10 ("Cập nhật tiến độ & nghiệm thu") so a Field Engineer can close
out an Inspection or Repair order from the field.

## Confirmed against the real backend (2026-10-06)

Read directly from `luxmap_backend` (dev branch): `WorkOrdersController.cs`, `WorkOrderService.cs`,
`WorkOrderRules.cs`, `WorkOrderEvidence.cs`, `Entities/RepairEvidence.cs`, `DomainEnums.cs`,
`Entities/WorkOrder.cs`, `tests/LuxMap.Api.Tests/WorkOrderTests.cs`.

- `POST /api/v1/work-orders/{id}/complete` — body `{ report_note, materials_used?, fault_outcomes? }`.
  `report_note` is required and must be ≥10 characters (same length rule the service applies to `return`
  complete notes generally — `WorkOrderService.Act`). `materials_used` is optional free text, blank stored
  as `null`. `fault_outcomes` is required, and must contain **exactly** the order's linked fault ids (no
  more, no fewer) each with a non-null `outcome`, **only when `task_kind == inspection` and the order has
  linked faults** — otherwise sending it at all is `400 VALIDATION_FAILED`.
- A FieldEngineer's `allowed_actions` only ever contains `start`/`complete` (`WorkOrderRules.AllowedActions`),
  and `complete` only appears when `wo_status == in_progress` (`WorkOrderRules.Allows`). No other action
  (`verify`/`return`/`cancel`/etc.) is reachable by this role — confirmed already in the F09 spec, repeated
  here because F10 is the other action FieldEngineer actually calls.
- **Repair cannot complete without an `after` photo** — the server itself enforces this
  (`WorkOrderService.Act`, `409 AFTER_EVIDENCE_REQUIRED` if no `RepairEvidence` row with
  `Kind == After` exists for the order). This is a real server-side gate, not just a mobile UX rule; mobile
  must still check it client-side first to avoid an avoidable round trip, but the server is the backstop.
- `POST /api/v1/work-orders/{id}/evidence` (multipart) — `file` (validated by JPEG magic bytes `FF D8 FF`,
  not by filename/content-type — `JpegMagicBytes.cs`), `kind` (`before`/`after` allowed on a repair,
  `observation` only on an inspection — `WorkOrderEvidenceService.AllowedKinds`), `captured_at` (complete
  ISO 8601 date-time, not just a time), `lat`/`lng` (±90/±180), `client_op_id` (optional UUID; replaying the
  same key from the same user returns the existing photo, `200`, instead of creating a second one, `201`).
  Only the assignee, only while `wo_status == in_progress` (`403`/`409` otherwise).
- Evidence upload is a single JPEG, max 16 MB (`WorkOrderEvidenceService.MaxUploadBytes`) — this is not the
  chunked/resumable upload CLAUDE.md requires for a survey video session; that rule is specific to the much
  larger video+track package, not a single evidence photo.
- `WorkOrderDetail` already carries `review_note`/`report_note`/`materials_note`/`materials_used` in the DTO
  (confirmed in the F09 spec) but F09 did not promote them to the domain model. F10 needs `review_note`
  (Manager's reason when returning a `done` order to `in_progress` via `POST /{id}/return`) and the
  engineer's own previous `report_note`/`materials_used` to prefill the form on a resubmission after a
  return — both get promoted now.
- Enum wire values (`LuxMap.Shared.Contracts.Enums.DomainEnums`, frozen contract v1.1 §1):
  `inspection_outcome`: `fault_present | fault_absent | inconclusive`. `task_kind`: `inspection | repair |
  survey` (`Entities/WorkOrder.cs`). Evidence `kind`: `before | after | observation`.

## Decisions (confirmed with project owner 2026-10-06)

1. **One screen, branching on `task_kind`** — `WorkOrderCompletionScreen`, not two separate screens/routes.
   Both branches submit through the same `complete` action and share `report_note`/loading/error state; only
   the form content differs (fault-outcome picker for Inspection vs. a mandatory "after" photo for Repair).
   Mirrors how F09 already branches its single screen on `task_kind == "survey"`.
2. **No "update progress" sub-feature.** `WorkOrderRules.AllowedActions` only ever gives a FieldEngineer
   `start`/`complete` — there is no intermediate action for them between those two states (`PATCH /{id}` is
   `ManageWorkOrders`-only). The folder name "Cập nhật tiến độ & nghiệm thu" in CLAUDE.md does not map to a
   real intermediate API; F10 is scoped to exactly what the backend exposes: evidence upload (any time while
   `in_progress`) plus the final `complete` call.
3. **Repair evidence: "after" only, no "before" capture in this screen's scope.** The backend allows
   `kind=before` too, but only `after` is required to complete, and the project owner chose to keep F10's
   first version to the mandatory minimum.
4. **Offline-first via a new, minimal, shared `core/sync`** — not a one-off local retry button. Neither
   `sync_queue`, `SyncQueueManager`, `SyncWorker`, nor the WorkManager Gradle dependency exist in the
   codebase yet (confirmed: `AppDatabase.kt` only declares `survey` entities; no `androidx.work` reference
   anywhere in `app/build.gradle.kts` or source). Because CLAUDE.md lists F10 among the screens that must be
   offline-first with background sync (not a manual-retry fallback), and because F03/F06/F11 will need the
   same mechanism later, F10 builds the minimum generic version now rather than a throwaway F10-only queue.
   WorkManager is already on the approved stack in CLAUDE.md — adding the Gradle dependency is not a new
   library decision, just wiring in what was already planned.

## `core/sync` foundation

New module, generic — no work-order knowledge inside it.

### `SyncQueueEntity` (Room, table `sync_queue`)

```kotlin
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientOpId: String,
    val opType: String,
    val payloadJson: String,
    val dependsOnClientOpId: String?,
    val status: String, // queued | syncing | done | failed | conflict
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

### `SyncQueueManager`

`enqueue(opType, payloadJson, clientOpId, dependsOnClientOpId): Unit` — inserts the row, then calls
`WorkManager.enqueueUniqueWork("sync_queue", ExistingWorkPolicy.KEEP, ...)` to trigger `SyncWorker`. Does not
call any API itself.

`payloadJson` is a **pointer, not a copy of the business data** — e.g. `{"work_order_id": "WO-1042"}` for
`complete_work_order`, `{"client_op_id": "..."}` for `upload_work_order_evidence`. The handler reads the
actual fields (report note, fault outcomes, file path...) from the feature's own Room table
(`local_work_order_completion`/`local_work_order_evidence`) by that id. This keeps `sync_queue` generic and
avoids the same data existing in two shapes that could drift apart.

### `SyncOpHandler` (interface, Hilt `Set<SyncOpHandler>` multibinding)

```kotlin
interface SyncOpHandler {
    val opType: String
    suspend fun handle(payloadJson: String): SyncOpResult
}

sealed interface SyncOpResult {
    data object Done : SyncOpResult
    data object RetryLater : SyncOpResult // network/5xx, or a dependency-shaped 409 that should resolve itself
    data class Failed(val message: String) : SyncOpResult // terminal 4xx — do not retry automatically
    data class Conflict(val message: String) : SyncOpResult // real state conflict — keep local, do not overwrite
}
```

Each feature registers its own handlers; `core/sync` never branches on `opType` string values itself beyond
looking the handler up by name.

### `SyncWorker` (`CoroutineWorker`, constraint `NetworkType.CONNECTED`)

Reads `sync_queue` rows with `status == "queued"`, ordered by `createdAt`. Skips a row whose
`dependsOnClientOpId` points at a row that is not yet `status == "done"`. For the rest, looks up the handler
by `opType` and applies the result: `Done` → `status = "done"`; `RetryLater` → leave `queued`,
`attemptCount += 1` (WorkManager's own backoff policy covers the retry delay); `Failed`/`Conflict` → set
`status` accordingly and stop retrying automatically. Runs once immediately after any `enqueue`, and on a
periodic fallback (CLAUDE.md's existing "constraint mạng/Wi-Fi + retry/exponential backoff" requirement) in
case the app was killed before the one-shot run completed.

## Data layer (`feature/workorder/`)

### DTOs (`data/dto/`)

```kotlin
@Serializable
data class CompleteWorkOrderRequestDto(
    @SerialName("report_note") val reportNote: String,
    @SerialName("materials_used") val materialsUsed: String? = null,
    @SerialName("fault_outcomes") val faultOutcomes: List<FaultOutcomeRequestDto>? = null,
)

@Serializable
data class FaultOutcomeRequestDto(@SerialName("fault_id") val faultId: String, val outcome: String)

@Serializable
data class EvidenceItemDto(
    @SerialName("evidence_id") val evidenceId: String,
    val kind: String,
    @SerialName("captured_at") val capturedAt: String,
    val lat: Double,
    val lng: Double,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("original_url") val originalUrl: String,
)
```

`complete` responds with `WorkOrderDetailDto` (the backend action handler ends with `return await
Detail(id, ct)`) — reuses the existing F09 DTO, no new response type.

### `WorkOrderDetail` domain model — promote 3 fields already read by the DTO but unused since F09

Add `reviewNote: String?`, `reportNote: String?`, `materialsUsed: String?` to the domain model built in F09
(`WorkOrderDetailDto.toWorkOrderDetail()` already reads these off the wire; F09 just didn't carry them
forward).

### `WorkOrdersApi` additions

```kotlin
@POST("api/v1/work-orders/{id}/complete")
suspend fun complete(@Path("id") id: String, @Body body: CompleteWorkOrderRequestDto): WorkOrderDetailDto

@Multipart
@POST("api/v1/work-orders/{id}/evidence")
suspend fun uploadEvidence(
    @Path("id") id: String,
    @Part file: MultipartBody.Part,
    @Part("kind") kind: RequestBody,
    @Part("captured_at") capturedAt: RequestBody,
    @Part("lat") lat: RequestBody,
    @Part("lng") lng: RequestBody,
    @Part("client_op_id") clientOpId: RequestBody,
): EvidenceItemDto
```

### Room entities (`data/entity/`)

```kotlin
@Entity(tableName = "local_work_order_evidence")
data class LocalWorkOrderEvidenceEntity(
    @PrimaryKey val clientOpId: String,
    val workOrderId: String,
    val kind: String, // "after" only, within this screen's scope
    val filePath: String,
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
    val uploadStatus: String, // pending | synced
)

@Entity(tableName = "local_work_order_completion")
data class LocalWorkOrderCompletionEntity(
    @PrimaryKey val workOrderId: String,
    val reportNote: String,
    val materialsUsed: String?,
    val faultOutcomesJson: String?, // non-null only when task_kind == inspection
    val clientOpId: String,
    val submitStatus: String, // pending | synced | failed | conflict
)
```

Kept separate from `sync_queue`: these two tables are the business record of what the engineer actually
entered/captured, read directly by the UI for an instant "Đã lưu trên thiết bị" state; `sync_queue` is a
purely mechanical outbox with no business meaning, matching the Repository Pattern note in CLAUDE.md
("đọc/ghi Room trước, đẩy thay đổi cần đồng bộ vào sync_queue").

### `WorkOrderCompletionRepository` (Fake + Real, same pattern as every other feature repository)

```kotlin
interface WorkOrderCompletionRepository {
    fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?>
    fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?>
    suspend fun captureAfterEvidence(workOrderId: String, filePath: String, lat: Double, lng: Double, capturedAt: Instant)
    suspend fun submitCompletion(workOrderId: String, taskKind: String, reportNote: String, materialsUsed: String?, faultOutcomes: List<FaultOutcome>?)
}
```

`captureAfterEvidence` writes `local_work_order_evidence` then calls
`syncQueueManager.enqueue("upload_work_order_evidence", ...)`. `submitCompletion` writes
`local_work_order_completion` then enqueues `"complete_work_order"` with `dependsOnClientOpId` set to the
evidence row's `clientOpId` when `taskKind == "repair"`, `null` when `"inspection"`.

Two new `SyncOpHandler`s in `feature/workorder/data/sync/`: `UploadWorkOrderEvidenceSyncHandler` (calls
`WorkOrdersApi.uploadEvidence`, marks the local evidence row `synced` on success) and
`CompleteWorkOrderSyncHandler` (calls `WorkOrdersApi.complete`, marks the local completion row `synced` on
success; maps `409 AFTER_EVIDENCE_REQUIRED` to `SyncOpResult.RetryLater` rather than `Failed`, since it is
expected to resolve itself once the evidence op finishes — but still caps retries so a permanently stuck
case does not retry silently forever).

## UI layer (`feature/workorder/ui/completion/`)

### `WorkOrderCompletionScreen`

4-state `WorkOrderCompletionUiState` (Loading/Success/Empty/Error), same convention as every other screen.
`Success` carries `detail: WorkOrderDetail`, `localEvidence`, `localCompletion`, `isSubmitting`,
`submitError`.

- If `detail.reviewNote` is non-blank (the order cycled `done → in_progress` through a Manager `return`):
  show a banner "Manager yêu cầu bổ sung: {reviewNote}" at the top, and prefill `report_note`/
  `materials_used` from `detail.reportNote`/`detail.materialsUsed` so the engineer edits rather than
  re-enters from scratch.
- **`RepairCompletionSection`**: thumbnail of the captured "after" photo (from `localEvidence.filePath`) or
  a "Chụp ảnh sau" button when none exists yet; `report_note` field (required, ≥10 chars); `materials_used`
  field (optional). "Nộp" is disabled until a local evidence row exists **and** `report_note` is ≥10 chars.
- **`InspectionCompletionSection`**: one row per `detail.faults` item with a 3-way `inspection_outcome`
  picker (`fault_present`/`fault_absent`/`inconclusive`); `report_note` field. "Nộp" is disabled until every
  fault has a selected outcome **and** `report_note` is ≥10 chars — mirrors the server's exact validation
  (missing one fault's outcome is a `400`). If `detail.faults` is empty (an inspection order with no linked
  faults — same edge case F09 already handles for Repair), the outcome picker list is empty and `submitCompletion`
  sends `fault_outcomes = null`; only `report_note` gates "Nộp" in that case, matching the backend's own rule
  that `fault_outcomes` must be absent when there are no linked faults.
- "Nộp" calls `viewModel.submitCompletion()`, which writes Room and enqueues sync immediately — does not
  wait for the network. The screen then shows the existing sync-status badge styling (Chờ đồng bộ/Đang đồng
  bộ/Đồng bộ lỗi/Xung đột/Đã đồng bộ from the Design System) rather than treating "offline" as an `Error`.

### `EvidenceCaptureScreen` (new, separate from survey capture)

A plain CameraX `ImageCapture` screen — explicitly **not** reusing `ExposureLockController`/
`SegmentedVideoRecorder` (those exist for night survey video with locked exposure; an evidence photo is a
daytime still of a just-repaired lamp, auto-exposure is correct here). On shutter: takes one JPEG to
`filesDir/evidence/{workOrderId}/{clientOpId}.jpg`, takes one Fused Location fix (a single point, not a
continuous track — this is not a survey session), stamps `captured_at` with the device clock, and calls
`repository.captureAfterEvidence(...)` directly from its own small `EvidenceCaptureViewModel`, then
navigates back. No shared state is passed through nav args; `WorkOrderCompletionScreen` just re-renders once
the local evidence row appears via its `Flow`.

### Navigation

`Routes.WorkOrderCompletion` (`"work-order/{workOrderId}/complete"`) and
`Routes.WorkOrderEvidenceCapture` (`"work-order/{workOrderId}/complete/evidence"`, pushed only from the
Repair branch). `WorkOrderDetailScreen` adds a "Hoàn thành" button next to "Bắt đầu", shown when
`"complete" in detail.allowedActions`, navigating to `WorkOrderCompletion`.

## Error handling

| Case | Handling |
|---|---|
| `409 AFTER_EVIDENCE_REQUIRED` on `complete` | `SyncOpResult.RetryLater` (self-resolves once evidence syncs); capped attempt count before surfacing as stuck |
| `404 WORK_ORDER_NOT_FOUND` | `Failed` — order reassigned/out of scope; prompt reload, do not retry |
| `400 VALIDATION_FAILED` (fault_outcomes mismatch — e.g. a fault was added server-side after the screen loaded) | `Failed`; prompt reload of the work order detail |
| `415 UNSUPPORTED_IMAGE_FORMAT` | `Failed`; prompt retake (should not normally happen — CameraX produces real JPEGs) |
| `409` state-conflict (order completed from another device) | `Conflict`; keep local record, do not auto-overwrite, per CLAUDE.md C5 |
| Retry of a not-yet-synced evidence capture | delete the local row + file, capture again |
| Retry of an already-synced evidence capture | capture a new row; server allows multiple `after` photos, only checks that at least one exists |

## Testing plan

Pure-logic and ViewModel tests only (no Compose UI tests — matches this codebase's existing convention;
screens get a real-device check instead):

- `SyncQueueManager`: enqueue writes the row correctly; a row whose dependency is not `done` is skipped by
  `SyncWorker`.
- `UploadWorkOrderEvidenceSyncHandler` / `CompleteWorkOrderSyncHandler`: map each HTTP outcome (success,
  400, 404, 409 evidence-required, 409 state-conflict) to the right `SyncOpResult`.
- `WorkOrderCompletionViewModel`: "Nộp" enablement logic for both branches; Loading→Success/Empty/Error;
  review-note banner + prefill when `reviewNote` is present.
- Repository Fake/Real: 404 → null-equivalent handling, consistent with `WorkOrderDetailRepository`.
- Real-device check before calling the task done: capture a photo outdoors, submit while offline and
  confirm `SyncWorker` picks it up once connectivity returns, exercise a `return` → resubmit cycle.

## Explicitly out of scope

- "Before" photo capture for Repair (Decision 3).
- Any "update progress" mid-point screen (Decision 2 — no such API exists for FieldEngineer).
- Manager-side actions (`verify`/`return`/`cancel`/`follow_up`) — never in a FieldEngineer's
  `allowed_actions`.
- `task_kind = survey` — unrelated to this screen, still handled entirely by F09's redirect message.
- A generic `sync_queue` UI/inbox screen (F13) — this spec only builds the underlying mechanism; F13's own
  listing screen is a separate task reusing the same table.
- Chunked/resumable upload for the evidence photo (not needed — see "Confirmed against the real backend").
