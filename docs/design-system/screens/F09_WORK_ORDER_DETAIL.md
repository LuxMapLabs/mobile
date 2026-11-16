# Đề xuất redesign: Chi tiết lệnh (F09 / F10)

> **Trạng thái:** **ĐÃ DUYỆT 2026-11-16** (Q1–Q7, mục 11). Q4 duyệt kèm điều kiện, Q6 **không duyệt**, Q7 duyệt kèm ngoại lệ. Chưa có code nào thay đổi.
> **Màn:** `feature/workorder/ui/detail/WorkOrderDetailScreen.kt` (2.034 dòng), route `work-order/{workOrderId}`.
> **Căn cứ:** `docs/design-system/AUDIT_REPORT.md` (P-04, P-05, P-07, P-08, A1–A7), `docs/design-system/vNext/` (component, màu, chữ), code hiện tại (đọc ngày 2026-11-16).
> **Phụ thuộc:** bộ component vNext (PR #155) phải merge trước khi code màn này.

---

## 1. Mục tiêu và giới hạn

**Mục tiêu:** giúp Kỹ sư hiện trường trả lời nhanh 3 câu hỏi khi mở một lệnh:
1. **Lệnh này là gì, hạn khi nào, đang ở trạng thái nào?**
2. **Tôi có làm được gì lúc này không? Nếu không, vì sao?**
3. **Tôi còn thiếu gì để nộp?**

**Giữ nguyên tuyệt đối (không đổi nghiệp vụ):**
- Luật hiện nút theo `allowed_actions` của server.
- Cửa sổ lịch Bắt đầu/Yêu cầu hủy (`workOrderScheduleWindow`): mở từ `scheduled_date − 1`, đóng sau `scheduled_date`, khoá form khi quá hạn.
- Khoá nút "Bắt đầu" ở client khi yêu cầu hủy đang chờ duyệt.
- Điều kiện nộp (`canSubmit()`): ghi chú ≥10 ký tự; sửa chữa cần ảnh sau; kiểm tra cần kết quả cho từng sự cố; đo GPS cần ≥1 cột đã đo.
- Ghi Room trước, đồng bộ qua `sync_queue`. Trạng thái "Đã nộp · chờ đồng bộ".
- Hộp xác nhận trước khi nộp. Luồng xin hủy lệnh (4 lý do + ghi chú).
- Điều hướng: Back, mở Google Maps ra sự cố, chụp ảnh bằng chứng, đo GPS cột, xem PDF, sang tab Khảo sát khi bắt đầu lệnh khảo sát.

**Thay đổi:** bố cục, thứ bậc thông tin, component, chữ, màu, cách hiển thị trạng thái và thông điệp.

---

## 2. Vấn đề của màn hiện tại (tóm tắt từ audit)

| # | Vấn đề | Hậu quả |
|---|---|---|
| 1 | Hero navy chỉ có ở trạng thái "Có dữ liệu"; Loading/Lỗi dùng thanh tiêu đề khác | Header nhảy khi dữ liệu về; vị trí nút Back đổi |
| 2 | Lỗi và Rỗng **không có nút Thử lại**; hiện nguyên văn lỗi kỹ thuật | Kỹ sư bị kẹt, chỉ còn cách Back |
| 3 | Tới **8 vùng thông điệp** có thể hiện cùng lúc ở đầu màn, không có thứ tự ưu tiên | Lý do "vì sao không bấm được" bị chìm |
| 4 | Lệnh kiểm tra liệt kê sự cố **hai lần** (danh sách + form kết quả) | Dài gấp đôi, phải cuộn qua lại để đối chiếu |
| 5 | 2 bước "Bắt đầu thực hiện ✓", "Xác nhận sự cố ✓" luôn hiện là đã xong | Nhiễu, không mang thông tin |
| 6 | Chữ 12–13sp, chữ in hoa trang trí, màu hardcode (`#9FB6D6`, `#1E3D6B`, `Navy700`) | Khó đọc, sai màu ở chế độ tối |
| 7 | Thuật ngữ lẫn lộn: "Manager" và "Kỹ sư bảo trì"; lộ "cổng #2" | Người dùng không rõ ai là ai |
| 8 | Một file 2.034 dòng gánh 4 loại lệnh | Rủi ro hồi quy cao khi sửa |

---

## 3. Bố cục mới (chung cho mọi loại lệnh)

```
┌──────────────────────────────────────┐
│ ←  Sửa đèn tuyến ĐH-12               │  LuxChildTopBar, KHÔNG đổi giữa các trạng thái
│    Sửa chữa · WO-1042                 │  phụ đề = loại lệnh · mã lệnh
├──────────────────────────────────────┤
│ ┌──────────────────────────────────┐ │  ① THẺ TÓM TẮT (LuxCard)
│ │ [Đang thực hiện]  [Chờ đồng bộ]  │ │  badge trạng thái lệnh (+ badge đồng bộ nếu có)
│ │ Hạn xử lý        Lịch sửa chữa   │ │  2 ô số liệu
│ │ Hôm nay ⏱        16/11/2026      │ │  màu due.* + icon (không chỉ màu)
│ │ Giai đoạn hiện tại: Sửa chữa     │ │  một dòng chữ trung thực, KHÔNG có thanh tiến trình (Q6)
│ └──────────────────────────────────┘ │
│                                      │
│ ┌──────────────────────────────────┐ │  ② MỘT BANNER NỔI BẬT DUY NHẤT (LuxBanner)
│ │ ⚠ Kỹ sư bảo trì yêu cầu bổ sung  │ │  chọn theo thứ tự ưu tiên ở mục 4
│ │   Chụp lại ảnh chóa đèn…         │ │
│ └──────────────────────────────────┘ │
│                                      │
│ ③ NỘI DUNG THEO LOẠI LỆNH (mục 5)    │  section dùng LuxSectionHeader + LuxCard/LuxListItem
│                                      │
│ ④ GHI CHÚ & LỊCH SỬ (LuxCard)        │  ghi chú giao việc, ghi chú duyệt, yêu cầu hủy…
│                                      │  (những thông điệp không được chọn làm banner ②)
├──────────────────────────────────────┤
│ ⓘ Còn 2 sự cố chưa có kết quả        │  ⑤ BOTTOM ACTION BAR (giữ cơ chế hiện có)
│ ▬▬▬▬▬▬▬▭▭▭                         │  thanh tiến độ (chỉ lệnh kiểm tra)
│ [ Yêu cầu hủy ] [   Bắt đầu  ▶   ]   │  "Yêu cầu hủy" = LuxDestructiveButton viền, bên trái
└──────────────────────────────────────┘
```

**Quy tắc chung:**
- Lề màn 16dp; khoảng cách giữa section 24dp; nền `background`, card `cardContainer`.
- Không gradient, không khối navy. Màu thương hiệu chỉ ở nút chính, tab và tiêu đề section.
- Mã lệnh, mã cột giữ nguyên dạng hệ thống trả về. Không viết hoa toàn bộ.
- Không thêm menu `⋯`: hiện không có hành động nào cần đặt vào đó.

---

## 4. Thứ tự ưu tiên thông điệp (giải quyết vấn đề #3)

Chỉ **1** thông điệp ở mức cao nhất được hiện thành banner ②. Các thông điệp còn lại vẫn hiện, nhưng trong card "Ghi chú & lịch sử" ④. Nội dung không mất, chỉ hạ mức nổi bật.

| Ưu tiên | Điều kiện (giữ đúng logic hiện tại) | Tông | Nội dung đề xuất |
|---|---|---|---|
| 1 | Quá hạn nộp: `scheduled_date` đã qua và lệnh `in_progress` | Lỗi | **Quá hạn nộp báo cáo** · Hạn dự kiến {ngày}. Form đã khoá. Liên hệ Kỹ sư bảo trì để được xử lý. |
| 1 | Quá hạn bắt đầu: `scheduled_date` đã qua và lệnh `assigned` | Lỗi | **Quá hạn bắt đầu** · Hạn dự kiến {ngày}. Liên hệ Kỹ sư bảo trì để được giao lại. |
| 2 | Bị trả lại để bổ sung: `review_note` có nội dung và lệnh `in_progress` | Cảnh báo | **Kỹ sư bảo trì yêu cầu bổ sung** · {review_note} |
| 2 | Yêu cầu hủy bị từ chối: `cancellation_request_status = rejected` | Cảnh báo | **Yêu cầu hủy bị từ chối** · {cancellation_reject_note} |
| 3 | Yêu cầu hủy đang chờ: `pending` | Trung tính | **Đã gửi yêu cầu hủy** · Đang chờ Kỹ sư bảo trì duyệt. Chưa thể bắt đầu lệnh. |
| 3 | Đã nộp, chờ đồng bộ hoặc chờ duyệt | Thông tin | **Đã nộp báo cáo** · {trạng thái đồng bộ}. Kỹ sư bảo trì sẽ duyệt sau khi đồng bộ. |
| 4 | Ghi chú duyệt (`review_note` khi lệnh không còn `in_progress`) | (vào card ④) | "Ghi chú duyệt: {review_note}" |
| 4 | Ghi chú giao việc (`note`) | (vào card ④) | "Ghi chú giao việc: {note}", rút gọn 3 dòng + "Xem thêm" (giữ) |

**Hai thay đổi nhỏ về nội dung:**
- Bỏ chữ "Manager", thống nhất **"Kỹ sư bảo trì"**. Theo đặc tả, đây là tên vai trò; câu hỏi mở số 2 của audit vẫn chờ chốt glossary.
- Bỏ "(cổng #2)" ở trạng thái đã nộp; người dùng không cần biết mã cổng duyệt nội bộ.

**Ngoại lệ đã duyệt (Q7):** banner **ngoại tuyến / dữ liệu đã lưu** được hiện **cùng lúc** với banner nghiệp vụ, đặt ngay trên nó. Lý do: nó mô tả chất lượng dữ liệu đang xem, không phải trạng thái lệnh, nên không cạnh tranh vị trí với banner nghiệp vụ.

Lỗi khi bấm Bắt đầu (`startError`) và lỗi khi nộp (`submitError`) **không** đi vào hàng ưu tiên này. Chúng hiện ngay **trên nút** ở Bottom Action Bar, vì gắn với hành động vừa làm, dưới dạng `LuxBanner` tông lỗi. Hiện tại một cái là chữ đỏ trơn, một cái là banner.

---

## 5. Nội dung theo loại lệnh

### 5.1 Lệnh kiểm tra (`inspection`)

**Thay đổi chính:** gộp "danh sách sự cố" và "kết quả từng sự cố" thành **một danh sách** (vấn đề #4).

```
Sự cố cần kiểm tra                    3 sự cố        ← LuxSectionHeader
┌──────────────────────────────────────┐
│ [💡] Đèn tắt                    [Cao]│  LuxListItem: icon loại sự cố, nhãn, badge ưu tiên
│      POLE-0047 · FAULT-1        [➤] │  overline mã cột · mã sự cố; nút chỉ đường 48dp
│ ┌ Có sự cố ┬ Không có ┬ Chưa rõ ┐   │  LuxSegmented, CHỈ hiện khi lệnh đang thực hiện
│ └──────────┴──────────┴─────────┘   │
├──────────────────────────────────────┤
│ … sự cố tiếp theo …                  │
└──────────────────────────────────────┘

Ghi chú kiểm tra            Bắt buộc           ← chỉ khi đang thực hiện
┌──────────────────────────────────────┐
│ VD: Cảm biến mất tín hiệu từ 08/10   │  OutlinedTextField M3, tối thiểu 3 dòng
└──────────────────────────────────────┘
  12/10 ký tự tối thiểu                        hiện trước khi gõ, không chỉ báo lỗi sau
```

- Khi lệnh **chưa bắt đầu** (`assigned`): danh sách chỉ đọc, kèm card "Cần chuẩn bị để nghiệm thu" (giữ nội dung hiện có, dựng bằng `LuxCard` + `RequirementList`).
- Khi **quá hạn nộp**: phân đoạn kết quả và ô ghi chú chỉ đọc, hiện giá trị đã chọn. Banner ưu tiên 1 giải thích lý do khoá.
- Sự cố không có toạ độ: thay nút chỉ đường bằng chữ "Chưa có toạ độ" (giữ).

### 5.2 Lệnh sửa chữa (`repair`)

```
Sự cố cần sửa                         2 sự cố
  (LuxListItem như 5.1; thêm badge "Có sự cố" lấy từ kết quả kiểm tra trước, nếu có)

Nghiệm thu                           1/2 bắt buộc     ← chỉ khi đang thực hiện
┌──────────────────────────────────────┐
│ Ảnh sau sửa chữa          Bắt buộc   │
│ [ + Chụp ảnh ]  hoặc  [ảnh] Xem/Chụp lại │  EvidencePicker (giữ), xem trước có "Chụp lại"
├──────────────────────────────────────┤
│ Kết quả sửa chữa          Bắt buộc   │
│ [ ô nhập, ≥10 ký tự ]                │
├──────────────────────────────────────┤
│ Vật tư đã thay         Không bắt buộc│
│ [ ô nhập 1 dòng ]                    │
└──────────────────────────────────────┘
```

- **Bỏ 2 bước giả** "Bắt đầu thực hiện ✓" và "Xác nhận sự cố ✓" (vấn đề #5). Thông tin "x/y sự cố đã xác nhận ở bước kiểm tra" chuyển thành badge trên từng sự cố.
- Bỏ đánh số bước 1–5 cùng đường nối dọc. Các mục không có thứ tự bắt buộc, nên số thứ tự gây hiểu nhầm.
- Mỗi mục có nhãn **"Bắt buộc" / "Không bắt buộc"** dạng chữ, không chỉ màu.

### 5.3 Lệnh cập nhật GPS cột (`pole_gps_update`)

```
Tệp đính kèm                          1 file
  LuxListItem: [📄] tên file · trạng thái tải (Đang tải / Lỗi – chạm để thử lại) · [⬇ Lưu về máy]

Cột đèn                              2/5 đã đo
  LuxListItem: mã cột · "GPS cũ: 10.762622, 106.660172" (hoặc "Chưa có") · badge Đã đo/Chưa chụp
  chạm để mở màn Đo GPS cột (chỉ khi đang thực hiện)

Báo cáo                               Bắt buộc
  [ ô ghi chú ≥10 ký tự ]
```

Giữ nguyên hành vi tải PDF, mở trình xem PDF, lưu file và mở màn đo GPS.

### 5.4 Lệnh khảo sát (`survey`)

Hiện màn chỉ có một dòng chữ "Lệnh khảo sát — xem trong tab Khảo sát". Đề xuất: một `LuxCard` thông tin.
- Lệnh khảo sát được thực hiện ở tab **Khảo sát**: chụp ảnh từng cột trên tuyến.
- Nút **Bắt đầu** ở đáy màn giữ hành vi hiện có: bắt đầu xong sẽ tự chuyển sang tab Khảo sát.
- ⚠️ **Câu hỏi Q3** (mục 11): có thêm nút "Mở tab Khảo sát" khi lệnh **đã** bắt đầu hay không. Đây là thêm một điều hướng.

---

## 6. Bốn trạng thái dữ liệu

| Trạng thái | Hiện tại | Đề xuất |
|---|---|---|
| **Đang tải** | Vòng xoay giữa màn + thanh tiêu đề "Chi tiết lệnh" | `LuxChildTopBar("Chi tiết lệnh")` + skeleton dựng theo hình thẻ tóm tắt và 3 dòng sự cố (`LuxLoadingRegion`, TalkBack đọc "Đang tải chi tiết lệnh"). Thanh tiêu đề **cùng kiểu** với khi có dữ liệu |
| **Có dữ liệu** | Hero navy riêng | Như mục 3 |
| **Rỗng** (không tìm thấy lệnh) | Icon + chữ, **không có hành động** | `LuxEmptyState`: "Không tìm thấy lệnh này · Lệnh có thể đã bị huỷ hoặc không còn thuộc địa bàn của bạn." + nút **"Quay lại"** |
| **Lỗi** | Hiện nguyên văn lỗi kỹ thuật, **không có Thử lại** | `LuxErrorState` với **"Thử lại"** (bắt buộc) + "Quay lại"; nội dung theo loại lỗi (mạng / khác), không hiện câu lỗi kỹ thuật. Cần thay đổi nhỏ ở ViewModel (mục 9) |

Khi offline mà có dữ liệu đã lưu: vẫn là "Có dữ liệu" (theo quy ước của dự án). Hiện `UiState` chỉ có `isOnline`, không có thời điểm lưu, nên ở bản này chỉ hiện banner thông tin "Đang ngoại tuyến · thao tác sẽ được lưu trên thiết bị". Banner này hiện song song với banner nghiệp vụ (ngoại lệ Q7, mục 4).

---

## 7. Giao diện sáng và Night Field

- Màn theo cài đặt hệ thống (CLAUDE.md: F09/F10 không ép Dark). Mọi màu lấy từ token, không còn màu hardcode.
- **Night Field cần kiểm tra riêng:**
  - Card dùng `cardContainer` `#1C1C1C` trên nền `#0D0D0D`, có viền `outlineVariant`.
  - Badge là chữ sáng trên ô tối.
  - Thanh tiến độ dùng `primary` (Navy300) và `outline`.
- Ảnh xem trước bằng chứng: hộp thoại nền đen giữ nguyên (đang đúng vai trò "ảnh trên nền tối").

---

## 8. Ánh xạ component

| Hiện tại | Đề xuất |
|---|---|
| Hero navy tự dựng + `AppTopBar` (khi đang tải/lỗi) | `LuxChildTopBar` cho cả 4 trạng thái |
| `WorkOrderSummaryCard` (nền/viền tự dựng) | `LuxCard` + `StatusBadge` + 2 ô số liệu |
| `InlineBanner` ×5 + `ManagerNoteCard` | 1 `LuxBanner` + card "Ghi chú & lịch sử" (`LuxCard`) |
| `CompletionSectionHeader` (avatar `Navy700`) | `LuxSectionHeader` + badge đếm |
| `FaultRow` tự dựng + `NavigateButton` | `LuxListItem` + nút chỉ đường 48dp |
| `OutcomeSegment` | `LuxSegmented` (radio, ✓, 48dp) |
| `WorkflowStepRow` (đánh số, đường nối) | Nhóm form trong `LuxCard`, nhãn Bắt buộc dạng chữ |
| `PreStartRequirementsPreview` | `LuxCard` + `RequirementList` (giữ) |
| `PdfAttachmentRow`, `PoleGpsRow` | `LuxListItem` |
| `MessageState` (lỗi/rỗng) | `LuxErrorState` / `LuxEmptyState` |
| `CircularProgressIndicator` | `LuxLoadingRegion` + `LuxSkeleton` |
| `SubmitConfirmationSheet` tự dựng | `LuxBottomSheet` + nội dung tóm tắt hiện có |
| `CancelRequestButton` | `LuxDestructiveButton(filled = false)` |
| `BottomActionBar`, `SideBySideBottomBar` | Gộp thành một `BottomActionBar` có nút phụ tuỳ chọn |
| `LifecycleTracker` + `workOrderLifecycleSteps()` | **Bỏ khỏi màn** (Q6). Thay bằng dòng "Giai đoạn hiện tại: {Kiểm tra / Sửa chữa / Khảo sát / Đo GPS cột}", lấy trực tiếp từ `task_kind` của chính lệnh đang xem, không suy luận bước trước/sau |
| `EvidencePicker`, `RequirementList`, `CancelRequestSheet`, `TopSlideToast` | Giữ (chỉ đổi màu/chữ nếu cần) |

---

## 9. Thay đổi ngoài giao diện (nhỏ, cần duyệt)

1. **`WorkOrderDetailViewModel.retry()`** công khai, để nút "Thử lại" hoạt động. Hiện `load()` là `private` và chỉ chạy một lần trong `init`.
   **Điều kiện khi duyệt (Q4):** không gọi lại `load()` nguyên trạng. `load()` mở các collector (`observe…`, trạng thái mạng, Room) trong `viewModelScope`; gọi lại sẽ tạo **collector song song**, cập nhật `uiState` chồng chéo. Cách làm:
   - Giữ `Job` của lần tải hiện tại và **huỷ nó trước** khi tải lại, hoặc gom thành **một luồng tải duy nhất** được kích hoạt lại bằng một tín hiệu (ví dụ `MutableSharedFlow<Unit>` + `flatMapLatest`).
   - `retry()` đặt `uiState = Loading` trước khi tải.
   - Unit test: gọi `retry()` nhiều lần liên tiếp vẫn chỉ có **một** lần thu thập dữ liệu đang chạy, và kết quả cuối không bị trạng thái cũ ghi đè.
2. **Phân loại lỗi** trong `WorkOrderDetailUiState.Error`: thêm `kind` (mạng / khác), suy ra từ `IOException` hoặc trạng thái mạng. Mục đích là chọn nội dung phù hợp thay vì hiện `e.message`. Giữ `message` cho log.

Không đổi repository, Room, sync, API hay `allowed_actions`.

---

## 10. Cấu trúc code và cách làm

**Tách file** (giảm rủi ro hồi quy, vấn đề #8):

```
feature/workorder/ui/detail/
  WorkOrderDetailScreen.kt        khung màn: top bar, 4 trạng thái, bottom bar
  WorkOrderSummary.kt             thẻ tóm tắt
  WorkOrderMessages.kt            chọn banner theo ưu tiên (hàm thuần, unit test được) + card ghi chú
  sections/InspectionSection.kt
  sections/RepairSection.kt
  sections/PoleGpsSection.kt
  sections/SurveySection.kt
  WorkOrderCta.kt                 workOrderCtaState (giữ nguyên logic, chỉ chuyển file)
```

**Test:**
- **Unit test mới** cho hàm chọn banner: mọi tổ hợp quá hạn / trả lại / hủy / đã nộp → đúng một banner, đúng tông.
- **`WorkOrderDetailScreenTest`** (15 test, trong đó 5 test lỗi từ trước khi redesign): cập nhật theo chữ mới. Thêm test "Lỗi có Thử lại", "chỉ một banner nổi bật", "lệnh kiểm tra không lặp danh sách sự cố". Chạy trên máy thật.
- `WorkOrderDetailViewModelTest` phải pass không đổi, chứng minh nghiệp vụ giữ nguyên. Bổ sung test cho `retry()` theo điều kiện Q4.
- `WorkOrderLifecycleTest`: các test của `workOrderScheduleWindow` **giữ nguyên** (luật lịch không đổi). Các test của `workOrderLifecycleSteps` bị xoá cùng hàm đó (Q6). `LifecycleTracker` và test androidTest của nó (`LifecycleTrackerTest`) chỉ xoá nếu không còn màn nào khác dùng.
- Thêm test: thẻ tóm tắt **không** hiện thanh tiến trình nhiều bước, chỉ hiện "Giai đoạn hiện tại: …".

**Chia commit (ước tính, mỗi commit ≤400 dòng):**

| # | Nội dung |
|---|---|
| 1 | Chuyển `workOrderCtaState` và lifecycle sang file riêng, không đổi logic |
| 2 | ViewModel `retry()` + phân loại lỗi + test |
| 3 | Khung màn: `LuxChildTopBar`, 4 trạng thái (skeleton, rỗng, lỗi) |
| 4 | Thẻ tóm tắt + hàm chọn banner + card ghi chú + unit test |
| 5 | Section kiểm tra (gộp danh sách + kết quả) |
| 6 | Section sửa chữa (bỏ bước giả) |
| 7 | Section đo GPS cột + khảo sát |
| 8 | Bottom bar gộp + sheet xác nhận dùng `LuxBottomSheet` |
| 9 | Cập nhật `WorkOrderDetailScreenTest` + test mới, chạy trên máy |
| 10 | Xoá code cũ không còn dùng trong màn này. Đã kiểm tra 2026-11-16: `LifecycleTracker` và `workOrderLifecycleSteps()` **chỉ** màn này dùng, nên xoá cả component, hàm và test đi kèm (`LifecycleTrackerTest`, phần lifecycle trong `WorkOrderLifecycleTest`); giữ `workOrderScheduleWindow` |

Mỗi bước dừng để bạn xem, như các giai đoạn trước.

---

## 11. Quyết định của chủ dự án (2026-11-16)

| # | Câu hỏi | Quyết định | Lý do / điều kiện |
|---|---|---|---|
| **Q1** | Gộp danh sách sự cố và kết quả từng sự cố (lệnh kiểm tra)? | **Đồng ý** | Mỗi sự cố có lựa chọn kết quả ngay trong dòng đó; không phải cuộn sang danh sách khác để trả lời |
| **Q2** | Bỏ 2 bước luôn-đã-xong và bỏ đánh số bước ở phần nghiệm thu? | **Đồng ý** | Hai bước luôn "đã xong" là thông tin giả. Thay bằng nhóm yêu cầu bắt buộc/không bắt buộc |
| **Q3** | Thêm nút "Mở tab Khảo sát"? | **Đồng ý** | **Chỉ khi lệnh khảo sát đã bắt đầu.** Là lối tắt tới luồng đã có, không tạo nghiệp vụ mới |
| **Q4** | Thêm `retry()` và phân loại lỗi mạng/khác? | **Đồng ý, làm cẩn thận** | Retry phải huỷ collector cũ hoặc dùng một luồng tải duy nhất; không gọi lại `load()` nguyên trạng (mục 9) |
| **Q5** | Gọi vai trò duyệt là "Kỹ sư bảo trì"? | **Đồng ý** | Chuẩn hoá **toàn màn**. Bỏ "Manager"; **tuyệt đối** bỏ mã nội bộ như "cổng #2" |
| **Q6** | Giữ thanh tiến trình vụ việc trong thẻ tóm tắt? | **Không** | Thanh hiện được **suy ra từ `task_kind`**, không có dữ liệu chuỗi vụ việc thật, nên có thể làm người dùng tin các bước trước đã xong dù app không biết. Đổi nhãn chỉ sửa chữ, không sửa vấn đề dữ liệu. **Thay bằng một dòng trung thực "Giai đoạn hiện tại: {…}"**. Không hiện thanh tiến trình, không suy luận tiến trình từ loại lệnh, cho tới khi API cung cấp dữ liệu chuỗi vụ việc thực tế |
| **Q7** | Một banner nghiệp vụ nổi bật theo thứ tự ưu tiên? | **Đồng ý, có ngoại lệ** | Banner ngoại tuyến/dữ liệu đã lưu được hiện cùng banner nghiệp vụ (mô tả chất lượng dữ liệu). Lỗi của CTA vừa bấm vẫn hiện ngay trên Bottom Action Bar |

### Ghi chú cho Q6: điều kiện để đưa thanh tiến trình trở lại (chưa làm)

Theo CLAUDE.md (mục WorkOrder, xác nhận từ source backend 2026-10-13), backend đã có `case_id` và `GET /api/v1/work-orders?case_id=` trả về cả chuỗi khảo sát → kiểm tra → sửa chữa. Trên mobile, `WorkOrderItemDto` đã đọc `case_id` / `parent_work_order_id`, nhưng **`WorkOrderDetailDto` chưa đọc** và màn chi tiết chưa gọi API theo `case_id`. Muốn hiện thanh tiến trình thật cần:
- Map `case_id` trong DTO chi tiết.
- Gọi danh sách lệnh theo `case_id`, xác định trạng thái **thật** của từng lệnh trong chuỗi.
- Xác nhận cách thể hiện các bước "duyệt" (lệnh `verified`) với backend.

Đây là một hạng mục riêng, cần duyệt riêng; không thuộc đợt redesign này.

---

## 12. Vòng 2: làm lại bố cục (2026-11-16)

> **Trạng thái:** **ĐÃ DUYỆT VÀ ĐÃ CODE XONG 2026-11-17** trên nhánh `style/workorder-detail-layout-v2`. Còn nợ: kiểm tra tay trên máy đã đăng nhập (Light/Night Field, cỡ chữ 200%).
> **Căn cứ:** ảnh chụp màn hiện tại (lệnh sửa chữa đang làm, quá hạn, 360dp) và phản hồi của chủ dự án.

### 12.1 Vấn đề thấy trên màn sau vòng 1

| # | Vấn đề | Hậu quả |
|---|---|---|
| 1 | Header: tiêu đề lệnh 2 dòng + dòng phụ "Sửa chữa · mã lệnh" trong thanh cao cố định 64dp | Dòng phụ bị che mất |
| 2 | "Hạn xử lý" nằm trong lưới 2 cột | Bị cắt thành "Quá hạn 1 ngà…" |
| 3 | "Quá hạn" lặp 3 lần: chữ đỏ ở ô hạn, banner 3 dòng, ghi chú đỏ 2 dòng trên nút | Dài, nặng nề |
| 4 | Dòng sự cố có badge mức độ ("Cao") | Nhiễu, không phục vụ thao tác sửa |
| 5 | Mỗi trường form có chữ "Bắt buộc"/"Không bắt buộc" bên phải | Rối mắt |

### 12.2 Bố cục mới (một cột gọn)

```
← Lệnh sửa chữa                                ← 1 dòng, không mã lệnh
┌──────────────────────────────────────┐
│ Sửa đèn tuyến liên xã Phước Hiệp      │  tiêu đề lệnh, titleMedium, xuống dòng tự do
│ đoạn qua chợ                          │
│ [⟳ Đang thực hiện]                    │
│ Hạn xử lý          15/11/2026         │  dòng nhãn – giá trị
│                    ⓘ Quá hạn 1 ngày   │  xuống dòng khi thiếu chỗ, không cắt
│ Lịch sửa chữa      15/11/2026         │
│ Giai đoạn hiện tại Sửa chữa           │
│ Mã lệnh            a3f9c2e1-7b4d-…    │  mã đầy đủ, bodySmall, được xuống dòng
└──────────────────────────────────────┘
[! Quá hạn nộp báo cáo                  ]  banner 2 dòng
[  Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì.]
Ghi chú & lịch sử …                         (giữ nguyên)
Sự cố cần sửa                      2 sự cố
  💡 Đèn tắt · POLE-0047 · F1        ➤      không badge mức độ
Nghiệm thu                     0/2 bắt buộc  (giữ nguyên)
  Ảnh sau sửa chữa *
  Kết quả sửa chữa *
  Vật tư đã thay
────────────────────────────────────────
Đã khoá do quá hạn                          1 dòng, căn trái
[        Hoàn thành lệnh 🔒        ]
```

**Header**
- `LuxChildTopBar` chỉ có tiêu đề loại lệnh, 1 dòng, không dòng phụ: "Lệnh sửa chữa" / "Lệnh kiểm tra" / "Lệnh khảo sát" / "Lệnh đo GPS cột".
- Đang tải / Rỗng / Lỗi: "Chi tiết lệnh" (chưa biết loại lệnh). Vẫn cùng một kiểu bar cho 4 trạng thái.

**Thẻ tóm tắt**
- Tiêu đề lệnh ở đầu thẻ (`titleMedium`), không giới hạn số dòng.
- Badge trạng thái lệnh.
- Các dòng nhãn – giá trị (nhãn `bodyMedium` `onSurfaceVariant` bên trái, giá trị `bodyLarge` bên phải). Khi không đủ chỗ, giá trị xuống dòng dưới nhãn; không cắt chữ, không ellipsis.
  - **Hạn xử lý:** ngày `due_date`. Nếu quá hạn, thêm dòng "Quá hạn N ngày" màu `dueOverdue` kèm icon (cùng phép tính hiện có).
  - **Lịch {sửa chữa / kiểm tra / khảo sát / đo GPS}:** ngày `scheduled_date`, hoặc "Chưa có lịch".
  - **Giai đoạn hiện tại:** giữ (không bỏ, theo V3).
  - **Mã lệnh:** mã đầy đủ, `bodySmall`, được xuống dòng.

**Thông điệp quá hạn: mỗi chỗ một câu ngắn**

| Chỗ | Hiện tại | Mới |
|---|---|---|
| Banner quá hạn nộp | "Hạn dự kiến {ngày}. Form đã khoá. Liên hệ Kỹ sư bảo trì để được xử lý." | Tiêu đề "Quá hạn nộp báo cáo" + "Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì." |
| Banner quá hạn bắt đầu | "Hạn dự kiến {ngày}. Liên hệ Kỹ sư bảo trì để được giao lại." | Tiêu đề "Quá hạn bắt đầu" + "Liên hệ Kỹ sư bảo trì để được giao lại." |
| Ghi chú trên nút, quá hạn nộp | "Đã quá hạn nộp báo cáo · liên hệ Kỹ sư bảo trì để xử lý" | "Đã khoá do quá hạn" |
| Ghi chú trên nút, quá hạn bắt đầu | "Đã quá hạn bắt đầu · liên hệ Kỹ sư bảo trì để được giao lại" | "Đã khoá do quá hạn" |
| Ghi chú trên nút, chưa tới lịch | "Mở vào {ngày}" | giữ |
| Ghi chú trên nút, chờ duyệt hủy | "Đang chờ duyệt yêu cầu hủy" | giữ |

Ngày hạn không lặp trong banner vì đã có ở thẻ tóm tắt. Ghi chú trên nút căn trái, 1 dòng ở cỡ chữ thường.

**Sự cố cần sửa / cần kiểm tra:** bỏ badge mức độ (`severity`). Dòng còn: icon loại sự cố, tên loại sự cố, "mã cột · mã sự cố", nút chỉ đường 48dp. Áp dụng cho cả lệnh sửa chữa và lệnh kiểm tra.

**Form nghiệm thu / báo cáo kiểm tra / đo GPS cột**
- Trường bắt buộc: nhãn + **dấu `*` màu `colorScheme.error`** ngay sau nhãn. Semantics của nhãn đọc "{nhãn}, bắt buộc" (TalkBack không đọc "sao").
- Trường không bắt buộc: chỉ nhãn.
- Bỏ chữ "Bắt buộc" / "Không bắt buộc" ở bên phải mỗi trường.
- Bộ đếm "x/y bắt buộc" cạnh tiêu đề nhóm: giữ (V3).

### 12.3 Giữ nguyên
Nghiệp vụ, `allowed_actions`, cửa sổ lịch, khoá "chờ duyệt hủy", điều kiện nộp, sheet xác nhận nộp, sheet xin hủy, ghi chú & lịch sử, luồng ảnh bằng chứng, đo GPS cột, PDF, lối tắt "Mở tab Khảo sát". Không đổi API, Room, sync.

### 12.4 Test
- `WorkOrderDetailScreenTest`: cập nhật chữ mới; thêm "header 1 dòng = loại lệnh, không có mã", "thẻ tóm tắt có mã lệnh đầy đủ", "dòng sự cố không có badge mức độ", "trường bắt buộc có * và đọc 'bắt buộc'; trường không bắt buộc không có chữ Bắt buộc/Không bắt buộc", "ghi chú quá hạn trên nút là 'Đã khoá do quá hạn'".
- `WorkOrderDetailLargeFontTest` (1.5×, 360dp): header không bị cắt; dòng "Hạn xử lý" và "Quá hạn N ngày" hiển thị đủ.
- `WorkOrderMessagesTest`: cập nhật nội dung banner.
- Chạy trên SM-A075F.

### 12.5 Quyết định (2026-11-16)

| # | Câu hỏi | Quyết định |
|---|---|---|
| V1 | Hướng bố cục: một cột gọn / chia tab / chỉ sửa lỗi | **Một cột gọn** |
| V2 | Mã lệnh: không trên header / rút gọn trên header / đầy đủ trên header | **Không trên header**, mã đầy đủ ở cuối thẻ tóm tắt |
| V3 | Bỏ thêm "Giai đoạn hiện tại" / bộ đếm "x/y bắt buộc" / mã sự cố? | **Không bỏ thêm gì** |
