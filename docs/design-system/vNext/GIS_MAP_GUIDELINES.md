# GIS Map Design Guidelines: LuxMap Mobile Design System vNext

> Trạng thái: **Đề xuất, chờ duyệt.** UI UX Pro Max **không có** dữ liệu cho GIS/bản đồ (xác minh ở `DESIGN_DIRECTION_RESEARCH.md` mục 1). Toàn bộ quy chuẩn dưới đây là lập luận thiết kế, có số tương phản tính được.
> Nguồn hiện trạng: `feature/map/ui/MapScreen.kt`, `core/map/MapLibreConfig.kt`, `core/map/MapMarkerIcons.kt`, `res/drawable/ic_marker_*.xml`, `ic_*_badge.xml` (đọc ngày 2026-11-16).

## 1. Những gì đang có (không phát minh lại)

| Thành phần | Hiện trạng | vNext |
|---|---|---|
| Nền bản đồ | **Vector OpenFreeMap mặc định** (`isSatelliteBasemap = false`), vệ tinh MapTiler là tùy chọn | Giữ, đúng quyết định #9 |
| Pole marker | Vector drawable 28×35dp, hình giọt nước, **cùng một hình cho cả 4 trạng thái**. Màu đổ theo trạng thái, viền trắng 2.5 (viewport 40), đĩa trắng + glyph cột đèn navy | Giữ silhouette, **thêm viền tối ngoài + phân biệt hình dạng qua badge** (mục 3) |
| Badge trạng thái | Tròn navy + glyph trắng ở góc trên-phải, cho dim/out/unknown. Normal không có badge | Đổi **hình dạng badge** theo trạng thái (mục 3.2) |
| Badge POI nhạy cảm | Tròn **đỏ `#D64545`** + "!" ở góc dưới-phải | ⚠️ Trùng màu marker "Hỏng/Tắt", đề xuất đổi (T-09) |
| Badge IoT | Tròn xanh `#3E86C9` + tia sét | Giữ |
| Marker đang chọn | Icon 1.3× + vòng halo `CircleLayer` bán kính 20 | Giữ cơ chế, chuẩn hoá màu vòng (mục 4) |
| Cụm (cluster) | Đếm số + "mức nghiêm trọng cao nhất" (out 3 > dim 2 > normal 1 > unknown 0), radius 50, max zoom 14 | Giữ, chuẩn hoá hình (mục 5) |
| Tuyến đường | Màu theo `has_active_segment_fault` (Blue500 / Rose600) | Thêm **nét đứt** cho tuyến lỗi + **viền trắng 1dp** cho cả hai loại (tuyến lỗi chỉ 2.74:1 trên vệ tinh) |
| Vị trí người dùng | MapLibre `LocationComponent` mặc định | Giữ (mục 6) |
| Chrome | FAB đổi nền (chữ "Vector"/"Vệ tinh"), FAB định vị, search, KPI chips, legend thu gọn, sheet lớp | Chuẩn hoá (mục 7) |

## 2. Nguyên tắc bản đồ

1. **Trạng thái được mã hoá 3 lớp: màu + hình dạng + glyph.** Mất một lớp (mù màu, nền nhiễu, màn hình chói) vẫn đọc được. Đèn mờ và Bình thường chênh nhau **1.02:1** về độ chói, nên màu không đủ.
2. **Đường biên marker luôn tương phản với nền, bất kể nền nào**, nhờ viền kép.
3. **Chrome đặc, không blur, không trong suốt.** Đọc được trên mọi loại ảnh nền.
4. **Bản đồ là nền, không phải giao diện.** Không đặt nhiều hơn 3 cụm chrome cùng lúc (search/KPI ở trên, FAB ở dưới-phải, legend ở dưới-trái).
5. **Không tự thêm loại đối tượng.** Chỉ hiển thị đối tượng mà dữ liệu hiện có (cột, tuyến, vị trí bản thân).

## 3. Pole Marker / Asset Condition Marker

