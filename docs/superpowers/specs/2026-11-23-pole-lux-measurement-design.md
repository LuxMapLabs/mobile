# Pole Lux Measurement — Design Spec

**Date:** 2026-11-23
**Status:** Business rules approved by the user in chat (2026-11-23); technical design below awaiting review

## 1. Background and confirmed decisions

The per-pole survey flow (`feature/survey/ui/polephoto/`) uploads one photo or one short video per pole, and the server AI returns `cv_state` = `on` / `off` / `inconclusive`. The AI only decides ON/OFF — `luxmap_backend` `YoloOnOffDetector.cs`: *"CV decides ON / OFF only; Dim comes from the lux sensor"*. A field lux reading is the missing input for the server to classify `dim`, and it is ground truth for RQ1 / CV-12.

Confirmed with the user (2026-11-23):
- **Q1 — Device (corrected 2026-11-24 on a real device):** the handheld **`ESP32C3_LUX`** meter, BLE only (not a fixed IoT node on the pole). The older `LuxMap_ESP32` (Classic Bluetooth SPP) is no longer supported.
- **Q2 — Link (revised 2026-11-23):** a reading is linked **directly to the photo/video it was taken with, on the server too** — not only by pole + time. This needs a backend change (section 2.1). Until BE ships it, the reading is sent by pole + `measured_at` and the link stays in Room.
- **Live capture only:** lux can be measured only together with a photo or video taken **now** with the camera. Gallery picks have no lux, because their capture time is unknown and cannot match a live reading.
- **Q3 — Verdict:** mobile shows the **raw, relative** value only. It never shows "dim", "pass" or "fail". The engineer's review stays ON/OFF only.

## 2. Backend contract (confirmed from source, not assumed)

Source: `luxmap_backend` `dev` @ `623d4e8` — `LuxReading.cs`, `LuxReadingConfiguration.cs`, `docs/api-contract-v1.1.md` section 5.7 (status `implemented`).

```
POST /api/v1/lux-readings          capability RecordLuxReading = field_engineer only
{
  "client_op_id": "uuid",           required, idempotency key
  "pole_id": "POLE-0047",           required
  "measured_at": "2026-11-23T12:42:00Z",  required, ISO 8601 UTC
  "lux_value": 12.4,                required, finite, >= 0, no upper bound (>200 logged, still stored)
  "data_source": "field",           required: field | calibration_rig
  "meter_model": "ESP32C3_LUX",    optional
  "note": "..."                     optional
}
201 new record · 200 same client_op_id already stored · 404 POLE_NOT_FOUND (also out of scope)
400 VALIDATION_FAILED · 400 SERVER_OWNED_FIELD if lux_id / commune_id is sent
Response: lux_id, client_op_id, pole_id, measured_at, lux_value, meter_model, data_source, note
```

Not used in v1: `GET /lux-readings`, `GET /lux-readings/poles/{poleId}`. The mobile shows only readings it took itself, from Room.

Known contract mismatch (to report to BE, not blocking): section 5.7 describes `lux_value` as the **phone's** light sensor and `meter_model` as the phone model. We send readings from the ESP32 meter with `meter_model = "LuxMap_ESP32"`.

### 2.1 Backend change requested — link a lux reading to a pole photo

Checked in source: `CreateLuxReadingRequest` (`LuxReadingContracts.cs`) has **no** photo field today, and an unknown body key is silently ignored, so mobile must not send one before BE adds it. The field name below is a **proposal**; mobile codes against whatever name BE confirms (contract version bump + `docs/contract-drift.md` entry).

Proposal for BE:
- `POST /lux-readings` gets an **optional** field `photo_id` — the server id of a `PolePhotoItem` (a photo, or the representative frame of a pole video).
- Validation: the photo exists, is in scope, and belongs to the same `pole_id`. Otherwise `400` / `404` with a clear code.
- `LuxReadingResponse` echoes `photo_id`.
- Optionally, `PolePhotoItem` exposes the linked reading (e.g. `lux_value`, `lux_measured_at`), so Web GIS shows it next to the photo without a second query.
- Additive only: readings without `photo_id` keep working.

