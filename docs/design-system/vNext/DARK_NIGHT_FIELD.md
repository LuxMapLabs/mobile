# Dark "Night Field": LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ duyệt.** Night Field là **theme Dark có token riêng** (bảng đầy đủ ở `COLOR_SYSTEM.md`, cột "Night Field"), không phải đảo màu Light.

## 1. Khi nào dùng

Giữ đúng bảng "Dark Mode theo ngữ cảnh" đã có trong CLAUDE.md. **Không đổi quy tắc chọn theme, không đổi navigation.**

| Ngữ cảnh | Theme | Hiện trạng code |
|---|---|---|
| Chụp ảnh / quay video cột đèn (`PolePhotoCaptureScreen`, `PoleVideoCaptureScreen`) | **Night Field bắt buộc** (`forceDark = true`) | Đã có |
| Các màn camera khác: Ảnh sau sửa chữa, Đo GPS cột, Chụp GPS cột thiếu | Dùng camera hệ thống qua Intent, nên app không vẽ UI trong lúc chụp. Màn **xem lại ảnh** của chúng theo hệ thống | Giữ. Thống nhất khung "chụp → xem lại" là việc của giai đoạn redesign (P-10) |
| F09 khi vào chế độ điều hướng ban đêm | Night Field | Theo CLAUDE.md. Chế độ này hiện điều hướng ra **app bản đồ ngoài** |
| Mọi màn khác | Theo hệ thống (mặc định Light) | Đã có |

⚠️ **T-13:** có nên cho phép người dùng **tự bật Night Field** (công tắc trong Cá nhân) độc lập với cài đặt hệ thống hay không? Đây là tính năng mới, **không tự thêm**. Ghi lại vì FE làm đêm có thể để điện thoại ở chế độ sáng.

## 2. Mục tiêu thiết kế và cách đạt được

| Mục tiêu | Cách làm trong token/component |
|---|---|
| **Giảm lóa** | Nền gần đen `#0D0D0D`/`#121212`. Chữ chính `#E6E6E6` thay `#F5F5F5`: giảm độ chói vẫn giữ 15.01:1. Không có vùng trắng lớn: dialog/sheet dùng `surfaceContainer*`, **không** `surface` Light |
| **Đọc rõ** | Mọi cặp chữ ≥7:1 cho nội dung chính, ≥4.5:1 cho metadata và badge (đã tính) |
| **Accent không bão hoà, không chiếm diện tích lớn** | Primary là Navy300 `#9DB7E0`, độ bão hoà thấp. Accent chỉ ở **chữ/icon/viền mảnh**. Nút Primary là khối duy nhất được dùng nền accent, mỗi màn tối đa **một** |
| **Trạng thái dễ phân biệt** | Badge NF = chữ sáng trên ô tối (nền badge chỉ cao hơn surface 1.25–1.58:1). Phân biệt bằng **icon + nhãn**, không bằng ô màu sáng. Mọi bộ trạng thái có cặp NF riêng (`COLOR_SYSTEM.md` 6) |
| **Không mất nhận diện thương hiệu** | Navy300 cùng hue với Navy700. Logo/nhận diện vẫn là `brand.navy`. ✅ T-01 đã duyệt |

## 3. Quy tắc riêng cho màn camera (chụp ảnh / quay video cột)

**Bố cục:**
```
┌───────────────────────────────┐ ← safeDrawing
│ [←]  POLE-0047   [GPS ±5 m]   │  hàng HUD trên: chip overlay.onMedia
│                               │
│        CAMERA PREVIEW         │  tràn toàn màn, không viền
│        (không phủ màu)        │
│                               │
│  ● 00:12   (khi quay video)   │  đồng hồ: ô độ rộng cố định
│   [Thư viện]  ( ◉ )  [Đổi]    │  shutter ≥64dp (DS v2.0 6.8); nút phụ 48dp
└───────────────────────────────┘
```

**Quy tắc:**
- **Không hiển thị Bottom Navigation** (giữ hành vi hiện tại).
- **HUD chip:** nền `overlay.onMedia` (đen 60%), chữ trắng `labelMedium`, shape `small`. Đây là ngoại lệ duy nhất cho nền bán trong suốt (`SHAPE_SURFACE.md` 5).
- **Shutter:** vòng trắng 4dp + lõi trắng. Khi quay: lõi `error` (đỏ NF `#FF9A9A`) hình vuông bo góc = "đang ghi". Tín hiệu bằng **hình**, không chỉ màu.
- **Cảnh báo** (GPS yếu, sắp hết dung lượng): banner nhỏ trên cùng, tone `warning` NF + icon + chữ. Không chỉ đổi màu chip.
- **Phản hồi chụp/lưu:** rung nhẹ (DS v2.0 mục 7). Cảnh báo dùng kiểu rung khác. Âm thanh chỉ bổ trợ.
- **Không ép độ sáng màn hình** lên tối đa. ⚠️ T-14: có cần chế độ "giảm độ sáng màn hình" khi chụp đêm hay không là quyết định sản phẩm.
- Ghi chú quan trọng (không thuộc màu): khoá exposure thật **không áp dụng** ở luồng chụp ảnh cột v1 (camera hệ thống). Đó là quyết định đã ghi trong CLAUDE.md, Design System không thay đổi điều này.

## 4. Night Field cho các bề mặt khác

| Bề mặt | Token NF |
|---|---|
| Nền màn | `background #0D0D0D` |
| Top bar, sheet, bottom bar | `surface #121212` (+ divider `outlineVariant #2A2A2A`) |
| Card | `surfaceContainer #1C1C1C` + viền `outlineVariant` |
| Chrome trên bản đồ | `surfaceContainerHigh #262626` + viền + shadow Level 2 |
| Text field | Viền `outline #8C8C8C` (5.57:1), focus `primary` |
| Snackbar | `inverseSurface #E6E6E6` / chữ `#1A1A1A`. Ô sáng nhỏ, có chủ đích để nổi bật thông báo tạm thời |
| Ảnh bằng chứng | Viền `outlineVariant`. Không thêm khung trắng |

## 5. Những gì Night Field **không** làm
- Không đảo màu tự động (`invert`), không lấy màu Light rồi giảm alpha.
- Không dùng chữ xám nhạt dưới 4.5:1 để "dịu mắt".
- Không dùng neon/glow, không gradient (anti-pattern của quy tắc Government trong UI UX Pro Max; quyết định #5).
- Không đổi màu marker bản đồ theo theme app. Marker phụ thuộc **nền bản đồ**, không phụ thuộc theme UI (`GIS_MAP_GUIDELINES.md`).

## 6. Kiểm tra bắt buộc (thủ công)
- Chụp màn hình từng màn Night Field, chuyển sang **thang xám**, xác nhận vẫn phân biệt được 4 trạng thái tài sản, 6 trạng thái lệnh, 6 trạng thái đồng bộ bằng icon + chữ.
- Kiểm tra **ban đêm thật**, ngoài trời, độ sáng màn hình 30% và 100%.
- Kiểm tra camera preview không bị UI chồng lấn ở cả máy có notch và máy có thanh điều hướng 3 nút.
