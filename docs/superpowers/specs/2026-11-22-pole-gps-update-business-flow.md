# Đo GPS cột đèn (`pole_gps_update`) — nghiệp vụ chốt và đề xuất cho mobile

**Ngày:** 2026-11-22 · **Trạng thái:** đề xuất, chờ nhóm xác nhận các câu hỏi ở mục 6
**Bổ sung cho:** `2026-11-13-pole-gps-update-design.md` (thiết kế gốc) và `2026-11-18-pole-gps-hardening-design.md` (củng cố). Hai file đó vẫn đúng; file này làm rõ quy tắc 25 m và thêm phản hồi sớm cho kỹ sư.

## 1. Nguồn đã đối chiếu (chỉ đọc, không sửa BE/Web)

| Nguồn | Nội dung dùng |
|---|---|
| `luxmap_backend/docs/contract-drift.md` — PL-3, PG-3…PG-13, G-2 | Luật hành lang 25 m, payload `complete`, duyệt tất cả-hoặc-không |
| `WorkOrderService.cs` — `CreatePoleGpsCore`, `Report`, `MovePolesAsync` | Mọi cột của lệnh thuộc đúng `segment_id` của lệnh; `complete` chỉ lưu báo cáo; `verify` mới đo 25 m |
| `Crud/PoleRelocation.cs` — `MeasureAsync`, `IsOffSegment` | Khoảng cách tính tới **hình tuyến**, không tới tọa độ cũ |
| `Crud/AssetsController.cs` — `GET /assets/segments/{id}` | Trả `geom_wkt`, policy `ReadNetwork` (Kỹ sư đọc được) |
| Web `FieldReportDetailModal.tsx`, `workScheduleSaga.ts` | Quản lý thấy "Cách tuyến X m / 25 m", kéo ghim chỉnh, duyệt; sau duyệt web vẽ lại hình tuyến theo cột |

## 2. Nghiệp vụ đã chốt (theo BE/Web hiện tại)

**Mục đích:** ghi nhận vị trí thật của các cột **đã có trong hệ thống**. Lệnh không tạo cột mới; cột chưa tồn tại phải được Quản lý tạo trước trên web.

**Hai trường hợp, cùng một luồng:**

| | TH1: cột đã có GPS cũ | TH2: cột chưa có GPS |
|---|---|---|
| `/work-orders/{id}/poles` | `location` = vị trí đang lưu trong GIS | `location: null` |
| Mobile hiển thị | Tọa độ cũ + khoảng dời (để tham khảo) | "Chưa có GPS" |
| Kiểm tra lúc duyệt | Cách tuyến ≤ 25 m | Cách tuyến ≤ 25 m |
| Audit khi duyệt | `before` = vị trí cũ, có `moved_m` | `before` = null |

**Các bước:**

1. Quản lý tạo lệnh: chọn 1 tuyến, 1–200 cột thuộc tuyến đó, đính kèm PDF (nếu có), giao Kỹ sư. Mỗi cột chỉ nằm trong 1 lệnh đang mở.
2. Kỹ sư bắt đầu lệnh, đến từng cột, chụp ảnh; app lấy GPS tại lúc chụp.
3. Kỹ sư nộp: ảnh từng cột (`evidence`, `kind=observation`) rồi `complete` kèm `report_note` (≥ 10 ký tự) và `pole_locations[]`. Được nộp một phần (≥ 1 cột). Server **chỉ lưu báo cáo**, chưa đổi tọa độ cột.
4. Quản lý xem trên web: GPS cũ, GPS mới, khoảng cách tới tuyến; kéo ghim chỉnh nếu cần.
5. Quản lý duyệt. Server ghi tọa độ vào cột với điều kiện **mọi cột cách tuyến ≤ 25 m**. Một cột vượt → 409 `POLE_OFF_SEGMENT`, không cột nào đổi, lệnh giữ `done` để Quản lý `return`.
6. Bị `return`: Kỹ sư thấy `review_note`, đo lại, nộp lại. Lần nộp sau **thay toàn bộ** báo cáo cũ (PG-11).

**Quy tắc 25 m (PL-3):** đo từ **tọa độ mới tới hình tuyến của chính cột**. **Không** giới hạn khoảng dời so với tọa độ cũ (tọa độ cũ có thể lệch vài chục mét). Tuyến chưa vẽ hình hoặc cột chưa thuộc tuyến → không kiểm tra.

## 3. Hiện trạng mobile

Đã có và đúng hợp đồng: danh sách cột (GPS cũ hoặc "Chưa có"), đo GPS kèm ảnh, sai số > 20 m phải xác nhận, mở bản đồ tới cột, tải PDF, nộp offline-first qua `sync_queue`, ảnh phải lên xong mới gửi `complete`, hiện `review_note` khi bị trả về.

