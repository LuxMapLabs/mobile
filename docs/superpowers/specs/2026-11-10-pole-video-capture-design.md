# Pole Video Capture — Design Spec

**Date:** 2026-11-10
**Status:** Approved by user in chat, ready for implementation planning

## 1. Background and confirmed decision

The mentor's 2026-10-15 request had two halves: a photo per pole (shipped) and a short video per pole (deferred, tracked in `CLAUDE.md` as "Ngoài phạm vi v1"). The backend half of the video flow is now confirmed complete by reading `luxmap_backend` source directly (`WorkOrderPoleVideosController.cs`, `WorkOrderPolePhotoVideoUpload.cs`, `PoleVideoVerdict.cs`, `PoleVideoOptions.cs`) and its own `docs/contract-drift.md` (section "POLE-VIDEO", codes PV-1…PV-10). This spec covers the mobile half: recording and uploading one short clip per pole.

**Confirmed with the user in this chat (2026-11-10), each its own decision:**
- Record with the **system camera's video intent** — no CameraX, no manual exposure lock. This is not a gap: the backend explicitly marks this clip's `video.capture_config = "not_verified"` and excludes it from the locked-camera-config survey flow (D-R24) on purpose (contract-drift.md, PV-9) — unlike the continuous-video survey (F03–F06), which keeps its real exposure lock, unchanged.
- The Field Engineer **watches the recorded clip back** (ExoPlayer, already a dependency, already used this way in `CoverageScreen.kt` for the continuous-survey flow) before confirming, with a Retake option — same shape as the existing photo capture screen's preview/confirm/retake, just with video playback instead of a still image.
- The server's `video` sub-object on `PolePhotoItem` (duration, frame counts, reason code, etc.) is **not** surfaced anywhere in the mobile UI for v1 — nothing today needs it, and the existing `cv_state`/review/decision UI already works generically regardless of whether a photo came from a still shot, a gallery pick, or now a video frame.

## 2. Scope (v1)

In scope:
- Enable the already-present, currently-disabled "Quay video" option in the per-pole `ReportMethodSheet` (`PoleListScreen.kt`), removing its lock icon and the "Chưa hỗ trợ gửi video, dùng ảnh thay thế" caption.
- A new capture screen: record with the system camera, play the clip back, enforce the server's 3–30 second window client-side before allowing a save, retake on demand.
- A new sync-queue upload path for the recorded clip, producing the same kind of Room row and the same downstream review/decision UI the photo flow already has.

Out of scope (explicitly):
- Any mobile UI for the `video` sub-object's fields (duration, sampled/on/off frame counts, `reason_code`, HDR flag, `viewed_by`/`viewed_at`). Not parsed into any DTO; `ignoreUnknownKeys = true` already makes the server sending it harmless to ignore.
- The Manager-side "must view the clip before confirming" rule (PV-10, `POST .../decision`) — Web GIS only, not mobile.
- Any change to the continuous-video survey flow (F03–F06) or its exposure lock (D-R24) — unrelated, unchanged.
- Enforcing the maximum 30-second limit at the OS/intent level being guaranteed — some camera apps may not honor `EXTRA_DURATION_LIMIT`, so client-side duration validation after recording is the real guardrail for both the 3s floor and the 30s ceiling.

## 3. Backend contract being coded against (confirmed from source, not assumed)

