# Typography: LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ duyệt.** Font: **Be Vietnam Pro** (đã duyệt), bundle offline trong `res/font/` (4 weight: Regular 400, Medium 500, SemiBold 600, Bold 700).

## 1. Kiểm tra font thực tế (2026-11-16)

Đọc trực tiếp bảng `cmap`, `hmtx` và `GSUB` của 4 file `.ttf` đang bundle, không suy đoán:

| Kiểm tra | Kết quả | Hệ quả |
|---|---|---|
| Glyph tiếng Việt (ă â đ ê ô ơ ư, 5 dấu thanh, hoa và thường) | ✅ Đủ ở cả 4 weight | Không rơi về font hệ thống |
| Ký hiệu kỹ thuật `° ± · – — … ≥ ≤` | ✅ Đủ | Dùng được cho "±5 m", "≥ 10 ký tự", dấu "·" phân tách metadata |
| OpenType features (GSUB) | `aalt case ccmp dnom frac liga locl numr ordn sinf ss01 subs sups` | **Không có `tnum`** (tabular figures), **không có `lnum`/`onum`/`pnum`** riêng |
| Độ rộng chữ số (Regular, đơn vị/1000) | `0`=676, `1`=385, `4`=710, `7`=594 | **Chữ số tỉ lệ (proportional)**: "111" hẹp hơn "444" khoảng 45% |

**Hệ quả với số liệu IoT, lux, %, timestamp (vấn đề có thật):**
- Số **thay đổi tại chỗ** (bộ đếm, % tiến độ, đồng hồ quay video `00:12`) sẽ **xê dịch ngang** khi giá trị đổi.
- Cột số trong danh sách **không thẳng hàng** theo hàng đơn vị.
- `fontFeatureSettings = "tnum"` **không có tác dụng** với font này.

**Quy tắc bắt buộc cho tới khi có quyết định T-05:**
1. Số thay đổi tại chỗ đặt trong ô có **`widthIn(min = …)` cố định**, căn **phải** (hoặc căn giữa với đồng hồ), để chiều rộng ô không đổi khi giá trị đổi.
2. Cột số trong danh sách căn **phải**.
3. Không đặt số liệu động sát nhãn tĩnh mà không có khoảng đệm.
4. ⚠️ **T-05 (cần duyệt):** có dùng **font thứ hai chỉ cho số** (ví dụ Roboto hệ thống, có `tnum`) ở các slot số liệu hay không. Không tự thêm.

## 2. Thang chữ: đủ 15 slot Material 3

Mọi slot đều dùng `BeVietnamPro`, `letterSpacing = 0.sp`. Không dùng letter-spacing âm: dấu tiếng Việt dễ chạm nhau.

