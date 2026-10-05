# F09 — Work order detail & navigation (design)

Status: approved by project owner (2026-10-05), ready for implementation planning.

## Why

`HomeScreen.onOpenWorkOrder` currently only shows a "not implemented" snackbar. The project owner asked
to implement F09 ("Chi tiết lệnh & điều hướng") so a Field Engineer can open a work order from "Việc hôm
nay" and see enough detail to act on it: what the order is, where each fault is, and a way to start work.

## Confirmed against the real backend (2026-10-05)

- `GET /api/v1/work-orders/{id}` already exists (`WorkOrdersController.Detail`), policy `ReadWorkOrders`
  (includes FieldEngineer). Returns `WorkOrderDetail` — all `WorkOrderItem` fields plus `segment_ids[]`,
  `note`, `review_note`, `report_note`, `materials_note`, `materials_used`, `created_by`, `assigned_at`,
  `started_at`, `completed_at`, `closed_at`, `assignee_eligible`, `allowed_actions[]`, `faults[]`.
- `faults[]` items (`WorkOrderFaultDetail`): `fault_id`, `pole_id`, `segment_id`, `location{lat,lng}`,
  `fault_type`, `fault_status`, `severity`, `inspection_outcome`.
- `severity` is a real enum on `Fault`: `low | medium | high | critical` (`DomainEnums.cs`) — this is the
  field that should drive the priority badge, not `priority_score` (see `docs/contract-drift.md`).
- `allowed_actions[]` is computed server-side by `WorkOrderRules.AllowedActions`. For role FieldEngineer it
  can only ever be a subset of `["start", "complete"]`, further filtered by `wo_status`:
  `start` only when `Assigned`, `complete` only when `InProgress`. Every other action string
  (`assign`/`unassign`/`edit`/`verify`/`return`/`cancel`/`follow_up`) is Manager-only and will never appear
  for a FieldEngineer's own work order — mobile does not need to handle those.
- `POST /api/v1/work-orders/{id}/start` takes no request body (`service.Act(id, "start", null, default, ct)`).
- `POST /api/v1/work-orders/{id}/complete` takes `report_note`, `fault_outcomes[]`, `materials_used` and
  (per the evidence API, confirmed earlier) requires an `after` photo for a repair ticket — this is F10
  (`feature/workorder/ui/completion/`), out of scope for this design.
- `task_kind = survey` orders come back from the same detail endpoint but carry `segment_ids[]` instead of
  `faults[]` (no per-fault lat/lng) — confirmed out of scope for this design (see Decisions).

## Decisions (confirmed with project owner 2026-10-05)

1. **Scope is `task_kind` in `{inspection, repair}` only.** A `survey` order opened through this screen
   shows a short message pointing to the Khảo sát tab instead of trying to render a route from
   `segment_ids[]` — that belongs to F03's planning flow, not F09.
2. **One "Điều hướng" button per fault**, not one for the whole order. A work order can carry faults on
   different poles; a single button to "the first fault" would be wrong whenever there is more than one.
3. **"Bắt đầu" calls the real `POST /{id}/start`** now, not just a placeholder — the API is simple
   (no request body) and already confirmed, so there is no reason to ship it inert.
4. **"Hoàn thành" (complete) is explicitly NOT part of this task.** It needs its own form (report note,
   materials used, per-fault inspection outcome, and for a repair ticket a mandatory `after` photo via the
   evidence API) — that is F10's job, a separate design.

## Data layer

### DTOs (`feature/workorder/data/dto/`)

`WorkOrderDetailDto` — matches `WorkOrderDetail` field-for-field (snake_case via `@SerialName`, same
conventions as `WorkOrderItemDto`). Nullable exactly where the backend record marks it nullable
(`DateOnly?`/`DateTime?`/`string?` fields all map to nullable Kotlin types). `allowed_actions` and `faults`
are non-null arrays (backend marks them `required`).

`WorkOrderFaultDetailDto` — `fault_id`, `pole_id` (nullable), `segment_id` (nullable), `location{lat,lng}`,
`fault_type`, `fault_status`, `severity`, `inspection_outcome` (nullable — only set once an inspection has
actually happened).

Not carried into the domain model: `assignee_eligible` (Manager-only re-assign concern, irrelevant to a
FieldEngineer viewing their own order) and `created_by` (not shown).

### Domain models (`feature/workorder/data/`)

```kotlin
data class WorkOrderDetail(
    val workOrderId: String,
    val title: String,
    val woStatus: String,
    val taskKind: String,
    val dueDate: String?,
    val scheduledDate: String?,
    val note: String?,
    val allowedActions: List<String>,
    val faults: List<WorkOrderFaultDetail>,
)

data class WorkOrderFaultDetail(
    val faultId: String,
    val location: Location,
    val faultType: String,
    val faultStatus: String,
    val severity: String,
    val inspectionOutcome: String?,
)

data class Location(val lat: Double, val lng: Double)
```

(`WorkOrderDetail` only carries what F09's UI needs — `segment_ids`/`review_note`/`report_note`/
`materials_note`/`materials_used`/`assigned_at`/`started_at`/`completed_at`/`closed_at` are read by the DTO
but not promoted to the domain model yet, same "trim to what the screen needs" convention as
`WorkOrderSummaryItem`. F10 can read them from the same DTO later without a mobile schema change.)

