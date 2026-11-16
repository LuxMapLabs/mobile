# Design Principles: LuxMap Mobile Design System vNext

> Hướng thiết kế đã duyệt: **Civic Utility (Light-first + Night Field + Restrained Material 3).**
> Các nguyên tắc dưới đây kế thừa 7 nguyên tắc nền tảng của Design System v2.0 (mục 1). vNext không thay chúng mà sắp xếp lại thành 7 nguyên tắc có thể dùng để **ra quyết định** khi hai lựa chọn mâu thuẫn. Thứ tự là thứ tự ưu tiên.

---

## 1. Field usability trước hết
**Mô tả:** Giao diện phải dùng được trong điều kiện thật của Kỹ sư hiện trường: ngoài trời nắng, ban đêm, một tay, đeo găng, mạng chập chờn. Điều không làm được ngoài hiện trường thì không đáng giá trên giấy.

**Áp dụng vào LuxMap:**
- Mọi vùng chạm ≥48dp. Tab lọc ở Việc hôm nay đang chỉ 40dp, nên phải nâng lên.
- CTA chính nằm ở **Bottom Action Bar** trong tầm ngón cái, không ở đầu màn.
- Màn chụp cột: shutter ≥64dp, không có thao tác cần độ chính xác cao.

## 2. Rõ ràng hơn trang trí
**Mô tả:** Mỗi phần tử thị giác phải trả lời một câu hỏi của người dùng. Không gradient, không chữ in hoa trang trí, không hiệu ứng không mang nghĩa (UI UX Pro Max, quy tắc Government: tránh "Ornate design", "Motion effects").

**Áp dụng:**
- Bỏ 3 kiểu hero gradient/navy. Header là `surface` phẳng, tiêu đề `headlineMedium`.
- Bỏ "TÀI KHOẢN", "KẾT QUẢ AI" viết hoa. Dùng sentence case.
- Hình bóng đèn trang trí ở F01 (`StreetlightSilhouette`) cần chủ dự án quyết định giữ hay bỏ.

## 3. Tin cậy chuẩn cơ quan nhà nước
**Mô tả:** Người dùng đang thao tác trên dữ liệu tài sản công và báo cáo cho cơ quan quản lý. Giao diện không được nói sai, không hứa điều nó không làm, không để nút "chết".

**Áp dụng:**
- Phân biệt tuyệt đối **"Đã lưu trên thiết bị"** và **"Đã đồng bộ"**.
- Không hiển thị hành động chưa có backend hoặc thuộc vai trò khác. Ví dụ "Tạo lệnh sửa chữa" ở Chi tiết cột là việc của Kỹ sư bảo trì (P-06 audit).
- Một thuật ngữ cho một khái niệm: "Kỹ sư bảo trì" không lẫn với "Manager", "Đèn mờ" không lẫn với "Đèn tắt" (P-08 audit, cần glossary).

## 4. Accessibility là mặc định, không phải tính năng
**Mô tả:** WCAG AA là sàn, AAA cho nội dung chính và CTA. Thiết kế đúng ngay từ token và component, để từng màn không phải tự nhớ.

**Áp dụng:**
- Mọi cặp chữ/nền trong `COLOR_SYSTEM.md` đã được tính ≥4.5:1 ở **cả Light và Night Field**.
- Badge, tab, segmented có `role` + `selected` sẵn trong component.
- Error state luôn có nút "Thử lại". F09 hiện không có, phải sửa.

## 5. Bản đồ trước, giao diện lùi lại
**Mô tả:** Trên màn bản đồ, thông tin địa lý là nội dung chính. Chrome chỉ phục vụ việc đọc bản đồ. Trạng thái tài sản phải đọc được dù nền là vector hay vệ tinh, ngày hay đêm.

**Áp dụng:**
- Marker mã hoá **màu + hình badge + glyph** + viền kép.
- Chrome là surface đặc, không blur, không chiếm >25% chiều cao bản đồ.
- Quick view chỉ có hành động thật ("Đường đi", "Chi tiết").

## 6. Nhất quán qua hệ thống, không qua trí nhớ
**Mô tả:** Cùng một ý nghĩa thì cùng một token, một component, một vị trí trên mọi màn. Nhất quán được bảo đảm bằng hệ thống (theme, component dùng chung, lint), không bằng việc mỗi người nhớ đúng mã màu.

**Áp dụng:**
- `feature/` không import primitive màu.
- Một `LuxTopBar` với 2 biến thể thay 3 kiểu header.
- Một `LuxErrorState` thay 3 cài đặt `MessageState`.
- Mỗi miền trạng thái (tài sản/lệnh/ưu tiên/đồng bộ/IoT) có bộ token riêng. Không mượn màu chéo như `task_kind` đang mượn màu `wo_status`.

## 7. Offline là trạng thái bình thường
**Mô tả:** Mất mạng là điều kiện làm việc thường xuyên, không phải lỗi. Giao diện luôn cho biết dữ liệu là bản lưu lúc nào, thao tác đã được giữ an toàn hay chưa, và không bao giờ hiện màn trắng.

**Áp dụng:**
- Offline hiển thị **dữ liệu cache + banner "Đang xem dữ liệu đã lưu · cập nhật lúc HH:mm"** (tone `info`), không phải Error state (đúng nguyên tắc A4/A6 của đặc tả).
- Mỗi bản ghi có thay đổi local mang badge `status.sync.*`. `failed` và `conflict` không gộp.
- Bản đồ mất nền thì dữ liệu cột vẫn vẽ, kèm thông báo (đã có).

---

## Khi hai nguyên tắc xung đột
Ưu tiên theo số thứ tự. Ví dụ:
- *Mật độ thông tin* (giúp nhìn nhanh) và *cỡ chữ ≥16sp* (#1, #4) xung đột: **giữ cỡ chữ**, chuyển bớt thông tin sang màn chi tiết.
- *Nhận diện thương hiệu navy* và *dễ đọc trong Night Field*: dùng **Navy300** (cùng hue, đủ sáng) thay vì Navy700 trên nền tối (#4 thắng #6).
