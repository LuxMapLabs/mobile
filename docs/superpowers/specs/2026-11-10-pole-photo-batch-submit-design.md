# Pole Photo Review — Batch Submit on "Hoàn tất" + Gallery Picker — Design Spec

**Date:** 2026-11-10
**Status:** Approved by user in chat, ready for implementation planning

## 1. Background and confirmed decision

Team decision (relayed by the user after a team meeting, 2026-11-10), changing how the already-shipped pole-photo survey flow (`docs/superpowers/specs/2026-10-15-pole-photo-capture-design.md`, PHOTO-REVIEW ticket) sends a Field Engineer's review correction to the server.

**Unchanged:** photo capture → upload → AI classification still happens immediately per pole, exactly as it does today. AI only runs server-side once a photo actually reaches the server, so this part cannot be deferred.

**Changed:** when the FE reviews an AI result and confirms/corrects the label (`ReviewCard` in `PolePhotoResultBottomSheet`), that correction is now saved **locally only** — not sent to the server at that moment. The FE moves on to the next pole. Only when the FE taps **"Hoàn tất"** on the Pole List screen are all pending review corrections for the whole route sent to the server, followed by the existing work-order completion call.

**Also added in the same meeting:** a "choose from gallery" capture method, alongside the existing camera capture, at the per-pole report-method sheet.

This does not touch the Manager-side `/decision` endpoint or flow (out of mobile scope, unchanged) and does not touch the continuous-video survey flow (F03–F06).

## 2. Scope (v1)

In scope:
- Add "Chọn từ thư viện" (pick from device gallery) as a third option in `ReportMethodSheet`, alongside "Chụp ảnh" (enabled) and "Quay video" (stays disabled, unchanged).
- Change `ReviewCard`'s "Xác nhận" action from an immediate network call (`PATCH .../review`) to a local-only Room write.
- Change "Hoàn tất" on the Pole List screen to first enqueue a sync job per pending review, then proceed with the existing work-order completion call.

Out of scope (explicitly):
- A dedicated per-row sync-status badge for the review-send job after "Hoàn tất" is tapped (confirmed cut 2026-11-10 — reuse the existing F13 Sync Queue screen for visibility into failed/pending review sends instead).
- Any change to the Manager-side `/decision` flow, or to the photo capture → upload → AI pipeline itself.
- Reading EXIF GPS/timestamp from a gallery-picked image — tagging stays "current device location at the moment the photo is saved," identical to the existing camera-capture behavior, even though a gallery pick may be an older photo taken elsewhere. Same accepted limitation as today's camera flow, now also applied to gallery picks.

## 3. Local storage changes

`LocalPolePhotoEntity` (table `local_pole_photo`) gets two new nullable columns:

```kotlin
val draftFinalCvState: String? = null  // FE's review choice, not yet sent to the server
val draftReviewNote: String? = null    // note accompanying the choice, not yet sent
```

`AppDatabase` version bumps from **13 → 14**, with a migration adding these two columns (`ALTER TABLE local_pole_photo ADD COLUMN ...`, both nullable TEXT, no default-value backfill needed).

These are separate from the existing `finalCvState`/`reviewNote`/`reviewedBy`/`reviewedAt` columns, which continue to mean "confirmed by the server" exactly as they do today.

## 4. Review becomes a local-only action

`PoleListViewModel.onSubmitReview(serverPhotoId, finalCvState, reviewNote)`: instead of calling `polePhotoRepository.reviewPhoto(...)` (a live `PATCH .../review` call), calls a new repository method:

```kotlin
suspend fun saveDraftReview(photoId: String, finalCvState: String, reviewNote: String?)
```

implemented by `RealPolePhotoRepository` as a single Room write (`dao.updateDraftReview(...)`) — no network call, no `Result`/error path needed since nothing can fail except a local DB write.

Existing client-side validation stays exactly where it is (`ReviewCard`/`ReviewChoiceSegment`): `reviewNote` is still required whenever the chosen value differs from `cv_state`, including when `cv_state` is `null`/`inconclusive`. This validation now gates the local save instead of gating a network call — same rule, same place, different trigger.

`PolePhotoCaptureViewModel`'s existing toast on `reviewSubmitted` changes text from "Đã gửi xác nhận" to **"Đã lưu, sẽ gửi khi Hoàn tất"** — the old text claimed the review had reached the server, which is no longer true at this point.

Re-opening a pole's AI result sheet before "Hoàn tất" shows the FE's own locally-saved draft choice as the current selection (sourced from the two new columns), not the original AI `cv_state` — so a FE revisiting a pole mid-route sees what they already decided, and can change it again (plain overwrite, no history kept of earlier drafts).

## 5. Batch submit on "Hoàn tất"

New sync op type `"submit_pole_photo_review"`, handled by a new `SubmitPolePhotoReviewSyncHandler : SyncOpHandler`, registered in `SyncModule.kt`'s `Set<SyncOpHandler>` alongside `UploadPolePhotoSyncHandler`.

**`PoleListViewModel.onComplete()`**, before calling `completionRepository.submitCompletion(...)` (unchanged call), now also calls a new repository method:

```kotlin
suspend fun enqueuePendingReviews(workOrderId: String): Int
```

implemented by `RealPolePhotoRepository`: loads all `local_pole_photo` rows for `workOrderId` where `draftFinalCvState != null`, and for each one calls:

```kotlin
syncQueueManager.enqueue(
    opType = "submit_pole_photo_review",
    payloadJson = json.encodeToString(PolePhotoSyncPayload(photoId)),  // same payload shape as upload
    clientOpId = "$photoId:review",                                    // see note below
)
```

