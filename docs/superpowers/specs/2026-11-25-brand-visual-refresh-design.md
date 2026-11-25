# Brand Visual Refresh — "Cột đèn" (A1)

Ngày: 2026-11-25 · Trạng thái: **ĐỀ XUẤT, chờ chủ dự án duyệt** · Phạm vi: toàn bộ app mobile · Khung thời gian: ~1 tuần

## 1. Bối cảnh và mục tiêu

Giảng viên review giao diện mobile (buổi chấm đồ án) và nhận xét: **nhìn đơn điệu, thiếu điểm nhấn, không có cảm giác thương hiệu, "nhạt", chưa được đầu tư**. Nguyên nhân chính là hướng "Civic Utility" của Design System vNext (2026-11-16) chủ động rất tiết chế.

**Mục tiêu:** tăng ấn tượng thị giác và bản sắc thương hiệu LuxMap ("ánh sáng đường làng") cho toàn bộ app, **không** làm giảm khả năng dùng ngoài hiện trường.

**Tiêu chí thành công:**
- Giảng viên không còn nhận xét "nhạt / thiếu thương hiệu" khi xem lại.
- Mọi màn có hero hoặc empty/error state đều dùng chung một ngôn ngữ thị giác.
- Tương phản vẫn đạt WCAG AA (AAA cho nội dung chính và CTA như vNext yêu cầu).
- Không đổi nghiệp vụ, navigation, field dữ liệu.

## 2. Quyết định đã chốt trong buổi brainstorm (2026-11-25)

| # | Quyết định | Ghi chú |
|---|---|---|
| D1 | Phạm vi: **toàn bộ app** | Hạn xem lại: ~1 tuần |
| D2 | Hướng: **A, giữ "không gradient"** | Quyết định vNext #5, #7, T-07 **được giữ nguyên**, không đảo |
| D3 | Biến thể thị giác: **A1 "Cột đèn"** | Hero navy phẳng + hình học + minh hoạ cột đèn vàng |
| D4 | Màu nhấn thứ hai **Lumen `#FFC44D`** chỉ là trang trí thương hiệu | Không mang ngữ nghĩa trạng thái; "Quá hạn" giữ màu cảnh báo cũ |
| D5 | Không đổi nghiệp vụ và navigation | Giữ quyết định vNext #12 |

Ghi chú lịch sử: trong buổi trao đổi, chủ dự án từng chọn hướng C (làm lại mạnh tay) rồi đổi lại thành A. **A là quyết định cuối.**

## 3. Phần 1 — Nền tảng

### 3.1 Token mới (theo quy tắc mở rộng, `vNext/README.md` mục 6)

| Token | Giá trị Light | Giá trị Night Field | Vai trò | Chỗ dùng |
|---|---|---|---|---|
| `Lumen` | `#FFC44D` | `#FFC44D` | Màu nhấn thương hiệu thứ hai | Nền/viền/minh hoạ cột đèn, chip lọc đang chọn, chỉ báo tab đang chọn |
| `OnLumen` | `#0E1E38` | `#0E1E38` | Chữ/icon trên nền Lumen | Chữ trong chip vàng |
| `HeroShapeA` | `#2A4A7F` | `#21375C` | Hoạ tiết hình tròn lớn trong hero | Chỉ trang trí |
| `HeroShapeB` | `#33558F` | `#2A4570` | Hoạ tiết hình tròn nhỏ trong hero | Chỉ trang trí |

**Tương phản (ước tính, phải khoá bằng test `ColorSchemeContrastTest`/`ExtendedColorsTest` trước khi dùng):**
- Lumen trên Navy700: ≈ 7,3:1 → đạt AA và AAA.
- OnLumen trên Lumen: rất cao (> 10:1).
- **Lumen trên nền trắng/xám nhạt: ≈ 1,6:1 → KHÔNG đạt.** Vì vậy Lumen **không bao giờ** dùng làm màu chữ hoặc icon đơn lẻ trên nền sáng; chỉ làm fill/viền (viền là trang trí, không mang thông tin riêng).
- Hai sắc `HeroShape*` là trang trí thuần, không cần tỉ lệ chữ.

### 3.2 Component dùng chung mới (`core/ui/components/`)

| Component | Mô tả | Trạng thái bắt buộc |
|---|---|---|
| `LuxHero` | Nền navy phẳng, 2 vòng tròn hình học (`HeroShapeA/B`), minh hoạ cột đèn Lumen. Slot: nhãn nhỏ, tiêu đề, phụ đề, nội dung đè (ô số liệu). Có biến thể **thu gọn** cho màn chi tiết | Light, Night Field |
| `LuxStatTile` | Ô số liệu nổi đè hero. Nền `surface`, viền `outlineVariant`. Ô cần nhấn dùng màu cảnh báo hiện có (amber + icon + nhãn), **không** dùng Lumen | Light, Night Field |
| `LuxEmptyIllustration` | Minh hoạ phẳng cùng ngôn ngữ cột đèn cho trạng thái rỗng/lỗi | Light, Night Field |

Quy tắc: component viết spec đầy đủ (mục đích, anatomy, kích thước, màu, states, a11y, anti-pattern) vào `COMPONENT_SPECIFICATIONS.md` **trước** khi code.

### 3.3 Điểm xung đột đã xử lý
Mockup ban đầu tô ô "Quá hạn" bằng vàng. Vàng Lumen dễ lẫn với màu cảnh báo/quá hạn (`warning` `#B45309`, `dueOverdue`). **Quyết định D4:** Lumen chỉ là thương hiệu; ngữ nghĩa trạng thái giữ nguyên bộ token hiện có và luôn kèm icon + nhãn.

