# Color System: LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ chủ dự án duyệt.** Token mới đánh dấu 🆕. Token cần xác nhận riêng đánh dấu ⚠️ kèm mã `T-xx` (tổng hợp ở `README.md` và `DESIGN_SYSTEM_REVIEW.md`).
> Mọi tỉ lệ tương phản ở đây được **tính** bằng công thức độ chói tương đối WCAG 2.x (script kiểm tra ghi ở `DESIGN_SYSTEM_REVIEW.md`). Không phải ước lượng.

## 1. Kiến trúc 3 lớp

```
Primitive (giá trị hex, không ngữ nghĩa)  →  Semantic (vai trò, đổi theo Light/Night Field)  →  Component/Domain (badge, marker…)
Navy700 = #1F3864                           color.primary (Light) = Navy700                     status.wo.assigned.content = …
```

**Luật cứng:**
1. Code trong `feature/` **chỉ** đọc lớp Semantic hoặc Domain (qua `MaterialTheme.colorScheme` / `LuxTheme.*`), **không import primitive**. Đây là nguyên nhân gốc của P-01 trong audit.
2. Mỗi **miền nghiệp vụ** (tài sản, lệnh, ưu tiên, đồng bộ, IoT) có **bộ token riêng**, kể cả khi hai token trùng giá trị hex. Lý do: ý nghĩa khác nhau thì phải tiến hoá độc lập được.
3. **Họ sắc độ dùng chung có chủ đích**: xám = trung tính/chưa xác định, xanh navy = đã giao/đang chờ hệ thống, hổ phách = đang diễn ra/cần chú ý, đỏ = hỏng/lỗi/huỷ, cam = xung đột/mất kết nối, xanh lục = tốt/hoàn tất. Màu **không bao giờ** là tín hiệu duy nhất, mỗi badge luôn có icon + nhãn (xem `ACCESSIBILITY.md`).

## 2. Brand colors

| Token | Light | Night Field | Ý nghĩa | Dùng cho | Không dùng cho |
|---|---:|---:|---|---|---|
| `brand.navy` | `#1F3864` Navy700 | `#1F3864` | Nhận diện LuxMap. **Giá trị không đổi** (đã duyệt) | Logo, màn đăng nhập, header phẳng | Chữ trên nền tối (chỉ đạt 1.6:1 trên `#121212`) |
| `color.primary` | `#1F3864` Navy700 | 🆕 `#9DB7E0` Navy300 (✅ T-01 đã duyệt 2026-11-16) | Hành động chính, điều hướng active | Primary button, tab active, link quan trọng, focus trong Dark | Trạng thái nghiệp vụ |

**Vì sao primary ở Night Field là Navy300 thay vì `Green400` (code hiện tại):** quyết định đã duyệt yêu cầu Night Field "không làm mất nhận diện thương hiệu". Teal `Green400 #5FC4B0` hiện là accent Dark, nhưng nó **trùng họ màu với marker/badge "Đèn bình thường"**, nên trên bản đồ đêm một nút active trông giống trạng thái tài sản. Navy300 là sắc navy được làm sáng (cùng hue), đạt **9.17:1** trên `#121212`. **✅ Đã duyệt (T-01, 2026-11-16).** Phương án giữ `Green400` (9.26:1) bị loại.

## 3. Primitive palette

Giữ nguyên toàn bộ primitive v2.0 đang dùng. Chỉ thêm 🆕 khi lớp semantic cần một giá trị chưa có.

### 3.1 Primitive đang có, giữ nguyên
`Navy50 #EEF2F8` · `Navy100 #D9E2EF` · `Navy700 #1F3864` · `Navy800 #172B4D` · `Navy900 #102443` · `Blue500 #3E86C9` · `Blue600 #2D6388` · `Green400 #5FC4B0` · `Amber500 #E9A23B` · `Rose600 #D64545` · `Success600 #059669` · `Danger600 #DC2626` · `Gray25 #F8FAFC` · `Gray50 #F1F5F9` · `Gray200 #E2E8F0` · `Gray500 #64748B` · `Slate600 #475569` · `Gray700 #334155` · `Gray900 #0F172A` · `Dark950 #0D0D0D` · `Dark900 #121212` · `Dark800 #1A1A1A` · `Dark700 #2A2A2A` · `DarkText #F5F5F5` · `DarkSecondary #B3B3B3` · `DarkMuted #A0A0A0` · `ErrorDark #FF9A9A` · cùng các cặp badge v2.0 (mục 6).

