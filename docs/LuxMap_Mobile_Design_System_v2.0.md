# LUXMAP MOBILE — DESIGN SYSTEM v2.0

**Trạng thái:** Bản chính thức, đồng bộ với đặc tả phát triển Mobile v2.2 và Design System Web  
**Phạm vi:** Ứng dụng Android native dành cho Tổ khảo sát/sửa chữa  
**Công nghệ tham chiếu:** Kotlin + Jetpack Compose  
**Font chính:** Be Vietnam Pro; fallback: Inter, sans-serif

Tài liệu này quy định cách thể hiện giao diện, token, component và trạng thái của LuxMap Mobile. Logic nghiệp vụ, API, Room, WorkManager, CameraX/Camera2Interop và MapLibre được mô tả trong `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`. Khi có xung đột, quy tắc nghiệp vụ trong đặc tả phát triển được ưu tiên; tài liệu này quyết định cách thể hiện trên giao diện.

---

## 1. Nguyên tắc nền tảng

1. **Field-first:** đọc được ngoài trời, dùng được ban đêm, thao tác được bằng một tay và khi đeo găng.
2. **Offline-first:** thao tác hiện trường ghi local trước; UI không chờ phản hồi mạng.
3. **Trạng thái rõ ràng:** luôn dùng chữ + màu + icon; không dùng màu đơn độc.
4. **Không thông báo sai:** “Đã lưu trên thiết bị” khác với “Đã đồng bộ lên hệ thống”.
5. **Chặn có giải thích:** CTA bị vô hiệu hóa phải hiển thị điều kiện chưa đạt và cách khắc phục.
6. **An toàn dữ liệu:** không tự xóa dữ liệu chưa đồng bộ; cảnh báo trước đăng xuất hoặc dọn dữ liệu.
7. **Khả năng tiếp cận:** hỗ trợ font scaling, screen reader, reduced motion và vùng chạm tối thiểu 48×48dp.

### 1.1 Chế độ hiển thị theo ngữ cảnh

| Màn hình/ngữ cảnh | Chế độ mặc định | Ghi chú |
|---|---|---|
| F03 Lập kế hoạch khảo sát đêm | Dark | Giảm chói trước khi bắt đầu khảo sát |
| F04 Capture Mode | Dark bắt buộc | Ẩn Bottom Navigation; hạn chế mảng sáng |
| F09 Chi tiết lệnh | Theo hệ thống | Chuyển Dark khi vào chế độ điều hướng ban đêm |
| F10 Nghiệm thu | Theo hệ thống | Không ép Dark; có thể dùng ban ngày hoặc ban đêm |
| Các màn còn lại | Theo hệ thống, mặc định Light | Bảo đảm đủ token cho cả Light/Dark |

Không gắn Dark Mode cứng với F08. Quy tắc gốc là **ngữ cảnh sử dụng**, mã màn hình chỉ là ánh xạ hiện tại.

---

## 2. Hệ thống Design Tokens

Token được tổ chức ba lớp:

- **Primitive:** giá trị màu gốc như `navy-700`, `gray-900`.
- **Semantic:** ý nghĩa sử dụng như `color.text.primary`.
- **Component:** giá trị theo component như `button.primary.container`.

### 2.1 Primitive colors

| Token | Hex | Vai trò |
|---|---:|---|
| `navy-700` | `#1F3864` | Thương hiệu, CTA Light Mode |
| `blue-500` | `#3E86C9` | Link, focus, thông tin |
| `green-400` | `#5FC4B0` | Đèn bình thường, điểm nhấn Dark Mode |
| `amber-500` | `#E9A23B` | Cảnh báo trung bình |
| `rose-600` | `#D64545` | Hỏng/tắt, SLA khẩn |
| `success-600` | `#059669` | Hoàn tất thành công |
| `danger-600` | `#DC2626` | Hành động nguy hiểm |
| `gray-25` | `#F8FAFC` | Nền Light |
| `gray-50` | `#F1F5F9` | Nền phụ Light |
| `gray-200` | `#E2E8F0` | Viền Light |
| `gray-500` | `#64748B` | Chữ phụ Light |
| `gray-700` | `#334155` | Chữ phụ đậm |
| `gray-900` | `#0F172A` | Chữ chính Light |
| `dark-950` | `#0D0D0D` | Nền Dark |
| `dark-900` | `#121212` | Nền Dark thay thế |
| `dark-800` | `#1A1A1A` | Card Dark |
| `dark-700` | `#2A2A2A` | Viền Dark |
| `dark-text` | `#F5F5F5` | Chữ chính Dark |
| `dark-muted` | `#A0A0A0` | Chữ phụ Dark |

