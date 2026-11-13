# LuxMap Mobile — Design System v2.0 "Civic Grade" + Tái thiết kế UI/UX

**Ngày:** 2026-11-13
**Trạng thái:** Spec — chờ chủ dự án duyệt trước khi lên implementation plan
**Phạm vi:** Toàn bộ lớp trình bày của app Android (22 màn hình, 22 component dùng chung, ~19.000 dòng UI)
**Không thuộc phạm vi:** Nghiệp vụ, API, Room, sync queue, camera/GPS/BLE pipeline

---

## 1. Bối cảnh và mục tiêu

LuxMap là phần mềm **phục vụ cơ quan nhà nước** (quản lý chiếu sáng đường giao thông nông thôn cấp xã), xác nhận với chủ dự án ngày 2026-11-13. Điều này đặt chuẩn thiết kế ở mức **institutional/civic-grade**, không phải thẩm mỹ consumer.

Yêu cầu ban đầu nêu hai mục tiêu thoạt nhìn mâu thuẫn: "hiện đại, premium, tạo ấn tượng" và "ưu tiên trải nghiệm thực tế". Bối cảnh nhà nước gỡ được mâu thuẫn này: các hệ thống thiết kế khu vực công hàng đầu (GOV.UK, U.S. Web Design System) đạt cảm giác cao cấp **bằng kỷ luật và sự rõ ràng, không bằng trang trí** — và kỷ luật đó trùng khít với nhu cầu đọc được ngoài nắng, thao tác được khi đeo găng của kỹ sư hiện trường.

**Mục tiêu:**
1. Thống nhất lớp trình bày về **một** nguồn sự thật duy nhất.
2. Đạt tối thiểu **WCAG AA** trên mọi cặp màu nền/chữ, có kiểm chứng bằng số.
3. Đưa chiều sâu và phân cấp thị giác vào giao diện vốn đang hoàn toàn phẳng.
4. Sửa các lỗi hiển thị đang chạy thật trên máy người dùng.
5. Giữ nguyên 100% nghiệp vụ và luồng xử lý hiện có.

---

## 2. Kết quả audit hiện trạng

Toàn bộ số liệu dưới đây đo trực tiếp từ source code nhánh `dev` ngày 2026-11-13, không phải ước lượng.

### 2.1 Điều đang tốt — giữ nguyên, không đụng vào

- **Kiến trúc trạng thái lành mạnh.** 11/14 file `UiState` khai báo đủ `Loading`/`Empty`/`Error`. Ba ngoại lệ (`LoginUiState`, `CaptureUiState`, `SubmitUiState`) là màn tiến trình, khác mô hình một cách hợp lý.
- **Hệ màu trạng thái nghiệp vụ là tài sản mạnh nhất đang có.** Dùng cặp nền+chữ nên tương phản tốt: badge "Bình thường" 6.78:1, "Chưa xác định" 6.48:1, "Đang đồng bộ" 5.39:1 — đều vượt AA.
- **Navy700 `#1F3864` đạt 11.6:1 trên nền trắng** (AAA). Màu neo thể chế đúng đắn, phù hợp quy ước thị giác khu vực công Việt Nam.
- **Be Vietnam Pro** là lựa chọn font đúng: thiết kế riêng cho tiếng Việt, dấu rõ ở cỡ nhỏ, bundle offline đúng nguyên tắc offline-first.
- MVVM + Repository tách bạch, offline-first được tôn trọng.

**Kết luận: vấn đề nằm hoàn toàn ở lớp trình bày.** Redesign không cần và không được chạm vào nghiệp vụ.

### 2.2 Vấn đề P0 — lỗi đang chạy thật trên máy người dùng

**P0-1. Thanh điều hướng dưới đang hiển thị màu tím mặc định của Material.**

`BottomNavBar.kt:37-39` dùng `MaterialTheme.colorScheme.primaryContainer` và `onPrimaryContainer`. Hai role này **không được định nghĩa** trong `Theme.kt`, nên rơi về bảng màu baseline M3 — viên chỉ báo tab đang chọn là tím `#EADDFF`, chữ/icon tím đậm `#21005D`.

Nguyên nhân: commit `2187ba1` (Phase 0 redesign v2.1) thêm hai role này; commit rollback `530f772` (2026-10-14) xoá khỏi theme **nhưng không dọn nơi gọi**. Lỗi tồn tại gần một tháng, hiển thị trên **mọi** màn hình có bottom nav.

Nơi bị ảnh hưởng: `BottomNavBar.kt:37,38,39`, `CoverageScreen.kt:108,116,122`, `SubmitScreen.kt:142,150,156`, `MapScreen.kt:653`.

