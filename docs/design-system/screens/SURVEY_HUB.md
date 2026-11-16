# Đề xuất redesign: Hub Khảo sát

> **Trạng thái:** **CHỜ DUYỆT BẢN VIẾT.** Hướng đã thống nhất 2026-11-17 (H1–H4, mục 9). Chưa có code nào thay đổi.
> **Màn:** `feature/survey/ui/hub/SurveyHubScreen.kt` (650 dòng), route `survey?highlightWorkOrderId=…`, tab 2 của bottom nav.
> **Căn cứ:** `docs/design-system/AUDIT_REPORT.md` (mục 6.3, P-11, P-12), `docs/design-system/vNext/`, code hiện tại (đọc 2026-11-17), cách đã làm ở F02 (`F02_HOME.md`) và F09 (`F09_WORK_ORDER_DETAIL.md`).

---

## 1. Mục tiêu và giới hạn

**Mục tiêu:** Kỹ sư hiện trường mở tab Khảo sát và trả lời nhanh được:
1. **Tôi đang dở việc khảo sát / đo GPS cột nào?**
2. **Việc nào tôi bắt đầu được hôm nay, việc nào chưa tới lịch hoặc đã quá lịch?**
3. **Tuyến nào tôi đã nộp nhưng chưa đồng bộ?**

**Giữ nguyên:**
- Nguồn dữ liệu `SurveyRepository.observeAssignedRoutes()` (`task_kind=survey`) và `observeAssignedPoleGpsOrders()` (`task_kind=pole_gps_update`), cache khi offline (`isStale`).
- "Bắt đầu" gọi `POST /work-orders/{id}/start` rồi mở danh sách cột (luồng `startedRoute`). "Chụp ảnh/video từng cột" mở danh sách cột. Thẻ đo GPS cột mở F09.
- Luồng xin hủy (sheet 4 lý do + ghi chú), trạng thái "đã gửi yêu cầu hủy" chỉ trong phiên.
- Highlight + tự cuộn khi mở từ thông báo / từ F09.
- Tải lại khi quay về màn.

**Thay đổi:** header, cách nhóm danh sách, thẻ, cửa sổ lịch cho nút trên thẻ, nhóm "Chờ đồng bộ", 4 trạng thái dữ liệu, bỏ lối vào "Cột thiếu GPS".

---

## 2. Vấn đề của màn hiện tại

| # | Vấn đề | Hậu quả |
|---|---|---|
| 1 | Hero navy + chuông nổi đè (`NotificationBellBar onDarkHero`) | Trái vNext; khác Home đã chuyển sang `LuxTabTopBar` |
| 2 | Ép `colorScheme.copy(primary = Navy700)` cho cả màn; `Color(0x…)` cho ô icon tuyến; `*TextStyle` cũ | Sai màu ở Night Field, vi phạm quy tắc token |
| 3 | 3 loại nội dung không cùng cấp trong 1 list: ô "Cột thiếu GPS", thẻ đo GPS cột, thẻ tuyến | Khó quét; "Cột thiếu GPS" là bản trùng của lệnh đo GPS cột (P-12) |
| 4 | Thẻ đo GPS cột luôn nằm trên, không theo trạng thái | Việc đang dở bị đẩy xuống dưới |
| 5 | "Bắt đầu" / "Yêu cầu hủy" trên thẻ bấm được bất kỳ lúc nào | Lệch luật cửa sổ lịch của F09 (CLAUDE.md, 2026-11-09) |
| 6 | Tuyến đã "Hoàn tất" trên máy bị ẩn ngay, kể cả khi chưa đồng bộ | Không biết tuyến nào đang chờ / lỗi đồng bộ |
| 7 | `items(...)` không có `key`; vị trí cuộn highlight tính tay theo chỉ số | Dễ nháy, dễ cuộn sai khi danh sách đổi |
| 8 | Lỗi hiện nguyên văn `e.message`; Rỗng không có hành động | Lộ câu kỹ thuật, không có lối thoát |

---

## 3. Bố cục mới

```
┌ LuxTabTopBar ─────────────────────────────┐
│ Khảo sát                             🔔³  │  headlineMedium + chuông có badge
│ Thứ Hai, 17/11/2026                       │  bodySmall
├───────────────────────────────────────────┤
│ (banner dữ liệu đã lưu / lỗi bắt đầu)     │  LuxBanner
│ Đang làm · 2                              │  LuxSectionHeader + số
│  [thẻ tuyến khảo sát] [thẻ đo GPS cột]    │
│ Chưa bắt đầu · 3                          │
│ Chờ đồng bộ · 1                           │
└───────────────────────────────────────────┘
```