Còn thiếu hoặc lệch:

1. **Không có phản hồi về hành lang 25 m.** Kỹ sư chỉ biết cột lệch khi Quản lý duyệt, lúc đó đã rời hiện trường.
2. **Nộp lại sau `return` có thể làm mất tọa độ.** `PoleGpsRepository.snapshotCompletion` mặc định chỉ lấy phép đo `source = DEVICE`. Phép đo lấy về từ báo cáo cũ (`SERVER`) không được gửi lại, trong khi PG-11 nói lần nộp sau thay toàn bộ báo cáo, nên cột không đo lại sẽ mất tọa độ báo. Thiết kế gốc (mục 4.3) yêu cầu gửi lại tất cả. Cần xác nhận đây là cố ý (mỗi cột phải có ảnh mới) hay là lỗi. Nếu cố ý, UI phải nói rõ "phải đo lại mọi cột cần giữ".

## 4. Đề xuất cho mobile

### 4.1 Tải trước ca (khi có mạng, lúc mở lệnh)

- Thêm `segment_id` vào `WorkOrderDetailDto` (BE đã trả, mobile chưa đọc).
- Gọi `GET /assets/segments/{segment_id}`, lưu `geom_wkt` vào Room cùng lệnh để đo offline.
- Tải lại mỗi lần mở lệnh khi có mạng: web vẽ lại hình tuyến sau mỗi lần duyệt nên hình có thể đã đổi.

### 4.2 Màn đo một cột

Hiển thị cho mẫu GPS hiện tại:

| Thông tin | TH1 (có GPS cũ) | TH2 (chưa có GPS) |
|---|---|---|
| Sai số GPS | ±X m (như hiện tại) | ±X m |
| Cách tuyến | "Cách tuyến X m / 25 m" | "Cách tuyến X m / 25 m" |
| So với vị trí cũ | "Lệch vị trí cũ Y m" — chỉ để tham khảo, không cảnh báo | Không hiển thị |

Đánh giá hành lang (`d` = khoảng cách tới tuyến):

| Kết quả | Điều kiện | Hành vi |
|---|---|---|
| Trong hành lang | `d ≤ 25` | Lưu bình thường |
| Ngoài hành lang | `d > 25` | Cảnh báo đỏ, hỏi "Đo lại" / "Vẫn lưu vị trí này"; dòng cột trong danh sách gắn nhãn "Ngoài hành lang tuyến" |
| Không kiểm tra được | Tuyến chưa có hình, hoặc chưa tải được hình | Ghi rõ "Chưa kiểm tra được hành lang", lưu bình thường |

Đề xuất **cảnh báo, không chặn cứng**, vì: (a) tài liệu BE không giao việc chặn cho mobile; (b) Quản lý có thể kéo chỉnh trên web; (c) hình tuyến có thể vẽ sai, cột thật vẫn đúng. Nếu nhóm muốn chặn, xem câu hỏi Q1.

Cách tính: khoảng cách điểm → đường gấp khúc (`LINESTRING`/`MULTILINESTRING` từ WKT), chiếu phẳng cục bộ quanh điểm đo. Sai khác so với `ST_Distance` của server ở mức xăng-ti-mét trong phạm vi vài chục mét. Ngưỡng 25 để một hằng số, khác ngưỡng sai số GPS 20 m đang có.

### 4.3 Trước khi nộp

Màn chi tiết lệnh liệt kê từng cột: đã đo / chưa đo / ngoài hành lang. Nút nộp:

- Còn cột chưa đo → hỏi xác nhận "Còn N cột chưa đo" (giữ quy tắc nộp một phần).
- Có cột ngoài hành lang → hỏi xác nhận "N cột ngoài hành lang 25 m, Quản lý có thể trả lại", cho phép nộp.
- Payload `complete` **không đổi**.

### 4.4 Khi bị trả về

- Hiện `review_note` (đã có).
- Xử lý theo kết luận của mục 3.2: hoặc gửi lại cả phép đo cũ, hoặc báo rõ cột nào sẽ mất tọa độ nếu không đo lại.

### 4.5 Không làm

- Không gọi `PUT /assets/poles/{id}/location` từ mobile (bỏ qua bước duyệt).
- Không tạo cột mới từ mobile.
- Không đổi JSON API, không thêm thư viện.

## 5. Ước lượng công việc