**P0-2. Ba trong bốn màu nhãn mức ưu tiên Work Order không đạt WCAG AA.**

Các màu này dùng làm **màu chữ trần trên nền surface**, không có nền riêng (theo `WorkOrderPriority.color()`).

| Mức | Màu | Tương phản trên nền trắng | Kết quả |
|---|---|---:|---|
| Thấp | `#64748B` | 4.76:1 | Đạt AA (sát ngưỡng) |
| Bình thường | `#3E86C9` | 3.85:1 | **Không đạt** |
| Cao | `#E9A23B` | **2.17:1** | **Hỏng nặng** |
| Khẩn | `#D64545` | 4.38:1 | **Không đạt** |

Nhãn "Cao" ở mức 2.17:1 gần như không đọc được ngoài trời. Nghịch lý: mức ưu tiên càng cao thì nhãn càng khó đọc.

**P0-3. Chữ phụ sát ngưỡng.** `Gray500 #64748B` trên nền `Gray25 #F8FAFC` đạt **4.55:1** — vừa đủ AA, không còn dư địa cho điều kiện ánh sáng thực địa.

### 2.3 Vấn đề P1 — Design System đã vỡ thành 4 thế hệ chồng nhau

| Thế hệ | Trạng thái thực tế trong code |
|---|---|
| **v2.0** | Chính thức, đang chạy |
| **v2.1 "Night Ledger"** | Đã rollback 2026-10-14, nhưng 22 token vẫn nằm trong `Color.kt:45-66`; `HudChip.kt`/`LoginScreen.kt` còn gọi |
| **v2.1 phụ trợ** | Thang typography **thứ hai** (9 `TextStyle` rời, `Type.kt:96-115`) + thang radius **thứ hai** (`Radius`, `Dimens.kt:28-35`) chạy song song bộ cũ |
| **"v3.0.1"** | Tham chiếu ở `Type.kt:11`, `PoleDetailScreen.kt:202,211,245`, `LuminanceTrendChart.kt:36` — **không có tài liệu nào trong `docs/`**, không ai kiểm chứng được |

Hệ quả đo được:
- `Radius.*` dùng 172 lần; `Dimens.radius*` 33 lần; 11 chỗ ghi `RoundedCornerShape(<số>)` thô.
- `Dimens.radiusLarge` và `Dimens.radiusSheet`: **0 lần dùng** (token chết).
- 54 mã màu hex hardcode nằm ngoài `core/theme`, tập trung ở các màn mới nhất (`ReviewSubmitScreen` 12, `PolePhotoResultBottomSheet` 9, `PendingApprovalScreen` 6).

### 2.4 Vấn đề P1 — không có chiều sâu

Trong ~19.000 dòng UI có đúng **1** lệnh đổ bóng (`LoginScreen.kt:202`, giá trị 2dp cục bộ, không khớp `elevation.sheet = 8dp` mà Design System v2.0 mục 4.2 quy định). Thống kê container: 0 `ElevatedCard`, 0 `OutlinedCard`, 0 `CardDefaults.cardElevation`, 38 `Card(`, 22 `Surface(`, 39 `.border(`.

Design System v2.0 chỉ định nghĩa `elevation.card = 1dp hoặc viền 1dp` và không có token elevation nào trong code. Toàn bộ phân tầng dựa vào viền xám 1px.

**Đây là nguyên nhân gốc khiến giao diện trông phẳng và thiếu cao cấp — không phải màu hay bo góc.**

### 2.5 Vấn đề P1 — không có navigation chrome thống nhất

Ba kiểu header cùng tồn tại:
- `AppTopBar` — 6 màn hình
- `TopAppBar` M3 thô — `PoleDetailScreen`
- Hero gradient tự vẽ — **9 file, copy-paste, không có component chung**

`NotificationListScreen.kt:343` còn tự chế gradient 2 stop, comment trong code ghi rõ *"distinct from the 3-stop BrandHeroGradient"*. `PoleListScreen` dùng đồng thời cả `AppTopBar` lẫn hero gradient.

`AppTopBar.kt:22-23` còn tự định nghĩa `SubtitleTextStyle` 13sp không thuộc thang chữ nào.

### 2.6 Vấn đề P2 — iconography một chiều

184 lần `Icons.Filled`, **0** Outlined, **0** Rounded. Không có quy tắc filled/outlined cho trạng thái chọn — thanh tab chỉ phân biệt bằng màu, bỏ phí một kênh thị giác miễn phí và một lớp dự phòng cho người khiếm sắc.

### 2.7 Vấn đề P2 — IA và cấu trúc màn hình