### 3.2 Primitive mới 🆕
| Token | Hex | Lý do cần |
|---|---:|---|
| `Gray150` | `#E8EDF3` | Bậc `surfaceContainerHigh` Light, giữa Gray50 và Gray200 |
| `Slate800` | `#1E293B` | `inverseSurface` Light (snackbar) |
| `Navy200` | `#A9BCDD` | `inversePrimary` Light (action trong snackbar) |
| `Blue50` | `#E4EEF6` | `tertiaryContainer` Light |
| `Blue900` | `#173B57` | `onTertiaryContainer` Light |
| `Red700` | `#B91C1C` | `error` Light. Thay `Danger600` cho **chữ** để đạt 6.47:1 (Danger600 chỉ 4.83:1) |
| `Red50` | `#FEE2E2` | `errorContainer` Light (trùng giá trị nền badge "Hỏng/Tắt" v2.0, token riêng) |
| `Red900` | `#7F1D1D` | `onErrorContainer` Light |
| `Emerald700` | `#047857` | `success` Light |
| `Emerald50` | `#D1FAE5` | `successContainer` Light |
| `Emerald900` | `#064E3B` | `onSuccessContainer` Light (8.57:1) |
| `Amber700` | `#B45309` | `warning` Light (đang tồn tại dưới tên `DueTodayColor`) |
| `Amber50` | `#FEF3C7` | `warningContainer` Light |
| `Amber900` | `#78350F` | `onWarningContainer` Light |
| `Sky700` | `#0369A1` | `info` Light (từ palette Government của UI UX Pro Max) |
| `Sky50` | `#E0F2FE` | `infoContainer` Light |
| `Sky900` | `#0C4A6E` | `onInfoContainer` Light |
| `Navy300` | `#9DB7E0` | `primary` Night Field (✅ T-01) |
| `Navy950` | `#0E1E38` | `onPrimary` Night Field |
| `Navy750` | `#21375C` | `primaryContainer` Night Field |
| `Blue300` | `#8FB8DA` | `tertiary` Night Field |
| `Blue950` | `#0B2235` | `onTertiary` Night Field |
| `Blue800` | `#1B3A52` | `tertiaryContainer` Night Field |
| `Blue100` | `#D2E4F2` | `onTertiaryContainer` Night Field |
| `Night100` | `#E6E6E6` | `onSurface` Night Field. Giảm lóa so với `#F5F5F5`, vẫn 15.01:1 |
| `Night850` | `#1C1C1C` | `surfaceContainer` Night Field |
| `Night800` | `#262626` | `surfaceContainerHigh` Night Field |
| `Night750` | `#2E2E2E` | `surfaceContainerHighest` Night Field |
| `Night500` | `#8C8C8C` | `outline` Night Field (≥3:1 cho control) |
| `NightError700` | `#4A1717` | `errorContainer` Night Field (= nền badge "Hỏng/Tắt" Dark v2.0) |
| `Mint300` | `#6FD3A8` | `success` Night Field |
| `Mint900` / `Mint100` | `#11392A` / `#BDF0D6` | `successContainer` / `onSuccessContainer` Night Field |
| `Sky300` / `Sky950` / `Sky100` | `#7CC4F0` / `#0F3550` / `#CDE9FA` | `info`, `infoContainer`, `onInfoContainer` Night Field |

### 3.3 Primitive đề xuất loại bỏ khỏi code
25 primitive "Night Ledger" v2.1 (`Paper`, `Ink`, `Ink2`, `Line`, `LineSoft`, `Lumen*`, `Night`, `NightSurf*`, `NightText*`, `LumenNight*`, `InfoContainerDark`) **không có trong vNext**. Xác minh 2026-11-16: không nơi nào ngoài `core/theme` dùng chúng, trừ `DisabledContent`, mà vNext thay bằng `color.disabledContent`. Việc xoá thuộc giai đoạn dọn dẹp, không làm trong tài liệu này. `BrandHeroGradient` / `Navy900` / `Blue600` cho gradient: **loại khỏi giao diện** theo quyết định #5. Primitive `Navy900`, `Blue600` vẫn giữ (Blue600 tái dùng làm tertiary Light).

## 4. Semantic: Material 3 roles

