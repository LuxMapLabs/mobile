# Đề xuất API — ảnh bằng chứng kèm GPS cho `pole_gps_update`

**Ngày soạn:** 2026-11-14
**Soạn bởi:** Mobile (WP6), gửi Backend (WP2/WP5) xác nhận trước khi code
**Trạng thái:** Đề xuất — chưa có field/endpoint nào trong danh sách dưới đây tồn tại trên backend (đã xác nhận bằng cách đọc source `luxmap_backend` nhánh `dev`, 2026-11-14)

## Bối cảnh

Nghiệp vụ `pole_gps_update` đã code xong (PR #148, đã merge `dev`), hiện tại kỹ sư chỉ **đo toạ độ GPS rời** tại mỗi cột (không có ảnh), gửi lên qua `pole_locations[]` của `POST /work-orders/{id}/complete` — đúng theo `docs/superpowers/specs/2026-11-13-pole-gps-update-design.md` và khớp với bảng web thật (`WorkOrderPoleLocation` chỉ có `pole_id/lat/lng/accuracy_m/captured_at`, không có ảnh).

**Thay đổi nghiệp vụ mới (xác nhận với chủ dự án, 2026-11-14):** tại mỗi cột, kỹ sư phải **chụp 1 ảnh cột đèn kèm GPS** thay vì chỉ đo GPS rời. Luồng mobile: chụp lần lượt từng cột, lưu cục bộ, khi xong hết danh sách mới bấm "Gửi báo cáo" để gửi toàn bộ 1 lần (không gửi rời từng ảnh ngay lúc chụp).

## Vì sao không dùng lại được `POST /work-orders/{id}/pole-photos` có sẵn

Endpoint ảnh cột đèn hiện có (dùng cho `task_kind=survey`, AI phân loại `cv_state`) đã đọc source xác nhận **không dùng được nguyên trạng** cho `pole_gps_update`:

1. `WorkOrderPolePhotoService.Target()` (dòng ~45-52): `if (pole.Geom is null) throw PoleLocationRequired.For(...)` — **bắt buộc cột đã có toạ độ GIS thật thì mới cho upload ảnh**. Đây chính xác là điều kiện **ngược lại** với lý do tồn tại của `pole_gps_update` (cột chưa có hoặc sai toạ độ).
2. Endpoint đó chạy pipeline AI phân loại đèn (`hinter.HintAsync`, `cv_state`) — không cần và không đúng cho mục đích "ảnh bằng chứng kèm vị trí", tốn tài nguyên AI vô ích.
3. Việc `Targets(id)` dùng chung đã đúng cho `pole_gps_update` (có dòng comment xác nhận `work_order_pole_target` được tính), chỉ riêng điều kiện `Geom is null` ở điểm 1 là chặn cứng.

Vì vậy cần 1 endpoint mới, không chạy AI, không đòi `Geom` đã có.

## 1. Upload ảnh bằng chứng kèm GPS cho 1 cột

```
POST /api/v1/work-orders/{id}/pole-gps-photos
Policy: ExecuteWorkOrders (giống pole-photos, chỉ kỹ sư đang được giao chính phiếu)
Content-Type: multipart/form-data

Fields:
  pole_id       string, bắt buộc — phải thuộc pole_ids của lệnh (WorkOrderPoleTarget), giống logic Targets() hiện có
  photo         file, bắt buộc — ảnh chụp cột đèn
  lat           string (double), bắt buộc, [-90, 90]
  lng           string (double), bắt buộc, [-180, 180]
  accuracy_m    string (double), bắt buộc, >= 0 — độ chính xác GPS lúc chụp (mobile đã có PoleFixGate gate 8m, BE không cần tự gate lại,
                chỉ validate finite >= 0, giống accuracy_m của pole_locations hiện có)
  captured_at   string ISO 8601 UTC, bắt buộc, tối đa 5 phút trong tương lai (giống rule captured_at của pole_locations/pole-photos hiện có)
  client_op_id  string (GUID), bắt buộc — idempotency key, giống pole-photos hiện có (gửi lại cùng op_id + cùng nội dung = replay,
                khác nội dung = 409 IDEMPOTENCY_CONFLICT)

Response 201 (lần đầu) / 200 (replay):
{
  "photo_id": "...",
  "work_order_id": "...",
  "pole_id": "...",
  "lat": ..., "lng": ..., "accuracy_m": ...,
  "captured_at": "...",
  "uploaded_by": "...", "uploaded_at": "...",
  "thumbnail_url": "...", "original_url": "..."
}
```

**Khác `pole-photos` ở các điểm:**
- Không check `pole.Geom is null` — đây đúng là trường hợp cần xử lý.
- Không chạy `hinter.HintAsync`/AI — không có `cv_state`/`ai_status`/`model_version` trong response.
- Có thêm `accuracy_m` (pole-photos hiện tại không có field này, vì mục đích khác nhau).
- Validate `pole_id` qua cùng cơ chế `WorkOrderPoleService.Targets(id)` đã có (đã xác nhận hỗ trợ `pole_gps_update` qua `WorkOrderPoleTarget`).
- `wo_status` phải `in_progress` (giống `Writable()` hiện có của pole-photos) — gọi được nhiều lần cho nhiều cột khác nhau, hoặc gọi lại cùng 1 cột để chụp lại (thay thế ảnh+toạ độ cũ của đúng cột đó, không cộng dồn).

## 2. Đọc lại danh sách ảnh đã chụp của lệnh (để mobile hiện trạng thái "Đã chụp" khi mở lại màn hình)

```
GET /api/v1/work-orders/{id}/pole-gps-photos
Policy: ReadWorkOrders
-> { items: [{ photo_id, pole_id, lat, lng, accuracy_m, captured_at, thumbnail_url, original_url }] }

GET /api/v1/work-orders/{id}/pole-gps-photos/{photo_id}/thumbnail  -> image/jpeg
GET /api/v1/work-orders/{id}/pole-gps-photos/{photo_id}/original   -> image/jpeg
```

Giống hệt mẫu đã có của `pole-photos` (list/thumbnail/original), chỉ đổi path và bỏ các field AI.

## 3. `complete` — không đổi hợp đồng cũ, lấy dữ liệu từ ảnh đã upload

`POST /work-orders/{id}/complete` **giữ nguyên** `pole_locations[{pole_id, lat, lng, accuracy_m, captured_at}]` như hiện tại — **không đổi gì ở đây**, để bảng web (`WorkOrderPoleLocation`, `FieldReportDetailModal.tsx` tab "Tọa Độ GPS Từng Cột") không cần sửa.

Đề xuất: BE tự lấy `lat/lng/accuracy_m/captured_at` của `pole_locations[]` **từ chính các ảnh đã upload qua mục 1** (mỗi `pole_id` lấy ảnh mới nhất của chính kỹ sư gọi `complete`), thay vì bắt mobile gửi lại 2 lần. Nếu BE không muốn tự suy ra, cách khác: mobile vẫn gửi `pole_locations[]` y hệt cũ (giá trị lấy từ ảnh đã chụp cục bộ) — cách này không cần BE đổi gì ở `complete`, chỉ cần endpoint mục 1 tồn tại. **Khuyến nghị dùng cách 2 (mobile tự gửi lại)** vì ít rủi ro nhất, không đổi logic `complete` đã test kỹ (`WorkOrderPoleGpsTests.cs`).

## 4. Hiển thị ảnh trên Web (đề xuất, không bắt buộc ngay)

Bảng "Tọa Độ GPS Từng Cột" (`FieldReportDetailModal.tsx`) có thể thêm 1 cột ảnh thumbnail (gọi `GET .../pole-gps-photos` song song với dữ liệu `pole_locations` đã có) — **không bắt buộc cho v1**, kỹ sư vẫn gửi được báo cáo nếu Web chưa làm phần hiển thị này, Quản lý chỉ tạm thời không thấy ảnh.

## Việc BE cần xác nhận lại

1. Đồng ý tạo endpoint riêng `pole-gps-photos` (mục 1-2) thay vì mở rộng `pole-photos` cũ hay không.
2. Đồng ý bỏ qua AI/`cv_state` cho endpoint mới này (đúng, vì mục đích là bằng chứng vị trí, không phải phân loại đèn).
3. Giữ nguyên `complete`/`pole_locations[]` như cũ (mục 3) hay muốn BE tự suy ảnh mới nhất — mobile khuyến nghị giữ nguyên, ít rủi ro hơn.
4. Giới hạn kích thước/định dạng ảnh — dùng chung `MaxUploadBytes`/validate ảnh như `pole-photos` hiện có, hay cần ngưỡng riêng?
