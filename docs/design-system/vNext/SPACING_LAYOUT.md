# Spacing & Layout: LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ duyệt.** Màn hình mục tiêu: điện thoại Android **≥360dp** chiều rộng (minSdk 26). Tablet chỉ cần không vỡ layout.

## 1. Spacing scale (lưới 4dp)

| Token | dp | Dùng cho | Hiện trạng |
|---|---:|---|---|
| `space.none` | 0 | | |
| `space.xxs` 🆕 | 2 | Khoảng cách nhãn ↔ giá trị trong ô KPI | đang là literal `2.dp` |
| `space.xs` | 4 | Icon ↔ chữ trong badge, chip | `Spacing.xs` |
| `space.sm` | 8 | Khoảng giữa các touch target liền kề (tối thiểu), gap trong list | `Spacing.sm` |
| `space.md` | 12 | Gap giữa các card trong list, padding badge ngang lớn | `Spacing.md` |
| `space.lg` | 16 | **Lề màn hình**, padding card, gap giữa các khối trong section | `Spacing.lg` |
| `space.xl` | 24 | **Khoảng giữa section**, padding dialog | `Spacing.xl` |
| `space.xxl` | 32 | Khoảng trên/dưới Empty/Error state | `Spacing.xxl` |
| `space.xxxl` | 40 | Dự phòng | `Spacing.xxxl` |

**Loại bỏ:** `ScreenMargin = 20dp`, các literal `3.dp`, `5.dp`, `6.dp` cho padding. Riêng padding dọc của badge là trường hợp cần đúng chiều cao, xem `COMPONENT_SPECIFICATIONS.md`.

## 2. Bố cục màn hình

| Hạng mục | Giá trị | Ghi chú |
|---|---|---|
| Lề ngang màn hình | **16dp** (`space.lg`) | Một giá trị cho mọi màn. Hiện trạng đang lẫn 12/16/20dp (audit 5.3) |
| Khoảng giữa section | 24dp | Tiêu đề section → nội dung: 8dp |
| Padding card | 16dp. Card dày đặc (list item dạng card) dùng 12dp dọc / 16dp ngang | |
| Gap giữa các card trong list | 12dp | |
| Gap giữa các control trong form | 16dp. Nhãn → field: 8dp. Field → hint/lỗi: 4dp | |
| Chiều rộng nội dung tối đa | 720dp (tablet), canh giữa | Giữ DS v2.0 4.3 |
| Lưới | ≤599dp: **4 cột**, gutter 16, lề 16 · ≥600dp: 8 cột, gutter 24, lề 24 | Chỉ để canh KPI/ảnh. Danh sách luôn 1 cột trên điện thoại |

### 2.1 Ngân sách chiều rộng ở 360dp
360 − 2×16 (lề) = **328dp** nội dung.

- KPI 3 ô: (328 − 2×8) / 3 = **104dp** mỗi ô. Đủ cho số `displaySmall` + nhãn `bodySmall` 2 dòng.
- 2 nút song song: (328 − 8) / 2 = **160dp** mỗi nút. Đủ cho "Yêu cầu hủy" / "Bắt đầu" ở `labelLarge`.
- Ở font scale ≥1.5 các bố cục này chuyển sang dọc (`TYPOGRAPHY.md` mục 4).

## 3. Chiều cao list item

| Loại | Chiều cao tối thiểu | Nội dung |
|---|---:|---|
| 1 dòng | 56dp | Cài đặt, mục chọn trong sheet |
| 2 dòng | 72dp | Cột đèn (mã + trạng thái), thông báo |
| 3 dòng | 88dp | Lệnh việc (loại · mã / tên cụm / hạn) |
| Toàn bộ vùng có thể chạm | ≥48dp | Mọi hàng clickable, kể cả khi nội dung ít |

Dùng `heightIn(min = …)`, nội dung dài được phép giãn cao.

## 4. Safe area & system bars

- App chạy **edge-to-edge** (`enableEdgeToEdge()` trong `MainActivity`, giữ nguyên).
- **Mỗi template màn hình** (không phải từng màn tự xử lý) áp `WindowInsets.safeDrawing`:
  - Top App Bar: `statusBars` inset nằm **trong** bar. Nền bar kéo dài lên dưới status bar.
  - Nội dung cuộn: `contentPadding` bottom = chiều cao Bottom Navigation hoặc Bottom Action Bar + `navigationBars` inset. Không để nội dung cuối list bị che.
  - Màn camera (Night Field): preview tràn toàn màn, **control** tôn trọng `safeDrawing`.
- **Chuông thông báo nằm trong Top App Bar của tab gốc**, không nổi đè nội dung (sửa P-04 audit). Lưu ý: đây là thay đổi **vị trí hiển thị** trong cùng một shell. Điều hướng tới màn Thông báo **không đổi**.
- Khi bàn phím mở: dùng `imePadding()` cho vùng chứa form, đảm bảo field đang nhập và thông báo lỗi luôn thấy được (DS v2.0 4.3).

## 5. Bottom Navigation

| Thuộc tính | Giá trị |
|---|---|
| Số mục | 4 (giữ nguyên: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân). **Không đổi navigation** |
| Chiều cao | 80dp (M3 `NavigationBar` mặc định) + `navigationBars` inset |
| Icon | 24dp. **Outlined khi chưa chọn, Filled khi chọn** (tín hiệu không phụ thuộc màu) |
| Nhãn | `labelSmall` 14sp, luôn hiện, 1 dòng |
| Chỉ báo chọn | Pill 64×32dp màu `primaryContainer` |
| Hiển thị | Chỉ ở 4 màn tab gốc. Ẩn ở màn con, camera, sheet toàn màn (giữ hành vi hiện tại) |

## 6. Bottom Action Bar (CTA cố định đáy)

```
┌────────────────────────────────────────┐  ← divider 1dp outlineVariant
│ [progress segments 6dp]  (tuỳ chọn)    │  padding top 12dp
│ "3/5 sự cố đã có kết quả" bodySmall    │  (tuỳ chọn, trung tính)
│ ⓘ Lý do khóa CTA        bodySmall      │  (tuỳ chọn, màu error/warning)
│ [ Secondary ][     Primary 56dp      ] │  gap 8dp
└────────────────────────────────────────┘  padding bottom 16dp + navigationBars
```

- Nền `surface`, lề ngang 16dp (không phải 20dp như hiện tại).
- Một CTA chính duy nhất. Tối đa 1 hành động phụ đặt **bên trái**. Hành động phá huỷ (Yêu cầu hủy) **không** dùng kiểu Primary.
- Hint lý do khóa luôn nằm **ngay trên** nút bị khóa (quy tắc v2.0 "Chặn có giải thích").
- Nội dung cuộn phía trên phải có `contentPadding` bằng chiều cao thanh này.

## 7. Mật độ thông tin

- **Không** dùng style "Data-Dense Dashboard" (padding 8–12px, chữ 12–14px). Chữ sẽ rơi dưới sàn 16sp của LuxMap.
- Một card lệnh tối đa **3 dòng nội dung + 1 dòng metadata**. Thông tin thêm nằm ở màn chi tiết.
- Mỗi màn có **một vùng thông điệp nổi bật duy nhất** ở đầu nội dung. Thứ tự ưu tiên ở `COMPONENT_SPECIFICATIONS.md` (Banner), giải quyết P-07 audit.
