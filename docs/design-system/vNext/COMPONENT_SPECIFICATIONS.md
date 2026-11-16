# Component Specifications: LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ duyệt.** Đây là đặc tả, chưa phải code. Tên token tham chiếu `COLOR_SYSTEM.md`, slot chữ tham chiếu `TYPOGRAPHY.md`, shape tham chiếu `SHAPE_SURFACE.md`.
> Nền tảng: **Material 3 (Compose BOM 2024.10.01 → material3 1.3.x)**, không Expressive, không dynamic color. Cột "M3 base" cho biết component M3 nào được bọc lại. Một số API (`SegmentedButton`, `SearchBar`, `DatePicker`, `ExposedDropdownMenuBox`, `ModalBottomSheet`) có thể yêu cầu `@OptIn(ExperimentalMaterial3Api::class)` ở bản 1.3.x. Kiểm tra annotation khi code.

**Quy ước chung cho mọi component tương tác:**
- **Vùng chạm ≥48×48dp**, khoảng cách giữa hai vùng chạm ≥8dp.
- **Pressed**: state layer M3 (ripple), *được clip theo shape*, không đổi kích thước layout.
- **Focused**: `border.focus` 2dp + offset 2dp.
- **Disabled**: `disabledContainer` / `disabledContent`, semantics `disabled()`, **luôn kèm lý do** nếu là CTA nghiệp vụ.
- **Loading**: giữ nguyên kích thước, chặn nhấn lặp, `stateDescription = "Đang xử lý"`.
- Mọi component dựng tay từ `Row`/`Box` + `clickable` **phải** khai báo `role` (Button/Tab/Checkbox/RadioButton/Switch) và `selected`/`stateDescription` khi có trạng thái. Audit đếm được 45 `clickable` nhưng chỉ 1 có `role`.

---

## A. Navigation

### A1. Bottom Navigation: `LuxBottomNav`
| | |
|---|---|
| Mục đích | Chuyển giữa 4 khu vực chính |
| Khi dùng | Chỉ ở 4 màn tab gốc. Thứ tự và đích giữ nguyên `NavGraph.kt` |
| M3 base | `NavigationBar` + `NavigationBarItem` |
| Anatomy | Container · 4 × (chỉ báo pill · icon · nhãn) |
| Kích thước | Cao 80dp + inset. Icon 24dp. Pill 64×32 |
| Chữ | `labelSmall` (14sp) |
| Màu | Container `surface` + divider trên `outlineVariant` · Active: icon/nhãn `onPrimaryContainer`/`primary`, pill `primaryContainer`, **icon Filled** · Inactive: `onSurfaceVariant`, **icon Outlined** |
| States | Default · Selected · Pressed · Focused |
| A11y | `selected = true` qua NavigationBarItem. Nhãn luôn hiển thị (không `alwaysShowLabel = false`) |
| Anti-pattern | Thêm tab thứ 5 · ẩn nhãn · dùng màu làm tín hiệu duy nhất (chỉ báo hiện tại chỉ đạt 1.12:1) |

### A2. Top App Bar: `LuxTopBar`
Hai biến thể, thay cho 3 kiểu header hiện có.

| | **Tab root** | **Màn con** |
|---|---|---|
| Dùng ở | Việc hôm nay, Khảo sát, Bản đồ (overlay riêng, xem GIS), Cá nhân | Mọi màn con (Chi tiết lệnh, danh sách cột, Thông báo…) |
| M3 base | `TopAppBar` | `TopAppBar` |
| Anatomy | Tiêu đề (`headlineMedium`) · phụ đề tuỳ chọn (`bodySmall`, ví dụ ngày hôm nay) · action: **chuông thông báo** | Back · tiêu đề (`titleLarge`, tối đa 2 dòng) · phụ đề tuỳ chọn (`bodySmall`, ví dụ mã lệnh) · tối đa 2 action |
| Cao | 64dp + status inset (giãn khi 2 dòng) | 64dp + status inset |
| Màu | Container `surface` (Light) / `surface` (NF), chữ `onSurface`, **không gradient, không khối navy** | như trái |
| Cuộn | Có thể đổi sang `surfaceContainer` + divider khi nội dung cuộn dưới bar | như trái |
| States | Default · Scrolled | Default · Scrolled |
| A11y | Tiêu đề là `heading()` | Back có `contentDescription = "Quay lại"` |
| Quy tắc | Header **không đổi kiểu giữa các UiState** (Loading/Success/Empty/Error cùng một bar). Sửa lỗi F09 | Thông tin trạng thái (badge "Đã nộp"…) **không** nằm trong bar, đặt ở đầu nội dung |

