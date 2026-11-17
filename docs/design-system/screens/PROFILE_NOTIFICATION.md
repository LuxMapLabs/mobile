# Đề xuất redesign: Cá nhân (F15) và Thông báo (F14)

> **Trạng thái:** **ĐÃ DUYỆT VÀ ĐÃ CODE XONG** trên nhánh `style/redesign-profile-notification-vnext` (2026-11-17). Còn nợ: kiểm tra tay trên máy đã đăng nhập (Light/Night Field, 200%).
> **Màn:** `feature/profile/ui/ProfileScreen.kt` (575 dòng, tab 4) và `feature/notification/ui/NotificationListScreen.kt` (632 dòng, mở từ chuông).
> **Căn cứ:** `docs/design-system/AUDIT_REPORT.md` (mục 6.10, 6.11), `docs/design-system/vNext/`, code hiện tại (đọc 2026-11-17), cách đã làm ở F02, F09 và Hub Khảo sát.

---

## 1. Mục tiêu và giới hạn

**Cá nhân:** kỹ sư biết mình đăng nhập là ai, phụ trách địa bàn nào, máy có đang online và còn dữ liệu chưa đồng bộ không, rồi đăng xuất an toàn.

**Thông báo:** kỹ sư đọc nhanh thông báo mới, biết dòng nào mở được màn chi tiết, đánh dấu đã đọc.

**Giữ nguyên:**
- Dữ liệu hồ sơ từ `GET /auth/me`; số đồng bộ (`pendingCount`, `errorCount`, `lastSyncedAt`) từ `SyncQueueManager`; trạng thái mạng; luồng đăng xuất (hộp thoại xác nhận, cảnh báo còn mục chưa đồng bộ).
- 4 endpoint thông báo (BE-27), polling 45 giây, bộ lọc Tất cả/Chưa đọc tính tại máy, phân trang, nhóm theo Hôm nay / Hôm qua / Trước đó, `title`/`body` hiển thị nguyên văn.
- Luật điều hướng thông báo: chỉ `entity_type = "work_order"` mở màn (lệnh khảo sát → Hub có highlight, lệnh khác → Chi tiết lệnh). Bấm bất kỳ dòng nào cũng đánh dấu đã đọc.

**Thay đổi:** header, chuông, bố cục thẻ, cách trình bày trạng thái làm việc, bộ lọc, dấu hiệu dòng mở được, 4 trạng thái dữ liệu.

---

## 2. Vấn đề của màn hiện tại

| # | Màn | Vấn đề | Hậu quả |
|---|---|---|---|
| 1 | Cá nhân | Hero navy gradient + chuông nổi (`NotificationBellBar onDarkHero`) | Trái vNext; là tab cuối còn dùng chuông nổi |
| 2 | Cá nhân | Nhãn mục viết HOA ("TÀI KHOẢN", "SẴN SÀNG LÀM VIỆC"); màu primitive (`Success600`, `AttentionColor`) | Trái quy tắc chữ/token vNext |
| 3 | Cá nhân | Dòng tổng "Sẵn sàng / N mục cần kiểm tra" chỉ là chấm màu + chữ suy ra từ 2 dòng bên dưới | Thông tin lặp, phân biệt bằng màu |
| 4 | Cá nhân | Lỗi không có "Thử lại" (ViewModel không có hàm thử lại), hiện nguyên văn câu lỗi; Đang tải chỉ có vòng xoay | Kẹt khi lỗi mạng |
| 5 | Thông báo | Hero gradient riêng; "Đọc tất cả" 13sp | Trái vNext; chữ hành động quá nhỏ |
| 6 | Thông báo | Chip lọc khi chọn ép `Navy700` | Sai ở Night Field |
| 7 | Thông báo | Dòng sự cố/phản ánh bấm vào không đi đâu, nhìn giống dòng mở được | Người dùng tưởng app bị treo |
| 8 | Thông báo | Màu ô icon riêng cho từng loại (`tileColors`) | Không thuộc token |

---

## 3. Màn Cá nhân