Đây là bảng ánh xạ trực tiếp vào `lightColorScheme(...)` / `darkColorScheme(...)` (xem `COMPOSE_IMPLEMENTATION.md`). Cột "Tương phản" là cặp chữ/nền tiêu biểu.

### 4.1 Nhóm Primary / Secondary / Tertiary

| Role | Light | Night Field | Tương phản (L / NF) | Ý nghĩa & cách dùng |
|---|---:|---:|---|---|
| `primary` | `#1F3864` | `#9DB7E0` ✅ | 11.10 trên bg / 9.17 trên surface | CTA chính, icon + nhãn tab active, link quan trọng |
| `onPrimary` | `#FFFFFF` | `#0E1E38` | 11.62 / 8.15 | Chữ/icon trên nền primary |
| `primaryContainer` | `#D9E2EF` Navy100 | `#21375C` | (nền) | Chỉ báo tab active, chip đã chọn, nền thẻ được highlight |
| `onPrimaryContainer` | `#172B4D` | `#D9E2EF` | 10.79 / 9.10 | Chữ/icon trên primaryContainer |
| `secondary` | `#475569` | `#B3B3B3` | 7.58 / 8.93 | Hành động phụ không nhấn mạnh, icon điều khiển trung tính |
| `onSecondary` | `#FFFFFF` | `#121212` | 7.58 / 8.93 | |
| `secondaryContainer` | `#E2E8F0` | `#2A2A2A` | (nền) | Segment/filter đang chọn kiểu trung tính, tonal button |
| `onSecondaryContainer` | `#0F172A` | `#E6E6E6` | 14.48 / 11.50 | |
| `tertiary` | `#2D6388` Blue600 | `#8FB8DA` | 6.46 / 8.95 | Link văn bản, hành động "xem thêm", đường đi (route) không lỗi trên UI (không phải trên bản đồ) |
| `onTertiary` | `#FFFFFF` | `#0B2235` | 6.46 / 7.75 | |
| `tertiaryContainer` | `#E4EEF6` | `#1B3A52` | (nền) | Thẻ gợi ý/hướng dẫn trung tính |
| `onTertiaryContainer` | `#173B57` | `#D2E4F2` | 9.92 / 9.09 | |

### 4.2 Nhóm nền và bề mặt

Thứ tự nâng dần (Light: tối dần · Night Field: sáng dần). Chi tiết phân tầng ở `SHAPE_SURFACE.md`.

| Role | Light | Night Field | Dùng cho |
|---|---:|---:|---|
| `background` | `#F8FAFC` Gray25 | `#0D0D0D` Dark950 | Nền màn hình |
| `surface` | `#FFFFFF` | `#121212` Dark900 | Nền mặc định của Scaffold con, sheet, top bar |
| `surfaceContainerLowest` | `#FFFFFF` | `#0D0D0D` | Card trên nền `surfaceContainer` |
| `surfaceContainerLow` | `#F8FAFC` | `#121212` | Vùng nền phụ (lane) |
| `surfaceContainer` | `#F1F5F9` Gray50 | `#1C1C1C` | Card mặc định trong Night Field, nền nhóm (segmented track) |
| `surfaceContainerHigh` | 🆕 `#E8EDF3` | `#262626` | Thanh chrome trên bản đồ (Night), menu |
| `surfaceContainerHighest` | `#E2E8F0` Gray200 | `#2E2E2E` | Text field filled, skeleton |
| `surfaceVariant` | `#F1F5F9` | `#1C1C1C` | Tương thích M3 cũ. **Ưu tiên dùng `surfaceContainer*`** |
| `surfaceDim` / `surfaceBright` | `#E2E8F0` / `#FFFFFF` | `#0D0D0D` / `#2E2E2E` | Chỉ để đủ bộ M3, không có use-case riêng |
| `surfaceTint` | `Color.Transparent` | `Color.Transparent` | **Tắt tonal tint**: thiết kế phẳng, phân tầng bằng token tường minh |
| `onBackground` / `onSurface` | `#0F172A` | `#E6E6E6` | Chữ chính: 17.85 trên surface Light / 15.01 trên surface NF |
| `onSurfaceVariant` | `#475569` | `#B3B3B3` | Chữ phụ, metadata: ≥6.44 trên mọi container Light / ≥6.48 NF |
| `inverseSurface` | 🆕 `#1E293B` | `#E6E6E6` | Snackbar |
| `inverseOnSurface` | `#F1F5F9` | `#1A1A1A` | 13.35 / 13.94 |
| `inversePrimary` | 🆕 `#A9BCDD` | `#1F3864` | Nút action trong snackbar (7.61 trên inverseSurface Light) |