- **Tab "Khảo sát" ở Home rỗng vĩnh viễn.** Comment tại `HomeScreen.kt:293-297` ghi rõ: *"it always shows a count of 0 and its own empty state"*. Một ngõ cụt cố ý để khớp mockup.
- **Không có màn hàng đợi đồng bộ (F13).** Thư mục `feature/sync/` không tồn tại. App offline-first mà người dùng không có nơi nào kiểm chứng dữ liệu đã lên server — lỗ hổng niềm tin, đi ngược nguyên tắc nền tảng số 4 của chính Design System v2.0 ("Đã lưu trên thiết bị" khác "Đã đồng bộ").
- **Không có F11 Báo cáo sự cố.** Thư mục `feature/faultreport/` không tồn tại dù CLAUDE.md liệt kê.
- **Màn hình quá tải:** `WorkOrderDetailScreen` 1.754 dòng, `MapScreen` 1.267 dòng, `PoleDetailScreen` 801, `PoleListScreen` 800.
- **Bốn luồng khảo sát** (video toàn tuyến · ảnh từng cột · video ngắn từng cột · nhập tài sản mới) cùng đổ vào một tab, chưa có mô hình phân cấp. Luồng thứ năm (`pole_gps_update` + QR) đang chờ backend.

### 2.8 Vấn đề P2 — accessibility chưa được kiểm chứng

94/132 `contentDescription` là `null`. Phần lớn là icon trang trí (đúng chuẩn), nhưng chưa có rà soát nào phân biệt icon trang trí với icon mang thông tin. 49 vùng `clickable` nhưng chỉ 38 lần dùng `minTouchTarget`. Chưa có kiểm thử font scaling.

---

## 3. Design Direction — "Civic Grade"

**Tuyên ngôn:** Cao cấp đến từ **kỷ luật**, không từ trang trí. Mỗi hiệu ứng thị giác phải trả lời được câu hỏi *"nó giúp kỹ sư hoàn thành việc nhanh hơn ở chỗ nào?"* — nếu không trả lời được, bỏ.

**Năm nguyên tắc:**

1. **Trang nghiêm trước, bắt mắt sau.** Giao diện phải đọc ra "hệ thống của cơ quan nhà nước", không phải "ứng dụng startup".
2. **Tương phản là tính năng, không phải tuỳ chọn.** Tối thiểu AA (4.5:1) cho mọi chữ; nhắm AAA (7:1) cho chữ phụ và mọi thứ hiển thị ngoài trời.
3. **Màu mạnh chỉ dành cho trạng thái nghiệp vụ.** Chrome giao diện dùng navy và thang trung tính. Khi một ô màu đỏ xuất hiện, nó phải luôn có nghĩa.
4. **Chiều sâu mang nghĩa chức năng.** Độ nổi biểu thị mức độ tương tác và ưu tiên, không bao giờ dùng để trang trí.
5. **Bền theo thời gian.** Phần mềm nhà nước có vòng đời dài. Không glassmorphism, không gradient trang trí ngoài hero, không bo góc quá lớn, **không dynamic color / Material You** (sẽ phá vỡ hệ màu trạng thái vốn mang nghĩa nghiệp vụ).

**Bảng màu:** hướng **Navy thuần** (đã chốt với chủ dự án 2026-11-13). Navy700 là màu thương hiệu duy nhất, mở rộng thành ramp đầy đủ. Phương án "Navy + đỏ quốc gia" bị loại vì app bảo trì đã dùng đỏ cho "Hỏng/Tắt" và "Khẩn" — thêm đỏ thương hiệu sẽ gây nhiễu cảnh báo.

---

## 4. Design System v2.0

### 4.1 Color

#### Lớp Primitive

**Navy ramp** (thương hiệu — mở rộng từ Navy700 hiện có):

| Token | Hex | Dùng cho |
|---|---|---|
| `navy-50` | `#EEF2F8` | Nền container được chọn, viên tab active |
| `navy-100` | `#D9E2EF` | Nền nhấn nhẹ |
| `navy-200` | `#B8C8DF` | Viền nhấn |
| `navy-300` | `#8EA6C8` | Chữ phụ trên nền navy đậm |
| `navy-400` | `#6382AC` | Trạng thái disabled trên nền navy |
| `navy-500` | `#41608F` | Hover/pressed |
| `navy-600` | `#2C4A76` | Pressed của nút chính |
| `navy-700` | `#1F3864` | **Màu neo thể chế** (giữ nguyên) |
| `navy-800` | `#172B4D` | Hero đậm |
| `navy-900` | `#102443` | Hero gradient (giữ nguyên) |
| `navy-950` | `#0B1830` | Điểm đậm nhất |

