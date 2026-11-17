# Đề xuất redesign: Bản đồ (F12) và Chi tiết cột

> **Trạng thái:** **CHỜ DUYỆT BẢN VIẾT.** Hướng đã thống nhất 2026-11-17 (M1–M4, mục 9). Chưa có code nào thay đổi.
> **Màn:** `feature/map/ui/` — `MapScreen.kt` (1267 dòng), `PoleDetailScreen.kt` (683), `PoleQuickViewBottomSheet.kt`, `RoadSegmentQuickViewBottomSheet.kt`, `MapSearchBar.kt`, `MapKpiChipRow.kt`, `MapLegend.kt`, `MapLayerToggle.kt`, `MapStatusFilterRow.kt`; marker ở `core/map/` (`MapLibreConfig.kt`, `MapMarkerIcons.kt`) và `res/drawable/ic_marker_*.xml`, `ic_*_badge.xml`.
> **Căn cứ:** `docs/design-system/vNext/GIS_MAP_GUIDELINES.md`, `AUDIT_REPORT.md` (mục 6.7, 8), bản đồ web `luxmap-web/src/pages/gis-map` + `utils/gis-map/markerUtils.ts` (đọc 2026-11-17), `luxmap_backend` `MapController.cs` / `MapProperties.cs` / `CabinetTopologyService.cs` (nhánh `dev`, đọc 2026-11-17).

---

## 1. Mục tiêu và giới hạn

**Bản đồ:** kỹ sư thấy cột đèn và tuyến trong địa bàn, biết cột nào hỏng/mờ, tìm và mở nhanh một cột, đi tới cột đó; ở đợt 2 thấy thêm tủ điện, tủ nào cấp điện cho cột nào và lộ nào mất nguồn, cùng ranh giới xã được giao.

**Chi tiết cột:** đọc đủ thông tin một cột và mở đường đi.

**Chia 2 đợt, 2 PR:**
- **Đợt 1 (M1, M2, M4, M5):** redesign theo vNext, marker giống web, vùng chạm 48dp, Chi tiết cột. Không thêm dữ liệu mới.
- **Đợt 2 (M3):** thêm 3 lớp dữ liệu backend đã có: Tủ điện, Sơ đồ cấp nguồn + lộ mất nguồn, Ranh giới xã.

**Giữ nguyên:** quy tắc KPI luôn đếm tổng thật (FM-36), gom cụm, lớp tuyến theo `has_active_segment_fault`, vị trí bản thân, xử lý quyền vị trí, mọi hành động hiện có. **Nền bản đồ chỉ còn vector** (M5): bỏ nền vệ tinh MapTiler.

**Ngoài phạm vi:** bản đồ offline (G1), lớp Work Order/Fault (T-11), bản đồ tối (T-12), điều khiển chiếu sáng (của Kỹ sư bảo trì, web `LightingPreviewModal`), lọc theo tuyến (không chọn).

## 2. Đối chiếu với web và backend

| Endpoint (`/api/v1/map/…`, policy `ReadNetwork` — cả 4 vai trò) | Web | Mobile hiện tại | Đề xuất |
|---|---|---|---|
| `poles`, `poles/{pole_id}`, `segments` | Có | Có | Giữ |
| `cabinets` (tủ, `feeders[]` với `control_mode` on/off/auto và `power_state` unknown/on/off, `iot_node_ids`) | "Tủ điện điều khiển" | Không | **Đợt 2** |
| `cabinets/{cabinetId}/topology` (cạnh LineString tủ → cột, `feeder_id`, `feeder_source`, `unlocated_pole_count`) | "Sơ đồ cấp nguồn (Trụ → Cột)", "Lộ ngắt điện / Mất nguồn" | Không | **Đợt 2** |
| `communes` (MultiPolygon ranh giới xã của người gọi) | Có (trang quản lý xã) | Không | **Đợt 2** |
| `iot-nodes` | Gộp vào tủ | Badge IoT trên cột | Bỏ badge IoT trên cột ở đợt 2 (thiết bị nằm ở tủ) |

Các endpoint đợt 2 được backend tự ghi **SELF-SIGNED** (drift CABINET, TOPO-INFER, COMMUNE-BOUNDARY), chưa vào Contract chính thức — vẫn là nguồn đúng để code theo, giống cách đã làm với `WorkOrder`.

