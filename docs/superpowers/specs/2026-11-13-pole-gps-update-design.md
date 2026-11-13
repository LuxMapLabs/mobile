# Pole GPS Update (`task_kind = pole_gps_update`) - Mobile Design

**Date:** 2026-11-13
**Status:** Draft for user review
**Branch:** `feat/pole-gps-update`
**Authority:** `luxmap_backend/docs/contract-drift.md` section WO-POLE-GPS (PG-1...PG-13), BE commit `1032574` on `dev`, status SELF-SIGNED. This replaces `docs/API_Requirements_PoleGpsUpdate_2026-11-13.md` (mobile's earlier proposal, now partly wrong).

## 1. Goal and scope

A Manager assigns a `pole_gps_update` work order: a list of poles (`pole_ids`) plus PDF files holding the QR labels. The Field Engineer goes to each pole, measures its real GPS position and submits it. A Manager later calls `verify` on the Web GIS, which is when BE writes `pole.geom`.

**In scope (mobile):**
- Show the new work order kind in Home / Hub / detail.
- Show the target poles with their current GIS position ("GPS cu", may be `null`).
- Download the PDFs while online and open them with no network.
- Measure GPS per pole, save it locally, submit it through `POST /work-orders/{id}/complete` with `pole_locations[]`.

**Out of scope:** `verify`, `return`, creating the order, uploading or removing attachments (all Manager, Web GIS). No QR scanning in v1 (decision: user chose option A on 2026-11-13; BE dropped `scanned_qr`). No new library.

**Done when:** an engineer with no signal can open the PDFs, measure several poles, and the positions sync on their own once online.

## 2. Contract summary (what mobile consumes)

| Item | Contract |
|---|---|
| Kind | `task_kind = "pole_gps_update"`. Single order, no parent or child. `fault_ids` is always empty. |
| List/detail | `WorkOrderItem` and `WorkOrderDetail` carry `pole_ids: string[]`. |
| Poles | `GET /work-orders/{id}/poles` returns ONLY the target poles. `position` can be `null`, `location` can be `null`. |
| Attachments | `WorkOrderDetail.attachments[] = {attachment_id, file_name, byte_count, uploaded_at, download_url}`. `download_url` is a relative path. `GET` on it returns `application/pdf`, Bearer token required, not presigned. Removed or unseen file: 404 `ATTACHMENT_NOT_FOUND`. |
| Complete | `POST /work-orders/{id}/complete` body adds `pole_locations[{pole_id, lat, lng, accuracy_m, captured_at}]`. Required (>= 1) for this kind, forbidden for other kinds. |
| Complete rules | Partial reporting allowed. Each `pole_id` must be in `pole_ids`, appear once. `lat` +-90, `lng` +-180, `accuracy_m` >= 0 and finite. `captured_at` ISO 8601 UTC, at most 5 minutes in the future. Failure: 400 `VALIDATION_FAILED`, `details.field = "pole_locations"`. |
| Readback | `WorkOrderDetail.pole_locations[]` holds the last reported positions in `pole_ids` order. After `return`, the old report stays; the next `complete` REPLACES all of it. |
| Not changed | `start`, `request-cancellation`, `assign`, notifications. `cancel` releases the poles. |

Mobile keeps sending through the existing REST `complete` call from `sync_queue`. It does NOT move to `/sync/push` or `/sync/bundle` (BE accepts both; the repo never used either).

## 3. Architecture

### 3.1 Data layer

- `WorkOrderTaskKind` gets `POLE_GPS_UPDATE`; `workOrderTaskKindFromWire` maps `"pole_gps_update"` to it. Icon, label and badge colour are chosen in the plan from the existing token set (no new colour token).
- DTOs: `WorkOrderItemDto` / `WorkOrderDetailDto` add `pole_ids`, `attachments`, `pole_locations` (all default empty). New `WorkOrderAttachmentDto`, `WorkOrderPoleLocationDto`.
- `PoleListItemDto`: `position` becomes `Int?` and `location: {lat,lng}?` is added. **This is a bug fix independent of the feature:** the current non-null `Int` makes parsing fail when BE sends `position = null`, and the app Json does not enable `coerceInputValues`.
- `CompleteWorkOrderRequestDto` adds `pole_locations: List<PoleLocationRequestDto>?`.

### 3.2 Local storage (Room v16 -> v17, written migration)

`DatabaseModule` uses `fallbackToDestructiveMigration()`: a version bump with no migration wipes the device, **including unsent `sync_queue` rows and offline measurements**. This change adds an explicit `Migration(16, 17)` (additive tables and columns only) so field data is never wiped by this release.

| Table | Purpose |
|---|---|
| `local_pole_gps_order` | Header cache of the order (id, title, wo_status, scheduled_date, due_date, note, review_note, allowed_actions JSON, fetched_at) so the detail screen opens offline. |
| `local_work_order_attachment` | One row per PDF: ids, file_name, byte_count, download status (`pending`/`downloading`/`ready`/`failed`), local file path under app-private storage. |
| `local_pole_gps_measurement` | One row per measured pole: work_order_id, pole_id, lat, lng, accuracy_m, captured_at, source (`device` or `server`). `server` rows are prefilled from `WorkOrderDetail.pole_locations` after a `return`. |
| `local_pole_list_item` (existing) | Add nullable `lat`, `lng`, make `position` nullable. |

### 3.3 Sync and download

- **Submit:** `submitCompletion` already writes Room then enqueues `complete_work_order`. For this kind the handler loads the measurements from Room and sends them in `pole_locations`. Because the server replaces the whole report on re-complete, the request always carries every local measurement for the order, device-measured and server-prefilled.
- **PDF download:** a `@HiltWorker` (`AttachmentDownloadWorker`, same style as `SyncWorker`) for orders of this kind in `assigned`/`in_progress`. Trigger: after Home refresh and when the detail screen loads online. It fetches each `download_url` with the existing authenticated OkHttp client, writes to app-private storage, updates the row. Files are removed when the order leaves `assigned`/`in_progress`.
- Handler outcomes keep the current mapping (400 or 404 -> failed, 409 -> conflict, network -> retry).

### 3.4 UI

- **Home / Hub / detail:** the new kind appears under its own tab or section in the Home by-kind layout; detail (F09) gets a branch for this kind. The existing schedule window (start / cancel / overdue lock) applies unchanged.
- **Detail branch:** header, "Tệp PDF" list (state: not downloaded / downloading / ready / failed, tap opens the viewer), pole list (pole id, old GPS or "Chưa có", measured / not measured, accuracy), report note, "Nộp báo cáo" button.
- **PDF viewer:** one small screen rendering pages with Android `PdfRenderer` (platform API, no dependency), pinch zoom, 4 states (loading / ready / empty / error).
- **Measure screen (per pole):** live fix with accuracy readout, old position vs new position, "Lưu vị trí" enabled only when the fix accuracy is <= `GpsAccuracyGate.PROPOSED_THRESHOLD_METERS` (8 m) held for the gate's hold time; measuring again overwrites the pole's row. Needs location permission handling like the rest of the app.
- **Submit rule (`canSubmit`):** the existing report-note rule for this screen (the plan reads the current threshold from `WorkOrderDetailUiState.canSubmit`) and >= 1 measured pole. Unmeasured poles are allowed (partial report, PG-10), with a confirmation line "Còn N cột chưa đo".

### 3.5 Location source

`LocationTracker` only returns a one-shot `LatLng` without accuracy. Add a second method returning a `GpsFix(lat, lng, accuracyM, capturedAt)` stream (`requestLocationUpdates`, ~1 s). The plan decides whether to reuse `PreRecordGpsAccuracyTracker` instead; the existing `getCurrentLocation()` is not changed.

## 4. Failure modes the design must cover

1. **Pole with no position:** DTO parses, UI shows "Chưa có GPS", no crash.
2. **Offline at the pole:** measure and save work with no network; submit is queued, never blocked.
3. **Order returned:** server `pole_locations` prefill the screen; resubmitting must resend ALL poles or earlier ones are dropped.
4. **PDF removed or expired (404 `ATTACHMENT_NOT_FOUND`):** mark that file `failed`, keep the others.
5. **`captured_at` in the future (phone clock ahead):** server rejects with 400; mobile clamps `captured_at` to now and surfaces `VALIDATION_FAILED` as a permanent failure (not an endless retry).
6. **GPS never reaches 8 m (bad sky view):** the engineer cannot save that pole. See Open items.
7. **Order cancelled / reassigned while offline:** `complete` returns 409, handler marks `conflict`, local measurements are kept (never overwritten).
8. **Low storage:** PDF download failure is shown per file, not as an app error.

## 5. Testing

TDD per repo convention. DTO tests use JSON copied from `WorkOrderPoleGpsTests.cs` in the backend repo. Room DAO + `Migration(16,17)` test. ViewModel tests for detail branch, measure screen, `canSubmit`. Handler test for the `pole_locations` payload including the "resend everything" rule. Compose UI test for the pole list states. Real-device check only once `AddPoleGpsUpdateWorkOrders` is applied to `luxmap_dev` (not yet as of 2026-11-13).

## 6. Dependencies on others (not blockers for coding)

- BE must apply the migration to `luxmap_dev`/VPS before any end-to-end test; BE lists WP6 as "Chua bao" - mobile should tell them it is starting.
- `PUT /assets/poles/{id}/location` is still open to Field Engineers (BE caveat): an engineer can write `pole.geom` without a Manager `verify`. Mobile does not call it; ask BE whether it will be closed.

## 7. Open items

- **8 m gate:** the number is BE's proposal, "chot sau buoi quay thu". If field testing shows rural sites rarely reach it, switch from hard block to "save with warning".
- **QR:** v1 does not scan. If the Manager later wants scan-to-confirm, it is a client-side match of the QR text against `pole_ids` (needs a scan library and the QR payload format from BE).
- **Report note:** whether BE still requires `report_note` on `complete` for this kind is unverified; the plan confirms it against `WorkOrderPoleGpsTests.cs` before fixing the `canSubmit` rule.