| Bước | Nội dung | File chính |
|---|---|---|
| 1 | `segment_id` vào DTO, tải + cache hình tuyến | `WorkOrderDetailDto`, `PoleGpsDetailSync`, entity/DAO mới, migration Room |
| 2 | Hàm tính khoảng cách điểm → tuyến (có unit test) | file mới trong `polegps/data` |
| 3 | Màn đo: hiển thị và hỏi xác nhận ngoài hành lang | `PoleGpsMeasureViewModel`, `PoleGpsMeasureScreen` |
| 4 | Nhãn trên danh sách và xác nhận lúc nộp | `PoleGpsSection`, `WorkOrderDetailViewModel` |
| 5 | Sửa luồng nộp lại sau `return` (sau khi có kết luận) | `PoleGpsRepository.snapshotCompletion` |

Mỗi bước một hoặc vài commit ≤ 400 dòng.

## 6. Câu hỏi cần nhóm/BE xác nhận

1. **Ngoài hành lang thì mobile cảnh báo hay chặn?** Đề xuất cảnh báo (mục 4.2).
2. **Nộp lại sau `return`:** kỹ sư phải đo lại mọi cột (mỗi cột cần ảnh mới), hay mobile gửi lại tọa độ cũ cho cột không đo lại? Hiện code và thiết kế gốc nói hai điều khác nhau.
3. **Ngưỡng 25 m** là cấu hình server (`RouteCorridorM`). BE có muốn trả giá trị này qua API để mobile không phải đặt cứng không?
4. **Cách tuyến hiểu đúng như PL-3?** Có thành viên hiểu là "tọa độ cũ trừ tọa độ mới ≤ 25 m"; PL-3 ghi rõ là không giới hạn khoảng dời. Cần chốt một cách hiểu chung.
5. **Lỗ hổng `EditPoleLocation`:** Kỹ sư vẫn gọi thẳng `PUT /assets/poles/{id}/location` được (BE tự ghi nhận). Có đóng không?
6. **Cách web duyệt khi Quản lý đã chỉnh ghim:** web gọi `PUT /location`, rồi tạm ghi đè `geom` bằng tọa độ kỹ sư qua `PUT /poles/{id}` để `verify` qua được, rồi ghi lại tọa độ Quản lý. Đây là cách làm phía web; BE có muốn hỗ trợ chính thức (ví dụ `verify` nhận tọa độ đã chỉnh) không? Không ảnh hưởng mobile, chỉ ghi lại để nhóm biết.

## 7. Đề xuất trả lời (mobile đề xuất, chờ nhóm duyệt)

| # | Đề xuất | Lý do |
|---|---|---|
| Q1 | **Cảnh báo + xác nhận, không chặn.** Ngoài 25 m: hộp thoại "Đo lại" (nút chính) / "Vẫn lưu"; cột gắn nhãn "Ngoài hành lang tuyến"; nhắc lại lúc nộp. | BE không giao việc chặn cho mobile; Quản lý chỉnh được trên web; hình tuyến có thể vẽ sai. Chặn cứng sẽ khiến kỹ sư không báo được cột thật. |
| Q2 | **Gửi lại tọa độ cũ cho cột không đo lại.** Cột đo lại: tọa độ mới + ảnh mới. Cột không đo lại: giữ tọa độ đã báo, không cần ảnh mới (ảnh lần trước vẫn nằm trên server). | BE thay toàn bộ báo cáo ở lần nộp sau (PG-11) và **không** bắt ảnh khi `complete` lệnh `pole_gps_update` (`WorkOrderEvidence.cs` chỉ cho phép `observation`, không bắt buộc). Bắt đo lại mọi cột là tốn công vô ích khi Quản lý chỉ trả về vì 1 cột. Cần sửa `snapshotCompletion`: lấy cả phép đo `SERVER`, chỉ đòi ảnh với phép đo `DEVICE`. |
| Q3 | **Đặt cứng 25 m trong mobile**, ghi rõ nguồn là `SurveyProcessing:RouteCorridorM`. Hỏi BE trả qua API, nhưng không chờ. | Mobile chỉ cảnh báo, lệch ngưỡng không làm mất dữ liệu; server vẫn là nơi quyết định. |
| Q4 | **Theo PL-3:** cách tuyến, không phải khoảng dời so với tọa độ cũ. Khoảng dời chỉ hiển thị để tham khảo. | Đúng tài liệu BE và cách web đang tính. |
| Q5 | **Mobile không gọi `PUT /location`.** Đề nghị BE đóng quyền này với Kỹ sư. | Không phải việc của mobile; không chặn việc code. |
| Q6 | **Mobile không đổi gì.** | Thuần phía web/BE. |

Thứ tự làm đề xuất: bước 5 (sửa nộp lại, vì có nguy cơ mất dữ liệu) → bước 1 → 2 → 3 → 4.

## 8. Tùy chọn "Thêm ảnh" — ĐÃ BỎ (2026-11-22)