```
PUT /api/v1/work-orders/{id}/pole-videos/{client_op_id}
  Policy ExecuteWorkOrders — same assignee/wo_status=in_progress rule as pole-photo upload.
  Body: raw MP4 bytes (NOT multipart). Content-Length required (chunked -> 400).
  Path: client_op_id is a GUID string — the idempotency key (PUT semantics, not a form field).
  Query: pole_id, captured_at, lat, lng — same required/validated shape as pole-photo upload
    (captured_at: ISO-8601, <= 7 days old, <= 5 min clock skew ahead; lat/lng: finite, |lat|<=90, |lng|<=180).
    Any other query key -> 400.
  Header: X-Content-SHA256 — REQUIRED, 64 lowercase hex chars (unlike the photo endpoint, where
    this concept doesn't apply since it's multipart).
  Limits: max 100 MiB; clip duration 3-30s (+0.5s grace) or 400 VIDEO_TOO_SHORT / VIDEO_TOO_LONG.
  -> 201 (new) or 200 (idempotent replay, same client_op_id + same content) with a PolePhotoItem
     body — the SAME shape /pole-photos already returns, plus one new optional field:
     video: { duration_ms, byte_count, sha256, sampled_frames, on_frames, off_frames,
       daylight_frames, confidence_min/median/max, median_luma, hdr, reason_code,
       viewed_by, viewed_at, video_url } | null (null for a plain photo)
  Errors: 400 VALIDATION_FAILED / HASH_MISMATCH / CONTENT_LENGTH_MISMATCH / VIDEO_TOO_SHORT /
    VIDEO_TOO_LONG / VIDEO_INVALID / VIDEO_ROTATION_UNSUPPORTED / VIDEO_DIMENSIONS;
    403/404; 409 IDEMPOTENCY_CONFLICT (same id, different content) / POLE_NOT_IN_WORK_ORDER /
    WORK_ORDER_NOT_IN_PROGRESS / POLE_PHOTO_TARGET_CHANGED;
    413 UPLOAD_TOO_LARGE; 415 UNSUPPORTED_VIDEO_FORMAT;
    503 VIDEO_PROCESSING_BUSY / _TIMEOUT / _UNAVAILABLE / STORAGE_OBJECT_MISSING.

GET /api/v1/work-orders/{id}/pole-photos/{photo_id}/video   (out of scope — Manager/Web GIS only
  per PV-1/PV-2/PV-3's viewed-by bookkeeping; mobile never calls this)
```

Everything else about a video-sourced row — `GET` list/detail/thumbnail/original, `PATCH .../review`, the Pole List screen's badges, the AI-result bottom sheet — is **already correct today, unchanged**, because the server stores a video-sourced upload as an ordinary `WorkOrderPolePhoto` row (the representative frame becomes `thumbnail_url`/`original_url`) and every one of those endpoints already operates on that row generically.

## 4. Local storage and repository changes

`LocalPolePhotoEntity` gains one column:

```kotlin
val mediaType: String = "photo"  // "photo" | "video" - decides which sync op capture() enqueues
```

`AppDatabase` version bumps (currently 14) for this column, via `fallbackToDestructiveMigration()` (pre-release convention already established, no `Migration` object needed).

`PolePhotoRepository` gains one method, parallel to the existing `capture()`:

```kotlin
suspend fun captureVideo(
    workOrderId: String,
    poleId: String,
    filePath: String,
    lat: Double,
    lng: Double,
): LocalPolePhotoEntity
```

`RealPolePhotoRepository.captureVideo()` writes a `LocalPolePhotoEntity` row with `mediaType = "video"` and enqueues:

```kotlin
syncQueueManager.enqueue(
    opType = "upload_pole_video",
    payloadJson = json.encodeToString(PolePhotoSyncPayload(entity.photoId)),  // same payload shape as the photo op
    clientOpId = entity.photoId,
)
```

`FakePolePhotoRepository.captureVideo()` mirrors `capture()`'s existing fake-success shape (insert the row; no sync simulation, matching how `capture()` itself behaves in the Fake today — note the Fake isn't bound anywhere, `RepositoryModule.kt` only binds `RealPolePhotoRepository`, kept in sync per the project's Fake/Real convention regardless).

## 5. New sync handler

`UploadPoleVideoSyncHandler : SyncOpHandler`, `opType = "upload_pole_video"`, registered in `SyncModule.kt` alongside `UploadPolePhotoSyncHandler`:

1. Load the row by `photoId` from the payload (same guard pattern as the photo handler: missing row -> `Failed`).
2. Compute the file's SHA-256 (same algorithm `PackageSurveySessionUseCase.sha256Of()` already uses for survey clips — `MessageDigest.getInstance("SHA-256")`, streamed, hex-encoded; duplicated locally here rather than extracted, matching this codebase's existing tolerance for small duplicated mapping/utility code between a repository and its sync handler).
3. Call the new Retrofit method (below), body = `File(photo.filePath).asRequestBody("video/mp4".toMediaType())`, using `photo.photoId` as the path's `client_op_id`.
4. On success: call `dao.updateServerPhotoId(photo.photoId, response.photoId)` — exactly what `UploadPolePhotoSyncHandler` does today. The server computes the AI verdict synchronously during this same upload (`PoleVideoVerdict`/D-3), unlike a photo's own async-feeling flow, but mobile doesn't need to special-case that: the existing `fetchResult`/`onViewAiResult` path (already wired to the AI-result bottom sheet) fetches and displays whatever `cv_state` is present the first time the Field Engineer taps to view it — already correct for a video-sourced row with no extra code, since both rows are the same shape server-side.
5. Error mapping (same shape as `UploadPolePhotoSyncHandler`): `409 -> Conflict`; `400, 403, 404, 413, 415 -> Failed`; everything else (`503`, unmapped) -> `RetryLater`; `IOException -> RetryLater`.

New Retrofit method on `WorkOrdersApi` (same shape as `SweepsApi.uploadClip`, already used for the continuous-survey clip upload, and returning the same minimal `PolePhotoUploadResponseDto` — just `photo_id` — that `uploadPolePhoto` already returns, for the same reason: this handler only needs the server-assigned id):

```kotlin
@PUT("api/v1/work-orders/{id}/pole-videos/{client_op_id}")
suspend fun uploadPoleVideo(
    @Path("id") id: String,
    @Path("client_op_id") clientOpId: String,
    @Query("pole_id") poleId: String,
    @Query("captured_at") capturedAt: String,
    @Query("lat") lat: String,
    @Query("lng") lng: String,
    @Header("X-Content-SHA256") sha256: String,
    @Body body: RequestBody,
): PolePhotoUploadResponseDto
```