```
┌ LuxTabTopBar ──────────────────────────┐
│ Cá nhân                           🔔³  │
├────────────────────────────────────────┤
│ ┌ thẻ hồ sơ ─────────────────────────┐ │
│ │ [NT] Nguyễn Văn Thịnh              │ │  avatar chữ cái đầu (primaryContainer)
│ │      @kysu1 · Tổ khảo sát/sửa chữa │ │  bodyMedium
│ │ Email          kysu1@luxmap.vn     │ │  dòng nhãn – giá trị, xuống dòng khi chật
│ │ Địa bàn phụ trách · 2              │ │
│ │ [COM-001] [COM-002]                │ │
│ └────────────────────────────────────┘ │
│ Tình trạng làm việc                    │  LuxSectionHeader
│ ┌────────────────────────────────────┐ │
│ │ 📶 Kết nối mạng        Trực tuyến  │ │  icon + chữ
│ │ 🔄 Đồng bộ dữ liệu                 │ │
│ │    Chờ 2 · Lỗi 1 · Lần cuối 10:24  │ │  bodyMedium; "Lỗi N" dùng error khi N>0
│ └────────────────────────────────────┘ │
│ [ Đăng xuất ]                          │  LuxDestructiveButton(filled = false), 48dp
│ Còn 2 mục chưa đồng bộ trên máy này    │  chỉ khi pending > 0
│ SM-A075F · Phiên bản 1.0               │  bodySmall, căn giữa
└────────────────────────────────────────┘
```

- **Top bar:** `LuxTabTopBar(title = "Cá nhân")` có chuông. `NavGraph` bỏ hẳn `NotificationBellBar` (Cá nhân là tab cuối còn dùng) và xoá component đó cùng test của nó nếu không còn nơi dùng.
- **Tình trạng làm việc (T3):** bỏ dòng tổng. 2 dòng trạng thái thật:
  - Kết nối mạng: "Trực tuyến" (icon Wifi) / "Ngoại tuyến" (icon WifiOff, chữ `error`).
  - Đồng bộ dữ liệu: "Chờ N · Lỗi N · Lần cuối HH:mm" — bỏ phần nào bằng 0; không có gì → "Đã đồng bộ · Lần cuối …"; chưa từng đồng bộ → "Chưa đồng bộ lần nào".
- **Đăng xuất:** giữ hộp thoại xác nhận, chuyển sang `LuxConfirmDialog` (nút xác nhận kiểu huỷ/đỏ). Cảnh báo "Còn N mục chưa đồng bộ trên máy này" giữ nguyên.
- **Bốn trạng thái:**
  - Đang tải: `LuxLoadingRegion` ("Đang tải thông tin cá nhân") skeleton thẻ hồ sơ + thẻ tình trạng.
  - Có dữ liệu: như trên.
  - Không có trạng thái Rỗng (hồ sơ của chính mình).
  - Lỗi: `LuxErrorState(kind)` + "Thử lại": mạng → "Không tải được thông tin cá nhân · Kiểm tra kết nối rồi thử lại."; khác → "… · Vui lòng thử lại sau ít phút." Bên dưới vẫn có thẻ Tình trạng làm việc + nút Đăng xuất, để kỹ sư không bị kẹt khi không tải được hồ sơ.

---

## 4. Màn Thông báo

```
┌ LuxChildTopBar ────────────────────────┐
│ ← Thông báo              [Đọc tất cả]  │  LuxTextButton, chỉ khi còn chưa đọc (T2)
├────────────────────────────────────────┤
│ [ Tất cả · 12 | Chưa đọc · 3 ]         │  LuxSegmented
│ Hôm nay                                │  LuxSectionHeader
│ ● [📋] Giao việc · 09:12            ›  │  dòng mở được: "›", role Button (T1)
│        Bạn được giao lệnh sửa đèn…     │  titleMedium đậm khi chưa đọc
│   [⚠] Sự cố · 08:40                    │  dòng không mở được: không "›"
│        Phát hiện đèn tắt POLE-0047     │
│ Đang hiện 20/45 thông báo gần nhất     │
└────────────────────────────────────────┘
```

- **Top bar:** `LuxChildTopBar(title = "Thông báo", onBack)`, action "Đọc tất cả" (`LuxTextButton`, `labelLarge`, ≥48dp) chỉ hiện khi còn thông báo chưa đọc.
- **Bộ lọc:** `LuxSegmented` "Tất cả · n" / "Chưa đọc · n" (RadioButton + selected), thay chip tự dựng.
- **Dòng thông báo (`LuxListItem` hoặc dòng tự dựng có role):**
  - Icon theo `type` trong ô `LuxTheme.status.kind` (trung tính), không màu riêng từng loại.
  - Dòng đầu: nhãn loại + giờ (Hôm nay/Hôm qua) hoặc ngày giờ (Trước đó).
  - `title` (`titleMedium`, đậm khi chưa đọc) + `body` (`bodyMedium`, tối đa 2 dòng).
  - Chưa đọc: chấm `primary` + chữ đậm; TalkBack đọc thêm "chưa đọc".
  - **Dòng mở được** (`entity_type = "work_order"`): có "›", role Button, mô tả hành động "Mở". **Dòng không mở được**: không "›"; bấm vẫn đánh dấu đã đọc (chấm biến mất ngay) (T1).