## 3. Device protocol (read from the real `ESP32C3_LUX`, 2026-11-24)

- BLE GATT, **Nordic UART Service**: service `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`, TX notify characteristic `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` (CCCD `0x2902`). No bonding needed.
- One plain-text line per notification, about **4 lines per second**: `Lux: 0.83
`. Chunks are joined on `
` before parsing (`LuxLineBuffer`), a line that does not match is skipped.
- Discovery is a BLE scan (`BluetoothLeScanner`). Classic discovery does not find this device.
- `core/ble/LuxSensorBleClient` connects with `connectGatt(TRANSPORT_LE)`, enables notifications, and reconnects every 2s after a lost link until `disconnect()`.
- The older `LuxMap_ESP32` (Classic SPP, `yyyy-MM-dd HH:mm:ss | Light: 69.17 lux`, 1 line per second) is no longer supported (owner decision, 2026-11-24).

## 4. Business rules

- **R1 — Never blocks.** Measuring lux never blocks capture, upload, review or completing the work order. No paired device or a lost connection: the rest of the flow works as today.
- **R2 — Linked to the photo.** The server gets `pole_id` + `measured_at` + the server `photo_id` of the photo/video taken with it (once BE supports it, section 2.1). The photo id is never put into `note` as a workaround.
- **R3 — Relative value.** Always labelled "số đo tương đối". No dim / pass / fail wording anywhere.
- **R4 — Offline-first.** Write Room first, then enqueue a `sync_queue` op. It is a separate op from the photo/video upload and runs after it (to get the server `photo_id`). A failed photo upload never loses the reading (section 7.3).
- **R5 — Validation.** Cannot record when the value is negative, NaN or Infinity, the line failed to parse, or the connection dropped during the sampling window. Above 200 lux: allowed, with a soft hint ("Có thể đang đo ban ngày").
- **R6 — Source.** `data_source = field`. `calibration_rig` is out of scope for v1 (no testbed UI).
- **R7 — Fixed posture.** A one-line instruction on how to hold the meter, so readings stay comparable.
- **Re-measure.** Each "Ghi nhận" creates a new reading. The UI shows the latest one per pole. There is no delete, because the server has no delete API.

## 5. Sampling rule

- Use a window of the **last 12 valid samples** (about 3 seconds at 4 Hz, owner decision 2026-11-24).
- **Stable** means `max - min <= max(0.5 lux, 10% of the median)`.
- The "Ghi nhận" button is enabled only when the window is full and stable.
- Recorded value = **median** of the window, rounded to 2 decimals. `measured_at` = phone clock (UTC) at the moment of the tap.
- A disconnect clears the window.

The thresholds are a first proposal. Tune them after a real-device check.

## 6. UX

### 6.1 Pairing — Profile screen
- New row "Cảm biến lux" in `ProfileScreen`: shows the paired device name, or "Chưa ghép nối".
- Tap opens a pairing sheet: request `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`, BLE-scan automatically when the sheet opens (devices whose name contains "LUX" listed first), choose one, then save its address (`LuxDevicePreferences`, DataStore).
- "Bỏ ghép nối" clears the saved address.

### 6.2 Lux card — where it appears
- **Live capture only** (confirmed by the user 2026-11-23): the card appears in the **preview/confirm step** after "Chụp ảnh" (`PolePhotoCaptureScreen`) or "Quay video" (`PoleVideoCaptureScreen`), under the media preview and before "Lưu". The camera itself is the system app (Intent), so this is the closest point to the capture moment that we control.
- **Gallery picks** ("Chọn từ thư viện", "Video từ thư viện"): **no card**. Gallery media may have been taken at any time, so a lux value measured now cannot be paired with it.
- **No standalone "Đo lux" option.** A reading always belongs to one live-captured photo or video.