### 2.2 Semantic colors theo theme

| Semantic token | Light | Dark |
|---|---:|---:|
| `color.background` | `#F8FAFC` | `#0D0D0D` |
| `color.surface` | `#FFFFFF` | `#1A1A1A` |
| `color.surface.subtle` | `#F1F5F9` | `#121212` |
| `color.text.primary` | `#0F172A` | `#F5F5F5` |
| `color.text.secondary` | `#64748B` | `#A0A0A0` |
| `color.border` | `#E2E8F0` | `#2A2A2A` |
| `color.focus` | `#3E86C9` | `#5FC4B0` |
| `color.navigation.active` | `#1F3864` | `#5FC4B0` |
| `color.navigation.inactive` | `#64748B` | `#A0A0A0` |
| `color.overlay.scrim` | `rgba(15,23,42,.56)` | `rgba(0,0,0,.68)` |
| `color.disabled.container` | `#94A3B8` | `#475569` |
| `color.disabled.content` | `#F1F5F9` | `#CBD5E1` |

### 2.3 Trạng thái tài sản

| Trạng thái | Nền Light | Chữ Light | Nền Dark | Chữ Dark | Icon/hình dạng bản đồ |
|---|---:|---:|---:|---:|---|
| Bình thường | `#D1FAE5` | `#065F46` | `#123D34` | `#8CE3D1` | Hình tròn + check |
| Đèn mờ | `#FEF3C7` | `#92400E` | `#4A3310` | `#F7C66D` | Tam giác + cảnh báo |
| Hỏng/Tắt | `#FEE2E2` | `#991B1B` | `#4A1717` | `#FF9A9A` | Hình thoi + dấu X |
| Chưa xác định | `#EFEFEF` | `#555555` | `#303030` | `#D0D0D0` | Vòng nét đứt + dấu hỏi |

Badge luôn có chấm/icon và nhãn chữ. Trong Camera Mode, badge nền sáng không phủ diện tích lớn; dùng biến thể Dark và tự giảm độ sáng.

### 2.4 Trạng thái đồng bộ

| Enum | Nhãn UI | Màu nền | Màu chữ | Hành động |
|---|---|---:|---:|---|
| `queued` + offline | Chờ mạng | `#EFEFEF` | `#555555` | Không cần thao tác |
| `queued` + online | Chờ đồng bộ | `#E8EEF7` | `#1F3864` | Đồng bộ ngay |
| `syncing` | Đang đồng bộ | `#FFF3DC` | `#8A5A00` | Tạm dừng nếu được hỗ trợ |
| `failed` | Đồng bộ lỗi | `#FBE4E4` | `#9B2C2C` | Thử lại/Xem lỗi |
| `conflict` | Xung đột | `#FDE8D0` | `#8A3B00` | Xem chi tiết |
| `done` | Đã đồng bộ | `#E3F6F1` | `#1E6B5C` | Không cần thao tác |

Không gộp `failed` và `conflict`. “Đã lưu trên thiết bị” là feedback lưu local, không phải `done`.

### 2.5 Trạng thái ưu tiên Work Order

| Priority | Nhãn | Màu |
|---|---|---:|
| `low` | Thấp | `#64748B` |
| `normal` | Bình thường | `#3E86C9` |
| `high` | Cao | `#E9A23B` |
| `urgent` | Khẩn | `#D64545` |

---

## 3. Typography

| Cấp | Cỡ/Line-height | Weight | Dùng cho |
|---|---|---|---|
| Display Number | 28sp/34sp | 700 | Lux, số lượng lớn, quãng đường |
| H1 | 20sp/26sp | 700 | Tiêu đề màn hình |
| H2 | 17sp/24sp | 600 | Tiêu đề section/card |
| Body | 16sp/24sp | 400 | Nội dung chính |
| Body Strong | 16sp/24sp | 600 | Label quan trọng, nút |
| Caption | 14sp/20sp | 500 | Timestamp, metadata phụ |

Quy tắc:

- Nội dung cần đọc hoặc hành động không nhỏ hơn 16sp.
- Caption 14sp chỉ dành cho metadata phụ; không dùng cho cảnh báo hoặc hướng dẫn.
- Dữ liệu số dùng tabular figures nếu font hỗ trợ.
- Hỗ trợ font scaling ít nhất 200%; không cắt chữ tiếng Việt.
- Tiêu đề card tối đa 2 dòng; metadata dài phải wrap, không dùng ellipsis cho mã cột hoặc lỗi đồng bộ.
- Dùng sentence case; hạn chế ALL CAPS.

---

## 4. Spacing, hình học và layout

### 4.1 Spacing scale

`4, 8, 12, 16, 24, 32, 40dp`

| Vai trò | Giá trị |
|---|---:|
| Lề ngang điện thoại | 16dp |
| Khoảng section | 24dp |
| Khoảng card | 12–16dp |
| Label → input | 8dp |
| Nội dung card | 16dp |
| Touch target | Tối thiểu 48×48dp |

### 4.2 Radius và elevation

| Token | Giá trị |
|---|---:|
| `radius.small` | 8dp |
| `radius.medium` | 12dp |
| `radius.pill` | 999dp |
| `elevation.card` | 1dp hoặc viền 1dp |
| `elevation.sheet` | 8dp |

Ưu tiên viền thay vì shadow đậm trong Dark Mode.

### 4.3 Safe area và responsive

- Top Bar và Bottom Navigation phải tôn trọng system insets.
- CTA cố định đáy phải nằm trên gesture area và không che nội dung cuộn.
- Khi bàn phím mở, trường đang nhập và thông báo lỗi phải nhìn thấy.
- Landscape: ưu tiên camera/bản đồ toàn màn hình; form dùng bố cục tối đa hai cột trên tablet.
- Tablet: nội dung form tối đa 720dp; bản đồ có thể dùng master-detail.

---

## 5. Navigation shell

### 5.1 Top Bar

- Cao tối thiểu 56dp, cộng system inset.
- Trái: Back hoặc tiêu đề màn hình.
- Phải: Sync Indicator và Notification Indicator; mỗi icon có vùng chạm 48dp.
- Badge số hiển thị tối đa `99+`.
- Screen reader: “Có 3 mục chờ đồng bộ”, không đọc số rời rạc.
- F04 Capture Mode và chế độ điều hướng tập trung dùng overlay tối giản thay Top Bar đầy đủ.

### 5.2 Bottom Navigation

4 mục: `Việc hôm nay` · `Khảo sát` · `Bản đồ` · `Cá nhân`.

- F08 mở từ F02 qua “Xem tất cả công việc”.
- Ẩn Bottom Navigation ở F04, chế độ điều hướng F09 và các bước xác nhận toàn màn hình.
- Rời form F10/F11/F07 phải tự lưu nháp hoặc hỏi xác nhận nếu có thay đổi chưa lưu.

---

## 6. Master Components

### 6.1 Button

Variants: Primary, Secondary, Success, Danger, Text, Icon.  
States: Default, Pressed, Focused, Loading, Disabled.

- Cao tối thiểu 48dp; Primary CTA thường full-width.
- Loading không thay đổi kích thước nút và chặn nhấn lặp.
- Disabled phải đi kèm giải thích gần nút nếu điều kiện chưa đạt.
- Sau hành động offline, feedback dùng “Đã lưu trên thiết bị — sẽ đồng bộ khi có mạng”.

### 6.2 Form controls

Bao gồm Text Field, Numeric Field, Text Area, Search, Selector, Checkbox, Radio, Switch.

- Label luôn hiển thị; placeholder không thay label.
- Numeric lux field dùng bàn phím số, cho số thập phân, hiển thị đơn vị `lux` bên ngoài giá trị.
- Error gồm icon + nội dung + cách sửa.
- Checkbox checklist có vùng chạm 48dp; điều kiện kỹ thuật tự kiểm tra không dùng checkbox thủ công.

### 6.3 Status Badge

Pill gồm icon/chấm + nhãn. Variants: asset, priority, work-order status, sync. Không dùng badge tài sản cho sync.

### 6.4 Work Order Card

Anatomy:

1. Mã lệnh + priority.
2. Loại sự cố.
3. Địa chỉ/mã cột.
4. SLA và khoảng cách.
5. Sync/local-change indicator nếu có.