**Neutral ramp** (slate hơi ngả xanh để hoà với navy — giữ nguyên toàn bộ giá trị cũ làm tập con, nên rủi ro thấp):

`#F8FAFC` · `#F1F5F9` · `#E8EEF5` · `#E2E8F0` · `#CBD5E1` · `#94A3B8` · `#64748B` · **`#475569`** · `#334155` · `#1E293B` · `#0F172A`

Bậc `#475569` là bậc mới, thay `Gray500` ở vai trò chữ phụ.

**Dark surface ramp** (đổi từ xám trung tính sang **tối ám navy** để ăn nhập thương hiệu):

| Token | Hex cũ | Hex mới | Vai trò |
|---|---|---|---|
| `dark-950` | `#0D0D0D` | `#0B1017` | Nền trang / elevation 0 |
| `dark-900` | `#121212` | `#0F1621` | elevation 1 |
| `dark-850` | — | `#141D2B` | elevation 2 |
| `dark-800` | `#1A1A1A` | `#1A2433` | elevation 3 |
| `dark-700` | `#2A2A2A` | `#243043` | elevation 4 / viền |
| `dark-600` | — | `#2F3D52` | Viền nhấn |
| `dark-text` | `#F5F5F5` | `#E8EDF5` | Chữ chính |
| `dark-muted` | `#A0A0A0` | `#94A3B8` | Chữ phụ |

#### Lớp Semantic — bảng kiểm chứng tương phản

| Semantic | Light | Dark | Tương phản |
|---|---|---|---|
| `background` | `#F8FAFC` | `#0B1017` | — |
| `surface` | `#FFFFFF` | `#0F1621` | — |
| `surface.subtle` | `#F1F5F9` | `#141D2B` | — |
| `text.primary` | `#0F172A` | `#E8EDF5` | 17.9:1 / 16.2:1 |
| `text.secondary` | **`#475569`** | **`#94A3B8`** | **7.24:1** / **7.44:1** |
| `border` | `#E2E8F0` | `#243043` | — |
| `focus` | `#2C4A76` | `#8EA6C8` | ≥ 4.5:1 |
| `nav.active` | `#1F3864` | `#8EA6C8` | 11.6:1 |
| `nav.inactive` | `#475569` | `#94A3B8` | 7.24:1 |

#### Ánh xạ M3 — bắt buộc khai báo **đủ** role đang dùng

Nguyên nhân gốc của P0-1 là khai báo thiếu. Spec này yêu cầu định nghĩa tường minh toàn bộ role mà code có gọi tới, tối thiểu: `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`, `secondary`, `onSecondary`, `tertiary`, `onTertiary`, `background`, `onBackground`, `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`, `error`, `onError`, `errorContainer`, `onErrorContainer`.

| Role | Light | Dark |
|---|---|---|
| `primary` | `#1F3864` | `#8EA6C8` |
| `onPrimary` | `#FFFFFF` | `#0B1017` |
| `primaryContainer` | `#EEF2F8` | `#172B4D` |
| `onPrimaryContainer` | `#1F3864` | `#D9E2EF` |

`primaryContainer` + `onPrimaryContainer` ở Light đạt **10.34:1** — sửa dứt điểm lỗi tím.

**Quy tắc bắt buộc:** thêm kiểm thử tự động chặn hồi quy — mọi role M3 được tham chiếu trong code phải có mặt trong cả hai color scheme. Lỗi P0-1 tồn tại một tháng vì không có lưới an toàn nào.

#### Sửa P0-2: mức ưu tiên chuyển từ chữ trần sang badge

Thay vì sửa từng mã màu, sửa **cấu trúc**: mức ưu tiên dùng cặp nền+chữ như mọi hệ trạng thái khác. Tái dùng các cặp đã kiểm chứng, không phát minh màu mới.

| Mức | Nền | Chữ | Tương phản | Trước |
|---|---|---|---:|---:|
| Thấp | `#F1F5F9` | `#475569` | **6.91:1** | 4.76:1 |
| Bình thường | `#E8EEF7` | `#1F3864` | **9.96:1** | 3.85:1 |
| Cao | `#FEF3C7` | `#92400E` | **6.37:1** | 2.17:1 |
| Khẩn | `#FEE2E2` | `#991B1B` | **6.80:1** | 4.38:1 |

### 4.2 Elevation — thang 5 bậc có ngữ nghĩa

Mỗi bậc gắn một nhiệm vụ. Không dùng độ nổi để trang trí.