- **Top bar:** `LuxTabTopBar(title = "Khảo sát", subtitle = "{Thứ…, dd/MM/yyyy}")` có chuông. `NavGraph` ẩn `NotificationBellBar` trên Survey (giống Home), truyền số chưa đọc + mở thông báo vào `SurveyHubRoute`. Cá nhân giữ chuông nổi tới khi redesign.
- **Một danh sách, nhóm theo trạng thái (H1):** tuyến khảo sát và lệnh đo GPS cột trộn chung.
  - **Đang làm:** `wo_status = in_progress`, chưa nộp trên máy.
  - **Chưa bắt đầu:** các trạng thái còn lại (thường `assigned`), chưa nộp trên máy.
  - **Chờ đồng bộ:** đã nộp trên máy, chưa đồng bộ (mục 5).
  - Nhóm rỗng thì ẩn tiêu đề nhóm. Trong mỗi nhóm giữ thứ tự server trả về, tuyến trước rồi đến lệnh đo GPS.
- **Bỏ ô "Cột thiếu GPS" (H2).** Route/màn `polelocation` giữ nguyên trong code; xoá ở PR riêng.
- `LazyColumn` có `key = workOrderId`. Cuộn tới thẻ highlight bằng chỉ số tìm theo `key` trong danh sách đã dựng, không cộng tay theo banner.
- Lề 16dp.

---

## 4. Thẻ

**Thẻ tuyến khảo sát** (`LuxCard` không clickable cả thẻ — hành động là nút):

```
[🛣] Tuyến khảo sát                [● Đã giao]   labelLarge + StatusBadge (workOrder)
Khảo sát tuyến liên xã Phước Hiệp               titleMedium, xuống dòng tự do
Lịch khảo sát       17/11/2026 · Hôm nay         dòng nhãn – giá trị (như F09)
Mở vào 16/11                                     gợi ý khi nút khoá (bodySmall)
[ Yêu cầu hủy ]   [ Bắt đầu ]                    1 hàng, 48dp
```

- Icon + nhãn loại lấy từ `WorkOrderTaskKind` (`LuxTheme.status.kind`), không màu riêng.
- **Lịch khảo sát:** ngày `scheduled_date`; thêm "· Hôm nay" khi trùng hôm nay; "Chưa có lịch" khi null. Khi đã qua lịch: thêm dòng đỏ "Đã qua lịch" (giống V4 của F09).
- **Nút theo trạng thái:**

| Trạng thái | Nút | Điều kiện bấm được |
|---|---|---|
| `assigned`, chưa xin hủy | "Yêu cầu hủy" (viền đỏ) + "Bắt đầu" | Trong cửa sổ lịch (H3) |
| `assigned`, đã xin hủy (trong phiên) | Không nút; dòng "Đã gửi yêu cầu hủy · chờ duyệt" | — |
| `in_progress` | "Chụp ảnh/video từng cột" | Luôn (giữ như hiện tại) |
| Đang gọi start | "Đang bắt đầu…" (khoá) | — |

- **Cửa sổ lịch (H3):** dùng chung `workOrderScheduleWindow(scheduled_date, today)` của F09. Ngoài cửa sổ: "Bắt đầu" và "Yêu cầu hủy" khoá; gợi ý một dòng: chưa tới → "Mở vào dd/MM/yyyy"; đã qua → "Đã khoá do quá hạn". `scheduled_date` null → không áp luật.
- Highlight: viền 2dp `colorScheme.primary` (thay `Navy700`).

**Thẻ đo GPS cột** (`LuxCard` clickable, role Button, mở F09; chưa nộp):

```
[📍] Đo GPS cột                   [● Đang thực hiện]
Đo GPS 12 cột ấp 3                                   titleMedium
Lịch đo GPS        18/11/2026                    ›
```

**Thẻ chờ đồng bộ** (cả hai loại): như thẻ tương ứng, **không nút / không mở được**, badge là trạng thái đồng bộ thay cho `wo_status`.

---

## 5. Nhóm "Chờ đồng bộ" (H4)

- Nguồn: `WorkOrderCompletionRepository.observeCompletionSyncStates()` (đã có, Home dùng) — chỉ chứa bản ghi đã nộp, chưa `synced`, không gồm `draft`.
- Lệnh có trong map → nhóm "Chờ đồng bộ", badge theo trạng thái thật: "Chờ đồng bộ" (`sync[QUEUED_ONLINE]`) / "Đồng bộ lỗi" (`sync[FAILED]`) / "Xung đột" (`sync[CONFLICT]`).
- Lệnh đã đồng bộ xong (có trong `observeCompletedWorkOrderIds()` nhưng không còn trong map) → **ẩn** như hiện tại, cho tới khi server trả trạng thái mới.

---

## 6. Bốn trạng thái dữ liệu

