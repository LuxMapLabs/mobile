# LuxMap — Mobile App (Android)

Đây là phần **Mobile — Field Operations**, ứng dụng di động hiện trường dành cho vai trò duy nhất **Tổ khảo sát/sửa chữa** (Field Crew) của hệ thống **LuxMap** (GIS + IoT + Computer Vision để quản lý tài sản và sự cố chiếu sáng đường giao thông nông thôn). Nền tảng **Android, Kotlin + Jetpack Compose**. Chỉ làm 1 nền tảng (không làm iOS song song).

Vai trò "Người dân" và luồng "phản ánh sự cố từ công dân" của hệ thống cũ (CivicFlow) **không còn tồn tại**. Ba vai trò còn lại — Cơ quan quản lý, Kỹ sư bảo trì, Quản trị — dùng nền tảng Web GIS riêng (không thuộc phạm vi repo này). Mọi sự cố (`Fault`) trong LuxMap do engine IoT/CV sinh ra; Tổ khảo sát/sửa chữa chỉ báo cáo bổ sung tại hiện trường như một nguồn ngoại lệ (`field_report`).

## Tài liệu tham chiếu bắt buộc đọc trước khi code

- `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx` — đặc tả từng màn hình (mã F01–F15), bảng ánh xạ Màn hình → API → Bảng dữ liệu (mục C2–C3), enum trạng thái dùng chung (mục C4), quy tắc nghiệp vụ bắt buộc (mục C5), ma trận truy vết UI/trạng thái (mục P9)
- `docs/LuxMap_Mobile_Design_System_v2.0.md` — design tokens 3 lớp (primitive/semantic/component), typography, spacing, master components, quy tắc Dark Mode theo ngữ cảnh
- `docs/LuxMap_Mobile_Technology_Stack.docx` — ngăn xếp công nghệ chốt, kiến trúc 5 lớp (Presentation/Domain/Data/Sync/Device), bảng local DB, quy tắc offline-first chi tiết
- `docs/LuxMap_Rural_Road_Lighting_GIS_phuonglhk.md` — hồ sơ đề tài: bối cảnh, kiến trúc tổng thể, vai trò hệ thống, câu hỏi nghiên cứu (RQ1/RQ2)

**Chưa có trong `docs/` hiện tại:** file task list dạng bảng tính (kiểu `CivicFlow_TaskList.xlsx` cũ) và wireframe tương tác. Mã việc `FM-XX` vẫn dùng xuyên suốt đặc tả và tham chiếu được trực tiếp qua bảng đối chiếu Mã màn hình ↔ Mã công việc ở mục A3 của `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`. Nếu có file task list mới xuất hiện trong `docs/`, ưu tiên đối chiếu với file đó thay vì chỉ dùng mục A3.

**Trước khi code một màn hình, luôn tra đúng mã màn (VD: F04) trong đặc tả để lấy đúng field, endpoint, quy tắc trạng thái — không tự đoán tên field hay endpoint.**

### Khoảng trống đã biết — dừng lại hỏi, không tự đoán

Đặc tả tự nêu rõ 2 điểm chưa chốt với backend (WP2), không phải lỗi thiếu sót của tài liệu:

- **F07 (Nhập lux):** thực thể `lux_reading` và endpoint `POST /api/v1/lux-readings` là **đề xuất**, chưa có tên chính thức trong sheet Backend. Trước khi code F07, xác nhận lại field/endpoint thật với backend.
- **F14 (Thông báo):** module `notification` đã có ở backend (BE-27) nhưng chưa có tên bảng chi tiết. Xác nhận trước khi code F14.

## Ngăn xếp công nghệ