Overdue luôn có chữ “Quá hạn”, không chỉ viền đỏ. Card có vùng chạm toàn phần ≥48dp.

### 6.5 Offline Banner

Ba variants:

- Offline: “Đang ngoại tuyến — thay đổi sẽ lưu trên thiết bị”.
- Stale data: “Dữ liệu cập nhật lần cuối lúc …”.
- Sync blocked: “Đang chờ Wi-Fi để tải … GB”.

Banner không che Top Bar/CTA và có screen-reader announcement.

### 6.6 Sync Indicator và Sync Queue Item

Sync Indicator hiển thị tổng mục chờ. Sync Queue Item gồm loại dữ liệu, dung lượng, tiến độ, trạng thái, lỗi và CTA phù hợp. Progress hiển thị theo byte và số mục; không chỉ spinner vô hạn.

### 6.7 Camera Readiness Panel

Các kiểm tra tự động:

- Quyền camera.
- Hỗ trợ manual exposure.
- Exposure đã khóa.
- GPS và heading hợp lệ.
- Dung lượng trống.
- Pin đủ theo ngưỡng dự án.

Mỗi hàng có trạng thái `Đang kiểm tra/Đạt/Không đạt` và hướng dẫn sửa. Các xác nhận vật lý như “thiết bị đã gắn chắc” mới dùng checkbox.

### 6.8 Camera Overlay

- Preview toàn màn hình, nền tối.
- Shutter ≥64dp, Pause/Resume và End có vùng chạm ≥48dp.
- Hiển thị exposure, GPS accuracy, heading, frame count, distance và storage remaining.
- Cảnh báo GPS bằng chữ + icon + rung; không chỉ đổi màu.
- Khi lỗi metadata, frame vẫn lưu local nhưng đánh dấu “Không hợp lệ để gắn cột”.
- Không hiển thị Bottom Navigation.

### 6.9 GPS Accuracy Indicator

| Trạng thái | Nhãn mẫu |
|---|---|
| Đạt | GPS tốt · ±5 m |
| Trung bình | GPS yếu · ±18 m |
| Không đạt | Không có vị trí hợp lệ |

Ngưỡng cụ thể do đặc tả kỹ thuật cấu hình. Không chỉ hiển thị “còn/mất GPS”.

### 6.10 Map components

Bao gồm Asset Marker, Work Order Marker, User Location, Heading Cone, Route, Cluster, Layer Control, Legend và Offline Region Status.

- Marker phân biệt bằng màu + hình dạng/icon.
- Selected marker có viền/scale rõ ràng.
- Cluster hiển thị số lượng và mức nghiêm trọng cao nhất.
- Bản đồ offline phải hiển thị phạm vi đã tải; không hứa turn-by-turn nếu chưa có routing offline.

### 6.11 Feedback components

- Snackbar: lưu local, retry, thao tác thành công.
- Inline error: lỗi trường/điều kiện.
- Banner: mất mạng, dữ liệu cũ, storage thấp.
- Dialog: hành động phá hủy hoặc rời màn có dữ liệu chưa lưu.
- Bottom Sheet: selector, marker preview, conflict detail.

### 6.12 Data states

Mọi vùng dữ liệu bất đồng bộ phải có:

1. Loading/skeleton.
2. Data.
3. Empty kèm hướng dẫn.
4. Error kèm Retry.
5. Offline cached/stale khi phù hợp.

---

## 7. Accessibility, motion và haptic

- Tương phản chữ thường ≥4.5:1; chữ lớn và thành phần UI ≥3:1.
- Icon-only button phải có content description.
- Thứ tự focus theo thứ tự thị giác và nghiệp vụ.
- Hỗ trợ TalkBack và font scaling 200%.
- Không dùng placeholder thay label.
- Tôn trọng Reduce Motion; animation chức năng 150–250ms.
- Rung nhẹ khi chụp/lưu thành công; rung cảnh báo khác biệt khi GPS/exposure không hợp lệ.
- Âm thanh chỉ là kênh bổ trợ và có thể tắt.

---

## 8. Bảng truy vết màn hình → component → trạng thái