| Bậc | Nhiệm vụ | Light | Dark |
|---|---|---|---|
| 0 | Nền trang | không bóng | `#0B1017` |
| 1 | Thẻ ở trạng thái nghỉ | `0 1px 2px rgba(15,23,42,.05)` + viền 1px | `#0F1621` + viền |
| 2 | Nổi / đang tương tác / ưu tiên cao | `0 2px 6px rgba(15,23,42,.10)` + `0 1px 2px rgba(15,23,42,.06)` | `#141D2B` + viền |
| 3 | Chrome dính (top bar, bottom nav khi nội dung cuộn dưới) | `0 4px 12px rgba(15,23,42,.12)` | `#1A2433` + viền |
| 4 | Modal, bottom sheet, dialog | `0 8px 28px rgba(15,23,42,.18)` | `#243043` + viền |

**Dark Mode:** bóng vô hình trên nền tối → chiều sâu biểu đạt bằng **sắc độ bề mặt + viền**, đúng nguyên tắc "ưu tiên viền thay vì shadow đậm trong Dark Mode" đã có ở Design System v2.0 mục 4.2.

**Quy tắc dùng:** thẻ của công việc đang thực hiện dùng bậc 2 + dải navy 3px bên trái; các thẻ còn lại bậc 1. Người dùng biết nhìn đâu trước mà không cần đọc chữ.

### 4.3 Typography — một thang duy nhất

Gộp 3 thang đang chạy song song. Ánh xạ thẳng vào slot M3 để `MaterialTheme.typography` dùng được ngay, không cần `TextStyle` rời.

| Vai trò | Cỡ/Dòng/Đậm | Slot M3 |
|---|---|---|
| Display | 28/34/700 | `displaySmall` |
| Tiêu đề màn | 22/28/700 | `titleLarge` |
| Tiêu đề mục | 17/24/600 | `titleMedium` |
| Nội dung chính | 16/24/400 | `bodyLarge` |
| Nhãn nút | 16/24/600 | `labelLarge` |
| Metadata phụ | 14/20/500 | `bodySmall` |
| Overline | 12/16/600, letter-spacing .07em | `labelSmall` |

Giữ Be Vietnam Pro. Giữ sàn 16sp cho nội dung đọc. Caption 14sp chỉ dùng cho metadata phụ, **không** dùng cho cảnh báo/hướng dẫn (quy tắc cũ, giữ nguyên).

`SubtitleTextStyle` 13sp cục bộ trong `AppTopBar` bị loại — dùng Metadata 14/20/500.

### 4.4 Spacing, grid, radius

- **Nhịp 4dp.** Spacing scale giữ nguyên: 4 · 8 · 12 · 16 · 24 · 32 · 40.
- **Lề màn hình thống nhất 16dp** (hiện `Spacing.lg` 16dp và `ScreenMargin` 20dp lẫn lộn — chọn 16dp vì mật độ thông tin quan trọng trên thiết bị hiện trường).
- **Một thang radius duy nhất**, hợp nhất `Radius` và `Dimens.radius*`:

| Token | Giá trị | Dùng cho |
|---|---:|---|
| `badge` | 6dp | Badge trạng thái |
| `chip` | 10dp | Chip, pill nhỏ |
| `control` | 12dp | Nút, ô nhập |
| `card` | 16dp | Thẻ, panel |
| `sheet` | 28dp | Bottom sheet, dialog |
| `pill` | 999dp | Viên tròn hoàn toàn |

`Radius.block` 20dp bị loại (chồng lấn `card`). `Dimens.radiusLarge`/`radiusSheet` xoá (vốn đã chết).

- **Vùng chạm tối thiểu 48×48dp**, không có ngoại lệ.

### 4.5 Iconography

- **Icon đặc (Filled) = đang chọn / đang hoạt động. Icon nét (Outlined) = mặc định.** Áp dụng trước hết cho bottom navigation.
- Một bộ duy nhất: Material Symbols. Không trộn phong cách.
- Kích thước chuẩn: 20dp (trong dòng chữ) · 24dp (mặc định) · 32dp (nhấn mạnh).
- Icon mang thông tin **phải** có `contentDescription`; icon trang trí để `null`. Rà soát toàn bộ 132 vị trí hiện có và phân loại dứt khoát.

### 4.6 Motion

- Thời lượng: 120ms (phản hồi tức thì) · 200ms (chuyển trạng thái) · 300ms (chuyển màn).
- Easing: `FastOutSlowIn` mặc định.
- **Tôn trọng cài đặt giảm chuyển động của hệ thống** — bắt buộc với phần mềm công.
- Không dùng animation trang trí. Chuyển động chỉ để giải thích quan hệ không gian hoặc phản hồi thao tác.

### 4.7 Hệ màu trạng thái — giữ nguyên