### 6.2.1 Order of steps (confirmed by the user 2026-11-23)

Lux is measured **right after** the capture, at the preview step, and **before** the AI result exists. It is not measured at the exact shutter moment: the system camera app runs in the foreground while capturing, so LuxMap cannot show or record anything then.

1. The engineer taps a pole, then "Chụp ảnh" or "Quay video".
2. The system camera returns the media to LuxMap, which shows the preview step before saving.
3. Optional: the engineer holds the ESP32 meter, watches the live value, and taps "Ghi nhận" once it is stable. The engineer can also skip this step.
4. The engineer taps "Lưu". The photo/video and the lux reading (if any) are saved in Room, then synced as **separate** `sync_queue` ops.
5. The server AI processes the media and returns `on` / `off` / `inconclusive`.
6. The AI result screen shows both: "AI: Sáng · Lux đo tại hiện trường: 12.4 (tương đối)".

Why before the AI result: the AI result is not immediate (upload + processing, possibly hours later when offline), and by then the engineer has left the pole. The lux reading does not depend on the AI result. It is separate input for the server's `dim` classification, not a check on the AI.

### 6.3 Card states (exhaustive)
| State | Shown | Action |
|---|---|---|
| `Collapsed` | one row "Đo lux (tuỳ chọn)" | tap to expand |
| `NotPaired` | "Chưa ghép nối máy đo lux" | "Ghép nối" → the pairing sheet, opened on the same screen |
| `PermissionMissing` | "Cần quyền Bluetooth" | "Cấp quyền" |
| `BluetoothOff` | "Bluetooth đang tắt" | "Bật Bluetooth" (system dialog) |
| `Connecting` | spinner + "Đang kết nối…" | — |
| `Live(value, stable)` | big number + "lux · số đo tương đối" + stability hint + R7 posture line | "Ghi nhận" (enabled when stable) |
| `Recorded(value, time)` | "Đã ghi 12.4 lux · 19:42" | "Đo lại" |
| `Disconnected` | "Mất kết nối máy đo" | "Thử lại" |
| `Unreachable` | after 2 failed attempts in a row: "Không kết nối được máy đo. Kiểm tra máy đo đã bật và ở gần điện thoại." (keeps retrying in the background, goes to `Live` as soon as it connects) | "Thử lại" |

The card is collapsed by default, with the title "Đo lux (tuỳ chọn)". Expanding it starts the connection; leaving the screen disconnects. There is no background service, so no foreground-service permission is needed.

### 6.4 Display elsewhere
- `PoleRow` (`PoleListScreen`): a secondary line "☀ 12.4 lux" with the sync badge (`LuxTheme.status.sync`), latest reading of that pole within this work order.
- `PolePhotoResultScreen`: a row "Lux đo tại hiện trường · 12.4 (tương đối)" under the AI conclusion, only from a reading linked to **this** `photo_id`. No fallback to other readings of the pole. Hidden when there is none (always the case for gallery media).
- Review card unchanged: ON/OFF only.

All UI uses vNext tokens only (`MaterialTheme.*`, `LuxTheme.*`), 48dp touch targets, and badge = icon + label.

## 7. Data layer

### 7.1 Room — `local_lux_reading` (DB 26 → 27, real `MIGRATION_26_27` `CREATE TABLE`, registered in `DatabaseModule` like `MIGRATION_25_26`, with a Room migration test)
| Column | Type | Note |
|---|---|---|
| `client_op_id` (PK) | String | UUID, also the sync idempotency key |
| `work_order_id` | String | for list/scope queries |
| `pole_id` | String | |
| `photo_id` | String | local id (`LocalPolePhotoEntity.photoId`) of the photo/video taken with this reading. The **server** id sent to BE is read at sync time from `LocalPolePhotoEntity.serverPhotoId` |
| `measured_at` | Instant | UTC |
| `lux_value` | Double | median of the window |
| `meter_model` | String | `"ESP32C3_LUX"` |
| `data_source` | String | `"field"` |
| `server_lux_id` | String? | filled from the 201/200 response |
| `created_at` | Instant | |

