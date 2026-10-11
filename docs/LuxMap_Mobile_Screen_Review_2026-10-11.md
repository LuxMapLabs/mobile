# LuxMap — Review thiết kế so với code (2026-10-11)

Đối chiếu `LuxMap App.dc.html` với `mobile/` (NavGraph, *UiState, CLAUDE.md, contract-drift.md).

> Tài liệu nền cho roadmap redesign `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md`. Các điểm ở mục A đã dùng để định hình nội dung từng phase trong roadmap đó — đọc cùng nhau.

## A. Lệch với code / quy tắc đã chốt (sửa trước khi bàn giao)

1. **Token màu.** Thiết kế dùng palette mới (paper #F5F4F0, ink #15171C, lumen #3448D6). CLAUDE.md quy định lấy đúng Design System v2.0 (Navy700 #1F3864, Gray25 #F8FAFC…), không tự chọn màu. Cần chủ dự án duyệt đổi token, hoặc quay về v2.0. **Đã duyệt 2026-10-11** — xem `docs/LuxMap_Mobile_Design_System_v2.1.md` và Phase 0 của roadmap.
2. **Đích sau đăng nhập.** Code: Login → **Bản đồ** (`Routes.Map`, cũng là gốc back stack). Prototype: Login → Việc hôm nay. Chưa giải quyết — roadmap Phase 11 flag đây là quyết định sản phẩm, không phải style.
3. **Cổng GPS F04.** Thiết kế ghi "cần ≤ 10 m". Code: `GpsAccuracyGate` ≤ **8 m giữ 3 s** (PROPOSED). Copy nên nêu cả điều kiện giữ ổn định.
4. **Cảm biến lux là Bluetooth classic (SPP), không phải BLE.** Đổi nhãn "BLE" thành "Bluetooth"; danh sách thiết bị nên hiện thiết bị đã ghép đôi (LuxMap_ESP32), RSSI kiểu BLE không có.
5. **F02 thẻ việc vượt dữ liệu có thật.** List API chỉ trả `work_order_id`, `wo_status`, `due_date`, `priority_score` (số thô). Địa chỉ, loại lỗi, khoảng cách, tên xã không có; cụm hiển thị `commune_id` (COM-001). Thẻ "Tiếp theo" cần bản rút gọn theo đúng field.
6. **F06 "Chỉ tải lên qua Wi‑Fi".** Code chưa có tuỳ chọn/constraint này. Tiến độ theo byte cũng chưa có (chỉ 0% / 100% mỗi file). Clip sẽ đổi 180 s → 60 s.
7. **Hoàn thành lệnh.** Code: ghi chú ≥ 10 ký tự; Kiểm tra cần kết quả cho **từng fault** (`faultOutcomes`), không phải từng cột; Sửa chữa cần evidence. Chỉnh copy 1t/1u/1v cho khớp.

## B. Màn/trạng thái có trong code nhưng thiếu trong thiết kế

- **F05 Xem lại video** (`SurveyReview` → `CoverageScreen`): phát video, Huỷ / Quay lại, Duyệt → Nộp. Thiết kế đi thẳng từ quay sang nộp.
- **Chi tiết cột** (`PoleDetail`): đã có màn, đủ 4 trạng thái (Empty = 404 ngoài phạm vi).
- **Chụp bằng chứng** (`WorkOrderEvidenceCapture`) của F10.
- **F04:** `Connecting`, `ScanTimedOut`, `StartingRecording`, `PackagingFailed`, cảnh báo **mất kết nối cảm biến lux** khi đang quay (`bleGapDetected`).
- **F06:** `Failed`, `Conflict` (409), `NotPackagedYet`, `Uploading` khi offline, `Done`.
- **4 trạng thái bắt buộc** (Loading / Empty / Error) còn thiếu ở: Lệnh chi tiết (F09), Hoàn thành (F10), Bản đồ (F12, kể cả stale), Khảo sát F03 (Empty/Error).
- **Đăng xuất:** `isLoggingOut` và hộp xác nhận.

## C. Thiết kế có nhưng code/backend chưa có (giữ nhãn "chờ chốt")

- Xác nhận AI 1q/1r, trạng thái "Bị từ chối · khảo sát lại" (1f/1j) — chưa có enum/API.
- F15: mục Thiết bị / Đồng bộ / Ứng dụng — code chỉ có tên tài khoản + Đăng xuất.
- Chip mạng + 3 bước tải ở F01 — cần BE (đã ghi trong spec).
- Khoảng trống của code đáng thể hiện trên UI: phiên khởi động từ F03 hoặc "Quay lại" chưa gắn `workOrderId` nên không tải lên được; khởi động lại máy giữa phiên (`boot_session_id`) chưa có luồng.

## D. Khớp tốt

Badge trạng thái đèn / đồng bộ / ưu tiên đúng bảng; vùng chạm ≥ 48dp; Dark theo ngữ cảnh (F03/F04 tối, F04 ẩn nav); tách Kiểm tra / Sửa chữa theo `taskKind`; chip KPI + tìm kiếm trên bản đồ (FM-36); lỗi F01 theo `LoginUiState`.