Bốn hệ (tình trạng tài sản · trạng thái đồng bộ · trạng thái work order · mức ưu tiên) **giữ nguyên ngữ nghĩa và nhãn tiếng Việt**. Chỉ hai thay đổi:
1. Mức ưu tiên chuyển sang badge (mục 4.1).
2. Chuẩn hoá thành component chính thức thay vì mỗi nơi tự dựng.

Đây là nơi **duy nhất** màu được phép mạnh.

---

## 5. Hệ component

### 5.1 Xây mới

| Component | Lý do |
|---|---|
| `HeroHeader` | Thay 9 bản copy-paste gradient; 1 nguồn duy nhất |
| `ScreenScaffold` | Khung chuẩn: chrome + nền + inset + xử lý 4 trạng thái |
| `LuxCard` | Thẻ theo thang elevation, biến thể `rest`/`raised`/`priority` |
| `PriorityBadge` | Sửa P0-2 |
| `SectionHeader` | Chuẩn hoá tiêu đề mục |
| `EmptyState` / `ErrorState` | Hiện mỗi màn tự dựng |

### 5.2 Sửa

| Component | Việc cần làm |
|---|---|
| `BottomNavBar` | Sửa lỗi tím; thêm quy tắc icon đặc/nét |
| `AppTopBar` | Gộp vào hệ header 3 biến thể; bỏ `SubtitleTextStyle` cục bộ |
| `StatusBadge` | Gom đủ 4 hệ trạng thái về một API |
| `PrimaryButton` | Bổ sung biến thể secondary/tertiary/destructive |

### 5.3 Dọn

- Xoá 22 token v2.1 "Night Ledger" (`Color.kt:45-66`) sau khi gỡ 2 nơi còn gọi.
- Xoá 9 `TextStyle` rời sau khi các màn chuyển sang slot M3.
- Hợp nhất 2 thang radius; xoá token chết.
- Quy 54 mã hex hardcode ngoài `core/theme` về token.
- Giải quyết tham chiếu "v3.0.1": hoặc tìm lại tài liệu gốc, hoặc gỡ tham chiếu và hợp nhất vào spec này.

---

## 6. Navigation chrome — một hệ, ba biến thể

| Biến thể | Dùng ở | Đặc điểm |
|---|---|---|
| **Hero** | Màn gốc của mỗi tab (Home, Hub Khảo sát, Bản đồ, Cá nhân, Đăng nhập) | Gradient navy, tiêu đề lớn, nội dung bên dưới là tấm bo góc đè lên |
| **Chuẩn** | Mọi màn con | Nền surface, nút quay lại, tiêu đề + phụ đề, elevation bậc 3 khi cuộn |
| **Immersive** | Quay/chụp, bản đồ toàn màn | Overlay tối giản, ẩn bottom nav, nền tối bắt buộc |

Ẩn bottom navigation ở: Capture, các bước xác nhận toàn màn, chế độ điều hướng — giữ nguyên quy tắc Design System v2.0 mục 5.2.

---

## 7. Rà soát và định hướng từng màn hình

**Nhóm A — Màn gốc của tab** (Hero header, ưu tiên hoàn thiện thị giác cao nhất)

| Màn | Vấn đề hiện tại | Hướng xử lý |
|---|---|---|
| `LoginScreen` (592 dòng) | Còn gọi token v2.1; shadow 2dp lệch chuẩn | Hero chuẩn; dọn token; elevation bậc 4 cho tấm nhập liệu |
| `HomeScreen` (681) | Tab "Khảo sát" rỗng vĩnh viễn; thẻ phẳng đồng hạng | Xử lý tab chết (mục 8.1); elevation bậc 2 cho việc đang làm |
| `SurveyHubScreen` (750) | 4 luồng khảo sát chen nhau; 4 hex hardcode | Phân cấp lại (mục 8.3) |
| `MapScreen` (1.267) | Quá tải; dùng role tím | Tách lớp điều khiển khỏi lớp bản đồ; sửa màu |
| `ProfileScreen` (622) | Gánh luôn vai trò bảng đồng bộ | Tách phần đồng bộ ra màn riêng (mục 8.2) |

**Nhóm B — Work order**

| Màn | Vấn đề | Hướng xử lý |
|---|---|---|
| `WorkOrderDetailScreen` (1.754) | Quá tải nghiêm trọng; 3 hex hardcode | Tách theo giai đoạn vòng đời thành các file section; **không đổi logic** |
| `EvidenceCaptureScreen` | Chưa rà | Áp chrome Immersive |

**Nhóm C — Khảo sát**

