# Shape, Border & Elevation: LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ duyệt.** Nguyên tắc: **phẳng, phân tầng bằng màu bề mặt và viền, không bằng bóng đổ hay gradient** (quyết định #5).

## 1. Shape scale

Ánh xạ thẳng vào `Shapes(...)` của Material 3. Hiện trạng **chưa khai báo `Shapes`**, nên component M3 đang dùng shape mặc định, lệch với các radius tự định nghĩa.

| Slot M3 | Radius | Dùng cho |
|---|---:|---|
| `extraSmall` | 6dp | Status badge, priority badge, tooltip |
| `small` | 8dp | Filter chip, snackbar, segmented button (mỗi đoạn), thumbnail ảnh nhỏ |
| `medium` | 12dp | **Card, nút (mọi loại), text field, menu, list-item dạng card, chrome nổi trên bản đồ** |
| `large` | 16dp | Dialog, ô ảnh lớn (preview ảnh chụp) |
| `extraLarge` | 24dp | **Bottom sheet** (chỉ 2 góc trên), đặt qua tham số `shape` của `ModalBottomSheet` |
| `full` (pill) | 50% | Chỉ cho: chỉ báo Bottom Navigation, badge đếm số trên chuông, user-location dot |

**Hợp nhất từ hiện trạng:**

| Hiện tại | vNext |
|---|---|
| `Dimens.radiusSmall` 8 | `small` |
| `Dimens.radiusMedium` 12 | `medium` |
| `Dimens.radiusLarge` 16 | `large` |
| `Dimens.radiusSheet` 28 | `extraLarge` (28 → 24) |
| `Dimens.radiusPill` 999 | `full` |
| `Radius.badge` 6 | `extraSmall` |
| `Radius.chip` 10 | `small` (10 → 8) |
| `Radius.control` 14 | `medium` (14 → 12) |
| `Radius.card` 16 | `medium` (16 → 12) |
| `Radius.block` 20 | `large` (20 → 16) |
| `Radius.sheet` 28 | `extraLarge` (28 → 24) |
| Literal `2.dp`, `3.dp`, `5.dp`, `6.dp` | Bỏ. Thanh tiến độ dùng `full` |

> Thay đổi radius card 16 → 12 và nút 14 → 12 là **đổi thị giác có chủ đích** theo hướng Civic Utility ("radius vừa phải 8–12dp", nghiên cứu mục 4). **✅ Đã duyệt (T-06, 2026-11-16).**

## 2. Border

| Token | Độ dày | Màu | Dùng cho |
|---|---:|---|---|
| `border.decorative` | 1dp | `outlineVariant` | Viền card, divider, ranh giới section |
| `border.control` | 1dp | `outline` (≥3:1) | Text field, outlined/secondary button, segmented button, checkbox |
| `border.selected` | 2dp | `primary` | Card/hàng được chọn, chip đã chọn kiểu outlined |
| `border.error` | 2dp | `error` | Field lỗi |
| `border.focus` | 2dp, offset 2dp | `focusRing` | Focus bàn phím/D-pad/switch access |
| `border.overdue` 🆕 | 1dp | `error` @ 100% | Card lệnh quá hạn. **Luôn kèm chữ "Quá hạn"**, không chỉ viền |

Hiện trạng dùng `error.copy(alpha = 0.4f)` cho viền quá hạn. Alpha làm tương phản phụ thuộc nền, nên vNext dùng màu đặc.

## 3. Elevation

| Mức | Shadow | Dùng cho |
|---|---|---|
| **Level 0** | không | Mặc định: card, list, top bar, bottom nav, bottom action bar. Phân tầng bằng `surfaceContainer*` + viền |
| **Level 1** | `shadowElevation = 1dp` | Card **đang được kéo/nhấn giữ** (nếu có). Hiện không dùng |
| **Level 2** | `shadowElevation = 3dp` | **Chrome nổi trên bản đồ** (search, FAB, legend, quick view peek). Bản đồ là nền "ồn", nên cần tách lớp. Luôn kèm viền `outlineVariant` |
| **Level 3** | `shadowElevation = 6dp` | Bottom sheet, dialog, menu |

- `tonalElevation = 0.dp` ở mọi nơi, và `surfaceTint = Transparent` (`COLOR_SYSTEM.md` 4.2). Không để M3 tự pha màu primary vào bề mặt, vì như vậy màu sẽ khác nhau giữa các component.
- Night Field: shadow gần như vô hình trên nền tối, nên **viền `outlineVariant` là bắt buộc** cho Level 2–3.

## 4. Phân tầng bề mặt

```
Light                                   Night Field
background      #F8FAFC  (màn)          background      #0D0D0D
 └ surface      #FFFFFF  (card, bar)     └ surface      #121212
    └ container #F1F5F9  (nhóm, track)      └ container #1C1C1C (card NF)
       └ high   #E8EDF3  (menu)                └ high   #262626 (chrome bản đồ)
          └ highest #E2E8F0 (field, skel.)        └ highest #2E2E2E
```

**Quy tắc:**
1. Card trên nền `background` dùng `surface` + `border.decorative`.
2. Trong Night Field, card dùng `surfaceContainer`. Nền đen tuyệt đối chỉ dành cho `background` và camera.
3. Tối đa **2 tầng lồng nhau** trong một card (ví dụ card → ô metric). Không lồng card trong card.
4. **Không gradient** ở bất kỳ bề mặt nào (quyết định #5). Header là `surface` phẳng (Light) hoặc `surface` (NF). Màu thương hiệu xuất hiện qua chữ/icon `primary`, không qua nền khối lớn.
5. ⚠️ **T-07:** quyết định #5 loại gradient "khỏi các màn hình". Cần xác nhận màn **Đăng nhập (F01)** có còn nền khối `brand.navy` phẳng (không gradient) hay chuyển sang nền `background` như các màn khác.

## 5. Ảnh và media

- Ảnh chụp (bằng chứng, ảnh cột) dùng `large` 16dp, viền `border.decorative`, tỉ lệ 4:3. Placeholder nền `surfaceContainerHighest`.
- Chip/HUD trên ảnh hoặc camera dùng `overlay.onMedia` (đen 60%) + chữ trắng. Đây là ngoại lệ duy nhất cho nền bán trong suốt, vì không có "surface đặc" phía sau ảnh.