### A3. Back navigation
- Nút Back: `IconButton` 48dp, icon `Icons.AutoMirrored.Filled.ArrowBack` 24dp (bỏ `Icons.Filled.ArrowBack` đã deprecated), màu `onSurface`.
- Luôn ở **góc trái trên** của `LuxTopBar` màn con. Không dùng chip back tự vẽ (F09 hiện có chip 40dp màu `#1E3D6B`).
- System back / gesture back và nút Back cho **cùng kết quả**. Form có thay đổi chưa lưu phải hỏi xác nhận (DS v2.0 5.2).

---

## B. Actions

Kích thước chung của mọi nút: cao **56dp** cho CTA chính đáy màn, **48dp** cho nút trong nội dung. Padding ngang 24dp. Icon 20dp, cách chữ 8dp. Shape `medium` (12dp). Chữ `labelLarge` (16sp).

| Component | M3 base | Container | Content | Viền | Dùng cho | Không dùng cho |
|---|---|---|---|---|---|---|
| **B1 Primary** `LuxPrimaryButton` | `Button` | `primary` | `onPrimary` | | **Một** hành động chính mỗi màn: Bắt đầu, Nộp báo cáo, Hoàn thành lệnh, Đăng nhập | Nhiều nút primary cạnh nhau |
| **B2 Secondary** `LuxSecondaryButton` | `OutlinedButton` | trong suốt | `primary` | `border.control` (`outline`) | Hành động thay thế: Kiểm tra lại, Chụp lại, Đường đi | Hành động phá huỷ |
| **B3 Text** `LuxTextButton` | `TextButton` | trong suốt | `primary` (L) / `primary` (NF) | | Hành động mức thấp: Xem thêm, Thu gọn, Đọc tất cả, Huỷ trong dialog | CTA chính |
| **B4 Icon** `LuxIconButton` | `IconButton` / `FilledTonalIconButton` | trong suốt hoặc `secondaryContainer` | `onSurface` | | Back, chuông, đóng, tải file, định vị | Icon không có `contentDescription` |
| **B5 Destructive** `LuxDestructiveButton` | `Button` hoặc `OutlinedButton` | Filled: `error` · Outlined: trong suốt | Filled: `onError` · Outlined: `error` | Outlined: `error` 1dp | Filled: xác nhận trong dialog (Đăng xuất, Gửi yêu cầu hủy) · Outlined: lối vào hành động phá huỷ (Yêu cầu hủy trên Bottom Action Bar) | Đặt filled destructive ngay cạnh primary |

**States (mọi nút):**
- **Default.**
- **Pressed:** state layer 12% `content`.
- **Focused:** ring.
- **Disabled:** `disabledContainer` / `disabledContent`. Outlined thì viền `outlineVariant`.
- **Loading:** spinner 18dp màu `content` thay icon. Chữ đổi theo ngữ cảnh ("Đang nộp…"), không đổi kích thước nút.

**A11y:**
- Chữ trên nút là nhãn. Icon trong nút `contentDescription = null`.
- Disabled CTA: `stateDescription` chứa **lý do khóa** (ví dụ "Bị khóa: còn 2 sự cố chưa có kết quả").

**Anti-pattern:**
- Nút toàn chữ hoa.
- Nút cao < 48dp.
- Nút chỉ đổi màu khi disabled mà không có lý do.
- Hai nút cùng cấp có màu khác nhau mà không có ý nghĩa.

### B6. Bottom Action Bar: `LuxBottomActionBar`
Đặc tả layout ở `SPACING_LAYOUT.md` mục 6.

| | |
|---|---|
| Anatomy | Divider · (progress segments) · (progress text) · (hint khóa + icon `Info`) · [Secondary/Destructive-outlined] + [Primary] |
| Màu | `surface`, divider `outlineVariant`. Hint khóa: `error` khi bị chặn, `warning` khi cảnh báo, `onSurfaceVariant` khi trung tính. Segment xong: `primary`, chưa xong: `outline` (hiện trạng hardcode `Navy700`, sai trong NF) |
| States | CTA enabled · CTA locked (có hint) · CTA loading · Đã nộp (CTA thay bằng trạng thái tĩnh + badge sync) |
| A11y | Hint là `liveRegion = Polite`. CTA bị khóa đọc kèm lý do |
| Quy tắc | Lý do khóa luôn **ngay trên** nút. Tối đa 2 nút. Không đặt 3 lớp thông tin (progress + text + hint) cùng lúc: hint khóa thắng |