- Kotlin, Jetpack Compose, Material 3 (không dùng XML View)
- Compose Navigation cho điều hướng và deep link
- MVVM + Clean Architecture cho tách UI/nghiệp vụ/dữ liệu (xem mục Kiến trúc & Design Pattern về mức độ dùng lớp Domain)
- Coroutines + StateFlow cho xử lý bất đồng bộ
- Hilt cho Dependency Injection
- Retrofit + OkHttp + kotlinx.serialization cho tầng API (tạm dùng mock, xem mục "Tầng API" bên dưới)
- **Room** — cơ sở dữ liệu cục bộ, là nguồn dữ liệu chính khi offline (không phải cache phụ)
- **DataStore** — lưu token và cấu hình (không dùng SharedPreferences dạng plain text)
- **WorkManager** — đồng bộ nền, có constraint mạng/Wi-Fi và retry/exponential backoff
- **CameraX + Camera2Interop** — chụp ảnh khảo sát đêm, khoá exposure thật (`CONTROL_AE_MODE_OFF`, `SENSOR_EXPOSURE_TIME`, `SENSOR_SENSITIVITY`)
- **ExifInterface** — đọc/ghi metadata ảnh (thời gian, hướng, thông số camera)
- Coil cho load ảnh Compose
- **Fused Location** — lấy GPS; **SensorManager** — lấy heading thiết bị
- **MapLibre Native Android SDK** — bản đồ, hiển thị Pole/WorkOrder/cụm địa lý, offline region; **GeoJSON** cho dữ liệu không gian
- **Firebase Cloud Messaging** — push notification
- **Android Keystore** — bảo vệ token và dữ liệu xác thực nhạy cảm
- ktlint cho format code
- Testing: JUnit + MockK (unit), Turbine (Flow/StateFlow), MockWebServer (API), Room Testing (migration/transaction/queue), Compose UI Test (luồng UI chính)

### Công nghệ chủ động không dùng ở giai đoạn hiện tại

- Flutter/React Native — đã chốt Android native, không cần đa nền tảng.
- Tự xây turn-by-turn navigation — nút "Điều hướng" chỉ mở Google Maps hoặc ứng dụng bản đồ ngoài bằng Intent.
- WebSocket chạy liên tục trên mobile — dùng WorkManager + FCM, phù hợp hơn với pin/mạng yếu.
- Lưu ảnh trực tiếp trong Room — Room chỉ lưu metadata và đường dẫn file, ảnh gốc nằm ở app-specific storage.
- Gọi đồng bộ trực tiếp từ UI/ViewModel — mọi đồng bộ đi qua Repository và sync queue (WorkManager), không có ngoại lệ.

## Cấu trúc thư mục — theo tính năng (feature-based), không theo layer

