# LuxMap Mobile: Nghiên cứu Design Direction

**Ngày:** 2026-11-16 · **Model:** Claude Opus 5.5 (`claude-opus-5-5`) · **Skill:** UI UX Pro Max v2.15.0
**Đi kèm:** `docs/design-system/AUDIT_REPORT.md` (audit hiện trạng)
**Loại tài liệu:** chỉ nghiên cứu. Không sửa code, không tạo component, không chốt token.

### Quy ước nguồn (đọc trước)
Mỗi nhận định đều gắn nhãn nguồn:

- **[SKILL]**: lấy nguyên văn hoặc tóm tắt trung thực từ output thật của `search.py` (lệnh ghi ở mục 2 và Phụ lục A).
- **[TÍNH]**: con số tôi tự tính (tỉ lệ tương phản WCAG 2.x bằng script riêng, Phụ lục B). Không phải output của skill.
- **[PHÂN TÍCH]**: lập luận thiết kế của tôi, dựa trên audit và nghiệp vụ LuxMap. Không phải output của skill.

---

## 1. Kiểm tra công cụ & skill

| Hạng mục | Kết quả | Cách kiểm |
|---|---|---|
| Python 3 | ✅ **3.12.10** tại `%LOCALAPPDATA%\Programs\Python\Python312\python.exe` | `python.exe --version` |
| Lệnh `python` gõ trần | ⚠️ vẫn trỏ vào alias Windows Store (`WindowsApps\python`), không phải bản thật | `which python`. Mọi lệnh ở đây dùng đường dẫn đầy đủ |
| UI UX Pro Max đã cài | ✅ plugin `ui-ux-pro-max@ui-ux-pro-max-skill` | `.claude-plugin/plugin.json` |
| Phiên bản | ✅ **2.15.0** (catalog `verifiedAt: 2026-11-04`) | `plugin.json`, `data/catalog-summary.json` |
| Dữ liệu | 80 style tìm được (51 active), 192 product/palette/reasoning, 74 cặp font, 1934 Google Fonts, 120 UX guideline, 22 stack / 1278 stack guideline | `catalog-summary.json` |
| Toàn vẹn dữ liệu | ✅ `validate_data.py`: "OK: validated 12 domain files, 22 stack files, and ui-reasoning.csv" | chạy trực tiếp |
| Test của skill | ✅ **213/213 pass** (`unittest discover -s tests`) | chạy trực tiếp |
| `search.py` | ✅ chạy được. Tham số thật đọc từ `--help`: `--domain`, `--stack`, `-n`, `--json`, `--full`, `--design-system`, `-p`, `-f ascii\|markdown`, `--persist`, `--page`, `-o`, `--force`, `--preview`, `--tokens dtcg\|css`, `--variance`, `--motion`, `--density`, `--rtl` | `search.py --help` |
| Design System Generator (`--design-system`) | ✅ chạy được, 6 lần (mục 2) | |
| Xuất token (`--tokens dtcg`) | ✅ chạy được, ra JSON W3C DTCG. ⚠️ **Không có định dạng Kotlin/Compose**, chỉ `dtcg` và `css` | |
| Hướng dẫn Jetpack Compose (`--stack jetpack-compose`) | ✅ 52 quy tắc, tra được | |
| Tham chiếu mobile | ✅ `references/pro-rules.md` (checklist native/mobile), `data/app-interface.csv` (tra qua `--domain web`) | |
| `--persist` / `--preview` | ⏸️ **Cố ý không chạy**: `--persist` ghi `design-system/<slug>/MASTER.md` vào repo, tức là chốt design system, việc thuộc giai đoạn sau | |

### Giới hạn thật của skill đối với LuxMap
Đây là các giới hạn quan sát được khi chạy, không phải phỏng đoán:

1. **Generator thiên về web/landing page.** Cả 6 lần chạy đều trả "Pattern" là bố cục landing page ("Hero headline > Benefit bullets > CTA", "Hero + Features + CTA", "Scroll-Triggered Storytelling"). Motion là snippet **GSAP/JS**. Checklist có "cursor-pointer", "hover states", breakpoint "375/768/1024/1440px". **Không phần nào trong số này áp dụng trực tiếp cho app Android native.**
2. **Khớp sản phẩm bị nhiễu với ngành tiện ích/bảo trì.** Truy vấn "utility infrastructure maintenance" ra 0 kết quả product. "maintenance work order" ra Portfolio/Personal và Remote Work. "energy utility smart city" ra Smart Home/IoT, Sustainable Energy và Sleep Tracker. **Bộ dữ liệu không có hàng nào cho "quản lý tài sản hạ tầng / field service".**
3. **Không có hướng dẫn nào cho làm việc ngoài trời, offline hay GIS.** Mỗi truy vấn đã thử lại 1 lần theo đúng quy định của skill:

   | Truy vấn | Kết quả |
   |---|---|
   | `"sunlight outdoor glare"`, `"outdoor sunlight"` | 0 |
   | `"offline state stale data"`, `"offline network"` | không liên quan, rồi 0 |
   | `"map location marker"` | ra "Breadcrumbs", không liên quan |

   Mọi khuyến nghị GIS, ngoài trời và offline ở tài liệu này đều là **[PHÂN TÍCH]**.
4. **Một số output tự mâu thuẫn hoặc không đạt chuẩn của chính skill:**
   - Truy vấn "night operations" trả palette tím/indigo ("Night indigo + dream violet"), trong khi quy tắc Government của chính skill liệt "AI purple/pink gradients" vào anti-pattern.
   - Primary `#4338CA` của palette đó trên nền `#0F172A` chỉ đạt **2.26:1** [TÍNH].
5. **Font gợi ý không hỗ trợ tiếng Việt.** Generator chọn **Atkinson Hyperlegible** cho ngành chính phủ. Theo `google-fonts.csv` của chính skill, font này chỉ có subset `latin | latin-ext`, **không có `vietnamese`** (bản "Next" cũng vậy).

