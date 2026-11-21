# Làm trọn ca khi mất mạng — ticket thiết kế (bản nháp)

**Trạng thái:** draft, chờ WP6 + BE1 (Mỹ) chốt các quyết định ở mục "Quyết định cần chốt" trước khi code.
**Nguồn:** buổi review UI/UX ngày 21/11/2026 (emulator Pixel 9, flavor `server`, tài khoản `crew`, `dev` = `153e6f2`),
báo cáo đầy đủ ở repo backend `.ai/results/mobile-ux-review-20261121/` (P1 #1, #2, #11). Code BE đọc ở `luxmap_backend`
`origin/dev` `5e3178a`, chỉ đọc.

## Vì sao

Luật dự án: **mobile chạy trọn ca offline** — kỹ sư làm đêm ở đường nông thôn, sóng yếu hoặc mất hẳn. Hiện app không đáp
ứng được. Bật chế độ máy bay sau khi đã đăng nhập và đã mở các tab:

| Màn | Khi mất mạng | Nguyên nhân trong code |
|---|---|---|
| Việc hôm nay | "Không tải được danh sách lệnh" — mất cả danh sách đã thấy trước đó | `RealHomeRepository` chỉ gọi API; ViewModel tải lại và thay dữ liệu bằng lỗi |
| Chi tiết lệnh kiểm tra / sửa chữa | Mở mới là lỗi; "Bắt đầu" gọi mạng trực tiếp | `RealWorkOrderDetailRepository` không cache; chỉ `pole_gps_update` có fallback |
| Bản đồ | Trắng, không cột / tuyến / tủ | `RealMapRepository` gọi mạng; không có vùng bản đồ offline |
| Cá nhân | "Chưa đồng bộ lần nào" | Chỉ phản ánh hàng đợi gửi lên, không nói gì về dữ liệu đã tải xuống |
| Sau đăng nhập | "Tải dữ liệu ngày làm việc" | `LoginViewModel.runPrefetchAndSucceed()` chỉ `delay(800)` rồi báo xong — **không tải gì** |

Phần đã có và giữ lại: Room cache danh sách khảo sát / GPS, danh sách cột, ảnh, báo cáo nghiệm thu và hàng đợi gửi lên
(`core/sync/SyncQueue*`), gửi qua từng endpoint riêng.

## Backend đã có gì (đọc code, không đoán)

`GET /api/v1/sync/bundle` — chỉ `field_engineer` (policy `SyncOffline`), `SyncBundleService.cs`:

- Không có `segment_id`: "việc tối nay" = mọi tuyến của các lệnh **mở của chính người gọi** (`assigned`, `in_progress`):
  tuyến của lệnh, tuyến khảo sát, tuyến của sự cố lệnh đang giữ và của cột mang sự cố đó; cộng cột không thuộc tuyến
  nào mà lệnh đang giữ sự cố trên đó. Có `segment_id` (tối đa 20) thì đóng gói đúng các tuyến đó.
- Trả: `generated_at`, `segment_ids`, `segments` (như `/map/segments`), `poles` (như `/map/poles` + `note`),
  `open_faults` (như item của `/faults`), `work_orders` (mỗi lệnh là **chi tiết đầy đủ** như `GET /work-orders/{id}`).
- Luôn là **bản chụp đầy đủ**, không delta: client **thay** cache của các tuyến đó; gửi `since` → 400. Đọc trong một
  transaction REPEATABLE READ nên năm phần mô tả cùng một thời điểm.

`POST /api/v1/sync/push` — body `{ operations: [{ client_op_id, op_type, payload }] }`, áp theo thứ tự; `client_op_id` là
UUID máy sinh, gửi lại an toàn; `op_type` ∈ `fault_report`, `lux_reading`, `pole_note`, `work_order_start`,
`work_order_complete`; `payload` là body của endpoint gốc. Trả ba danh sách `applied` (`id`, `replayed`), `conflicts`
(`reason`, `message`, `server_state`) và `rejected`; HTTP luôn 200.

## Khoảng trống phía backend (cần BE1 xác nhận — đọc code, chưa chạy thử)

- **BE-GAP-1 · Lệnh đo GPS có thể không vào bundle.** Bundle chọn lệnh qua `segment_id` của lệnh, `work_order_segment`
  hoặc sự cố lệnh đang giữ. Lệnh `pole_gps_update` gắn cột qua `work_order_pole_target`, không qua ba đường trên →
  có thể bị bỏ sót, kéo theo cột của nó. Cần kiểm trên dữ liệu thật (ví dụ WO-0024 / WO-0025 của `crew`).
- **BE-GAP-2 · Lệnh kiểm tra không sự cố.** WO-0023 (kiểm tra, 0 sự cố, nhắm POLE-0214) chỉ vào bundle nếu lệnh có
  `segment_id`. Cần xác nhận lệnh kiểm tra luôn có tuyến, hoặc bundle phải theo `GET /work-orders/{id}/poles`.
- **BE-GAP-3 · Không có tủ điện** trong bundle, trong khi bản đồ mobile hiển thị tủ. Quyết định: thêm vào bundle, hay
  bản đồ offline không có tủ.
- **BE-GAP-4 · Ảnh không đi qua `/sync/push`** (ảnh bằng chứng, ảnh cột là multipart riêng). Đúng thiết kế; mobile giữ
  hàng đợi ảnh hiện có và xếp `work_order_complete` sau ảnh (repair không `complete` được khi thiếu ảnh `after` → 409).

## Thiết kế đề xuất (mobile)

1. **Tải dữ liệu ca thật.** Sau đăng nhập và khi người dùng bấm "Tải dữ liệu ca" ở Cá nhân / trang chủ: gọi
   `/sync/bundle` mặc định, ghi vào Room trong **một transaction**, thay toàn bộ cache của các tuyến trả về
   (xoá thứ không còn trong bundle — lệnh bị giao cho người khác, cột bị xoá). Bỏ `delay(800)`.
2. **Room là nguồn đọc** cho Việc hôm nay, chi tiết lệnh, bản đồ (cột / tuyến / sự cố) và tìm kiếm cột. Có mạng thì làm
   mới nền; làm mới thất bại **giữ** dữ liệu cũ, hiện "Dữ liệu lúc HH:mm" (từ `generated_at`) thay vì màn lỗi.
3. **Thao tác offline** (`work_order_start`, `work_order_complete`, `fault_report`, `pole_note`) ghi local trước, hiện
   trạng thái "Đã lưu trên máy · chờ gửi", rồi gửi khi có mạng. Chỉ báo "máy chủ đã nhận" sau khi có kết quả applied.
4. **Nền bản đồ offline:** tải vùng MapLibre theo khung bao các tuyến trong bundle (zoom giới hạn để không phình dung
   lượng); hiện % tải và dung lượng. Không có nền thì vẫn vẽ cột / tuyến trên nền trơn.
5. **Trạng thái sẵn sàng ở Cá nhân** tách hai dòng: "Dữ liệu ca: N lệnh · M cột · lúc HH:mm" và "Chờ gửi: K thao tác /
   L ảnh". "Sẵn sàng offline" chỉ hiện khi bundle và vùng nền đã tải xong.
6. **Xung đột khi gửi lên** (`conflicts` / `rejected`, hoặc 409 ở endpoint riêng): giữ dữ liệu, hiện `message` server
   trả, không tự xoá. Ví dụ lệnh đã bị huỷ
   hoặc giao lại trong lúc offline.

## Quyết định cần chốt

- **D-1** Gửi lên: chuyển sang `POST /sync/push` (một batch, có `replayed`) hay giữ từng endpoint riêng như hiện tại
  (đã chạy, có `client_op_id`)? Đề xuất: dùng `/sync/push` cho 5 loại nó nhận, ảnh vẫn đi endpoint riêng.
- **D-2** Khi nào tải bundle: chỉ lúc đăng nhập + bấm tay, hay thêm tự động khi mở app có Wi-Fi? Đề xuất: lúc đăng nhập,
  bấm tay, và tự động khi mở app nếu bản cũ quá 6 giờ.
- **D-3** BE-GAP-1/2/3 — sửa phía backend trước, hay mobile tự bù bằng `GET /work-orders/{id}/poles` khi có mạng?
- **D-4** Khoá lệnh theo ngày lịch (`WorkOrderScheduleWindow`) khi offline qua 00:00: thao tác đã lưu local trước giờ
  khoá có được gửi sau không? Liên quan luật "phiên đêm cắt qua nửa đêm".
- **D-5** Dung lượng tối đa cho vùng nền offline trên máy.

## Tiêu chí xong (kiểm được)

- [ ] Đăng nhập có mạng → bật máy bay → mở lại app: Việc hôm nay, chi tiết từng lệnh mở, bản đồ (cột / tuyến / sự cố)
      và tìm cột đều hiện từ Room, có dòng "Dữ liệu lúc …".
- [ ] Offline: Bắt đầu một lệnh kiểm tra, nhập kết quả, chụp ảnh, Nộp → trạng thái "chờ gửi"; tắt máy bay → gửi xong,
      server trả applied, trạng thái đổi "đã nhận"; gửi lại cùng batch → `replayed: true`, không nhân đôi.
- [ ] Lệnh bị giao cho người khác trong lúc offline → lần tải bundle sau, lệnh biến khỏi máy; thao tác chờ gửi của nó
      hiện lý do bị từ chối, dữ liệu không mất.
- [ ] Test Room / repository cho: thay cache theo bundle (xoá thứ không còn), đọc offline, giữ dữ liệu khi làm mới lỗi.
- [ ] `LoginViewModel` không còn `delay` giả; test chứng minh màn tải gọi `/sync/bundle`.

## KHÔNG làm trong ticket này

- Không đổi API, quyền hay trạng thái backend — khoảng trống BE ghi thành D-item, BE1 quyết.
- Không tự sinh ID hiển thị (`WO-…`, `FAULT-…`) ở máy; thao tác offline mang `client_op_id`, server gán ID.
- Không âm thầm bỏ thao tác / ảnh bị từ chối để "dọn" hàng đợi.
- Không gộp các sửa P1 khác của buổi review (danh sách cột trong lệnh kiểm tra, khoá lệnh trên thẻ, tự lưu nháp) vào
  đây — ticket riêng.