---

## C. Information

### C1. Status Badge: `LuxStatusBadge`
| | |
|---|---|
| Mục đích | Hiển thị **một** trạng thái nghiệp vụ |
| Variant | `asset` · `woStatus` · `sync` · `iot`. **Mỗi variant đọc bộ token riêng** (`status.asset.*`, `status.wo.*`…), không dùng chéo (DS v2.0 6.3: "không dùng badge tài sản cho sync") |
| Anatomy | Icon 16dp · 4dp · nhãn |
| Kích thước | Cao tối thiểu **28dp** (đủ cho 14sp/20 + padding dọc 4dp). Padding ngang 8dp. Shape `extraSmall` |
| Chữ | `labelMedium` (14sp/600). **Không** dùng 13sp như hiện tại |
| Màu | `container` / `content` của token tương ứng, Light và NF |
| States | Tĩnh (không tương tác). Nếu badge mở ra hành động (ví dụ sync lỗi → thử lại), bọc trong vùng chạm 48dp riêng |
| A11y | Hợp nhất với ngữ cảnh: "Trạng thái: Đang thực hiện". Badge đếm động dùng câu đầy đủ ("3 thông báo chưa đọc") |
| Quy tắc | Luôn icon + chữ. Nhãn **1 dòng**, không cắt. Nếu không vừa thì xuống dòng **cả badge** |
| Anti-pattern | Badge chỉ có chấm màu · dùng `status.wo.inProgress` để tô loại lệnh · badge chồng badge |

### C2. Priority Badge: `LuxPriorityBadge`
Như C1 với token `status.priority.*`. Icon: 1/2/3 vạch hoặc `PriorityHigh` cho Khẩn (✅ đã duyệt, T-03). Chỉ hiện khi dữ liệu có priority thật. Hiện item lệnh chỉ có `priorityScore` số, không có enum. **Không suy ra mức ưu tiên từ điểm số ở client** khi chưa có quy tắc.

### C3. Task Kind Label: `LuxKindLabel`
Nhãn loại lệnh **trung tính** (`kind.container` / `kind.content`) + icon theo loại. Phân biệt bằng icon + chữ, không bằng màu (`COLOR_SYSTEM.md` 6.6).

### C4. KPI Card: `LuxKpi`
| | |
|---|---|
| Mục đích | Một con số tóm tắt + nhãn (Tổng nhiệm vụ, Quá hạn, Đang thực hiện; KPI bản đồ) |
| Anatomy | Giá trị (`displaySmall`, hoặc `titleSmall` khi dạng hàng) · nhãn (`bodySmall`, tối đa 2 dòng) · icon tuỳ chọn |
| Kích thước | Ô tối thiểu 104×72dp (3 ô / 360dp). Padding 12dp |
| Màu | Nền `surfaceContainer`, giá trị `onSurface`. Giá trị cảnh báo dùng `error`/`warning` **kèm icon**, không chỉ đổi màu số |
| States | Tĩnh · Clickable (nếu lọc theo KPI: role Button, `selected`) · Loading (skeleton) |
| Quy tắc | Số **căn trong ô có độ rộng cố định** (chữ số tỉ lệ, `TYPOGRAPHY.md` mục 1). Tối đa 3 KPI một hàng |

### C5. Information Card: `LuxCard`
| | |
|---|---|
| Mục đích | Nhóm thông tin liên quan (tóm tắt lệnh, thông tin nhanh của cột, tài khoản) |
| M3 base | `Card` / `OutlinedCard` (thay `Modifier.background + border` tự dựng, 0 lần dùng `Card` hiện nay) |
| Anatomy | Tiêu đề section tuỳ chọn (`titleMedium`) · nội dung · action tuỳ chọn (Text button) |
| Kích thước | Padding 16dp, shape `medium`, viền `border.decorative`. Level 0 |
| Màu | Light: `surface`. NF: `surfaceContainer` |
| States | Tĩnh · Clickable (toàn card, role Button, ripple clip theo shape) · Selected (`border.selected`) |
| Anti-pattern | Card trong card · card chỉ chứa 1 dòng chữ |

