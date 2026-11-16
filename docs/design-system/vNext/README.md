# LuxMap Mobile: Design System vNext

> **Trạng thái: ĐỀ XUẤT, chờ chủ dự án duyệt.** Chưa có code nào được thay đổi theo tài liệu này. Design System v2.0 (`docs/LuxMap_Mobile_Design_System_v2.0.md`) vẫn là tài liệu hiện hành cho tới khi vNext được duyệt. Không xoá, không ghi đè v2.0.

## 1. Design Direction
**Civic Utility: Light-first + Night Field + Restrained Material 3.**

- **Light-first:** giao diện sáng là mặc định. Đọc tốt nhất dưới nắng khi sửa chữa, nghiệm thu.
- **Night Field:** theme tối có **token riêng** cho ca đêm (chụp cột, khảo sát, xem bản đồ). Giảm lóa, không đảo màu.
- **Restrained Material 3:** dựng bằng component M3 để có sẵn trợ năng và semantics, nhưng không dùng Expressive (motion lò xo, màu rực, shape tương phản) và không dùng dynamic color.

Nguồn quyết định:
- `docs/design-system/AUDIT_REPORT.md` (hiện trạng).
- `docs/design-system/DESIGN_DIRECTION_RESEARCH.md` (so sánh 3 hướng, dùng UI UX Pro Max làm tham chiếu).
- 12 quyết định đã duyệt (mục 4).

## 2. Cấu trúc tài liệu

| File | Nội dung | Đọc khi |
|---|---|---|
| `DESIGN_PRINCIPLES.md` | 7 nguyên tắc, có thứ tự ưu tiên khi xung đột | Bắt đầu mọi quyết định thiết kế |
| `COLOR_SYSTEM.md` | Primitive · M3 roles · success/warning/info/disabled/overlay · 6 bộ trạng thái nghiệp vụ · màu bản đồ · bảng tương phản | Chọn bất kỳ màu nào |
| `TYPOGRAPHY.md` | 15 slot M3, kiểm tra font thực tế, số liệu, tiếng Việt, font scaling | Chọn chữ |
| `SPACING_LAYOUT.md` | Spacing scale, lề, list item, safe area, Bottom Nav, Bottom Action Bar | Dựng layout |
| `SHAPE_SURFACE.md` | Shapes M3, viền, elevation, phân tầng bề mặt | Dựng card/sheet/chrome |
| `COMPONENT_SPECIFICATIONS.md` | 30 component: Navigation · Actions · Information · Input · Feedback | Dựng/sửa component |
| `GIS_MAP_GUIDELINES.md` | Marker, halo, selected, cluster, chrome, legend, quick view, filter, KPI | Bất kỳ thứ gì trên bản đồ |
| `DARK_NIGHT_FIELD.md` | Khi nào dùng, quy tắc màn camera, những gì không làm | Màn đêm/camera |
| `ACCESSIBILITY.md` | 19 hạng mục bắt buộc, phân loại Auto/Thủ công | Trước khi coi một màn là xong |
| `COMPOSE_IMPLEMENTATION.md` | Ánh xạ sang `ColorScheme`, `Typography`, `Shapes`, `CompositionLocal`, preview, semantics | Chuyển thành code |
| `DESIGN_SYSTEM_REVIEW.md` | Kết quả tự review, mâu thuẫn, vấn đề còn tồn tại | Trước khi duyệt |

## 3. Cách tra cứu token