```
app/src/main/java/com/luxmap/
  LuxMapApp.kt              # Application class (@HiltAndroidApp)
  MainActivity.kt

  core/
    theme/
      Color.kt               # token màu 3 lớp: primitive/semantic/component (FM-02)
      Type.kt                # token chữ (FM-02)
      Theme.kt                # Light/Dark theme, áp dụng quy tắc Dark Mode theo ngữ cảnh
    ui/
      components/            # component dùng chung nhiều feature
        StatusBadge.kt        # variants: asset condition, priority, work-order status, sync
        PrimaryButton.kt
        WorkOrderCard.kt
        OfflineBanner.kt
        SyncIndicator.kt
        GpsAccuracyIndicator.kt
        CameraReadinessPanel.kt
        BottomNavBar.kt        # 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân
    network/
      ApiClient.kt            # Retrofit + OkHttp instance, base URL, JWT interceptor
    database/
      AppDatabase.kt          # Room DB — local_survey_sweep, local_survey_frame, local_lux_reading,
      dao/                    # local_work_order, local_work_order_evidence, local_fault_report,
      entity/                 # local_notification, sync_queue, offline_map_region, sync_checkpoint,
                               # local_pole/fixture/road_segment (cache) — xem mục C2/P6 đặc tả
    sync/
      SyncWorker.kt            # CoroutineWorker xử lý sync_queue theo priority/dependency/backoff
      SyncQueueManager.kt      # điều phối queue, không tự ghi đè khi conflict (409)
    camera/
      ExposureLockController.kt  # Camera2Interop: khoá AE/ISO/shutter
      CaptureMetadataWriter.kt   # ExifInterface: ghi GPS/heading/exposure vào ảnh
    location/
      LocationTracker.kt       # Fused Location
      HeadingSensor.kt          # SensorManager
    map/
      MapLibreConfig.kt
      OfflineRegionManager.kt
    security/
      TokenStore.kt            # Android Keystore + DataStore
    common/
      DateFormatUtils.kt

  di/                        # Hilt modules
    NetworkModule.kt
    DatabaseModule.kt         # cung cấp Room DB/DAO
    RepositoryModule.kt        # bind Fake<->Real repository ở đây, 1 chỗ duy nhất
    SyncModule.kt              # cấu hình WorkManager

  navigation/
    NavGraph.kt                # NavHost, 4 tab + các màn con
    Routes.kt

  feature/
    auth/                     # F01 Đăng nhập
      data/
        AuthRepository.kt
        FakeAuthRepository.kt
        RealAuthRepository.kt
        dto/LoginResponseDto.kt
      ui/
        LoginScreen.kt
        LoginViewModel.kt
        LoginUiState.kt        # bao gồm bước "Đang tải dữ liệu ngày làm việc..." (pre-fetch)

    home/                     # F02 Việc hôm nay
      data/ ...
      ui/
        HomeScreen.kt
        HomeViewModel.kt
        HomeUiState.kt

    survey/                   # F03–F07, feature lớn nhất — khảo sát đêm, đường găng dự án
      data/
        SurveyRepository.kt
        FakeSurveyRepository.kt
        RealSurveyRepository.kt
        LuxReadingRepository.kt      # F07 — endpoint/entity CHƯA chốt, xem mục "Khoảng trống đã biết"
        dto/SurveySweepDto.kt
        dto/SurveyFrameDto.kt
      domain/
        usecase/
          CheckSurveyReadinessUseCase.kt   # F03 — checklist exposure/GPS/storage/pin, logic đủ phức tạp
          CalculateCoverageUseCase.kt       # F05 — tính % độ phủ so với ngưỡng tối thiểu
      ui/
        plan/                 # F03 Lập kế hoạch tuyến khảo sát
        capture/              # F04 Chế độ chụp khảo sát (Capture Mode, dark bắt buộc)
        coverage/              # F05 Kiểm tra độ phủ & chất lượng
        submit/                 # F06 Nộp đợt khảo sát (upload chunk/resume)
        lux/                    # F07 Nhập số đo lux đối chứng

    workorder/                 # F08–F10 — lệnh sửa chữa
      data/
        WorkOrderRepository.kt
        FakeWorkOrderRepository.kt
        RealWorkOrderRepository.kt
        dto/WorkOrderDto.kt
      ui/
        list/                   # F08 Danh sách công việc
        detail/                  # F09 Chi tiết lệnh & điều hướng
        completion/               # F10 Cập nhật tiến độ & nghiệm thu (RepairEvidence)

    faultreport/                # F11 Báo cáo sự cố tại hiện trường (nguồn field_report)
    map/                        # F12 Bản đồ hiện trường (MapLibre)
    sync/                       # F13 Hàng đợi đồng bộ — UI đọc từ core/database sync_queue
    notification/                # F14 Thông báo
    profile/                     # F15 Cá nhân & Cài đặt
```

Mỗi feature nhỏ hơn (`auth`, `home`, `map`, `notification`, `profile`...) chỉ cần `data/` + `ui/`, không bắt buộc có `domain/` — chỉ thêm `domain/` khi thực sự có logic nghiệp vụ tách riêng được (xem mục Kiến trúc & Design Pattern).

## Design tokens — 3 lớp, lấy đúng từ Design System v2.0, không tự chọn màu khác

Token tổ chức theo 3 lớp: **Primitive** (giá trị gốc) → **Semantic** (ý nghĩa sử dụng, khác nhau theo Light/Dark) → **Component** (giá trị theo component cụ thể).

### Primitive colors

```kotlin
val Navy700 = Color(0xFF1F3864)    // Thương hiệu, CTA Light Mode
val Blue500 = Color(0xFF3E86C9)    // Link, focus, thông tin
val Green400 = Color(0xFF5FC4B0)   // Đèn bình thường, điểm nhấn Dark Mode
val Amber500 = Color(0xFFE9A23B)   // Cảnh báo trung bình
val Rose600 = Color(0xFFD64545)    // Hỏng/tắt, SLA khẩn
val Success600 = Color(0xFF059669) // Hoàn tất thành công
val Danger600 = Color(0xFFDC2626)  // Hành động nguy hiểm
val Gray25 = Color(0xFFF8FAFC)     // Nền Light
val Gray900 = Color(0xFF0F172A)    // Chữ chính Light
val Dark950 = Color(0xFF0D0D0D)    // Nền Dark
val Dark800 = Color(0xFF1A1A1A)    // Card Dark
val DarkText = Color(0xFFF5F5F5)   // Chữ chính Dark
```

