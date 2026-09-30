# Khảo sát đêm (F03 + F04 core + F06 interface) — Design Spec

Ngày viết: 2026-09-28
Tác giả: Claude Code (phối hợp với thinh2509, vai trò WP6)
Trạng thái nguồn tham chiếu: `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx` mục C6/C7/C8 (cập nhật 2026-09-28), `docs/LuxMap_Mobile_Luong_Hoat_Dong_v1.0.md`, `CLAUDE.md`.

## 1. Phạm vi

Trong phạm vi đợt triển khai này:

- **Spike kỹ thuật bắt buộc trên máy thật** (bước a0, mục 4) — làm TRƯỚC mọi bước khác.
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
- Hợp đồng BLE firmware thật (UUID, byte layout) — mục 9 dưới đây chỉ là **đề xuất** để code, cần chốt cùng người phụ trách firmware ESP32.

## 2. Thuật ngữ vai trò dùng trong spec này

- **Giả định, chưa xác nhận:** spec này coi **Kỹ sư bảo trì** và vai trò `Manager` trong hệ thống (CLAUDE.md, `UserRole.Manager`) là cùng một vai trò — dùng nhãn "Kỹ sư bảo trì" khi mô tả hành động **giao tuyến khảo sát** (khớp nguyên văn F03 trong đặc tả), dùng nhãn "Manager" cho các bước duyệt (cổng duyệt #1/#2) như D1 mô tả. **Đây là giả định của spec này, chưa được bạn xác nhận chính thức** — nếu sai, các đoạn liên quan tới "Kỹ sư bảo trì giao tuyến" cần sửa lại.
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
   (Camera2 -> MediaCodec    (SurveyTrackRecorder +    (LuxSensorBleClient)
    -> MediaMuxer, xoay       HeadingSensor)
    segment tại keyframe)
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

Khi app khởi động lại giữa chừng (bị kill/crash), có luồng khôi phục riêng — xem mục 12.

## 4. Spike kỹ thuật bắt buộc trước khi code (bước a0)

Làm **trước mọi bước khác** trong mục "Thứ tự triển khai" (mục 15). Trên tối thiểu 2–3 máy thật khác model trong nhóm, kiểm tra:

1. Pipeline **Camera2 → MediaCodec (surface input) → MediaMuxer** giữ được cấu hình đã khoá (AE/AF/AWB, ISO, shutter, tắt EIS/HDR/night mode) ổn định suốt phiên quay dài (≥10 phút), hệ thống không tự khôi phục AE/AF giữa chừng.
2. Chuyển segment (đóng `MediaMuxer` cũ, mở `MediaMuxer` mới) đúng tại **keyframe**, không rơi mất frame ở ranh giới, không giật hình.
3. PTS do `MediaCodec` sinh ra có khớp (cùng hệ quy chiếu, độ lệch nằm trong ngưỡng chấp nhận được) với `SENSOR_TIMESTAMP` của Camera2 hay không — nếu lệch, đo độ lệch trung bình để ghi vào `capture_config.json` hoặc tính phương án bù trừ.
4. Giá trị `SENSOR_INFO_TIMESTAMP_SOURCE` thực tế trên từng máy thật của nhóm.

Kết quả spike quyết định: pipeline này có dùng được không, bitrate/keyframe interval thực tế cho profile camera, danh sách thiết bị được hỗ trợ. Nếu spike phát hiện vấn đề chặn (ví dụ PTS lệch không kiểm soát được), dừng lại xin ý kiến trước khi code tiếp — không tự chọn phương án thay thế.

## 5. Module lõi (bước a — độc lập, có unit test)

| File | Vai trò | Ghi chú |
|---|---|---|
| `core/camera/SegmentedVideoRecorder.kt` (mới) | Quay video theo pipeline **Camera2 → MediaCodec (surface input) → MediaMuxer** (không dùng `MediaRecorder`/CameraX `VideoCapture`), tự xoay sang file `MediaMuxer` mới mỗi 2–5 phút tại keyframe | Thông số cụ thể (bitrate, keyframe interval) chốt sau spike ở mục 4 |
| `core/camera/ExposureLockController.kt` | Khoá AE/AF/AWB, ISO, shutter, tắt EIS/HDR/night mode — áp dụng và giữ **suốt phiên quay** (mở rộng từ khoá 1 lần chụp) | Thêm `checkTimestampSourceRealtime(): Boolean` đọc `SENSOR_INFO_TIMESTAMP_SOURCE` qua Camera2 |
| `core/location/SurveyTrackRecorder.kt` (mới) | Ghi track GPS liên tục theo `Location.getElapsedRealtimeNanos()`, ~1 điểm/giây | Không sửa `LocationTracker.kt` hiện có (đang phục vụ F12 one-shot) |
| `core/location/HeadingSensor.kt` | Đọc heading liên tục từ rotation vector sensor (SensorManager), tần suất riêng theo cảm biến | Đường dẫn đã có sẵn trong cấu trúc thư mục CLAUDE.md, chưa code — không phải package mới |
| `core/ble/LuxSensorBleClient.kt` (thư mục **mới**) | Kết nối GATT, subscribe notify, parse gói `(seq, module_ms, lux)`, gắn `elapsedRealtimeNanos` lúc nhận | Expose `Flow<LuxSample>` + `Flow<BleConnectionState>`; byte layout gói — xem mục 9 |
| `core/camera/FrameTimestampLogger.kt` (mới) | Ghi `(frame_index, sensor_timestamp_ns, video_pts_us, segment)` mỗi frame (hoặc tối thiểu frame đầu mỗi segment) | `segment` = index của video segment chứa frame này; tên field sửa theo mục 8 |

Mỗi module: constructor injection qua Hilt, không phụ thuộc Room/UI, unit test bằng MockK (giả lập Camera2/Location/BLE callback).

## 6. Foreground Service & quyền

- `feature/survey/capture/SurveyCaptureService.kt` — foreground service, `android:foregroundServiceType="camera|location|connectedDevice"`.
- Thêm quyền vào `AndroidManifest.xml`: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (API 31+), cùng các quyền camera/location đã có.
- Service điều phối các module lõi ở mục 5, ghi file trực tiếp (không qua Room) trong lúc quay.
- `feature/survey/capture/PackageSurveySessionUseCase.kt` — chạy khi "Dừng quay" (hoặc khi khôi phục sau crash, mục 12): đóng video segment cuối, tính checksum từng file, ghi `manifest.json`, cập nhật `local_survey_session.recording_state = packaged`.

## 7. Room schema mới

### `local_survey_session`

| Field | Kiểu | Ghi chú |
|---|---|---|
| `session_id` | String (PK) | UUID sinh trên máy, dùng làm idempotency key khi upload |
| `survey_sweep_id` | String | Tuyến được Kỹ sư bảo trì giao (từ F03) |
| `recording_state` | String | enum `recording / stopped / packaged` — vòng đời ghi dữ liệu, tách biệt khỏi đồng bộ |
| `sync_state` | String? | enum theo đúng P6.1: `queued / syncing / conflict / failed / done`; `null` cho tới khi `packaged` và được đẩy vào `sync_queue` |
| `started_at_utc`, `started_at_elapsed_ns` | Instant, Long | cặp mốc UTC↔elapsedRealtime đầu phiên (C8 mục 2) |
| `ended_at_utc`, `duration_seconds` | Instant?, Long? | |
| `distance_meters` | Double? | tính từ track GPS lúc dừng quay |
| `gps_track_file_path`, `lux_log_file_path`, `heading_log_file_path`, `frame_timestamp_log_file_path`, `capture_config_file_path`, `manifest_file_path` | String? | đường dẫn app-specific storage |
| `package_schema_version` | String | `"v0"` |
| `timestamp_source_realtime` | Boolean | kết quả kiểm tra ở F03, giữ lại cho phiên |
| `ble_gap_detected` | Boolean | đánh dấu nếu BLE mất kết nối giữa phiên (mục 11) |
| `created_at`, `updated_at` | Instant | |

Bỏ `road_segment_id` khỏi bảng này (điểm sửa #8) — 1 tuyến (`survey_sweep_id`) có thể gồm nhiều đoạn đường, không gán được 1 giá trị `road_segment_id` duy nhất cho cả phiên. `survey_sweep_id` là đủ để truy vết phiên về đúng tuyến.

Video **không** lưu path đơn trên bảng này — xem bảng riêng bên dưới.

### `local_survey_video_segment` (bảng mới, tách theo yêu cầu quay theo segment)

| Field | Kiểu | Ghi chú |
|---|---|---|
| `segment_id` | String (PK) | |
| `session_id` | String (FK → `local_survey_session`) | |
| `segment_index` | Int | 0-based, khớp với field `segment` trong `frame_timestamp_log.ndjson` |
| `file_path` | String | |
| `started_at_elapsed_ns`, `ended_at_elapsed_ns` | Long, Long? | `ended_at_elapsed_ns == null` nghĩa là segment chưa đóng xong (dùng để phát hiện phiên bị crash giữa chừng — mục 12) |
| `size_bytes` | Long? | set khi segment đóng |
| `checksum_sha256` | String? | tính khi `PackageSurveySessionUseCase` chạy |

Lý do: quay 1 file video liên tục cả phiên (có thể 10–30 phút) rủi ro mất trắng nếu app crash; chia segment 2–5 phút/file để chỉ mất tối đa 1 segment nếu sự cố giữa chừng.

### `local_survey_plan`, `local_road_segment`

Tên bảng đã có sẵn ở mục P6 đặc tả (cache tuyến/đoạn đường cho F02–F05). Thiết kế field chi tiết sẽ chốt trong bước viết plan cho F03 (không thuộc phạm vi liệt kê schema mới ở spec này vì không phải bảng mới, không có quyết định đặt tên cần bạn duyệt).

## 8. "Session package v0" — hợp đồng file

Áp dụng `schema_version` khác nhau theo loại file:
- File `.ndjson`: dòng đầu tiên là **header** `{"schema_version":"v0","file_role":"<tên>"}`, các dòng sau là dữ liệu.
- File `.json` đơn (`manifest.json`, `capture_config.json`): `schema_version` là **field bên trong JSON**, không dùng header line riêng (vì bản thân file đã là 1 object).

### `manifest.json`

```json
{
  "schema_version": "v0",
  "session_id": "...",
  "survey_sweep_id": "...",
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

`road_segment_id` bỏ khỏi manifest cùng lý do với `local_survey_session` (mục 7).

### `capture_config.json`

```json
{
  "schema_version": "v0",
  "utc_elapsed_anchor": {"utc_iso": "...", "elapsed_realtime_ns": 0},
  "camera": {
    "resolution": "1920x1080", "fps": 30, "iso": 0, "shutter_ns": 0,
    "codec": "video/avc", "bitrate_bps": 0, "keyframe_interval_s": 0,
    "af_locked": true, "awb_locked": true,
    "eis_disabled": true, "hdr_disabled": true, "night_mode_disabled": true,
    "sensor_timestamp_source": "REALTIME"
  },
  "segment_duration_s": 0,
  "device": {"manufacturer": "...", "model": "...", "camera_id": "...", "focal_length": 0},
  "app_version": "...",
  "lux_module_id": "...",
  "lux_module_firmware": "..."
}
```

Thêm so với bản trước (điểm sửa #5): `codec`, `bitrate_bps`, `keyframe_interval_s`, `segment_duration_s`, `app_version`, `lux_module_firmware` — cần để server/WP4 biết chính xác cấu hình đã dùng khi xử lý video, và để truy vết phiên bị lỗi về đúng phiên bản app/firmware.

### `gps_track.ndjson`

```
{"schema_version":"v0","file_role":"gps_track"}
{"elapsed_realtime_ns":0,"lat":0.0,"lng":0.0,"accuracy_m":0.0,"gps_bearing_deg":0.0,"speed_mps":0.0}
```
`gps_bearing_deg` dùng timestamp của chính fix GPS đó (`elapsed_realtime_ns` trên cùng dòng) — không tách riêng thêm vì cùng nguồn `Location`.

### `heading_log.ndjson`

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
Byte layout gói BLE gốc trước khi app parse thành JSON — xem mục "Hợp đồng BLE với firmware" (mục 9).

### `frame_timestamp_log.ndjson`

```
{"schema_version":"v0","file_role":"frame_timestamp_log"}
{"frame_index":0,"sensor_timestamp_ns":0,"video_pts_us":0,"segment":0}
```
Đổi tên field `elapsed_realtime_ns` → **`sensor_timestamp_ns`** (điểm sửa #7): giá trị này lấy từ `SENSOR_TIMESTAMP` của Camera2, không phải trực tiếp từ `SystemClock.elapsedRealtimeNanos()` như 3 file log kia — tên field cần phản ánh đúng nguồn, dù về lý thuyết 2 giá trị này cùng hệ quy chiếu khi `SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME` (xác nhận bằng spike ở mục 4). Server khớp theo `sensor_timestamp_ns`, dùng `segment` + `video_pts_us` để định vị đúng frame trong đúng file video — không khớp theo `frame_index` đơn thuần vì có nhiều file segment.

## 9. Hợp đồng BLE với firmware ESP32 (đề xuất — chưa chốt)

Soạn cùng người phụ trách firmware trước khi code `LuxSensorBleClient`. Nội dung cần chốt:

- **Service UUID** và **Characteristic UUID** (notify) của module lux.
- **Byte layout** của mỗi gói: kiểu dữ liệu và **endianness** của từng field.
  - Đề xuất ban đầu: `seq` = uint16 little-endian, `module_ms` = uint32 little-endian, `lux` = float32 little-endian.
- **Độ rộng và hành vi quay vòng (wraparound) của `seq`** — ví dụ nếu uint16, quay vòng ở 65536: app phải tính khoảng trống có tính tới quay vòng, không coi mọi lần `seq` giảm là mất toàn bộ gói.
- **Trường `boot_id`** (hoặc tương đương) tăng dần mỗi lần module khởi động lại/mất nguồn — để app/server phát hiện module đã reset giữa phiên (khi đó `module_ms` và `seq` về lại giá trị nhỏ, không được hiểu nhầm là thời gian chạy ngược).

Các giá trị cụ thể ở trên là **đề xuất để có cái bắt đầu code `LuxSensorBleClient`**, chưa phải quyết định cuối — cập nhật `contract-drift.md` (mục 14) nếu giá trị thật khác khi chốt cùng firmware.

## 10. F03 — Checklist sẵn sàng

`CheckSurveyReadinessUseCase` (đã có trong cấu trúc dự kiến ở CLAUDE.md, chưa code) bổ sung:

- Danh sách tuyến hiển thị là tuyến **Kỹ sư bảo trì đã phân công** (đọc `GET /api/v1/survey-sweeps/planned`, đã cache) — không có nút tạo/sửa tuyến.
- Kiểm tra `SENSOR_INFO_TIMESTAMP_SOURCE == REALTIME`: nếu **khác** `REALTIME` → **chặn cứng**, coi thiết bị không được hỗ trợ khảo sát, hiển thị rõ lý do. Đây là chính sách **tạm thời** cho tới khi điểm mở #8 (C8) được chốt — code cần chú thích rõ điều này để dễ sửa lại sau.
- Các kiểm tra khác giữ nguyên theo đặc tả gốc (quyền camera, khả năng khoá exposure, GPS, dung lượng, pin).
- Kết nối BLE **không** kiểm tra ở F03 (đúng đặc tả — là điều kiện chặn ở Bước 0 của F04).

## 11. Xử lý mất kết nối BLE giữa phiên (chốt theo đề xuất)

Nếu `LuxSensorBleClient` báo mất kết nối sau khi phiên đã bắt đầu:
- **Không dừng phiên**, video và GPS/heading tiếp tục ghi bình thường.
- Đặt `local_survey_session.ble_gap_detected = true`.
- Cảnh báo người dùng bằng màu + chữ + rung (theo đúng nguyên tắc "trạng thái thể hiện bằng cả màu và chữ" P3).
- `LuxSensorBleClient` tự thử kết nối lại ở nền; nếu kết nối lại được, tiếp tục ghi log lux bình thường (khoảng trống được server phát hiện qua `seq` không liên tục, không nội suy — đúng C8 mục 1).

Đây vẫn là điểm mở thật (#4 trong danh sách 9 điểm mở của C8) — hành vi trên là lựa chọn mặc định hợp lý theo nguyên tắc chung, cần xác nhận lại với chủ dự án khi điểm mở này được chốt chính thức.

## 12. Khôi phục phiên sau khi app bị kill giữa lúc quay

- Mọi file `.ndjson` (`gps_track`, `heading_log`, `lux_log`, `frame_timestamp_log`) được **flush định kỳ ~1 giây** trong lúc quay, không chờ tới lúc dừng quay mới ghi xuống đĩa — giảm dữ liệu mất khi crash.
- Khi app khởi động (hoặc mở lại F04), kiểm tra Room: nếu có `local_survey_session.recording_state == recording` (nghĩa là lần trước app bị kill/crash giữa phiên, không đi qua đúng luồng "Dừng quay"):
  1. Tìm `local_survey_video_segment` của phiên đó có `ended_at_elapsed_ns == null` (segment đang ghi dở) → **bỏ segment này** (file nhiều khả năng hỏng do `MediaMuxer` chưa được đóng đúng cách).
  2. Chuyển `recording_state` sang `stopped`.
  3. Chạy `PackageSurveySessionUseCase` trên phần dữ liệu còn lại (các segment đã đóng hoàn chỉnh trước đó + log tới thời điểm flush cuối cùng).
- Khi đọc file `.ndjson` để đóng gói, reader phải **bỏ qua dòng cuối cùng nếu ghi dở dang** (ví dụ parse JSON lỗi ở dòng cuối) — vì flush theo chu kỳ có thể cắt giữa lúc ghi 1 dòng.
- **Test bắt buộc cho luồng này**: giả lập trạng thái sau crash (session ở `recording`, 1 video segment có `ended_at_elapsed_ns == null`, file `.ndjson` có dòng cuối bị cắt) → xác nhận: segment dở bị loại, các file log được đọc đúng (bỏ dòng cuối hỏng), `manifest.json` được tạo đúng cho phần dữ liệu còn lại, `recording_state` chuyển đúng thành `packaged`.

## 13. F06 — Upload interface

- `feature/survey/data/UploadRepository.kt` (interface): `fun uploadSession(sessionId: String): Flow<UploadProgress>` (bỏ `suspend` — điểm sửa #8, hàm trả `Flow` không cần đánh dấu `suspend`) với hỗ trợ resume (idempotency key = `session_id`, checksum từng file theo `manifest.json`).
- `feature/survey/data/FakeUploadRepository.kt`: mô phỏng tiến độ theo byte, ngắt giữa chừng rồi resume, lỗi mạng → theo đúng hành vi offline-first mô tả trong CLAUDE.md (không chỉ trả JSON tĩnh).
- Khi Backend chốt endpoint thật: chỉ thay `RealUploadRepository`, không sửa UI/ViewModel (bind qua Hilt `RepositoryModule` như các feature khác).
- Đẩy vào `sync_queue` với loại mục mới (tên do WP6 đặt, ví dụ `survey_session_package` — ghi vào `contract-drift.md` vì đây là tên nội bộ, không phải tên bảng Room nhưng là giá trị enum cần theo dõi).

## 14. `contract-drift.md`

Tạo mới ở `docs/contract-drift.md` (đã kiểm tra: chưa tồn tại). Nội dung ban đầu: bảng liệt kê mọi tên tạm/giả định trong đợt triển khai này — tên bảng Room đã chốt chính thức thì KHÔNG liệt kê ở đây; liệt kê phần còn chờ xác nhận:
- Endpoint upload thật, MinIO (chờ Backend WP2/WP5).
- Tên field phía server cho gói phiên (chờ WP2/WP5).
- Giá trị enum `sync_queue` loại mục mới, ví dụ `survey_session_package` (WP6 đặt, ghi lại để dễ đối chiếu).
- UUID/byte layout/`boot_id` của hợp đồng BLE firmware (mục 9 — chờ chốt cùng firmware).
- Giả định "Kỹ sư bảo trì = Manager" (mục 2 — chờ bạn xác nhận).

## 15. Cập nhật `CLAUDE.md`

Thực hiện trong 1 commit riêng, TRƯỚC khi code:
1. Mục "Khoảng trống đã biết" — F07: sửa thành "đã loại bỏ khỏi đặc tả (2026-09-27), không code".
2. Thay đoạn quy tắc "không tự đặt tên bảng/field khi code F03/F04" bằng tóm tắt phân quyền C8 mục 5 (Room do WP6 đặt chính thức; định dạng file "Session package" do mobile soạn, WP4 duyệt; endpoint do Backend quyết).
3. Thêm dòng trỏ tới `docs/contract-drift.md` là nơi theo dõi các điểm lệch/tên tạm.

## 16. Testing

- Unit test (MockK) cho từng module lõi ở mục 5 — test riêng exposure lock giữ nguyên cấu hình khi được gọi lại nhiều lần, test parse gói BLE với gói bị thiếu `seq` và với `boot_id` đổi giữa chừng, test ghi track GPS đúng định dạng.
- `PackageSurveySessionUseCase`: test tạo đúng `manifest.json`, checksum khớp file thật, xử lý đúng khi có nhiều segment video, test luồng khôi phục sau crash (mục 12).
- Room: test DAO cho `local_survey_session`/`local_survey_video_segment` (Room Testing, theo CLAUDE.md).
- `FakeUploadRepository`: test resume sau khi ngắt giữa chừng, test idempotency khi gọi lại cùng `session_id`.
- Compose UI test cho F03 (đủ 4 trạng thái: loading/có dữ liệu/rỗng/lỗi + trạng thái chặn khi timestamp source không phải REALTIME).
- **Checklist kiểm thử trên thiết bị thật** (theo P5.1, không chỉ MockK/giả lập):
  - Quay thử liên tục ≥10 phút trên từng máy thật trong danh sách hỗ trợ, xác nhận cấu hình camera không bị hệ thống khôi phục giữa chừng.
  - Xác nhận chuyển segment không mất frame, không giật hình (nghe/xem lại video ghép).
  - Đo độ lệch thực tế giữa `video_pts_us` và `sensor_timestamp_ns` trên từng máy.
  - Kiểm tra kết nối BLE thật với module ESP32+BH1750, thử rút nguồn module giữa phiên để xác nhận hành vi ở mục 11 và việc phát hiện `boot_id` đổi.
  - Kiểm thử ở điều kiện ánh sáng yếu thực tế ngoài trời (không chỉ trong nhà/trình giả lập).

## 17. Thứ tự triển khai

**(a0) Spike kỹ thuật (mục 4) — làm trước mọi bước khác, trên máy thật.**
(a) Module lõi (mục 5) — độc lập, có test.
(b) F03 (mục 10) — song song với (a), dùng API đã có.
(c) Nối (a) vào Room (mục 7) + UI F04, ghi đủ 6 file + video segment(s) + manifest, gồm cả luồng khôi phục sau crash (mục 12).
(d) F06 (mục 13) trên `UploadRepository` + `FakeUploadRepository`.

Mỗi bước tuân thủ giới hạn commit ≤400 dòng của CLAUDE.md — bước (c) chắc chắn cần chia nhiều commit nhỏ (Service khung → ghi video segment → ghi GPS/heading → ghi lux BLE → khôi phục sau crash → đóng gói/manifest → Room).