No `video` field added to `PolePhotoItemDto` (section 2's scope cut) — `ignoreUnknownKeys = true` already makes the server sending that sub-object (on this response, and later on every `GET`/`review`/list response for this row) harmless to ignore.

## 5a. No `dependsOnClientOpId` chaining needed

A video-upload op is self-contained (the clip file + its own metadata); it has no ordering dependency on any other queued op, same reasoning already applied to the photo-upload and review-send ops in the batch-submit-review feature.

## 6. Capture screen

New route `survey/pole-videos/{workOrderId}/capture/{poleId}`, reached from `ReportMethodSheet`'s now-enabled "Quay video" option (a new `onChooseVideo` callback alongside the existing `onChoosePhoto`/`onChooseGallery`, wired through `PoleListScreen`/`PoleListRoute`/`NavGraph.kt` the same way the gallery option was wired in the batch-submit-review feature).

New files: `PoleVideoCaptureScreen.kt` (Route + Composable), `PoleVideoCaptureViewModel.kt` — a new, separate screen and ViewModel, not a branch inside the existing photo capture screen, because the preview step is fundamentally different (video playback, not an image) and the capture step is fundamentally different (a recording session, not a single shutter press).

**Record step:** launch the system video-capture intent (`MediaStore.ACTION_VIDEO_CAPTURE` via a small custom `ActivityResultContract`, since the stock `ActivityResultContracts.CaptureVideo()` doesn't expose a way to set `MediaStore.EXTRA_DURATION_LIMIT`) targeting a `FileProvider` URI for `viewModel.outputFile(context.filesDir)` (an `.mp4` file under `pole-videos/$workOrderId/`), with `EXTRA_DURATION_LIMIT = 30` seconds as a best-effort cap — not trusted alone, per section 2's scope note.

**Preview step:** on a successful recording, show an ExoPlayer-backed preview (same Compose wiring `CoverageScreen.kt` already uses: `remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(...)); prepare() } }` + `DisposableEffect` to release on dispose + `AndroidView { PlayerView(it).apply { player = ... } }`), with "Quay lại" / "Dùng video này" buttons matching the photo screen's `RetakeButton`/`ConfirmButton` visual pattern.

**Duration validation:** immediately after a successful recording (before showing the preview, or as the preview mounts), read the clip's duration via `MediaMetadataRetriever` and run it through a small, independently testable pure function:

```kotlin
fun isValidPoleVideoDuration(durationMs: Long): Boolean = durationMs in 3_000..30_500
```

(3s floor, 30s ceiling + the server's own 0.5s grace, matching section 3's contract exactly). When invalid, the preview screen shows an inline error, "Dùng video này" stays disabled, and "Quay lại" is the only way forward — mirrors the existing `ReviewCard`'s "note required" inline-error pattern (disable the primary action, show why).

**Confirm step:** on tap, `viewModel.onVideoCaptured(file, onDone)` — same shape as `PolePhotoCaptureViewModel.onPhotoCaptured`: get a GPS fix via the existing `LocationTracker` (same `location?.latitude ?: 0.0` fallback the photo and gallery flows already use), call `repository.captureVideo(...)`, then `onDone()`.

## 7. Error handling summary

- Recording cancelled/failed (user backs out, or the system camera app returns no result) → return to Pole List without writing a Room row, same as the existing photo/gallery cancel paths.
- Clip shorter than 3s or longer than 30.5s → caught client-side before any upload is attempted (section 6); the server-side `VIDEO_TOO_SHORT`/`VIDEO_TOO_LONG` codes exist as a backstop, not the primary guard.
- No network at capture time → irrelevant to the capture step itself (Room-first, same as every other capture flow in this app); the upload is queued and retried by `sync_queue` like any other op.
- A 100 MB clip failing partway through a flaky upload → `IOException` → `RetryLater`, same backoff/retry machinery already used for every other sync op — no new resumability work needed here (PUT + `client_op_id` already makes a full retry idempotent server-side; this is not a chunked/resumable upload).

## 8. Testing

- Unit: `UploadPoleVideoSyncHandlerTest` (mirrors `UploadPolePhotoSyncHandlerTest`'s shape exactly: success calls `updateServerPhotoId`; missing row; 409 -> Conflict; 404/413 -> Failed; 500 -> RetryLater).
- Unit: `RealPolePhotoRepositoryTest`/`FakePolePhotoRepositoryTest` additions for `captureVideo()` (writes `mediaType = "video"`; enqueues `upload_pole_video` with the right payload/clientOpId).
- Unit: `PoleVideoCaptureViewModelTest` (mirrors `PolePhotoCaptureViewModelTest`: GPS fix used/falls back to 0,0; `onVideoCaptured` calls `repository.captureVideo` and then `onDone`).
- Unit: a small dedicated test for `isValidPoleVideoDuration` (boundary cases: 2999ms false, 3000ms true, 30500ms true, 30501ms false) — same "extract the risky/rule-bearing logic into a plain testable function" approach already used for `copyGalleryImageToFile` in the batch-submit-review feature's final review.
- Instrumented: extend `PoleListScreenTest` for the now-enabled "Quay video" option (exists, enabled, calls the new `onChooseVideo`/`onCaptureVideo` callback with the right pole id) — same shape as the existing gallery-option test.
- No instrumented test for `PoleVideoCaptureScreen` itself (system camera intent + ExoPlayer playback, not reliably mockable) — same established exclusion as the photo/gallery capture screens; verified by hand on a real device instead.