### 3.1 Cấu trúc (từ trong ra ngoài)
```
① Glyph cột đèn navy #1F3864 trên đĩa trắng  (11.62:1, đã có)
② Thân giọt nước, màu đổ = map.marker.<status>.fill
③ Halo trong: trắng, stroke 2.5 (viewport 40)  → tương phản với nền TỐI (12.01:1)
④ Halo ngoài 🆕: #0F172A, stroke 1.5 bao ngoài ③  → tương phản với nền SÁNG (15.56:1)
⑤ Badge trạng thái (góc trên-phải), hình dạng theo trạng thái
⑥ Badge phụ (POI dưới-phải, IoT dưới-trái), chỉ khi dữ liệu có
```
Kích thước hiển thị: 28×35dp (giữ). **Vùng chạm 48×48dp** (mục 8).

### 3.2 Mã hoá theo trạng thái

| Trạng thái | Màu đổ ② | Badge ⑤: hình | Badge ⑤: glyph | Tín hiệu thêm |
|---|---|---|---|---|
| Bình thường (`normal`) | `#5FC4B0` | **không badge** | | Sự vắng badge là tín hiệu "không có gì bất thường" |
| Đèn mờ (`dim`) | `#E9A23B` | 🆕 **Tam giác** navy, viền trắng | "!" trắng | |
| Hỏng/Tắt (`out`) | `#D64545` | 🆕 **Hình thoi** navy, viền trắng | "×" trắng (đã có) | Ưu tiên vẽ **trên cùng** (z-order) khi chồng lấn |
| Chưa xác định (`unknown`) | `#64748B` | 🆕 **Tròn viền nét đứt** navy | "?" trắng | |

Hình dạng lấy đúng ngữ nghĩa Design System v2.0 mục 2.3 (tròn+check · tam giác+cảnh báo · thoi+X · nét đứt+?), áp lên **badge** thay vì toàn bộ marker. Lý do: silhouette giọt nước là "điểm neo vị trí" quen thuộc, đổi toàn bộ hình marker sẽ làm mất điểm neo đó.

> ⚠️ **Mâu thuẫn đã ghi nhận:** DS v2.0 mục 2.3 mô tả hình dạng cho **toàn bộ marker**. Code hiện tại (thiết kế "Icon Cot Den Map", 2026-11-04) dùng cùng một silhouette và badge **tròn** cho mọi trạng thái. vNext đề xuất phương án ở giữa (badge đổi hình). Cần duyệt (T-10), và thay đổi đụng tới 3 file `ic_marker_badge_*.xml` + 4 file `ic_marker_*.xml` (thêm halo ngoài).

### 3.3 Badge phụ
| Badge | Hiện tại | vNext | Lý do |
|---|---|---|---|
| Gần khu vực nhạy cảm (`near_sensitive_poi`) | Tròn đỏ `#D64545` + "!" | ⚠️ **Hình vuông bo góc, nền trắng, viền + glyph navy** (T-09) | Đỏ trùng "Hỏng/Tắt"; "!" trùng ngữ nghĩa cảnh báo của "Đèn mờ" |
| Có thiết bị IoT (`has_iot_node`) | Tròn `#3E86C9` + tia sét | Giữ | Không trùng màu trạng thái |

## 4. Selected Marker
- **Selected:** icon 1.3× (giữ), **vòng chọn** đặc 3dp quanh marker.
  - Nền vector: `map.selection.ring` = `#1F3864` (10.12:1).
  - Nền vệ tinh: `#9DB7E0` (5.88:1).
- **Unselected:** kích thước 1.0, không vòng.
- **Chỉ một marker được chọn tại một thời điểm.** Marker được chọn vẽ trên cùng.
- Khi chọn, mở **Quick View** (mục 10). Đóng Quick View thì bỏ chọn.
- Hiện vòng halo dùng `CircleLayer` cùng màu với cả hai nền. vNext đổi màu theo nền bản đồ đang bật.

