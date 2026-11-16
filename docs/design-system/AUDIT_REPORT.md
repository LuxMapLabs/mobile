# LuxMap Mobile: Báo cáo audit sản phẩm & UI/UX

**Ngày:** 2026-11-16 · **Nhánh khảo sát:** `refactor/remove-asset-import` (commit `d2e81a8`) · **Phạm vi:** toàn bộ `app/src/main/java/com/luxmap` (229 file Kotlin, khoảng 16.800 dòng UI)
**Loại tài liệu:** chỉ audit. Không có thay đổi code, UI hay token nào đi kèm báo cáo này.

> Mọi đường dẫn file dưới đây tính từ `app/src/main/java/com/luxmap/`, trừ khi ghi rõ khác.
> Mỗi vấn đề lớn có mã `P-xx`. Mỗi mã trả lời 4 câu hỏi: **Sai gì · Vì sao là vấn đề · Ai bị ảnh hưởng · Nên cải thiện thế nào.**

---

## 0. Tóm tắt điều hành

Về kiến trúc, app ở trạng thái tốt: MVVM + UDF, UiState 4 trạng thái, Room-first + `sync_queue`, Hilt, 100% repository đã là Real. Vấn đề nằm ở **lớp trình bày**. Qua 10+ đợt redesign từng phần (Night Ledger v2.1 → rollback v2.0 → "Khảo sát v4" → "Chi tiết lệnh v2" → "Design System v3.0.1"...), giao diện bị ghép từ nhiều hệ thống thị giác chồng lên nhau:

1. **Ba hệ token song song cùng tồn tại** (v2.0 đang dùng, v2.1 đã rollback nhưng còn khai báo, cộng các hằng số "v2.1 additive"), và **hai thang chữ song song** (`MaterialTheme.typography` và các `*TextStyle` rời).
2. **Màu hardcode ở cấp màn hình** (`Navy700`/`Navy900`/`Color(0x…)`) phá vỡ Dark Mode và theme theo ngữ cảnh. Có ít nhất 3 kiểu "hero" khác nhau và 1 màn tự ghi đè `colorScheme`.
3. **Badge trạng thái không có biến thể Dark** (sync / work-order status / priority / task-kind). Một số màu chữ trạng thái dưới ngưỡng tương phản 4.5:1 trên nền tối.
4. **Thiếu nhất quán ở trạng thái lỗi/rỗng**: màn Chi tiết lệnh có Error/Empty **không có nút Thử lại**, và có 3 cài đặt `MessageState` khác nhau.
5. **Còn CTA "chết" và nội dung lỗi thời** ngay trên màn chính: Chi tiết cột có 6 hành động chỉ hiện snackbar "Chức năng đang được hoàn thiện", và 1 nút cho Field Engineer "Tạo lệnh sửa chữa" là việc của Manager. Màn Cá nhân bắt buộc có cảm biến lux BLE cho luồng video đã bị xoá.
6. **Kiến trúc thông tin bị xé lẻ**: cùng là `WorkOrder` nhưng kiểm tra/sửa chữa nằm ở tab Việc hôm nay, còn khảo sát/đo GPS cột nằm ở tab Khảo sát. Có 2 luồng "đo GPS cột" gần như trùng nhau (`polelocation` và `polegps`).

Đề xuất thứ tự redesign (chi tiết ở mục 12): **(1) dọn và hợp nhất nền tảng token/typography/theme**, sau đó **(2) bộ component lõi có biến thể Dark và a11y chuẩn**, rồi mới **(3) restyle từng màn theo tần suất dùng**: Chi tiết lệnh → Việc hôm nay → Khảo sát → Bản đồ → còn lại.

---

## 1. Tổng quan sản phẩm

| Khía cạnh | Thực tế trong repo |
|---|---|
| Sản phẩm | LuxMap: nền tảng GIS + IoT + Computer Vision quản lý tài sản và sự cố chiếu sáng đường giao thông nông thôn, dùng cho cơ quan nhà nước |
| App mobile này | **"Field Operations"**, Android native (Kotlin + Jetpack Compose + Material 3), **chỉ phục vụ 1 vai trò: Field Engineer (Tổ khảo sát/sửa chữa)** |
| Nguồn sự cố (`Fault`) | Engine IoT/CV sinh tự động, Citizen gửi qua QR (ngoài repo này), và Field Engineer báo tại hiện trường (`field_report`, F11, **chưa có màn trên mobile**) |
| Ngữ cảnh sử dụng | Ngoài trời, ban đêm, nông thôn, mạng yếu/mất mạng, thao tác một tay, có thể đeo găng. Từ đó suy ra các yêu cầu: offline-first, vùng chạm 48dp, chữ ≥16sp, Dark theo ngữ cảnh |
| Yêu cầu thẩm mỹ | Phần mềm khu vực công: trang trọng, rõ ràng, đáng tin cậy, không chạy theo trend, không dùng dynamic color |

---

## 2. Tổng quan kiến trúc hiện tại

```
MainActivity (enableEdgeToEdge + LuxMapTheme)
└─ NavGraph (1 Scaffold toàn cục: NotificationBellBar nổi trên + BottomNavBar 4 tab)
   ├─ feature/<x>/ui      Composable (Route + Screen) + ViewModel + sealed UiState
   ├─ feature/<x>/data    Repository interface → Real (đều đã bind Real) + Fake (còn file, không bind)
   │                      Room DAO/entity + SyncOpHandler đăng ký vào Set<SyncOpHandler>
   ├─ core/theme          Color.kt (334 dòng) · Type.kt · Dimens.kt · Theme.kt
   ├─ core/ui/components  22 component dùng chung (6 component không còn được dùng, xem P-09)
   ├─ core/sync           SyncQueueManager / Processor / Worker (WorkManager)
   ├─ core/map            MapLibre config, marker icons (vector drawable → bitmap)
   └─ core/network        Retrofit APIs, TokenAuthenticator, ConnectivityObserver
```

**Điểm mạnh nên giữ khi redesign:**
- UiState là `sealed interface` 4 nhánh, render bằng `when` exhaustive ở mọi màn có dữ liệu. Có `isStale` cho offline cache, đúng nguyên tắc A4/A6 của đặc tả.
- Skeleton loading có hình dạng giống nội dung thật (Home, Hub, PoleList, Notification), không bị nhảy layout.
- Badge trạng thái tài sản (`AssetCondition`) có đủ cặp Light/Dark và nhãn tập trung một chỗ (`label()`). Marker bản đồ phân biệt bằng **badge glyph + màu**, không chỉ dựa vào màu.
- Ở một số component (`InlineBanner`, `BottomActionBar`, `InlineErrorText`), live region (`LiveRegionMode.Polite`) và `mergeDescendants` dùng đúng chỗ.
- Có transition cố ý: fade-through 90/210ms, bỏ fade khi chuyển cảnh liên quan tới bản đồ để tránh lỗi GL surface.

**Điểm yếu kiến trúc ảnh hưởng trực tiếp tới redesign:**
- Mọi chuỗi UI hardcode trong composable: `res/values/strings.xml` chỉ có 1 entry, không có `stringResource` nào. Hậu quả là đổi thuật ngữ (P-08) phải sửa rải rác trong code.
- Màn quá lớn, khó restyle an toàn: `WorkOrderDetailScreen.kt` 2034 dòng (một file gánh 4 loại lệnh), `MapScreen.kt` 1267 dòng (logic MapLibre nằm trong composable).
- Không khai báo `Shapes` cho `MaterialTheme`, nên component M3 mặc định (Card, Dialog, TextField...) dùng shape M3 gốc, lệch với `Radius.*`.

---

## 3. Danh mục màn hình

17 route thật (`navigation/Routes.kt`). "4 trạng thái" nghĩa là có đủ Loading/Success/Empty/Error.

