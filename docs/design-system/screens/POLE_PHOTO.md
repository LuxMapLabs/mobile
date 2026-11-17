# Đề xuất redesign: Chụp ảnh cột đèn (danh sách cột, xem trước, kết quả AI)

> **Trạng thái:** **CHỜ DUYỆT BẢN VIẾT.** Hướng đã thống nhất 2026-11-17 (P1–P3, mục 8). Chưa có code nào thay đổi.
> **Màn:** `feature/survey/ui/polephoto/` — `PoleListScreen.kt` (749 dòng), `PolePhotoCaptureScreen.kt` (351), `PoleVideoCaptureScreen.kt` (433), `PolePhotoResultScreen.kt` (912).
> **Căn cứ:** `docs/design-system/AUDIT_REPORT.md` (mục 6.4, 6.5), `docs/design-system/vNext/` (đặc biệt `DARK_NIGHT_FIELD.md` mục 3), CLAUDE.md mục "chụp ảnh/video ngắn theo từng cột đèn" và "PHOTO-REVIEW", code hiện tại (đọc 2026-11-17), cách đã làm ở F02, F09, Hub Khảo sát, Cá nhân/Thông báo.

---

## 1. Mục tiêu và giới hạn

**Danh sách cột:** kỹ sư biết cột nào cần chụp tiếp, cột nào đã gửi/đang chờ/lỗi, mở nhanh kết quả AI của cột đã chụp, rồi hoàn tất tuyến.

**Xem trước:** sau khi camera hệ thống trả ảnh/video, kỹ sư quyết định dùng hay chụp lại.

**Kết quả AI:** kỹ sư đọc kết luận AI, xác nhận hoặc sửa nhãn (dữ liệu cho RQ1), xem quyết định của Kỹ sư bảo trì nếu đã có.

**Giữ nguyên:**
- Mọi endpoint, field, Room (`local_pole_photo`, `local_pole_list_item`), `sync_queue`, luật review (ghi chú bắt buộc khi khác AI, khoá khi đã có `decision`), luồng chụp bằng Intent camera hệ thống, nhận "Đèn sáng/Đèn tắt" theo `cv_state` on/off.
- Câu chữ của sheet chọn cách báo lỗi, sheet xác nhận Hoàn tất, hộp thoại Chụp lại.
- Màn xem trước vẫn tối cố định (`forceDark = true`, Night Field bắt buộc theo `DARK_NIGHT_FIELD.md`).

**Thay đổi:** thanh tiêu đề, thẻ, badge, hành động khi bấm dòng cột, vị trí phần xác nhận nhãn, 4 trạng thái dữ liệu, toàn bộ màu/chữ sang token vNext.

## 2. Vấn đề của màn hiện tại

| Màn | Vấn đề |
|---|---|
| Danh sách cột | Hero navy + sheet đè (`offset`/`clip`), `Navy700` cố định cho ô "cột kế tiếp" (sai trong Night Field), nhãn rời `LabelTextStyle`/`HeadlineTextStyle`, thẻ viền tự vẽ. Mỗi dòng tới 3 badge + 1 nút đuôi, dễ xuống dòng ở 360dp. Xem AI phải bấm nút robot nhỏ, bấm dòng lại mở sheet báo lỗi. Rỗng/Lỗi dùng `MessageState`, lỗi hiện câu kỹ thuật thô. |
| Xem trước ảnh/video | Gradient `Navy900`, `Blue500`, `Color.White` alpha tự đặt, nút Đóng 44dp (< 48dp), nút viết lại bằng `Button`/`OutlinedButton` riêng. Hai màn ảnh/video lặp gần như nguyên khối UI. |
| Kết quả AI | Hero `BrandHeroGradient`, nhãn viết HOA ("KẾT QUẢ AI", "KẾT LUẬN", "KỸ SƯ XÁC NHẬN NHÃN"), 9 màu hardcode. Khối xác nhận nhãn ghim ở đáy chiếm nửa màn 360dp khi bàn phím mở, che ảnh và kết luận. Lỗi hiện câu kỹ thuật thô. |

## 3. Màn Danh sách cột

