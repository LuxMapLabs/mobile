# Pole Photo Capture — Design Spec

**Date:** 2026-10-15
**Status:** Approved by user in chat, pending BE endpoint (user has already asked BE to build it)

## 1. Background and confirmed decision

The capstone mentor (GVHD), treated as equivalent to "chủ dự án" for business-decision purposes, asked to prioritize a new per-pole capture flow for a demo. This runs in **parallel** with the existing continuous-video-per-route survey flow (confirmed 2026-09-25, unchanged) — it does not replace it. The short-video part of this new flow is deferred; this spec covers **photo capture only**.

Recorded in `CLAUDE.md` under "Quyết định nghiệp vụ mới đã chốt (2026-10-15)".

At each pole on an assigned survey route, the Field Engineer takes one photo of that pole and uploads it for the server/AI to scan and classify. This is for a quick demo to the mentor, so scope is deliberately tight (see "Out of scope").

## 2. Scope (v1)

In scope: pick an assigned survey work order → pick a pole from that work order's pole list → capture one photo for that pole, with exposure locked and a GPS fix attached → save locally and queue for sync → repeat for the next pole → return to the hub when done.

Out of scope (explicitly, so no one assumes it's done):
- Short video capture (the other half of the mentor's request — separate follow-up task).
- Editing/overriding the AI classification result per pole (blocked on BE; same gap already recorded in `CLAUDE.md` for the existing AI Confirmation screen).
- Folding this photo's result into the existing `AiConfirmationScreen` (that screen is driven by `/sweeps/{id}/results`, a different data source — no merge in v1).
- Retake/delete of an already-captured photo (capture is one-shot per pole for v1).
- The full F03 readiness checklist (GPS accuracy/storage/battery) — see section 4 for what v1 checks instead.

## 3. Navigation flow

Survey Hub → new button "Chụp ảnh cột đèn" on an assigned survey work order card, next to the existing "Kiểm tra sẵn sàng" button → **Pole List screen** (`GET /api/v1/work-orders/{id}/poles`, already used by F09) → tap a pole → **Pole Photo Capture screen** → on capture, save and return to Pole List with that pole marked "đã chụp" → "Hoàn tất" button returns to Survey Hub.

New routes added to `Routes.kt`:
- `survey/pole-photos/{workOrderId}` — Pole List screen.
- `survey/pole-photos/{workOrderId}/capture/{poleId}` — Capture screen.

## 4. Pole Photo Capture screen

Reuses existing, already-tested infrastructure instead of writing new camera code:
- **`ExposureLockController`** (existing, from `core/camera`) — locks AE/ISO/shutter before the capture button is enabled. Per `CLAUDE.md`'s core rule ("không cho bắt đầu khảo sát nếu chưa khoá exposure thật — ảnh auto-exposure không sửa lại được sau khi chụp"), this is a hard requirement, not a toggle — same as F04's video flow.
- **`LocationTracker`** (existing, from `core/location`) — takes a single GPS fix at capture time (not a continuous track like F04's video session).
- **`CaptureMetadataWriter`** (existing) — writes GPS + timestamp EXIF into the JPEG.

Readiness check for this screen is lighter than F03's full checklist (`CheckSurveyReadinessUseCase`): only "exposure locked" and "GPS fix valid" gate the capture button. Storage/battery checks are skipped — a handful of JPEGs, unlike a continuous video session, don't risk running out of space or battery mid-capture.

On capture: write the JPEG to app-specific storage, write a `LocalPolePhoto` Room row, enqueue a sync-queue entry — same pattern as a single `WorkOrder` evidence photo (F10), not the chunked/resumable upload used for the big video sessions in F06.

## 5. Local storage

New Room entity `local_pole_photo` (table name chosen by mobile per `CLAUDE.md`'s C8.5 naming authority — no BE confirmation needed for local-only names):

```kotlin
@Entity(tableName = "local_pole_photo")
data class LocalPolePhoto(
    @PrimaryKey val photoId: String,       // client-generated UUID, doubles as client_op_id
    val workOrderId: String,
    val poleId: String,
    val filePath: String,                  // app-specific storage, not Room
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
    val syncStatus: SyncStatus,             // reuse the existing enum (queued/syncing/failed/conflict/done)
)
```

A DAO (`PolePhotoDao`) follows the existing pattern used by other local tables: insert, update sync status, query by `workOrderId` (to render the Pole List screen's "đã chụp" marks).

## 6. Repository — Fake now, Real once BE ships it

`PolePhotoRepository` interface, added under `feature/survey/data/`:

```kotlin
interface PolePhotoRepository {
    fun observePhotos(workOrderId: String): Flow<List<LocalPolePhoto>>
    suspend fun capture(workOrderId: String, poleId: String, filePath: String, lat: Double, lng: Double): LocalPolePhoto
}
```

`FakePolePhotoRepository` — writes to Room immediately (real local behavior, not mocked), simulates `queued → syncing → done` over a few seconds like the other Fake repositories, and does not yet surface a classification result (no fake AI result invented for this one — BE hasn't confirmed the response shape, so nothing is shown beyond "đã đồng bộ"/"đồng bộ lỗi").

`RealPolePhotoRepository` is **not** written yet, not even as a stub — per the project's own rule against half-finished implementations, it gets written only once the BE endpoint exists, then bound in `RepositoryModule` in one line.

## 7. Proposed BE endpoint (already sent to BE by the user)

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

## 8. Error handling

- Exposure lock fails to engage → capture button stays disabled, same UX pattern as F04.
- GPS fix unavailable/stale → capture button stays disabled with a short inline reason, same pattern as F03's GPS check.
- Local save succeeds, sync fails (offline or BE error) → row stays `queued`/`failed` in Room, retried by the existing `SyncWorker`/`SyncQueueManager` — no special-casing needed, this reuses the existing sync queue machinery.
- No network at capture time → capture still succeeds locally (offline-first); sync is a background concern, never blocks the capture screen.

## 9. Testing

- Unit: `PolePhotoDao` (Room in-memory, matching the existing DAO test pattern), `FakePolePhotoRepository`'s queued→syncing→done simulation.
- No new ViewModel test added to `CaptureViewModelTest` or its suite — this is a new, separate ViewModel, avoiding the pre-existing hang in that test class.
- Compose UI test for the Pole List screen's "đã chụp" marking, following the project's existing Compose UI Test conventions.
- No real-device camera/GPS verification is in scope for this spec — same deferred status as the rest of the app's capture screens (tracked in memory, not re-litigated here).