| # | Màn (mã đặc tả) | Route | File chính (dòng) | Vào từ | 4 trạng thái | Theme |
|---|---|---|---|---|---|---|
| 1 | Đăng nhập (F01) | `auth/login` | `LoginScreen.kt` (561) | Khởi động | riêng (LoggingIn/Prefetching) | Hệ thống + hero navy |
| 2 | Việc hôm nay (F02) | `home` | `HomeScreen.kt` (683) | Tab 1 | ✅ | Hệ thống + hero gradient |
| 3 | Hub Khảo sát | `survey?highlight…` | `SurveyHubScreen.kt` (648) | Tab 2, F09, F14 | ✅ | **Ghi đè `primary` cục bộ** |
| 4 | Chụp ảnh cột đèn (danh sách cột) | `survey/pole-photos/{wo}` | `PoleListScreen.kt` (751) | Hub | ✅ | Hệ thống + hero |
| 5 | Kết quả AI + review | `…/result/{photo}` | `PolePhotoResultScreen.kt` (913) | PoleList | ✅ | Hệ thống |
| 6 | Chụp ảnh 1 cột | `…/capture/{pole}` | `PolePhotoCaptureScreen.kt` (351) | PoleList | n/a | **forceDark** |
| 7 | Quay video 1 cột | `survey/pole-videos/…` | `PoleVideoCaptureScreen.kt` (433) | PoleList | n/a | **forceDark** |
| 8 | Cột thiếu GPS | `pole-location` | `PoleLocationListScreen.kt` (153) | Hub | ✅ | Hệ thống |
| 9 | Chụp GPS cột (thiếu GPS) | `pole-location/{pole}/capture` | `PoleLocationCaptureScreen.kt` (192) | #8 | n/a | Hệ thống |
| 10 | Bản đồ hiện trường (F12) | `map` | `MapScreen.kt` (1267) + 12 file phụ | Tab 3 | ✅ (overlay) | Hệ thống |
| 11 | Chi tiết cột đèn | `map/pole/{pole}` | `PoleDetailScreen.kt` (801) | Map quick view | ✅ (không retry) | Hệ thống |
| 12 | Chi tiết lệnh (F09/F10) | `work-order/{wo}` | `WorkOrderDetailScreen.kt` (2034) | Home, F14, Hub | ✅ (**không retry**) | Hệ thống + hero flat |
| 13 | Ảnh sau sửa chữa (F10) | `…/complete/evidence` | `EvidenceCaptureScreen.kt` (83) | #12 | n/a | Camera hệ thống |
| 14 | Đo GPS cột (pole_gps_update) | `…/pole-gps/{pole}` | `PoleGpsMeasureScreen.kt` (245) | #12 | ✅ | Hệ thống |
| 15 | Xem PDF đính kèm | `…/pole-gps/pdf/{id}` | `PoleGpsPdfViewerScreen.kt` (169) | #12 | ✅ | Hệ thống |
| 16 | Thông báo (F14) | `notification` | `NotificationListScreen.kt` (632) | Chuông | ✅ | Hệ thống + hero gradient 2 stop |
| 17 | Cá nhân & Cài đặt (F15) | `profile` | `ProfileScreen.kt` (622) | Tab 4 | 3 (không Empty, hợp lý) | Hệ thống + hero gradient |

**Màn có trong đặc tả nhưng không có trên mobile:**
- F03–F06 (luồng video liên tục): đã xoá có chủ đích ngày 2026-11-15.
- F07: đã loại khỏi đặc tả.
- **F11 Báo cáo sự cố tại hiện trường**: chưa làm. Không có `feature/faultreport/`.
- **F13 Hàng đợi đồng bộ**: chưa có UI. Không có `feature/sync/`, trạng thái sync chỉ hiện rải rác dạng badge.

Cấu trúc thư mục trong `CLAUDE.md` vẫn liệt kê `faultreport/`, `sync/`, `OfflineRegionManager.kt`, `SyncPill`... nhưng các thư mục/file này không tồn tại hoặc không còn được dùng.

---

## 4. Vai trò người dùng & luồng nghiệp vụ

### 4.1 Vai trò: chỉ mô tả cái thật sự có

| Vai trò (FA26SE222 v1.2) | Dùng mobile? | Có mặt trong mobile thế nào |
|---|---|---|
| **Field Engineer** (Tổ khảo sát/sửa chữa) | **Có, vai trò duy nhất** | Mọi màn. Badge vai trò ở Profile ghi cứng "Tổ khảo sát/sửa chữa" |
| **Manager** (= Maintenance Engineer, Kỹ sư bảo trì) | Không, dùng Web GIS | Chỉ xuất hiện **gián tiếp**: người giao lệnh, viết `review_note`, duyệt 2 cổng, duyệt/từ chối yêu cầu hủy, quyết định ảnh cột (`/decision`) |
| Superior | Không | Không xuất hiện |
| System Admin | Không | Không xuất hiện. Quyền FE (`ExecuteWorkOrders`/`ReadWorkOrders`) và phạm vi địa bàn (`administrative_unit`) do server áp |
| Citizen | Không (QR web) | Gián tiếp: notification `citizen_report_received` (thực tế chỉ gửi Manager) |

> Lưu ý cho giai đoạn Design Direction: "Managers" và "Maintenance Engineers" trong yêu cầu audit là **cùng một vai trò**, và vai trò này **không có màn nào trên mobile**. Redesign mobile không nên thêm UI cho Manager/Admin. Việc cần làm là thiết kế tốt **điểm chạm của FE với quyết định của Manager**: ghi chú duyệt, trả lại, từ chối hủy, quá hạn.

### 4.2 Các luồng thật của Field Engineer

**A. Việc được giao hằng ngày (F02 → F09)**
Đăng nhập → (prefetch dữ liệu ngày làm việc) → **Việc hôm nay**: 2 tab Sửa chữa/Kiểm tra, sắp xếp quá hạn → hôm nay → điểm ưu tiên → chạm thẻ → **Chi tiết lệnh**.
*Lưu ý:* lệnh `survey` và `pole_gps_update` **bị loại khỏi Home** và chỉ hiện ở tab Khảo sát (`HomeScreen.kt:294-303`).

