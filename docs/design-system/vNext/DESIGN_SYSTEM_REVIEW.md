# Design System vNext: Tự review chất lượng

**Ngày:** 2026-11-16 · **Người review:** Claude Opus 5.5, tự kiểm tra trước khi chuyển chủ dự án duyệt.
**Phạm vi:** 11 file trong `docs/design-system/vNext/`. Không có thay đổi code nào đi kèm.

## 0. Phương pháp kiểm tra (đã chạy thật)

| Kiểm tra | Công cụ | Kết quả |
|---|---|---|
| Tương phản mọi cặp màu trong tài liệu | Script Python, công thức độ chói tương đối WCAG 2.x (scratchpad phiên làm việc, không thuộc repo) | ~90 cặp đã tính. Kết quả ở mục 1–2 |
| Glyph tiếng Việt + ký hiệu trong font | Đọc bảng `cmap` của 4 file `.ttf` bundle | Đủ |
| OpenType feature (`tnum`) và độ rộng chữ số | Đọc bảng `GSUB` và `hmtx` | **Không có `tnum`, chữ số tỉ lệ** |
| Tỉ lệ line height / size của 15 slot | Script đọc bảng trong `TYPOGRAPHY.md` | Đạt sau khi sửa (mục 3) |
| Hiện trạng code được mô tả | Đọc trực tiếp `core/theme/*`, `core/ui/components/*`, `MapScreen.kt`, `MapMarkerIcons.kt`, `res/drawable/ic_marker_*`, `libs.versions.toml` | Mục 8 |
| Mã TBD được tham chiếu đầy đủ | `grep T-[0-9]{2}` | 16 mã T-01 → T-16, đều có định nghĩa ở README |

## 1. Light / Night Field có nhất quán không?
✅ **Có.** Mọi role M3 (mục 4 `COLOR_SYSTEM.md`) và mọi bộ trạng thái nghiệp vụ (mục 6) đều có cặp Light và Night Field. Mọi cặp chữ đạt **≥4.5:1** ở cả hai theme.

Các cặp chữ chính / CTA đạt AAA:

| | Light | Night Field |
|---|---|---|
| `onSurface` | 17.85 | 15.01 |
| `onPrimary` | 11.62 | 8.15 |
| `primary` trên nền | 11.10 | 9.17 |

Mức thấp nhất (chấp nhận được, AA):
- `warning` trên `warningContainer` Light: **4.51**. Sát ngưỡng, nên khi dùng chữ `warning` trong banner, ưu tiên `onWarningContainer` (8.15).
- `success` trên `successContainer` Light: 4.84.

**Điểm cần chú ý:**
- Night Field **không** đảo màu: badge NF là "chữ sáng trên ô tối" (nền badge chỉ cao hơn `surface` 1.25–1.58:1). Đây là chủ ý giảm lóa. Hệ quả là ranh giới badge NF mờ hơn Light, được bù bằng icon + nhãn.
- Primary NF đổi `Green400` thành `Navy300` là **thay đổi so với code**. ✅ Đã duyệt (T-01, 2026-11-16).

## 2. Màu semantic có bị dùng sai mục đích không?
✅ Đã tách:
- `error/success/warning/info` (phản hồi của **ứng dụng**) tách khỏi trạng thái **nghiệp vụ**.
- Mỗi miền nghiệp vụ có token riêng.

⚠️ **Trùng giá trị hex có chủ đích** (khác token, cùng màu), ghi lại để không ai tưởng là lỗi:
- `status.wo.assigned` = `status.sync.queuedOnline` = `status.priority.normal` (`#E8EEF7/#1F3864`)
- `status.wo.inProgress` = `status.sync.syncing`
- `status.wo.done` = `status.sync.done` = `status.iot.online`
- `status.asset.dim` = `status.priority.high`
- `status.asset.out` = `status.priority.urgent`
- `status.iot.offline` = `status.sync.conflict`