## 3. Vấn đề của màn hiện tại

| # | Vấn đề |
|---|---|
| G3 | Nút đổi nền hiện chữ **đích đến** ("Vector"/"Vệ tinh") bằng font hệ thống, người dùng không biết đang ở nền nào |
| — | Marker 28×35dp, chỉ trúng khi chạm đúng hình (< 48dp) |
| — | Badge "Gần khu vực nhạy cảm" tròn đỏ trùng màu "Hỏng/Tắt"; marker khác hình web |
| — | Còn primitive `Success600`, `Rose600`, `Amber500`, `Gray500`, `Radius` trong chrome |
| — | Chi tiết cột: `TopAppBar` thường, `MessageState` không có nút Thử lại, nhãn IoT "Online" tiếng Anh |
| — | So với web: thiếu tủ điện, sơ đồ cấp nguồn, lộ mất nguồn, ranh giới xã (đợt 2) |

## 4. Đợt 1 — Marker (M2: giống web, bỏ gradient)

- **Hình:** tròn phẳng, đường kính **17dp** (đang chọn **24dp**), viền trắng 2dp + nét viền tối `#0F172A` 1dp bao ngoài (thay đổ bóng của web để nổi trên nền sáng). Bỏ gradient, glow, blur, nhấp nháy.
- **Màu đổ** (theo web, khai báo một lần trong `core/map` dưới tên `MapMarkerColors`, không dùng ở `feature/` ngoài bản đồ):

  | Trạng thái | Màu |
  |---|---|
  | `normal` Đạt chuẩn | `#10B981` |
  | `dim` Đèn mờ | `#F59E0B` |
  | `out` Hỏng/Tắt | `#F43F5E` |
  | `unknown` Chưa quét | `#64748B` |

- **Badge "Gần trường, cầu"** (`near_sensitive_poi`): tròn hổ phách `#F59E0B` viền trắng + ngôi sao trắng, góc trên-phải (giống web). Bỏ 3 badge trạng thái `ic_marker_badge_*` (web không có).
- **Badge IoT:** giữ ở đợt 1, bỏ ở đợt 2 khi có lớp tủ điện.
- **Đang chọn:** 24dp + vòng chọn hiện có (`CircleLayer`), màu vòng `#1F3864`.
- **Thứ tự vẽ:** `out` trên cùng khi chồng lấn.
- **Lưu ý trợ năng (đã chấp nhận, M2):** trạng thái trên marker chỉ phân biệt bằng màu như web. Bù lại: chú giải và sheet xem nhanh luôn có nhãn chữ; TalkBack đọc trạng thái khi chọn marker.
- Cụm (cluster): giữ hành vi; vòng viền dùng màu trạng thái nghiêm trọng nhất theo bảng trên.
- File đổi: 4 `ic_marker_*.xml`, `ic_poi_badge.xml`, xoá 3 `ic_marker_badge_*.xml`, `MapMarkerIcons.kt`, `MapLibreConfig.markerColor()`.

## 5. Đợt 1 — Chạm marker (M1: vùng 48dp)

- Chạm bản đồ: `queryRenderedFeatures(RectF)` trong ô **48×48dp** quanh điểm chạm, trên lớp cột (và đợt 2: lớp tủ).
- Nhiều marker trong ô: chọn **gần tâm nhất**. Hàm thuần `pickNearestFeature(tap: PointF, candidates: List<Pair<id, PointF>>): id?` có unit test (rỗng, 1, nhiều, bằng nhau → lấy phần tử đầu theo id ổn định).
- Chạm trúng cụm: giữ hành vi zoom tách cụm.

## 6. Đợt 1 — Chrome, sheet, Chi tiết cột