| Màn | Vấn đề | Hướng xử lý |
|---|---|---|
| `SurveyPlanScreen` (318) | Checklist readiness — mẫu tốt | Giữ cấu trúc, áp token mới |
| `CaptureScreen` (626) | Rủi ro cao (camera/GPS/BLE) | **Chỉ restyle lớp overlay**, không chạm pipeline |
| `CoverageScreen` | Dùng role tím | Sửa màu; áp chrome |
| `SubmitScreen` (308) | Dùng role tím | Sửa màu; áp chrome |
| `AiConfirmationScreen` (547) | Chưa rà | Áp hệ badge trạng thái |
| `PoleListScreen` (800) | Dùng đồng thời 2 kiểu header | Chọn một; áp `LuxCard` |
| `PolePhotoCaptureScreen` (352) | Chưa rà | Chrome Immersive |
| `PoleVideoCaptureScreen` (433) | 2 hex hardcode | Chrome Immersive |
| `PolePhotoResultBottomSheet` (776) | **9 hex hardcode** — nhiều nhất | Dọn về token; elevation bậc 4 |

**Nhóm D — Nhập tài sản** (mới nhất, lệch chuẩn nhiều nhất)

| Màn | Vấn đề | Hướng xử lý |
|---|---|---|
| `ReviewSubmitScreen` (334) | **12 hex hardcode** — nhiều nhất toàn app | Dọn về token |
| `CapturePointScreen` | 5 hex hardcode; bản đồ còn placeholder | Dọn token; giữ placeholder (chờ nghiệp vụ) |
| `TripListScreen` (281) | 2 hex hardcode | Dọn token; áp `LuxCard` |
| `PendingApprovalScreen` (263) | 6 hex hardcode | Dọn token |

**Nhóm E — Còn lại**

| Màn | Vấn đề | Hướng xử lý |
|---|---|---|
| `NotificationListScreen` (632) | Tự chế gradient lệch chuẩn | Dùng `HeroHeader` chung |
| `PoleDetailScreen` (801) | Dùng `TopAppBar` thô; theo "v3.0.1" | Chuyển sang chrome Chuẩn |

---

## 8. Cải thiện UX — không đụng nghiệp vụ

### 8.1 Tab "Khảo sát" rỗng vĩnh viễn ở Home

Hiện tab luôn hiển thị 0 và trạng thái rỗng. **Đề xuất:** bỏ tab khỏi Home, thay bằng một dòng dẫn hướng sang Hub Khảo sát khi có lệnh khảo sát được giao. Giữ nguyên nguồn dữ liệu và logic lọc.

### 8.2 Hàng đợi đồng bộ cần một bề mặt thật

App offline-first nhưng người dùng không có nơi kiểm chứng. **Đề xuất:** màn hình đọc từ `sync_queue` đã có sẵn, hiển thị đủ 6 trạng thái (`queued`/`syncing`/`failed`/`conflict`/`done`, tách `queued` offline/online) bằng hệ badge đã chuẩn hoá. Vào từ Cá nhân và từ chỉ báo đồng bộ trên chrome. **Chỉ đọc** — không thêm hành động ghi, không đụng sync logic.

### 8.3 Phân cấp lại Hub Khảo sát

Bốn luồng hiện chen ngang nhau. **Đề xuất** nhóm theo câu hỏi người dùng thực sự hỏi:
- *"Tôi được giao tuyến nào?"* → danh sách `WorkOrder` `task_kind=survey`
- *"Khu vực này chưa có bản đồ"* → luồng nhập tài sản mới
- *"Tôi cần nộp gì?"* → phiên đang dở, chờ upload

Thuần tổ chức lại thị giác, không đổi route hay điều kiện nghiệp vụ.

### 8.4 Tách `WorkOrderDetailScreen`

1.754 dòng trong một file, chứa cả kiểm tra lẫn sửa chữa, nhiều cổng trạng thái. **Đề xuất** tách thành các file section theo giai đoạn vòng đời. Thuần cấu trúc — **không đổi một dòng logic nào**, có test hồi quy chứng minh.

---

## 9. Không thuộc phạm vi

Nêu tường minh để tránh phạm vi trôi:

- **Không** đổi nghiệp vụ, điều kiện trạng thái, `allowed_actions`, quy tắc cổng duyệt.
- **Không** đổi API, DTO, tên field, Room schema, sync queue logic.
- **Không** chạm vào pipeline camera/GPS/BLE (`VideoCaptureSession.kt`, `ExposureLockController`, `LocationTracker`).
- **Không** thêm thư viện ngoài Ngăn xếp công nghệ đã chốt.
- **Không** làm F11 (Báo cáo sự cố) — chưa có nghiệp vụ chốt.
- **Không** đụng luồng `pole_gps_update` — đang chờ backend xác nhận.
- **Không** dùng Material You / dynamic color.