Đã cân nhắc cho kỹ sư chụp thêm ảnh minh họa ngoài ảnh đo GPS (`POST /evidence`, `kind=observation`, `pole_id` tùy chọn). Nhóm quyết định **bỏ**: luồng GPS chạy đủ chỉ với ảnh đo. Lưu ý nếu làm lại sau: bảng GPS trên web hiện ảnh **mới nhất** của mỗi cột, nên ảnh thêm gắn cột sẽ đè ảnh đo; và ảnh thêm phải nằm trong danh sách phụ thuộc của `complete` vì server từ chối ảnh sau khi nộp.

## 9. Bản chốt nghiệp vụ mobile (2026-11-22)

Tổng hợp từ mục 2–8, lấy các đề xuất ở mục 7 làm phương án. Những điểm còn chờ nhóm/web xác nhận được ghi rõ ở 9.4.

### 9.1 Phạm vi

- Chỉ đo vị trí cho **cột đã có trong hệ thống**, gồm 2 trường hợp: có GPS cũ, chưa có GPS.
- Không tạo cột mới, không gọi `PUT /assets/poles/{id}/location`, không chọn ảnh từ thư viện, không có tùy chọn thêm ảnh (mỗi cột chỉ có 1 ảnh đo).
- Mobile **không chặn** vì 25 m; server quyết định ở bước duyệt.

### 9.2 Luồng trên app

1. **Nhận lệnh:** lệnh `pole_gps_update` hiện ở Home/Hub. Mở lệnh khi có mạng: tải chi tiết, danh sách cột (kèm GPS cũ nếu có), hình tuyến, PDF; lưu Room để làm offline.
2. **Bắt đầu:** bấm "Bắt đầu" theo cửa sổ lịch hiện có → `in_progress`.
3. **Đo từng cột** (thứ tự tùy ý):
   - Chụp ảnh bằng camera; app lấy mẫu GPS (≤ 10 giây tuổi), ghi lat/lng vào EXIF, lưu Room.
   - Hiện: sai số GPS, "Cách tuyến X m / 25 m", và nếu có GPS cũ thì "Lệch vị trí cũ Y m" (tham khảo).
   - Sai số > 20 m → hỏi "Đo lại" / "Vẫn lưu".
   - Cách tuyến > 25 m → hỏi "Đo lại" / "Vẫn lưu"; cột gắn nhãn "Ngoài hành lang tuyến".
   - Tuyến chưa có hình → ghi "Chưa kiểm tra được hành lang", lưu bình thường.
   - Đo lại một cột → thay phép đo cũ của cột đó.
4. **Nộp báo cáo:**
   - Điều kiện: ≥ 1 cột đã đo, ghi chú ≥ 10 ký tự, đang trong ngày cho phép nộp.
   - Hỏi xác nhận nếu còn cột chưa đo hoặc có cột ngoài hành lang.
   - Ghi Room trước; hàng đợi tải ảnh đo của từng cột, xong hết mới gửi `complete` kèm `pole_locations`. Offline thì chờ mạng.
   - Trạng thái: "Đã nộp, chờ đồng bộ" → "Đã đồng bộ" / "Đồng bộ lỗi" / "Xung đột".
5. **Chờ duyệt:** Quản lý xem, chỉnh ghim, duyệt trên web. App không làm gì thêm.
6. **Bị trả về:** hiện `review_note`; tọa độ đã báo được nạp sẵn. Kỹ sư đo lại cột cần sửa. Nộp lại gửi **toàn bộ** cột: cột đo lại dùng tọa độ + ảnh mới, cột không đo lại giữ tọa độ cũ không cần ảnh mới.
7. **Được duyệt:** lệnh `verified`, tọa độ cột trên bản đồ được cập nhật.

### 9.3 Việc mobile cần làm (theo thứ tự)

1. Sửa nộp lại sau `return` để gửi cả tọa độ cũ (tránh mất dữ liệu).
2. Đọc `segment_id`, tải và cache hình tuyến.
3. Hàm tính khoảng cách điểm → tuyến (unit test).
4. Màn đo: hiển thị và hỏi xác nhận ngoài hành lang.
5. Danh sách cột: nhãn trạng thái, xác nhận lúc nộp.

### 9.4 Còn chờ xác nhận (không chặn việc 1–5)

| # | Điểm | Phương án đang theo |
|---|---|---|
| a | Ngoài 25 m: cảnh báo hay chặn | Cảnh báo + xác nhận |
| b | Nộp lại: giữ tọa độ cũ cho cột không đo lại | Giữ |
| c | Cách hiểu 25 m | Theo PL-3: cách tuyến, không phải khoảng dời |