## 5. Cluster
- **Hình tròn**, đường kính 40/48/56dp theo số lượng (<10 / <100 / ≥100), nền `surface` + viền 3dp màu của **trạng thái nghiêm trọng nhất** (dùng `map.marker.<status>.fill`), số đếm `labelMedium` `onSurface`.
- Viền ngoài `#0F172A` 1dp như marker, để nổi trên nền sáng.
- Nếu cụm chứa `out`: thêm badge thoi nhỏ. Không chỉ dựa vào màu viền.
- Chạm thì zoom tới mức tách cụm (giữ hành vi hiện có).

## 6. User Location Marker
- Dùng `LocationComponent` mặc định của MapLibre: chấm xanh có viền trắng + vòng độ chính xác (accuracy circle). Không tự vẽ lại.
- Vòng độ chính xác **luôn bật** để người dùng thấy GPS yếu.
- Nút "Về vị trí của tôi" (FAB, mục 7) có `contentDescription = "Định vị vị trí hiện tại"` (giữ).
- Night Field: giữ màu mặc định. Chấm xanh + viền trắng vẫn rõ trên nền tối (viền trắng 12:1).

## 7. Map Control (chrome)

| Control | Vị trí | Spec |
|---|---|---|
| Ô tìm kiếm | Trên cùng, dưới status bar, lề 16dp | `LuxSearchField`, Level 2 |
| KPI overlay | Ngay dưới ô tìm kiếm | Hàng chip, xem mục 12 |
| FAB định vị | Dưới-phải | `FloatingActionButton` 56dp, nền `surface`, icon `MyLocation` `onSurface`, Level 2 + viền `outlineVariant` |
| FAB đổi nền | Trên FAB định vị, cách 12dp | `SmallFloatingActionButton` 48dp + nhãn. **Hiển thị trạng thái hiện tại** bằng icon (`Map` = vector, `SatelliteAlt` = vệ tinh) + `contentDescription` "Nền bản đồ: Vector. Chạm để đổi sang Vệ tinh". Sửa G3 audit: hiện đang hiện chữ "đích đến" bằng font hệ thống |
| Nút lớp/lọc | Cạnh ô tìm kiếm (trailing icon `Layers`) | Mở bottom sheet (mục 11) |
| Legend | Dưới-trái, thu gọn mặc định | Mục 9 |

**Chung:**
- Nền chrome là **surface đặc** (`surface` Light / `surfaceContainerHigh` NF) + viền `outlineVariant` + shadow Level 2.
- **Không** blur, **không** alpha.
- Mọi control ≥48dp.
- Chrome không che quá **25% chiều cao bản đồ** khi không có sheet mở.

## 8. Touch target trên bản đồ
- Marker hiển thị 28×35dp **< 48dp**, nên khi chạm, truy vấn feature trong **hình vuông 48×48dp quanh điểm chạm** (`queryRenderedFeatures(RectF)`), không truy vấn đúng 1 điểm.
- Nhiều marker trong vùng chạm: chọn **marker gần tâm nhất**. Nếu ≥2 marker cách đều nhau (khó phân định), mở danh sách chọn nhỏ trong sheet.
- Ở zoom thấp, cluster thay thế marker (radius 50, giữ), nên không xảy ra chạm nhầm hàng loạt.
- Mọi thao tác kéo/pinch đều có lựa chọn thay thế bằng nút: Về vị trí của tôi, tìm kiếm rồi chọn kết quả thì camera bay tới.

## 9. Map Legend
- Card thu gọn: tiêu đề "Chú giải" (`labelLarge`) + chevron. Mở ra là danh sách.
- **Mỗi dòng vẽ đúng icon thật** đang dùng trên bản đồ (marker + badge), không dùng chấm màu.
- Nhãn `labelSmall` (14sp, thay `bodySmall`), dùng đúng nhãn `AssetCondition.label()`.
- Nhóm: Trạng thái cột (4) · Tuyến đường (bình thường / đang gặp sự cố, có mẫu nét liền/nét đứt) · Ký hiệu phụ (gần khu vực nhạy cảm, có thiết bị IoT).
- Mở legend **không** che FAB.