- **Bốn trạng thái:**
  - Đang tải: `LuxLoadingRegion` ("Đang tải thông báo") skeleton bộ lọc + 5 dòng.
  - Rỗng: `LuxEmptyState` "Chưa có thông báo nào · Thông báo về việc được giao, khảo sát và sự cố sẽ xuất hiện ở đây."
  - Lỗi: `LuxErrorState(kind)` + "Thử lại": mạng → "Không tải được thông báo · Kiểm tra kết nối rồi thử lại."; khác → "… · Vui lòng thử lại sau ít phút."
  - Lọc "Chưa đọc" mà không còn gì: `LuxEmptyState` nhỏ "Đã đọc hết thông báo" (không nút).

---

## 5. Thay đổi ngoài giao diện (nhỏ)

1. **`ProfileViewModel.retry()`** tải lại hồ sơ (huỷ job cũ trước khi tải). `ProfileUiState.Error` thêm `isNetworkError` (`IOException`).
2. **`NotificationListUiState.Error`** thêm `isNetworkError`.
3. **Hàm thuần `profileSyncSummary(pending, error, lastSyncedAt): String`** và **`isNavigable(row)`** (= `notificationNavigationTarget(row) != null`) để unit test.
4. **`NavGraph`:** bỏ topBar chuông nổi; `ProfileRoute` nhận `unreadNotificationCount` + `onOpenNotifications`. Xoá `NotificationBellBar` (+ test) nếu không còn nơi dùng.

Không đổi API, polling, luật điều hướng, Room, sync.

---

## 6. Test và chia commit

**Test:**
- Unit: `profileSyncSummary` (0/chờ/lỗi/chưa từng đồng bộ), `ProfileViewModelTest` (`retry`, `isNetworkError`), `NotificationListViewModelTest` (`isNetworkError`), `isNavigable`.
- UI `ProfileScreenTest`: top bar + chuông; 2 dòng trạng thái; Lỗi có "Thử lại" và vẫn có Đăng xuất; cảnh báo chưa đồng bộ; hộp thoại đăng xuất.
- UI `NotificationListScreenTest`: "Đọc tất cả" chỉ khi còn chưa đọc; bộ lọc RadioButton; dòng `work_order` có "›" và mở được, dòng `fault` không có "›" nhưng đánh dấu đã đọc; 4 trạng thái.
- Cỡ chữ 1.5× ở 360dp cho cả 2 màn. Chạy trên SM-A075F.

**Chia commit (≤400 dòng mỗi commit):**

| # | Nội dung |
|---|---|
| 1 | Hàm thuần + ViewModel (retry, isNetworkError) + unit test |
| 2 | Cá nhân: top bar có chuông, NavGraph bỏ chuông nổi, xoá `NotificationBellBar` |
| 3 | Cá nhân: thẻ hồ sơ, tình trạng làm việc, đăng xuất, 3 trạng thái |
| 4 | Thông báo: top bar + "Đọc tất cả", bộ lọc, dòng thông báo có/không "›" |
| 5 | Thông báo: 4 trạng thái |
| 6 | Test cỡ chữ lớn, chạy máy, dọn code cũ |

---

## 7. Quyết định của chủ dự án (2026-11-17)

| # | Câu hỏi | Quyết định |
|---|---|---|
| T1 | Thông báo không mở được màn | **Chỉ dòng mở được có "›" và role Button**; dòng khác bấm chỉ đánh dấu đã đọc, không snackbar |
| T2 | Vị trí "Đọc tất cả" | **Nút chữ trên thanh tiêu đề**, chỉ khi còn chưa đọc |
| T3 | Phần sẵn sàng làm việc ở Cá nhân | **Bỏ dòng tổng**, giữ 2 dòng trạng thái thật (mạng, đồng bộ) có icon + chữ |
