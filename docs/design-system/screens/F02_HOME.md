# Đề xuất redesign: Việc hôm nay (F02)

> **Trạng thái:** **CHỜ DUYỆT BẢN VIẾT.** Hướng thiết kế đã thống nhất trong buổi trao đổi 2026-11-16 (Q1–Q3, mục 10). Chưa có code nào thay đổi.
> **Màn:** `feature/home/ui/HomeScreen.kt` (683 dòng), route `home`, tab 1 của bottom nav.
> **Căn cứ:** `docs/design-system/AUDIT_REPORT.md` (mục 6.2, P-11, A1, A3, A4, A6, quy tắc Compose #20), `docs/design-system/vNext/` (component, màu, chữ), code hiện tại (đọc ngày 2026-11-16), quyết định trước của PR #109 và PR #127.
> **Phụ thuộc:** bộ component vNext (`LuxTabTopBar`, `LuxSegmented`, `LuxCard`, `LuxEmptyState`, `LuxErrorState`, `LuxSkeleton`, `LuxBanner`) đã có trên `dev`.

---

## 1. Mục tiêu và giới hạn

**Mục tiêu:** khi mở app, Kỹ sư hiện trường trả lời nhanh được:
1. **Tôi đang có bao nhiêu lệnh sửa chữa / kiểm tra, có lệnh nào quá hạn không?**
2. **Mỗi lệnh là việc gì, làm ngày nào, hạn ngày nào?**
3. **Lệnh nào tôi đã nộp nhưng chưa đồng bộ?**

**Giữ nguyên tuyệt đối:**
- **Tổ chức theo loại lệnh** (tab Sửa chữa / Kiểm tra), đúng quyết định PR #127 (2026-11-09). Không quay lại nhóm theo hạn như PR #109.
- Lệnh `survey` và `pole_gps_update` **không** hiện ở màn này, chỉ ở tab Khảo sát.
- Nguồn dữ liệu: `GET /work-orders?assigned_to=me&wo_status=assigned,in_progress`. Không đổi API, không thêm Room cache.
- Thứ tự sắp xếp: quá hạn → hạn hôm nay → `priority_score` giảm dần.
- "Quá hạn" tính theo `due_date` như hiện tại.
- Tải lại khi quay về màn (ON_RESUME, bỏ qua lần đầu).
- Chạm thẻ → mở Chi tiết lệnh (F09).

**Thay đổi:** header, thẻ lệnh, component, chữ, màu, 4 trạng thái dữ liệu, vị trí chuông thông báo.

---

## 2. Vấn đề của màn hiện tại

| # | Vấn đề | Hậu quả |
|---|---|---|
| 1 | Hero gradient navy + "sheet" bo góc chồng lên hero | Trái vNext (không gradient, header tab gốc là `surface`); code phải canh toạ độ với chuông nổi |
| 2 | Chuông là lớp nổi chung của `NavGraph`, đè lên header | Mỗi lần đổi header phải canh lại; không cùng hệ với top bar |
| 3 | Tab Sửa chữa/Kiểm tra cao **40dp**, không có semantics tab | Dưới 48dp; TalkBack không biết tab nào đang chọn |
| 4 | Thẻ chỉ có mã lệnh + mã xã + hạn | Không biết lệnh là việc gì, làm ngày nào, có mấy sự cố, dù API đã trả `title`, `scheduled_date`, `fault_ids` |
| 5 | `.clickable` đặt trước `.background(shape)`, không `clip`, không `role` | Ripple vuông tràn góc bo; TalkBack không đọc là nút |
| 6 | Badge mượn màu chéo miền: "Quá hạn" dùng màu đèn hỏng (`AssetCondition.OUT`), "Đã nộp · chờ đồng bộ" dùng màu `ASSIGNED` | Sai quy tắc "mỗi miền trạng thái có token riêng" |
| 7 | Rỗng có `action = {}`; Lỗi hiện nguyên văn `e.message` | Rỗng không có lối thoát; Lỗi lộ câu kỹ thuật |
| 8 | Dùng `*TextStyle` cũ (`DisplayTextStyle`, `LabelTextStyle`…), chữ trắng alpha 0.75 trên gradient | Không theo 15 slot M3 |
| 9 | Lọc/sắp xếp chạy trong composable | Trái quy tắc Compose #20; không unit test được |

---

## 3. Bố cục mới

```
┌ LuxTabTopBar ─────────────────────────────┐
│ Việc hôm nay                         🔔³  │  headlineMedium + chuông có badge
│ Xin chào, {tên} · Thứ Hai, 16/11/2026     │  bodySmall
├───────────────────────────────────────────┤
│ 5 Tổng nhiệm vụ │ 1 Quá hạn │ 2 Đang làm  │  thanh số liệu
│ [ Sửa chữa  3 | Kiểm tra  2 ]             │  LuxSegmented, 48dp, Role.Tab
│ (banner dữ liệu đã lưu, nếu có)           │  LuxBanner
│ ┌ thẻ lệnh ─────────────────────────────┐ │
│ │ ...                                   │ │  LazyColumn, key = work_order_id
└───────────────────────────────────────────┘
```

- **Top bar:** `LuxTabTopBar(title = "Việc hôm nay", subtitle = "Xin chào, {tên} · {thứ, ngày}")`, nền `surface`. Tên lấy từ `AuthRepository.observeUsername()` như hiện tại; không có tên thì "Xin chào". **Cùng một bar cho cả 4 trạng thái dữ liệu.**
- **Chuông:** nằm trong `LuxTabTopBar`. `NavGraph` ẩn `NotificationBellBar` khi route là Home (giống Bản đồ), truyền `unreadNotificationCount` và hành động mở `Routes.Notification` vào `HomeRoute`. Khảo sát và Cá nhân **giữ nguyên** chuông nổi cho tới khi được redesign.
- **Thanh số liệu:** giữ 3 số. "Tổng nhiệm vụ" đếm từ danh sách đang hiển thị (Sửa chữa + Kiểm tra), không cộng từ `HomeMetrics`. Số "Quá hạn" > 0 dùng `LuxTheme.colors.dueOverdue`. Số đặt trong ô độ rộng tối thiểu cố định (Be Vietnam Pro không có `tnum`).
- **Tab:** `LuxSegmented` với 2 lựa chọn, mỗi lựa chọn có nhãn + số đếm, mặc định Sửa chữa, `rememberSaveable`.
- **Lề màn:** 16dp.

---

## 4. Thẻ lệnh

```
┌ LuxCard (role Button) ─────────────────────┐
│ [icon loại]  Sửa chữa · WO-1042  [badge]    │  labelLarge / StatusBadge
│              Sửa đèn tuyến liên xã Phước…   │  titleMedium, tối đa 2 dòng
│              3 sự cố · COM-001              │  bodyMedium, onSurfaceVariant
├─────────────────────────────────────────────┤
│ 📅 Làm: 17/11      ⏰ Hạn: 20/11          ›  │  bodyMedium, ≥48dp
└─────────────────────────────────────────────┘
```

- **Dòng chính:** `title` thật. Mã xã (`commune_id`) xuống dòng phụ, cùng số sự cố (`fault_ids.size`). Không có sự cố thì chỉ hiện mã xã.
- **Ngày làm / Hạn:** hiện **đúng ngày có trong dữ liệu** (`scheduled_date`, `due_date`). Thiếu ngày nào thì bỏ phần đó; thiếu cả hai thì dòng ngày hiện "Chưa có lịch". **Không** tự suy nhãn "làm được hôm nay" (luật cửa sổ lịch chỉ áp dụng ở F09).
- **Màu hạn:** `dueOverdue` (quá hạn) / `dueToday` (hôm nay) / `onSurfaceVariant` (tương lai). Luôn có icon + chữ ("Quá hạn 18/11", "Hạn hôm nay"), không chỉ màu.
- **Badge trạng thái** (một badge, theo thứ tự ưu tiên):
  1. Đã nộp trên thiết bị, chưa đồng bộ → "Đã nộp · chờ đồng bộ", màu `LuxTheme.status.sync[QUEUED_ONLINE]` (nhãn "Chờ đồng bộ" của bảng đồng bộ; Home không biết trạng thái mạng của từng thao tác nên không tách "Chờ mạng").
  2. Quá hạn → "Quá hạn", nền `colorScheme.errorContainer` + chữ/icon `LuxTheme.colors.dueOverdue` (Light `#B91C1C`/`#FEE2E2`, Night Field `#FF9A9A`/`#4A1717`), icon `EventBusy`. Đây là ngữ nghĩa lỗi/hạn, không mượn màu trạng thái đèn.
  3. Còn lại → nhãn `wo_status` (`LuxTheme.status.workOrder`).
- **Viền:** `outlineVariant` cho mọi thẻ. Không đổi viền đỏ khi quá hạn (tín hiệu đã có ở badge + dòng hạn).
- **Chạm:** cả thẻ là một nút ("Mở lệnh WO-1042"), ripple theo góc bo 12dp.

---

## 5. Bốn trạng thái dữ liệu

| Trạng thái | Hiện tại | Đề xuất |
|---|---|---|
| **Đang tải** | Skeleton (ngay dưới hero) | `LuxTabTopBar` + `LuxLoadingRegion`: skeleton thanh số liệu, tab, 3 thẻ. TalkBack đọc "Đang tải việc được giao" |
| **Có dữ liệu** | Như mục 3 | Như mục 3–4 |
| **Rỗng** (không có lệnh đang mở nào) | Icon + chữ, `action = {}` | `LuxEmptyState`: "Chưa có việc được giao · Khi Kỹ sư bảo trì giao lệnh sửa chữa hoặc kiểm tra, lệnh sẽ hiện ở đây." + nút **"Làm mới"** |
| **Lỗi** | Hiện `e.message` + "Kiểm tra kết nối mạng" | `LuxErrorState(kind = NETWORK hoặc GENERIC)` có **"Thử lại"**; dùng nội dung mặc định theo loại lỗi, không hiện câu lỗi kỹ thuật |
| Rỗng theo tab | Thông điệp riêng từng tab | Giữ nội dung, đổi sang `LuxEmptyState` dạng nhỏ (không nút) |

`isStale` hiện luôn `false` (chưa có Room cache cho Home). Vẫn giữ nhánh banner `LuxBanner` "Đang xem dữ liệu đã lưu, cập nhật lúc …" để khi có cache thì dùng được ngay.

---

## 6. Giao diện sáng và Night Field

- Theo cài đặt hệ thống (CLAUDE.md: F02 không ép Dark). Mọi màu lấy từ token.
- Kiểm tra riêng ở Night Field: card `cardContainer` trên `background`, viền `outlineVariant`; chữ hạn `dueOverdue`/`dueToday` đạt ≥4.5:1 (đã khoá ở `ExtendedColorsTest`).

---

## 7. Ánh xạ component

| Hiện tại | Đề xuất |
|---|---|
| `HomeHeader` (gradient, chữ trắng) + chuông nổi `NotificationBellBar` | `LuxTabTopBar` có chuông |
| `Box` offset −24dp bo góc trên | Bỏ |
| `SummaryStatsRow` | Giữ cấu trúc, đổi sang slot chữ M3 + token |
| `TaskFilterTabs` (40dp tự dựng) | `LuxSegmented` |
| `TaskRow` | `LuxCard` + `StatusBadge` |
| `TaskStatusBadge` (mượn màu) | Badge theo mục 4 |
| `LoadingSkeletonState` + `SkeletonBar` | `LuxLoadingRegion` + `LuxSkeleton` |
| `EmptyState`, `ErrorState` (`MessageState`) | `LuxEmptyState`, `LuxErrorState` |
| `TabEmptyState` | `LuxEmptyState` dạng nhỏ |
| `InlineBanner` (stale) | `LuxBanner` |

---

## 8. Thay đổi ngoài giao diện (nhỏ)

1. **`WorkOrderSummaryItem`** thêm `title: String`, `scheduledDate: String?`, `faultCount: Int`, map từ `WorkOrderItemDto` có sẵn (`title`, `scheduled_date`, `fault_ids`). Cập nhật `FakeHomeRepository`.
2. **Lọc/sắp xếp** chuyển khỏi composable thành hàm thuần (`HomeTaskList.kt`): danh sách hiển thị, số đếm theo tab, danh sách đã sắp xếp theo tab. Có unit test. Logic giữ nguyên.
3. **`HomeUiState.Error`** thêm `isNetworkError: Boolean`, suy ra từ `IOException` trong `HomeViewModel`. Giữ `message` cho log. `retry()` đã huỷ job cũ trước khi tải lại, không cần sửa.
4. **`NavGraph`**: ẩn chuông nổi trên Home; `HomeRoute` nhận `unreadNotificationCount` + `onOpenNotifications`. Không đổi `NotificationBadgeViewModel` hay polling.

Không đổi repository Real (ngoài mapping 3 field), API, Room, sync.

---

## 9. Cấu trúc code, test và chia commit

```
feature/home/ui/
  HomeScreen.kt          khung màn: top bar, 4 trạng thái
  HomeTaskList.kt        lọc/sắp xếp/đếm (hàm thuần)
  HomeTaskCard.kt        thẻ lệnh + badge
```

**Test:**
- Unit test mới cho `HomeTaskList.kt` (loại bỏ survey/pole_gps, đếm tab, thứ tự sắp xếp).
- `RealHomeRepositoryTest`: map đúng `title`, `scheduledDate`, `faultCount`.
- `HomeViewModelTest`: lỗi mạng → `isNetworkError = true`; lỗi khác → `false`.
- `HomeScreenTest` (androidTest): cập nhật theo chữ mới; thêm "Lỗi có Thử lại, không lộ câu kỹ thuật", "Rỗng có Làm mới", "thẻ hiện tiêu đề, số sự cố, ngày làm, hạn", "thiếu ngày thì ẩn", "tab có role Tab + selected", "chuông mở thông báo".
- Test cỡ chữ 1.5× trên màn 360dp: tab và thẻ vẫn ≥48dp, không cắt chữ.
- Chạy trên máy thật SM-A075F.

**Chia commit (ước tính, mỗi commit ≤400 dòng):**

| # | Nội dung |
|---|---|
| 1 | Model + mapping 3 field + hàm lọc/sắp xếp thuần + unit test |
| 2 | `HomeUiState.Error.isNetworkError` + test ViewModel |
| 3 | `LuxTabTopBar` + chuông vào Home, `NavGraph` ẩn chuông nổi trên Home |
| 4 | Thanh số liệu + `LuxSegmented` + thẻ lệnh mới |
| 5 | Bốn trạng thái dữ liệu (skeleton, rỗng, lỗi, banner) |
| 6 | Cập nhật `HomeScreenTest` + test cỡ chữ lớn, chạy trên máy; xoá code cũ không còn dùng |

Mỗi bước dừng để bạn xem, như các giai đoạn trước.

---

## 10. Quyết định của chủ dự án (2026-11-16)

| # | Câu hỏi | Quyết định | Ghi chú |
|---|---|---|---|
| **Q1** | Tổ chức danh sách: giữ tab theo loại, hộp việc thống nhất (P-11), hay thêm tab khảo sát/GPS? | **Giữ tab theo loại** | Không đổi IA lần thứ 3. P-11 (hộp việc thống nhất) để lại, không thuộc đợt này |
| **Q2** | Thẻ lệnh hiện gì? | **Tiêu đề + số sự cố + ngày làm + hạn** | Chỉ hiện ngày thật, không tự suy "làm được hôm nay" |
| **Q3** | Chuông thông báo đặt ở đâu? | **Trong `LuxTabTopBar`** | `NavGraph` ẩn chuông nổi trên Home; là mẫu cho Khảo sát/Cá nhân sau này |