### 4.3 Nhóm viền, focus, lớp phủ

| Role | Light | Night Field | Tương phản | Dùng cho |
|---|---:|---:|---|---|
| `outline` | `#64748B` Gray500 | 🆕 `#8C8C8C` | ≥4.34 (L) / ≥4.04 (NF) trên mọi bề mặt | **Ranh giới mang thông tin**: viền text field, checkbox, outlined button, segmented button. Đạt ≥3:1 (WCAG 1.4.11) |
| `outlineVariant` | `#E2E8F0` Gray200 | `#2A2A2A` Dark700 | 1.23 / 1.21 (cố ý thấp) | **Chỉ trang trí**: viền card, divider. Không bao giờ là cách duy nhất để thấy một control |
| `focusRing` 🆕 (domain) | `#3E86C9` Blue500 | `#9DB7E0` | 3.85 / 9.17 trên surface | Vòng focus bàn phím/D-pad/switch access, rộng 2dp, cách control 2dp |
| `scrim` | `#0F172A` @ 56% | `#000000` @ 68% | | Lớp phủ sau dialog/bottom sheet (giá trị v2.0, giữ nguyên) |
| `overlay.onMedia` 🆕 | `#000000` @ 60% | `#000000` @ 60% | | Nền chip/HUD đặt trên ảnh, camera preview hoặc vệ tinh |

### 4.4 Nhóm trạng thái hệ thống (Error / Success / Warning / Info)

Dùng cho **phản hồi của ứng dụng** (lỗi nhập liệu, lưu thành công, cảnh báo quá hạn, thông tin), **không** dùng cho trạng thái tài sản hay lệnh (mục 6).

| Token | Light | Night Field | Tương phản (L / NF) | Dùng cho |
|---|---:|---:|---|---|
| `error` | 🆕 `#B91C1C` | `#FF9A9A` ErrorDark | 6.47 / 9.22 | Chữ/icon lỗi, viền field lỗi, nhãn "Quá hạn" |
| `onError` | `#FFFFFF` | `#3B0A0A` | 6.47 / 8.38 | Chữ trên nút Destructive |
| `errorContainer` | `#FEE2E2` | `#4A1717` | (nền) | Banner lỗi |
| `onErrorContainer` | `#7F1D1D` | `#FFD5D5` | 8.20 / 11.03 | |
| `success` 🆕 | `#047857` | `#6FD3A8` | 5.48 / 10.31 | Xác nhận thành công, "Đã nộp" |
| `onSuccess` | `#FFFFFF` | `#11392A` | 5.48 / — | |
| `successContainer` | `#D1FAE5` | `#11392A` | (nền) | Banner/snackbar thành công |
| `onSuccessContainer` | `#064E3B` | `#BDF0D6` | 8.57 / 10.11 | |
| `warning` 🆕 | `#B45309` | `#F7C66D` | 5.02 / 11.82 | "Đến hạn hôm nay", cảnh báo không chặn |
| `onWarning` | `#FFFFFF` | `#3A2606` | 5.02 / — | |
| `warningContainer` | `#FEF3C7` | `#4A3310` | (nền) | Banner cảnh báo |
| `onWarningContainer` | `#78350F` | `#FBE2B0` | 8.15 / 9.37 | |
| `info` 🆕 | `#0369A1` | `#7CC4F0` | 5.93 / 9.82 | Thông tin trung tính, "Đang xem dữ liệu đã lưu" |
| `onInfo` | `#FFFFFF` | `#0F3550` | 5.93 / — | |
| `infoContainer` | `#E0F2FE` | `#0F3550` | (nền) | Banner thông tin |
| `onInfoContainer` | `#0C4A6E` | `#CDE9FA` | 8.24 / 10.11 | |

**Thay đổi so với hiện trạng (cần ghi nhớ khi chuyển code):**
- `colorScheme.error` Light đổi `#DC2626` thành `#B91C1C` (4.83 lên 6.47).
- `error` Dark đổi `#DC2626` thành `#FF9A9A` (3.60 lên 9.22, sửa lỗi A1 của audit).
- `DueTodayColor #B45309` trở thành `warning` Light, và có bản Dark `#F7C66D` (sửa 3.47:1 lên 11.82:1).