```
┌──────────────────────────────────┐
│ ←  Chụp ảnh cột đèn          🔔? │  LuxChildTopBar, phụ đề = mã lệnh
│    WO-1042                       │
├──────────────────────────────────┤
│ [Banner: danh sách đã lưu…]      │  chỉ khi isStale
│ Cột trên tuyến · 12              │  LuxSectionHeader
│ ┌──────────────────────────────┐ │
│ │ [3] POLE-0047             📷 │ │  cột kế tiếp: ô số nền primary
│ │     Bình thường              │ │
│ ├──────────────────────────────┤ │
│ │ [2] POLE-0046              › │ │  có kết quả AI: bấm mở Kết quả AI
│ │     Hỏng/Tắt · Đã tải lên    │ │
│ └──────────────────────────────┘ │
├──────────────────────────────────┤
│ Còn 10 cột chưa chụp  [Hoàn tất] │  thanh dưới
└──────────────────────────────────┘
```

- **Thanh tiêu đề:** `LuxChildTopBar(title = "Chụp ảnh cột đèn", subtitle = workOrderId, onBack)`. Không có chuông (màn con).
- **Banner dữ liệu cũ:** `LuxBanner` tone offline, giữ câu "Đang xem danh sách cột đã lưu trên máy, có thể chưa phải bản mới nhất".
- **Tiêu đề phần:** `LuxSectionHeader("Cột trên tuyến · N")`.
- **Thẻ:** một `LuxCard(contentPadding = 0)`, các dòng ngăn bằng `HorizontalDivider(outlineVariant)`.
- **Ô số thứ tự** (40dp, `shapes.small`): cột kế tiếp cần chụp `primary`/`onPrimary`; cột đã chụp `primaryContainer`/`onPrimaryContainer`; cột chưa tới `surfaceContainerHigh`/`onSurfaceVariant`. Cột chưa có GPS hiện "–" (giữ `poleNumberLabel`).
- **Nội dung dòng:** mã cột `titleMedium` (1 dòng, ellipsis) + `FlowRow` gồm badge tình trạng đèn (`fixtureStatus` từ server) và **tối đa 1 badge tiến độ** (`poleProgressBadge`, mục 6):

  | Ưu tiên | Điều kiện | Nhãn | Màu |
  |---|---|---|---|
  | 1 | `syncStatus` = FAILED | Đồng bộ lỗi | `status.sync[FAILED]` |
  | 2 | `syncStatus` = CONFLICT | Xung đột | `status.sync[CONFLICT]` |
  | 3 | `syncStatus` = QUEUED_* / SYNCING | nhãn sync sẵn có | `status.sync[…]` |
  | 4 | `hasPendingReview` | Đã xác nhận | `status.sync[QUEUED_ONLINE]` |
  | 5 | `hasPendingDecision` | Chờ duyệt | `status.sync[QUEUED_ONLINE]` |
  | 6 | `syncStatus` = DONE | Đã tải lên | `status.sync[DONE]` |
  | — | chưa chụp | (không badge) | |

  Badge luôn có icon + nhãn.
- **Hành động khi bấm dòng** (`poleRowAction`, mục 6):
  - Có kết quả AI (`syncStatus == DONE && serverPhotoId != null`): `OpenAiResult` — dòng có "›", `Role.Button`, `onClickLabel = "Xem kết quả AI"`. **Bỏ nút robot riêng.**
  - Chưa chụp, hoặc đã chụp nhưng chưa có kết quả AI, và tuyến chưa hoàn tất: `OpenReportSheet` — icon camera ở cuối dòng chỉ cho cột chưa chụp, `onClickLabel = "Chụp ảnh cột"`.
  - Tuyến đã hoàn tất và chưa có kết quả AI: `None` — dòng không bấm được.