| Màn hình | Component chính | Trạng thái đặc biệt |
|---|---|---|
| F01 Đăng nhập | Form, Primary Button, Prefetch Progress | Sai thông tin, offline lần đầu, phiên offline hợp lệ |
| F02 Việc hôm nay | Metric Card, Work Order Card, Offline Banner | Cached/stale, empty, overdue |
| F03 Lập kế hoạch | Map/List Selector, Camera Readiness Panel | Permission, exposure, GPS, storage |
| F04 Capture Mode | Camera Overlay, GPS Indicator | Paused, GPS weak/lost, metadata invalid, storage low |
| F05 Kiểm tra độ phủ | Map Coverage, Photo Grid, Quality Badge | Thiếu ảnh, ảnh lỗi, coverage thấp |
| F06 Nộp khảo sát | Upload Summary, Progress, Network Constraint | Waiting Wi-Fi, paused, resumed, failed |
| F07 Nhập lux | Pole Selector, Numeric Field, Evidence Photo | Pole required, draft, queued |
| F08 Danh sách việc | Filter Chips, Work Order Card | Cached/stale, filtered empty |
| F09 Chi tiết/điều hướng | Asset Card, Map, Navigation CTA | Offline map unavailable, external map limitation |
| F10 Nghiệm thu | Status Selector, Evidence Capture, Notes | Draft, missing after-photo, conflict |
| F11 Báo cáo sự cố | Location Picker, Fault Selector, Camera | GPS unavailable, manual pin, queued |
| F12 Bản đồ | Map Layers, Markers, Legend | Offline region, tile unavailable, clustering |
| F13 Hàng đợi | Sync Queue Item, Progress, Conflict Sheet | Queued, waiting network, syncing, failed, conflict |
| F14 Thông báo | Notification List | Cached, unread, deep-link unavailable |
| F15 Cá nhân | Settings, Cache Management, Logout Dialog | Pending sync, storage usage, destructive confirmation |

---

## 9. Nội dung và thuật ngữ

- Dùng “Đã lưu trên thiết bị” khi mới ghi local.
- Chỉ dùng “Đã đồng bộ” sau khi server xác nhận.
- Dùng “Chờ Wi-Fi” thay cho lỗi mạng khi chính sách chỉ cho upload qua Wi-Fi.
- Lỗi phải trả lời ba câu hỏi: chuyện gì xảy ra, dữ liệu có an toàn không, người dùng cần làm gì.
- Không dùng thuật ngữ kỹ thuật như payload, backoff, conflict version trong UI người dùng.

---

## 10. Checklist bàn giao

- [ ] Kiểm thử chế độ máy bay cho F04, F07, F10 và F11.
- [ ] Kiểm thử camera/exposure trên danh sách thiết bị hỗ trợ thực tế.
- [ ] Kiểm thử ngoài trời nắng mạnh và trong đêm.
- [ ] Kiểm thử bằng một tay và khi đeo găng.
- [ ] Kiểm thử TalkBack và font scaling 200%.
- [ ] Kiểm thử permission camera/location bị từ chối.
- [ ] Kiểm thử GPS yếu, heading thiếu và đồng hồ thiết bị sai.
- [ ] Kiểm thử storage thấp/hết dung lượng giữa phiên.
- [ ] Kiểm thử upload bị ngắt, resume và app/thiết bị khởi động lại.
- [ ] Kiểm thử hàng đợi `failed` và `conflict` riêng biệt.
- [ ] Kiểm thử dữ liệu cũ/cached và thời điểm tải cuối.
- [ ] Kiểm thử đăng xuất khi còn dữ liệu chưa đồng bộ.
- [ ] Kiểm thử kích thước màn hình nhỏ, landscape và tablet.
- [ ] Kiểm tra toàn bộ vùng chạm ≥48×48dp và tương phản đạt chuẩn.
- [ ] Kiểm tra trạng thái luôn có màu + chữ + icon/hình dạng khi cần.

---

## 11. Quy tắc bàn giao Figma/Compose

- Figma Variables dùng cùng tên semantic token trong tài liệu.
- Component Set phải có variants và states được liệt kê ở mục 6.
- Tên component theo mẫu `Mobile/{Category}/{Component}`.
- Compose theme ánh xạ Semantic Tokens, không dùng Hex trực tiếp trong màn hình.
- Mỗi màn hình phải có bản Light/Dark khi áp dụng và đầy đủ data states.
- Prototype F04/F09 phải thể hiện chế độ tập trung không có Bottom Navigation.

