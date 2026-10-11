# LuxMap Field — Redesign Spec (Night Ledger) · v0.1 · 2026-10-10

Thiết kế tham chiếu: `LuxMap Redesign.dc.html` (canvas, mã 1a–1y) và `LuxMap App.dc.html` (prototype tương tác 360×800 dp).
Phạm vi đợt 1: hệ thống thiết kế + F02, Hub Khảo sát, F03, F04, F06, Xác nhận AI (mới), F09/F10 tách Kiểm tra / Sửa chữa, F12, F15.
F01 Đăng nhập bổ sung sau (0a–0h). Không thuộc đợt 1: F05, F11, F13 (UI riêng), F14, PoleDetail.

> Đây là spec thiết kế, chưa phải code đã build. Không có file Kotlin nào được sửa. Mọi tên field/endpoint cho Xác nhận AI, Inspection/Repair Task và trạng thái "bị từ chối" vẫn chờ WP2/WP5 (xem CLAUDE.md "Khoảng trống đã biết") — UI dưới đây chỉ định nghĩa hình thức, không định nghĩa schema.

---

## 1. Ý tưởng

App là một công cụ đo đạc ban đêm cho một vai trò (Field Engineer), không phải dashboard.

1. **Dải cột (PoleStrip)** — mô-típ dữ liệu xuyên suốt: mỗi tuyến = một hàng vạch, mỗi vạch = một cột, tô theo `normal/dim/out/unknown`.
2. **Một việc nổi bật** — Trang chủ đẩy việc gấp nhất thành khối mực (`NextUpCard`), còn lại là danh sách gọn theo cụm.
3. **Hành động chính ở đáy** — `BottomActionBar` 56 dp, luôn kèm dòng giải thích khi nút bị khoá.
4. **Dark theo ngữ cảnh** — giữ đúng bảng Dark Mode của Design System v2.0.
5. **Vòng đời 2 cổng duyệt** — `LifecycleTracker` 5 bước trên mọi lệnh.

Thay đổi so với bản hiện tại: bỏ `BrandHeroGradient`, bỏ 4 ô HeroStat trong khối gradient, navy700 không còn là màu thương hiệu. Màu trạng thái (asset/sync/priority/wo_status) **giữ nguyên** từ `Color.kt`.

---

## 2. Design tokens

### 2.1 Màu thương hiệu mới (thay lớp brand/semantic, giữ lớp status)

```kotlin
// core/theme/Color.kt — primitives mới
val Paper     = Color(0xFFF5F4F0) // light background
val Ink       = Color(0xFF15171C) // light text primary, "ink block" surface
val Ink2      = Color(0xFF5B5F68) // light text secondary (≥ 5.9:1 on Paper)
val Line      = Color(0xFFDEDBD3) // light outline
val LineSoft  = Color(0xFFECEAE4) // light divider inside cards
val Disabled  = Color(0xFFE4E2DB) // disabled container (light)
val DisabledOn= Color(0xFF8A8D96)
val Lumen     = Color(0xFF3448D6) // primary (light)
val LumenCont = Color(0xFFE4E7FB)
val LumenOnCont = Color(0xFF1B2A8F)

val Night     = Color(0xFF0B0C0F) // dark background
val NightSurf = Color(0xFF16181D)
val NightSurf2= Color(0xFF1F2128)
val NightLine = Color(0xFF2C2F38)
val NightText = Color(0xFFEDEBE6)
val NightText2= Color(0xFF9A9DA6)
val LumenNight= Color(0xFFA3ADFF) // primary (dark), onPrimary = Night
val LumenNightCont = Color(0xFF262C5E)
val LumenNightOnCont = Color(0xFFDADEFF)
val DangerAction = Color(0xFFDC2626) // = Danger600, giữ nguyên
```

Ánh xạ M3 `ColorScheme`:

| M3 role | Light | Dark |
|---|---|---|
| primary / onPrimary | Lumen / White | LumenNight / Night |
| primaryContainer / on | LumenCont / LumenOnCont | LumenNightCont / LumenNightOnCont |
| background | Paper | Night |
| surface | White | NightSurf |
| surfaceContainerHigh | LineSoft | NightSurf2 |
| inverseSurface / inverseOnSurface | Ink / Paper | — |
| onSurface | Ink | NightText |
| onSurfaceVariant | Ink2 | NightText2 |
| outline | Line | NightLine |
| outlineVariant | LineSoft | Color(0xFF23262D) |
| error | Danger600 | Color(0xFFFF9A9A) |

`Ink` block (NextUpCard, dải tóm tắt AI, avatar) dùng `inverseSurface` trong Light.

> **Lưu ý triển khai (2026-10-11):** Phase 0 implement (`docs/superpowers/plans/2026-10-11-luxmap-redesign-phase0-tokens.md`) đã đổi `outlineVariant` thành `Line`/`NightLine` thay vì `LineSoft`/`#23262D` ở trên — giá trị gốc trong bảng này làm divider gần như vô hình (phát hiện qua final review của Phase 0, xem `docs/contract-drift.md`). Mọi phase sau nên dùng giá trị đã sửa trong code, không phải bảng này.

### 2.2 Màu trạng thái — không đổi
Giữ nguyên toàn bộ `AssetCondition.badgeColors`, `SyncStatus.badgeColors`, `WorkOrderStatus.badgeColors`, `WorkOrderTaskKind.badgeColors`, `WorkOrderPriority.color` trong `Color.kt`.
Bổ sung một token cho vạch PoleStrip (dùng lại primitive có sẵn):

```kotlin
fun AssetCondition.tickColor(): Color = when (this) {
    NORMAL -> Green400; DIM -> Amber500; OUT -> Rose600; UNKNOWN -> Color(0xFF8A8D96)
}
```

Quy tắc: Priority hiển thị bằng **chấm 8 dp màu priority + chữ màu onSurface** — không dùng `Amber500` làm màu chữ trên nền sáng (tương phản 2.2:1).
"Quá hạn" dùng `#9B2C2C` (chữ badge FAILED) cho dòng hạn, `#C53030` cho số lớn ở hàng metrics. "Hạn hôm nay" giữ `#B45309` (DueTodayColor hiện có).

### 2.3 Chữ — Be Vietnam Pro (giữ 4 file .ttf bundle)

| Token | M3 slot | Size/Line | Weight | Tracking | Dùng cho |
|---|---|---|---|---|---|
| numeric | displayMedium | 44/48 | 700 | -0.02em | Đồng hồ quay F04 |
| display | displaySmall | 30/36 | 700 | -0.02em | Tiêu đề tab gốc |
| headline | headlineSmall | 24/30 | 700 | -0.01em | Tiêu đề màn chi tiết |
| metric | headlineMedium | 28/32 | 700 | -0.02em | Hàng số liệu Home, quãng đường |
| title | titleLarge | 18/24 | 600 | 0 | Tiêu đề card, top bar |
| bodyStrong | titleMedium | 16/22 | 600 | 0 | Dòng tiêu đề list item, nút |
| body | bodyLarge | 16/24 | 400 | 0 | Nội dung |
| label | bodyMedium | 14/20 | 500 | 0 | Metadata |
| overline | labelMedium | 13/16 | 600 | +0.08em, UPPERCASE | Tiêu đề section |
| badge | labelSmall | 13/18 | 600 | 0 | Badge, nav label |

- Mọi số dùng `fontFeatureSettings = "tnum"` để timer/khoảng cách không nhảy.
- Ngoại lệ có chủ đích so với DS v2.0 (caption tối thiểu 14sp): overline/badge/nav label 13sp, đều SemiBold. Nếu nhóm muốn giữ 14sp tuyệt đối, đổi 3 token này lên 14 — layout 360 dp vẫn đủ chỗ (đã chừa).
- Tiếng Việt: line-height ≥ 1.22× cho cỡ ≥ 24 để không cắt dấu chồng (Ỗ, Ữ). Không dùng `maxLines = 1` cho tiêu đề lệnh; cho phép 2 dòng.