- **Thanh dưới:** `surface` + viền trên `outlineVariant`; trái "Còn lại" (`bodyMedium`, `onSurfaceVariant`) + "N cột chưa chụp" (`titleMedium`); phải `PrimaryButton("Hoàn tất")`, khi `isCompleted` thành "Đã hoàn tất" và bị khoá.
- **Sheet chọn cách báo lỗi** (4 lựa chọn) và **sheet xác nhận Hoàn tất:** `ModalBottomSheet` giữ cấu trúc và câu chữ, đổi sang token (tiêu đề `titleLarge`, dòng lựa chọn 56dp với icon `primary`, nút `LuxSecondaryButton` + `PrimaryButton`). Bỏ `LabelTextStyle`, `Radius.*`, `cardOutline`.
- **4 trạng thái:**
  - Loading: `LuxLoadingRegion("Đang tải danh sách cột")` với skeleton 1 dòng tiêu đề + 5 dòng cột.
  - Empty: `LuxEmptyState("Tuyến này chưa có cột đèn nào", "Liên hệ Kỹ sư bảo trì nếu bạn cho rằng đây là nhầm lẫn.")`.
  - Error: `LuxErrorState(title = "Không tải được danh sách cột", message = mạng ? "Kiểm tra kết nối rồi thử lại." : "Vui lòng thử lại sau ít phút.")`, cần `PoleListUiState.Error.isNetworkError` (mục 6).
  - Success: như trên.

## 4. Màn Xem trước ảnh/video (tối cố định)

```
┌──────────────────────────────────┐
│ [✕]  ( POLE-0047 )               │  nút 48dp + chip overlay.onMedia
│                                  │
│      ┌──────────────────────┐    │
│      │   ảnh / video 3:4    │    │  viền outlineVariant
│      └──────────────────────┘    │
│                                  │
│ [ Chụp lại ]   [ Dùng ảnh này ]  │  LuxSecondaryButton + PrimaryButton
└──────────────────────────────────┘
```

- Nền `background` của Night Field (`#0D0D0D`), bỏ gradient `Navy900` và `PolePhotoPreviewBackground`.
- Hàng trên: `IconButton` Đóng 48dp (`contentDescription = "Đóng"`, khoá khi đang lưu); chip mã cột nền `LuxTheme.colors.overlayOnMedia`, chữ `labelMedium` trắng, `shapes.small` — ngoại lệ bán trong suốt duy nhất được phép (`DARK_NIGHT_FIELD.md` mục 3).
- Ảnh/video: giữ tỉ lệ và `ContentScale` hiện tại (Crop cho camera, Fit cho thư viện), `shapes.medium`, viền `outlineVariant`.
- Nút: `LuxSecondaryButton` ("Chụp lại" / "Chọn ảnh khác" / "Quay lại" / "Chọn video khác") + `PrimaryButton` ("Dùng ảnh này" / "Dùng video này", `isLoading` khi đang lưu → "Đang lưu…"). Bỏ `Blue500`, `PolePhotoConfirmSavingColor`, `RetakeButton`/`ConfirmButton` riêng.
- Hai màn ảnh/video dùng chung một composable `PoleMediaPreviewFrame(poleId, isSaving, retakeLabel, confirmLabel, onClose, onRetake, onConfirm, media: @Composable () -> Unit)` — đặt trong `polephoto/PoleMediaPreviewFrame.kt`.
- Màn chờ (chưa có file): `LuxLoadingRegion("Đang mở camera")` trên nền `background`.

## 5. Màn Kết quả AI

```
┌──────────────────────────────────┐
│ ←  Kết quả AI                    │  LuxChildTopBar
│    Cột 3 · POLE-0047             │
├──────────────────────────────────┤
│ [Banner: Kỹ sư bảo trì đã xác nhận] │  chỉ khi có decision
│ ┌──────────────────────────────┐ │
│ │          ảnh / video          │ │
│ └──────────────────────────────┘ │
│ Kết luận                         │
│ ┌ Đèn tắt · Theo AI · 87% ─────┐ │
│ Kỹ sư xác nhận nhãn              │  chỉ khi chưa có decision
│ ┌ [Đèn sáng | Đèn tắt]          ┐│
│ │ Ghi chú (bắt buộc khi khác AI)││
│ └ Đã lưu, sẽ gửi khi Hoàn tất  ┘│
│ ▸ Chi tiết kỹ thuật              │
├──────────────────────────────────┤
│ [ Xác nhận nhãn ]                │  nút gửi ghim ở đáy
└──────────────────────────────────┘
```

