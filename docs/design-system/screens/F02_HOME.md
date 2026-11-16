# Đề xuất redesign: Việc hôm nay (F02)

> **Trạng thái:** **CHỜ DUYỆT BẢN VIẾT (lần 2).** Hướng thiết kế đã thống nhất 2026-11-16 (Q1–Q3); đã đưa 5 góp ý review R1–R5 vào (mục 10). Chưa có code nào thay đổi.
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
| 10 | Thanh số liệu và quy tắc rỗng dựa trên `HomeMetrics` (đếm cả lệnh khảo sát/đo GPS cột), còn danh sách thì đã lọc | Số liệu lệch danh sách; API chỉ có lệnh khảo sát thì màn "có dữ liệu" nhưng trống |
| 11 | Badge "Đã nộp · chờ đồng bộ" dựa trên việc **có** bản ghi hoàn thành, kể cả bản đã đồng bộ; không phân biệt lỗi/xung đột | Báo sai "chờ đồng bộ" sau khi đã gửi thành công; lỗi đồng bộ không hiện |

---

## 3. Bố cục mới

```
┌ LuxTabTopBar ─────────────────────────────┐
│ Việc hôm nay                         🔔³  │  headlineMedium + chuông có badge
│ Xin chào, {tên} · Thứ Hai, 16/11/2026     │  bodySmall
├───────────────────────────────────────────┤
│ 5 Tổng nhiệm vụ │ 1 Quá hạn │ 2 Đang làm  │  thanh số liệu
│ [ Sửa chữa  3 | Kiểm tra  2 ]             │  LuxSegmented, 48dp, RadioButton
│ (banner dữ liệu đã lưu, nếu có)           │  LuxBanner
│ ┌ thẻ lệnh ─────────────────────────────┐ │
│ │ ...                                   │ │  LazyColumn, key = work_order_id
└───────────────────────────────────────────┘
```

- **Top bar:** `LuxTabTopBar(title = "Việc hôm nay", subtitle = "Xin chào, {tên} · {thứ, ngày}")`, nền `surface`. Tên lấy từ `AuthRepository.observeUsername()` như hiện tại; không có tên thì "Xin chào". **Cùng một bar cho cả 4 trạng thái dữ liệu.**
- **Chuông:** nằm trong `LuxTabTopBar`. `NavGraph` ẩn `NotificationBellBar` khi route là Home (giống Bản đồ), truyền `unreadNotificationCount` và hành động mở `Routes.Notification` vào `HomeRoute`. Khảo sát và Cá nhân **giữ nguyên** chuông nổi cho tới khi được redesign.
- **Tập lệnh hiển thị (một nguồn duy nhất):** các lệnh có `task_kind` là `repair` hoặc `inspection`. Cả 3 số trên thanh số liệu, số đếm trên tab và việc xác định trạng thái rỗng (mục 5) **đều tính từ tập này**, không dùng `HomeMetrics` (vốn đếm cả lệnh khảo sát và đo GPS cột).
- **Thanh số liệu:** 3 số: Tổng nhiệm vụ (số lệnh trong tập), Quá hạn (lệnh trong tập có `due_date` trước hôm nay), Đang làm (lệnh trong tập có `wo_status = in_progress`). Số "Quá hạn" > 0 dùng `LuxTheme.colors.dueOverdue`. Số đặt trong ô độ rộng tối thiểu cố định (Be Vietnam Pro không có `tnum`).
- **Bộ chọn loại lệnh:** `LuxSegmented` (M3 `SegmentedButton` chọn một) với 2 lựa chọn, mỗi lựa chọn có nhãn + số đếm, mặc định Sửa chữa, `rememberSaveable`. **Semantics:** giữ đúng semantics có sẵn của component (`Role.RadioButton` + `selected`), không đổi sang `Role.Tab`; vNext chấp nhận cả hai cho bộ lọc này (`COMPONENT_SPECIFICATIONS.md`, `LuxSegmented`). Test kiểm tra đúng `RadioButton` + `selected`.
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
  1. **Trạng thái đồng bộ của báo cáo đã nộp trên thiết bị**, lấy từ dữ liệu thật (mục 8.2), không từ "có bản ghi hoàn thành hay không":
     - đang chờ (`submitStatus = pending`, op trong `sync_queue` là `queued`/`syncing`) → "Đã nộp · chờ đồng bộ", màu `sync[QUEUED_ONLINE]` (Home không biết trạng thái mạng từng thao tác nên không tách "Chờ mạng");
     - op `failed` → "Đồng bộ lỗi", màu `sync[FAILED]`, icon lỗi;
     - op `conflict` → "Xung đột", màu `sync[CONFLICT]`, icon cảnh báo;
     - `submitStatus = synced` → **không** hiện badge đồng bộ; rơi xuống quy tắc 2–3.
  2. Quá hạn → "Quá hạn", nền `colorScheme.errorContainer` + chữ/icon `LuxTheme.colors.dueOverdue` (Light `#B91C1C`/`#FEE2E2`, Night Field `#FF9A9A`/`#4A1717`), icon `EventBusy`. Đây là ngữ nghĩa lỗi/hạn, không mượn màu trạng thái đèn.
  3. Còn lại → nhãn `wo_status` (`LuxTheme.status.workOrder`).