Lý do: họ sắc độ dùng chung theo ý nghĩa (`COLOR_SYSTEM.md` 1). Hai badge cùng màu khác nghĩa được phân biệt bằng **icon + nhãn + vị trí**, và mỗi token tiến hoá độc lập được. **Rủi ro còn lại:** trên cùng một list item, badge "Đã giao" (lệnh) và "Chờ đồng bộ" (sync) trông cùng màu. Quy tắc C1 (icon khác nhau: `Assignment` / `Schedule`) phải được tuân thủ khi code.

✅ Đã sửa 2 chỗ dùng sai ở hiện trạng:
- `task_kind` mượn màu `wo_status`: đổi sang trung tính `kind.*`.
- IoT "offline" chưa có token, đang dễ bị tô đỏ như "Hỏng": dùng họ cam.

## 3. Typography có đủ 15 slot không?
✅ **Đủ 15 slot**, tất cả Be Vietnam Pro, không slot nào <14sp. Nội dung và hành động ≥16sp.

🔧 **Lỗi tự phát hiện và đã sửa trong lúc review:** quy tắc "line height ≥1.35×" ban đầu không khớp chính bảng của nó (headline 1.31–1.33×, labelSmall 1.29×).
- Đã đổi `headlineLarge` 26/34 thành **26/36**, `headlineMedium` 24/32 thành **24/33**, `labelSmall` 14/18 thành **14/20**.
- Riêng `display*` (1.235–1.29×) được giới hạn **chỉ cho số liệu**, và quy tắc ghi đúng ≥1.23×.
- Script đọc lại bảng: 15/15 đạt.

⚠️ **Vấn đề chưa giải quyết được bằng token:** Be Vietnam Pro **không có `tnum`**, chữ số tỉ lệ ("1" = 385, "4" = 710 đơn vị). Số động sẽ xê dịch. Đã có quy tắc giảm nhẹ (ô độ rộng cố định, căn phải). Giải pháp triệt để cần quyết định font số (T-05).

⚠️ `labelSmall` 14sp cho nhãn Bottom Navigation ("Việc hôm nay") **chưa được đo trên máy thật** ở 360dp / font scale ≥1.3.

## 4. Có token trùng lặp hoặc mâu thuẫn không?
- ✅ **Trùng lặp cấu trúc đã loại bỏ** (trên giấy):
  - 2 bộ radius `Dimens.radius*` / `Radius.*` gộp thành 1 `Shapes`.
  - 2 thang chữ gộp thành 15 slot.
  - `ScreenMargin 20` / `Spacing.lg 16` gộp thành 16.
  - 25 primitive v2.1 đánh dấu loại.
- ⚠️ `surfaceVariant` và `surfaceContainer` cùng giá trị. Có chủ đích: `surfaceVariant` chỉ để tương thích M3, tài liệu yêu cầu dùng `surfaceContainer*`.
- ⚠️ `bodyLarge` và `bodyMedium` cùng 16sp/Regular, chỉ khác line height (24 và 22). Khác biệt nhỏ, có thể gây phân vân khi chọn. Quy tắc: `bodyMedium` chỉ cho nội dung trong card/list chật.
- ⚠️ **Mâu thuẫn với tài liệu hiện hành** (vNext theo code hoặc theo tương phản, cần cập nhật CLAUDE.md/v2.0 sau khi duyệt):

| # | Chỗ | Tài liệu cũ | Code hiện tại | vNext | Mã |
|---|---|---|---|---|---|
| 1 | Disabled Light | v2.0: `#94A3B8` / `#F1F5F9` | `#E2E8F0` / `#475569` | theo code | ✅ T-02 |
| 2 | Badge ưu tiên | CLAUDE.md / v2.0: 1 màu đơn mỗi mức | cặp nền/chữ | theo code | ✅ T-03 |
| 3 | Hình dạng marker | v2.0 2.3: hình riêng cho toàn marker | cùng silhouette, badge tròn | badge đổi hình | T-10 |
| 4 | Chữ phụ Light | CLAUDE.md: `#64748B` | `Slate600 #475569` | theo code (7.58:1) | ghi nhận, không cần duyệt riêng |
| 5 | Chữ phụ Dark | CLAUDE.md: `#A0A0A0` | `#B3B3B3` | theo code | ghi nhận |
| 6 | Accent Dark | CLAUDE.md / v2.0: `#5FC4B0` | `#5FC4B0` | `#9DB7E0` | ✅ T-01 |
| 7 | Error Dark | `#DC2626` (3.60:1, **trượt AA**) | `#DC2626` | `#FF9A9A` | sửa lỗi, không cần duyệt riêng |

