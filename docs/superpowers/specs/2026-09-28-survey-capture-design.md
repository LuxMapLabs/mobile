# Khảo sát đêm (F03 + F04 core + F06 interface) — Design Spec

Ngày viết: 2026-09-28
Tác giả: Claude Code (phối hợp với thinh2509, vai trò WP6)
Trạng thái nguồn tham chiếu: `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx` mục C6/C7/C8 (cập nhật 2026-09-28), `docs/LuxMap_Mobile_Luong_Hoat_Dong_v1.0.md`, `CLAUDE.md`.

## 1. Phạm vi

Trong phạm vi đợt triển khai này:

- **F03 — Lập kế hoạch tuyến khảo sát**: danh sách tuyến do **Kỹ sư bảo trì** phân công (Field Engineer không tự tạo/chọn tuyến), checklist sẵn sàng kỹ thuật (camera, GPS, dung lượng, pin) chặn cứng trước khi vào F04.
- **Module lõi ghi dữ liệu phiên quay** (camera khoá cấu hình suốt phiên, track GPS/heading liên tục, cảm biến lux BLE) — độc lập, test được, chưa gắn UI.
- **F04 — Capture Mode**: nối các module lõi vào Room + UI, ghi và đóng gói 1 phiên khảo sát thành "Session package v0".
- **F06 — Nộp đợt khảo sát**: `UploadRepository` (interface) + `FakeUploadRepository` mô phỏng chunk/resume; chưa nối endpoint thật.
- `contract-drift.md` — nơi ghi mọi tên tạm/giả định.
- Cập nhật `CLAUDE.md` cho khớp với đặc tả mới nhất (bỏ F07, thay quy tắc đặt tên bảng/field F03/F04).

**Ngoài phạm vi đợt này** (giữ nguyên là khoảng trống, không code):

- **F05 (Kiểm tra độ phủ & chất lượng)** — nội dung cần kiểm tra gì trước khi nộp (độ phủ theo track GPS, khoảng trống log lux, tính toàn vẹn video) chưa được chốt lại theo mô hình quay video; đây là khoảng trống nghiệp vụ thật, không chỉ thiếu tên.
- **F07 (Nhập lux thủ công)** — đã bị loại bỏ khỏi đặc tả (cập nhật 2026-09-27). Không code màn này, không dùng `lux_reading`/`POST /api/v1/lux-readings`.
- **F08–F10 (Inspection Task / Repair Task)**, màn hình "FE xem & sửa kết quả AI" (C6 bước 4), nhánh từ chối ở 2 cổng duyệt — vẫn là khoảng trống thật, chờ WP2/WP5.
- Endpoint upload thật, MinIO — chờ Backend (WP2/WP5) quyết.

## 2. Thuật ngữ vai trò dùng trong spec này