Bảng đầy đủ (gray-50/200/500/700, dark-900/700, dark-muted...) xem mục 2.1 Design System v2.0.

### Semantic colors (Light / Dark)

| Semantic token | Light | Dark |
|---|---:|---:|
| `color.background` | `#F8FAFC` | `#0D0D0D` |
| `color.surface` | `#FFFFFF` | `#1A1A1A` |
| `color.text.primary` | `#0F172A` | `#F5F5F5` |
| `color.text.secondary` | `#64748B` | `#A0A0A0` |
| `color.border` | `#E2E8F0` | `#2A2A2A` |
| `color.focus` | `#3E86C9` | `#5FC4B0` |
| `color.navigation.active` | `#1F3864` | `#5FC4B0` |

Bảng đầy đủ (surface.subtle, overlay.scrim, disabled.container/content...) xem mục 2.2.

### Badge trạng thái tài sản (Light / Dark) — dùng đúng cặp, không đổi

| Trạng thái | Nền Light | Chữ Light | Nền Dark | Chữ Dark |
|---|---:|---:|---:|---:|
| Bình thường (`normal`) | `#D1FAE5` | `#065F46` | `#123D34` | `#8CE3D1` |
| Đèn mờ (`dim`) | `#FEF3C7` | `#92400E` | `#4A3310` | `#F7C66D` |
| Hỏng/Tắt (`out`) | `#FEE2E2` | `#991B1B` | `#4A1717` | `#FF9A9A` |
| Chưa xác định | `#EFEFEF` | `#555555` | `#303030` | `#D0D0D0` |

### Badge trạng thái đồng bộ (sync_queue.status) — không gộp `failed` và `conflict`

| Enum | Nhãn UI | Nền | Chữ |
|---|---|---:|---:|
| `queued` + offline | Chờ mạng | `#EFEFEF` | `#555555` |
| `queued` + online | Chờ đồng bộ | `#E8EEF7` | `#1F3864` |
| `syncing` | Đang đồng bộ | `#FFF3DC` | `#8A5A00` |
| `failed` | Đồng bộ lỗi | `#FBE4E4` | `#9B2C2C` |
| `conflict` | Xung đột | `#FDE8D0` | `#8A3B00` |
| `done` | Đã đồng bộ | `#E3F6F1` | `#1E6B5C` |

"Đã lưu trên thiết bị" (ghi Room thành công) khác với "Đã đồng bộ" (`done`, server đã xác nhận) — không dùng lẫn hai cụm này trong UI.

### Badge ưu tiên Work Order

| Priority | Nhãn | Màu |
|---|---|---:|
| `low` | Thấp | `#64748B` |
| `normal` | Bình thường | `#3E86C9` |
| `high` | Cao | `#E9A23B` |
| `urgent` | Khẩn | `#D64545` |

Typography: nội dung tối thiểu 16sp, H1 20sp/700 (tiêu đề màn hình), H2 17sp/600 (tiêu đề section/card), Caption 14sp chỉ dùng cho metadata phụ (không dùng cho cảnh báo/hướng dẫn). Nút bấm và mọi vùng chạm tối thiểu **48×48dp** (không phải 44dp — phù hợp thao tác một tay hoặc đeo găng tay khi khảo sát).

### Dark Mode theo ngữ cảnh — không phải theo màn hình cố định

| Màn hình/ngữ cảnh | Chế độ mặc định |
|---|---|
| F03 Lập kế hoạch khảo sát đêm | Dark |
| F04 Capture Mode | Dark bắt buộc, ẩn Bottom Navigation |
| F09 khi vào chế độ điều hướng ban đêm | Chuyển Dark |
| F10 Nghiệm thu | Theo hệ thống, không ép Dark |
| Các màn còn lại | Theo hệ thống, mặc định Light |