| Trạng thái | Đề xuất |
|---|---|
| **Đang tải** | `LuxTabTopBar` + `LuxLoadingRegion` ("Đang tải việc khảo sát"): skeleton tiêu đề nhóm + 3 thẻ |
| **Có dữ liệu** | Mục 3–5 |
| **Rỗng** | `LuxEmptyState`: "Chưa có việc khảo sát được giao · Khi Kỹ sư bảo trì giao tuyến khảo sát hoặc lệnh đo GPS cột, lệnh sẽ hiện ở đây." + "Làm mới" |
| **Lỗi** | `LuxErrorState(kind)` + "Thử lại": mạng → "Không tải được việc khảo sát · Kiểm tra kết nối rồi thử lại."; khác → "Không tải được việc khảo sát · Vui lòng thử lại sau ít phút." Không hiện câu kỹ thuật. Thêm `isNetworkError` vào `SurveyHubUiState.Error` |

Banner dữ liệu đã lưu: `LuxBanner` INFO "Đang xem danh sách đã lưu trên máy, có thể chưa phải bản mới nhất". Lỗi bắt đầu: `LuxBanner` ERROR. Lỗi gửi yêu cầu hủy: giữ hiển thị trong sheet như hiện tại.

---

## 7. Thay đổi ngoài giao diện (nhỏ)

1. **Hàm thuần `SurveyHubList.kt`:** dựng danh sách nhóm từ (tuyến, lệnh đo GPS, map đồng bộ, tập đã hoàn thành) → các nhóm có thứ tự; tính trạng thái khoá theo lịch cho thẻ tuyến. Unit test.
2. **`SurveyHubViewModel`:** đọc thêm `observeCompletionSyncStates()`; `Error` có `isNetworkError` (`IOException`). Giữ `locallyCompletedWorkOrderIds` để ẩn lệnh đã đồng bộ; thêm `completionSyncStates`. `onRetry()` (Rỗng/Lỗi) dùng chung để "Làm mới".
3. **`NavGraph`:** ẩn chuông nổi trên Survey; `SurveyHubRoute` nhận `unreadNotificationCount` + `onOpenNotifications`.
4. Bỏ tham số/lối vào `onOpenPoleLocationList` khỏi `SurveyHubScreen` (route `PoleLocationList` trong NavGraph giữ, chỉ không còn lối vào từ Hub).

Không đổi API, Room, sync.

---

## 8. Cấu trúc code, test, chia commit

```
feature/survey/ui/hub/
  SurveyHubScreen.kt      khung: top bar, 4 trạng thái, danh sách nhóm
  SurveyHubList.kt        nhóm + khoá theo lịch (hàm thuần)
  SurveyHubCards.kt       thẻ tuyến, thẻ đo GPS cột, thẻ chờ đồng bộ
```

**Test:**
- Unit `SurveyHubListTest`: nhóm theo trạng thái trộn 2 loại; lệnh chờ đồng bộ sang nhóm 3; lệnh đã đồng bộ bị ẩn; khoá theo lịch (trước / trong / sau / không có lịch).
- `SurveyHubViewModelTest`: cập nhật; thêm `isNetworkError`, trạng thái đồng bộ.
- `SurveyHubScreenTest` (androidTest): top bar + chuông; nhóm; nút khoá + gợi ý "Mở vào…"/"Đã khoá do quá hạn"; nhóm Chờ đồng bộ không nút + badge; không còn "Cột thiếu GPS"; Rỗng có "Làm mới"; Lỗi có "Thử lại", không lộ câu kỹ thuật; highlight.
- Test cỡ chữ 1.5× ở 360dp: hai nút cùng hàng ≥48dp, tiêu đề tuyến hiện đủ.
- Chạy trên SM-A075F.

**Chia commit (≤400 dòng mỗi commit):**

| # | Nội dung |
|---|---|
| 1 | `SurveyHubList.kt` (nhóm + khoá lịch) + unit test |
| 2 | ViewModel: trạng thái đồng bộ + `isNetworkError` + test |
| 3 | `LuxTabTopBar` + chuông, `NavGraph` ẩn chuông nổi trên Survey, bỏ lối vào "Cột thiếu GPS" |
| 4 | Thẻ tuyến / đo GPS / chờ đồng bộ + danh sách nhóm có `key` + highlight |
| 5 | 4 trạng thái dữ liệu + banner |
| 6 | Test UI + cỡ chữ lớn, chạy máy, dọn code cũ |

---

## 9. Quyết định của chủ dự án (2026-11-17)

| # | Câu hỏi | Quyết định |
|---|---|---|
| H1 | Sắp xếp: theo trạng thái / theo loại / tab theo loại | **Nhóm theo trạng thái**, trộn tuyến khảo sát và đo GPS cột |
| H2 | Ô "Cột thiếu GPS" | **Bỏ khỏi Hub**; xoá code `polelocation` ở PR riêng |
| H3 | Cửa sổ lịch cho "Bắt đầu" / "Yêu cầu hủy" trên thẻ | **Áp luật như F09** (`workOrderScheduleWindow`) |
| H4 | Tuyến đã nộp trên máy | **Nhóm "Chờ đồng bộ"** với badge trạng thái đồng bộ thật |