- **Ô tìm kiếm:** nền `surface` đặc, viền `outlineVariant`, shadow Level 2, lề 16dp; nút lớp/lọc là trailing icon `Layers` 48dp, có **chấm báo** khi đang ẩn trạng thái nào đó + `contentDescription` "Đang lọc: ẩn N trạng thái".
- **Chip KPI:** cao 32dp, vùng chạm 48dp, nền đặc + viền, mỗi chip có chấm màu marker + số (ô độ rộng tối thiểu cố định) + nhãn ngắn; chạm = lọc nhanh nếu hiện đang có hành vi đó (giữ, không thêm).
- **Nút đổi nền:** bỏ (M5, chỉ còn nền vector). Lỗi G3 hết tồn tại theo.
- **Nút định vị:** `FloatingActionButton` 56dp, nền `surface`, icon `MyLocation` `onSurface`, viền `outlineVariant`; giữ spinner khi đang lấy vị trí.
- **Chú giải:** card thu gọn "Chú giải" (`labelLarge`) + chevron; mở ra vẽ **đúng hình marker thật**; nhóm Trạng thái cột (Đạt chuẩn / Đèn mờ / Hỏng/Tắt / Chưa quét) · Ký hiệu (Gần trường, cầu; Có thiết bị IoT ở đợt 1) · Tuyến đường (bình thường / đang có sự cố). Nhãn `labelMedium`. Không che FAB.
- **Sheet "Hiển thị trên bản đồ":** nhóm Lớp bản đồ (Switch Cột đèn, Switch Tuyến đường; hàng 56dp, role Switch) + nhóm Trạng thái cột (4 `LuxFilterChip` có chấm màu + check). Áp dụng ngay.
- **Sheet xem nhanh cột:** `ModalBottomSheet` có nút đóng; mã cột `titleLarge`, `StatusBadge` tài sản, tuyến `bodyMedium`, 2–3 thông tin nhanh hiện có; `LuxSecondaryButton` "Đường đi" + `PrimaryButton` "Chi tiết". Không thêm hành động.
- **Sheet xem nhanh tuyến:** tên tuyến, trạng thái sự cố tuyến, chiều dài, số cột (dữ liệu `RoadSegmentLine` hiện có), nút đóng.
- **Overlay trạng thái bản đồ:** Loading/Error/Offline dùng `LuxLoadingRegion`, `LuxBanner` (nền đặc, không trong suốt), giữ câu "Không tải được nền bản đồ · Dữ liệu cột vẫn khả dụng".
- **Chi tiết cột:** `LuxChildTopBar("Chi tiết cột đèn", subtitle = mã cột)`; các khối dùng `LuxSectionHeader` + `LuxCard`; nút "Đường đi" `LuxSecondaryButton`; IoT "Trực tuyến"/"Mất kết nối"; 4 trạng thái: Loading `LuxLoadingRegion("Đang tải chi tiết cột")`, Empty `LuxEmptyState("Không tìm thấy cột đèn này")`, Error `LuxErrorState("Không tải được chi tiết cột", mạng/chung)` có Thử lại (cần `PoleDetailViewModel.retry()` + `PoleDetailUiState.Error.isNetworkError`). Biểu đồ xu hướng giữ, đổi màu sang token.
- Bỏ primitive và `Radius` trong `feature/map/ui/`.

## 7. Đợt 2 — Lớp dữ liệu mới (M3)

**Dữ liệu:** `MapApi` thêm `cabinets(bbox)`, `cabinetTopology(cabinetId)`, `communes()`; DTO đúng tên field backend (`cabinet_id`, `cabinet_name`, `commune_id`, `feeder_ids`, `feeders[{feeder_id, relay_no, control_mode, mode_reported_at, power_state}]`, `iot_node_ids`; topology `features[]` LineString + `feeder_id`, `feeder_source`, `unlocated_pole_count`; communes MultiPolygon + `commune_id`, tên xã). `MapRepository` Real/Fake trả thêm 3 tập. Lỗi 404/409 của topology (tủ ngoài địa bàn / `POLE_SEGMENT_REQUIRED`) hiện thông báo trong sheet tủ, không làm hỏng bản đồ.

**Tủ điện:**
- Marker tia sét kiểu web, phẳng: tím `#4F46E5` bình thường, đỏ `#E11D48` khi **có ít nhất một lộ `power_state = off`**; viền trắng + nét tối như cột. Kích thước 24dp, vùng chạm 48dp.
- Sheet xem nhanh tủ: tên tủ `titleLarge`, xã, danh sách lộ (mỗi lộ: mã lộ, rơ-le, chế độ "Bật / Tắt / Tự động", nguồn "Có nguồn / Mất nguồn / Chưa rõ" bằng badge icon + nhãn), số thiết bị IoT, nút "Đường đi".
- Chạm tủ cũng **bật sơ đồ cấp nguồn** của tủ đó.