### C6. List Item: `LuxListItem`
| | |
|---|---|
| M3 base | `ListItem` hoặc Row tự dựng có `role` |
| Anatomy | Leading (icon 24dp trong ô 40dp `surfaceContainer`, hoặc thumbnail) · headline (`titleSmall`) · supporting (`bodyMedium`/`bodySmall`, tối đa 2 dòng) · trailing (badge / chevron / action) |
| Kích thước | 56 / 72 / 88dp theo số dòng (`SPACING_LAYOUT.md` 3). Lề 16dp |
| Biến thể | **Work order row**: loại · mã (`bodySmall`) / tên cụm (`titleSmall`) / hạn (icon + `bodySmall` màu `due.*`) + badge trạng thái. **Pole row**: mã cột / tuyến + badge tài sản + badge sync |
| States | Default · Pressed · Selected · Disabled · Có thay đổi local chưa đồng bộ (badge `status.sync.*`) |
| A11y | `mergeDescendants`. Toàn hàng là 1 node có `role = Button` + mô tả gộp |
| Quy tắc | Chevron chỉ khi mở ra màn khác. Ripple clip theo shape (sửa A6 audit) |

### C7. Banner: `LuxBanner` (bổ sung, thay `InlineBanner`)
| | |
|---|---|
| Mục đích | Thông điệp ở mức màn hình: offline/dữ liệu cũ, lỗi hành động, cảnh báo quá hạn, phản hồi của Kỹ sư bảo trì, yêu cầu hủy đang chờ |
| Tone | `info` · `warning` · `error` · `success` · `neutral` (dùng `*Container` / `on*Container`) |
| Anatomy | Icon 20dp · tiêu đề tuỳ chọn (`titleSmall`) · nội dung (`bodyMedium`) · action tuỳ chọn (Text button) |
| Thứ tự ưu tiên (khi nhiều thông điệp) | 1. **Chặn hành động** (quá hạn, form khóa) · 2. **Phản hồi Kỹ sư bảo trì** (trả lại, từ chối hủy) · 3. **Đang chờ** (hủy chờ duyệt, chưa đồng bộ) · 4. **Tham khảo** (ghi chú) |
| Quy tắc | **Chỉ hiển thị 1 banner nổi bật** (mức ưu tiên cao nhất). Phần còn lại gom vào card "Ghi chú & lịch sử". Banner offline luôn được phép hiển thị cùng banner khác (nó mô tả **dữ liệu**, không mô tả lệnh) |
| A11y | `liveRegion = Polite`. Banner lỗi chặn hành động dùng `Assertive` |

### C8. Empty State: `LuxEmptyState`
- **Anatomy:** icon 32dp trong ô 64dp `surfaceContainer` · tiêu đề (`titleMedium`) · mô tả (`bodyMedium`, `onSurfaceVariant`) · action tuỳ chọn (Secondary).
- **Quy tắc:** nói **vì sao trống** và **điều gì sẽ xuất hiện ở đây** ("Lệnh sửa chữa được giao sẽ hiện ở đây"). Phân biệt *trống thật* (`UiState.Empty`) với *bộ lọc không có kết quả*: cái sau có action "Bỏ lọc".
- **Màu:** không dùng màu trạng thái cho icon trống. Dùng `onSurfaceVariant` (hiện trạng tô màu badge task-kind).

### C9. Error State: `LuxErrorState`
- **Anatomy:** icon `CloudOff` (mạng) / `ErrorOutline` (khác) 32dp · tiêu đề (`titleMedium`) · mô tả 3 ý (DS v2.0 mục 9): chuyện gì xảy ra, dữ liệu có an toàn không, cần làm gì · **nút "Thử lại" bắt buộc** (Secondary) · action phụ tuỳ chọn ("Quay lại").
- **Quy tắc:** **không bao giờ** `action = {}` (F09, PoleDetail hiện tại). Thông điệp phụ phải khớp loại lỗi, không mặc định "Kiểm tra kết nối mạng".
- **A11y:** tiêu đề là `liveRegion = Assertive` khi xuất hiện sau hành động người dùng.

### C10. Loading State: `LuxSkeleton` + `LuxProgress`
| Thời gian chờ dự kiến | Hiển thị |
|---|---|
| < 300ms | Không hiển thị gì (tránh nháy) |
| Tải danh sách/màn | **Skeleton** có hình dạng giống nội dung thật (đã làm tốt ở Home/Hub/PoleList). Màu `surfaceContainerHighest`, shape giống phần tử thật. **Một** `LuxSkeleton` dùng chung, thay 3 bản `SkeletonBar` |
| Hành động trên nút | Spinner trong nút (B) |
| Tiến trình có số đo (upload) | `LinearProgressIndicator` xác định + chữ "3/5 · 12 MB" |