## 5. Có đáp ứng tiếng Việt không?
✅ Font đủ glyph tiếng Việt + ký hiệu (đã đọc `cmap`). Line height ≥1.35× cho mọi slot chứa chữ. `LineHeightStyle.Trim.None` để không cắt dấu. Letter-spacing 0. Cấm ellipsis cho mã/lỗi/lý do khóa.

⚠️ **Chưa kiểm thử hiển thị thật** chuỗi mẫu ở font scale 2.0 trên máy. Đây là kiểm tra thủ công bắt buộc.

## 6. GIS marker có phân biệt được trên hai loại bản đồ không?
**Phát hiện được (marker là một vật):** ✅ có, nhờ viền kép.
- Viền trắng trong: **12.01:1** trên nền vệ tinh tối ước lượng.
- Viền tối ngoài 🆕: **15.56:1** trên nền vector sáng.
- Không cần đổi màu đổ theo nền.

**Phân biệt trạng thái:** ✅ có điều kiện. Màu Bình thường và Đèn mờ chênh nhau **1.02–1.03:1** về độ chói, nên ở thang xám **chỉ** phân biệt được nhờ badge hình dạng (không badge / tam giác / thoi / tròn nét đứt). Badge đổi hình là **đề xuất chưa được duyệt** (T-10). Nếu không duyệt, hiện trạng (badge tròn cùng hình, khác glyph) vẫn phân biệt được bằng glyph, nhưng yếu hơn ở kích thước nhỏ.

**Tuyến:** tuyến lỗi Rose600 chỉ **2.74:1** trên vệ tinh. 🔧 Đã sửa trong review: yêu cầu viền trắng 1dp cho **cả hai** loại tuyến (bản đầu chỉ ghi cho tuyến thường).

⚠️ **Giới hạn của phép kiểm:** nền vệ tinh `#2B3A2F` là **giá trị ước lượng** cho vùng nông thôn ban đêm/xanh cây, không phải đo trên ảnh MapTiler thật. Cần kiểm tra thủ công trên vùng thật.

## 7. Các component có triển khai được bằng Compose không?
✅ Mọi component ánh xạ vào API có trong material3 1.3.x (BOM 2024.10.01): `NavigationBar`, `TopAppBar`, `Button`/`OutlinedButton`/`TextButton`/`IconButton`, `Card`/`OutlinedCard`, `ListItem`, `OutlinedTextField`, `SearchBar`, `ExposedDropdownMenuBox`, `DatePickerDialog`, `FilterChip`, `SingleChoiceSegmentedButtonRow`, `Snackbar`, `AlertDialog`, `ModalBottomSheet`.

⚠️ Một số API có thể yêu cầu `@OptIn(ExperimentalMaterial3Api::class)` (đã có 8 file trong repo dùng annotation này). Kiểm tra khi code.

⚠️ `collectAsStateWithLifecycle` cần **thư viện mới** `lifecycle-runtime-compose` (T-16).

⚠️ Luật "`feature/` không import primitive" **không ép được bằng `internal`** vì app là 1 module. Cần lint rule hoặc checklist review (T-15).

## 8. Có vô tình thay đổi nghiệp vụ hoặc navigation không?