---

## 2. Kết quả chạy Design System Generator (thật)

Tất cả chạy với `-p "LuxMap Mobile" -f markdown --full`. Output gốc lưu tạm ở scratchpad của phiên làm việc (không đưa vào repo). Các giá trị dưới đây chép đúng từ output.

| # | Truy vấn (+ dial) | Style [SKILL] | Palette [SKILL] | Typography [SKILL] | Đánh giá nhanh [PHÂN TÍCH] |
|---|---|---|---|---|---|
| DS1 | `government public infrastructure field operations` | **Accessible & Ethical** (Best For: Government, healthcare… public). Light ✓ / Dark ✓ | Primary `#0F172A`, Secondary `#334155`, Accent `#0369A1`, Bg `#F8FAFC`, Card `#FFFFFF`, Muted fg `#475569`, Border `#E2E8F0`, Destructive `#DC2626`. *"High contrast navy + blue"* | Atkinson Hyperlegible ×2 | **Phù hợp nhất về định hướng.** Font loại vì thiếu tiếng Việt |
| DS2 | `GIS utility asset management maintenance` | **Dark Mode (OLED)**. *Light: not-recommended* | Primary `#16A34A`, Accent `#DC2626`, Bg `#0F172A`, Card `#111827`, Border `#334155`. *"Operational green + incident red + maintenance amber"* | Cormorant Garamond / Crimson Pro (*"academia, victorian, leather"*) | Ý tưởng "trạng thái vận hành" có giá trị. **Font hoàn toàn sai ngữ cảnh** |
| DS3 | `field service technician mobile work orders` | **Motion-Driven** (Portfolio, storytelling…) | Monochrome `#18181B` + Accent `#2563EB` | Inter / Playfair Display (*"luxury, manifesto"*) | Không phù hợp, khớp nhầm ngành |
| DS4 | `government infrastructure maintenance field app` `--variance 2 --motion 2 --density 7` | **Minimalism & Swiss Style** (Best For: Enterprise apps, dashboards, professional tools) | Giống DS1 | Atkinson Hyperlegible | Dial đẩy style sang Swiss. Palette giữ nguyên. Motion trả snippet GSAP "Scroll Reveal" (không áp dụng được) |
| DS5 | `night operations monitoring dark` `--variance 3 --motion 2 --density 8` | Minimalism & Swiss Style | Primary `#4338CA`, Accent `#7C3AED`, Bg `#0F172A`, Card `#192134`. *"Night indigo + dream violet"* | Outfit / Work Sans | Palette vi phạm anti-pattern của chính skill. Outfit không có tiếng Việt |
| DS6 | `android material 3 enterprise mobile app` `--variance 3 --motion 3 --density 6` | Minimalism & Swiss Style | Primary `#2563EB`, Accent `#EA580C`. *"Trust blue + orange CTA"* | Roboto / Roboto | Trung tính, chung chung |