- **Kỹ sư bảo trì** = vai trò `Manager` trong hệ thống (CLAUDE.md, `UserRole.FieldEngineer`/`Manager`...). Dùng đúng nhãn "Kỹ sư bảo trì" khi mô tả hành động **giao tuyến khảo sát** (khớp nguyên văn F03 trong đặc tả). Nhãn "Manager" chỉ dùng cho các bước duyệt (cổng duyệt #1/#2) như D1 đã mô tả — hai nhãn chỉ cùng 1 vai trò hệ thống, không phải 2 vai trò khác nhau.
- **Field Engineer** — vai trò duy nhất của app mobile này.

## 3. Kiến trúc tổng quan

```
F03 (ui/plan) --checklist--> [Nút "Vào chế độ khảo sát"]
                                     |
                                     v
F04 (ui/capture) --start--> SurveyCaptureService (foreground service)
                                     |
        +----------------------+----+----------------------+
        |                      |                           |
   Video segments         GPS + heading track          Lux BLE log
   (ExposureLockController +   (SurveyTrackRecorder +    (LuxSensorBleClient)
    segmented video writer)     HeadingSensor)
        |                      |                           |
        +----------------------+----+----------------------+
                                     |
                              [Dừng quay] -> PackageSurveySessionUseCase
                                     |
                          Room: local_survey_session (+ video_segment)
                                     |
                              F06 (ui/submit) -> UploadRepository (Fake)
```

Ba luồng video/GPS+heading/lux **độc lập hoàn toàn** khi đang chạy — lỗi một luồng không dừng hai luồng còn lại (đúng nguyên tắc C7/C8).

## 4. Module lõi (bước a — độc lập, có unit test)

| File | Vai trò | Ghi chú |
|---|---|---|
| `core/camera/ExposureLockController.kt` | Khoá AE/AF/AWB, ISO, shutter, tắt EIS/HDR/night mode — áp dụng và giữ **suốt phiên quay** (mở rộng từ khoá 1 lần chụp) | Thêm `checkTimestampSourceRealtime(): Boolean` đọc `SENSOR_INFO_TIMESTAMP_SOURCE` qua Camera2 |
| `core/location/SurveyTrackRecorder.kt` (mới) | Ghi track GPS liên tục theo `Location.getElapsedRealtimeNanos()`, ~1 điểm/giây | Không sửa `LocationTracker.kt` hiện có (đang phục vụ F12 one-shot) |
| `core/location/HeadingSensor.kt` | Đọc heading liên tục từ rotation vector sensor (SensorManager), tần suất riêng theo cảm biến | Đường dẫn đã có sẵn trong cấu trúc thư mục CLAUDE.md, chưa code — không phải package mới |
| `core/ble/LuxSensorBleClient.kt` (thư mục **mới**) | Kết nối GATT, subscribe notify, parse gói `(seq, module_ms, lux)`, gắn `elapsedRealtimeNanos` lúc nhận | Expose `Flow<LuxSample>` + `Flow<BleConnectionState>` |
| `core/camera/FrameTimestampLogger.kt` (mới) | Ghi `(frame_index, elapsed_realtime_ns, video_pts_us, segment)` mỗi frame (hoặc tối thiểu frame đầu mỗi segment) | `segment` = index của video segment chứa frame này |

Mỗi module: constructor injection qua Hilt, không phụ thuộc Room/UI, unit test bằng MockK (giả lập Camera2/Location/BLE callback).

## 5. Foreground Service & quyền

- `feature/survey/capture/SurveyCaptureService.kt` — foreground service, `android:foregroundServiceType="camera|location|connectedDevice"`.
- Thêm quyền vào `AndroidManifest.xml`: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+), cùng các quyền camera/location đã có.
- Service điều phối 4 module lõi ở mục 4, ghi file trực tiếp (không qua Room) trong lúc quay.
- `feature/survey/capture/PackageSurveySessionUseCase.kt` — chạy khi "Dừng quay": đóng video segment cuối, tính checksum từng file, ghi `manifest.json`, cập nhật `local_survey_session.recording_state = packaged`.

## 6. Room schema mới

### `local_survey_session`

| Field | Kiểu | Ghi chú |
|---|---|---|
| `session_id` | String (PK) | UUID sinh trên máy, dùng làm idempotency key khi upload |
| `survey_sweep_id` | String | Tuyến được Kỹ sư bảo trì giao (từ F03) |
| `road_segment_id` | String? | |
| `recording_state` | String | enum `recording / stopped / packaged` — vòng đời ghi dữ liệu, tách biệt khỏi đồng bộ |
| `sync_state` | String? | enum theo đúng P6.1: `queued / syncing / conflict / failed / done`; `null` cho tới khi `packaged` và được đẩy vào `sync_queue` |
| `started_at_utc`, `started_at_elapsed_ns` | Instant, Long | cặp mốc UTC↔elapsedRealtime đầu phiên (C8 mục 2) |
| `ended_at_utc`, `duration_seconds` | Instant?, Long? | |
| `distance_meters` | Double? | tính từ track GPS lúc dừng quay |
| `gps_track_file_path`, `lux_log_file_path`, `heading_log_file_path`, `frame_timestamp_log_file_path`, `capture_config_file_path`, `manifest_file_path` | String? | đường dẫn app-specific storage |
| `package_schema_version` | String | `"v0"` |
| `timestamp_source_realtime` | Boolean | kết quả kiểm tra ở F03, giữ lại cho phiên |
| `ble_gap_detected` | Boolean | đánh dấu nếu BLE mất kết nối giữa phiên (mục 9) |
| `created_at`, `updated_at` | Instant | |