---

## 10. Lộ trình và rủi ro

### Thứ tự triển khai

| Giai đoạn | Nội dung | Rủi ro |
|---|---|---|
| **0** | Sửa P0: lỗi tím, badge ưu tiên, chữ phụ | Thấp — sửa lỗi thuần |
| **1** | Nền tảng token: color ramp, elevation, typography, radius | Thấp — cộng thêm, chưa đổi màn |
| **2** | Component dùng chung: `HeroHeader`, `ScreenScaffold`, `LuxCard`, badge | Thấp |
| **3** | Dọn: token zombie, hex hardcode, thang trùng | Trung bình — chạm nhiều file |
| **4** | Áp lên màn Nhóm A (màn gốc các tab) | Trung bình |
| **5** | Áp lên Nhóm B, C, D, E | Trung bình |
| **6** | Cải thiện UX mục 8 | Trung bình |
| **7** | Rà accessibility: contentDescription, touch target, font scaling | Thấp |

Giai đoạn 0 đứng riêng và nên lên `dev` trước — đó là lỗi đang ảnh hưởng người dùng thật, không nên chờ cả đợt redesign.

### Rủi ro đã nhận diện

| Rủi ro | Giảm thiểu |
|---|---|
| Hồi quy ở màn camera/GPS rủi ro cao | Chỉ restyle overlay; kiểm tra tay trên máy thật trước khi merge |
| Tái diễn lỗi role M3 thiếu | Thêm test chặn hồi quy (mục 4.1) |
| `connectedDebugAndroidTest` đang hỏng trên emulator | Kiểm tra trên máy thật (SM-A075F) như các đợt trước |
| Commit vượt 400 dòng | Chia nhỏ theo từng bước, đúng quy ước CLAUDE.md |
| Lặp lại thất bại của v2.1 (rollback giữa chừng, để lại token zombie) | Mỗi giai đoạn tự đứng vững; không để hai thế hệ token cùng sống qua một PR |

**Bài học từ v2.1:** đợt redesign trước bị rollback nhưng không dọn sạch, để lại 22 token mồ côi và gây ra lỗi tím kéo dài một tháng. Spec này yêu cầu mỗi giai đoạn phải **tự khép kín**.

---

## 11. Tiêu chí nghiệm thu

1. **Tương phản:** 100% cặp nền/chữ đạt ≥ 4.5:1; chữ phụ và nhãn ngoài trời đạt ≥ 7:1 — có bảng kiểm chứng.
2. **Nguồn sự thật duy nhất:** 0 mã hex ngoài `core/theme`; 1 thang chữ; 1 thang radius; 0 token chết.
3. **Không còn role M3 thiếu:** có test tự động chứng minh.
4. **Chiều sâu:** mọi thẻ/sheet/chrome dùng đúng bậc elevation đã định nghĩa.
5. **Nhất quán chrome:** 0 bản copy-paste hero; mọi màn dùng 1 trong 3 biến thể.
6. **Không hồi quy nghiệp vụ:** toàn bộ unit test hiện có vẫn xanh; các màn rủi ro cao kiểm tra tay trên máy thật.
7. **Accessibility:** toàn bộ vùng chạm ≥ 48dp; `contentDescription` đã phân loại dứt khoát; không vỡ layout ở font scale 1.3×.

---

## 12. Quyết định của chủ dự án (2026-11-13)

Bốn điểm chờ quyết đã được chủ dự án trả lời, chốt như sau:

1. **Spec được duyệt.** Tiến hành lập implementation plan.
2. **Không còn tài liệu gốc "v3.0.1".** Được phép **gỡ toàn bộ tham chiếu** "Design System v3.0.1" trong code (`Type.kt:11`, `PoleDetailScreen.kt:202,211,245`, `LuminanceTrendChart.kt:36`) và hợp nhất mọi quy tắc vào spec này. Spec này trở thành **nguồn sự thật duy nhất** cho lớp trình bày; `LuxMap_Mobile_Design_System_v2.0.md` giữ vai trò tài liệu nền, `v2.1`/`Redesign_Spec_v0.1` chỉ còn giá trị lịch sử.
3. **Giai đoạn 0 tách PR riêng lên `dev` ngay**, không chờ phần còn lại. Lý do: đây là lỗi đang ảnh hưởng người dùng thật.
4. **Được phép bỏ hẳn tab "Khảo sát" ở Home** (mục 8.1) — không ảnh hưởng kỳ vọng của mentor/hội đồng.