## Kiến trúc & Design Pattern

- **MVVM** cho lớp UI: mỗi màn hình gồm 1 `@Composable` (chỉ vẽ UI, không chứa logic) + 1 `ViewModel` (giữ state, xử lý logic). State chảy xuống qua `StateFlow`, sự kiện chảy lên qua callback — Unidirectional Data Flow, không hoisting state ngược.
- **Repository Pattern** cho lớp Data, mở rộng cho offline-first: mỗi feature có 1 interface Repository, tối thiểu 2 cài đặt — `Fake...Repository` (mock, dùng trước khi có backend) và `Real...Repository` (khi backend sẵn sàng). Repository là nơi duy nhất biết cả Room lẫn Retrofit: đọc/ghi Room trước, đẩy thay đổi cần đồng bộ vào `sync_queue`, không bao giờ gọi API trực tiếp từ UI/ViewModel. ViewModel chỉ phụ thuộc vào interface Repository.
- **Không** thêm lớp UseCase/Domain riêng cho mọi màn hình — chỉ thêm khi 1 màn hình có logic nghiệp vụ thực sự phức tạp (VD: `CheckSurveyReadinessUseCase` ở F03 kiểm tra đồng thời exposure/GPS/storage/pin trước khi cho bắt đầu khảo sát, `CalculateCoverageUseCase` ở F05 tính % độ phủ so với ngưỡng, xử lý conflict ở F13). Màn hình đơn giản (F02, F15, F14...) ViewModel gọi thẳng Repository.
- **UiState dạng `sealed interface`** cho mọi màn hình có dữ liệu bất đồng bộ, bắt buộc đủ 4 trạng thái theo đúng mục P9 của đặc tả (đang tải / có dữ liệu / rỗng / lỗi):
  ```kotlin
  sealed interface XxxUiState {
      data object Loading : XxxUiState
      data class Success(val data: ..., val isStale: Boolean = false, val lastSyncedAt: Instant? = null) : XxxUiState
      data object Empty : XxxUiState
      data class Error(val message: String) : XxxUiState
  }
  ```
  Composable dùng `when` (exhaustive, không có nhánh `else`) để vẽ đúng cả 4 nhánh. Khi offline, hiển thị dữ liệu cache trong `Success` kèm nhãn thời điểm tải cuối (`isStale`/`lastSyncedAt`) — **không** coi offline/cached là một trạng thái `Error` hay thêm trạng thái thứ 5, đúng nguyên tắc A4/A6 của đặc tả ("không hiển thị màn trắng hay lỗi khi offline").
- **Dependency Injection bằng Hilt**: inject Repository (Fake hoặc Real), Room DAO và WorkManager dependencies vào ViewModel qua constructor, không khởi tạo trực tiếp trong Composable hay ViewModel.

## Nguyên tắc nghiệp vụ cốt lõi — bắt buộc, ảnh hưởng trực tiếp tới kiến trúc code

Danh sách đầy đủ ở mục C5 của đặc tả; các quy tắc dưới đây lặp lại xuyên suốt nhiều màn hình nên cần nắm trước khi code bất kỳ feature nào:

- **Offline-first tuyệt đối**: mọi màn hình thao tác chính (F03, F04, F05, F06, F07, F10, F11) ghi Room trước, không chờ phản hồi mạng; đồng bộ là tiến trình nền độc lập qua `sync_queue` + WorkManager.
- **Không cho bắt đầu khảo sát nếu chưa khoá exposure thật** (F03 chặn cứng, không chỉ nhắc nhở) — ảnh auto-exposure không sửa lại được sau khi chụp.
- **`RepairEvidence` (F10) và `SurveyFrame` (F04) là hai luồng ảnh tách biệt hoàn toàn** (BE-11): không dùng chung API upload, không dùng chung bảng lưu trữ.
- **Không tự động ghi đè khi xung đột đồng bộ** (`conflict`, HTTP 409 dựa trên `version`/`updated_at`): giữ nguyên bản ghi local, đánh dấu xung đột, để người dùng hoặc Kỹ sư bảo trì xử lý qua Web.
- **Upload phải resume được** theo chunk, có checksum SHA-256 và idempotency key — không tải lại từ đầu một phiên khảo sát khi bị ngắt giữa chừng.
- **Đóng lệnh sửa chữa (F10) bắt buộc có ảnh sau + ghi chú kết quả**, không cho phép đóng lệnh nếu thiếu.
- **Dữ liệu theo địa bàn**: chỉ thấy/tải trước dữ liệu thuộc `administrative_unit` được phân công, lọc ở tầng server (BE-08) — mobile không tự lọc lại phía client như một biện pháp phân quyền.

