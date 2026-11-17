# Đề xuất API — task_kind "pole_gps_update" (Cập nhật vị trí cột GPS)

**Ngày soạn:** 2026-11-13
**Soạn bởi:** Mobile (WP6), gửi Backend (WP2/WP5) xác nhận trước khi code
**Trạng thái:** Đề xuất — chưa có field/endpoint nào trong danh sách dưới đây tồn tại trên backend (đã xác nhận bằng cách đọc source `luxmap_backend` nhánh `dev`, commit `044318a`, 2026-11-13)

## Bối cảnh

Nghiệp vụ mới: Quản lý giao cho Field Engineer đi dán tem QR + đo tọa độ GPS thực địa cho một tập cột đèn trên 1 tuyến đường — tác vụ **đơn lẻ, không nối chuỗi** (khác hẳn chuỗi Khảo sát → Kiểm tra → Sửa chữa hiện có, không dùng `parent_work_order_id`/`case_id`).

Nguồn: prototype web phía Quản lý (`work_schedule_gps_update_prototype.html`) + mô tả nghiệp vụ trực tiếp từ chủ dự án.

`TaskKind` backend hiện chỉ có `Inspection | Repair | Survey` (`src/LuxMap.Modules.WorkOrders/Entities/WorkOrder.cs`). Phần lớn cơ chế `WorkOrder` đã generic và dùng lại được nguyên vẹn (start/complete/verify/return, `note`/`review_note`/`report_note`, `segment_id`, `scheduled_date`...) — tài liệu này chỉ liệt kê đúng phần còn thiếu.

## 1. Thêm giá trị enum

```
TaskKind: Inspection | Repair | Survey | PoleGpsUpdate   (wire: "pole_gps_update")
```

> Lưu ý: prototype web hiện đang code tạm là `"gps_update"` — cần BE chốt **1 tên duy nhất** (khuyến nghị `pole_gps_update` cho rõ nghĩa) và yêu cầu Web sửa lại khớp theo tên chốt.

## 2. Tạo lệnh — mở rộng `POST /api/v1/work-orders`

`CreateWorkOrderRequest` hiện đã có `task_kind`, `segment_id`, `assigned_to`, `scheduled_date`, `note`... dùng lại được hết.

**Field mới cần thêm:**

```
pole_ids: string[]   // bắt buộc khi task_kind = pole_gps_update
                      // danh sách cột Manager chọn trên tuyến (tập con, không phải cả segment)
```

Khác `fault_ids` đang có sẵn — field này không gắn với `Fault` nào.

## 3. Đọc chi tiết lệnh — mở rộng `WorkOrderDetail`

**Cần trả thêm:**

```
pole_ids: string[]
```

**Lý do không dùng được `GET /work-orders/{id}/poles` có sẵn:** endpoint đó (`WorkOrderPoleService.PolesAsync`) trả về **toàn bộ cột trên segment** của lệnh (suy ra từ `segment_id`/`WorkOrderSegment`), trong khi ở nghiệp vụ này Manager chỉ chọn **một tập con cột cụ thể** trên tuyến (ví dụ 4/7 cột đã có trên segment). Cần 1 danh sách tường minh riêng, không suy ra được từ segment.

## 4. File PDF mã QR — tài nguyên mới hoàn toàn

Chưa có cơ chế nào trong `WorkOrder` phù hợp — evidence hiện có (`POST /work-orders/{id}/evidence`) chỉ dành cho ảnh kỹ sư tự chụp (`before`/`after`/`observation`), hướng ngược lại (kỹ sư → server) và khác loại file (ảnh, không phải PDF).

**Đề xuất:**

```
POST   /api/v1/work-orders/{id}/attachments
       (multipart/form-data, Manager gọi lúc tạo/PATCH lệnh — phía Web)

GET    /api/v1/work-orders/{id}/attachments
       -> [{ attachment_id, file_name, size_bytes, content_type, uploaded_at }]

GET    /api/v1/work-orders/{id}/attachments/{attachment_id}/download
       -> trả file nhị phân (mobile tải về rồi mở bằng app PDF có sẵn trên máy qua Intent, không cần PDF renderer riêng)
```

Giới hạn `content_type = application/pdf`.

## 5. Nộp báo cáo — mở rộng `POST /work-orders/{id}/complete`

Theo đúng mẫu `fault_outcomes[]` đã dùng cho `task_kind=inspection`, thêm nhánh bắt buộc khi `task_kind = pole_gps_update`:

```
pole_locations: [
  { pole_id: string, lat: number, lng: number, accuracy_m: number }
]
```

`report_note` dùng lại field `report_note` sẵn có (đã map vào `WorkOrder.ReportNote`).

## 6. Duyệt / Yêu cầu làm lại — dùng lại nguyên vẹn, không cần endpoint mới

- **`POST /work-orders/{id}/verify`** — Manager duyệt. Cần BE tự xử lý thêm ở bước này: ghi `lat`/`lng` của từng `pole_id` trong `pole_locations` vào dữ liệu cột GIS thật (`Pole.Geom`) — phần import GIS, ngoài phạm vi mobile.
- **`POST /work-orders/{id}/return`** — Manager yêu cầu làm lại, dùng lại `review_note` có sẵn. Không cần field/endpoint mới.

## 7. Mã QR dán trên cột — không cần thêm endpoint

Đề xuất QR mã hoá **thẳng `pole_id` dạng text thuần** (ví dụ `"POLE-001"`), **không** dùng chung với hệ thống Citizen-QR hiện có (`LuxMap.Modules.Citizen.QrCode`):

- Module đó đánh dấu `[ClientSurface(ClientSurface.Web)]` — chỉ dành cho Web (Quản lý phát hành/thu hồi mã cho Citizen), không gọi được từ mobile.
- `Code` của Citizen-QR cố tình là chuỗi ngẫu nhiên 16 ký tự, **không được log ra bất cứ đâu** (lý do bảo mật: ai cầm được QR thì gửi được báo cáo sự cố) — đặc tính này không phù hợp với nhu cầu ở đây (kỹ sư chỉ cần biết "đây đúng là cột nào" để khớp tọa độ).
- Chính code backend còn ghi rõ: *"The public side (scanning a code, filing a report) is P2b and does not exist yet."* — nghĩa là ngay cả việc quét-rồi-tra-server cũng chưa có endpoint nào, kể cả cho Citizen.

**Nếu đồng ý hướng mã hoá `pole_id` thuần:** mobile tự giải mã QR rồi so khớp với `pole_ids` đã nhận ở mục 3 — **không cần BE làm gì thêm cho việc quét**, chỉ cần khâu sinh PDF (phía Web) đảm bảo in đúng `pole_id` vào QR.

## Việc BE cần xác nhận lại

1. Tên `task_kind` chốt là gì (`pole_gps_update` hay giữ `gps_update` như web đang code tạm).
2. Đồng ý cơ chế `pole_ids` (mục 2–3) / `pole_locations` (mục 5) / `attachments` (mục 4) như trên, hay có hướng khác.
3. QR có đồng ý mã hoá `pole_id` thuần (client tự so khớp, mục 7) hay cần 1 cơ chế xác thực khác.
4. Bước `verify` (mục 6) có thật sự ghi đè `Pole.Geom` ngay lập tức, hay còn qua 1 bước duyệt riêng khác trên GIS.