### Badge/label mapping (`core/theme/Color.kt`, same file as the other `WorkOrderXxx` mappings)

- `severity` → reuse **`WorkOrderPriority`** (already has label + color: Thấp/Bình thường/Cao/Khẩn) via a
  new `severityFromWire(value): WorkOrderPriority` (`"low"→LOW`, `"medium"→NORMAL`, `"high"→HIGH`,
  `"critical"→URGENT`, unknown→NORMAL). No new badge/color is introduced — this is the real use of the
  priority badge flagged in `docs/contract-drift.md`.
- `fault_type`, `fault_status`, `inspection_outcome` → plain Vietnamese labels only (no color badge — the
  Design System does not define one for these, and inventing one is out of scope here).

### Repository (`feature/workorder/data/`)

```kotlin
interface WorkOrderDetailRepository {
    fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?>
    suspend fun start(workOrderId: String): Result<Unit>
}
```

Same shape as `PoleDetailRepository`: null = backend 404 (not found / out of commune scope) → UI `Empty`,
distinct from a network/parse `Error`. `FakeWorkOrderDetailRepository` for previewing; `RealWorkOrderDetailRepository`
calls `WorkOrdersApi.detail()`/`.start()` once wired.

### API (`core/network/WorkOrdersApi.kt`)

Add to the existing interface (currently only `list`):

```kotlin
@GET("api/v1/work-orders/{id}")
suspend fun detail(@Path("id") id: String): WorkOrderDetailDto

@POST("api/v1/work-orders/{id}/start")
suspend fun start(@Path("id") id: String)
```

## UI layer (`feature/workorder/ui/detail/`)

`WorkOrderDetailViewModel` — `SavedStateHandle` arg (`workOrderId`, same pattern as `PoleDetailViewModel`),
4-state `WorkOrderDetailUiState` (Loading/Success/Empty/Error), plus a separate `isStarting: Boolean` /
start-error surface for the action button (distinct from the page-level load state — starting the order
must not blank out the page the user is looking at).

`WorkOrderDetailScreen`:
- Header: `work_order_id`, `WorkOrderStatus` badge, `WorkOrderTaskKind` badge (both already exist, reused
  as-is from the F02 work).
- Due/scheduled date, `note` if present.
- `task_kind == "survey"` branch: short message ("Lệnh khảo sát — xem trong tab Khảo sát"), nothing else
  rendered (per Decision 1).
- Otherwise: fault list, one row per `WorkOrderFaultDetail` — severity badge (`WorkOrderPriority`),
  fault_type label, fault_status label, inspection_outcome label if present, and its own "Điều hướng"
  button.
- "Bắt đầu" button, visible only when `"start" in allowedActions`, calls `viewModel.start()`.

### Opening Maps (`core/common/`, new — first occurrence of this pattern in the codebase)

A small helper, `fun openInMaps(context: Context, lat: Double, lng: Double)`, builds a `geo:` Intent and
starts it via `Intent.createChooser` (per CLAUDE.md: "nút Điều hướng chỉ mở Google Maps hoặc ứng dụng bản
đồ ngoài bằng Intent" — no turn-by-turn built in-app).

## Navigation

`Routes.WorkOrderDetail` (`"work-order/{workOrderId}"`, `createRoute(workOrderId)`), wired in `NavGraph.kt`.
`HomeScreen.onOpenWorkOrder` (currently `showNotImplemented`) becomes a real `navController.navigate(...)`
call in `NavGraph.kt`'s `HomeRoute` composable call site.

## Testing plan

TDD, pure-logic pieces first:
- `severityFromWire` → `WorkOrderPriority` mapping (4 known values + unknown fallback).
- Fault type/status/inspection-outcome wire→label functions.
- `WorkOrderDetailDto` → `WorkOrderDetail` mapping (field-for-field, including the survey case with empty
  `faults`).
- `RealWorkOrderDetailRepository`: 404 → `null`; other HTTP/parse errors → propagate (caught by ViewModel,
  same as `PoleDetailViewModel`/`HomeViewModel` already do).
- `WorkOrderDetailViewModel`: Loading→Success, Loading→Empty (404), Loading→Error, and `start()` success
  (re-fetches detail so the button disappears once `wo_status` moves to `in_progress`) / failure (shows an
  error without discarding the loaded detail).

No Compose UI test planned (matches the rest of this codebase's current coverage — only pure functions and
ViewModels get automated tests today; the screen itself gets a real-device check before calling the task
done, same as every other screen built this session).

## Explicitly out of scope

- `task_kind = survey` detail rendering (Decision 1).
- "Hoàn thành" (complete) action, evidence upload, materials/report-note form — F10.
- `GET /work-orders/{id}/poles` (F09's "poles on the segment" list, confirmed separately in
  `docs/contract-drift.md`, not part of this pass — the fault list above is a different, smaller view:
  only the faults this specific order covers, not every pole on the segment).
- Re-assign / cancel / verify / return / follow-up actions — Manager-only, never in a FieldEngineer's
  `allowed_actions`.