## Tầng API — mock trước, chưa có backend thật

Backend đang làm song song. Quy tắc:

1. Định nghĩa interface Retrofit đúng theo endpoint trong bảng C3 của đặc tả (VD: `GET /api/v1/work-orders/assigned-to-me`).
2. Data class DTO đặt đúng tên field tiếng Anh như trong đặc tả (VD: `work_order_id`, `status`, không tự đặt tên khác).
3. Viết implementation giả (`FakeXxxRepository`) ghi dữ liệu mẫu vào Room hoặc trả `Flow` tĩnh đúng schema, mô phỏng đúng hành vi offline-first (queued → syncing → done) thay vì chỉ trả JSON tĩnh một lần.
4. Dùng Hilt để sau này đổi từ Fake sang Real repository chỉ bằng 1 dòng trong `RepositoryModule`, không sửa UI.
5. Với `lux_reading` (F07) và `notification` (F14): xem mục "Khoảng trống đã biết" ở trên — xác nhận entity/endpoint thật với WP2 trước khi code, không tự đặt tên bảng/field.

## Quy ước Git — theo chuẩn [Conventional Commits](https://www.conventionalcommits.org/)

### Nhánh — tuyệt đối không đụng vào `main`

- **`main` chỉ để đọc, không bao giờ commit hoặc push trực tiếp vào `main`**, kể cả khi thấy thay đổi nhỏ. Đây là quy tắc bắt buộc, không có ngoại lệ.
- Nhánh làm việc chính là **`dev`** — mọi nhánh tính năng đều tạo ra (branch off) từ `dev`, không tạo từ `main`.
- Quy trình: `dev` → tạo nhánh `<type>/fm-XX-...` từ `dev` → code → commit → push nhánh đó → mở PR **vào `dev`** (không mở PR thẳng vào `main`).
- Trước khi bắt đầu 1 task mới, luôn `git checkout dev && git pull` rồi mới tạo nhánh mới từ đó, tránh nhánh bị lệch xa `dev`.
- Nếu Claude Code phát hiện đang ở nhánh `main` khi chuẩn bị commit, phải dừng lại, báo cho mình, và tự chuyển sang tạo/checkout nhánh `dev` (hoặc nhánh con của `dev`) trước — không tự ý commit vào `main` dù được yêu cầu "commit giúp tôi" một cách chung chung.
- Việc merge `dev` vào `main` (khi có bản ổn định, demo...) do mình hoặc trưởng nhóm quyết định thủ công, Claude Code không tự merge/push vào `main`.

### Tên nhánh và commit — viết bằng tiếng Anh

- Tên nhánh (tiếng Anh, kebab-case): `<type>/fm-XX-mo-ta-ngan`, khớp đúng mã task `FM-XX` đối chiếu ở mục A3 của đặc tả (hoặc file task list mới nếu có trong `docs/`)
  Ví dụ: `feat/fm-09-survey-plan-checklist`, `fix/fm-08-work-order-pin-drag`
- Nội dung commit viết bằng **tiếng Anh**, theo format: `<type>(scope): description`
  - `type`: `feat` (tính năng mới), `fix` (sửa lỗi), `refactor`, `style` (đổi format, không đổi logic), `docs`, `test`, `chore` (deps, config), `perf`
  - `scope`: mã task, viết thường, VD `fm-09`
  - description: câu mệnh lệnh ngắn gọn, tiếng Anh, viết thường, không có dấu chấm cuối câu
  - Ví dụ:
    - `feat(fm-09): add survey readiness checklist`
    - `fix(fm-08): correct work order card tap target`
    - `chore(fm-01): set up ktlint and project structure`