**B. Kiểm tra (inspection, F09)**
Chi tiết lệnh (`assigned`) → xem danh sách sự cố + "Cần chuẩn bị để nghiệm thu" → **Bắt đầu** (chỉ mở trong cửa sổ `scheduled_date −1 ngày … scheduled_date`) → `in_progress` → chọn `fault_present / fault_absent / inconclusive` cho **từng** sự cố (`OutcomeSegment`) + ghi chú ≥10 ký tự → sheet xác nhận → **Nộp báo cáo kiểm tra** (ghi Room → `sync_queue`) → chờ Manager duyệt (cổng #2).

**C. Sửa chữa (repair, F10)**
Tương tự B, nhưng nghiệm thu bắt buộc có **≥1 ảnh sau** (camera hệ thống, gắn GPS) + kết quả ≥10 ký tự, vật tư đã thay là tùy chọn → **Hoàn thành lệnh**.

**D. Xin hủy lệnh (WO-CANCEL-REQUEST)**
Chỉ khi `wo_status = assigned` và `request_cancellation ∈ allowed_actions` và đang trong cửa sổ lịch → bottom sheet 4 lý do + ghi chú → banner "Chờ duyệt". Trong lúc chờ, client **tự khóa** nút Bắt đầu. Nếu bị từ chối thì hiện banner lý do. Có ở **F09 và Hub Khảo sát**.

**E. Quá hạn (UI-only, theo `scheduled_date`)**
Quá ngày thì khóa Bắt đầu/Hoàn thành và toàn bộ form, kèm banner "liên hệ Kỹ sư bảo trì". Server không biết luật này, và không có notification tương ứng cho Manager.

**F. Khảo sát theo từng cột (survey, quyết định 2026-10-15)**
Hub → thẻ tuyến → Bắt đầu → **Chụp ảnh cột đèn** (danh sách cột) → mỗi cột chọn 1 trong 4: chụp ảnh / ảnh từ thư viện / quay video / video từ thư viện → upload qua `sync_queue` → **Kết quả AI** (`cv_state`) → FE review nhãn On/Off (bắt buộc ghi chú nếu khác AI; bị khóa khi Manager đã `decision`) → **Hoàn tất khảo sát tuyến**.

**G. Cập nhật GPS cột (`pole_gps_update`)**
- Nghiệp vụ (chủ dự án xác nhận 2026-11-16): Manager giao task đi lấy GPS cho **cột đèn mới**, kèm file PDF. Trên app: F09 → danh sách cột + PDF đính kèm → **Đo GPS cột** (chụp ảnh + GPS) → ghi chú → Nộp → Manager verify.
- `polelocation` ("Cột thiếu GPS") **thuộc cùng nghiệp vụ này**, nhưng trong code đang là một lối vào riêng ở Hub, không gắn WorkOrder và không có PDF. Đây là bản cài đặt trùng lặp. Xem P-12.

**H. Tra cứu GIS / IoT (F12 + chi tiết cột)**
Bản đồ (vệ tinh/vector) → marker theo `fixture_status` + badge POI/IoT → lọc trạng thái, bật/tắt lớp cột/tuyến, tìm kiếm, KPI chips → chạm marker mở **quick view** → **Chi tiết cột**: cảnh báo ưu tiên, thông tin nhanh (công suất, nguồn, bảo hành, IoT online/offline + lần báo cuối), biểu đồ xu hướng độ sáng (Canvas). Tuyến đường mở quick view riêng.

**I. Thông báo (F14)**
Polling 45s khi foreground → chuông + badge → danh sách lọc Tất cả/Chưa đọc → chạm: mở F09 nếu `entity_type=work_order`, mở Hub (có highlight) với survey. Các loại khác chỉ đánh dấu đã đọc.

**Chưa có trên mobile:** F11 báo sự cố hiện trường, F13 xem/giải quyết hàng đợi sync (chỉ có badge, không có màn xử lý `failed`/`conflict`).

---

## 5. Phân tích Design System hiện tại

### 5.1 Màu: 3 lớp trên giấy, thực tế bị trộn

| Phát hiện | Bằng chứng |
|---|---|
| **Token v2.1 "Night Ledger" đã rollback nhưng còn 25 primitive** (`Paper/Ink/Lumen/Night*`...). Comment nói "HudChip.kt và LoginScreen.kt vẫn dùng", **thực tế không file nào dùng** ngoài `DisabledContent` (NotificationList) | `core/theme/Color.kt:47-76` |
| Lớp semantic `LuxMapSemanticColors` **không được expose qua CompositionLocal**. Màn hình chỉ thấy M3 `colorScheme`, nên khi cần màu không có slot M3 (navy hero, "due today"...) thì import thẳng primitive | 19 tham chiếu primitive trực tiếp ở `WorkOrderDetailScreen`, 17 ở `NotificationListScreen`, 10 ở `SurveyHubScreen` |
| `Color(0x…)` cục bộ trong màn | `PolePhotoResultScreen` 9, `SurveyHubScreen` 4 (gồm `ImportAreaTile*` tím, sót lại từ asset-import đã xoá), `WorkOrderDetailScreen` 3 (`HeroBackChipColor`, `0xFF9FB6D6`...) |
| **Badge `SyncStatus` / `WorkOrderStatus` / `WorkOrderPriority` / `WorkOrderTaskKind` không có biến thể Dark**, chỉ `AssetCondition` có | `Color.kt:177-334`, hàm `badgeColors()` không nhận `isDark` |
| `DueTodayColor = #B45309` hằng số, không theo theme | `core/ui/components/DueDateLabel.kt:33` |
| `colorScheme.error = Danger600 #DC2626` ở **cả Dark**. Không có `errorContainer`, `surfaceContainer*`, `outlineVariant`, `inverseSurface` | `Theme.kt` |
| Một màn tự fork theme: `colorScheme.copy(primary = Navy700)` | `SurveyHubScreen.kt:130` |
| Gradient: quy tắc gốc là "chỉ cho 1 hero nổi bật nhất". Thực tế có **3 kiểu hero**: `BrandHeroGradient` 3 stop (Login, Home, Profile, PoleList...), `NotificationHeroGradient` 2 stop, và flat `Navy900` (F09, Hub) | 8 file dùng Brush/hero |

### 5.2 Typography: hai thang song song
- `MaterialTheme.typography` chỉ định nghĩa 8 slot (`displayLarge, titleLarge, titleMedium, bodyLarge, bodyMedium, labelLarge, bodySmall, labelSmall`). **Các slot còn lại (`labelMedium`, `titleSmall`, `headline*`...) rơi về mặc định M3, tức font hệ thống chứ không phải Be Vietnam Pro, cỡ 12–14sp.** Vẫn đang được dùng: `labelMedium` ở nút đổi nền bản đồ (`MapScreen.kt:627`), tiêu đề sheet "Báo lỗi cột" (`PoleListScreen.kt:322`), nhãn sẵn sàng ở Profile.
- Song song đó có 10 `*TextStyle` rời "v2.1 additive" (`DisplayTextStyle 30sp`, `HeadlineTextStyle 24sp`, `TitleTextStyle 18sp`...). Màn mới trộn cả hai: Home dùng 15 lần `*TextStyle` và 0 lần `MaterialTheme.typography`, còn PoleDetail ngược lại (17/0).
- Chữ **dưới ngưỡng 14sp** của chính dự án: `OverlineTextStyle`/`BadgeTextStyle` 13sp (badge, nhãn tab bottom nav, "Đọc tất cả"), subtitle `AppTopBar` 13sp, `HeroMetadataTextStyle` **12sp uppercase** ở hero F09 (`WorkOrderDetailScreen.kt:831`).

### 5.3 Shape / spacing / kích thước
- Radius có **hai bộ song song**: `Dimens.radius*` (8/12/16/28/999) dùng 29 lần, `Radius.*` (6/10/14/16/20/28) dùng 140 lần, cộng literal `2/3/5/6.dp`. Không có `Shapes` cho M3.
- Spacing: `Spacing.*` dùng tốt. Vẫn còn literal (`4.dp`, `3.dp`, `2.dp`, `6.dp`) cho padding, kích thước icon 14/16/20/22/24/28/34dp chưa thành token.
- `ScreenMargin = 20dp` cùng tồn tại với `Spacing.lg = 16dp` làm lề màn: Home/Hub dùng 16, `BottomActionBar` dùng 20, F09 dùng 12 (`Spacing.md`).

### 5.4 Component
| Nhóm | Hiện trạng |
|---|---|
| Dùng tốt, nên giữ làm nền | `StatusBadge`, `PrimaryButton` (56dp, loading), `InlineBanner`, `BottomActionBar` (hint khóa CTA), `MessageState`, `CancelRequestSheet` (`selectable` + 48dp), `OutcomeSegment` (`selectable`), `TopSlideToast`, `LifecycleTracker`, `EvidencePicker`, `RequirementList` |
| **Chết, không còn ai dùng** | `SyncPill`, `HudChip`, `CameraReadinessPanel`, `CaptureWarning`, `GpsAccuracyIndicator`, `PlaceholderScreen` (đều thuộc luồng video đã xoá ngày 2026-11-15) |
| **Trùng lặp cục bộ** | `SkeletonBar` ×3 (Home/Hub/PoleList), `MessageState` riêng ở PoleDetail + `CenteredMessage` ở PoleGpsMeasure, `cardOutline()` ×2 (Profile/PoleList), `SecondaryOutlinedButton` riêng ở PoleDetail, 2 kiểu filter tab (Home segmented, Notification chip) |
| **Thiếu** (đang tự dựng tay ở mỗi màn) | Hero/Header chuẩn, Card (0 lần dùng M3 `Card`, toàn `background + border`), SecondaryButton, ListRow/InfoRow, SectionHeader, FilterTabs, Skeleton, EmptyState/ErrorState có retry, ConfirmSheet |

### 5.5 Icon
- Toàn bộ dùng Material Icons `Filled`, nhất quán về họ icon. Có lẫn `Icons.Filled.ArrowBack` (đã deprecated) với `Icons.AutoMirrored.Filled.ArrowBack` (PolePhotoResult).
- Icon trạng thái đi kèm chữ, không chỉ dựa vào màu: tốt.
- Marker bản đồ là vector drawable có màu baked-in, kèm badge cho dim/out/unknown: tốt cho người mù màu. Nhưng màu marker (`Green400/Amber500/Rose600`) **không có biến thể** cho nền vệ tinh tối và nền vector sáng.

---

## 6. Phát hiện UI/UX theo từng màn

### 6.1 Đăng nhập (F01)
- ✅ Hero navy rõ thương hiệu, nhãn trường hiển thị (không chỉ placeholder), chip trạng thái mạng, bước "Đang tải dữ liệu ngày làm việc…", "Duy trì đăng nhập" có giải thích offline.
- ⚠️ Có `StreetlightSilhouette` vẽ trang trí. Trong bối cảnh phần mềm nhà nước cần cân nhắc giữ hay bỏ.
- ⚠️ Lỗi đăng nhập dùng `InlineErrorText`, mà màu lấy từ `isSystemInDarkTheme()` thay vì theme thật.

### 6.2 Việc hôm nay (F02)
- ✅ Hero → sheet bo góc chồng lên, thống kê 3 số, tab có đếm, sắp xếp quá hạn trước, empty theo từng tab, banner stale.
- ❌ Tab Kiểm tra/Sửa chữa cao **40dp** (< 48dp) và **không có semantics tab** (`Role.Tab`/`selected`). TalkBack không biết tab nào đang chọn (`HomeScreen.kt:394-397`).
- ❌ `TaskRow`: `.clickable` đặt **trước** `.background(shape)` và không `clip`, nên ripple là hình chữ nhật tràn ra ngoài góc bo (`HomeScreen.kt:512`).
- ❌ "Quá hạn" và số quá hạn dùng `colorScheme.error` (#DC2626). Trên nền Dark `#1A1A1A` chỉ đạt **≈3.6:1**, dưới 4.5:1. "Hôm nay" dùng `DueTodayColor #B45309` trên Dark chỉ đạt **≈3.5:1** (tính lại bằng công thức WCAG ngày 2026-11-16; bản trước ghi nhầm 2.5:1).
- ⚠️ Tên tab "Việc hôm nay" nhưng nội dung là **mọi lệnh được giao** (kể cả tuần sau), nhóm theo loại chứ không theo ngày. Đồng thời không có lệnh survey/pole_gps (P-11).
- ⚠️ Thông tin mỗi thẻ mỏng: mã + cụm + hạn. Không có số sự cố, ưu tiên, `scheduled_date`, trong khi nút Bắt đầu chỉ mở theo `scheduled_date`. FE không biết lệnh nào "mở được hôm nay".

### 6.3 Hub Khảo sát
- ✅ Nhóm "Đang thực hiện / Chưa bắt đầu", highlight + auto-scroll khi đến từ thông báo, CTA hủy ngay trên thẻ.
- ❌ Ghi đè `primary = Navy700` cho cả màn để "nút nổi trên nền sáng" (`SurveyHubScreen.kt:130`). Trong Dark Mode, màn này lệch khỏi accent Dark (`Green400`) của toàn app.
- ❌ `items(routes)` và `items(assignedPoleGpsOrders)` **không có `key`**, nên khi highlight/cập nhật dễ nháy hoặc mất state (vi phạm quy tắc Compose #19 của skill).
- ⚠️ Trộn 3 loại nội dung không cùng cấp trong 1 list: lối vào "Cột thiếu GPS" (bản trùng của `pole_gps_update`, xem P-12), thẻ lệnh `pole_gps_update` (WorkOrder), thẻ tuyến khảo sát (WorkOrder). Màu tile cục bộ `ImportAreaTile*` (tím) và `RouteTile*` không thuộc token nào.

### 6.4 Chụp ảnh cột đèn (danh sách cột) + Kết quả AI
- ✅ Bottom bar "Còn lại N cột", confirm sheet Hoàn tất, badge sync từng cột, banner stale, sheet chọn phương thức 4 lựa chọn.
- ⚠️ Tiêu đề sheet "Báo lỗi cột" dùng `labelMedium` (font hệ thống 12sp). Nhưng hành động thật là "ghi nhận cột", không phải "báo lỗi", nên tiêu đề dễ gây hiểu nhầm.
- ⚠️ Kết quả AI hiển thị **"Đèn sáng/Đèn tắt"** (theo `cv_state` on/off), trong khi mọi nơi khác dùng **Bình thường/Đèn mờ/Hỏng-Tắt**. Hai từ vựng trạng thái song song làm FE lúng túng. Nguồn gốc là dữ liệu BE thật, nên đây là vấn đề trình bày, không phải lỗi field.
- ⚠️ `PolePhotoResultScreen` 913 dòng, 9 màu hardcode, thẻ "Kết luận" + "Chi tiết kỹ thuật" + footer review dày đặc. Màn này là nơi sinh dữ liệu RQ1 (đối chiếu AI và con người), nên cần ưu tiên độ rõ ràng.
- ⚠️ Toast trộn tiếng Anh: "…$pendingReviews **review** đang gửi lên Kỹ sư bảo trì".

### 6.5 Chụp ảnh / quay video cột (forceDark)
- ✅ Dark theo ngữ cảnh đúng quy tắc.
- ⚠️ Có **5 màn "chụp → xem lại → Dùng ảnh này / Chụp lại"** cài đặt riêng: PolePhotoCapture, PoleVideoCapture, EvidenceCapture, PoleGpsMeasure, PoleLocationCapture. Bố cục, nhãn và theme khác nhau (2 màn forceDark, 3 màn theo hệ thống).

### 6.6 Bản đồ (F12): xem mục 8

### 6.7 Chi tiết cột đèn
- ✅ Cảnh báo ưu tiên theo trạng thái, "Thông tin nhanh", biểu đồ xu hướng có `contentDescription` tóm tắt.
- ❌ **6 hành động là placeholder** ("Chức năng đang được hoàn thiện"): menu, mở tuyến khảo sát lại, **tạo lệnh sửa chữa**, xem vị trí, xem lịch sử đầy đủ, mở sự cố, xem tất cả frame (`PoleDetailScreen.kt:95-107`). CTA **chính** của trạng thái OUT và DIM đều là nút chết.
- ❌ "Tạo lệnh sửa chữa" là **nghiệp vụ của Manager**. FE không có endpoint/policy tạo WorkOrder. CTA này không nên tồn tại trên app FE.
- ⚠️ Error/Empty dùng `MessageState(text)` cục bộ, không có nút Thử lại. Nhãn IoT "Online" là tiếng Anh, lẫn với "Mất kết nối".

### 6.8 Chi tiết lệnh (F09/F10): màn quan trọng nhất
- ✅ `allowed_actions` lái CTA, hint khóa CTA nằm ngay trên nút, `LifecycleTracker`, sheet xác nhận tóm tắt trước khi nộp, preview "Cần chuẩn bị để nghiệm thu", khóa form khi quá hạn có giải thích.
- ❌ **Error và Empty có `action = {}`, không có nút Thử lại.** FE bị kẹt, chỉ còn cách Back (`WorkOrderDetailScreen.kt:287-302`). Thông điệp phụ luôn là "Kiểm tra kết nối mạng" kể cả với lỗi không phải mạng.
- ❌ Hero metadata 12sp uppercase + letterSpacing, màu `#9FB6D6` hardcode. Chip back `#1E3D6B` + viền `#93B2E0` hardcode.
- ❌ Đến **8 vùng thông điệp có thể xuất hiện cùng lúc** trên đầu màn: note, review_note (2 biến thể), hủy pending, hủy bị từ chối, quá hạn bắt đầu, quá hạn nộp, sync badge, startError (text đỏ trần), submitError (banner). Không có thứ tự ưu tiên hiển thị.
- ⚠️ `startError` là `Text` đỏ trơn, trong khi `submitError` là `InlineBanner`: hai kiểu lỗi cho cùng một màn.
- ⚠️ Lệnh `survey` mở vào F09 chỉ hiện câu "Lệnh khảo sát — xem trong tab Khảo sát", không có nút đi tới.
- ⚠️ Một file gánh 4 `task_kind` với các nhánh `when` lồng nhau (2034 dòng), rủi ro regression cao khi restyle.

### 6.9 Ảnh sau sửa chữa / Đo GPS cột / PDF
- ✅ Nhỏ gọn, đủ trạng thái, có retry (PoleGps).
- ⚠️ Không dùng chung khung với các màn capture khác (mục 6.5). `CenteredMessage` cục bộ.

### 6.10 Thông báo (F14)
- ✅ Bộ lọc Tất cả/Chưa đọc đạt 48dp, skeleton, "Đọc tất cả", phân trang, icon tile theo `type`, fallback `UNKNOWN`.
- ❌ Chip lọc khi chọn dùng `Navy700` hardcode, nên trong Dark vẫn là navy. Hero gradient riêng 2 stop.
- ⚠️ "Đọc tất cả" dùng `BadgeTextStyle` 13sp. Đây là hành động, cần ≥16sp hoặc ít nhất là label 14sp.
- ⚠️ Chạm vào thông báo `fault`/`citizen_report` chỉ đánh dấu đã đọc, không có phản hồi điều hướng. Phạm vi này đã có chủ đích, nhưng cần affordance khác để người dùng không nghĩ app bị đơ.

### 6.11 Cá nhân & Cài đặt (F15)
- ✅ Phần "Sẵn sàng làm việc" (mạng/đồng bộ/cảm biến), cảnh báo "còn N mục chưa đồng bộ" ngay trên nút Đăng xuất, dialog xác nhận.
- ❌ **Dòng "Cảm biến lux · cần cho khảo sát video" bị tính vào điều kiện sẵn sàng.** Luồng video đã xoá ngày 2026-11-15 và BLE không còn được feature nào khác dùng, nên mọi FE không có cảm biến **luôn thấy trạng thái "chưa sẵn sàng"** (`ProfileScreen.kt` dòng ~396, `bleConnectionState != Connected` cộng vào số vấn đề).
- ⚠️ Dòng đồng bộ không mở được nơi xử lý lỗi/xung đột (F13 chưa có).

---

## 7. Phân tích điều hướng

| Khía cạnh | Hiện trạng | Đánh giá |
|---|---|---|
| Bottom nav | 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân. `saveState/restoreState`, `popUpTo(Home)` | ✅ Đúng giới hạn ≤5, giữ state tab |
| Đích sau đăng nhập | `Home` (đảo lại ngày 2026-11-14) | ✅ |
| Nhãn tab | `BadgeTextStyle` 13sp | ❌ Dưới 14sp. Chỉ báo selected dùng `primaryContainer` (Navy50) trên nền trắng, tương phản ≈1.12:1 (việc còn mở từ Phase 0) |
| Chuông thông báo | `NotificationBellBar` **nổi đè** lên nội dung của 3 tab (ẩn ở Map). Mỗi màn tự canh hero để chừa chỗ. Có biến thể `onDarkHero` cho Home/Survey/Profile | ⚠️ Mong manh: mỗi khi đổi header là phải canh lại toạ độ. Trên Profile/Hub chuông nổi trên hero navy không cùng hệ thống với `AppTopBar` của màn con |
| Header màn con | 3 kiểu: `AppTopBar` trong suốt (PoleLocation, PoleGps, PDF, F09-loading), M3 `TopAppBar` riêng (PoleDetail), hero tự dựng có nút back (F09-success, PoleList, PolePhotoResult) | ❌ Vị trí, kích thước và tương phản của nút Back thay đổi giữa các màn. F09 **đổi kiểu header giữa Loading và Success** nên header nhảy khi dữ liệu về |
| Chuyển cảnh | Fade-through 90/210ms, bỏ fade với Map | ✅ |
| Kiến trúc thông tin | WorkOrder chia theo `task_kind` sang 2 tab. Hub là "thùng chứa" cho cả 3 loại tác vụ | ❌ P-11 |
| Deep link từ thông báo | Chỉ `work_order` và survey (Hub + highlight) | ✅ Đúng phạm vi đã chốt |
| Back | Đều `popBackStack`, phiên hết hạn → Login + xoá stack | ✅ |

---

## 8. Phân tích trải nghiệm GIS

**Có gì:**
- Nền **vector OpenFreeMap là mặc định** (`isSatelliteBasemap = false`, `MapScreen.kt:224`), vệ tinh MapTiler (nếu có key) là tùy chọn qua nút chuyển.
- Marker vector theo trạng thái + badge glyph. Badge POI nhạy cảm và IoT.
- Gom cụm (cluster) có "mức nghiêm trọng cao nhất". Marker được chọn phóng to 1.3× kèm vòng halo.
- Lớp tuyến tô theo `has_active_segment_fault`.
- Lọc trạng thái + bật/tắt lớp trong bottom sheet.
- Tìm kiếm cột/tuyến, KPI chips có đếm (luôn là tổng thật, không bị filter làm sai).
- Quick view cột và tuyến, tự fit camera theo bbox địa bàn, định vị bản thân (FAB + spinner), overlay offline.
- Xử lý quyền vị trí đủ các nhánh (chưa cấp, từ chối vĩnh viễn, độ chính xác thấp).

**Vấn đề:**

| # | Vấn đề | Tác động |
|---|---|---|
| G1 | **Không có bản đồ offline.** `OfflineRegionManager` trong CLAUDE.md không tồn tại. Mất mạng thì nền trắng, marker vẫn vẽ ("Không tải được nền bản đồ · Dữ liệu cột vẫn khả dụng") | FE ở nông thôn mất toàn bộ ngữ cảnh địa lý đúng lúc cần nhất. Đây là vấn đề sản phẩm, không chỉ là styling |
| G2 | ~~Mặc định nền vệ tinh~~ **Đính chính 2026-11-16:** code đã dùng **vector làm mặc định** (`MapScreen.kt:224`). Bản audit đầu đọc nhầm dòng chọn style. Vấn đề còn lại là khi người dùng chuyển sang vệ tinh (ảnh mờ ở nông thôn, nền tối), marker dùng chung một bộ màu nên khó đọc | Marker kém tương phản trên nền vệ tinh (xem G4) |
| G3 | FAB đổi nền hiện nhãn **đích đến** ("Vector"/"Vệ tinh") thay vì trạng thái hiện tại, dùng `labelMedium` (font hệ thống) | Mơ hồ: người dùng không biết đang ở chế độ nào |
| G4 | Marker 1 bộ màu cho cả nền vệ tinh (tối) và vector (sáng). Không có quầng/viền tương phản | Đèn "mờ" (amber) và "bình thường" (teal) khó phân biệt trên ảnh vệ tinh ban đêm |
| G5 | Từ bản đồ chỉ vào được **Chi tiết cột** (nhiều CTA chết). Không có liên kết cột → WorkOrder liên quan, không có "đi tới" ngay ở quick view chi tiết. Nút "Đường đi" có ở quick view | Bản đồ là ngõ cụt về nghiệp vụ. FE thấy cột hỏng nhưng không làm gì tiếp được trên app |
| G6 | Không thấy vị trí các **lệnh được giao** trên bản đồ: chỉ có cột/tuyến, không có lớp WorkOrder | Mất cơ hội lập lộ trình "việc đêm nay" (BE đã có `GET /work-orders/agenda`) |
| G7 | IoT chỉ là badge "có node" + 1 dòng online/offline + biểu đồ % độ sáng. Không có chú giải ý nghĩa ngưỡng, không có trạng thái "dữ liệu cũ" | Khó tin vào số liệu IoT ngoài hiện trường |
| G8 | `MapScreen.kt` 1267 dòng, logic MapLibre (style, layer, location component) nằm trong composable | Không phải lỗi UX, nhưng mọi restyle overlay đều có rủi ro chạm vào logic bản đồ |

---

## 9. Vấn đề trợ năng (Accessibility)

| # | Vấn đề | Vị trí | Mức |
|---|---|---|---|
| A1 | Chữ trạng thái lỗi/quá hạn trên Dark dưới 4.5:1: `#DC2626` ≈3.6:1, `#B45309` ≈3.5:1 trên `#1A1A1A` | Home, F09 summary, mọi `colorScheme.error` | **Nghiêm trọng** |
| A2 | Chữ <14sp: nhãn bottom nav 13sp, badge 13sp, hero F09 12sp, subtitle TopBar 13sp, các slot M3 không định nghĩa (12sp) | Toàn app | Cao |
| A3 | **45 `.clickable`, chỉ 1 có `role`.** Card/hàng/tab tự dựng không báo là button/tab, không báo `selected` | Home tab, Notification chip, row các list | Cao |
| A4 | Vùng chạm <48dp: tab Home 40dp, nút trong sheet xác nhận 44dp (`WorkOrderDetailScreen.kt:667`), `SyncPill` (đã chết) | | Cao |
| A5 | Badge `SyncStatus`/`WorkOrderStatus`/`Priority`/`TaskKind` dùng màu Light trên nền Dark. Vẫn đọc được nhưng chói, phá cấp bậc thị giác | Mọi list | Trung bình |
| A6 | Ripple không clip theo shape (TaskRow và các thẻ dùng `clickable` trước `background`) | Home, Hub | Thấp (thẩm mỹ) |
| A7 | Error state không có hành động phục hồi (F09, PoleDetail) | | Cao (khả dụng) |
| A8 | Một số `InlineErrorText`/banner dùng `isSystemInDarkTheme()` thay vì theme, nên sai màu trong màn `forceDark` | `ErrorBanner.kt`, `InlineBanner.kt` | Thấp |
| A9 | Chưa kiểm tra font scale 200% có hệ thống. Nhiều `maxLines = 1` + ellipsis (tab, hero title, badge) sẽ cắt nội dung quan trọng khi phóng chữ | Toàn app | Cần kiểm chứng |
| A10 | Mọi chuỗi hardcode: không có `strings.xml`, khó review thuật ngữ và khó kiểm thử TalkBack đồng nhất | Toàn app | Trung bình |

Điểm tốt: icon trang trí cạnh chữ đều `contentDescription = null` (đúng). Icon nút độc lập có mô tả ("Quay lại", "Thông báo, N chưa đọc", "Định vị vị trí hiện tại", "Tải file … về máy"). Có live region cho hint/banner, `selectable` cho OutcomeSegment/CancelRequestSheet.

---

## 10. Các vấn đề thiết kế chính

### P-01 · Ba hệ token chồng nhau, lớp semantic không tới được màn hình
- **Sai gì:** v2.0 (đang dùng) + v2.1 (rollback, còn 25 primitive) + hằng số "v2.1 additive" (`Radius`, `ScreenMargin`, `*TextStyle`). `LuxMapSemanticColors` không được expose, nên màn import thẳng `Navy700`/`Color(0x…)`.
- **Vì sao:** Mỗi màn tự "chọn đúng màu", nên Dark Mode, theme theo ngữ cảnh và đổi thương hiệu đều phải sửa từng file. Redesign trên nền này sẽ tạo thêm hệ thứ tư.
- **Ai bị ảnh hưởng:** Đội dev (chi phí bảo trì). FE gián tiếp (thiếu nhất quán, Dark lỗi).
- **Cải thiện:** Một nguồn token duy nhất. Xoá primitive v2.1 không dùng. Mở rộng `ColorScheme` đủ role M3 (container, surfaceContainer*, outlineVariant, inverse*). Thêm `LocalLuxMapColors` cho role không có slot M3 (hero, dueToday, status). Cấm import primitive từ `feature/`.

### P-02 · Hai thang typography, slot M3 bị bỏ trống
- **Sai gì:** 8/15 slot M3 được định nghĩa. 10 `*TextStyle` rời được dùng song song. Các slot trống (`labelMedium`...) rơi về font hệ thống 12sp.
- **Vì sao:** Chữ Việt có dấu hiển thị bằng 2 font khác nhau trên cùng màn. Không kiểm soát được cỡ chữ tối thiểu 14/16sp.
- **Ai bị ảnh hưởng:** FE (đọc ban đêm, ngoài trời), người cần chữ lớn.
- **Cải thiện:** Định nghĩa đủ 15 slot M3 bằng Be Vietnam Pro, ánh xạ vai trò v2.0 (H1/H2/Body/Caption/Display number) vào slot, bỏ `*TextStyle` rời. Nâng badge/nav label lên ≥14sp, bỏ 12sp.

### P-03 · Badge trạng thái và màu trạng thái không theo Dark
- **Sai gì:** Chỉ `AssetCondition` có cặp Dark. `SyncStatus`/`WorkOrderStatus`/`Priority`/`TaskKind`/`DueToday`/`error` dùng một bộ màu cho cả 2 theme.
- **Vì sao:** Chữ lỗi/quá hạn trên nền tối dưới 4.5:1 (A1). Chip pastel sáng trên nền đen phá cấp bậc thị giác.
- **Ai bị ảnh hưởng:** FE làm đêm bật Dark hệ thống. Đây là đúng nhóm người dùng chính.
- **Cải thiện:** Mỗi bộ trạng thái có cặp Light/Dark như `AssetCondition`. `error` Dark dùng tông sáng (đã có sẵn `ErrorDark #FF9A9A`). Kiểm tra tương phản tự động bằng test.

### P-04 · Ba kiểu hero/header và chuông nổi đè
- **Sai gì:** Gradient 3 stop, gradient 2 stop, flat Navy900. Chuông là overlay toàn cục, mỗi màn tự chừa chỗ. Màn con có 3 kiểu header. F09 đổi header giữa Loading và Success.
- **Vì sao:** App trông như ghép từ nhiều sản phẩm. Vị trí nút Back và chuông không đoán trước được. Mỗi lần sửa header dễ chồng lấn (đã gặp lỗi chồng chuông ở PR #87).
- **Ai bị ảnh hưởng:** FE (định hướng), dev (lỗi chồng lấn lặp lại).
- **Cải thiện:** Hai template header chuẩn: **Tab root** (tiêu đề + chuông nằm trong header, không overlay) và **Màn con** (back + tiêu đề + phụ đề + action). Gradient chỉ cho F01. Header không đổi kiểu theo trạng thái tải.

### P-05 · Trạng thái lỗi/rỗng không nhất quán, có nơi không có lối thoát
- **Sai gì:** F09 Error/Empty không có Thử lại. PoleDetail và PoleGps có `MessageState` riêng. Lỗi khởi động là text đỏ trơn, lỗi nộp là banner.
- **Vì sao:** FE ở vùng mạng chập chờn gặp lỗi thường xuyên. Không có Thử lại thì phải Back rồi vào lại, mất ngữ cảnh.
- **Ai bị ảnh hưởng:** FE.
- **Cải thiện:** Một `ErrorState` (luôn có Thử lại, phân biệt mạng/không mạng/không có quyền) và một `EmptyState` dùng chung. Một kiểu thông báo lỗi inline (banner) cho lỗi hành động.

### P-06 · CTA chết, sai vai trò, và nội dung lỗi thời
- **Sai gì:** PoleDetail có 6 hành động placeholder, trong đó "Tạo lệnh sửa chữa" là việc của Manager. Profile bắt buộc có BLE lux cho luồng video đã xoá. F09 lệnh survey chỉ có câu chữ, không có nút. 6 component chết.
- **Vì sao:** Phần mềm nhà nước cần độ tin cậy: nút bấm không làm gì, hoặc chỉ báo "chưa sẵn sàng" vĩnh viễn, làm giảm niềm tin vào cả hệ thống.
- **Ai bị ảnh hưởng:** FE, và người nghiệm thu/đánh giá đồ án khi demo.
- **Cải thiện:** Bỏ CTA không có backend/không đúng vai trò. Thay bằng hành động FE thật làm được: "Đường đi", "Xem lệnh liên quan" nếu có dữ liệu. Bỏ BLE khỏi điều kiện sẵn sàng. Xoá component chết trong một PR dọn dẹp riêng.

### P-07 · Chi tiết lệnh quá tải thông điệp
- **Sai gì:** Tới 8 vùng banner/ghi chú có thể cùng xuất hiện trên đầu màn, không có thứ tự ưu tiên. 4 loại lệnh trong 1 màn.
- **Vì sao:** Thông tin quyết định ("vì sao tôi không bấm được Bắt đầu?") bị chìm giữa ghi chú phụ.
- **Ai bị ảnh hưởng:** FE, ở màn dùng nhiều nhất.
- **Cải thiện:** Quy tắc ưu tiên: **chặn hành động** (quá hạn/khóa) > **phản hồi từ Manager** (trả lại/từ chối hủy) > **đang chờ** (hủy pending, sync) > **ghi chú tham khảo**. Chỉ hiện 1 banner nổi bật, phần còn lại gom vào thẻ "Ghi chú & lịch sử". Tách các section theo `task_kind`.

### P-08 · Thuật ngữ không nhất quán
- **Sai gì:** "Manager" và "Kỹ sư bảo trì" lẫn lộn trong cùng màn F09 (`"Manager yêu cầu bổ sung"` và `"liên hệ Kỹ sư bảo trì"`). Lộ thuật ngữ nội bộ "cổng #2". Trộn tiếng Anh ("review", "Online"). Hai từ vựng trạng thái đèn (Bình thường/Mờ/Hỏng và Sáng/Tắt).
- **Vì sao:** Người dùng nhà nước cần ngôn ngữ chuẩn mực, thống nhất. "Manager" và "Kỹ sư bảo trì" nghe như 2 người khác nhau.
- **Ai bị ảnh hưởng:** FE.
- **Cải thiện:** Bảng thuật ngữ (glossary) chốt một lần, đưa chuỗi vào `strings.xml`. Một tên duy nhất cho Manager ("Kỹ sư bảo trì" theo đặc tả). Ẩn mã cổng duyệt. Ánh xạ `cv_state` sang câu thân thiện, nói rõ đây là "nhận định của AI", khác "tình trạng tài sản".

### P-09 · Component trùng lặp, chết và thiếu
- **Sai gì:** 6 component chết. Skeleton/MessageState/cardOutline/SecondaryButton bị nhân bản. Không có Card, ListRow, SectionHeader, FilterTabs, Header chuẩn. 0 lần dùng M3 `Card`.
- **Vì sao:** Mỗi màn tự dựng nên lệch nhau vài dp/màu. Restyle phải sửa N chỗ.
- **Ai bị ảnh hưởng:** Dev, và FE (lệch nhỏ cộng dồn thành cảm giác "không chuyên nghiệp").
- **Cải thiện:** Bộ component lõi (mục 12, giai đoạn 2), có biến thể Dark, semantics và 48dp tích hợp sẵn.

### P-10 · Năm màn chụp/xem lại cài đặt riêng
- **Sai gì:** PolePhoto/PoleVideo/Evidence/PoleGpsMeasure/PoleLocationCapture có bố cục, nhãn và theme khác nhau cho cùng thao tác "chụp → xem lại → dùng/chụp lại".
- **Vì sao:** FE phải học 5 lần cùng một thao tác, dễ bấm nhầm khi đeo găng.
- **Ai bị ảnh hưởng:** FE.
- **Cải thiện:** Một khung "Capture Review" dùng chung (vùng preview, metadata GPS/thời gian, 2 nút cố định vị trí). Theme theo ngữ cảnh ban đêm nhất quán.

### P-11 · Kiến trúc thông tin xé lẻ theo `task_kind`
- **Sai gì:** "Việc hôm nay" chỉ có kiểm tra/sửa chữa. Khảo sát và đo GPS cột nằm ở tab Khảo sát. Tên tab nói "hôm nay" nhưng liệt kê mọi lệnh được giao.
- **Vì sao:** FE không có một chỗ duy nhất trả lời câu hỏi "tối nay tôi phải làm gì?". Lệnh `pole_gps_update` dễ bị bỏ sót.
- **Ai bị ảnh hưởng:** FE.
- **Cải thiện:** (Cần quyết định sản phẩm.) Hướng đề xuất: Home là **hộp việc thống nhất** cho mọi `task_kind`, nhóm theo **khả năng hành động hôm nay** (mở được hôm nay / sắp tới / quá hạn) dựa trên `scheduled_date`. Tab Khảo sát trở thành không gian làm việc cho phiên khảo sát đang chạy. Không đổi API.

### P-12 · "Cột thiếu GPS" là bản trùng của `pole_gps_update` (đã xác nhận 2026-11-16)
- **Sai gì:** Chủ dự án xác nhận "Cột thiếu GPS" **chính là nghiệp vụ `pole_gps_update`**: Manager giao task lấy GPS cho cột đèn mới, kèm PDF. Code lại có 2 bản: `feature/polelocation` (lối vào riêng ở Hub, tự liệt kê cột thiếu toạ độ, không gắn WorkOrder, không PDF, không có cổng duyệt) và `feature/workorder/polegps` (đúng nghiệp vụ, có Manager verify).
- **Vì sao:** Đây là cùng kiểu trùng lặp đã dẫn tới việc xoá asset-import ngày 2026-11-16. FE có thể đo GPS ngoài task được giao, dữ liệu về server qua 2 kênh khác nhau, và Hub có thêm một thẻ gây nhiễu.
- **Ai bị ảnh hưởng:** FE (không biết dùng lối nào), Manager (dữ liệu GPS không đi qua cổng duyệt).
- **Cải thiện:** Trong redesign, **chỉ thiết kế một luồng**: `pole_gps_update` qua WorkOrder. Bỏ thẻ "Cột thiếu GPS" khỏi IA của Hub. Việc gỡ `feature/polelocation` khỏi code (UI, repository, sync handler, Room entity) là **một PR dọn dẹp riêng**, cần hỏi lại trước khi làm vì có đụng tới DB và sync queue. Nhãn "Cột thiếu GPS" có thể dùng lại làm tên nhóm hoặc mô tả cho thẻ lệnh `pole_gps_update`.

### P-13 · GIS chưa phục vụ công việc hiện trường
- Xem G1–G7: thiếu offline basemap, nền mặc định không hợp nông thôn, không có lớp WorkOrder, bản đồ là ngõ cụt hành động.
- **Ai bị ảnh hưởng:** FE.
- **Cải thiện:** Giữ nền vector mặc định (đã có). Marker có viền tương phản theo nền. Lớp "việc được giao". Quick view có hành động FE thật. Offline region là hạng mục sản phẩm riêng (cần quyết định, thuộc "Ngăn xếp công nghệ" đã có MapLibre offline).

---

## 11. Cơ hội cải thiện & áp dụng UI UX Pro Max

### 11.1 Trạng thái công cụ (minh bạch)
> **Cập nhật 2026-11-16:** đã cài Python 3.12.10 và `search.py` chạy được (đã thử `--stack jetpack-compose` và `--design-system`). Truy vấn thử "government field operations utility" trả về style **"Accessible & Ethical"** (Best For: Government, public; yêu cầu tương phản 4.5, focus rõ, reduced motion), khớp hướng ở 11.5. Phần phân tích dưới đây vẫn được viết từ quy tắc tĩnh. Truy vấn đầy đủ sẽ chạy ở bước Design Direction.

Skill **UI UX Pro Max v2.15.0 đã được cài**. Lúc viết báo cáo, công cụ tìm kiếm `scripts/search.py` **chưa chạy được vì máy chưa có Python 3**. Vì vậy báo cáo này chỉ dùng **phần tĩnh** của skill: `references/pro-rules.md` (quy tắc mobile + Pre-Delivery Checklist), `references/quick-reference.md` (10 nhóm quy tắc theo ưu tiên), `data/stacks/jetpack-compose.csv` (52 quy tắc Compose) và `data/app-interface.csv`. **Chưa có** kết quả `--design-system` (gợi ý style/palette/font theo ngành) nào. Mọi khuyến nghị về style dưới đây là **fallback từ quy tắc chung**, không phải kết quả tra cơ sở dữ liệu. Nên cài Python trước giai đoạn Design Direction.

### 11.2 Đối chiếu với thứ tự ưu tiên của skill

| Ưu tiên skill | Nhóm | LuxMap hiện tại | Hành động |
|---|---|---|---|
| 1 CRITICAL | Accessibility (tương phản 4.5:1, nhãn, focus) | ❌ A1, A2, A3 | Sửa ở nền token/component, không sửa từng màn |
| 2 CRITICAL | Touch & Interaction (48dp, 8dp gap, loading feedback, pressed state) | ⚠️ A4, A6. ✅ loading trên nút | Component lõi bắt buộc `minimumInteractiveComponentSize` + clip ripple |
| 3 HIGH | Performance (Lazy key, không tính toán nặng trong UI) | ⚠️ thiếu `key` ở Hub. `filteredSortedItems` tính trong composable (Home) | Đưa sắp xếp/lọc vào ViewModel (quy tắc Compose #20) |
| 4 HIGH | Style selection (hợp loại sản phẩm, nhất quán, icon vector) | ❌ P-01/P-04: 3 kiểu hero, gradient lạm dụng | Một style "institutional": phẳng, viền mảnh, gradient tối thiểu |
| 5 HIGH | Layout & Responsive (safe area, nhịp 4/8dp, gutter) | ⚠️ lề 12/16/20dp lẫn lộn, chuông overlay | Một token lề màn. Header chiếm chỗ thật |
| 6 MEDIUM | Typography & Color (base 16, token semantic, không hex trong component) | ❌ P-02, P-03 | Đủ 15 slot M3. Cấm `Color(0x…)` trong `feature/` |
| 7 MEDIUM | Animation (thời lượng theo ngữ cảnh, reduced motion) | ✅ fade 90/210ms. ⚠️ chưa xét reduced motion | Giữ nguyên, thêm tôn trọng "Remove animations" |
| 8 MEDIUM | Forms & Feedback (nhãn hiển thị, lỗi gần trường) | ✅ phần lớn (Login, ghi chú F09/F10, review AI) | Thống nhất kiểu lỗi (P-05) |
| 9 HIGH | Navigation (back dự đoán được, bottom nav ≤5) | ⚠️ P-04 header không nhất quán, P-11 IA | Template header chuẩn |
| 10 LOW | Charts | ✅ biểu đồ độ sáng có `contentDescription` | Thêm chú giải ngưỡng (G7) |

### 11.3 Quy tắc Jetpack Compose của skill đối chiếu với code
- **Đã đạt:** composable thuần UI (#1), UiState `sealed` (#5), `StateFlow` một nguồn (#4), UDF (#12), Hilt `hiltViewModel` (#38), `rememberSaveable` cho tab/sheet (#7), `LazyColumn` cho list dài (#28), có `@Preview` nhiều trạng thái ở F09 (#37), `testTag` (#36).
- **Chưa đạt:**
  - #15: `collectAsState` thay vì `collectAsStateWithLifecycle` (NavGraph, PoleDetail...).
  - #19: thiếu `key` trong Lazy (Hub).
  - #20: lọc/sắp xếp trong composable (Home).
  - #22/#34: hardcode màu và cỡ chữ.
  - #33: layout lặp lại không tách thành component (P-09).
  - #2: composable nguyên khối (F09 2034 dòng).
  - #29: F09 dùng `Column.verticalScroll` cho nội dung dài thay vì Lazy (chấp nhận được vì nội dung hữu hạn, nhưng cần lưu ý khi thêm section).
  - #41: semantics cho control tự dựng (A3).

### 11.4 Checklist pro-rules (mobile): những mục LuxMap đang trượt
Icon từ một họ: ✅. Không dùng emoji: ✅. Pressed feedback không xê dịch layout: ⚠️ ripple không clip. Disabled rõ ràng: ✅ (hint lý do khóa CTA, điểm mạnh riêng của LuxMap). Tương phản chữ ≥4.5:1 **ở cả 2 theme**: ❌. Divider thấy được ở Dark: cần kiểm (`Dark700` trên `Dark800` rất sát). Safe area: ✅ edge-to-edge + inset từng màn. Nội dung không bị thanh cố định che: ✅ (`BottomActionBar` dùng `navigationBarsPadding`). Kiểm font scale lớn nhất: chưa làm. Scrim modal đo trên nền thật: chưa làm (dialog xem ảnh nền đen).

### 11.5 Hướng style gợi ý (fallback, chờ `--design-system` xác nhận)
Phù hợp nhóm *government / utility / field operations*: **"Institutional Utility"**. Nền sáng trung tính. Một màu thương hiệu navy dùng cho hành động chính và điều hướng. Màu trạng thái chỉ dùng cho trạng thái. Viền mảnh, ít đổ bóng. Gradient chỉ ở màn đăng nhập. Mật độ trung bình (thông tin đủ để ra quyết định trên một thẻ). Dark Mode ưu tiên vì làm việc ban đêm. Giữ Be Vietnam Pro (đã bundle offline, hỗ trợ tiếng Việt tốt). Đây là giả thuyết để thảo luận ở bước Design Direction, chưa phải quyết định.

---

## 12. Ưu tiên redesign đề xuất

Thứ tự dựa trên: (a) mức ảnh hưởng tới FE theo thứ tự ưu tiên của skill, (b) phụ thuộc kỹ thuật (nền trước, màn sau), (c) rủi ro chạm logic nghiệp vụ (càng thấp càng làm sớm). Mỗi giai đoạn chia commit ≤400 dòng theo CLAUDE.md.

| Giai đoạn | Nội dung | Giải quyết | Rủi ro logic | Cần quyết định sản phẩm? |
|---|---|---|---|---|
| **0. Dọn dẹp** | Xoá 6 component chết, primitive v2.1 không dùng, màu `ImportArea*`. Bỏ BLE khỏi điều kiện sẵn sàng Profile. Gỡ CTA placeholder/sai vai trò ở PoleDetail | P-06, một phần P-01 | Thấp | **Có**: xác nhận gỡ "Tạo lệnh sửa chữa" và các CTA chưa có backend |
| **1. Nền tảng** | Một hệ token: `ColorScheme` đủ role + `LocalLuxMapColors` + cặp Dark cho mọi bộ trạng thái. 15 slot typography. `Shapes`. Token icon size và lề màn. Glossary + `strings.xml` | P-01, P-02, P-03, P-08, A1, A2, A10 | Thấp (thuần trình bày) | **Có**: chốt glossary (tên vai trò Manager, nhãn `cv_state`) |
| **2. Component lõi** | Header (tab root/màn con, chuông nằm trong header), Card, ListRow, SectionHeader, FilterTabs (semantics tab, 48dp), StatusBadge v2, Error/Empty/Skeleton dùng chung, Secondary/Tertiary button, ConfirmSheet, CaptureReview | P-04, P-05, P-09, P-10, A3, A4, A6, A7 | Thấp–trung bình | Không |
| **3. Chi tiết lệnh (F09/F10)** | Tách theo `task_kind`, quy tắc ưu tiên banner, header ổn định, Error có Thử lại | P-07, P-05 | **Cao** (cửa sổ lịch, khóa form, `allowed_actions`): cần test hồi quy | Không |
| **4. Việc hôm nay + IA** | Hộp việc thống nhất / nhóm theo khả năng hành động hôm nay. Thẻ hiện `scheduled_date` + số sự cố | P-11 | Trung bình | **Có**: IA mới cho Home/Khảo sát |
| **5. Khảo sát** | Hub (bỏ fork theme, có `key`, bỏ thẻ "Cột thiếu GPS"), danh sách cột, Kết quả AI (làm rõ AI và người xác nhận), khung capture chung | P-10, P-12, một phần P-08 | Trung bình (review/upload `sync_queue`) | Không (P-12 đã chốt). Chỉ cần duyệt PR gỡ `polelocation` |
| **6. Bản đồ & chi tiết cột** | Nền mặc định, marker theo nền, overlay controls chuẩn, quick view có hành động FE thật, chú giải IoT | P-13 (G2–G7) | Trung bình (MapLibre) | **Có**: lớp WorkOrder trên bản đồ, offline basemap (G1) |
| **7. Thông báo, Cá nhân, Đăng nhập** | Áp component lõi, bỏ hero riêng | P-04 | Thấp | Không |
| **Ngoài phạm vi redesign** (ghi nhận) | F11 báo sự cố hiện trường, F13 hàng đợi đồng bộ, offline basemap | | | Cần lập task riêng |

**Điều kiện xong cho mỗi giai đoạn** (theo Pre-Delivery Checklist của skill + CLAUDE.md): tương phản ≥4.5:1 kiểm cả Light/Dark, font scale lớn nhất không vỡ layout, mọi vùng chạm ≥48dp, đủ 4 trạng thái, `ktlintCheck` sạch, kiểm tra trên máy thật.

---

## Phụ lục A: Câu hỏi mở cần chủ dự án quyết định trước Design Direction

1. Gỡ hẳn các CTA chưa có backend ở Chi tiết cột ("Tạo lệnh sửa chữa", "Mở tuyến để khảo sát lại", "Xem lịch sử đầy đủ"...), hay giữ ở dạng disabled kèm giải thích?
2. Tên chuẩn hiển thị cho vai trò Manager trên mobile: "Kỹ sư bảo trì", "Quản lý", hay tên khác?
3. Home có trở thành hộp việc thống nhất cho mọi `task_kind` (gồm khảo sát, đo GPS cột) không?
4. ~~Luồng `polelocation` có phải nghiệp vụ riêng?~~ **Đã chốt 2026-11-16:** là cùng nghiệp vụ `pole_gps_update`, xem P-12.
5. Cảm biến lux BLE có còn vai trò nào trên mobile không? Nếu không thì gỡ khỏi Profile.
6. Bản đồ offline (G1) có nằm trong phạm vi đợt này không?
7. ~~Cài Python 3?~~ **Đã cài 2026-11-16** (Python 3.12.10), xem mục 11.1.
