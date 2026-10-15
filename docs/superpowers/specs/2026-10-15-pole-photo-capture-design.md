# Pole Photo Capture — Design Spec

**Date:** 2026-10-15
**Status:** Approved by user in chat, pending BE endpoint (user has already asked BE to build it)

## 1. Background and confirmed decision

The capstone mentor (GVHD), treated as equivalent to "chủ dự án" for business-decision purposes, asked to prioritize a new per-pole capture flow for a demo. This runs in **parallel** with the existing continuous-video-per-route survey flow (confirmed 2026-09-25, unchanged) — it does not replace it. The short-video part of this new flow is deferred; this spec covers **photo capture only**.

Recorded in `CLAUDE.md` under "Quyết định nghiệp vụ mới đã chốt (2026-10-15)".

At each pole on an assigned survey route, the Field Engineer takes one photo of that pole and uploads it for the server/AI to scan and classify. This is for a quick demo to the mentor, so scope is deliberately tight (see "Out of scope").

## 2. Scope (v1)

In scope: pick an assigned survey work order → pick a pole from that work order's pole list → capture one photo for that pole, with a GPS fix attached → save locally → repeat for the next pole → return to the hub when done.

**Known, accepted gap for v1 (confirmed with user 2026-10-15, not an oversight):** the photo is captured with CameraX `ImageCapture` default auto-exposure, **not** a real manual exposure lock. `CLAUDE.md`'s hard rule ("không cho bắt đầu khảo sát nếu chưa khoá exposure thật") stays in force for the existing continuous-video flow, unchanged — this v1 photo flow is a deliberate, recorded exception, because a real lock means writing a manual Camera2 still-capture pipeline on the scale of the 992-line `VideoCaptureSession.kt`, which does not fit the demo timeline. Tracked as technical debt to fix after the demo.

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

- **Camera:** plain CameraX `ImageCapture` use case bound to the screen's lifecycle, default auto-exposure (see the v1 gap noted in section 2 — this is new code, not a reuse of `VideoCaptureSession.kt`'s manual Camera2 pipeline).
- **`LocationTracker`** (existing, from `core/location`) — `getCurrentLocation()` takes a single GPS fix at capture time (not a continuous track like F04's video session). Returns `null` on no permission/no fix.
- **EXIF:** no existing writer to reuse — `CaptureMetadataWriter` from `CLAUDE.md`'s folder sketch was never built (the project dropped standalone-photo capture in the 2026-09-25 decision, before this new exception). New, small `PolePhotoExifWriter` class using `androidx.exifinterface:exifinterface` (already on the approved tech stack, just unused until now): writes `GPSLatitude`/`GPSLongitude`/`GPSTimeStamp` and `DateTimeOriginal` onto the saved JPEG.

Readiness check for this screen: only "GPS fix valid" gates the capture button (no exposure check in v1, per section 2). Storage/battery checks are skipped — a handful of JPEGs, unlike a continuous video session, don't risk running out of space or battery mid-capture.

On capture: write the JPEG to app-specific storage, write EXIF via `PolePhotoExifWriter`, then write one `LocalPolePhotoEntity` row. No sync-queue entry in v1 — see section 6 for why.

## 5. Local storage

New Room entity `local_pole_photo` (table name chosen by mobile per `CLAUDE.md`'s C8.5 naming authority — no BE confirmation needed for local-only names). No `syncStatus` field in v1 — see section 6 for why; a row existing for a `poleId` means "captured and saved", full stop:

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

## 6. Repository — Fake now, Real once BE ships it

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

- GPS fix unavailable (no permission) or the fused location call returns `null` → capture button stays disabled with a short inline reason, same pattern as F03's GPS check.
- `ImageCapture.takePicture()` fails (device/storage error) → show an inline error, let the user retry; nothing is written to Room until the file is confirmed saved.
- No network at capture time → irrelevant in v1 — capture never touches the network, see section 6.

## 9. Testing

- Unit: `PolePhotoDao` (Room in-memory, matching the existing DAO test pattern), `PolePhotoExifWriter`, `FakePolePhotoRepository.capture()`.
- No new ViewModel test added to `CaptureViewModelTest` or its suite — this is a new, separate ViewModel, avoiding the pre-existing hang in that test class.
- Compose UI test for the Pole List screen's "đã chụp" marking, following the project's existing Compose UI Test conventions.
- No real-device camera/GPS verification is in scope for this spec — same deferred status as the rest of the app's capture screens (tracked in memory, not re-litigated here).