## 4. Phần 2 — Thứ tự áp dụng

| Ngày | Việc | Màn / thành phần |
|---|---|---|
| 1 | Nền tảng | Token + test tương phản, `LuxHero`, `LuxStatTile`, `LuxEmptyIllustration`, cập nhật `docs/design-system/vNext/` |
| 2 | Ấn tượng đầu tiên | F01 Đăng nhập, F02 Việc hôm nay |
| 3 | Khảo sát + bản đồ | Hub Khảo sát, danh sách cột đèn, F12 Bản đồ (chỉ chrome/marker theo hướng đã duyệt) |
| 4 | Lệnh việc | F08 Danh sách công việc, F09/F10 Chi tiết lệnh (hero thu gọn) |
| 5 | Còn lại | F14 Thông báo, F15 Cá nhân, F11 Báo cáo sự cố, F13 Hàng đợi đồng bộ, chụp/xem ảnh cột |
| 6–7 | Dự phòng | Empty/error state có minh hoạ ở mọi màn, kiểm tra Night Field, kiểm tra máy thật, sửa lỗi nhỏ |

**Quy tắc xuyên suốt:**
- **Có hero:** Đăng nhập, Home, Hub Khảo sát, danh sách việc, Cá nhân, Thông báo. **Không hero:** màn thao tác tập trung (chụp ảnh, nộp báo cáo, camera) và bản đồ.
- **Màn chi tiết:** hero thu gọn, cuộn cùng nội dung, không chiếm chỗ của form.
- **Night Field:** hero chuyển sang `HeroShape` tối, giữ Lumen ở cột đèn; màn camera đêm không thêm gì.
- Mỗi bước chia commit ≤ 400 dòng, dừng chờ duyệt theo CLAUDE.md. Không tự mở rộng sang việc ngoài danh sách.

## 5. Phạm vi KHÔNG làm
- Không gradient, không M3 Expressive, không dynamic color (giữ #5, #7, #8).
- Không đổi nghiệp vụ, navigation, DTO, endpoint.
- Không thêm thư viện mới (minh hoạ dùng `ImageVector`/Canvas Compose).
- Không hiển thị dữ liệu chưa có (ví dụ khoảng cách km, tên người dùng giả) — mockup chỉ minh hoạ.
- Không gỡ `BrandHeroGradient` / primitive v2.1 thừa ở giai đoạn này (dọn dẹp riêng).

## 6. Rủi ro và cách kiểm soát

| Rủi ro | Kiểm soát |
|---|---|
| Hero ăn chiều cao màn hình, máy nhỏ (SM-A075F) phải cuộn nhiều | Chiều cao hero cố định có trần; kiểm tra danh sách vẫn thấy ≥ 1,5 thẻ việc không cần cuộn; ở màn chi tiết dùng biến thể thu gọn |
| Lumen bị dùng sai làm màu chữ/trạng thái | Chỉ expose qua component; review checklist: không `Lumen` trong text/icon đơn lẻ trên nền sáng |
| Font scale lớn làm vỡ ô số liệu | `LuxStatTile` độ rộng linh hoạt, kiểm tra ở font scale 1.3 |
| 1 tuần không đủ toàn app | Thứ tự ưu tiên giảng viên hay xem trước; ngày 6–7 là dự phòng, màn thuộc ngày 5 có thể dời |
| Test UI trên emulator local fail do lỗi môi trường (`InputManager`) | Kiểm tra bằng máy thật; unit test token chạy được ở JVM |

## 7. Kiểm thử
- **Unit:** mở rộng `ColorSchemeContrastTest`/`ExtendedColorsTest` cho `Lumen`, `OnLumen`, cặp Lumen-trên-navy ở cả hai theme; khẳng định không có cặp Lumen-làm-chữ-trên-nền-sáng.
- **Compose UI:** test 4 trạng thái (đang tải / dữ liệu / rỗng / lỗi) cho từng màn có hero, ở Light và Night Field.
- **Thủ công trên máy thật:** từng màn ở Light/Night Field, font scale mặc định và 1.3, kiểm tra chiều cao hero.
- Chạy `./gradlew ktlintCheck` sau mỗi bước; **không** tự chạy toàn bộ test suite khi chưa hỏi.

## 8. Tài liệu cần cập nhật khi triển khai
- `docs/design-system/vNext/README.md`: thêm mục duyệt bổ sung 2026-11-25 (D1–D5, Lumen), ghi rõ không đảo #5/#7/T-07.
- `COLOR_SYSTEM.md`: bảng 🆕 cho 4 token mới + tương phản đã đo.
- `COMPONENT_SPECIFICATIONS.md`: spec 3 component mới.
- `CLAUDE.md` (mục Design tokens): ghi nhận token mới và nguyên tắc "Lumen chỉ là trang trí thương hiệu".

## 9. Điểm còn mở (không chặn bắt đầu)
- **O1:** Minh hoạ cột đèn dùng một mẫu chung hay biến thể theo từng tab (Home, Khảo sát, Cá nhân)? Đề xuất: một mẫu chung, đổi vị trí/kích thước theo màn.
- **O2:** Màn Bản đồ có hero hay chỉ chrome mới? Đề xuất: chỉ chrome, không hero (đã nêu ở mục 4).