```
Cần màu cho…                         → Tra ở
nút / nền / chữ / viền chung          → COLOR_SYSTEM.md §4  (MaterialTheme.colorScheme.*)
lỗi / thành công / cảnh báo / thông tin → COLOR_SYSTEM.md §4.4 (LuxTheme.colors.*)
hạn xử lý (quá hạn / hôm nay)         → COLOR_SYSTEM.md §5  (LuxTheme.colors.due*)
badge tài sản / lệnh / ưu tiên / sync / IoT → COLOR_SYSTEM.md §6 (LuxTheme.status.<miền>)
marker / tuyến / vòng chọn trên bản đồ → COLOR_SYSTEM.md §7 + GIS_MAP_GUIDELINES.md
chữ                                   → TYPOGRAPHY.md §2   (MaterialTheme.typography.<slot>)
khoảng cách                           → SPACING_LAYOUT.md §1 (LuxSpacing.*)
bo góc                                → SHAPE_SURFACE.md §1 (MaterialTheme.shapes.*)
```

**Quy tắc đặt tên:**
- `color.<role>` (M3).
- `status.<miền>.<giá trị>.container|content`.
- `map.<đối tượng>.<thuộc tính>`.
- `space.<cỡ>`.
- Slot chữ/shape theo tên M3.
- Primitive giữ tên code hiện có (`Navy700`, `Gray25`…).

## 4. Quyết định đã được duyệt (nguồn: chủ dự án, 2026-11-16)

1. Light-first: giao diện sáng là mặc định.
2. Brand Primary **Navy700 `#1F3864`**, không đổi.
3. Typography **Be Vietnam Pro**.
4. **WCAG AA** tối thiểu, **AAA** ưu tiên cho nội dung chính và CTA.
5. **Loại bỏ gradient** khỏi các màn hình.
6. **Night Field** có hệ token riêng, không đảo màu.
7. **Material 3** làm nền tảng, **không Expressive**.
8. **Không** Android Dynamic Color.
9. **Vector** là nền bản đồ mặc định, Satellite là tùy chọn. Code đã đúng như vậy.
10. GIS marker kết hợp **màu + hình dạng + biểu tượng + halo/viền**.
11. Design System viết thủ công. UI UX Pro Max là tham khảo, không phụ thuộc generator.
12. **Không thay đổi nghiệp vụ và navigation** ở giai đoạn này.

**Duyệt bổ sung (2026-11-16, theo đề xuất mặc định):**

13. **T-01:** Primary Night Field = **Navy300 `#9DB7E0`** (thay `Green400`).
14. **T-02:** Disabled Light theo code: `#E2E8F0` / `#475569`.
15. **T-03:** Badge ưu tiên dùng cặp nền/chữ + icon "vạch" (Khẩn: `PriorityHigh`).
16. **T-06:** Radius: card 12, nút 12, sheet 24, chip 8 (`SHAPE_SURFACE.md` §1).

Mọi nội dung khác trong vNext là **đề xuất**.

## 5. Điểm còn chờ xác nhận