- Skeleton **không** nhấp nháy (shimmer) khi bật reduced motion. Hiện tại chưa dùng shimmer, giữ nguyên tĩnh.
- Vùng loading có `contentDescription = "Đang tải …"`.

### C11. Offline / Sync Indicator: `LuxSyncIndicator`
| | |
|---|---|
| Mục đích | Cho biết dữ liệu là bản lưu trên thiết bị hay đã đồng bộ, và còn bao nhiêu mục chờ |
| Biến thể | **Inline badge** (C1, variant `sync`) trên từng bản ghi · **Banner offline/stale** (C7 tone `info`): "Đang xem dữ liệu đã lưu · cập nhật lúc HH:mm" · **Tóm tắt hàng đợi** (Cá nhân): "Còn N mục chưa đồng bộ" |
| Quy tắc | Phân biệt tuyệt đối "**Đã lưu trên thiết bị**" (local, snackbar `success`) và "**Đã đồng bộ**" (`status.sync.done`). Không dùng spinner vô hạn cho đồng bộ nền |
| Ngoài phạm vi | Màn F13 Hàng đợi đồng bộ chưa tồn tại. Indicator **không** điều hướng tới đâu cho tới khi F13 được làm (không thêm navigation) |

---

## D. Input

### D1. Text Field: `LuxTextField`
| | |
|---|---|
| M3 base | `OutlinedTextField` |
| Anatomy | **Nhãn luôn hiển thị phía trên** (`labelMedium`, `onSurface`) · field · supporting text (hint hoặc lỗi, `bodySmall`; lỗi dùng `bodyMedium` + icon) · bộ đếm ký tự tuỳ chọn |
| Kích thước | Cao tối thiểu 56dp. Text area tối thiểu 3 dòng. Shape `medium` |
| Màu | Viền `outline` (≥3:1), focus `primary` 2dp, lỗi `error` 2dp. Nền `surface` |
| States | Default · Focused · Filled · Error · Disabled · Read-only (form bị khóa do quá hạn: hiện giá trị, không viền, có banner lý do) |
| A11y | Nhãn gắn với field. Lỗi được announce. Placeholder **không** thay nhãn |
| Quy tắc | Yêu cầu độ dài tối thiểu hiển thị **trước** khi người dùng nhập ("Tối thiểu 10 ký tự"), không chỉ báo lỗi sau |

### D2. Search Field: `LuxSearchField`
- Dùng trên bản đồ và danh sách.
- Kiểu M3 `SearchBar`/`DockedSearchBar`, hoặc `OutlinedTextField` có icon `Search` + nút xoá `Close` (48dp).
- Cao 56dp. Trên bản đồ: chrome Level 2 (`GIS_MAP_GUIDELINES.md`).
- Placeholder mô tả phạm vi ("Tìm mã cột hoặc tên tuyến").
- Kết quả rỗng có thông điệp riêng.
- `imeAction = Search`.

### D3. Dropdown: `LuxDropdown`
- `ExposedDropdownMenuBox` + `OutlinedTextField` read-only.
- Hiện **chưa có use-case** dropdown trên mobile. Các lựa chọn hiện nay (lý do hủy, kết quả sự cố) dùng **danh sách radio** hoặc **segmented** vì luôn ≤5 lựa chọn và cần thấy hết.
- Quy tắc: ≤5 lựa chọn thì dùng radio/segmented. >5 thì dùng dropdown hoặc sheet có tìm kiếm.

### D4. Date Picker: `LuxDatePicker`
- M3 `DatePickerDialog`.
- **Chưa có use-case**: ngày lịch/hạn do Kỹ sư bảo trì đặt trên Web GIS. Mobile chỉ hiển thị.
- Spec dự phòng: định dạng `dd/MM/yyyy`, tuần bắt đầu thứ Hai, nhãn tiếng Việt, luôn cho phép nhập tay.
- **Không thêm** use-case mới.

### D5. Filter Chip: `LuxFilterChip`
| | |
|---|---|
| M3 base | `FilterChip` |
| Kích thước | Cao 32dp hình, **vùng chạm 48dp** (`minimumInteractiveComponentSize`). Shape `small` |
| Chữ | `labelMedium` + số đếm tuỳ chọn |
| Màu | Chưa chọn: viền `outline`, chữ `onSurfaceVariant`. Đã chọn: `secondaryContainer` / `onSecondaryContainer` + **icon check** (không chỉ đổi màu). Hiện trạng NotificationList hardcode `Navy700`, sai trong NF |
| Dùng cho | Lọc nhiều lựa chọn độc lập (trạng thái cột trên bản đồ, Tất cả/Chưa đọc) |
| A11y | `selected` qua FilterChip. Nhãn có số đếm đọc thành "Chưa đọc, 3" |