### 2.4 Hình khối & khoảng cách

```kotlin
object Radius { val badge = 6.dp; val chip = 10.dp; val control = 14.dp; val card = 16.dp; val block = 20.dp; val sheet = 28.dp }
object Spacing { val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp }
object Dimens { val screenMargin = 20.dp; val minTouch = 48.dp; val primaryButton = 56.dp; val recordButton = 84.dp; val topBar = 64.dp; val navBar = 76.dp }
```

Elevation: phẳng. Chỉ phần tử nổi trên bản đồ (search, FAB, sheet) có bóng `0 1 2 / 0 4 14` alpha 8–12%. Card dùng `surface` trên `background`, không viền; card dark dùng viền `NightLine` 1 dp.

---

## 3. Component inventory → Compose

| Component | Mục đích | Hình thức | Compose |
|---|---|---|---|
| `PoleStrip` | Tình trạng tuyến theo cột | Row vạch `weight(1f)`, gap 3 dp, cao 18–22 dp, radius 2 dp | `Row { conditions.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it.tickColor(), RoundedCornerShape(2.dp))) } }`. >40 cột: gộp theo bucket (lấy mức nặng nhất). `semantics { contentDescription = "14 cột, 3 hỏng" }` |
| `NextUpCard` | Việc gấp nhất | Khối `inverseSurface`, radius 20, padding 18; overline LUMEN + priority; title 20/700; PoleStrip; hạn + khoảng cách; 2 nút 48 dp | `Surface(color = inverseSurface, shape = Radius.block)` + `Column`. Nút trái `OutlinedButton`, nút phải `Button(containerColor = LumenNight)` |
| `MetricRow` | 3 số Home | Grid 3 cột, kẻ trên/dưới `outline`, vạch dọc giữa | `Row(Modifier.height(IntrinsicSize.Min))` + `VerticalDivider` |
| `TaskRow` | Một lệnh trong cụm | Tile 40 dp icon theo task_kind, title 16/600 (2 dòng), meta, badge wo_status + dòng hạn, chấm priority bên phải | Thay `WorkOrderCard`. `Row(Modifier.clickable().heightIn(min = 72.dp))` |
| `ClusterSection` | Nhóm theo cụm | Header 15/600 + "n việc"; card `surface` radius 16, divider thụt 68 dp | `LazyColumn` item header + item card |
| `StatusBadge` | Giữ API hiện có | Radius 6 (từ 8), 13/600, padding 3×8 | Sửa `StatusBadge.kt` shape/typography |
| `PriorityDot` | Ưu tiên | Chấm 8 dp + chữ | thay `TextOnlyStatusBadge` |
| `SyncPill` | Trạng thái đồng bộ ở header | Pill 36 dp, icon + giờ tải cuối, màu `SyncStatus` | `Surface(shape = CircleShape, onClick)` → F15/F13 |
| `InlineBanner` | Quá hạn / Ngoại tuyến | Radius 12, icon 20, 14/500; màu FAILED hoặc QUEUED_OFFLINE | Thay `ErrorBanner` + `OfflineBanner` bằng 1 component có `tone` |
| `AppTopBar` | Màn con | 64 dp, back 48 dp, title 18/600 + subtitle 13 | `TopAppBar` với `title = { Column {…} }`, `colors` transparent |
| `BottomActionBar` | Hành động chính | Viền trên, padding 12/20/16, hint 14 căn giữa + nút 56 dp radius 16 | `Scaffold(bottomBar = …)` + `navigationBarsPadding()`. Hint là `Text` có `liveRegion = Polite` |
| `AppNavigationBar` | 4 tab | 76 dp, pill 60×32 `primaryContainer`, icon FILL khi active, label 13 | `NavigationBar` + `NavigationBarItem(colors = indicatorColor = primaryContainer)`. Icons: `Today, Videocam, Map, Person` (Rounded) |
| `ReadinessChecklist` | F03 | Card, mỗi dòng: icon trạng thái 22, label 16, giá trị phải 14, hint đỏ khi fail | Thay `CameraReadinessPanel`. Trạng thái `PENDING/CHECKING/PASS/FAIL`; CHECKING dùng `CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)` |
| `CaptureHudChip` | F04 trạng thái | Cao 32, radius 10, nền `Night` 78%, icon 16 + 13/600 | `Surface(color = Night.copy(alpha = .78f))`. Thay strip scrim |
| `RecordButton` | F04 bắt đầu/dừng | 84 dp vòng 4 dp; inner tròn đỏ 60 dp (bắt đầu) hoặc vuông 30 dp (dừng) | `Box(Modifier.size(84.dp).border(4.dp, …, CircleShape).pointerInput { detectTapGestures(onPress = …) })`. Dừng = **nhấn giữ 900 ms** (`awaitRelease` + `withTimeout`), rung `VibrationEffect` khi đạt |
| `CaptureWarning` | Mất GPS/BLE | Radius 14, nền `#4A3310`, chữ `#F7C66D`, icon warning | Dùng cùng `warningPulses` hiện có cho rung |
| `UploadPartRow` | F06 | Icon theo loại, tên, dung lượng, badge `SyncStatus` | Đọc từ `sync_queue` theo session |
| `ConditionOptionGrid` | Sửa phân loại AI | 2×2 nút 52 dp, vạch màu + nhãn, chọn = viền 2 dp primary | `LazyVerticalGrid(2)` hoặc `FlowRow` + `selectable(role = RadioButton)` |
| `LifecycleTracker` | Vòng đời lệnh | 5 bước, chấm 24 dp, đường 2 dp; done = Ink + check, current = viền primary, future = outline | `Row` 5×`weight(1f)`, vẽ line bằng `drawBehind` |
| `OutcomeSegment` | Kết quả kiểm tra từng cột | 3 nút ≥ 44 dp: Có sự cố / Không có / Chưa xác định; chọn = Ink fill | `SingleChoiceSegmentedButtonRow` (M3) với shape tuỳ chỉnh, hoặc 3 `FilterChip` |
| `EvidencePicker` | Ảnh sau sửa | Tile 88 dp, tile thêm viền đứt | `FlowRow`; tile thêm mở `EvidenceCaptureScreen` hiện có |
| `RequirementList` | Điều kiện đóng lệnh | Icon check/khuyết + nhãn 14 | `Column` |
| `MapSearchBar`, `MapFilterChips`, `PoleQuickSheet` | F12 | Search nổi 52 dp radius 16; chip có chấm màu + số đếm; sheet 3 nút | Giữ logic `MapSearchBar.kt`, `MapStatusFilterRow.kt`, `PoleQuickViewBottomSheet.kt`, đổi style |
| `MessageState` | Rỗng/Lỗi | Card trắng radius 20, icon 32 căn trái, title 18/600, body, nút | Thay `MessageState` trong HomeScreen (căn trái thay vì giữa) |

