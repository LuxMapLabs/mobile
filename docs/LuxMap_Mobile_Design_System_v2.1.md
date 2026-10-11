# LuxMap Mobile — Design System v2.1 (đề xuất, đã duyệt token migration 2026-10-11)

Bổ sung cho `docs/LuxMap_Mobile_Design_System_v2.0.md`. Mọi token v2.0 giữ nguyên tên và giá trị, trừ mục 3 (Disabled). Phần token thương hiệu/semantic đã được chủ dự án duyệt chuyển sang v2.1 (2026-10-11, roadmap `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` quyết định #1) — xem `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2 cho giá trị primitive đầy đủ đã lên code trong `core/theme/Color.kt`/`Theme.kt`.

## 1. Token bổ sung

| Token | Light | Dark | Dùng cho |
|---|---:|---:|---|
| `color.info.container` | `#E8EEF7` | `#1C2A44` | Badge "Đã lên kế hoạch", nav active pill, "Đã lưu trên thiết bị" |
| `color.info.content` | `#1F3864` | `#C9D6EE` | Chữ trên info.container |
| `color.onBrand.secondary` | `#C9D6EE` | — | Chữ phụ trên nền navy-700 |
| `color.navigation.activeContainer` | `#E8EEF7` | `rgba(95,196,176,.16)` | Nền pill tab đang chọn |
| `radius.large` | 16dp | 16dp | Bottom sheet, dialog, khối "Tiếp theo" |

## 2. Typography bổ sung

| Cấp | Cỡ/Line-height | Weight | Dùng cho |
|---|---|---|---|
| Large Title | 26sp/32sp | 700 | Tiêu đề màn gốc của 4 tab và F01 |
| Display Camera | 44sp/48sp | 700 | Đồng hồ quay F04 (đọc lướt khi đang lái) |
| Section Label | 14sp/20sp | 600 | Tiêu đề section, viết sentence case (thay ALL CAPS + letter-spacing) |

H1 20/26 giữ nguyên cho Top Bar màn con. Badge dùng 14sp/600, pill `radius.pill`.

> **Lưu ý triển khai (2026-10-11):** Phase 0 implement đã thêm bộ typography này vào `core/theme/Type.kt` dưới dạng `TextStyle` **bổ sung** (`NumericTextStyle`, `DisplayTextStyle`, ...), không ghi đè lên object `Typography` hiện có — xem rationale trong `docs/superpowers/plans/2026-10-11-luxmap-redesign-phase0-tokens.md`.

## 3. Disabled (thay giá trị v2.0)

| Token | v2.0 | v2.1 | Lý do |
|---|---:|---:|---|
| `color.disabled.container` (Light) | `#94A3B8` | `#E2E8F0` | Cặp cũ `#F1F5F9` trên `#94A3B8` chỉ ~2,4:1 |
| `color.disabled.content` (Light) | `#F1F5F9` | `#475569` | Đạt ~6:1, vẫn khác rõ nút active navy |
| `color.disabled.container` (Dark) | `#475569` | `#2A2A2A` | Hạn chế mảng sáng ban đêm |
| `color.disabled.content` (Dark) | `#CBD5E1` | `#A0A0A0` | |

Nút disabled luôn kèm dòng giải thích phía trên (quy tắc 6.1 giữ nguyên).

> **Lưu ý triển khai (2026-10-11):** đây là bảng được chọn làm mặc định khi `LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2.1 cho một cặp giá trị `Disabled`/`DisabledOn` khác (`#E4E2DB`/`#8A8D96`) — xung đột giữa 2 tài liệu, Phase 0 ưu tiên bảng này vì có lập luận tương phản rõ. Cả 2 cặp hiện đều **chưa được dùng ở đâu trong code** (4 token `DisabledContainer`/`DisabledContent`/`DisabledContainerDark`/`DisabledContentDark` trong `Color.kt` là token đã khai báo nhưng chưa có call site) — quyết định chọn cặp nào thực ra chưa ảnh hưởng hành vi hiển thị, chỉ có ý nghĩa khi Phase 1 nối `PrimaryButton`/`StatusBadge` disabled state vào.

## 4. Component bổ sung

- **GPS Hold Indicator (F04):** 3 trạng thái — "GPS yếu · ±14 m · cần ≤ 8 m ổn định 3 giây" / "GPS ±6 m · giữ ổn định thêm 2 giây" / "GPS tốt · ±3 m · sẵn sàng quay". Ngưỡng đọc từ `GpsAccuracyGate`, không hard-code trong UI.
- **Sensor Device Row:** tên thiết bị Bluetooth + trạng thái ghép đôi ("Đã ghép đôi"). Không hiển thị RSSI (kết nối SPP, không phải BLE).
- **Upload File Row (F06):** loại tệp, dung lượng hoặc số đoạn, badge sync. Thứ tự theo luồng server: clips → gps_track → lux_log → capture_config → submit.
- **Video Review Player (F05):** khung 16:9 tối, nút Phát 64dp, thanh thời gian, số đoạn hiện tại.
- **Confirm Dialog (destructive):** nút chính `danger-600`, nút phụ dạng Text navy-700. Dùng cho xoá phiên F05 và đăng xuất khi còn dữ liệu chưa đồng bộ.

## 5. Quy tắc bổ sung

- Lề ngang màn hình 16dp (v2.0 mục 4.1) áp dụng cho cả màn Dark.
- Màn Dark (Hub Khảo sát, F03, F04): CTA chính dùng `green-400` nền + chữ `dark-950`.
- Hero F01 dùng `navy-700`; đèn đường minh hoạ dùng ánh sáng ấm `#FFF3DC`, không thêm màu thương hiệu mới.
- Thẻ việc F02 chỉ hiển thị field có trong `GET /work-orders` (mã lệnh, trạng thái, hạn, điểm ưu tiên). Anatomy 6.4 đầy đủ áp dụng khi API bổ sung field.

> **Xung đột chưa giải quyết, để Phase 11 (F01) quyết định:** mục này nói Hero F01 dùng `navy-700`, nhưng `LuxMap_Mobile_Redesign_Spec_v0.1.md` mục "F01 Đăng nhập" lại mô tả hero là `inverseSurface` (Ink `#15171C`). Xem roadmap `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` mục "Hai mâu thuẫn cross-cutting" — không tự chọn một bên khi code F01.