## 10. Map Quick View
- **Bottom sheet** (`LuxBottomSheet`) ở trạng thái peek khoảng 40% màn, có **nút đóng**.
- **Cột:** mã cột (`titleLarge`) · badge tài sản · tuyến (`bodyMedium`) · 2–3 thông tin nhanh hiện có (nguồn điện, có IoT, gần khu vực nhạy cảm) · action **hiện có**: "Đường đi" (Secondary, mở ứng dụng bản đồ ngoài) + "Chi tiết" (Primary).
- **Tuyến:** tên tuyến · trạng thái sự cố tuyến · chiều dài · số cột (dữ liệu `RoadSegmentLine` hiện có).
- **Không thêm** hành động chưa có backend hoặc thuộc vai trò Kỹ sư bảo trì (ví dụ "Tạo lệnh sửa chữa", P-06 audit).

## 11. Map Filter / Map Bottom Sheet (lớp hiển thị)
- Sheet "Hiển thị trên bản đồ" (giữ nội dung hiện có):
  - Nhóm **Lớp bản đồ:** Switch cột đèn, Switch tuyến đường, mỗi hàng 56dp, role Switch.
  - Nhóm **Trạng thái cột:** 4 `LuxFilterChip` có icon trạng thái + check khi chọn.
- Thay đổi áp dụng ngay (không cần nút Áp dụng). KPI vẫn hiện **tổng thật** (giữ quy tắc FM-36).
- Khi bộ lọc đang ẩn một số trạng thái: nút lớp/lọc hiện **chấm báo** + `contentDescription` "Đang lọc: ẩn 2 trạng thái".

## 12. Map KPI Overlay
- Hàng `FilterChip`/chip thông tin cuộn ngang, cao 32dp (vùng chạm 48dp), nền `surface` đặc + viền.
- Mỗi chip: icon trạng thái + số + nhãn ngắn ("12 Hỏng/Tắt").
- Số dùng ô độ rộng tối thiểu cố định (chữ số tỉ lệ, `TYPOGRAPHY.md` 1).
- Chạm chip thì **lọc nhanh** theo trạng thái đó, nếu đây là hành vi hiện có. Không thêm hành vi mới.

## 13. Fault Marker & Work Order Marker: **TBD, không định nghĩa**
- **Fault Marker:** bản đồ mobile hiện **không** vẽ fault như đối tượng riêng. Sự cố thể hiện qua trạng thái cột và `has_active_segment_fault` của tuyến. Không có endpoint/lớp dữ liệu fault cho bản đồ mobile trong code hiện tại.
- **Work Order Marker:** DS v2.0 mục 6.10 có liệt kê, nhưng **bản đồ mobile không có lớp WorkOrder** (audit G6). Việc thêm lớp này là quyết định sản phẩm.
- ⚠️ **T-11:** nếu chủ dự án duyệt thêm một trong hai lớp, quy chuẩn phải tuân theo mục 2. Gợi ý ràng buộc: hình dạng **khác hẳn** giọt nước (ví dụ ô vuông có icon loại lệnh), không dùng màu trạng thái tài sản. Chưa thiết kế chi tiết cho tới khi có nghiệp vụ.

## 14. Bản đồ ban đêm
- Chrome theo Night Field (`DARK_NIGHT_FIELD.md`).
- ⚠️ **T-12:** style bản đồ tối (dark basemap) **chưa có**. OpenFreeMap/MapTiler có style tối hay không và có miễn phí hay không cần kiểm tra trước khi đưa vào phạm vi. Trong lúc chờ, ban đêm dùng vector sáng hoặc vệ tinh. Viền kép giữ marker đọc được trên cả hai.
- Bản đồ offline (audit G1) là quyết định sản phẩm riêng, ngoài Design System.