The sync status comes from the `sync_queue` row, joined like the pole-photo rows.

### 7.2 Repository
`LuxReadingRepository` (interface) + `RealLuxReadingRepository`, bound in `RepositoryModule`:
- `record(workOrderId, poleId, photoId, value, measuredAt)`: Room insert + enqueue, one transaction, called when the photo/video is saved.
- `observeLatestByPole(workOrderId): Flow<Map<PoleId, LuxReadingUi>>`
- `observeForPhoto(photoId)` / latest for a pole.

### 7.3 Sync
- `UploadLuxReadingSyncHandler`, registered in `SyncModule`'s `Set<SyncOpHandler>`, same pattern as `UploadPolePhotoSyncHandler`.
- **Dependency on the photo upload:** the server `photo_id` exists only after the photo/video upload succeeds. The lux op is enqueued with `dependsOnClientOpId` = the photo/video op's `client_op_id`, so it runs after it. At send time the handler reads `serverPhotoId` from `local_pole_photo` and puts it in the body.
- If the photo/video op ends `failed` (permanent), the lux reading is **still sent, without `photo_id`**, so the measurement is not lost. The server then has only pole + time for that reading.
- Before BE ships section 2.1, the handler sends no `photo_id` at all (one switch in the DTO mapping, removed when BE confirms).
- 201 / 200: save `server_lux_id`, then `done`.
- 404 `POLE_NOT_FOUND`, 400: permanent `failed`.
- Network errors and 5xx: retry with backoff.
- No 409 case exists on this endpoint.
- New `LuxReadingsApi` (Retrofit) + `CreateLuxReadingRequestDto` / `LuxReadingDto` with exact snake_case field names.

### 7.4 Bluetooth
- `core/ble` restored and simplified (section 3). `LuxSensorBleClient` exposes `connectionState: StateFlow` + `samples: SharedFlow`.
- A small `LuxSamplingWindow` (pure Kotlin, unit-tested) implements section 5.
- A `LuxMeterViewModel` (or state holder shared by the two capture ViewModels) maps client + window to the card states in 6.3.
- Manifest: `BLUETOOTH` / `BLUETOOTH_ADMIN` (`maxSdkVersion=30`), `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`. Foreground-service permissions are **not** restored.

No new library: Android Bluetooth is a platform API.

## 8. Tests
- Unit: `LuxPacketCodecTest` (restored), `LuxSamplingWindowTest` (stable/unstable/negative/NaN/disconnect reset), `RealLuxReadingRepositoryTest`, `UploadLuxReadingSyncHandlerTest` (201/200/404/400/IOException), ViewModel tests for card states.
- Compose UI: the lux card states. `PoleListScreenTest` and `PolePhotoResultScreenTest`: lux line shown or hidden.
- Manual on a real device (SM-A075F + ESP32): pairing, live value, record, offline record then sync, disconnect mid-window.

## 9. Delivery plan (each PR ≤ 400 changed lines per commit, PRs into `dev`)

1. **Restore `core/ble`** + simplified codec + `LuxSamplingWindow` + tests + manifest permissions.
2. **Data:** Room entity/DAO, DB v27, `LuxReadingsApi`, DTOs, repository, sync handler + tests.
3. **Pairing row** in Profile.
4. **Lux card** in the live photo/video preview step (not for gallery picks) + tests.
5. **Display:** pole row line + result screen row + tests.
6. **Send `photo_id`** — after BE confirms the field from section 2.1: DTO field, handler reads `serverPhotoId`, tests. Blocked on BE; PRs 1–5 do not wait for it.

## 10. Out of scope
- Manual lux entry (old F07) and `calibration_rig` UI.
- A mobile "dim" verdict, any pass/fail standard.
- Fixed IoT node telemetry.
- Reading other users' lux history from the server.
- Choosing the backend field name or validation for `photo_id` — BE decides (section 2.1 is only a proposal).