- Mỗi task làm 1 PR riêng nếu được. Không gộp nhiều mã FM-XX vào 1 PR trừ khi chúng thực sự phải đi cùng nhau.
- Tiêu đề PR theo đúng format `type(scope): description` như commit message, cũng viết bằng tiếng Anh.
- Trường hợp breaking change (hiếm gặp trong dự án này): thêm `!` sau type/scope, VD `feat(fm-04)!: change API client base URL config`.
- Repo có file `.claude/settings.json` tắt sẵn attribution (`Co-Authored-By: Claude` và logo Claude trên GitHub) — đừng xoá file này, nó không phải file thừa.

## Nguyên tắc làm việc với Claude Code (bổ sung, đi cùng bộ nguyên tắc Karpathy đã cài qua plugin)

- Trước khi sửa code, nêu ngắn gọn kế hoạch và các file sẽ đụng tới; dừng lại hỏi nếu đặc tả không nói rõ field/behavior nào đó thay vì tự đoán (đặc biệt với `lux_reading` và `notification`, xem "Khoảng trống đã biết").
- Không tự thêm thư viện mới ngoài danh sách ở mục "Ngăn xếp công nghệ" nếu chưa hỏi qua.
- Không refactor code không liên quan tới task đang làm, kể cả khi thấy code cũ chưa đẹp.
- Sau khi code xong 1 task, tự chạy `./gradlew ktlintCheck` và sửa lỗi format trước khi coi là xong.
- Nếu 1 màn hình có 4 trạng thái bắt buộc theo đặc tả (đang tải / có dữ liệu / rỗng / lỗi), phải làm đủ cả 4, không được bỏ qua trạng thái rỗng/lỗi vì "ít quan trọng".
- Với các màn hình thao tác chính (F03, F04, F05, F06, F07, F10, F11), luôn tự kiểm tra lại: thao tác có ghi Room trước khi gọi API không, có bị chặn bởi trạng thái mạng không — nếu có, đó là vi phạm nguyên tắc offline-first.
- **Comment trong code viết bằng tiếng Anh, không dùng tiếng Việt** — kể cả comment giải thích lý do/quyết định kỹ thuật (dạng "vì sao chọn cách này"), không chỉ comment mô tả đơn thuần. Áp dụng cho mọi file code (`.kt`, `.kts`...). Tài liệu (`CLAUDE.md`, đặc tả, PR description, commit message) vẫn viết tiếng Việt/tiếng Anh như quy ước riêng của từng loại đã nêu ở trên, không đổi.

### Làm theo từng bước nhỏ, không viết một mạch rồi đưa hết

**Bắt buộc**, kể cả với task nhìn có vẻ đơn giản: khi triển khai một function/màn hình/luồng, chia nhỏ thành các bước hợp lý (VD: dựng UI tĩnh trước → nối state → nối Room/mock → nối API/sync queue → xử lý lỗi/loading/offline). Sau **mỗi bước**:

1. Dừng lại, tóm tắt ngắn gọn vừa làm gì và tại sao chọn cách đó.
2. Chờ mình đọc hiểu và xác nhận ("ok", "tiếp tục"...) rồi mới làm bước kế tiếp.
3. Không tự động dồn nhiều bước lại thành một lần trả lời chỉ vì "làm cho nhanh".

Mục đích: mình cần hiểu được từng phần code trước khi nó được thêm vào, không phải chỉ review một khối lớn ở cuối.

### Giới hạn kích thước commit

- Mỗi commit **không vượt quá 400 dòng code thay đổi** (tính cả thêm lẫn xoá, không tính file tự sinh như lock file).
- Nếu 1 task (VD 1 mã FM-XX) cần nhiều hơn 400 dòng để hoàn chỉnh, chủ động chia thành nhiều commit nhỏ hợp lý theo từng bước ở trên (VD: `feat(fm-09): add static UI for readiness checklist`, rồi `feat(fm-09): wire up checklist state`, rồi `feat(fm-09): connect mock repository`), thay vì cố nhét vào 1 commit.
- Nếu ước lượng trước một thay đổi sẽ vượt 400 dòng, báo trước cho mình biết dự kiến chia commit thế nào, trước khi bắt đầu viết code.
