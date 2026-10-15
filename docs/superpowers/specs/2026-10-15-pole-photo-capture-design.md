# Pole Photo Capture — Design Spec

**Date:** 2026-10-15
**Status:** Approved by user in chat, pending BE endpoint (user has already asked BE to build it)

## 1. Background and confirmed decision

The capstone mentor (GVHD), treated as equivalent to "chủ dự án" for business-decision purposes, asked to prioritize a new per-pole capture flow for a demo. This runs in **parallel** with the existing continuous-video-per-route survey flow (confirmed 2026-09-25, unchanged) — it does not replace it. The short-video part of this new flow is deferred; this spec covers **photo capture only**.

Recorded in `CLAUDE.md` under "Quyết định nghiệp vụ mới đã chốt (2026-10-15)".

At each pole on an assigned survey route, the Field Engineer takes one photo of that pole and uploads it for the server/AI to scan and classify. This is for a quick demo to the mentor, so scope is deliberately tight (see "Out of scope").

## 2. Scope (v1)

In scope: pick an assigned survey work order → pick a pole from that work order's pole list → capture one photo for that pole, with a GPS fix attached → save locally → repeat for the next pole → return to the hub when done.

**Known, accepted gap for v1 (confirmed with user 2026-10-15, not an oversight):** the photo is taken by the phone's own system camera app (whatever auto-exposure it uses), **not** a real manual exposure lock. `CLAUDE.md`'s hard rule ("không cho bắt đầu khảo sát nếu chưa khoá exposure thật") stays in force for the existing continuous-video flow, unchanged — this v1 photo flow is a deliberate, recorded exception, because a real lock means writing a manual Camera2 still-capture pipeline on the scale of the 992-line `VideoCaptureSession.kt`, which does not fit the demo timeline. Tracked as technical debt to fix after the demo.