**Không có thay đổi nghiệp vụ.**
- Không thêm enum backend. `IotStatus` là enum **trình bày** ánh xạ từ dữ liệu có sẵn.
- Không thêm endpoint, không thêm màn.
- Fault marker và Work Order marker được **cố ý để TBD** (T-11).
- `status.iot.stale` để TBD (T-04).
- Không đề xuất ưu tiên từ `priorityScore`.

**Không đổi navigation:** 4 tab, thứ tự, đích, back stack giữ nguyên.

⚠️ **Hai điểm sát ranh giới, cần chủ dự án biết rõ:**
1. **Chuông thông báo chuyển từ overlay nổi vào Top App Bar của tab gốc.** Đích điều hướng không đổi, nhưng vị trí hiển thị và cấu trúc `NavGraph` Scaffold sẽ phải đổi khi code. Xếp vào "layout shell", không phải navigation, nhưng cần duyệt cùng Design System.
2. **Thay `TopSlideToast` bằng Snackbar M3** đổi vị trí xuất hiện phản hồi (trên → dưới) (T-08).

Các đề xuất mới về *hành vi hiển thị* (không phải nghiệp vụ) đều gắn mã TBD:
- Chấm báo "đang lọc" trên nút lớp bản đồ.
- Chọn marker gần tâm trong vùng 48dp.
- Icon "vạch" cho priority (T-03).

## 9. Có phần nào chỉ là suy đoán nhưng viết thành quyết định chính thức không?
Đã rà soát. Các mục **là đề xuất**, không phải quyết định đã duyệt, đều có ⚠️ + mã T-xx:
- ~~T-01 (Navy300), T-03 (icon priority), T-06 (radius)~~ đã duyệt 2026-11-16 · T-07 (nền F01), T-08 (snackbar), T-09 (badge POI), T-10 (hình badge), T-13 (công tắc Night Field), T-14 (độ sáng camera).

Các mục **là quyết định đã duyệt** (README mục 4) chỉ gồm 12 quyết định trong prompt. Mọi phần khác là đề xuất.

Hai giá trị là **ước lượng**, đã ghi rõ trong tài liệu:
- Màu nền vệ tinh `#2B3A2F`.
- Nhận định "nhãn Bottom Nav vừa khít ở 360dp".

## 10. Vấn đề tồn tại sau review
| # | Vấn đề | Mức | Hướng xử lý |
|---|---|---|---|
| R-1 | Chữ số tỉ lệ (không `tnum`) | Trung bình | T-05 |
| R-2 | Bình thường và Đèn mờ không phân biệt được bằng độ chói | Cao (GIS) | Badge hình dạng (T-10), không có cách sửa bằng màu mà giữ được nhận diện |
| R-3 | Chưa kiểm tra thủ công: font 200%, TalkBack, thang xám, ngoài trời, vệ tinh thật | Cao | Bắt buộc ở giai đoạn redesign từng màn |
| R-4 | Không ép được luật "không import primitive" bằng compiler | Trung bình | T-15 |
| R-5 | `warning` trên `warningContainer` Light chỉ 4.51:1 | Thấp | Dùng `onWarningContainer` cho chữ trong banner |
| R-6 | CLAUDE.md và DS v2.0 lệch với code/vNext ở 7 chỗ (mục 4) | Trung bình | Cập nhật CLAUDE.md sau khi duyệt |
| R-7 | `connectedDebugAndroidTest` fail toàn bộ trên emulator local (lỗi môi trường đã biết) | Trung bình | Kiểm tra a11y tự động phải chạy trên máy thật |

## 11. Đính chính tài liệu trước đó (phát hiện trong lúc làm vNext)
- `AUDIT_REPORT.md` G2 ghi bản đồ mặc định là **vệ tinh**. **Sai.** Code mặc định **vector** (`MapScreen.kt:224`). Đã sửa audit và `DESIGN_DIRECTION_RESEARCH.md`.
- `AUDIT_REPORT.md` ghi `#B45309` trên Dark ≈2.5:1. **Sai.** Đúng là **3.47:1**. Đã sửa.