- **Thanh tiêu đề:** `LuxChildTopBar(title = "Kết quả AI", subtitle = "Cột N · $poleId")` (khi chưa có số: chỉ `poleId`). Bỏ hero + ô `#N`.
- **Banner dữ liệu cũ:** `LuxBanner` tone offline, giữ câu.
- **Quyết định của Kỹ sư bảo trì** (khi `decision != null`): `LuxBanner` — `confirm` tone success, `reject` tone error, khác → info; có icon + tiêu đề hiện có ("Kỹ sư bảo trì đã xác nhận/đã từ chối") + ghi chú + "Lúc …". Bỏ `PolePhotoDecisionCard` tự vẽ.
- **Ảnh/video:** giữ `PolePhotoPreview`, `PhotoViewerDialog`, `VideoPlayer`; đổi khung sang `shapes.medium` + viền `outlineVariant`.
- **Kết luận:** `LuxSectionHeader("Kết luận")` + `LuxCard`: nhãn hiệu lực (`final_cv_state ?? cv_state`) `titleLarge` + badge màu tình trạng (bật = `asset[NORMAL]`, tắt = `asset[OUT]`, không kết luận = `asset[UNKNOWN]`); dòng nguồn (`verdictSourceText` hiện có) + độ tin cậy `bodyMedium`.
- **Kỹ sư xác nhận nhãn** (chỉ khi `decision == null`): `LuxSectionHeader("Kỹ sư xác nhận nhãn")` + `LuxCard` chứa `LuxSegmented("Đèn sáng" | "Đèn tắt")` (thay `ReviewChoiceSegment`/`ReviewChoiceCard`), `OutlinedTextField` "Ghi chú" với dấu * đỏ khi bắt buộc + dòng lỗi "Cần ghi chú vì khác với nhận định của AI", dòng "Đã lưu, sẽ gửi khi Hoàn tất" khi có bản nháp. **Chỉ nút gửi** (`PrimaryButton`, nhãn `reviewActionLabel` hiện có) ghim ở đáy, `navigationBarsPadding` + `imePadding`.
- **Chi tiết kỹ thuật:** giữ mở rộng/thu gọn (`Role.Button`, `stateDescription` "Đang mở"/"Đang đóng"), `DetailRow` sang `bodyMedium`/`onSurfaceVariant`; nút "Chụp lại cột này" `LuxSecondaryButton` + `LuxConfirmDialog(destructive = true)` thay `AlertDialog`.
- **4 trạng thái:**
  - Loading: `LuxLoadingRegion("Đang tải kết quả AI")`, skeleton ảnh + 2 thẻ.
  - Empty (phòng thủ): `LuxEmptyState("AI chưa phân loại được ảnh này", "Model AI gặp lỗi khi xử lý, ảnh vẫn đã lưu trên hệ thống.")`.
  - Error: `LuxErrorState(title = "Không tải được kết quả AI", message = mạng/chung)`, cần `PolePhotoResultUiState.Error.isNetworkError`.
  - Success: như trên.
- Toast "Đã lưu, sẽ gửi khi Hoàn tất" giữ nguyên (`TopSlideToast`).

## 6. Thay đổi ngoài giao diện (nhỏ)

- Hàm thuần mới trong `polephoto/PoleRowLogic.kt`, có unit test:
  - `fun poleProgressBadge(row: PoleListRowState): PoleProgress?` — enum `PoleProgress { SYNC_FAILED, SYNC_CONFLICT, SYNCING, QUEUED_OFFLINE, QUEUED_ONLINE, REVIEW_SAVED, AWAITING_DECISION, UPLOADED }` theo thứ tự ưu tiên ở mục 3.
  - `fun poleRowAction(row: PoleListRowState, isCompleted: Boolean): PoleRowAction` — `OpenAiResult(serverPhotoId) | OpenReportSheet | None`.