### 4.5 Disabled

| Token | Light | Night Field | Tương phản | Ghi chú |
|---|---:|---:|---|---|
| `disabledContainer` | `#E2E8F0` | `#2A2A2A` | | Nền nút/field bị vô hiệu |
| `disabledContent` | `#475569` | `#A0A0A0` | 6.15 / 5.49 | Chữ/icon bị vô hiệu |

> ⚠️ **Mâu thuẫn tài liệu, đã ghi nhận:** Design System v2.0 mục 2.2 ghi `disabled.container #94A3B8 / content #F1F5F9` (Light). Code hiện tại (Phase 0, PR #145) dùng `#E2E8F0 / #475569`. vNext **theo code**, vì cặp v2.0 chỉ đạt khoảng 2.6:1 và quá giống nút đang hoạt động. Disabled không bắt buộc đạt 4.5:1 theo WCAG, nhưng LuxMap luôn hiển thị **lý do bị khóa** cạnh nút, nên chữ trong nút vẫn phải đọc được **✅ Đã duyệt (T-02, 2026-11-16):** giữ theo code.

## 5. Màu theo ngữ cảnh lệnh (hạn xử lý)

| Token | Light | Night Field | Dùng cho |
|---|---|---|---|
| `due.overdue` | = `error` | = `error` | Chữ "Quá hạn …" + icon `EventBusy` |
| `due.today` | = `warning` | = `warning` | Chữ "Hôm nay" + icon `Schedule` |
| `due.future` | = `onSurfaceVariant` | = `onSurfaceVariant` | Ngày tương lai + icon `EventAvailable` |

Đây là **alias** (cùng giá trị, khác tên vai trò). Code gọi `LuxTheme.due.today`, không gọi `warning` trực tiếp, để sau này đổi được riêng.

## 6. Domain colors: trạng thái nghiệp vụ

Mỗi token badge là cặp **`container` (nền)** + **`content` (chữ + icon)**. Tất cả đều đạt **≥4.5:1** ở cả hai theme (đã tính, mục 8). Nền Dark của badge chỉ cao hơn `surface` NF khoảng 1.25–1.58:1. Đó là chủ ý: badge Night Field là "chữ sáng trên ô tối", không phải ô sáng gây lóa. Ranh giới badge được nhận biết bằng chữ + icon, không bằng nền.

### 6.1 Tình trạng tài sản (`fixture_status`): giữ nguyên v2.0, chỉ đặt lại tên token

| Token | Nhãn | Light container / content | Night Field container / content | Icon (UI) | Tương phản L / NF |
|---|---|---|---|---|---|
| `status.asset.normal` | Bình thường | `#D1FAE5` / `#065F46` | `#123D34` / `#8CE3D1` | `CheckCircle` | 6.78 / 8.05 |
| `status.asset.dim` | Đèn mờ | `#FEF3C7` / `#92400E` | `#4A3310` / `#F7C66D` | `WarningAmber` (tam giác) | 6.37 / 7.48 |
| `status.asset.out` | Hỏng/Tắt | `#FEE2E2` / `#991B1B` | `#4A1717` / `#FF9A9A` | `Cancel` (X) | 6.80 / 7.25 |
| `status.asset.unknown` | Chưa xác định | `#EFEFEF` / `#555555` | `#303030` / `#D0D0D0` | `HelpOutline` (?) | 6.48 / 8.56 |

Icon theo đúng hình dạng ngữ nghĩa ở Design System v2.0 mục 2.3 (tròn+check · tam giác+cảnh báo · X · dấu hỏi). Màu marker trên bản đồ là token riêng (mục 7).

### 6.2 Trạng thái lệnh (`wo_status`)

Light giữ đúng giá trị code đang dùng. Night Field là 🆕.

| Token | Nhãn | Light | Night Field | Icon | L / NF |
|---|---|---|---|---|---|
| `status.wo.open` | Mở | `#EFEFEF` / `#555555` | `#2E2E2E` / `#CFCFCF` | `Assignment` | 6.48 / 8.72 |
| `status.wo.assigned` | Đã giao | `#E8EEF7` / `#1F3864` | `#1C2A44` / `#C3D3EE` | `Assignment` | 9.96 / 9.48 |
| `status.wo.inProgress` | Đang thực hiện | `#FFF3DC` / `#8A5A00` | `#3D2C0B` / `#F3C978` | `Autorenew` | 5.39 / 8.59 |
| `status.wo.done` | Hoàn tất | `#E3F6F1` / `#1E6B5C` | `#12352E` / `#93DCCB` | `CheckCircle` | 5.65 / 8.47 |
| `status.wo.verified` | Đã nghiệm thu | `#D1FAE5` / `#065F46` | `#0F3B2B` / `#86E0B5` | `Verified` | 6.78 / 7.94 |
| `status.wo.cancelled` | Đã huỷ | `#FBE4E4` / `#9B2C2C` | `#451818` / `#F8A5A5` | `Cancel` | 6.21 / 7.82 |

Trạng thái hiển thị dẫn xuất ở client (không phải `wo_status` thật), dùng token hệ thống:
- **"Quá hạn"** dùng `errorContainer` / `onErrorContainer` + `ErrorOutline`.
- **"Đã nộp · chờ đồng bộ"** dùng `status.sync.queuedOnline`.

Không thêm enum mới.

### 6.3 Ưu tiên lệnh

| Token | Nhãn | Light | Night Field | Icon/hình | L / NF |
|---|---|---|---|---|---|
| `status.priority.low` | Thấp | `#F1F5F9` / `#475569` | `#262626` / `#BDBDBD` | 1 vạch | 6.92 / 8.06 |
| `status.priority.normal` | Bình thường | `#E8EEF7` / `#1F3864` | `#1C2A44` / `#C3D3EE` | 2 vạch | 9.96 / 9.48 |
| `status.priority.high` | Cao | `#FEF3C7` / `#92400E` | `#4A3310` / `#F7C66D` | 3 vạch | 6.37 / 7.48 |
| `status.priority.urgent` | Khẩn | `#FEE2E2` / `#991B1B` | `#4A1717` / `#FF9A9A` | `PriorityHigh` (!) | 6.80 / 7.25 |

> ⚠️ **Mâu thuẫn tài liệu, đã ghi nhận (T-03):** bảng "Badge ưu tiên" trong `CLAUDE.md` / DS v2.0 mục 2.5 ghi **một** màu đơn cho mỗi mức (`#64748B / #3E86C9 / #E9A23B / #D64545`). Code (`Color.kt`) dùng **cặp nền/chữ** như bảng trên. Các màu đơn của v2.0 không đạt 4.5:1 khi làm chữ trên nền trắng (ví dụ `#E9A23B` chỉ đạt 2.17:1). **✅ Đã duyệt (T-03, 2026-11-16):** vNext theo code (cặp nền/chữ) + icon "vạch". Bảng trong CLAUDE.md sẽ được cập nhật khi chuyển code token.
> Biểu tượng "vạch" cho priority **đã duyệt** (T-03), để không phụ thuộc màu. Hiện code chỉ có chữ.

### 6.4 Trạng thái đồng bộ (`sync_queue.status`): Light giữ nguyên v2.0, Night Field 🆕

| Token | Nhãn | Light | Night Field | Icon | L / NF |
|---|---|---|---|---|---|
| `status.sync.queuedOffline` | Chờ mạng | `#EFEFEF` / `#555555` | `#2E2E2E` / `#CFCFCF` | `CloudOff` | 6.48 / 8.72 |
| `status.sync.queuedOnline` | Chờ đồng bộ | `#E8EEF7` / `#1F3864` | `#1C2A44` / `#C3D3EE` | `Schedule` | 9.96 / 9.48 |
| `status.sync.syncing` | Đang đồng bộ | `#FFF3DC` / `#8A5A00` | `#3D2C0B` / `#F3C978` | `Sync` | 5.39 / 8.59 |
| `status.sync.failed` | Đồng bộ lỗi | `#FBE4E4` / `#9B2C2C` | `#451818` / `#F8A5A5` | `ErrorOutline` | 6.21 / 7.82 |
| `status.sync.conflict` | Xung đột | `#FDE8D0` / `#8A3B00` | `#45250B` / `#F6B27A` | `Warning` | 6.51 / 7.60 |
| `status.sync.done` | Đã đồng bộ | `#E3F6F1` / `#1E6B5C` | `#12352E` / `#93DCCB` | `CloudDone` | 5.65 / 8.47 |

`failed` và `conflict` **không bao giờ gộp** (CLAUDE.md). "Đã lưu trên thiết bị" là phản hồi lưu local (dùng `success`/snackbar), **không** phải `status.sync.done`.

### 6.5 Trạng thái thiết bị IoT 🆕

Dữ liệu thật hiện có: `has_iot_node` (bool), `iot_node.node_status` (`online` / `offline`), `last_report_at`.

| Token | Nhãn đề xuất | Light | Night Field | Icon | L / NF | Điều kiện |
|---|---|---|---|---|---|---|
| `status.iot.online` | Đang kết nối | `#E3F6F1` / `#1E6B5C` | `#12352E` / `#93DCCB` | `SensorsOutlined` | 5.65 / 8.47 | `node_status = online` |
| `status.iot.offline` | Mất kết nối | `#FDE8D0` / `#8A3B00` | `#45250B` / `#F6B27A` | `SensorsOff` | 6.51 / 7.60 | `node_status = offline` |
| `status.iot.none` | Không có cảm biến | `#F1F5F9` / `#475569` | `#262626` / `#BDBDBD` | `RemoveCircleOutline` | 6.92 / 8.06 | `has_iot_node = false` |
| `status.iot.stale` | **TBD** | | | | | ⚠️ T-04: backend chưa có khái niệm "dữ liệu cũ". Ngưỡng và nhãn cần xác nhận. Không tự định nghĩa |

Nhãn "Online" tiếng Anh hiện tại đổi thành "Đang kết nối" (đề xuất ngôn ngữ, P-08 audit). `offline` dùng **họ cam** (không phải đỏ): thiết bị mất kết nối là cần chú ý, không phải tài sản hỏng. Tránh trùng nghĩa với "Hỏng/Tắt".

### 6.6 Loại lệnh (`task_kind`): đổi sang trung tính 🆕

Hiện tại `WorkOrderTaskKind.badgeColors()` **mượn màu trạng thái** (Kiểm tra = "Đã giao", Sửa chữa = "Đang thực hiện"…). Kết quả là badge loại lệnh trông như badge trạng thái, vi phạm luật số 2 ở mục 1.

| Token | Light | Night Field | Phân biệt bằng |
|---|---|---|---|
| `kind.container` / `kind.content` | `#F1F5F9` / `#334155` (9.45) | `#262626` / `#D4D4D4` (10.21) | **Icon + nhãn** (`Search` Kiểm tra · `Build` Sửa chữa · `Videocam` Khảo sát · `LocationOn` Cập nhật GPS cột) |

## 7. Màu bản đồ (GIS)

Chi tiết hình dạng ở `GIS_MAP_GUIDELINES.md`. Phần này chỉ định nghĩa token màu.

| Token | Giá trị | Nền vector sáng | Nền vệ tinh tối (`#2B3A2F` ước lượng) | Ghi chú |
|---|---:|---|---|---|
| `map.marker.normal.fill` | `#5FC4B0` Green400 | 2.10 | 5.72 | Giữ giá trị hiện tại |
| `map.marker.dim.fill` | `#E9A23B` Amber500 | 2.17 | 5.54 | Giữ |
| `map.marker.out.fill` | `#D64545` Rose600 | 4.38 | 2.74 | Giữ |
| `map.marker.unknown.fill` | `#64748B` Gray500 | 4.76 | 2.52 | Giữ (viền trắng bù cho nền tối) |
| `map.marker.halo.inner` 🆕 (đổi vai trò) | `#FFFFFF`, 2.5 (viewport 40) | 1.15 ❌ | **12.01** ✅ | Đã có (stroke trắng). Đảm bảo tương phản trên nền **tối** |
| `map.marker.halo.outer` 🆕 | `#0F172A` @ 100%, 1.5 ngoài halo trắng | **15.56** ✅ | (không cần) | **Mới**. Đảm bảo tương phản trên nền **sáng** |
| `map.marker.glyph` | `#1F3864` trên đĩa trắng | 11.62 | 11.62 | Đã có |
| `map.selection.ring` | Light map: `#1F3864` · Satellite: `#9DB7E0` | 10.12 | 5.88 | Vòng chọn |
| `map.route.normal` | `#3E86C9` Blue500 | 3.35 | 3.12 | Giữ, rộng ≥4dp, **viền trắng 1dp** (12.01:1 trên nền tối) |
| `map.route.fault` | `#D64545` Rose600 + **nét đứt** | 3.82 | 2.74 ❌ | **Viền trắng 1dp bắt buộc** (bù nền tối). Phân biệt với tuyến thường bằng nét đứt, không bằng màu |
| `map.chrome.*` | = `surface` / `onSurface` / `outlineVariant` | | | Chrome là surface đặc, không trong suốt |

**Vì sao không đổi màu marker theo nền bản đồ:** không màu nào đạt ≥3:1 trên cả hai nền. Thay vì nhân đôi bộ marker (khó bảo trì, dễ lệch), **viền kép** (trắng trong + tối ngoài) bảo đảm đường biên marker luôn ≥12:1 trên nền tối và ≥15.6:1 trên nền sáng, bất kể màu đổ. Người dùng nhận ra *có* marker nhờ viền, và nhận ra *trạng thái* nhờ hình dạng + glyph + màu.

**Hạn chế đã biết:** Đèn mờ và Bình thường chênh nhau **1.02–1.03:1** về độ chói. Khi nhìn ở thang xám hoặc với người mù màu đỏ-lục, hai màu này **không phân biệt được**, nên badge glyph và hình dạng là **bắt buộc**.

## 8. Bảng kiểm tra tương phản

Tất cả cặp ở mục 4 và 6 đã tính và **đạt ≥4.5:1** cho chữ. Các cặp chữ chính và CTA đạt mức AAA ≥7:1:

| | Light | Night Field |
|---|---|---|
| `onSurface` / `surface` | 17.85 | 15.01 |
| `primary` / `background` | 11.10 | 9.17 (trên surface) |
| `onPrimary` / `primary` | 11.62 | 8.15 |

Mức thấp nhất trong các cặp chữ là `warning` trên trắng: **5.02** (AA, dùng cho nhãn ngắn kèm icon).

**Cặp không đạt 3:1 có chủ đích** (chỉ trang trí, phải có tín hiệu khác đi kèm):

| Cặp | Light | NF |
|---|---|---|
| `outlineVariant` / surface | 1.23 | 1.21 |
| `primaryContainer` (chỉ báo tab) / surface | 1.31 | 1.58 |

Tab active được nhận biết chủ yếu bằng **icon filled + nhãn màu primary + weight SemiBold**. Chỉ báo nền chỉ là tín hiệu phụ.

## 9. 🆕 F01 brand surface — T-07 đã duyệt 2026-11-16

Ngoại lệ chỉ cho header Login: navy phẳng, nhỏ gọn, không gradient; form vẫn theo theme.

| Token Kotlin | Light | Night Field | Vai trò / nơi dùng |
|---|---|---|---|
| `LoginColors.headerContainer` | Navy700 `#1F3864` | Navy700 `#1F3864` | Nền header F01 |
| `LoginColors.onHeader` | White `#FFFFFF` | White `#FFFFFF` | Wordmark, mô tả và icon/nhãn kết nối trên header |

Cặp chữ/nền đạt **11.62:1** ở cả hai theme; không dùng `primary/onPrimary` cho khối này vì primary Night Field sáng hơn. Thanh trạng thái hệ thống giữ nền `surface`, nội dung header và form nằm ngoài vùng inset.

## 🆕 Màu thương hiệu (2026-11-25)

Truy cập qua `LuxTheme.brand`. Chỉ là trang trí thương hiệu, không mang ngữ nghĩa trạng thái. Cập nhật: header F01 nay dùng `hero` (Night Field là `#0E1E38`) và phủ cả vùng thanh trạng thái; `LoginColors.onHeader` vẫn dùng cho chữ.

| Token | Light | Night Field | Vai trò |
|---|---:|---:|---|
| `hero` | `#1F3864` | `#0E1E38` | Nền hero và thanh tiêu đề màn con |
| `onHero` | `#FFFFFF` | `#FFFFFF` | Chữ chính trên hero |
| `onHeroVariant` | `#D9E2EF` | `#A9BCDD` | Chữ phụ, nét cột đèn |
| `heroShapeA` / `heroShapeB` | `#2A4A7F` / `#33558F` | `#21375C` / `#2A4570` | Hình tròn hoạ tiết |
| `lumen` | `#FFC44D` | `#FFC44D` | Điểm nhấn: đầu đèn, tia sáng |
| `onLumen` | `#0E1E38` | `#0E1E38` | Chữ/icon trên nền Lumen |

Quy tắc: Lumen không làm chữ hoặc icon đơn lẻ trên nền sáng (tương phản ≈ 1,6:1). Tương phản được khoá bởi `BrandColorsTest`.