---

## 4. Màn hình

Mọi màn có dữ liệu bất đồng bộ giữ `sealed interface UiState { Loading, Success(isStale, lastSyncedAt), Empty, Error }`. Offline = `Success(isStale = true)` + `InlineBanner(offline)`.

### F01 Đăng nhập (2a–2h) · Light, không nav
- Hero `inverseSurface` (Ink #15171C, tràn dưới status bar, icon status sáng): logo 40dp placeholder + "LUXMAP" / "Vận hành hiện trường"; chip mạng bên phải (`isOnline`: "Trực tuyến" dot Green400 / "Ngoại tuyến" dot Amber500); display 30 "Chào mừng trở lại" + body #B5B8C0.
- Đáy hero: hình tuyến đèn phối cảnh 13 cột (vẽ bằng Canvas/`drawBehind`, không phải ảnh): thân #343842, đầu đèn #DDE1FF + glow LumenNight 22%; cột 4 và 9 tắt. Trang trí, `contentDescription = null`. Thay gradient cũ.
- Thẻ form `surface` radius 20, lề 16, đè lên hero −24dp, elevation 2 (shadow mềm). Tiêu đề 18/600 "Đăng nhập làm việc" + "Sử dụng tài khoản nhân viên đã được cấp.".
- Trường không focus nền #F8F7F4; focus/lỗi nền trắng.
- "Duy trì đăng nhập" có dòng phụ "Lần mở sau vào thẳng, kể cả khi offline".
- Prefetching (đề xuất): 3 bước Xác thực tài khoản → Lệnh được giao hôm nay → Tuyến khảo sát và bản đồ offline. ⚠ ViewModel hiện chỉ có 1 trạng thái Prefetching; cần tiến độ theo bước, nếu không thì chỉ hiển thị thanh tiến trình.
- Footer: "Kết nối được mã hóa và bảo vệ · v0.9.0" (version lấy từ BuildConfig).
- Trường: label 14/600 trên trường, `OutlinedTextField` cao 56, radius 14, container `surface`, viền `outline` 1.5 → focus `primary` 2 → lỗi `error` 2. Icon đầu badge / lock, mật khẩu có nút mắt 48dp. Giữ KeyboardOptions hiện có.
- Hàng "Duy trì đăng nhập" toàn hàng là vùng chạm 48dp, checkbox ink (mặc định bật).
- Đặt lỗi giữ nguyên `errorPlacementFor`: thiếu mã → dưới mã; thiếu mật khẩu → dưới mật khẩu; sai thông tin → viền đỏ cả 2, message dưới mật khẩu; khoá / mạng / không rõ → banner `#FBE4E4`/`#9B2C2C` trên nút, có icon + dòng gợi ý. Message dùng `#9B2C2C` (không dùng `#DC2626` cho chữ trên Paper).
- Nút chính 56dp Lumen. LoggingIn: nền `primaryContainer`, chữ "Đang đăng nhập…" + icon tiến trình; Prefetching: "Đã đăng nhập" + thẻ surface "Đang tải dữ liệu ngày làm việc..." với `LinearProgressIndicator` và dòng mô tả nội dung đang tải. Trường mờ 60% và khoá khi đang gửi.
- Footer cuối cột cuộn (không ghim): divider, "Cần hỗ trợ đăng nhập?" (chữ thường, chưa có đích), "Kết nối được mã hóa và bảo vệ".
- ⚠ Copy lỗi sai thông tin / khoá tài khoản là tạm; dùng `error.message` từ server khi có.

### F02 Việc hôm nay (1a–1e) · Light
- Mục tiêu: biết ngay việc nào làm trước và đi tới đó.
- Thứ tự: header (ngày + "Việc hôm nay" + SyncPill) → MetricRow (Được giao / Đang xử lý / Quá hạn) → InlineBanner offline → InlineBanner quá hạn → NextUpCard → hàng "Khảo sát tối nay" (nếu có sweep planned) → ClusterSection × n.
- NextUpCard chọn theo: quá hạn > urgent > due today > priority_score. Lệnh nằm trong NextUp không lặp lại ở cụm.
- Bỏ PrimaryButton "Bắt đầu đợt khảo sát mới" khỏi cuối list — thay bằng hàng "Khảo sát tối nay" → tab Khảo sát (giữ một lối vào, không nhân đôi).
- Loading: skeleton đúng hình NextUpCard + 2 row. Empty: MessageState + "Mở tab Khảo sát". Error: MessageState + "Thử lại".
- Số liệu "Đợt khảo sát" (plannedSweepCount) không còn là ô số; thể hiện qua hàng Khảo sát tối nay.

### Hub Khảo sát (1f) · Dark bắt buộc
- Route `survey` (thay SurveyPlanScreen làm landing).
- Section "CẦN BẠN XỬ LÝ" theo thứ tự: bị từ chối (viền `#4A1717`, trích lý do Manager, nút "Khảo sát lại") → chờ xác nhận AI (PoleStrip + đếm, "Xem & xác nhận") → đang tải lên (progress, badge sync).
- Section "TUYẾN ĐƯỢC GIAO": card route (segments, độ dài, ngày dự kiến, người giao) → "Kiểm tra sẵn sàng".
- Empty: "Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì" (copy hiện có).
- ⚠ Card "Bị từ chối" và "Chờ xác nhận AI" cần trạng thái sweep chưa có trong C4 — ẩn sau feature flag tới khi WP2/WP5 chốt.

### F03 Chuẩn bị khảo sát (1g–1j) · Dark bắt buộc
- Route `survey/plan/{surveySweepId}` (mới, tách khỏi danh sách).
- Checklist chạy tuần tự khi vào màn (hiệu ứng từng dòng 300–500 ms, thực tế theo kết quả `CheckSurveyReadinessUseCase`). Giữ đúng 6 mục hiện có + "Quyền Bluetooth" (API 31+).
- Chặn cứng: CTA "Vào chế độ khảo sát" disabled + hint đỏ "Còn n mục chưa đạt — chưa thể khảo sát". Mục fail hiện `fixHint`.
- Thiếu quyền: CTA phụ "Cấp quyền" thay vị trí hint (giữ logic `CAPTURE_PERMISSIONS`).
- Khảo sát lại (1j): banner đỏ trên cùng với lý do từ chối.

### F04 Chế độ quay (1k–1o) · Dark, ẩn nav, khoá xoay
- Nền: preview camera full-bleed. Trên: HUD chips (GPS ±m, lux BLE, AE khoá + shutter/ISO, dung lượng). Dưới: panel `Night` radius 28 trên.
- Trạng thái (giữ `CaptureUiState`): Scanning → Connecting → Ready (GPS gate) → StartingRecording → Recording → Packaging → Packaged / PackagingFailed.
- Ready: nút quay mờ 40% tới khi GPS ≤ ngưỡng `GpsAccuracyGate`; dòng chữ nói rõ giá trị hiện tại và ngưỡng.
- Recording: chip REC trên cùng; timer 44 sp + quãng đường; nút dừng **nhấn giữ** (tránh chạm nhầm khi cầm máy trên xe). Nhãn "Nhấn giữ để dừng quay" → "Tiếp tục giữ…".
- Mất GPS/BLE: `CaptureWarning` phía trên panel + chip GPS đổi "Mất GPS" vàng; không dừng phiên.
- Giữ nguyên: `BackHandler`, `SCREEN_ORIENTATION_LOCKED`, `FLAG_KEEP_SCREEN_ON`, TextureView sống suốt `isCameraSessionLive`.

### F06 Nộp đợt khảo sát (1p) · theo hệ thống (Light)
- Badge "Đã lưu trên thiết bị" ngay khi Packaged (khác "Đã đồng bộ").
- Lưới 2×2: thời lượng, quãng đường, dung lượng, số đoạn video.
- Tiến độ tổng + từng phần (video đoạn 1..n, log GPS+lux, manifest), badge theo `sync_queue.status`.
- Toggle "Chỉ tải lên qua Wi‑Fi" (constraint WorkManager). Nút "Về Khảo sát".

### Xác nhận kết quả AI (1q–1r) · Light · MỚI
- Khối tóm tắt Ink: PoleStrip theo `final` (AI hoặc đã sửa), đếm 3 loại.
- Copy: "Sửa nếu AI phân loại sai, kèm lý do. Kết quả gốc của AI luôn được giữ lại.".
- Chip lọc: Tất cả / Cần xem (AI ≠ normal hoặc đã sửa) / Đã sửa.
- Row: thumbnail khung hình (vạch màu final ở đáy), POLE-id, "km · AI n%" (n < 85 → "tin cậy thấp", màu `#8A5A00`), badge final. Row đã sửa: dòng phụ màu primary "đã sửa từ …".
- Sheet: khung hình 16:9, AI label + %, `ConditionOptionGrid`, ô "Lý do sửa *" chỉ hiện khi chọn khác AI, nút "Lưu thay đổi" disabled tới khi có lý do; chọn lại đúng giá trị AI = "Giữ kết quả AI" (xoá override).
- Gửi: dialog xác nhận → lưu Room + sync_queue → toast "Đã lưu trên thiết bị · sẽ gửi khi có mạng".
- Dữ liệu tối thiểu UI cần (chờ đặt tên chính thức): ai_class, ai_confidence, frame ref/timestamp, final_class, override_reason, overridden_at, overridden_by.

### Lệnh kiểm tra (1s–1t) · Light
- AppTopBar "Lệnh kiểm tra". Header: badge task_kind, wo_status, PriorityDot; title 24; id + cụm; dòng hạn.
- `LifecycleTracker` current = "Kiểm tra". Ghi chú Manager. Mini-map (MapLibre lite, không tương tác) + "Điều hướng" (Intent Google Maps).
- Danh sách cột: vạch màu, POLE-id, loại sự cố + nguồn, nút chỉ đường 48 dp.
- Chưa bắt đầu: CTA "Bắt đầu kiểm tra". Đang làm: mỗi cột có `OutcomeSegment` (fault_present / fault_absent / inconclusive); CTA "Nộp báo cáo kiểm tra" + "n/N cột đã ghi nhận", enable khi đủ.
- Sau nộp: CTA disabled "Đã nộp báo cáo", hint "Chờ Kỹ sư bảo trì duyệt (cổng #2)".

### Lệnh sửa chữa (1u–1v) · Light
- Như trên, tracker current = "Sửa chữa", danh sách cột kèm kết quả kiểm tra.
- Section "NGHIỆM THU" inline (thay màn Completion riêng cho trường hợp phổ biến): EvidencePicker, ghi chú, RequirementList.
- CTA "Hoàn thành lệnh" enable khi ≥ 1 ảnh sau **và** có ghi chú (quy tắc F10). Hint "Cần ảnh sau sửa chữa và ghi chú kết quả".
- Route `WorkOrderEvidenceCapture` giữ nguyên cho chụp ảnh.

### F12 Bản đồ (1w–1x) · Light
- Search nổi + chip lọc có số đếm (Tất cả / Cần xử lý / Hỏng/Tắt / Đèn mờ), FAB vị trí, legend thu gọn góc trái.
- Pole chọn: marker phóng to + halo primary; `PoleQuickSheet`: id, tuyến, badge, lux gần nhất, IoT; 3 nút Đường đi / Báo sự cố (F11) / Chi tiết.

### F15 Cá nhân (1y) · Light
- Nhóm THIẾT BỊ (cảm biến BLE đã ghép, bản đồ offline), ĐỒNG BỘ (hàng đợi F13 + badge lỗi, lần tải cuối), ỨNG DỤNG (giao diện, Thông báo = "Chưa hỗ trợ trong phiên bản này"), nút Đăng xuất viền đỏ.

---

## 5. Điều hướng

- Giữ 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân. Tab active theo nhánh (task detail → tab Việc hôm nay; plan/review/submit → Khảo sát).
- Nav bar ẩn ở mọi màn con và F04. Back hệ thống pop stack; F04 chặn Back khi camera sống.
- Route mới đề xuất (tên route, không phải endpoint): `survey/plan/{sweepId}`, `survey/review/{sweepId}` (đã có `SurveyReview` theo sessionId — cân nhắc dùng lại), `work-order/{id}` hiển thị theo `task_kind`.
- Dark/Light: `LuxMapTheme(forceDark = true)` bọc Hub Khảo sát, F03, F04. Đổi theme giữa tab dùng `Crossfade` 200 ms trên background để tránh nháy.
- Edge-to-edge: `enableEdgeToEdge()`, `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`; status bar icon sáng/tối theo theme (`WindowCompat.getInsetsController`).

## 6. Tương tác & chuyển động

- Nhấn: ripple M3 mặc định; card dùng `Modifier.clickable` với `indication = ripple(bounded = true)`.
- Checklist F03: mỗi dòng đổi icon bằng `AnimatedContent` (fade 150 ms).
- Record → Stop: `animateFloatAsState` cho inner shape (tròn → vuông) 200 ms; nhấn giữ hiển thị vòng đỏ.
- Sheet: `ModalBottomSheet` (skipPartiallyExpanded = true cho sheet AI).
- Toast/xác nhận: `Snackbar` với copy "Đã lưu trên thiết bị…" — không bao giờ nói "Đã đồng bộ" khi chỉ ghi Room.
- Nút khoá luôn có lý do bằng chữ ngay trên nút; không dùng nút disabled câm.

## 7. Responsive

- Baseline 360×800 dp. Lề 20 dp; < 360 dp (VD 320) lề 16 dp, MetricRow chuyển số 24 sp.
- Font scale 1.3×: tiêu đề cho 2–3 dòng, badge xuống dòng (`FlowRow`), BottomActionBar không cố định chiều cao.
- `WindowWidthSizeClass.Medium+` (foldable/tablet): `NavigationRail` thay NavigationBar; Home 2 cột (NextUp + cụm | danh sách); Map sheet thành panel trái 360 dp; F04 giữ full-bleed.
- Landscape F04: panel điều khiển chuyển sang cạnh phải 280 dp (chưa vẽ — đề xuất đợt sau).

## 8. Khả năng truy cập

- Vùng chạm ≥ 48 dp (nút chính 56, record 84). Tương phản: text ≥ 4.5:1 (Ink2/Paper 5.9:1, NightText2/Night 7.4:1, LumenNight/Night 9.6:1).
- Màu không phải kênh duy nhất: badge luôn có chữ; PoleStrip có `contentDescription` tổng hợp; marker bản đồ giữ icon riêng (`ic_marker_*`).
- `liveRegion` cho cảnh báo GPS/BLE và hint CTA.

## 9. Ghi chú triển khai

1. Cập nhật `Color.kt`/`Theme.kt`/`Type.kt`/`Dimens.kt` trước (1 PR, `style(fm-xx)`), rồi từng màn — mỗi PR ≤ 400 dòng theo CLAUDE.md.
2. `BrandHeroGradient` có thể xoá sau khi Home đổi xong; grep các nơi dùng.
3. Không thêm dependency: Material Symbols Rounded có sẵn trong `material-icons-extended` (`Icons.Rounded.*`); không cần font icon riêng.
4. PoleStrip cần list condition theo thứ tự trên tuyến — với WorkOrder hiện tại chỉ có fault_ids; cần thứ tự cột trên segment (có trong `mock-poles.geojson`/RoadSegment). Nếu chưa có, ẩn strip.
5. Mini-map trong lệnh: dùng `MapView` lite (snapshot `MapSnapshotter`) để tránh 2 MapView sống cùng lúc.
6. Thiết kế chưa phủ: F05, F11, F13 (UI riêng), F14, PoleDetail, landscape F04, dialog xung đột 409.

## 10. Câu hỏi mở cho WP2/WP5

- Trạng thái sweep: "AI đã phân loại · chờ FE xác nhận", "Đã gửi duyệt", "Bị từ chối" + lý do + người từ chối.
- Entity Inspection/Repair + enum trạng thái từng giai đoạn; trường `inspection_outcome` theo từng cột (đã có label `fault_present/fault_absent/inconclusive`).
- Ngưỡng GPS chính thức (`GpsAccuracyGate.PROPOSED_THRESHOLD_METERS`) — thiết kế đang hiển thị "≤ 10 m" minh hoạ.