### D6. Segmented Button: `LuxSegmented`
| | |
|---|---|
| M3 base | `SingleChoiceSegmentedButtonRow` + `SegmentedButton` |
| Kích thước | Cao 48dp, tối đa 4 đoạn ở 360dp. Shape `small` theo đoạn |
| Chữ | `labelLarge` (16sp), hoặc `labelMedium` khi 3–4 đoạn |
| Màu | Đã chọn: `secondaryContainer` + icon check · Chưa chọn: `surface`, viền `outline` |
| Dùng cho | Chọn **một** trong 2–4: tab Kiểm tra/Sửa chữa ở Việc hôm nay (thay segmented tự dựng 40dp), kết quả sự cố (Có sự cố/Không có/Chưa rõ), review AI (Đèn sáng/Đèn tắt) |
| A11y | Role `RadioButton` / `Tab` + `selected`. Thay `OutcomeSegment` tự dựng |
| Anti-pattern | Segmented cho >4 lựa chọn · chữ bị cắt |

---

## E. Feedback

### E1. Snackbar: `LuxSnackbar`
- **Màu:** `inverseSurface` / `inverseOnSurface`, action `inversePrimary`. Shape `small`. Chữ `bodyMedium`.
- **Dùng cho:** phản hồi ngắn **không chặn** ("Đã lưu trên thiết bị — sẽ đồng bộ khi có mạng", "Đã gửi yêu cầu hủy · chờ Kỹ sư bảo trì duyệt").
- **Vị trí:** trên Bottom Navigation/Bottom Action Bar.
- **Thời lượng:** ≥4s. Có action thì ≥10s, hoặc `Indefinite` cho lỗi cần thử lại.
- **Hợp nhất:** `TopSlideToast` (toast trượt từ trên) hiện là component riêng. vNext đề xuất **thay bằng Snackbar M3** để có `liveRegion` và vị trí nhất quán (T-08: xác nhận có giữ toast trên đỉnh hay không).

### E2. Dialog: `LuxDialog`
- **M3 base:** `AlertDialog`. Shape `large`. Nền `surfaceContainerHigh` (NF) / `surface` (L). Scrim `scrim`.
- **Anatomy:** icon tuỳ chọn · tiêu đề (`headlineSmall`) · nội dung (`bodyLarge`) · tối đa 2 nút (Text + Text, hoặc Text + Destructive filled).
- **Dùng cho:** quyết định ngắn, cần chặn. Nội dung dài hoặc có form thì dùng Bottom Sheet.

### E3. Confirmation Dialog: `LuxConfirmDialog`
- Biến thể của E2 cho hành động **phá huỷ/không hoàn tác**: Đăng xuất khi còn dữ liệu chưa đồng bộ, Chụp lại (thay ảnh), Hoàn tất khảo sát tuyến.
- Tiêu đề là câu hỏi cụ thể ("Đăng xuất khỏi thiết bị này?"). Nội dung nêu **hậu quả**.
- Nút xác nhận nói đúng hành động ("Đăng xuất", **không** "OK"). Nút huỷ bên trái.
- Hậu quả nặng (mất dữ liệu chưa đồng bộ) dùng `LuxDestructiveButton` filled.

### E4. Bottom Sheet: `LuxBottomSheet`
- **M3 base:** `ModalBottomSheet`. Shape `extraLarge` (2 góc trên 24dp). Nền `surface` (L) / `surfaceContainer` (NF). Drag handle mặc định.
- **Anatomy:** tiêu đề (`titleLarge`) + **nút đóng `Close` 48dp** (quy tắc Modal Escape) · nội dung · footer action (nếu có, dùng Bottom Action Bar không pinned).
- **Dùng cho:** chọn lý do hủy, chọn phương thức chụp, xác nhận nộp kèm tóm tắt, quick view trên bản đồ, lớp bản đồ.
- **Quy tắc:** chiều cao vừa nội dung (không ép full). Nội dung dài thì cuộn **bên trong** sheet. Nút xác nhận cuối nội dung cuộn phải luôn tới được. Bài học từ test: dùng `performScrollTo()`.