| Slot | Size / Line height | Weight | Vai trò LuxMap | Ánh xạ từ v2.0 / code hiện tại |
|---|---|---|---|---|
| `displayLarge` | 34 / 42 | Bold 700 | Số liệu lớn duy nhất trên màn (ví dụ % độ sáng ở Chi tiết cột) | mới |
| `displayMedium` | 30 / 38 | Bold 700 | Dự phòng, hiện **không dùng** | `DisplayTextStyle` 30sp (bỏ, cùng gradient hero) |
| `displaySmall` | 28 / 36 | Bold 700 | **Display Number** v2.0: lux, số lượng, quãng đường trong KPI | `displayLarge` hiện tại 28/34 |
| `headlineLarge` | 26 / 36 | Bold 700 | Dự phòng (tablet) | mới |
| `headlineMedium` | 24 / 33 | Bold 700 | **Tiêu đề màn tab gốc** ("Việc hôm nay", "Khảo sát", "Bản đồ", "Cá nhân") | `HeadlineTextStyle` 24/30 |
| `headlineSmall` | 22 / 30 | SemiBold 600 | Tiêu đề dialog/bottom sheet quan trọng ("Nộp báo cáo?") | mới |
| `titleLarge` | 20 / 28 | Bold 700 | **H1** v2.0: tiêu đề Top App Bar màn con | `titleLarge` 20/26 (line height tăng 26→28 để dấu chồng không bị cắt) |
| `titleMedium` | 17 / 24 | SemiBold 600 | **H2** v2.0: tiêu đề section, tiêu đề card | giữ nguyên |
| `titleSmall` | 16 / 24 | SemiBold 600 | Dòng tiêu đề của list item, nhãn quan trọng | `BodyStrongTextStyle` 16/22 |
| `bodyLarge` | 16 / 24 | Regular 400 | **Body** v2.0: nội dung đọc, mô tả, ghi chú | giữ |
| `bodyMedium` | 16 / 22 | Regular 400 | Nội dung trong không gian hẹp (card, list item 2–3 dòng) | đổi line height 24→22 để **khác** bodyLarge |
| `bodySmall` | 14 / 20 | Medium 500 | **Caption** v2.0: timestamp, metadata phụ | giữ |
| `labelLarge` | 16 / 24 | SemiBold 600 | Chữ trên **nút** (mọi loại), chữ trên segmented button | giữ |
| `labelMedium` | 14 / 20 | SemiBold 600 | **Badge, chip, nhãn tab lọc, nhãn trường** | thay `BadgeTextStyle` 13sp, nâng lên 14 |
| `labelSmall` | 14 / 20 | Medium 500 | Nhãn **Bottom Navigation**, nhãn trục biểu đồ, chú giải bản đồ | thay `OverlineTextStyle` 13sp; slot hiện tại 14/20 |

### 2.1 Ràng buộc
- **Sàn cứng:** không slot nào < 14sp. Nội dung đọc và chữ hành động ≥16sp (`body*`, `title*`, `labelLarge`). 14sp chỉ cho `bodySmall`, `labelMedium`, `labelSmall`.
- **Không thêm `TextStyle` ngoài 15 slot.** 10 `*TextStyle` rời trong `core/theme/Type.kt` (`NumericTextStyle`, `DisplayTextStyle`, `HeadlineTextStyle`, `MetricTextStyle`, `TitleTextStyle`, `BodyStrongTextStyle`, `BodyTextStyle`, `LabelTextStyle`, `OverlineTextStyle`, `BadgeTextStyle`), `SubtitleTextStyle` (AppTopBar, 13sp) và `HeroMetadataTextStyle` (F09, 12sp uppercase) đều được **ánh xạ vào slot** ở cột cuối bảng trên, rồi bị loại.
- Được phép **biến thể có tên** ở mức component, chỉ đổi `color` hoặc `fontWeight` của một slot (ví dụ "titleSmall + màu error"). **Không đổi `fontSize`**.
- **Ngoại lệ đã biết, cần thử trên máy:** `labelSmall` 14sp cho nhãn Bottom Navigation. "Việc hôm nay" ở 360dp / 4 tab ≈ 90dp mỗi mục. Ở font scale 1.0 vừa khít, ở 1.3+ có thể bị cắt (xem mục 4).

### 2.2 Chữ in hoa
- **Không dùng ALL CAPS để trang trí** (bỏ "TÀI KHOẢN", "KẾT QUẢ AI", "KẾT LUẬN", "KỸ SƯ XÁC NHẬN NHÃN", mã lệnh viết hoa ở hero F09).
- Tiêu đề section dùng **sentence case** + `titleMedium` hoặc `labelMedium` màu `onSurfaceVariant`.
- Mã định danh giữ nguyên dạng hệ thống trả về (`WO-1042`, `POLE-0047`). Không tự chuyển hoa/thường.

### 2.3 Tiếng Việt
- Line height **≥1.35× size** cho mọi slot chứa chữ (headline, title, body, label), để chữ hoa có dấu chồng (Ấ, Ễ, Ỗ) không bị cắt đầu. Riêng 3 slot `display*` (≥1.23×) chỉ dùng cho **số liệu**, không dùng cho chữ có dấu.
- `LineHeightStyle(alignment = Center, trim = None)`. **Không trim** phần trên dòng đầu tiên, vì chính phần đó chứa dấu.
- Không `maxLines = 1` + ellipsis cho: **mã cột, mã lệnh, lỗi đồng bộ, lý do khóa CTA, tên tuyến** (giữ quy tắc v2.0 mục 3). Cho phép wrap 2 dòng.
- Kiểm tra bằng chuỗi mẫu: **"Đã nghiệm thu · Cập nhật GPS cột · Ưu tiên khẩn · Kỹ sư bảo trì yêu cầu bổ sung"**.