- `PoleListUiState.Error` và `PolePhotoResultUiState.Error` thêm `isNetworkError: Boolean = false`; `PoleListViewModel` đặt `true` khi lỗi là `IOException`.
- Không đổi ViewModel nào khác, không đổi repository/Room/sync.

## 7. Test và chia commit

**Unit:** `PoleRowLogicTest` (mọi nhánh ưu tiên badge, 3 hành động), `PoleListViewModelTest` thêm 2 case lỗi mạng (danh sách, kết quả AI).

**UI (máy thật SM-A075F):** sửa `PoleListScreenTest`, `PolePhotoResultScreenTest` cho cấu trúc mới (bỏ assert theo tag/nhãn cũ đã bị thay), thêm:
- Bấm dòng có kết quả AI gọi `onViewAiResult`, dòng có "›" và `Role.Button`; dòng chưa chụp mở sheet; tuyến hoàn tất thì dòng chưa có AI không bấm được.
- Dòng có cả lỗi đồng bộ và bản nháp xác nhận chỉ hiện "Đồng bộ lỗi".
- Lỗi mạng ở 2 màn dùng đúng câu, không lộ tên exception.
- Kết quả AI: phần xác nhận là `LuxSegmented` (RadioButton), ghi chú bắt buộc khi khác AI, khi có `decision` thì không còn phần xác nhận và nút gửi.
- Cỡ chữ 1.5× ở 360dp: dòng cột, "Hoàn tất", lựa chọn Đèn sáng/Đèn tắt, nút gửi giữ vùng chạm ≥ 48dp.

Màn xem trước không có test Composable riêng (đã chốt 2026-10-18 trong CLAUDE.md vì phụ thuộc quyền camera và Intent); `PoleMediaPreviewFrame` là composable thuần nên có 1 test UI: nút Đóng ≥ 48dp, đang lưu thì khoá cả 3 nút.

**Chia commit (≤ 400 dòng mỗi commit):**
1. Hàm thuần + `isNetworkError` + unit test.
2. Danh sách cột: thanh tiêu đề, thẻ, dòng, hành động bấm dòng.
3. Danh sách cột: thanh dưới, 2 sheet, 4 trạng thái.
4. Xem trước: `PoleMediaPreviewFrame` + áp dụng cho màn ảnh và video.
5. Kết quả AI: thanh tiêu đề, banner quyết định, ảnh, thẻ Kết luận.
6. Kết quả AI: thẻ xác nhận nhãn + nút gửi ghim, chi tiết kỹ thuật, hộp thoại Chụp lại.
7. Kết quả AI: 4 trạng thái.
8. Test cỡ chữ lớn, chạy toàn bộ trên máy, dọn dẹp, cập nhật trạng thái đề xuất.

**Không làm trong đợt này** (ghi lại từ `AUDIT_REPORT.md` 6.4/6.5): đổi tiêu đề sheet "Báo lỗi cột", hợp nhất từ vựng "Đèn sáng/Đèn tắt" với "Bình thường/Đèn mờ/Hỏng-Tắt" (nguồn là `cv_state` thật của BE), sửa toast lẫn tiếng Anh "review" ở `PoleListRoute`, gộp 3 màn chụp khác (Ảnh sau sửa chữa, Đo GPS cột) vào `PoleMediaPreviewFrame`.

## 8. Quyết định của chủ dự án (2026-11-17)

| Mã | Câu hỏi | Chọn |
|---|---|---|
| P1 | Bấm dòng cột đã có kết quả AI | Mở Kết quả AI; bỏ nút robot riêng; cột chưa chụp vẫn mở sheet báo lỗi |
| P2 | Badge trên dòng cột | Giữ badge tình trạng đèn + tối đa 1 badge tiến độ theo thứ tự ưu tiên |
| P3 | Phần "Kỹ sư xác nhận nhãn" | Thẻ trong nội dung cuộn, chỉ ghim nút gửi ở đáy |
| — | F13 Hàng đợi đồng bộ | Để sau (chưa có màn, là tính năng mới) |
| — | Màn Hoàn thành lệnh | Bỏ qua (đã theo vNext trong F09 + `EvidenceCaptureScreen`) |