- **Viền:** `outlineVariant` cho mọi thẻ. Không đổi viền đỏ khi quá hạn (tín hiệu đã có ở badge + dòng hạn).
- **Chạm:** cả thẻ là một nút ("Mở lệnh WO-1042"), ripple theo góc bo 12dp, vùng chạm ≥48dp.
- **Xuống dòng ở 360dp và cỡ chữ lớn** (không cắt mất nội dung):
  - Dòng đầu: icon loại + "Sửa chữa · WO-1042" bên trái, badge bên phải. Khi không đủ chỗ, **badge xuống nguyên một hàng riêng** dưới dòng đầu (không co chữ badge, không ellipsis).
  - Mã lệnh không bị cắt; tiêu đề tối đa 2 dòng rồi ellipsis (mã lệnh vẫn đủ để nhận diện).
  - Dòng ngày: "Làm" và "Hạn" nằm cùng hàng; khi không đủ chỗ, **xếp dọc** thành 2 hàng. Chữ ngày hạn không bao giờ bị cắt.
  - Ưu tiên giữ nguyên vẹn khi chật: **mã lệnh, badge đồng bộ, ngày hạn**.

---

## 5. Bốn trạng thái dữ liệu

| Trạng thái | Hiện tại | Đề xuất |
|---|---|---|
| **Đang tải** | Skeleton (ngay dưới hero) | `LuxTabTopBar` + `LuxLoadingRegion`: skeleton thanh số liệu, tab, 3 thẻ. TalkBack đọc "Đang tải việc được giao" |
| **Có dữ liệu** | Như mục 3 | Như mục 3–4 |
| **Rỗng** (tập lệnh hiển thị trống) | Icon + chữ, `action = {}` | `LuxEmptyState`: "Chưa có việc được giao · Khi Kỹ sư bảo trì giao lệnh sửa chữa hoặc kiểm tra, lệnh sẽ hiện ở đây." + nút **"Làm mới"** |
| **Lỗi** | Hiện `e.message` + "Kiểm tra kết nối mạng" | `LuxErrorState(kind = …)` có **"Thử lại"** ở cả hai loại, với **nội dung riêng cho Home** (không dùng câu mặc định của component, vì câu đó nói về dữ liệu đã lưu mà Home chưa có cache): mạng → "Không tải được danh sách lệnh · Kiểm tra kết nối rồi thử lại."; khác → "Không tải được danh sách lệnh · Vui lòng thử lại sau ít phút." Không hiện câu lỗi kỹ thuật |
| Rỗng theo tab | Thông điệp riêng từng tab | Giữ nội dung, đổi sang `LuxEmptyState` dạng nhỏ (không nút) |

**Quy tắc rỗng (thay đổi so với hiện tại):** hiện `HomeViewModel` kết luận `Empty` bằng `HomeMetrics` **trước khi lọc** loại lệnh, nên khi API chỉ trả lệnh khảo sát/đo GPS cột, màn hiện "Có dữ liệu" với cả hai tab trống và thanh số liệu đếm cả lệnh không hiển thị. Sau thay đổi:
- Tập lệnh hiển thị trống (kể cả khi API có trả lệnh `survey`/`pole_gps_update`) → **Rỗng toàn màn**.
- Tập có lệnh nhưng một tab không có lệnh nào → "Có dữ liệu", tab đó hiện **rỗng theo tab**.

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
2. **Badge đồng bộ theo trạng thái thật:** `WorkOrderCompletionRepository` thêm `observeCompletionSyncStates(): Flow<Map<String, CompletionSyncState>>` (`PENDING` / `FAILED` / `CONFLICT`, theo `work_order_id`). Cài đặt Real: đọc các bản ghi hoàn thành có `submitStatus != synced`, ghép với trạng thái op trong `sync_queue` qua `clientOpId` (`SyncQueueDao.observeStatusesFor`, đã có). Bản ghi `synced` không có trong map. **Lý do:** `observeCompletedWorkOrderIds()` trả mọi bản ghi đã nộp, kể cả đã `synced`, nên Home sẽ hiện "chờ đồng bộ" cả sau khi gửi thành công; còn `failed`/`conflict` không ghi vào `submitStatus` (chỉ có `pending`/`synced`) mà chỉ nằm ở `sync_queue`. `observeCompletedWorkOrderIds()` **giữ nguyên** vì Survey Hub vẫn dùng theo nghĩa khác. `HomeUiState.Success.locallyCompletedWorkOrderIds` đổi thành `completionSyncStates`.
3. **Lọc/sắp xếp/đếm/rỗng** chuyển khỏi composable thành hàm thuần (`HomeTaskList.kt`): tập lệnh hiển thị, 3 số liệu, số đếm theo tab, danh sách đã sắp xếp theo tab. `HomeViewModel` xác định `Empty` theo **tập lệnh hiển thị** (mục 5), không theo `HomeMetrics`. Có unit test. Thứ tự sắp xếp giữ nguyên.
4. **`HomeUiState.Error`** thêm `isNetworkError: Boolean`, suy ra từ `IOException` trong `HomeViewModel`. Giữ `message` cho log. `retry()` đã huỷ job cũ trước khi tải lại, không cần sửa.
5. **`NavGraph`**: ẩn chuông nổi trên Home; `HomeRoute` nhận `unreadNotificationCount` + `onOpenNotifications`. Không đổi `NotificationBadgeViewModel` hay polling.

