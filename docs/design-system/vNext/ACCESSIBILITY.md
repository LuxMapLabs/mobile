# Accessibility: LuxMap Mobile Design System vNext

> Mức sàn **WCAG 2.2 AA** cho mọi thứ. **AAA (7:1)** ưu tiên cho nội dung chính và CTA (quyết định #4).
> Cột **"Kiểm tra"**: `Auto` = kiểm được bằng unit/UI test hoặc script · `Thủ công` = phải thử bằng người trên máy thật · `Cả hai`.

## 1. Checklist bắt buộc

| # | Yêu cầu | Ngưỡng | Áp dụng | Kiểm tra |
|---|---|---|---|---|
| 1 | Tương phản chữ | ≥4.5:1. **≥7:1** cho nội dung chính và chữ trên CTA | Mọi cặp chữ/nền, **riêng từng theme** Light và Night Field | Auto (script tương phản trên token) + Thủ công (chữ đặt trên ảnh/bản đồ) |
| 2 | Tương phản phi văn bản | ≥3:1 | Viền control (`outline`), icon mang nghĩa, focus ring, viền marker, thanh tiến độ | Auto (token) + Thủ công (marker trên vệ tinh) |
| 3 | Vùng chạm | ≥48×48dp | Mọi phần tử tương tác, gồm chip, marker, icon trong card | Cả hai (`assertTouchHeightIsAtLeast` / `assertTouchWidthIsAtLeast` trong Compose test) |
| 4 | Khoảng cách vùng chạm | ≥8dp giữa hai vùng chạm liền kề | Nút song song, chip, FAB | Thủ công |
| 5 | Icon-only button | Có `contentDescription` mô tả **hành động** ("Quay lại", "Định vị vị trí hiện tại") | Mọi `IconButton`, FAB, icon clickable | Auto (UI test tìm node theo mô tả) |
| 6 | Icon trang trí | `contentDescription = null` khi cạnh chữ cùng nghĩa | Icon trong badge, nút có chữ, list item | Review code |
| 7 | Role & state | `role` (Button/Tab/RadioButton/Checkbox/Switch) + `selected` / `toggleableState` / `stateDescription` | Mọi control dựng tay bằng `clickable`, segmented, tab lọc, outcome | Auto (semantics test) |
| 8 | Font scaling | Không mất nội dung tới **200%** | Toàn app. Ưu tiên 5 màn ở `TYPOGRAPHY.md` 4 | **Thủ công** |
| 9 | Reduced motion | Khi tắt hiệu ứng động (`ANIMATOR_DURATION_SCALE = 0` hoặc "Xoá hiệu ứng động"): bỏ chuyển cảnh fade, bỏ animate scroll, skeleton tĩnh | NavGraph transitions, `animateScrollToItem`, `TopSlideToast` | Thủ công |
| 10 | Không dùng màu làm tín hiệu duy nhất | Mọi trạng thái có **icon + chữ** (UI) hoặc **hình + glyph** (bản đồ) | Badge, due date, viền quá hạn, marker, route lỗi (nét đứt) | Thủ công (thang xám) |
| 11 | Lỗi có hướng khắc phục | Nói chuyện gì xảy ra, dữ liệu có an toàn không, cần làm gì. Có hành động (Thử lại/Sửa/Liên hệ) | Error state, banner lỗi, lỗi field, lỗi đồng bộ | Review nội dung |
| 12 | Lỗi được thông báo | `liveRegion` Polite (field) / Assertive (chặn hành động) | Banner, hint khóa CTA, lỗi submit | Thủ công (TalkBack) |
| 13 | Badge/đếm động | Đọc **câu có ngữ cảnh**, không đọc số trần | Chuông ("Thông báo, 3 chưa đọc"), số mục chờ đồng bộ | Auto + TalkBack |
| 14 | Thứ tự focus | Theo thứ tự thị giác + nghiệp vụ. Hint khóa đọc **trước** CTA | Bottom Action Bar, form | Thủ công (TalkBack, bàn phím ngoài) |
| 15 | Focus không bị che | Sticky bar, snackbar, sheet không che control đang focus | Form có Bottom Action Bar, bàn phím mở | Thủ công |
| 16 | Kéo có lựa chọn thay thế | Mọi thao tác kéo trên bản đồ có nút thay thế | Bản đồ, sheet | Thủ công |
| 17 | Đăng nhập | Cho phép trình quản lý mật khẩu và dán (paste) | F01 | Thủ công |
| 18 | Tiêu đề | Tiêu đề màn và section có `heading()` | `LuxTopBar`, tiêu đề section | Auto |
| 19 | Kiểm tra **cả Light và Night Field** | Không suy ra Dark từ Light | Mọi màn có dữ liệu | Thủ công (chụp cả 2 theme) |

## 2. Những gì không tự động hoá được: bắt buộc thử tay trước khi coi một màn là xong
1. **TalkBack**: đi hết luồng chính của màn bằng vuốt. Mọi control đọc đúng vai trò và trạng thái.
2. **Font 200%** + **Display size lớn nhất**: không cắt mã lệnh, mã cột, lý do khóa CTA, lỗi đồng bộ.
3. **Thang xám** (Developer options → Simulate color space → Monochromacy): phân biệt được mọi trạng thái.
4. **Ngoài trời ban ngày** (độ sáng 100%) và **ban đêm** (độ sáng 30%).
5. **Đeo găng** hoặc chạm bằng đốt ngón tay: bấm trúng tab, chip, nút trong sheet.
6. **Marker trên vệ tinh** ở vùng nông thôn thật: viền kép đủ nổi.

## 3. Ghi chú môi trường test
- `connectedDebugAndroidTest` hiện fail toàn bộ trên emulator local do lỗi môi trường (`InputManager.getInstance`, đã ghi nhận trước đây), không phải lỗi code. Kiểm tra a11y tự động cần chạy trên máy thật hoặc emulator khác.
- Bài học đã gặp: nút ở cuối nội dung cuộn dài trong sheet cần `performScrollTo()` trước `performClick()`.