**`clientOpId` namespacing:** the review job's `clientOpId` is `"$photoId:review"`, not plain `photoId`. The photo-upload job already uses `clientOpId = photoId`. Since `sync_queue` status lookups key off `clientOpId` (`observeUploadStatuses` maps `clientOpId -> status`), reusing the same key for two different ops would collide. This suffix is new to this feature; the upload job's `clientOpId` is unchanged.

**`SubmitPolePhotoReviewSyncHandler.handle(...)`:**
1. Load the `local_pole_photo` row by `photoId` from the payload. Missing row, or missing `serverPhotoId` → `SyncOpResult.Failed` (defensive guard only — per the flow in section 4, a draft can only exist after the AI result was already fetched, which requires `serverPhotoId` to already be set, so this should not happen in practice).
2. Call `api.reviewPolePhoto(workOrderId, serverPhotoId, PolePhotoReviewRequestDto(finalCvState = draftFinalCvState, reviewNote = draftReviewNote))`.
3. On success: write the response into the existing confirmed columns (`finalCvState`, `reviewNote`, `reviewedBy`, `reviewedAt`, same as today's `persist()`), **and clear both draft columns to `null`** — this is what makes a second "Hoàn tất" tap (or a retry) a no-op for an already-sent review, with no separate "sent" flag needed.
4. HTTP 409 (`POLE_PHOTO_ALREADY_DECIDED`, the Manager already decided before this review reached the server) → `SyncOpResult.Failed`, permanent, no retry. Draft columns are left as-is (visible as a failed row in the F13 Sync Queue screen; no recovery action, matching the endpoint's own "locks review permanently" rule).
5. 400 (missing/invalid note) → `SyncOpResult.Failed`. This should not happen in practice since the same rule is already enforced client-side before a draft can be saved.
6. Network/5xx → `SyncOpResult.RetryLater`, same as the upload handler's `IOException` branch.

**No `dependsOnClientOpId` chaining** is needed in either direction:
- Review jobs don't need to depend on the photo-upload job, because a draft review can only exist once an AI result was already fetched, which already requires a successful upload (`serverPhotoId` set).
- The `complete_work_order` job doesn't need to depend on the review jobs — `onComplete()` already doesn't wait for photo uploads to finish today, and there is no business rule requiring reviews to reach the server before the work order is marked complete (the Manager checks `final_cv_state` whenever they open Web GIS, independent of work-order completion timing).

Completion toast text updates to reflect that sending is in progress, not finished, e.g. "Đã hoàn tất · N review đang gửi lên Kỹ sư bảo trì" when `N > 0`, otherwise the existing message when there was nothing to send.

## 6. Gallery picker

`ReportMethodSheet` gets a third `ReportMethodOption`: icon `Icons.Filled.PhotoLibrary`, label "Chọn từ thư viện", `enabled = true`.

`PolePhotoCaptureRoute` gains a capture-source argument (camera vs. gallery), passed through the nav route alongside the existing `workOrderId`/`poleId` arguments. The `LaunchedEffect` that currently always requests `CAMERA` + launches `TakePicture()` branches on this argument:
- Camera (unchanged): request `CAMERA` + `ACCESS_FINE_LOCATION`, launch `TakePicture()`.
- Gallery: launch `ActivityResultContracts.PickVisualMedia()` (part of `androidx.activity`, already a dependency — no new library). No `CAMERA` permission needed; `ACCESS_FINE_LOCATION` stays best-effort, requested the same way.

On a successful gallery pick, the selected image's content is copied (stream copy) into the same app-specific output file the camera path would have used (`viewModel.outputFile(context.filesDir)`), so everything downstream (preview screen, `onPhotoCaptured`, Room write, sync-queue upload) is identical regardless of source. The preview screen's "Chụp lại" button label becomes "Chọn ảnh khác" when the source is gallery (re-opens the picker instead of the camera); everything else in `PolePhotoPreview` is unchanged.

GPS tagging for a gallery-picked photo uses the current device location at save time, same call (`locationTracker.getCurrentLocation()`) and same fallback as the camera path — no EXIF reading from the picked image. This is an accepted, explicitly-scoped-out limitation (section 2), not a gap to fix in v1.

## 7. Error handling summary

- Gallery pick cancelled (user backs out of the picker) → same as a cancelled camera capture today: return to Pole List without writing a Room row.
- No network at review-save time → irrelevant, review-save never touches the network (section 4).
- No network at "Hoàn tất" time → enqueue still succeeds (local Room + `sync_queue` writes only); jobs retry automatically once the network returns, same as every other queued op in the app.
- Review-send permanently failed (409) → visible only via the F13 Sync Queue screen per section 2's scope cut; no in-place retry affordance on the Pole List screen itself.

## 8. Testing

- Unit: `RealPolePhotoRepository.saveDraftReview()` (writes Room, makes no API call), `RealPolePhotoRepository.enqueuePendingReviews()` (enqueues exactly the rows with a non-null draft, correct `clientOpId` suffix), `SubmitPolePhotoReviewSyncHandler` (success path clears drafts and writes confirmed columns; 409 and 400 map to `Failed`; `IOException` maps to `RetryLater`), `PoleListViewModel.onComplete()` (calls `enqueuePendingReviews` before `submitCompletion`).
- Room migration test, version 13 → 14 (new nullable columns, no data loss for existing rows).
- Update `PolePhotoResultBottomSheetTest` assertions for the new toast text and the absence of a network call on "Xác nhận".
- Update `PoleListScreenTest` for the new "Chọn từ thư viện" option in `ReportMethodSheet` and for the updated "Hoàn tất" toast text.
- No instrumented test for the gallery picker itself (same reasoning already applied to the camera capture composable in the 2026-10-15 spec — it is a thin Android-system pipe, not reliably mockable); verified by hand on a real device instead.