| Mã | Câu hỏi | Đề xuất mặc định | Chặn giai đoạn nào |
|---|---|---|---|
| ~~T-01~~ | ✅ **Đã duyệt 2026-11-16:** Primary Night Field = **Navy300 `#9DB7E0`** | — | — |
| ~~T-02~~ | ✅ **Đã duyệt 2026-11-16:** Disabled Light theo code (`#E2E8F0`/`#475569`) | — | — |
| ~~T-03~~ | ✅ **Đã duyệt 2026-11-16:** Badge ưu tiên dùng cặp nền/chữ + icon "vạch" (Khẩn: `PriorityHigh`) | — | — |
| T-04 | IoT "dữ liệu cũ": ngưỡng thời gian và nhãn? Backend chưa có khái niệm này | Chưa định nghĩa | Không chặn |
| **T-05** | Chữ số không đều độ rộng: dùng font thứ hai (Roboto hệ thống có `tnum`) cho slot số liệu, hay chỉ giảm nhẹ bằng layout? | Giảm nhẹ bằng layout trước | Không chặn |
| ~~T-06~~ | ✅ **Đã duyệt 2026-11-16:** radius card 12, nút 12, sheet 24, chip 8 | — | — |
| **T-07** | Màn Đăng nhập: nền khối navy phẳng (không gradient) hay nền `background` như mọi màn? Giữ hình bóng đèn trang trí? | Navy phẳng, bỏ hình trang trí | Redesign F01 |
| T-08 | Thay `TopSlideToast` (trên đỉnh) bằng Snackbar M3 (dưới)? | Đồng ý | Component |
| T-09 | Badge "Gần khu vực nhạy cảm": đổi từ tròn đỏ (trùng "Hỏng/Tắt") sang vuông trắng viền navy? | Đồng ý | Redesign bản đồ |
| **T-10** | Marker: badge đổi **hình dạng** theo trạng thái (tam giác / thoi / tròn nét đứt) + thêm **viền tối ngoài** cho mọi marker? (sửa 7 file `ic_marker_*`) | Đồng ý | Redesign bản đồ |
| T-11 | Có thêm lớp **Fault** hoặc **Work Order** lên bản đồ mobile không? Hiện không có, nên vNext không định nghĩa | Không (giữ phạm vi) | Không chặn |
| T-12 | Có tìm style **bản đồ tối** miễn phí cho ca đêm không? | Khảo sát, không cam kết | Không chặn |
| T-13 | Công tắc **Night Field thủ công** trong Cá nhân (độc lập với hệ thống)? Là tính năng mới | Không, ở giai đoạn này | Không chặn |
| T-14 | Màn camera đêm: có giảm độ sáng màn hình không? | Không | Không chặn |
| T-15 | Ép luật "`feature/` không import primitive" bằng lint rule hay chỉ checklist review? | Checklist trước, lint sau | Không chặn |
| **T-16** | Thêm thư viện **`androidx.lifecycle:lifecycle-runtime-compose`** để dùng `collectAsStateWithLifecycle`? | Đồng ý | Không chặn (tối ưu) |

**Đã duyệt (2026-11-16):** T-01, T-02, T-03, T-06. Giai đoạn token **không còn bị chặn**.
**Cần duyệt trước khi redesign bản đồ:** T-09, T-10.

## 6. Quy tắc mở rộng Design System

1. **Token mới phải có lý do.** Ghi vào bảng 🆕 của file tương ứng: tên, giá trị Light + Night Field, vai trò, chỗ dùng. Không có use-case thật thì không thêm.
2. **Kiểm tra tương phản trước khi thêm.** Chữ ≥4.5:1 (≥7:1 nếu là nội dung chính/CTA). Phi văn bản ≥3:1. Kiểm cả hai theme.
3. **Không thêm `TextStyle` ngoài 15 slot.** Được tạo biến thể đổi màu/weight, không đổi size.
4. **Trạng thái nghiệp vụ mới** (ví dụ khi backend thêm enum): tạo bộ `status.<miền>.<giá trị>` mới, chọn họ sắc độ theo ý nghĩa (`COLOR_SYSTEM.md` 1), **luôn** kèm icon + nhãn. Không tái dùng token của miền khác.
5. **Component mới:** viết spec theo mẫu của `COMPONENT_SPECIFICATIONS.md` (mục đích, khi dùng, anatomy, kích thước, chữ, màu, states, a11y, anti-pattern) **trước** khi code.
6. **Thay đổi giá trị đã duyệt** (Navy700, font, 12 quyết định ở mục 4) phải hỏi chủ dự án.
7. Khi code mâu thuẫn với tài liệu: **ghi vào `DESIGN_SYSTEM_REVIEW.md`** và hỏi. Không âm thầm sửa bên nào.

## 7. Bước tiếp theo (chưa làm, chờ duyệt)
T-01, T-02, T-03, T-06 đã duyệt (2026-11-16). Giai đoạn redesign sẽ đi theo thứ tự ở `AUDIT_REPORT.md` mục 12:
1. Dọn dẹp.
2. Nền tảng token/theme theo `COMPOSE_IMPLEMENTATION.md`.
3. Component lõi.
4. Lần lượt từng màn.

Mỗi bước chia commit ≤400 dòng và dừng chờ duyệt (CLAUDE.md).