Không đổi API, schema Room, hay luồng sync (chỉ đọc thêm trạng thái `sync_queue` đã có).

---

## 9. Cấu trúc code, test và chia commit

```
feature/home/ui/
  HomeScreen.kt          khung màn: top bar, 4 trạng thái
  HomeTaskList.kt        lọc/sắp xếp/đếm (hàm thuần)
  HomeTaskCard.kt        thẻ lệnh + badge
```

**Test:**
- Unit test mới cho `HomeTaskList.kt`: loại bỏ survey/pole_gps; 3 số liệu chỉ đếm Sửa chữa + Kiểm tra; đếm tab; thứ tự sắp xếp.
- `HomeViewModelTest`:
  - API chỉ trả lệnh `survey`/`pole_gps_update` → `Empty` toàn màn.
  - Có lệnh sửa chữa, không có lệnh kiểm tra → `Success` (tab Kiểm tra rỗng ở UI).
  - Lỗi mạng → `isNetworkError = true`; lỗi khác → `false`.
  - Trạng thái đồng bộ đổi `PENDING` → không còn trong map (sau `synced`) → `Success` cập nhật, badge biến mất.
- `RealHomeRepositoryTest`: map đúng `title`, `scheduledDate`, `faultCount`.
- Test repository (`RealWorkOrderCompletionRepository`): `pending` + op `queued` → `PENDING`; op `failed` → `FAILED`; op `conflict` → `CONFLICT`; chuyển `pending` → `synced` → bản ghi rời khỏi map.
- `HomeScreenTest` (androidTest): cập nhật theo chữ mới; thêm "Lỗi mạng/khác có Thử lại và đúng câu của Home, không lộ câu kỹ thuật", "Rỗng có Làm mới", "thẻ hiện tiêu đề, số sự cố, ngày làm, hạn", "thiếu ngày thì ẩn", "badge chờ / lỗi / xung đột / không badge khi synced", "bộ chọn có RadioButton + selected", "chuông mở thông báo".
- Test cỡ chữ 1.5× trên màn 360dp (tự động): bộ chọn và thẻ ≥48dp; mã lệnh, badge đồng bộ, ngày hạn hiển thị đủ.
- **Kiểm tra thủ công ở 200%** (theo `vNext/ACCESSIBILITY.md` mục 8): ưu tiên mã lệnh, badge đồng bộ, ngày hạn; badge xuống hàng, hai ngày xếp dọc.
- Chạy trên máy thật SM-A075F.

**Chia commit (ước tính, mỗi commit ≤400 dòng):**

| # | Nội dung |
|---|---|
| 1 | Model + mapping 3 field + hàm lọc/sắp xếp/đếm thuần + quy tắc rỗng mới trong ViewModel + unit test |
| 2 | `observeCompletionSyncStates()` + `HomeUiState` dùng trạng thái đồng bộ thật + `isNetworkError` + test repository và ViewModel |
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

### Góp ý review bản viết (2026-11-16), đã đưa vào đề xuất

| # | Góp ý | Xử lý |
|---|---|---|
| R1 | Số liệu và trạng thái rỗng phải dùng cùng một tập lệnh (Sửa chữa + Kiểm tra); ViewModel hiện kết luận rỗng bằng `HomeMetrics` trước khi lọc | Mục 3, 5, 8.3; thêm test 2 trường hợp |
| R2 | Badge đồng bộ phải theo trạng thái thật, không theo "có bản ghi"; nhãn riêng cho lỗi/xung đột; bỏ khi đã đồng bộ | Mục 4, 8.2; test `pending` → `synced`. Đã kiểm tra code: `failed`/`conflict` chỉ ở `sync_queue`, không ở `submitStatus` |
| R3 | Chốt semantics bộ chọn | Giữ `LuxSegmented` với `RadioButton` + `selected` (mục 3); test đúng semantics đó |
| R4 | Quy định xuống dòng ở 360dp và cỡ chữ lớn | Mục 4; test tự động 1.5× + kiểm tra thủ công 200% (mục 9) |
| R5 | Câu lỗi riêng cho Home, vì câu mặc định nói tới dữ liệu đã lưu | Mục 5 |