**Quy tắc suy luận Government** (`ui-reasoning.csv` #13) [SKILL]:
- Pattern `Minimal & Direct`. Style ưu tiên `Accessible & Ethical + Minimalism & Swiss Style`.
- Màu `Professional blue + High contrast`, chữ `Clear + Large typography`.
- Bắt buộc: `constraint:wcag-aaa`, `constraint:keyboard-navigation`.
- Anti-pattern: `Ornate design + Low contrast + Motion effects + AI purple/pink gradients`. Severity `HIGH`.

**Xuất token thử** (`--design-system --tokens dtcg`, DS1) [SKILL]: chạy được, ra JSON có `color.primary`, `on-primary`, `background`… ở light mode. Định dạng này dùng được làm nguồn tham chiếu, nhưng **không ánh xạ thẳng vào `ColorScheme` M3** (thiếu `*Container`, `surfaceContainer*`, `outlineVariant`, `inverse*`).

---

## 3. Khuyến nghị liên quan (tra theo domain)

### 3.1 Product [SKILL] (`--domain product`)
- **Government/Public Service:**
  - Primary style `Accessible & Ethical + Minimalism & Swiss Style`, secondary `Flat Design, Inclusive Design`.
  - Dashboard `Executive Dashboard`.
  - Màu `Professional blue + high contrast`.
- **Government Portal / Civic Services:** `Accessible & Ethical + Inclusive Design`. Màu `Professional blue + accessibility high contrast + service category colors`.
- **Logistics/Delivery** (gần nhất với "đội hiện trường + bản đồ"):
  - `Minimalism & Swiss Style + Flat Design`, secondary `Dark Mode (OLED)`.
  - Dashboard `Real-Time Monitoring + Route Analytics`.
  - Màu `Blue + Orange (tracking) + Green (delivered)`.
- **Status Page / Incident Management:** `Data-Dense Dashboard + Real-Time Monitoring`. Màu `Status green + incident red + maintenance amber + neutral dark`.
- Kết quả bị loại vì lạc ngành: Hyperlocal Services, Beauty/Spa, Drone Fleet (HUD/Sci-Fi), Smart Home (Glassmorphism).

### 3.2 Style [SKILL] (`--domain style`)

| Style | Best For | Light/Dark | Độ phức tạp | Điểm đáng chú ý |
|---|---|---|---|---|
| Accessible & Ethical | Government, healthcare, public, legal compliance | ✓ / ✓ | Low | Hướng tới tương phản 7:1+, chữ ≥16px, focus 3–4px, reduced motion |
| Minimalism & Swiss Style | Enterprise apps, dashboards, professional tools | ✓ / ✓ | Low | "Avoid shadows and gradients", lưới, chữ sans-serif |
| Flat Design | Mobile apps, dashboards, corporate | ✓ / ✓ | Low | No shadows, radius 0–4px, 4–6 màu đặc |
| Dark Mode (OLED) | Night-mode apps, low-light, eye-strain | **Light not-recommended** / ✓ | Low | Nền `#000`/`#121212`, accent sáng |
| Data-Dense Dashboard | Operational dashboards, enterprise reporting | ✓ / ✓ | Medium | Padding 8–12px, chữ 12–14px (**xung đột** với quy tắc ≥16sp của LuxMap) |
| Material 3 Expressive (Mobile) | Android/Pixel dùng M3 | ✓ / ✓ | Medium | Vibrant color, spring motion, shape tương phản, **cost: moderate (animation, blur)** |

### 3.3 Màu [SKILL] (`--domain color`)
- **Government/Public Service:** `#0F172A / #334155 / #0369A1 / #F8FAFC`, *"High contrast navy + blue"*.
- **Government Portal:** Primary `#1E40AF`, Secondary `#3B82F6`, Accent `#16A34A`, Bg `#EFF6FF`, Border `#BFDBFE`, *"Professional blue + service green"*.
- **Status/Incident:** Primary `#16A34A`, Accent `#DC2626`, *"Operational green + incident red + maintenance amber"*.
- **Smart Home/IoT (dark):** Bg `#0F172A`, Card fg `#F8FAFC`, Muted fg `#94A3B8`, Border `#475569`, Destructive `#EF4444`.

### 3.4 Typography [SKILL] (`--domain typography` + `google-fonts.csv`)
- Cặp **"Vietnamese Friendly"**: Heading **Be Vietnam Pro**, Body Noto Sans. *"Be Vietnam Pro excellent Vietnamese support. Noto as fallback."*
- Cặp **"Corporate Trust"**: Lexend + Source Sans 3 (*enterprise, government*).
- Cặp **"Accessibility First"**: Atkinson Hyperlegible ×2.
- Subset `vietnamese` theo dữ liệu skill:

  | Có `vietnamese` ✅ | Không có ❌ |
  |---|---|
  | Be Vietnam Pro, Inter, IBM Plex Sans, Noto Sans, Public Sans, Roboto, Source Sans 3, Lexend, Work Sans | Atkinson Hyperlegible, Atkinson Hyperlegible Next, Outfit |

### 3.5 UX & giao diện app [SKILL] (`--domain ux`, `--domain web`)
- **Mức Critical:**
  - Icon-only button phải có accessible label.
  - Input phải có nhãn hiển thị + accessibility label.
  - **Respect Reduced Motion** (*"Check reduceMotionEnabled and simplify animations"*).
  - Back Behavior dự đoán được.
- **Mức High:**
  - Light/Dark Contrast: *"Use semantic tokens and test both themes. Don't reuse light-theme grays directly in dark mode."*
  - Safe Area Insets.
  - Modal Escape (sheet phải có nút đóng rõ).
  - Color Only: *"Use icons/text in addition to color"*.
  - Contextual Live Badge Updates: đọc *"3 items…"* thay vì một con số trần.
  - Compact Label Overflow: badge/chip giữ 1 dòng, lộ toàn văn khi bị cắt.
  - Loading Indicators theo thời gian chờ thực tế.
  - Error Messages được announce.
- **Mức Medium:**
  - Error Recovery: *"Provide clear next steps"*.
  - Touch Spacing ≥8dp.
  - Bottom Tabs ≤5.
  - Preserve Screen State.
  - Limited Continuous Motion.
- **Touch Target Size:** *"Use 44pt on iOS and 48dp on Android"*.

### 3.6 Jetpack Compose [SKILL] (`--stack jetpack-compose`)
| Guideline | Do | Don't | Severity |
|---|---|---|---|
| Design system | Material3 tokens | Hardcoded values | High |
| Dark mode support | `colorScheme` | Fixed color | Medium |
| No hardcoded text style | `MaterialTheme.typography` | Hardcode sp | Medium |
| Semantics / Content description | `Modifier.semantics` / `contentDescription` | Ignore a11y | Medium |
| Lifecycle-aware collect | `collectAsStateWithLifecycle` | `collectAsState` | High |
| Use key in Lazy | `key = id` | No key | High |

### 3.7 Chart [SKILL] (`--domain chart`, "time series trend mobile")
Line Chart. *"Use solid, dashed, and dotted line styles plus direct series labels; never distinguish series by hue alone."* Áp dụng cho biểu đồ xu hướng độ sáng ở Chi tiết cột.

---

## 4. Ba hướng thị giác đề xuất

Cả ba đều **[PHÂN TÍCH]**, được dựng từ các style mà skill trả về [SKILL]. Ba hướng khác nhau thật ở **chế độ mặc định**, **cách xử lý bề mặt** và **mức bám platform**, không chỉ khác bảng màu.

### Hướng A: "Civic Utility" (Accessible & Ethical + Minimalism & Swiss)
*Nguồn style: DS1, DS4, product Government [SKILL].*

| Khía cạnh | Mô tả |
|---|---|
| Triết lý | Công cụ công vụ: rõ ràng trên hết, trang trí tối thiểu, mỗi phần tử đều có lý do. Giống biểu mẫu hành chính được làm tốt: ai cũng đọc được, không cần học |
| Màu | Light-first. Nền trung tính slate. **Một màu thương hiệu navy** cho hành động chính và điều hướng. Màu trạng thái *chỉ* dùng cho trạng thái. Không gradient (trừ màn đăng nhập, nếu giữ) |
| Typography | Be Vietnam Pro. Thang chữ lớn (body 16sp, tiêu đề 20sp). Tương phản cao. Chữ số đọc nhanh (cần kiểm tra tabular figures) |
| Bề mặt | Phẳng. Card trắng viền 1px. Không shadow, hoặc elevation tonal rất nhẹ. Radius vừa phải (8–12dp) |
| Component | M3 component với style tiết chế: nút chữ nhật bo 8–12dp, badge chữ nhật bo nhẹ, list row có divider, header phẳng một màu |
| Mật độ | Trung bình (dial density ~6–7): đủ thông tin trên 1 thẻ để quyết định, không dồn thành dashboard |
| GIS | Tốt: chrome giao diện trung tính nên màu marker trên bản đồ không bị lấn át |
| Ưu điểm | Khớp chính xác quy tắc Government của skill. Gần với v2.0 hiện tại (Navy700/Gray*) nên chi phí chuyển đổi thấp. Ít rủi ro tương phản. Bền theo thời gian, không lỗi mốt |
| Nhược điểm | Dễ trông "khô" nếu làm thiếu tinh tế. Light-first chưa tự giải quyết ca làm đêm: **bắt buộc thiết kế Dark nghiêm túc kèm theo**, không chỉ đảo màu |

### Hướng B: "Night Operations" (Dark Mode OLED + vận hành thời gian thực)
*Nguồn style: DS2, product Status/Incident + Logistics (secondary Dark OLED) [SKILL].*

| Khía cạnh | Mô tả |
|---|---|
| Triết lý | Trung tâm điều hành ngoài hiện trường: tối mặc định, bản đồ là nhân vật chính, trạng thái phát sáng nổi trên nền đen. Tối ưu cho ca đêm và giữ thị lực ban đêm |
| Màu | Dark-first. Nền gần đen (`#0D0D0D`/`#121212`). Accent sáng (teal `Green400` hiện có, đạt 9.26:1 trên nền tối [TÍNH]). Bộ trạng thái chỉnh riêng cho nền tối |
| Typography | Be Vietnam Pro. Weight nặng hơn một bậc trên nền tối để bù hiện tượng "chữ mảnh tan vào nền" |
| Bề mặt | Lớp surface tối phân cấp bằng độ sáng (tonal), viền tối. Không shadow |
| Component | Overlay/HUD nổi trên bản đồ, chip trạng thái phát sáng, CTA tương phản cao |
| Mật độ | Cao (dial ~8): nhiều trạng thái trong một khung nhìn |
| GIS | Rất tốt vào ban đêm với nền vệ tinh/bản đồ tối. Kém với nền vector sáng |
| Ưu điểm | Khớp thời điểm làm việc thực tế (khảo sát đèn **phải** làm ban đêm). Tiết kiệm pin OLED. Bản đồ nổi bật |
| Nhược điểm | Skill tự ghi *"Light not-recommended"*, nghĩa là ban ngày (đi sửa chữa, nghiệm thu dưới nắng) chữ sáng trên nền tối khó đọc hơn khi chói [PHÂN TÍCH]. Thẩm mỹ "tactical" dễ trượt sang kiểu HUD/sci-fi, không hợp tính trang trọng của cơ quan nhà nước. Palette night mà skill trả về thất bại tương phản (2.26:1). Dark-first đảo ngược quyết định Light mặc định đã có |

### Hướng C: "Material 3 Utility" (bám sát Material 3, gồm cả Expressive)
*Nguồn style: style "Material 3 Expressive (Mobile)", DS6 [SKILL].*

| Khía cạnh | Mô tả |
|---|---|
| Triết lý | Làm "Android đúng chuẩn": dùng tối đa hệ màu tonal, shape và motion của Material 3, ít tùy biến riêng. Người dùng Android thấy quen ngay |
| Màu | Bảng tonal M3 sinh từ 1 seed navy: `primary/primaryContainer/secondaryContainer/surfaceContainer*`. **Không dùng dynamic color** (theo quyết định đã có) |
| Typography | Thang M3 chuẩn 15 slot, map sang Be Vietnam Pro |
| Bề mặt | Surface container theo tầng. Bo góc lớn (16–28dp). Nút dạng pill. State layer khi nhấn |
| Component | M3 nguyên bản: `Card`, `FilterChip`, `SegmentedButton`, `NavigationBar`, `ModalBottomSheet`, `ListItem`… |
| Mật độ | Trung bình–thấp (M3 mặc định khá thoáng) |
| GIS | Trung tính. Chip/FAB nổi trên bản đồ theo chuẩn M3 |
| Ưu điểm | Ít code tùy biến nhất. Accessibility và semantics có sẵn trong component M3, nên **giải quyết nhanh nhóm lỗi A3/A4** của audit. Dễ bảo trì cho đội sinh viên |
| Nhược điểm | Bản *Expressive* (vibrant, spring motion, shape tương phản) đi ngược anti-pattern "Motion effects" của quy tắc Government, và skill ghi *cost: moderate (animation, blur)*. Container tonal nhạt dễ cho tương phản thấp (indicator Navy50 hiện chỉ 1.12:1 [TÍNH]). Dễ trông giống "app Google" hơn là công cụ chính quyền. Bo góc lớn, pill tốn chỗ, giảm mật độ thông tin |

---

## 5. Đánh giá so sánh

Thang điểm 1–5 (5 là tốt nhất) theo **yêu cầu thật của LuxMap** lấy từ audit và CLAUDE.md. Trọng số phản ánh mức ảnh hưởng tới Field Engineer. **Toàn bộ bảng này là [PHÂN TÍCH].**

| Tiêu chí | Trọng số | A Civic Utility | B Night Ops | C M3 Utility | Lý do chính |
|---|---:|---:|---:|---:|---|
| Đọc được ngoài trời ban ngày (sửa chữa, nghiệm thu) | 3 | **5** | 2 | 4 | Nền sáng + chữ tối chống chói tốt nhất. B ngược lại |
| Dùng ban đêm (khảo sát, chụp cột) | 3 | 4* | **5** | 4* | *A/C đạt được **nếu** có Dark theo ngữ cảnh thiết kế riêng |
| Trang trọng, tin cậy (phần mềm nhà nước) | 3 | **5** | 2 | 3 | Quy tắc Government của skill; B có rủi ro "tactical/HUD"; C Expressive quá "vui" |
| Accessibility (WCAG AA bắt buộc, AAA khi khả thi) | 3 | **5** | 3 | 4 | A rủi ro thấp nhất. B dễ trượt tương phản màu trạng thái. C có semantics sẵn nhưng container nhạt |
| GIS: marker/bản đồ dễ đọc | 2 | 4 | 4 | 3 | A/B có chrome trung tính. C có FAB/chip màu container chen vào bản đồ |
| Mật độ thông tin vừa đủ cho quyết định | 2 | 4 | 4 | 3 | C mặc định quá thoáng |
| Chi phí chuyển đổi từ code hiện tại (v2.0) | 2 | **5** | 2 | 3 | A gần v2.0. B đảo mặc định Light. C đổi shape/màu toàn bộ |
| Bảo trì lâu dài (đội nhỏ) | 2 | 4 | 3 | **5** | C bám component M3 nhiều nhất |
| Hiệu năng / pin | 1 | 4 | **5** | 3 | B: OLED. C: motion/blur |
| **Tổng có trọng số** (tối đa 105) | | **95** | **67** | **76** | |

Cách tính (điểm × trọng số):
- A = 15+12+15+15+8+8+10+8+4 = **95**
- B = 6+15+6+9+8+8+4+6+5 = **67**
- C = 12+12+9+12+6+6+6+10+3 = **76**

Lưu ý độ nhạy: điểm "ban đêm" (4*) và "accessibility" (5) của A phụ thuộc vào việc Dark được thiết kế nghiêm túc. Nếu Dark làm kém (ban đêm 2, accessibility 3), A còn 83 nhưng vẫn đứng đầu.

**Quan sát chính:**
1. Điểm yếu thật duy nhất của A là ca đêm, và nó được giải quyết bằng cách lấy **đúng phần mạnh nhất của B** (Dark theo ngữ cảnh) mà không lấy phần rủi ro của B (Dark-first, thẩm mỹ tactical).
2. Điểm mạnh thật của C (component M3 có a11y sẵn, dễ bảo trì) **không mâu thuẫn với A**. Đó là chuyện cài đặt, không phải chuyện thẩm mỹ. Phần bị loại của C là lớp *Expressive* (spring motion, shape tương phản, màu vibrant).

---

## 6. Hướng đề xuất

**Đề xuất: Hướng A "Civic Utility" làm nền thị giác, kèm 2 bổ sung có điều kiện.**

1. **Từ B: Dark "Night Field" là chế độ hạng nhất, không phải bản đảo màu.** Áp dụng theo đúng bảng "Dark Mode theo ngữ cảnh" đã có trong CLAUDE.md:
   - Màn chụp/quay cột luôn Dark.
   - F09 Dark khi điều hướng đêm.
   - Các màn khác theo hệ thống.

   Dark có bảng trạng thái riêng, đã kiểm tương phản (mục 7).
2. **Từ C: cài đặt bằng component Material 3**, style tiết chế (không Expressive, không dynamic color). Mục đích là hưởng sẵn semantics, `minimumInteractiveComponentSize` và state layer, đúng với quy tắc Compose "Material3 tokens, not hardcoded values" [SKILL].

**Vì sao không chọn theo "đẹp" [PHÂN TÍCH]:**
- B nhìn ấn tượng nhất trên ảnh mock bản đồ đêm, nhưng thua ở 3 tiêu chí trọng số cao: đọc ngoài trời ban ngày, tính trang trọng, chi phí chuyển đổi.
- C là lựa chọn "an toàn kỹ thuật", nhưng phần Expressive đi ngược anti-pattern Government của chính skill.
- A khớp cả quy tắc suy luận của skill (`Government/Public Service → Accessible & Ethical + Minimalism & Swiss`) lẫn ràng buộc thực tế của LuxMap (gần v2.0, Light mặc định đã chốt, chữ ≥16sp, 48dp).

**Hướng này không phải là "giữ nguyên v2.0".** So với hiện trạng, nó yêu cầu:
- Bỏ gradient hero ở mọi màn trừ F01 (có thể bỏ cả ở F01).
- Một kiểu header duy nhất.
- Dark được thiết kế lại hoàn toàn cho mọi bộ trạng thái.
- Đủ 15 slot typography.
- Hệ component lõi thay cho `background + border` tự dựng.

---

## 7. Cân nhắc về màu và typography

### 7.1 Màu thương hiệu: giữ Navy700 hay theo palette của skill?

| Ứng viên | Nguồn | Trên nền trắng [TÍNH] | Nhận xét [PHÂN TÍCH] |
|---|---|---:|---|
| `#1F3864` Navy700 (hiện tại) | Design System v2.0 | **11.62:1** | Đạt AAA. Đã là nhận diện LuxMap (F01, nav, Web GIS nếu dùng chung). Đề xuất **giữ** |
| `#0F172A` + accent `#0369A1` | DS1 [SKILL] | 17.06:1 / 5.93:1 | Gần đen, ít "thương hiệu" hơn. Accent `#0369A1` chỉ đạt AA (5.93:1) |
| `#1E40AF` | Government Portal [SKILL] | 8.72:1 | Xanh "cổng dịch vụ công" sáng hơn. Khác Web GIS |

→ Đây là **quyết định của chủ dự án** (Q1, mục 10). Mặc định đề xuất giữ Navy700 vì thương hiệu và chi phí chuyển đổi.

### 7.2 Màu trạng thái trên nền Dark: các điểm phải sửa [TÍNH]

| Vai trò | Hiện tại trên `#1A1A1A` | Ứng viên Dark | Tương phản |
|---|---:|---|---:|
| Lỗi / Quá hạn | `#DC2626` **3.60:1** ❌ | `#F87171` hoặc `#FF9A9A` (đã có, `ErrorDark`) | 6.29 / **8.57:1** ✅ |
| Hôm nay | `#B45309` **3.47:1** ❌ | `#F7C66D` (đã có trong bộ badge DIM Dark) | **10.98:1** ✅ |
| Accent / điều hướng | `Green400 #5FC4B0` | giữ | 9.26:1 ✅ |
| Chữ phụ | `#B3B3B3` | giữ (`#A0A0A0` = 6.66:1 vẫn đạt) | 8.30:1 ✅ |

Nguyên tắc [SKILL, Light/Dark Contrast]: *"Don't reuse light-theme grays directly in dark mode."* Mỗi bộ trạng thái (`SyncStatus`, `WorkOrderStatus`, `Priority`, `TaskKind`) cần cặp Dark riêng, giống `AssetCondition`.

### 7.3 Viền và chỉ báo: vấn đề tương phản phi văn bản [TÍNH]
- Viền card `Gray200` trên trắng **1.23:1**, `Dark700` trên `Dark800` **1.21:1**. Mức này chấp nhận được cho viền trang trí, **không đủ** cho ranh giới mang thông tin, ví dụ ô nhập liệu hay toggle (WCAG 1.4.11 yêu cầu ≥3:1).
- Chỉ báo tab bottom nav `Navy50` trên trắng **1.12:1**. Cần dựa thêm vào chữ/icon đổi màu, hoặc tăng đậm chỉ báo.
- [PHÂN TÍCH] Tách 2 token: `outline` (≥3:1, cho control) và `outlineVariant` (trang trí). Đây đúng là cặp role M3 đang thiếu (audit 5.1).

### 7.4 AA hay AAA?
Quy tắc Government của skill ghi `must_have: constraint:wcag-aaa` [SKILL]. [PHÂN TÍCH] AAA cho **mọi** chữ (7:1) là khả thi với chữ chính (Navy700 11.6:1, Gray900 rất cao, DarkText 15.96:1), nhưng sẽ loại phần lớn màu chữ trạng thái và chữ phụ:
- `#64748B` chỉ 4.76:1.
- `#0369A1` 5.93:1.

Đề xuất: **AA là mức sàn bắt buộc cho mọi thứ, AAA cho chữ nội dung chính và CTA.** Cần chủ dự án duyệt (Q3).

### 7.5 Typography
- **Giữ Be Vietnam Pro** [SKILL xác nhận qua cặp "Vietnamese Friendly"]. Đã bundle 4 file `.ttf` offline, khớp nguyên tắc offline-first.
- **Loại Atkinson Hyperlegible** dù generator gợi ý: không có subset tiếng Việt [SKILL, dữ liệu `google-fonts.csv`]. Chữ có dấu sẽ rơi về font hệ thống, đúng lỗi mà audit đã ghi nhận (P-02).
- Ứng viên dự phòng nếu muốn đổi: Public Sans (font chính phủ Mỹ, có tiếng Việt), Lexend + Source Sans 3 ("Corporate Trust"), Noto Sans (pairing của skill). **Đề xuất không đổi** [PHÂN TÍCH]: Be Vietnam Pro được thiết kế cho tiếng Việt, đổi font không giải quyết vấn đề nào trong audit.
- Vấn đề thật cần sửa là **cấu trúc** (P-02): 15 slot M3, bỏ 10 `*TextStyle` rời, sàn 14sp cho caption/badge, 16sp cho nội dung và hành động.
- [PHÂN TÍCH] Cần kiểm tra Be Vietnam Pro có `tnum` (chữ số đều độ rộng) cho cột số liệu (lux, %, đếm) hay không, trước khi chốt token.

---

## 8. Cân nhắc riêng cho GIS

> Skill **không có** hướng dẫn GIS/bản đồ (mục 1, giới hạn #3). Toàn bộ mục này là **[PHÂN TÍCH]**, có số tương phản [TÍNH] để kiểm chứng.

### 8.1 Marker trạng thái: phát hiện quan trọng [TÍNH]

| Cặp | Tương phản |
|---|---:|
| `Green400` (bình thường) với `Amber500` (đèn mờ) | **1.03:1** |
| Marker Green400 / Amber500 / Rose600 trên nền vector sáng (≈trắng) | 2.10 / 2.17 / 4.38:1 |
| Marker Green400 / Amber500 / Rose600 trên nền vệ tinh tối (ước lượng `#2B3A2F`) | 5.72 / 5.54 / **2.74:1** |

Hệ quả:
- "Bình thường" và "Đèn mờ" có **độ chói gần như bằng nhau**. Người mù màu đỏ-lục, hoặc khi nhìn dưới ánh sáng yếu, gần như không phân biệt được nếu chỉ dựa vào màu. **Badge glyph hiện có là bắt buộc, không phải trang trí.** Nên tăng thêm tín hiệu hình dạng (ví dụ marker OUT có viền đậm hoặc hình khác).
- Không có màu nào đạt ≥3:1 trên **cả hai** loại nền. Đề xuất **viền/quầng tương phản (halo)** quanh marker: viền trắng trên nền tối, viền tối trên nền sáng, hoặc viền kép cố định. Đây là kỹ thuật chuẩn của bản đồ.
- Áp dụng đúng nguyên tắc [SKILL] *"Color Only: Use icons/text in addition to color"* và *"Icon Contrast: meaningful icons need at least 3:1 against adjacent colors"* (pro-rules).

### 8.2 Nền bản đồ & chrome
- **Nền mặc định là vector**, vệ tinh là lựa chọn phụ. *Đính chính 2026-11-16:* code **đã** làm đúng như vậy (`MapScreen.kt:224`). Bản audit đầu ghi nhầm là vệ tinh, nên không cần thay đổi hành vi, chỉ cần giữ nguyên.
- Nút đổi nền phải hiện **trạng thái hiện tại** kèm icon (audit G3), không hiện "đích đến".
- Chrome trên bản đồ (search, FAB, chip KPI, legend) dùng **surface đặc có viền**, không trong suốt/blur. Lý do: khả năng đọc trên nền ảnh bất kỳ, hiệu năng GL, và anti-pattern blur của Government.
- Cân nhắc **style bản đồ tối** cho ca đêm, đồng bộ với Dark "Night Field". Cần kiểm tra OpenFreeMap/MapTiler có style tối miễn phí hay không, trước khi đưa vào phạm vi.

### 8.3 Tương tác
- Marker là vùng chạm nhỏ. **Hit area ≥48dp** quanh tâm marker và chọn marker gần nhất khi chạm, không đòi chạm trúng pixel.
- Quick view là bottom sheet có **nút đóng rõ ràng** [SKILL, Modal Escape], không chỉ vuốt.
- Mọi thao tác kéo (pan) phải có lựa chọn thay thế bằng nút: "Về vị trí của tôi", "Về địa bàn", danh sách kết quả tìm kiếm [SKILL quick-reference, `dragging-alternative`].

### 8.4 Thông tin IoT
- Mỗi số liệu IoT kèm **thời điểm báo cuối** và **trạng thái "dữ liệu cũ"** khi quá ngưỡng. Skill có nguyên tắc tương tự ở pattern Real-Time/Operations: *"Label telemetry as live only when backed by a current source, with update time and stale state"* [SKILL, DS2 pattern].
- Biểu đồ xu hướng: đường liền/nét đứt + nhãn trực tiếp, không chỉ dựa vào màu [SKILL, chart].

### 8.5 Ngoài phạm vi thiết kế thị giác
Bản đồ offline (audit G1) và lớp WorkOrder trên bản đồ (G6) là **quyết định sản phẩm**, không phải quyết định design system. Ghi lại để không bị lẫn vào việc chọn hướng thị giác.

---

## 9. Cân nhắc về Accessibility

### 9.1 Mức sàn đề xuất (gộp [SKILL] + yêu cầu đã có của LuxMap)

| Hạng mục | Mức | Nguồn |
|---|---|---|
| Tương phản chữ thường | ≥4.5:1 ở **cả Light và Dark**, kiểm riêng từng theme | [SKILL] Light/Dark Contrast; pro-rules |
| Tương phản phi văn bản (control, icon mang nghĩa, marker) | ≥3:1 | [SKILL] pro-rules "Icon Contrast" |
| Vùng chạm | **48dp** Android (LuxMap đã chốt 48dp, không dùng 44) + khoảng cách ≥8dp | [SKILL] Touch Target Size, Touch Spacing |
| Cỡ chữ | Nội dung/hành động ≥16sp; caption/badge ≥14sp; không dưới 14sp | LuxMap CLAUDE.md; [SKILL] style Accessible "16px+" |
| Màu không là tín hiệu duy nhất | Icon/chữ/hình dạng kèm màu | [SKILL] Color Only |
| Semantics | Control tự dựng phải có `Role` + `selected/stateDescription`; icon-only có nhãn | [SKILL] Icon Button Labels (Critical), Compose Semantics |
| Reduced motion | Tôn trọng thiết lập "Xoá hiệu ứng động" của hệ thống | [SKILL] Respect Reduced Motion (Critical) |
| Badge/đếm động | Đọc cả câu có ngữ cảnh ("3 thông báo chưa đọc") | [SKILL] Contextual Live Badge Updates |
| Lỗi | Có đường phục hồi (Thử lại / bước tiếp theo) + được announce | [SKILL] Error Recovery, Error Messages |
| Font scale | Không vỡ layout ở cỡ chữ hệ thống lớn nhất; badge giữ 1 dòng hoặc lộ toàn văn | [SKILL] Compact Label Overflow; pro-rules checklist |

### 9.2 Đặc thù hiện trường [PHÂN TÍCH, skill không có dữ liệu]
- **Dưới nắng:** độ chói màn hình giới hạn tương phản thực tế, nên ưu tiên mặt sáng + chữ tối ở các màn ban ngày, tránh chữ xám nhạt, tránh truyền tải thông tin bằng sắc độ tinh tế.
- **Ban đêm:** tránh màn trắng toàn phần ở màn đêm (lóa, mất thích nghi bóng tối). Tránh accent bão hoà cao chiếm diện tích lớn.
- **Đeo găng / một tay:** CTA chính đặt vùng dưới màn hình (đã có `BottomActionBar`). Không đặt hành động phá hủy cạnh hành động chính.
- **Mạng yếu:** trạng thái "đã lưu trên thiết bị" khác "đã đồng bộ" phải luôn nhìn thấy được. Đây là quy tắc đã có của LuxMap, cần component chuẩn.

---

## 10. Câu hỏi cần chủ dự án duyệt

| # | Câu hỏi | Đề xuất mặc định |
|---|---|---|
| **Q1** | **Duyệt hướng A "Civic Utility"** (+ Dark "Night Field" theo ngữ cảnh + cài đặt bằng M3 tiết chế)? | Duyệt |
| Q2 | Màu thương hiệu: giữ **Navy700 `#1F3864`**, hay theo palette của skill (`#0F172A`+`#0369A1`, hoặc `#1E40AF`)? | Giữ Navy700 |
| Q3 | Mức tương phản: **AA sàn + AAA cho chữ chính/CTA**, hay AAA toàn bộ như quy tắc Government của skill? | AA sàn + AAA chữ chính |
| Q4 | Gradient hero: bỏ ở mọi màn, hay chỉ giữ ở F01 Đăng nhập? | Chỉ giữ F01, hoặc bỏ hẳn |
| Q5 | Font: giữ Be Vietnam Pro? (Atkinson Hyperlegible bị loại vì không có tiếng Việt) | Giữ |
| Q6 | Nền bản đồ mặc định là **vector**, vệ tinh là tùy chọn? (*Đính chính:* code đã đúng như vậy, chỉ cần xác nhận giữ nguyên) | Đồng ý |
| Q7 | Marker: thêm **halo/viền tương phản** và tín hiệu hình dạng cho OUT? (thay đổi asset `ic_marker_*.xml`) | Đồng ý |
| Q8 | Có khảo sát **style bản đồ tối** cho ca đêm không? (cần kiểm tra nguồn miễn phí) | Khảo sát, không cam kết |
| Q9 | Bổ sung các câu còn mở từ audit (Phụ lục A của AUDIT_REPORT): gỡ CTA chưa có backend ở Chi tiết cột, tên hiển thị của Manager, IA của "Việc hôm nay", vai trò cảm biến BLE, bản đồ offline | Xem audit |
| Q10 | Có cho phép dùng `--persist` của skill để sinh `design-system/luxmap-mobile/MASTER.md` làm tài liệu tham chiếu ở bước tiếp theo, biết rằng nội dung phải sửa tay (font, pattern landing page)? Hay viết spec design system bằng tay hoàn toàn? | Viết tay, dùng output skill làm tham chiếu |

**Dừng tại đây.** Chưa xây dựng design system và chưa chốt token cho tới khi Q1–Q8 được duyệt.

---

## Phụ lục A: Lệnh đã chạy (để tái lập)

```bash
PY="$LOCALAPPDATA/Programs/Python/Python312/python.exe"
S="C:/Users/Thinkpad/.claude/plugins/cache/ui-ux-pro-max-skill/ui-ux-pro-max/2.15.0/.claude/skills/ui-ux-pro-max/scripts/search.py"

# Kiểm tra
"$PY" "$S" --help
"$PY" ".../scripts/validate_data.py"
"$PY" -m unittest discover -s ".../scripts/tests"            # 213 tests OK

# Design System Generator
"$PY" "$S" "government public infrastructure field operations" --design-system -p "LuxMap Mobile" -f markdown --full
"$PY" "$S" "GIS utility asset management maintenance"          --design-system -p "LuxMap Mobile" -f markdown --full
"$PY" "$S" "field service technician mobile work orders"       --design-system -p "LuxMap Mobile" -f markdown --full
"$PY" "$S" "government infrastructure maintenance field app"   --design-system -p "LuxMap Mobile" -f markdown --full --variance 2 --motion 2 --density 7
"$PY" "$S" "night operations monitoring dark"                  --design-system -p "LuxMap Mobile" -f markdown --full --variance 3 --motion 2 --density 8
"$PY" "$S" "android material 3 enterprise mobile app"          --design-system -p "LuxMap Mobile" -f markdown --full --variance 3 --motion 3 --density 6
"$PY" "$S" "government public infrastructure field operations" --design-system --tokens dtcg -p "LuxMap Mobile"

# Domain
"$PY" "$S" "government public service" --domain product -n 2      # + "utility infrastructure maintenance", "GIS map field service",
                                                                   #   "logistics fleet operations", "energy utility smart city", "maintenance work order"
"$PY" "$S" "<style>" --domain style -n 1 --full                    # Accessible Ethical, Minimalism Swiss, Flat Design,
                                                                   #   Dark Mode OLED, Data-Dense Dashboard, Material Design
"$PY" "$S" "government professional blue" --domain color -n 2     # + "status incident maintenance", "dark operational monitoring"
"$PY" "$S" "accessible government readable" --domain typography -n 3   # + "vietnamese"
"$PY" "$S" "<topic>" --domain ux -n 2                              # dark mode contrast, color not only indicator, status badge chip,
                                                                   #   loading skeleton, error recovery retry, touch target spacing,
                                                                   #   dynamic type font scaling, sunlight outdoor glare (0), outdoor sunlight (0),
                                                                   #   offline state stale data, offline network (0), map location marker
"$PY" "$S" "<topic>" --domain web -n 2                             # dark mode theme contrast, safe area insets, empty state,
                                                                   #   bottom sheet modal dismiss, accessibility label icon button, reduced motion
"$PY" "$S" "<topic>" --stack jetpack-compose -n 2                  # theme design system, dark mode, typography hardcode,
                                                                   #   semantics accessibility, lifecycle collect, lazy list key
"$PY" "$S" "time series trend mobile" --domain chart -n 1
```

Không chạy: `--persist`, `--preview`, `--page`, `--force` (sẽ ghi file design system vào repo, ngoài phạm vi giai đoạn này).

## Phụ lục B: Bảng tương phản [TÍNH]

Công thức độ chói tương đối WCAG 2.x. Script nằm ở scratchpad của phiên, không thuộc repo.

| Cặp màu | Tỉ lệ |
|---|---:|
| Navy700 `#1F3864` ↔ trắng | 11.62 |
| `#0F172A` ↔ `#F8FAFC` | 17.06 |
| `#0369A1` ↔ trắng | 5.93 |
| `#1E40AF` ↔ trắng | 8.72 |
| Slate600 `#475569` ↔ `#F8FAFC` | 7.24 |
| Gray500 `#64748B` ↔ trắng | 4.76 |
| Danger600 `#DC2626` ↔ trắng / ↔ `#1A1A1A` | 4.83 / **3.60** |
| `#FF9A9A` / `#F87171` ↔ `#1A1A1A` | 8.57 / 6.29 |
| `#B45309` ↔ trắng / ↔ `#1A1A1A` | 5.02 / **3.47** |
| `#F7C66D` ↔ `#1A1A1A` | 10.98 |
| Green400 `#5FC4B0` ↔ `#0D0D0D` | 9.26 |
| DarkText `#F5F5F5` / `#B3B3B3` / `#A0A0A0` ↔ `#1A1A1A` | 15.96 / 8.30 / 6.66 |
| Viền Gray200 ↔ trắng / Dark700 ↔ Dark800 | **1.23 / 1.21** |
| Navy50 (chỉ báo nav) ↔ trắng | **1.12** |
| Skill night primary `#4338CA` ↔ `#0F172A` | **2.26** |
| Marker Green400 / Amber500 / Rose600 ↔ trắng | 2.10 / 2.17 / 4.38 |
| Marker Green400 / Amber500 / Rose600 ↔ `#2B3A2F` (ước lượng nền vệ tinh) | 5.72 / 5.54 / **2.74** |
| Amber500 ↔ Green400 (đèn mờ và bình thường) | **1.03** |

*Đính chính:* audit (`AUDIT_REPORT.md`) từng ghi `#B45309` trên Dark ≈2.5:1 do tính tay sai. Số đúng là **3.47:1** (vẫn dưới 4.5:1), đã sửa trong audit.