**Sơ đồ cấp nguồn + lộ mất nguồn:**
- Khi một tủ được chọn: vẽ các cạnh tủ → cột; lộ có nguồn nét liền `#4F46E5`, lộ mất nguồn **nét đứt** `#E11D48`; cột thuộc tủ đó giữ nguyên, cột khác làm mờ (alpha 0.35, giống web).
- `unlocated_pole_count > 0` → dòng "N cột chưa có vị trí" trong sheet tủ.
- Bỏ chọn tủ → xoá sơ đồ, bỏ làm mờ.

**Ranh giới xã:**
- `FillLayer` trong suốt + `LineLayer` viền `#1F3864` 2dp (vệ tinh: `#9DB7E0`), vẽ dưới mọi lớp khác; bật mặc định.

**Sheet lớp & chú giải:** thêm Switch "Tủ điện", "Ranh giới xã"; chú giải thêm "Tủ điện điều khiển", "Sơ đồ cấp nguồn (Tủ → Cột)", "Lộ ngắt điện / Mất nguồn", "Ranh giới xã". Bỏ badge IoT trên cột và mục "Có thiết bị IoT".

**Tìm kiếm:** thêm kết quả tủ điện (theo `cabinet_name`/`cabinet_id`), chọn thì bay tới tủ và mở sheet.

## 8. Test và chia commit

**Đợt 1**
- Unit: `pickNearestFeature`, ánh xạ trạng thái → màu/icon marker, `PoleDetailViewModel` (retry, lỗi mạng), `MapViewModel` nếu đổi trạng thái lọc.
- UI (SM-A075F): sheet xem nhanh cột/tuyến, sheet lớp (Switch, chip, chấm báo), nút đổi nền (`contentDescription` theo trạng thái), chú giải, Chi tiết cột 4 trạng thái, cỡ chữ 1.5× ở 360dp (vùng chạm 48dp).
- Thủ công: marker trên nền vector và vệ tinh, chạm marker sát nhau.
- Commit (≤ 400 dòng): 1) hàm chạm + màu marker + test; 2) drawable marker + đăng ký icon; 3) chrome (tìm kiếm, KPI, FAB, chú giải); 4) sheet lớp + 2 sheet xem nhanh; 5) Chi tiết cột khung + nội dung; 6) Chi tiết cột trạng thái + retry; 7) cỡ chữ lớn, dọn, trạng thái đề xuất.

**Đợt 2**
- Unit: parse 3 DTO, repository Real (MockWebServer) + Fake, quy tắc màu tủ (có lộ off → đỏ), làm mờ cột khác tủ, lỗi 404/409 topology.
- UI: sheet tủ (lộ, chế độ, nguồn, cột chưa có vị trí), sheet lớp có 2 Switch mới, kết quả tìm kiếm tủ.
- Commit: 1) API + DTO + repo; 2) lớp ranh giới xã; 3) lớp + marker tủ; 4) sheet tủ; 5) sơ đồ cấp nguồn; 6) tìm kiếm + chú giải + bỏ badge IoT; 7) dọn.

## 9. Quyết định của chủ dự án (2026-11-17)

| Mã | Câu hỏi | Chọn |
|---|---|---|
| M1 | Chạm marker | Vùng chạm 48dp, chọn marker gần tâm nhất |
| M2 | Kiểu marker | **Giống web** (tròn, màu theo web), bỏ gradient; không dùng T-10/T-09 của vNext |
| M3 | Lớp thêm so với web | Tủ điện; Sơ đồ cấp nguồn + lộ mất nguồn; Ranh giới xã (đợt 2). Không lọc theo tuyến |
| M4 | Chi tiết cột | Làm chung đợt 1 |
| M5 | Nền vệ tinh | **Bỏ hẳn**, mobile chỉ dùng nền vector (OpenFreeMap). Xoá nút đổi nền, `MAP_STYLE_URL`, khoá `MAPTILER_API_KEY` trong `BuildConfig` |