Video **không** lưu path đơn trên bảng này — xem bảng riêng bên dưới (điểm sửa #1).

### `local_survey_video_segment` (bảng mới, tách theo yêu cầu quay theo segment)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `segment_id` | String (PK) | |
| `session_id` | String (FK → `local_survey_session`) | |
| `segment_index` | Int | 0-based, khớp với field `segment` trong `frame_timestamp_log.ndjson` |
| `file_path` | String | |
| `started_at_elapsed_ns`, `ended_at_elapsed_ns` | Long, Long? | |
| `size_bytes` | Long? | set khi segment đóng |
| `checksum_sha256` | String? | tính khi `PackageSurveySessionUseCase` chạy |

Lý do: quay 1 file video liên tục cả phiên (có thể 10-30 phút) rủi ro mất trắng nếu app crash; chia segment 2–5 phút/file để chỉ mất tối đa 1 segment nếu sự cố giữa chừng.

### `local_survey_plan`, `local_road_segment`

Tên bảng đã có sẵn ở mục P6 đặc tả (cache tuyến/đoạn đường cho F02–F05). Thiết kế field chi tiết sẽ chốt trong bước viết plan cho F03 (không thuộc phạm vi liệt kê schema mới ở spec này vì không phải bảng mới, không có quyết định đặt tên cần bạn duyệt).

## 7. "Session package v0" — hợp đồng file

Áp dụng `schema_version` khác nhau theo loại file (điểm sửa #7):
- File `.ndjson`: dòng đầu tiên là **header** `{"schema_version":"v0","file_role":"<tên>"}`, các dòng sau là dữ liệu.
- File `.json` đơn (`manifest.json`, `capture_config.json`): `schema_version` là **field bên trong JSON**, không dùng header line riêng (vì bản thân file đã là 1 object).

### `manifest.json`

```json
{
  "schema_version": "v0",
  "session_id": "...",
  "survey_sweep_id": "...",
  "road_segment_id": "...",
  "started_at_utc": "...",
  "ended_at_utc": "...",
  "files": [
    {"name": "segment_0.mp4", "role": "video_segment", "segment_index": 0, "checksum_sha256": "...", "size_bytes": 0},
    {"name": "gps_track.ndjson", "role": "gps_track", "checksum_sha256": "...", "size_bytes": 0},
    {"name": "heading_log.ndjson", "role": "heading_log", "checksum_sha256": "...", "size_bytes": 0},
    {"name": "lux_log.ndjson", "role": "lux_log", "checksum_sha256": "...", "size_bytes": 0},
    {"name": "frame_timestamp_log.ndjson", "role": "frame_timestamp_log", "checksum_sha256": "...", "size_bytes": 0},
    {"name": "capture_config.json", "role": "capture_config", "checksum_sha256": "...", "size_bytes": 0}
  ],
  "device": {"manufacturer": "...", "model": "...", "camera_id": "..."}
}
```

### `capture_config.json`

```json
{
  "schema_version": "v0",
  "utc_elapsed_anchor": {"utc_iso": "...", "elapsed_realtime_ns": 0},
  "camera": {
    "resolution": "1920x1080", "fps": 30, "iso": 0, "shutter_ns": 0,
    "af_locked": true, "awb_locked": true,
    "eis_disabled": true, "hdr_disabled": true, "night_mode_disabled": true,
    "sensor_timestamp_source": "REALTIME"
  },
  "device": {"manufacturer": "...", "model": "...", "camera_id": "...", "focal_length": 0},
  "lux_module_id": "..."
}
```

### `gps_track.ndjson`

```
{"schema_version":"v0","file_role":"gps_track"}
{"elapsed_realtime_ns":0,"lat":0.0,"lng":0.0,"accuracy_m":0.0,"gps_bearing_deg":0.0,"speed_mps":0.0}
```
`gps_bearing_deg` dùng timestamp của chính fix GPS đó (`elapsed_realtime_ns` trên cùng dòng) — không tách riêng thêm vì cùng nguồn `Location`.

### `heading_log.ndjson` (file mới, theo điểm sửa #8)

```
{"schema_version":"v0","file_role":"heading_log"}
{"elapsed_realtime_ns":0,"heading_deg":0.0}
```
Nguồn: rotation vector sensor, tần suất và mốc thời gian **độc lập với GPS** — đây là lý do tách file riêng thay vì gộp vào `gps_track.ndjson`.

### `lux_log.ndjson`

```
{"schema_version":"v0","file_role":"lux_log"}
{"seq":0,"module_ms":0,"phone_elapsed_ns":0,"lux":0.0}
```

### `frame_timestamp_log.ndjson` (sửa theo điểm #2)

```
{"schema_version":"v0","file_role":"frame_timestamp_log"}
{"frame_index":0,"elapsed_realtime_ns":0,"video_pts_us":0,"segment":0}
```
Server khớp theo `elapsed_realtime_ns` (thời gian thật), dùng `segment` + `video_pts_us` để định vị đúng frame trong đúng file video — không khớp theo `frame_index` đơn thuần vì có nhiều file segment.

## 8. F03 — Checklist sẵn sàng (thay đổi so với thiết kế ban đầu)

`CheckSurveyReadinessUseCase` (đã có trong cấu trúc dự kiến ở CLAUDE.md, chưa code) bổ sung:

- Danh sách tuyến hiển thị là tuyến **Kỹ sư bảo trì đã phân công** (đọc `GET /api/v1/survey-sweeps/planned`, đã cache) — không có nút tạo/sửa tuyến.
- Kiểm tra `SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME` (điểm sửa #5): nếu **khác** `REALTIME` → **chặn cứng**, coi thiết bị không được hỗ trợ khảo sát, hiển thị rõ lý do. Đây là chính sách **tạm thời** cho tới khi điểm mở #8 (C8) được chốt — code cần chú thích rõ điều này để dễ sửa lại sau.
- Các kiểm tra khác giữ nguyên theo đặc tả gốc (quyền camera, khả năng khoá exposure, GPS, dung lượng, pin).
- Kết nối BLE **không** kiểm tra ở F03 (đúng đặc tả — là điều kiện chặn ở Bước 0 của F04).

## 9. Xử lý mất kết nối BLE giữa phiên (điểm sửa #10 — chốt theo đề xuất)

Nếu `LuxSensorBleClient` báo mất kết nối sau khi phiên đã bắt đầu:
- **Không dừng phiên**, video và GPS/heading tiếp tục ghi bình thường.
- Đặt `local_survey_session.ble_gap_detected = true`.
- Cảnh báo người dùng bằng màu + chữ + rung (theo đúng nguyên tắc "trạng thái thể hiện bằng cả màu và chữ" P3).
- `LuxSensorBleClient` tự thử kết nối lại ở nền; nếu kết nối lại được, tiếp tục ghi log lux bình thường (khoảng trống được server phát hiện qua `seq` không liên tục, không nội suy — đúng C8 mục 1).

Đây vẫn là điểm mở thật (#4 trong danh sách 9 điểm mở của C8) — hành vi trên là lựa chọn mặc định hợp lý theo nguyên tắc chung, cần xác nhận lại với chủ dự án khi điểm mở này được chốt chính thức.

## 10. F06 — Upload interface

- `feature/survey/data/UploadRepository.kt` (interface): `suspend fun uploadSession(sessionId: String): Flow<UploadProgress>` với hỗ trợ resume (idempotency key = `session_id`, checksum từng file theo `manifest.json`).
- `feature/survey/data/FakeUploadRepository.kt`: mô phỏng tiến độ theo byte, ngắt giữa chừng rồi resume, lỗi mạng → theo đúng hành vi offline-first mô tả trong CLAUDE.md (không chỉ trả JSON tĩnh).
- Khi Backend chốt endpoint thật: chỉ thay `RealUploadRepository`, không sửa UI/ViewModel (bind qua Hilt `RepositoryModule` như các feature khác).
- Đẩy vào `sync_queue` với loại mục mới (tên do WP6 đặt, ví dụ `survey_session_package` — ghi vào `contract-drift.md` vì đây là tên nội bộ, không phải tên bảng Room nhưng là giá trị enum cần theo dõi).

## 11. `contract-drift.md`

Tạo mới ở `docs/contract-drift.md` (đã kiểm tra: chưa tồn tại — điểm sửa #9). Nội dung ban đầu: bảng liệt kê mọi tên tạm/giả định trong đợt triển khai này (tên bảng Room đã chốt chính thức thì KHÔNG liệt kê ở đây — chỉ liệt kê phần còn chờ WP2/WP5: endpoint upload thật, tên field phía server cho gói phiên, giá trị enum `sync_queue` loại mục mới).

## 12. Cập nhật `CLAUDE.md`

Thực hiện trong 1 commit riêng, TRƯỚC khi code (theo đúng yêu cầu):
1. Mục "Khoảng trống đã biết" — F07: sửa thành "đã loại bỏ khỏi đặc tả (2026-09-27), không code".
2. Thay đoạn quy tắc "không tự đặt tên bảng/field khi code F03/F04" bằng tóm tắt phân quyền C8 mục 5 (Room do WP6 đặt chính thức; định dạng file "Session package" do mobile soạn, WP4 duyệt; endpoint do Backend quyết).
3. Thêm dòng trỏ tới `docs/contract-drift.md` là nơi theo dõi các điểm lệch/tên tạm.

## 13. Testing

- Unit test (MockK) cho từng module lõi ở mục 4 — test riêng exposure lock giữ nguyên cấu hình khi được gọi lại nhiều lần, test parse gói BLE với gói bị thiếu `seq`, test ghi track GPS đúng định dạng.
- `PackageSurveySessionUseCase`: test tạo đúng `manifest.json`, checksum khớp file thật, xử lý đúng khi có nhiều segment video.
- Room: test DAO cho `local_survey_session`/`local_survey_video_segment` (Room Testing, theo CLAUDE.md).
- `FakeUploadRepository`: test resume sau khi ngắt giữa chừng, test idempotency khi gọi lại cùng `session_id`.
- Compose UI test cho F03 (đủ 4 trạng thái: loading/có dữ liệu/rỗng/lỗi + trạng thái chặn khi timestamp source không phải REALTIME).

## 14. Thứ tự triển khai

(a) Module lõi (mục 4) — độc lập, có test.
(b) F03 (mục 8) — song song với (a), dùng API đã có.
(c) Nối (a) vào Room (mục 6) + UI F04, ghi đủ 6 file + video segment(s) + manifest.
(d) F06 (mục 10) trên `UploadRepository` + `FakeUploadRepository`.

Mỗi bước tuân thủ giới hạn commit ≤400 dòng của CLAUDE.md — bước (c) chắc chắn cần chia nhiều commit nhỏ (Service khung → ghi video segment → ghi GPS/heading → ghi lux BLE → đóng gói/manifest → Room).