Out of scope (explicitly, so no one assumes it's done):
- Short video capture (the other half of the mentor's request — separate follow-up task).
- Editing/overriding the AI classification result per pole (blocked on BE; same gap already recorded in `CLAUDE.md` for the existing AI Confirmation screen).
- Folding this photo's result into the existing `AiConfirmationScreen` (that screen is driven by `/sweeps/{id}/results`, a different data source — no merge in v1).
- Retake/delete of an already-captured photo (capture is one-shot per pole for v1).
- The full F03 readiness checklist (GPS accuracy/storage/battery) — see section 4 for what v1 checks instead.

## 3. Navigation flow

Survey Hub → new button "Chụp ảnh cột đèn" on an assigned survey work order card, next to the existing "Kiểm tra sẵn sàng" button → **Pole List screen** → tap a pole → **Pole Photo Capture screen** → on capture, save and return to Pole List with that pole marked "đã chụp" → "Hoàn tất" button returns to Survey Hub.

**Third correction:** the Pole List screen is not "already used by F09" as the first draft claimed — checked `WorkOrderDetailScreen`/`WorkOrdersApi` directly, F09 only carries a single nullable `pole_id` per work order (one fault's pole, for `inspection`/`repair`), never a list. `GET /api/v1/work-orders/{id}/poles` is a real, already-implemented BE endpoint (WO-12 in `luxmap_backend`'s `contract-drift.md`) that mobile has simply never called yet. This is new mobile-side integration, not reuse of an existing call — but since the endpoint already exists and is confirmed, this part needs **no Fake repository**, it can be real from the first commit (see section 6).

New routes added to `Routes.kt`:
- `survey/pole-photos/{workOrderId}` — Pole List screen.
- `survey/pole-photos/{workOrderId}/capture/{poleId}` — Capture screen.

## 4. Pole Photo Capture screen

**Second correction:** reuses an even closer existing precedent found while planning — `feature/workorder/ui/completion/EvidenceCaptureScreen.kt` + `EvidenceCaptureViewModel.kt` (F10's "ảnh sau" repair-evidence photo). That screen already does exactly this shape of work (one photo, GPS-tagged, written to Room) using the **system camera app via an implicit intent** (`ActivityResultContracts.TakePicture()`), not CameraX:

- Launch `TakePicture()` with a `FileProvider` URI pointing at an app-specific output file — no CameraX, no camera preview UI to build, no `CAMERA` runtime permission to request (the system camera app handles that itself).
- On success, call `LocationTracker.getCurrentLocation()` (existing, single GPS fix, returns `null` on no permission/no fix) and pass the file path + lat/lng + timestamp to the repository.
- No EXIF writing — matching F10's precedent exactly, GPS/timestamp are stored in the Room row, not burned into the JPEG. (This drops the `PolePhotoExifWriter`/`CaptureMetadataWriter` idea from the first two drafts of this spec — the app has no EXIF-writing code anywhere, and the one existing single-photo flow doesn't need it either.)

This replaces the CameraX-auto-exposure approach from the previous revision of this section — same v1 exception (no manual exposure lock), but now by reusing a proven, already-shipped screen pattern instead of writing new camera integration code. Smaller and lower-risk than either earlier draft.

Readiness check for this screen: only "GPS fix valid" gates showing a result — same as F10, there's no separate pre-check screen, the location permission prompt is part of the capture flow itself.

On capture: `onPhotoCaptured` fires after the system camera returns, gets the GPS fix, then writes one `LocalPolePhotoEntity` row. No sync-queue entry in v1 — see section 7 for why.

## 5. Local storage

New Room entity `local_pole_photo` (table name chosen by mobile per `CLAUDE.md`'s C8.5 naming authority — no BE confirmation needed for local-only names). No `syncStatus` field in v1 — see section 7 for why; a row existing for a `poleId` means "captured and saved", full stop:

```kotlin
@Entity(tableName = "local_pole_photo")
data class LocalPolePhotoEntity(
    @PrimaryKey val photoId: String,       // client-generated UUID
    val workOrderId: String,
    val poleId: String,
    val filePath: String,                  // app-specific storage, not Room
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
)
```

A DAO (`PolePhotoDao`) follows the existing pattern used by other local tables: insert, query by `workOrderId` (to render the Pole List screen's "đã chụp" marks).

## 6. Pole List data source — real from day one

New Retrofit method on `WorkOrdersApi`:

```kotlin
@GET("api/v1/work-orders/{id}/poles")
suspend fun poles(
    @Path("id") id: String,
    @Query("page") page: Int = 1,
    @Query("page_size") pageSize: Int = 200,
): PagedResultDto<PoleListItemDto>
```

`PoleListItemDto` only declares the fields this screen uses — `ignoreUnknownKeys = true` is already set on the shared `Json` instance (`ApiClient.kt`), so the rest of WO-12's response shape is safely ignored:

```kotlin
@Serializable
data class PoleListItemDto(
    @SerialName("pole_id") val poleId: String,
    val position: Int,
    @SerialName("fixture_status") val fixtureStatus: String,
)
```

A small `PoleListRepository` (interface + `RealPoleListRepository`, under `feature/survey/data/`) wraps this call — no Fake needed, the endpoint is real and already shipped on `dev`.

## 7. Photo capture repository — Fake now, Real once BE ships it

**Correction from the first draft of this spec:** the original wording said this photo flow would "enqueue a sync-queue entry". It doesn't, in v1. `SyncQueueProcessor.processQueuedOps()` marks any row with no matching `SyncOpHandler` as `failed` with "No handler for {opType}" on the very next sync cycle — queuing now, before a handler exists, would show the photo as a sync error right after a successful capture, which is worse for a demo than just not claiming cloud sync yet.

`PolePhotoRepository` interface, added under `feature/survey/data/`:

```kotlin
interface PolePhotoRepository {
    fun observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>>
    suspend fun capture(workOrderId: String, poleId: String, filePath: String, lat: Double, lng: Double): LocalPolePhotoEntity
}
```

`FakePolePhotoRepository` — writes to Room immediately (real local behavior, not mocked). No sync simulation, no AI result: the photo just shows as "đã lưu trên máy" (never "đã đồng bộ" — keeping that distinction from `CLAUDE.md`'s sync badge table honest, since nothing is actually uploaded yet).

`RealPolePhotoRepository` is **not** written yet, not even as a stub — per the project's own rule against half-finished implementations, it gets written only once the BE endpoint exists. That task also adds a `UploadPolePhotoSyncHandler` (`SyncOpHandler`, `opType = "upload_pole_photo"`) and switches `capture()` to call `SyncQueueManager.enqueue(...)` — that's when this flow actually joins the real offline-first sync queue.

## 8. Proposed BE endpoint (already sent to BE by the user)

```
POST /api/v1/work-orders/{work_order_id}/pole-photos
multipart/form-data:
  pole_id        required
  photo          required, JPEG, same 16MB ceiling as AiController.MaxUploadBytes
  captured_at    required, ISO 8601 UTC
  lat, lng       required, EPSG:4326
  client_op_id   required, UUID, idempotency key (existing pattern across the API)
```

Expected response: the AI classification result for that pole, persisted (unlike the existing `/api/v1/ai/detect`, which is Manager-only and doesn't save anything), tied to `pole_id` and `work_order_id` so it can support RQ1 accuracy comparison later and eventually surface in a results screen.

This contract is a mobile-side proposal, not yet BE-confirmed. Field names may change once BE replies — do not hardcode a `RealPolePhotoRepository` against this shape until BE confirms it in `docs/contract-drift.md` or the API contract doc.

## 9. Error handling

- GPS fix unavailable (no permission, or the fused location call returns `null`) → store `lat = 0.0, lng = 0.0` and proceed, same fallback `EvidenceCaptureViewModel.onPhotoCaptured` already uses (`location?.latitude ?: 0.0`) — not blocking capture matches that precedent; revisit if a demo photo with a missing real fix turns out to matter.
- `TakePicture()` returns `success = false` (user backed out of the system camera, or it failed) → return to Pole List without writing a Room row, same as `EvidenceCaptureRoute` falling through to `onDone()`.
- Pole List fetch (`GET /work-orders/{id}/poles`) fails → `PoleListUiState.Error`, with a retry action (same 4-state `UiState` pattern required everywhere per `CLAUDE.md`).
- No network at photo-capture time → irrelevant in v1 — capture never touches the network, see section 7.

## 10. Testing

- Unit: `PolePhotoDao` (Room in-memory, matching the existing DAO test pattern), `FakePolePhotoRepository.capture()`, `PoleListViewModel` (MockWebServer or a fake `PoleListRepository`, mapping all 4 `UiState` branches), `PolePhotoCaptureViewModel.onPhotoCaptured`.
- No new ViewModel test added to `CaptureViewModelTest` or its suite — these are new, separate ViewModels, avoiding the pre-existing hang in that test class.
- No real-device camera/GPS verification is in scope for this spec — same deferred status as the rest of the app's capture screens (tracked in memory, not re-litigated here).