## 3. Số liệu, đơn vị, thời gian

| Loại | Slot | Định dạng | Ví dụ |
|---|---|---|---|
| Lux (đo) | `displaySmall` (KPI) / `titleSmall` (dòng) | số + khoảng trắng hẹp + đơn vị ở `bodySmall` | **42** lux |
| Phần trăm độ sáng | `displayLarge` / `titleSmall` | không khoảng trắng | **68%** |
| Độ chính xác GPS | `bodySmall` | `±N m` | ±5 m |
| Toạ độ | `bodySmall` | 6 chữ số thập phân, dấu chấm | 10.762622, 106.660172 |
| Timestamp tương đối gần | `bodySmall` | "Cập nhật lúc HH:mm" | Cập nhật lúc 21:45 |
| Ngày | `bodySmall` / `titleSmall` | `dd/MM/yyyy` (theo `DateFormatUtils`) | 16/11/2026 |
| Bộ đếm | `labelMedium` trong badge | `99+` khi vượt | 3 · 99+ |

Đơn vị luôn **tách khỏi** giá trị, viết thường, đặt ngoài vùng nhập số (DS v2.0 mục 6.2).

## 4. Font scaling

| Mức hệ thống | Hành vi bắt buộc |
|---|---|
| 0.85–1.0 | Thiết kế chuẩn |
| 1.15–1.3 | Không cắt nội dung chính. Card tự giãn cao. Badge có thể xuống dòng **trước** nhãn đi kèm, không cắt nhãn |
| 1.5–2.0 | Layout ngang (2–3 cột KPI, nút song song) **chuyển sang dọc**. Bottom action bar có 2 nút chuyển thành 2 nút xếp chồng. Nhãn Bottom Navigation được phép ellipsis (icon + `contentDescription` vẫn đủ nghĩa) |

- Kích thước dùng `sp` cho chữ, `dp` cho layout. **Không** dùng `sp` cho icon hay kích thước khung.
- Vùng chứa chữ dùng `heightIn(min = …)`, **không** `height(…)` cố định.
- Font scale 2.0 phải kiểm **thủ công trên máy thật** cho: Việc hôm nay, Chi tiết lệnh (CTA + hint khóa), Kết quả AI, Bottom Navigation, bottom sheet hủy lệnh.

## 5. Ánh xạ hiện trạng → vNext (dùng khi chuyển code)

| Hiện tại (`Type.kt`, component) | vNext |
|---|---|
| `NumericTextStyle` 44sp | `displayLarge` 34sp (44sp không có use-case sau khi bỏ hero) |
| `DisplayTextStyle` 30sp | `headlineMedium` (tiêu đề tab gốc) |
| `HeadlineTextStyle` 24sp | `headlineMedium` |
| `MetricTextStyle` 28sp | `displaySmall` |
| `TitleTextStyle` 18sp | `titleLarge` (tiêu đề bar) hoặc `titleMedium` (card), tuỳ vai trò |
| `BodyStrongTextStyle` | `titleSmall` |
| `BodyTextStyle` | `bodyLarge` |
| `LabelTextStyle` 14sp/500 | `bodySmall` (metadata) hoặc `labelMedium` (nhãn) |
| `OverlineTextStyle` 13sp | `labelMedium` sentence case |
| `BadgeTextStyle` 13sp | `labelMedium` |
| `AppTopBar.SubtitleTextStyle` 13sp | `bodySmall` |
| `HeroMetadataTextStyle` 12sp uppercase | `bodySmall`, không uppercase |
| `MaterialTheme.typography.labelMedium` (đang trống, rơi về font hệ thống 12sp) | `labelMedium` vNext |
