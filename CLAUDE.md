# LuxMap — Mobile App (Android)

Đây là phần **Mobile — Field Operations**, ứng dụng di động hiện trường dành cho vai trò duy nhất **Tổ khảo sát/sửa chữa** (Field Crew) của hệ thống **LuxMap** (GIS + IoT + Computer Vision để quản lý tài sản và sự cố chiếu sáng đường giao thông nông thôn). Nền tảng **Android, Kotlin + Jetpack Compose**. Chỉ làm 1 nền tảng (không làm iOS song song).

Vai trò "Người dân" kiểu hệ thống cũ (CivicFlow) — có tài khoản, đăng nhập, dùng app riêng để theo dõi tiến độ phản ánh — **không còn tồn tại**. Tuy nhiên theo hồ sơ đăng ký đề tài (`docs/FA26SE222 v1.2.docx`, mục 3.2.c), LuxMap có vai trò **Citizen** ở dạng tối giản: quét QR code gắn trên trụ đèn để gửi một báo cáo sự cố, không tài khoản, không app, không theo dõi trạng thái. Bề mặt quét QR này **không thuộc phạm vi repo mobile này** (không phải app Android cho công dân) — nhưng khi làm F11/BE liên quan tới việc tạo `Fault`, cần biết `field_report` không phải nguồn ngoại lệ duy nhất, còn có nguồn từ QR công dân.

Bốn vai trò còn lại theo đúng tên trong hồ sơ đăng ký: **Superior** (giám sát tổng quan — Cơ quan quản lý), **Manager** (= Maintenance Engineer, Kỹ sư bảo trì), **System Admin** (Quản trị) — dùng nền tảng Web GIS riêng (không thuộc phạm vi repo này) — và **Field Engineer** (= Tổ khảo sát/sửa chữa), vai trò duy nhất của app mobile này. Mọi sự cố (`Fault`) trong LuxMap do engine IoT/CV sinh ra hoặc do Citizen gửi qua QR; Field Engineer chỉ báo cáo bổ sung tại hiện trường như một nguồn ngoại lệ (`field_report`).

## Tài liệu tham chiếu bắt buộc đọc trước khi code

- `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx` — đặc tả từng màn hình (mã F01–F15), bảng ánh xạ Màn hình → API → Bảng dữ liệu (mục C2–C3), enum trạng thái dùng chung (mục C4), quy tắc nghiệp vụ bắt buộc (mục C5), ma trận truy vết UI/trạng thái (mục P9)
- `docs/design-system/vNext/` (2026-11-16) — **Design System hiện hành** "Civic Utility": token màu Light/Night Field, 15 slot typography, shape, component spec, quy chuẩn bản đồ GIS, accessibility, ánh xạ Compose. Đọc `README.md` trước (quyết định đã duyệt + điểm còn chờ). Đi kèm `docs/design-system/AUDIT_REPORT.md` (audit hiện trạng UI) và `DESIGN_DIRECTION_RESEARCH.md` (so sánh hướng thiết kế). Thay thế v2.0 ở phần token; v2.0 vẫn là nguồn cho nguyên tắc nền tảng/thuật ngữ khi vNext chưa nói tới
- `docs/LuxMap_Mobile_Design_System_v2.0.md` — design tokens 3 lớp (primitive/semantic/component), typography, spacing, master components, quy tắc Dark Mode theo ngữ cảnh. **Phần token đã được vNext thay thế (2026-11-16)**
- `docs/LuxMap_Mobile_Technology_Stack.docx` — ngăn xếp công nghệ chốt, kiến trúc 5 lớp (Presentation/Domain/Data/Sync/Device), bảng local DB, quy tắc offline-first chi tiết
- `docs/LuxMap_Rural_Road_Lighting_GIS_phuonglhk.md` — hồ sơ đề tài: bối cảnh, kiến trúc tổng thể, vai trò hệ thống, câu hỏi nghiên cứu (RQ1/RQ2)
- `docs/FA26SE222 v1.2.docx` — hồ sơ đăng ký đề tài Capstone (bản chính thức nộp GVHD): bối cảnh, 8 module giải pháp, danh sách vai trò hệ thống (Superior/Manager/Field Engineer/System Admin/Citizen) và nghiệp vụ từng vai trò, yêu cầu phi chức năng, deliverables, phân công thành viên, câu hỏi nghiên cứu. Đây là nguồn xác định **tên và phạm vi vai trò hệ thống** — khi có khác biệt với các tài liệu đặc tả chi tiết khác về vai trò/phạm vi, ưu tiên đối chiếu lại với file này trước khi kết luận. **Đã xác nhận với chủ dự án (2026-09-25) và đã sửa lại mục A2 của `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`** cho khớp: hệ thống có 5 vai trò (thêm Citizen), không còn ghi "đúng 4 vai trò, Citizen không tồn tại" như bản cũ
- `docs/api-contract-v1.1.md.docx` — hợp đồng API chính thức giữa Backend và Frontend ("đổi field/enum phải mở issue, tăng version, không đổi ngầm"). Mục 2.9 (BE-42, lux-readings) không còn áp dụng cho mobile — F07 đã bị loại bỏ khỏi đặc tả (xem "Khoảng trống đã biết"). Mục 2.8 (BE-41) chốt `POST /api/v1/faults`. **Bản `.docx` trong repo này không có mục `notification`/BE-27 vì đã cũ — mục 5.9 của `api-contract-v1.1.md` thật trong repo `luxmap_backend` đã có, xem "Khoảng trống đã biết" mục F14 đã cập nhật 2026-10-13.**
- `docs/LuxMap_TaskList_v2.xlsx` — file task list dạng bảng tính đã xuất hiện (sheet `Frontend-Mobile`, `Huong dan`, `Backend`...). Theo đúng nguyên tắc đã nêu, **ưu tiên đối chiếu file này** cho mã `FM-XX` thay vì chỉ dùng mục A3 của đặc tả chi tiết.
- `docs/LuxMap_Mobile_Luong_Hoat_Dong_v1.0.md` — luồng hoạt động chi tiết vòng đời 1 sự cố đèn đường theo quyết định 2026-09-25 (video+track, AI phân loại, Inspection/Repair Task, 2 cổng duyệt). Mục 4 của file này liệt kê đúng các khoảng trống chưa chốt, khớp với mục "Khoảng trống đã biết" ở đây — đọc cả hai cùng nhau.
- `docs/Backend_API_Requirements_For_Mobile.docx` (2026-09-25) — đối chiếu trực tiếp từ source code backend (nhánh dev), liệt kê chính xác endpoint nào đã có Controller, endpoint nào chỉ thiếu Controller (field đã chốt, code được ngay), và endpoint nào còn phải chờ WP2/WP5/Dylan chốt schema (nhóm `survey-sweeps`, `inspection-tasks`, `repair-tasks`, `sync/bundle`, `sync/push`, `notifications`). **Dùng file này để biết endpoint nào code được ngay** thay vì tự đoán từ đặc tả chi tiết bản cũ. **Cập nhật 2026-10-14 — nhóm `survey-sweeps` ở đây đã lỗi thời, xem mục "Khoảng trống đã biết" bên dưới**: không có endpoint `survey-sweeps` nào cả, kể cả bản chốt — "giao tuyến khảo sát" dùng đúng `WorkOrder`/`task_kind=survey` như kiểm tra/sửa chữa.
- `docs/nghiep_vu_khao_sat_den.docx` (2026-09-27) — mô tả lại luồng thu thập dữ liệu khảo sát (video + GPS track liên tục), khớp với quyết định 2026-09-25 ở mục dưới, và thêm một nguồn dữ liệu thứ ba: cảm biến ánh sáng kết nối BLE, đo lux liên tục trong lúc quay. Nguồn BLE này đã được xác nhận với chủ dự án (2026-09-27) và ghi vào mục C7 của `LuxMap_Mobile_DacTaChiTiet_v2.2.docx` — xem ghi chú cập nhật ở mục "Khoảng trống đã biết" ngay dưới đây (chỉ còn thiếu tên bảng/field/endpoint chính thức, không còn là mâu thuẫn nghiệp vụ).

**Không thuộc danh sách bắt buộc đọc trước khi code nhưng có trong `docs/`:** `LuxMap_Mobile_Global_Design_System.md` — bộ token/hướng dẫn dùng để import vào công cụ AI sinh thiết kế (Claude Design), không phải nguồn token cho code Kotlin; nguồn token khi code vẫn là `LuxMap_Mobile_Design_System_v2.0.md`.

**Trước khi code một màn hình, luôn tra đúng mã màn (VD: F04) trong đặc tả để lấy đúng field, endpoint, quy tắc trạng thái — không tự đoán tên field hay endpoint.**

### Khoảng trống đã biết — dừng lại hỏi, không tự đoán

- **F07 (Nhập lux) — ĐÃ BỊ LOẠI BỎ khỏi đặc tả (cập nhật 2026-09-27), ĐẢO LẠI MỘT PHẦN 2026-11-23:** vẫn không có màn **nhập lux thủ công** (gõ tay). Nhưng `lux_reading`/`local_lux_reading`/`POST /api/v1/lux-readings` **được dùng lại** cho việc đo lux tuỳ chọn bằng máy đo cầm tay ESP32 trong luồng khảo sát từng cột — xem mục "Đo lux theo cột đèn (máy đo cầm tay ESP32)" phía dưới, nguồn đúng thay cho câu cấm cũ. Phần lịch sử dưới đây giữ để tham khảo: nguồn lux cho RQ1/CV-12 chuyển sang log lux BLE ghi trong phiên quay ở F04 (xem mục C7 của `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`), còn chờ WP4 xác nhận cách dùng lại dữ liệu này cho đối chiếu độ chính xác.
- **F14 (Thông báo) — ĐÃ CODE XONG VÀ MERGE VÀO `dev` (2026-10-18, PR #87, đã qua review độc lập + fix pass).** Phần ghi dưới đây (API backend, 2026-10-13) vẫn đúng; phần "vẫn khoảng trống thật" cũ đã lỗi thời, xoá. Xác nhận trực tiếp bằng cách đọc source code repo `luxmap_backend` (nhánh `dev`) và `docs/api-contract-v1.1.md` của repo đó: module `LuxMap.Modules.Notifications` **đã code xong và implemented** (BE-27, v1.10) — `NotificationsController.cs` có đủ 4 endpoint `GET /api/v1/notifications` (`?unread_only=&page=&page_size=`, trả `{page, page_size, total, unread_count, items[]}`), `GET /api/v1/notifications/unread-count` (`{unread_count}`), `POST /api/v1/notifications/{notification_id}/read` (204, idempotent), `POST /api/v1/notifications/read-all` (204). Mỗi item: `notification_id, type, title, body, entity_type, entity_id, created_at, read_at` — `title`/`body` do server soạn sẵn tiếng Việt, client hiển thị nguyên văn không tự dịch; `type` chọn icon (**13 giá trị thật, không phải 11 như bản ghi cũ** — đã sửa khi đọc lại source để code F14: `work_order_assigned/unassigned/rescheduled/returned/cancelled/completed/verified/overdue`, `survey_returned/ready_for_review/processing_failed`, `fault_reported`, `citizen_report_received`; giá trị lạ vẫn hiển thị được, không lỗi — `NotificationType.UNKNOWN` fallback); `entity_type`+`entity_id` dùng để mở đúng màn chi tiết tương ứng. **Đây là polling, không phải push** — BE ghi rõ "client polling: gọi `unread-count` mỗi 30–60 giây khi app mở, tải danh sách khi người dùng mở chuông. Một kênh push (Firebase) thêm sau sẽ đọc cùng dữ liệu — bốn endpoint này giữ nguyên." Firebase Cloud Messaging trong mục "Ngăn xếp công nghệ" **vẫn là việc chưa làm** (không cần cho F14 chạy được, chỉ cần cho đẩy thông báo tức thời sau này).
  **Mobile đã có:** `feature/notification/` (data + ui, `NotificationRepository` (Real), `NotificationListViewModel`/`Screen`, `NotificationBadgeViewModel`), chuông + badge số chưa đọc nằm trong `LuxTabTopBar` riêng của 3 tab Việc hôm nay · Khảo sát · Cá nhân (tab Bản đồ không có chuông; chuông nổi `NotificationBellBar` ở Scaffold của `NavGraph.kt` đã xoá 2026-11-17 khi redesign Cá nhân/Thông báo vNext), polling 45s chỉ chạy khi app foreground + đã đăng nhập (`repeatOnLifecycle` + gate theo `isLoggedIn`, tự reset badge khi logout), **Phạm vi điều hướng v1 — có chủ đích, không phải thiếu sót:** bấm vào 1 thông báo chỉ mở màn chi tiết thật khi `entity_type = "work_order"` (dùng `Routes.WorkOrderDetail`); `survey_sweep`/`fault`/`citizen_report` chỉ đánh dấu đã đọc, không mở màn — vì `survey_sweep` chưa có màn đích thật trong mobile (các màn khảo sát hiện dùng UUID cục bộ, không phải `SweepId` thật của server; xem mục "Giao tuyến khảo sát" phía dưới và mục F06/F13 ở trên), còn `fault`/`citizen_report` trên thực tế chỉ gửi cho Manager. Không Room cache (F14 vẫn thuộc nhóm màn "đơn giản" theo mục Kiến trúc & Design Pattern, không bắt buộc offline-first như F03/F04/F05/F06/F10/F11).
- **F06/F13 (Phiên khảo sát bị từ chối do chất lượng → khảo sát lại):** hồ sơ đăng ký đề tài (`FA26SE222 v1.2.docx`, mục 3.2.c) mô tả nghiệp vụ Manager "review... accept or reject submitted sessions" và Field Engineer "re-survey a segment when a session is rejected for quality", nhưng đặc tả chi tiết hiện tại (`LuxMap_Mobile_DacTaChiTiet_v2.2.docx` mục C4, D1) chỉ có `survey_sweep.status`: `planned/in_progress/pending_upload/submitted/processed` — không có trạng thái "bị từ chối"/"cần khảo sát lại", và luồng D1 dừng ở bước Kỹ sư bảo trì xác nhận sự cố, không có bước từ chối cả phiên. Trước khi code phần xử lý trạng thái sweep sau khi nộp (F06/F13), xác nhận với WP2/WP5 có bổ sung trạng thái/API mới hay không — không tự thêm enum hay màn hình cho việc này.
- **"Giao tuyến khảo sát" (Survey Hub assigned routes) — SỬA 2026-10-14, bản ghi cũ ở mục này sai.** Trước đó mobile code (`SurveyRepository`/`FakeSurveyRepository`/comment "GET /api/v1/survey-sweeps/planned") giả định có 1 endpoint riêng cho việc này — **endpoint đó không tồn tại**, xác nhận trực tiếp bằng cách đọc source code repo `luxmap_backend` (nhánh `dev`, 2026-10-14): `SweepsController.cs` (route thật `api/v1/sweeps`) và `SurveyIngestService.LockOrder()` (dòng 36–52) bắt buộc `WorkOrder.TaskKind == Survey` + `AssignedTo == người gọi` + `WoStatus == InProgress` trước khi cho tạo sweep. Nghĩa là "giao tuyến khảo sát" dùng **đúng y hệt `WorkOrder`** như kiểm tra/sửa chữa: Manager tạo `WorkOrder(task_kind=survey, assigned_to=FE)` → FE thấy qua **cùng endpoint** `GET /work-orders?task_kind=survey&assigned_to=me` mà Home (F02) đã dùng → FE bấm "Bắt đầu" (action `start` chung) → `wo_status=in_progress` → lúc đó mới được `POST /sweeps` với `work_order_id` đó. Đã sửa `SurveyRepository`/`SurveyHubViewModel`/`SurveyPlanScreen` đọc đúng nguồn này (khoá chọn tuyến đổi từ `surveySweepId` giả sang `workOrderId` thật), và sửa luôn 1 lỗi phát sinh từ giả định cũ: `WorkOrderDetailScreen` (F09) khi bấm "Bắt đầu" ở 1 lệnh `task_kind=survey` trước đây nhảy **thẳng** vào F04 Capture, bỏ qua hoàn toàn checklist readiness bắt buộc ở F03 — nay route qua `Routes.SurveyPlan` trước, đúng quy tắc "không cho bắt đầu khảo sát nếu chưa khoá exposure thật" ở trên. **Vẫn còn khoảng trống thật:** `POST /sweeps` (đăng ký sweep thật phía server) chưa được gọi ở đâu trong luồng upload mobile — `surveySweepId` hiện vẫn chỉ là UUID sinh tại máy cho phiên Room cục bộ, theo dõi riêng ở `docs/contract-drift.md`, không phải lỗi mới do thay đổi này.
- **Cảm biến lux qua BLE trong phiên quay khảo sát (F03/F04) — ĐÃ XÁC NHẬN với chủ dự án (2026-09-27), không còn là mâu thuẫn nghiệp vụ:** mâu thuẫn nêu ở bản trước (mobile tự kết nối BLE theo `docs/nghiep_vu_khao_sat_den.docx` so với `iot_node`/`runtime_history` là hạ tầng IoT cố định tự báo cáo lên server theo `docs/Backend_API_Requirements_For_Mobile.docx` mục 2.1) đã được xác nhận: đây là 1 nguồn dữ liệu thứ ba thật — video + track GPS/heading/timestamp (đã có ở mục C6) **cộng thêm** log lux liên tục qua BLE, gán timestamp bằng đồng hồ điện thoại — chứ không phải cách diễn đạt khác của IoT cố định. Luồng đầy đủ (chặn cứng "Bắt đầu quay" tới khi kết nối BLE ổn định → 3 luồng dữ liệu video/GPS/lux chạy song song độc lập trong phiên quay → dừng quay đóng gói video + 1 file log GPS+lux theo thời gian → upload cả bộ lên server, mobile không tự xử lý gì thêm) đã ghi vào mục **C7** của `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx`. **Cập nhật 2026-09-28 (mục C8.5 của đặc tả):** phân quyền đặt tên đã chốt — bảng Room cục bộ do WP6 (mobile) tự đặt tên chính thức, không cần chờ WP2/WP5; định dạng file trong "Session package" (log lux, GPS track, log timestamp frame, file cấu hình quay, manifest) do mobile soạn bản đầu (`v0`), WP4 duyệt sau; chỉ endpoint upload và tên field phía server vẫn chờ Backend (WP2/WP5). Mọi tên tạm và điểm lệch được theo dõi ở `docs/contract-drift.md`. **Cập nhật 2026-11-16 — code BLE trên mobile ĐÃ XOÁ** theo quyết định trực tiếp của chủ dự án: gói `core/ble` (`LuxSensorBleClient`/`LuxDeviceScanner`/`LuxPacketCodec`/`LuxDevicePreferences`, kết nối Classic Bluetooth SPP tới `LuxMap_ESP32`), test `LuxPacketCodecTest`, dòng "Cảm biến lux" ở màn Cá nhân và các quyền `BLUETOOTH*`/`FOREGROUND_SERVICE_CONNECTED_DEVICE` trong Manifest — người dùng duy nhất của nó là luồng quay video đã xoá ngày 2026-11-15. Nghiệp vụ cảm biến lux vẫn giữ như mô tả ở trên; nếu làm lại, lấy code cũ từ lịch sử git (trước commit xoá). **Cập nhật 2026-11-24 — `core/ble` đã được viết lại theo BLE GATT** (Nordic UART, máy `ESP32C3_LUX`) cho luồng đo lux theo cột; code SPP cũ không còn dùng, xem mục "Đo lux theo cột đèn (máy đo cầm tay ESP32)".
Xem `docs/contract-drift.md` để biết chính xác tên nào đã chốt, tên nào còn tạm.

- `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` (2026-10-10, copy từ Claude Design project `82538650-3a87-4ecd-8cbc-37aaf17badc1`, bản gốc `LuxMap Redesign.dc.html`) — spec thiết kế lại giao diện ("Night Ledger"), token/component/màn hình chi tiết. **Đã duyệt chuyển token thương hiệu/semantic sang v2.1 (2026-10-11)** — xem mục "Design tokens" dưới đây đã cập nhật theo spec này. Lộ trình triển khai theo từng màn: `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md`.
- `docs/LuxMap_Mobile_Design_System_v2.1.md` (đề xuất, phần token đã duyệt 2026-10-11) — bổ sung cho `LuxMap_Mobile_Design_System_v2.0.md`: token `Disabled` mới, typography bổ sung, component mới. Đọc cùng với `LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2 vì 2 file có 1 điểm xung đột giá trị (token `Disabled`) và 1 điểm xung đột màu hero F01 — cả hai được ghi rõ trong roadmap ở trên, chưa tự chọn một bên.
- `docs/LuxMap_Mobile_Screen_Review_2026-10-11.md` — đối chiếu trực tiếp giữa prototype thiết kế và code/CLAUDE.md hiện tại tại thời điểm viết; dùng để biết phần nào của redesign là style thuần, phần nào đụng vào field/endpoint chưa chốt.

### Quyết định nghiệp vụ mới đã chốt (2026-09-25) — chưa cập nhật vào đặc tả chi tiết / api-contract v1.1

**Cập nhật 2026-11-15 — CODE CỦA LUỒNG QUAY VIDEO LIÊN TỤC (F03 Lập kế hoạch + F04 Capture Mode + F05 Kiểm tra độ phủ + F06 Nộp đợt khảo sát, bước 2 ở mô tả dưới đây) ĐÃ BỊ XOÁ HẲN khỏi mobile, theo quyết định trực tiếp của người dùng.** Lý do: luồng này đã mất lối vào UI từ lâu (nút "Bắt đầu" ở Hub Khảo sát/F09 chỉ còn dẫn vào luồng chụp ảnh/video từng cột — xem mục "chụp ảnh/video ngắn theo từng cột đèn" phía dưới), nhưng mã nguồn vẫn còn, khiến người dùng thấy lệch khi hỏi lại. Đã xoá: `feature/survey/ui/{plan,capture,coverage,submit,aiconfirm}/`, `feature/survey/capture/` (toàn bộ, gồm `VideoCaptureSession.kt`), `feature/survey/domain/` (`GpsAccuracyGate.kt`, `CheckSurveyReadinessUseCase.kt`, `SurveyReadinessInputProvider.kt`), các entity/DAO/sync-handler/repository riêng cho sweep (`LocalSurveyPlanEntity`/`LocalSurveySessionEntity`/`LocalSurveyVideoSegmentEntity`/`LocalRoadSegmentEntity`, `SurveyPlanDao`/`SurveySessionDao`, `UploadRepository`, `AiClassificationRepository`, 4 sync handler sweep), và các lớp `core/camera`/`core/location`/`core/common` chỉ phục vụ riêng luồng này (`CameraSelector`, `ExposureLockController`, `FrameTimestampLogger`, `SegmentedVideoRecorder`, `SegmentRotationPolicy`, `StorageMonitor`, `SurveyTrackRecorder`). `AppDatabase` bump v19→v20 (4 entity + 2 DAO bị bỏ, `fallbackToDestructiveMigration()` đã có từ trước nên không cần migration DROP TABLE). Tác dụng phụ đã sửa luôn: badge "GPS yếu · nên đo lại" ở `PoleGpsRow` (luồng `pole_gps_update`, mục "chụp ảnh/video ngắn theo từng cột đèn") từng mượn tạm ngưỡng 8m của `GpsAccuracyGate` — nay bỏ hẳn cảnh báo đó, vì BE (`WorkOrderService.cs` `Report()`) không có ngưỡng độ chính xác tối thiểu nào, chỉ kiểm tra `lat∈[-90,90]`, `lng∈[-180,180]`, `accuracy_m≥0` và là số hữu hạn. **Bước 1 (video khảo sát toàn tuyến + track GPS/heading/timestamp liên tục) và các mục C6/C7 của đặc tả mô tả nghiệp vụ này vẫn giữ nguyên dưới đây — chỉ phần triển khai trên mobile là đã xoá, không phải nghiệp vụ bị huỷ.** Nếu làm lại luồng này sau, coi mục này là điểm bắt đầu, không phải code cũ đã xoá.

Đã xác nhận với chủ dự án, thay đổi lớn so với F08–F10 hiện mô tả trong `LuxMap_Mobile_DacTaChiTiet_v2.2.docx` (1 `WorkOrder` liền mạch) và chưa có mục nào tương ứng trong `api-contract-v1.1.md.docx`. **Không code theo enum/model cũ của F08–F10 cho tới khi đặc tả và API contract được cập nhật chính thức** — phần dưới đây là quyết định nghiệp vụ đã chốt, chưa phải tên bảng/field/endpoint thật.

**Cập nhật 2026-10-13 — phần F08–F10/`WorkOrder` (điểm 2 dưới đây) ĐÃ CÓ tên bảng/field/endpoint thật, không còn là "chưa phải tên thật" nữa.** Xác nhận trực tiếp bằng cách đọc source code repo `luxmap_backend` (nhánh `dev`, commit `00725dd`, 2026-10-12) và `docs/contract-drift.md` + `docs/api-contract-v1.1.md` của repo đó — không phải suy đoán từ `docs/Backend_API_Requirements_For_Mobile.docx` (file đó đã cũ, ghi "MỚI - CẦN CHỐT" cho các endpoint này nhưng BE đã chốt và code xong từ 28–29/09/2026 qua các ticket BE-23/FR-2/FR-2a/FR-3/BE-24). Xem chi tiết đã sửa ở điểm 2. **Điểm 1 (`ai_classified`/`final_classified`, survey AI) vẫn CHƯA xác nhận** — không đụng tới, giữ nguyên nguyên tắc audit trail ở dưới.

5 vai trò hệ thống là Superior/Manager/Field Engineer/System Admin/Citizen — Superior chỉ xem báo cáo tổng quan, không liên quan tới mobile, giữ nguyên như đã ghi ở phần đầu file, không cần sửa thêm.

Luồng Field Engineer đã chốt (khác hẳn thiết kế F08–F10 hiện tại):

1. Manager giao tuyến khảo sát cho Field Engineer.
2. FE quay video hiện trường, đồng thời ghi liên tục 1 track GPS + heading + timestamp trong suốt thời gian quay (không chụp ảnh rời như `SurveyFrame` mô tả trong đặc tả hiện tại). Mobile upload lên server **cả video lẫn file track GPS/timestamp** — **không** tự cắt frame trên máy. Việc cắt frame từ video và khớp từng frame với mốc GPS/timestamp gần nhất trong track do **server/AI xử lý** (đã xác nhận 2026-09-25) — mobile không cần thêm thư viện xử lý video (MediaMetadataRetriever hay tương tự) ngoài danh sách đã chốt ở mục "Ngăn xếp công nghệ".
   - Tác động tới kiến trúc capture hiện tại (`feature/survey`, `core/camera`, `core/location`): việc khoá exposure (`ExposureLockController`, `CONTROL_AE_MODE_OFF`/`SENSOR_EXPOSURE_TIME`/`SENSOR_SENSITIVITY`) cần áp dụng cho **phiên quay video**, không chỉ 1 lần chụp ảnh tĩnh; `LocationTracker`/`HeadingSensor` cần ghi **liên tục theo thời gian** (track nhiều điểm có timestamp) thay vì chỉ lấy 1 fix tại thời điểm bấm chụp; `CaptureMetadataWriter`/`ExifInterface` không còn ghi trực tiếp vào ảnh lúc chụp (vì chưa có ảnh, chỉ có video) — vai trò của nó và tên bảng Room lưu track (thay cho hoặc thêm cạnh `local_survey_frame`) cần chốt lại với WP2/WP5 trước khi sửa code F03/F04, không tự đặt tên bảng/field.
3. AI sinh báo cáo, phân loại từng cột đèn theo `normal/dim/out`.
4. FE xem kết quả AI, **có thể sửa** phân loại nếu thấy sai, kèm ghi chú lý do sửa (bước duyệt sơ bộ tại hiện trường, không phải Manager duyệt).
5. FE gửi kết quả lên Manager duyệt (cổng duyệt #1).
6. [Manager duyệt] → Manager giao **Inspection Task** (kiểm tra hiện trường) cho 1 Field Engineer — có thể khác người đã khảo sát ở bước 1–2.
7. FE đi kiểm tra, nộp báo cáo kiểm tra → gửi lên Manager duyệt (cổng duyệt #2).
8. [Manager duyệt] → Manager giao **Repair Task** (sửa chữa) cho 1 Field Engineer — có thể khác người ở bước 6–7.
9. FE sửa chữa, nộp ảnh/video kết quả cho Manager.

Hai điểm tác động trực tiếp tới mô hình dữ liệu và kiến trúc code, ghi lại ngay để không ai code lệch trong lúc chờ cập nhật đặc tả/API contract chính thức:

1. **Tách `ai_classified` khỏi `final_classified`/override** — không ghi đè trực tiếp lên kết quả AI. Đề tài cần đối chiếu độ chính xác AI với kết quả con người xác nhận để trả lời RQ1, nên bắt buộc giữ audit trail: có sửa hay không, sửa từ giá trị gì thành giá trị gì, sửa lúc nào (và bởi ai, khi có ngữ cảnh nhiều FE liên quan tới cùng một tuyến). Áp dụng cho model kết quả phân loại AI ở feature `survey` — chưa có tên bảng/field chính thức, không tự đặt tên khi code, chờ API contract.
2. **`WorkOrder` không còn là 1 lệnh liền mạch — ĐÃ CHỐT VÀ HIỆN THỰC XONG ở backend (cập nhật 2026-10-13), không phải tách 2 entity như dự đoán ban đầu.** BE giữ **1 entity `WorkOrder` duy nhất** (không tách `InspectionTask`/`RepairTask`), diễn tả 2 giai đoạn + 2 cổng duyệt bằng:
   - `task_kind: inspection | repair | survey` (enum thật, đã có cả `survey` theo FR-1/BE-15, không chỉ 2 giá trị như đoán trước đây).
   - `wo_status` thật: `open | assigned | in_progress | done | verified | cancelled` — **không có `overdue`** (khác với enum đoán ở mục C4 đặc tả cũ `new/assigned/in_progress/done/overdue/cancelled`, đã lỗi thời).
   - **Chaining thay cho tách entity**: `POST /api/v1/work-orders/{id}/follow-up` (Manager, chỉ gọi được khi phiếu cha `verified`) tạo phiếu kế tiếp, nối bằng `parent_work_order_id` + `case_id` (= id phiếu gốc của cả chuỗi khảo sát→kiểm tra→sửa chữa). `GET /work-orders?case_id=` trả cả chuỗi.
   - Per-fault outcome: `inspection_outcome: fault_present | fault_absent | inconclusive` lưu trên từng dòng nối `WorkOrder↔Fault`, gửi lúc `POST /work-orders/{id}/complete` qua `fault_outcomes[{fault_id, outcome}]` (bắt buộc với phiếu `inspection` có fault). `verify` một phiếu kiểm tra tự chuyển `fault_present` từ `detected`→`confirmed` (FR-2a).
   - `materials_note` (Manager đặt lúc tạo/PATCH) tách riêng `materials_used` (FE ghi lúc `complete`) — 2 field khác nhau, không dùng chung (FR-3).
   - Ảnh evidence `POST /work-orders/{id}/evidence`: `kind=before|after` cho phiếu `repair`, `kind=observation` cho phiếu `inspection` (BE-24/EV-1) — không dùng chung nhãn.
   - Response `WorkOrderDetail` có sẵn `allowed_actions[]`, `review_note`, `report_note`, `case_id` để FE không phải tự suy máy trạng thái.
   - Các endpoint khác đã có: `GET /work-orders` (lọc `wo_status`/`task_kind`/`assigned_to`/`segment_id`/`commune_id`/`case_id`), `GET /{id}`, `GET /{id}/poles`, `GET /assignees`, `POST` (tạo), `PATCH /{id}` (chỉ `title`/`due_date`/`scheduled_date`), `PUT /{id}/assignee`, `POST /{id}/start|verify|return|cancel`, `GET /work-orders/agenda` (BE-25, "việc đêm nay" gom theo tuyến).
   - Mobile (`WorkOrderDetailDto.kt`, `CompleteWorkOrderRequestDto.kt`, `Real...Repository`) **đã khớp đúng các field thật này từ trước** — phần F08–F10 hiện tại không cần sửa field, Phase 8 của roadmap redesign chỉ còn là restyle UI (`LifecycleTracker`/`OutcomeSegment`/`AppTopBar`), không còn "partially blocked".
   - Lưu ý: các quyết định này backend tự ghi là **SELF-SIGNED / nền tạm tới FW kế tiếp xác nhận** (`docs/contract-drift.md` của `luxmap_backend`, mục BE-23/FR-2/FR-2a/FR-3) — đã chạy thật trên `dev`, nhưng chưa qua vòng duyệt chính thức với WP2/WP5. Coi là nguồn đúng để code theo, nhưng nếu BE đổi lại sau vòng duyệt đó thì cập nhật lại mục này.

**Trước khi code lại phần xử lý kết quả phân loại AI trong `survey` (điểm 1 ở trên):** vẫn cần xác nhận field/entity/endpoint thật với WP2/WP5 — điểm này **chưa** được xác nhận, không tự đặt tên khi code, dừng lại hỏi nếu cần bắt đầu trước khi tài liệu được cập nhật. Điểm 2 (F08–F10/`WorkOrder`) đã xác nhận xong theo mục trên, code được ngay.

### Quyết định nghiệp vụ mới đã chốt (2026-10-15) — chụp ảnh/video ngắn theo từng cột đèn, ưu tiên làm, chưa có đặc tả/API

Đã xác nhận trong cuộc trao đổi với chủ dự án (truyền đạt qua mentor đồ án/GVHD, 2026-10-15) — **mentor đồ án được coi là chủ dự án** cho quyết định này. Mentor yêu cầu **ưu tiên làm** phần này trước các việc khác.

Quyết định: **thêm 1 luồng khảo sát mới theo từng cột đèn, chạy SONG SONG với luồng quay video liên tục toàn tuyến hiện có (luồng cũ ở mục "Quyết định nghiệp vụ mới đã chốt (2026-09-25)" phía trên — giữ nguyên, không xoá, không thay thế).**

- Khi Field Engineer đã được giao tuyến khảo sát, tại **từng cột đèn** trên tuyến: FE có thể **chụp 1 ảnh HOẶC quay 1 video ngắn** cho cột đó (không bắt buộc cả hai, không phải chụp ảnh rồi mới được quay video — xác nhận lại 2026-10-15, khác với câu chữ "chụp ảnh rồi gửi lên AI quét, sau đó là 1 video ngắn" nêu ban đầu).
- Ảnh/video ngắn này được tải lên để **AI quét/phân loại** cột đèn đó — mobile không tự xử lý ảnh/video, chỉ tải lên.
- Đây là lựa chọn/bước **khác**, tách biệt khỏi luồng quay video liên tục toàn tuyến đã chốt 2026-09-25 — không gộp, không sửa logic của luồng cũ.

**Đã chốt — cập nhật 2026-10-16, không còn là khoảng trống:**
- Endpoint ảnh cột đèn (upload + đọc lại) đã có thật ở backend và **code được ngay**, xác nhận trực tiếp bằng cách đọc source code `luxmap_backend` nhánh `dev` (`WorkOrderPolePhotosController.cs`/`WorkOrderPolePhotoService.cs`/`WorkOrderPolePhotoContracts.cs` — đã merge, PR #151/#153, nhánh `feat/POLE-PHOTO-AI`), sau khi BE lead mô tả luồng qua chat. Toàn bộ 6 endpoint (`POST .../pole-photos`, `GET .../pole-photos`, `GET .../pole-photos/{photo_id}`, `.../thumbnail`, `.../original`, `POST .../decision`) cùng field/response thật đã ghi đầy đủ vào `docs/contract-drift.md` (dòng "Pole photo endpoints...") và mục 8 của `docs/superpowers/specs/2026-10-15-pole-photo-capture-design.md` — dùng đúng field đó khi viết `RealPolePhotoRepository`, không tự đoán lại. Endpoint `POST .../pole-photos/{photo_id}/decision` (policy `ReviewSurveys`, khác hẳn 4 endpoint còn lại dùng `ExecuteWorkOrders`/`ReadWorkOrders`) xác nhận là **ngoài phạm vi mobile** — Manager dùng Web GIS riêng, mobile không gọi endpoint này và Field Engineer không xem kết quả decision trong app. **Cập nhật 2026-10-20 — có 1 endpoint mới nằm giữa 2 nhóm trên, lại thuộc phạm vi mobile, xem đoạn PHOTO-REVIEW ngay dưới đây.**
- Kiến trúc màn hình phần **video ngắn** (nửa còn lại của yêu cầu mentor, chưa làm) vẫn chưa quyết — xem mục "Ngoài phạm vi v1" của spec.

**Kỹ sư review nhãn AI trước khi Quản lý quyết định — THÊM 2026-10-20 (ticket BE `PHOTO-REVIEW`), code được ngay, chưa viết ở mobile.** Xác nhận trực tiếp bằng cách đọc source `luxmap_backend` (`WorkOrderPolePhotosController.cs` dòng ~75 `PATCH {photo_id}/review`, `WorkOrderPolePhotoService.ReviewAsync`, migration `20261020134307_AddPolePhotoReviews`, `.ai/results/PHOTO-REVIEW-p2.md` — 229/229 test pass, đã merge). Đây là **thêm**, không đổi 6 endpoint cũ: `/decision` (Quản lý, `ReviewSurveys`) và 4 field `decision/decided_by/decided_at/decision_note` giữ nguyên y hệt — không được xoá hay coi là thay thế.
- Endpoint mới `PATCH /api/v1/work-orders/{id}/pole-photos/{photo_id}/review`, policy `ExecuteWorkOrders` — **chỉ Field Engineer được giao chính phiếu đó**, cùng kiểu 404/409 như lúc upload (`WORK_ORDER_NOT_FOUND`/`WORK_ORDER_NOT_IN_PROGRESS`), ảnh phải thuộc đúng phiếu/đúng xã. Dùng để kỹ sư sửa/đồng ý nhãn `cv_state` của AI trước khi Quản lý công bố — khác việc Quản lý `/decision` (công bố kết quả, đổi trạng thái bản đồ, tạo `lamp_out`).
- `PolePhotoItem` có thêm 4 field mới (mọi response ảnh, không chỉ sau khi review): `final_cv_state` (`"on"`/`"off"`, null khi chưa review — **không mặc định bằng `cv_state`**, FE muốn hiện nhãn hiệu lực thì tự tính `final_cv_state ?? cv_state`, không nhận sẵn từ server), `review_note`, `reviewed_by`, `reviewed_at`.
- Body `{final_cv_state: "on"|"off", review_note?}` — `review_note` **bắt buộc** khi `final_cv_state != cv_state`, kể cả khi `cv_state` là `null`/`"inconclusive"` (kỹ sư luôn chọn được on/off ngay cả khi AI không kết luận hoặc lỗi) → thiếu note trong trường hợp này là 400. Gọi lại nhiều lần trước khi Quản lý quyết định thì ghi đè (có audit before/after phía server); gửi lại y hệt giá trị cũ là no-op. Ảnh đã có `decision` (Quản lý đã quyết) → 409 `POLE_PHOTO_ALREADY_DECIDED`, khoá review vĩnh viễn cho ảnh đó.
- 4 endpoint GET (list/detail/thumbnail/original) **vẫn đúng** `ReadWorkOrders` như đã ghi — không đổi thành `ExecuteWorkOrders`; chỉ upload và review mới dùng `ExecuteWorkOrders`.
- Chi tiết đầy đủ (field, lỗi, ví dụ) xem `docs/contract-drift.md` dòng "PATCH .../review — new 2026-10-20". **Đã code xong toàn luồng (2026-10-21), đã test, chưa commit.** 4 field đã có trong DTO/entity Room `local_pole_photo` (bump DB v11→v12); `PolePhotoRepository.reviewPhoto()` (Real + Fake) gọi `PATCH .../review` thật, dùng chung hàm ghi Room (`PolePhotoDao.updateAiResult`) với `fetchResult`; `PolePhotoResultBottomSheet` có `ReviewCard` (chọn On/Off qua `ReviewChoiceSegment`, ghi chú bắt buộc khi khác AI kể cả khi AI null/inconclusive, ẩn hẳn khi đã có `decision`). **Đã sửa luôn khoảng trống AI lỗi hoàn toàn:** `PolePhotoResultUiState.Success.cvState` đổi thành `String?` — `ai_status="unavailable"` (AI crash, `cv_state` null) giờ map vào `Success(cvState = null)` thay vì `Empty`, nên kỹ sư vẫn review được (đúng luật D-2); `PolePhotoResultUiState.Empty` giờ chỉ còn là nhánh phòng thủ cho đúng mẫu 4-trạng-thái bắt buộc, hợp đồng server đảm bảo không bao giờ thực sự rơi vào nhánh này. Test: `PoleListViewModelTest` +3 case (`onSubmitReview` thành công/thất bại, mapping `unavailable`→`Success`), `RealPolePhotoRepositoryTest` +3 case, `PolePhotoResultBottomSheetTest` +5 case — tất cả chạy thật trên máy Samsung SM-A075F (`PolePhotoResultBottomSheetTest` 15/15, `PoleListScreenTest` 11/11, `PolePhotoDaoTest` 6/6, không có test nào bị regression). **Lưu ý bẫy đã gặp khi viết test:** `performClick()` trên nút ở cuối nội dung cuộn dài (nút "Gửi xác nhận"/"Chụp lại cột này", sau khi thêm `ReviewCard` làm sheet dài hơn) có thể auto-scroll tính sai toạ độ trên máy thật, khiến chạm rơi ra ngoài nút (test fail "expected true but was false" không kèm exception) — sửa bằng cách gọi rõ `.performScrollTo()` trước `.performClick()` trong test, không phải lỗi code UI thật. **Còn lại:** chưa commit (diff ước ~700 dòng, phải chia nhiều commit theo giới hạn 400 dòng/commit).

**Đã code xong phần chụp ảnh (v1, ưu tiên demo) — bắt đầu 2026-10-15, hoàn thiện dần qua PR #70-81 (tới 2026-10-18).** Phần dưới đây đã cập nhật theo code thật ngày 2026-10-18 (audit lại toàn bộ luồng) — bản ghi cũ hơn mô tả trạng thái ngày đầu code (chỉ có Fake, chưa nối sync_queue, chưa cache) đã lỗi thời, không áp dụng nữa:
- Màn hình: `feature/survey/ui/polephoto/` — `PoleListScreen`/`PoleListViewModel` (danh sách cột, gọi `GET /work-orders/{id}/poles` thật) và `PolePhotoCaptureScreen`/`PolePhotoCaptureViewModel` (chụp 1 ảnh/cột). Routes: `survey/pole-photos/{workOrderId}` và `survey/pole-photos/{workOrderId}/capture/{poleId}`. Vào từ nút "Chụp ảnh cột đèn" mới trong `SurveyHubScreen`.
- Repository **đã là Real, không còn Fake**: `RepositoryModule.kt` bind `RealPolePhotoRepository`/`RealPoleListRepository`. Ghi Room (`local_pole_photo`, entity `LocalPolePhotoEntity`, DAO `PolePhotoDao`) trước, rồi đẩy `sync_queue` qua `UploadPolePhotoSyncHandler` — handler này **đã đăng ký thật** vào `Set<SyncOpHandler>` (`SyncModule.kt`) và chạy qua `SyncQueueProcessor`/WorkManager, dùng `client_op_id` = photoId local làm idempotency key, phân biệt đúng lỗi tạm thời (retry)/vĩnh viễn (`failed`)/409 (`conflict`). `PoleListScreen` đã cache Room (`local_pole_list_item`, PR #77) và sheet kết quả AI đã cache-first + `isStale` (PR #76) — cả 2 gap "chưa cache Room" ghi ở bản cũ đã đóng. `FakePolePhotoRepository` đã bị xoá 2026-11-17 cùng các bản Fake khác (xem mục Kiến trúc).
- **Camera dùng camera hệ thống qua Intent (`ActivityResultContracts.TakePicture()`), KHÔNG dùng CameraX.** Đây là thay đổi so với lần ghi đầu tiên (từng định dùng CameraX `ImageCapture`) — khi code mới phát hiện màn "ảnh sau" của F10 (`EvidenceCaptureScreen`/`EvidenceCaptureViewModel`) đã làm đúng việc này rồi (1 ảnh, gắn GPS, lưu Room), nên tái dùng pattern đó thay vì viết mới. Vẫn giữ nguyên lý do ban đầu: khoá exposure thật **KHÔNG áp dụng ở v1** cho màn chụp ảnh theo cột — đây là **ngoại lệ có chủ đích, đã xác nhận với người dùng**, không phải quên, vì khoá exposure thật đòi hỏi viết thêm 1 pipeline Camera2 thủ công như `VideoCaptureSession.kt` (992 dòng). **Nợ kỹ thuật cần làm sau demo**, không áp dụng cho luồng video liên tục hiện có (luồng đó vẫn khoá exposure thật, không đổi). Không ghi EXIF vào ảnh (GPS/thời gian chỉ lưu trong Room, giống hệt F10).
  - **Câu hỏi mở, chưa hỏi mentor/BE (ghi lại 2026-10-18, dừng lại hỏi trước khi tự quyết định có cần làm hay không):** model AI xử lý `cv_state` cho endpoint `POST .../pole-photos` có yêu cầu ảnh phơi sáng cố định (khoá AE) không? Nếu model nhạy với việc camera tự bù sáng, 1 cột đèn mờ/gần tắt có thể bị auto-exposure "chụp sáng lên" trông giống đèn bình thường, làm sai kết quả phân loại — lúc đó bắt buộc phải làm pipeline Camera2 khoá exposure thật trước khi dùng ngoài hiện trường thật (không chỉ demo). Nếu model không nhạy (train trên ảnh auto-exposure, hoặc BE/AI tự chuẩn hoá độ sáng) thì giữ nguyên camera hệ thống như hiện tại là đủ. **Không tự quyết định hướng nào khi chưa có câu trả lời.**
- **Test coverage, cập nhật 2026-10-18**: `PoleListScreen` nay đã có Compose UI test (`PoleListScreenTest.kt`, 11 case — 4 trạng thái UiState, tap cột, badge sync, icon xem kết quả AI, banner stale, nút Hoàn tất/Thử lại), chạy thật trên máy (SM-A075F) 11/11 pass. **Chủ động không viết test cho Composable `PolePhotoCaptureScreen`/`PolePhotoCaptureRoute`** (quyết định, không phải thiếu sót): logic nghiệp vụ thật đã có `PolePhotoCaptureViewModelTest` che (3 case); phần còn lại trong composable chỉ là ống dẫn Android thuần (check quyền `CAMERA` qua `ContextCompat`, dựng URI `FileProvider`, bắn Intent camera hệ thống) — không mock được quyền thật nên test sẽ phập phù theo máy, và cách làm đáng tin cần thêm dependency `espresso-intents` (chưa có trong "Ngăn xếp công nghệ", đã hỏi và người dùng chọn không thêm, 2026-10-18). Dựa vào test ViewModel + kiểm tra thủ công trên máy thật (PR #74) cho phần này.

### Đo lux theo cột đèn (máy đo cầm tay ESP32) — THÊM 2026-11-23, nghiệp vụ đã chốt, CHƯA code

Quyết định trực tiếp với chủ dự án (2026-11-23). Spec kỹ thuật: `docs/superpowers/specs/2026-11-23-pole-lux-measurement-design.md`. Backend **đã có sẵn** endpoint, xác nhận bằng cách đọc source `luxmap_backend` (`LuxReading.cs`, `LuxReadingConfiguration.cs`, `docs/api-contract-v1.1.md` mục 5.7, trạng thái `implemented`) — mobile không cần chờ BE.

- **Mục đích:** AI ảnh/video cột chỉ phân biệt sáng/tắt (`YoloOnOffDetector.cs`: "CV decides ON / OFF only; Dim comes from the lux sensor") — số lux là dữ liệu để **server** phân loại `dim` và đối chiếu RQ1.
- **Thiết bị (sửa 2026-11-24, kiểm tra trên máy thật):** máy đo cầm tay **`ESP32C3_LUX`** (chip ESP32-C3, **chỉ BLE**, không phải node IoT gắn trụ). Giao thức: **Nordic UART Service** — service `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`, notify characteristic `6E400003-…`, mỗi gói là 1 dòng chữ `"Lux: 0.83
"`, khoảng **4 dòng/giây**, không cần bond. Máy cũ `LuxMap_ESP32` (Bluetooth Classic SPP, dòng `"… | Light: 69.17 lux"`, 1 dòng/giây) **không còn được hỗ trợ** (quyết định của chủ dự án 2026-11-24). Ghép nối 1 lần ở màn Cá nhân (quét BLE, lưu địa chỉ), hoặc ngay trên thẻ đo.
- **Luồng:** chỉ khi **chụp ảnh/quay video trực tiếp** từng cột (`feature/survey/ui/polephoto/`) — **không** áp dụng cho ảnh/video chọn từ thư viện (không biết chụp lúc nào nên không ghép được với số đo hiện tại), và không có nút "Đo lux" riêng không kèm ảnh. Ở bước xem lại trước khi lưu, thẻ "Đo lux (tuỳ chọn)" hiện số trực tiếp; bấm "Ghi nhận" lấy trung vị **12 mẫu liên tiếp (~3 giây ở 4 mẫu/giây)**, chỉ bấm được khi số ổn định. Bỏ qua được, đo lại tạo bản ghi mới (UI hiện lần gần nhất).
- **Quy tắc:** (R1) không bao giờ chặn chụp/upload/hoàn tất lệnh; (R2) số lux **gắn trực tiếp với ảnh/video chụp cùng lúc, cả trên server** (bổ sung 2026-11-23) — nhưng `POST /lux-readings` đã có field `photo_id` (BE chốt 2026-11-24, xem mục dưới): thao tác lux chờ upload ảnh xong để lấy `serverPhotoId`, ảnh lỗi vĩnh viễn thì vẫn gửi lux không kèm ảnh. **Không** nhét id ảnh vào `note`; (R3) luôn ghi "số đo tương đối", mobile **không** hiện "mờ"/"đạt"/"không đạt" — review của kỹ sư vẫn chỉ on/off; (R4) offline-first: Room `local_lux_reading` → `sync_queue`, độc lập với upload ảnh; (R5) chặn giá trị âm/NaN/Infinity/gói lỗi/mất kết nối giữa chừng, >200 chỉ nhắc nhẹ; (R6) `data_source=field` (testbed: `calibration_rig`); (R7) hiện hướng dẫn tư thế đo cố định.
- **Endpoint:** `POST /api/v1/lux-readings` — body `client_op_id, pole_id, measured_at, lux_value, data_source, meter_model ("ESP32C3_LUX"), note?`; trùng `client_op_id` → 200 (coi là xong); `404 POLE_NOT_FOUND` → `failed`; cấm gửi `lux_id`/`commune_id` (`400 SERVER_OWNED_FIELD`).
- **`photo_id` — ĐÃ CHỐT 2026-11-24** (BE PR #293, contract v1.23, drift LUX-PHOTO-1…5): `POST /lux-readings` nhận `photo_id` tuỳ chọn (id `PPH-…` của ảnh/clip trên server), mỗi ảnh tối đa 1 số đo; lỗi `404 POLE_PHOTO_NOT_FOUND`, `400 PHOTO_POLE_MISMATCH`, `409 PHOTO_LUX_ALREADY_LINKED`; `PolePhotoItem` có thêm `lux_id`/`lux_value`/`lux_measured_at`. Mobile gửi `photo_id` từ `serverPhotoId` (PR 6): handler tự chờ ảnh upload xong (`WaitingForDependency`, **không** dùng `dependsOnClientOpId` vì processor sẽ đánh lỗi luôn op lux khi ảnh lỗi), ảnh lỗi hẳn/bị xoá → gửi không kèm `photo_id`, server từ chối liên kết → gửi lại 1 lần không kèm. Contract 5.7 nay đã ghi nhận máy đo cầm tay, nhưng ví dụ ghi `meter_model = "LuxMap_ESP32"` trong khi máy thật là `ESP32C3_LUX` — cần nhắn BE sửa ví dụ.
- **Đo đứng yên theo quy trình — THÊM 2026-11-26** (BE LUX-BASELINE, contract **v1.24**, drift LUX-BASELINE-1…6; D-R29 của BE: lux đo **đứng yên ngay dưới đầu đèn, sào cao 1,0 m**). Mỗi số đo gửi kèm `protocol {samples, sensor_height_m, module_id}`: `samples` là cửa sổ 12 mẫu thô đã dùng, `sensor_height_m` = `LuxProtocol.SENSOR_HEIGHT_M` (1,0), `module_id` = địa chỉ BLE của máy đã ghép; `lux_value` = **trung vị chính xác** của `samples` (server kiểm ±1%). Server chấm số đo khi Quản lý xác nhận ảnh (so với baseline của chính cột, từ đêm thứ 4) — mobile **vẫn không** hiện mờ/đạt (R3). Ngưỡng "ổn định" của thẻ đo = ngưỡng server: `(max − min) / trung vị ≤ 5%` (`LuxProtocol.MAX_WINDOW_SPREAD`), **bỏ sàn 0,5 lux** cũ — nếu không server sẽ từ chối vĩnh viễn số app đã nhận. Lỗi mới: `400 LUX_PROTOCOL_MISMATCH` / `LUX_WINDOW_UNSTABLE` / `LUX_SAMPLES_MISMATCH` → `Failed`; `409 POLE_PHOTO_ALREADY_DECIDED` (ảnh đã được Quản lý duyệt trước khi lux tới) → gửi lại không kèm `photo_id` như các lỗi liên kết ảnh. Room v27→v28 (`samplesJson`, `moduleId`, `sensorHeightM` ở `local_lux_reading`, `MIGRATION_27_28`); số đo cũ không có các cột này gửi như số đo đối chứng. **Chưa làm:** thẻ thử tấm lọc ND (`optical_filter_transmittance`), migration test 27→28 trên máy thật.

### Đăng nhập bằng Google (ACCT-4) — THÊM 2026-11-16, đã code xong ở mobile

Xác nhận trực tiếp bằng cách đọc source `luxmap_backend`: `AuthController.cs` (`POST /api/v1/auth/google`), `AuthService.GoogleSignInAsync`, `GoogleIdTokenVerifier.cs`, `GoogleLoginOptions.cs` — BE đã có thật và đang chạy, không phải suy đoán. Việc mobile làm theo (thêm nút, gọi endpoint) được xác nhận trực tiếp với người dùng (chủ dự án) trong buổi làm việc 2026-11-16, sau khi phát hiện code đã có sẵn trên 1 nhánh riêng (`feat/auth-google-sign-in`, code ngày 2026-11-11, chưa merge) nhưng chưa có ghi chú ở đây.

- `POST /api/v1/auth/google` — body `{"id_token": "..."}`, trả về **cùng 4 field** như `/auth/login` (`access_token`, `refresh_token`, `token_type`, `expires_in`). **Chỉ đăng nhập vào tài khoản đã tồn tại do System Admin tạo trước** (comment BE: "Đăng nhập bằng Google vào tài khoản đã được Quản trị tạo") — không có đăng ký mới qua Google. BE tra theo `email` của Google identity, yêu cầu tài khoản đó đã có `PasswordHash` (tức được tạo qua flow thường) và chưa bị khoá; Google email phải `email_verified = true`.
- BE verify ID token qua `Google.Apis.Auth` (`GoogleJsonWebSignature.ValidateAsync`), audience phải khớp 1 trong các Client ID cấu hình server-side (`GOOGLE_CLIENT_IDS`).
- **Mọi lý do Google đăng nhập thất bại (sai token, email không khớp tài khoản nào, tài khoản chưa có password, tính năng bị tắt server-side) đều trả về cùng 1 mã `INVALID_CREDENTIALS`** như sai mật khẩu thường — không phân biệt được qua response. Chỉ `ACCOUNT_LOCKED` là tách riêng.
- Mobile: `GoogleSignInHelper` (dùng `androidx.credentials` + `com.google.android.libraries.identity.googleid`, **thư viện mới**, đã thêm vào "Ngăn xếp công nghệ" — xem mục đó) lấy ID token qua Credential Manager; `AuthRepository.loginWithGoogle(idToken, rememberMe, displayName)` gọi `AuthApi.google()`; `LoginFailureReason.GoogleSignInFailed` tách riêng khỏi `InvalidCredentials` để hiện đúng thông báo cho người dùng (không có ô username/password để đổ lỗi cho luồng Google) — vì BE không phân biệt được 2 lỗi đó. Nút "Đăng nhập bằng Google" nằm dưới form thường trên `LoginScreen`, ngăn bằng divider "Hoặc".
- Web Client ID (OAuth "Web application", không phải Android) để trong `BuildConfig.GOOGLE_WEB_CLIENT_ID` — public by nature (ship trong mọi APK gọi Google Sign-In) nên để làm giá trị mặc định ngay trong `app/build.gradle.kts`, không phải secret riêng trong `local.properties`. Phải khớp đúng 1 giá trị trong `GOOGLE_CLIENT_IDS` phía BE.
- Test: `RealAuthRepositoryTest.kt` (case đăng nhập Google thành công/thất bại/lỗi mạng), `LoginViewModelTest.kt` (case bấm nút, huỷ account-picker, lỗi). **Đã kiểm tra tay trên máy thật (SM-A075F), thành công** — ngoài Web Client ID có sẵn, còn cần tạo thêm 1 OAuth Client loại **Android** trong cùng project Google Cloud Console (package `com.luxmap.local`, SHA-1 của debug keystore máy build) mới lấy được ID token qua Credential Manager; tài khoản test tạo qua `POST /admin/users` (Web GIS), đặt mật khẩu qua mail mời, rồi đăng nhập Google khớp đúng email đó mới thành công.

### Tab Lịch sử + bỏ tab Khảo sát — THÊM 2026-11-27, đã code ở mobile

Quyết định trực tiếp với chủ dự án (2026-11-27). Thanh dưới còn 4 tab: **Hôm nay · Lịch sử · Bản đồ · Cá nhân**.

- **Hôm nay** hiện cả lệnh `survey` và `pole_gps_update`. Tab con Khảo sát/GPS cột chỉ hiện khi có lệnh loại đó; Sửa chữa/Kiểm tra luôn hiện.
- **Hub Khảo sát đã xoá** (`feature/survey/ui/hub/`, `SurveyRepository`, 2 bảng cache `local_assigned_survey_route`/`local_assigned_pole_gps_order`, `MIGRATION_31_32` DROP TABLE). Lệnh khảo sát đi qua F09: Bắt đầu/Yêu cầu hủy dùng chung `allowed_actions`, sau khi Bắt đầu hoặc bấm "Mở danh sách cột" thì vào `PolePhotoList`; "Hoàn tất" ở danh sách cột quay về F09. Thông báo về mọi loại lệnh đều mở F09. Việc mở lại báo cáo bị Quản lý trả về: `PoleListViewModel` (survey) và F09 (`pole_gps_update`) đã tự làm, không còn phụ thuộc Hub.
- **Lịch sử** (`feature/history/`): `GET /work-orders?assigned_to=me&wo_status=done,verified,cancelled` (endpoint có sẵn, không cần BE mới), trang đầu 200 lệnh, giữ thứ tự server (`created_at` giảm dần). Cache Room `local_history_item` lọc theo `ownerUserId`, lưu `fetchedAt` để banner offline ghi đúng giờ tải; xoá khi đăng nhập/đăng xuất (`RealAuthRepository`). Lịch sử gọi `sort=-closed_at`, rồi đưa lệnh `done` (chưa có `closed_at`) lên đầu theo `completed_at` mới nhất; ngày hiển thị là `closed_at` (đã duyệt/hủy) hoặc `completed_at` (đã nộp), lùi về `updated_at` cho dòng cache cũ.
- **BE đã làm (2026-11-27, WO-SORT, PR #318, SELF-SIGNED):** item của `GET /work-orders` có `completed_at`/`closed_at` (nullable), tham số `sort` một khoá (`created_at|updated_at|completed_at|closed_at`, tiền tố `-` giảm dần, null xếp cuối). Mobile đã dùng (Room v34, `MIGRATION_33_34`).
- F09 với lệnh `verified`/`cancelled`: bỏ badge/khung "Đã nộp, chờ duyệt" của completion cục bộ và cảnh báo quá hạn (`WorkOrderDetail.isClosed()`).

### Xin hủy lệnh (WO-CANCEL-REQUEST) — THÊM 2026-10-27, code được ngay, đã viết ở mobile

Xác nhận trực tiếp bằng cách đọc source `luxmap_backend` (nhánh `dev`, tới commit `aa0d0ba`): migration `20261026100552_AddWorkOrderCancellationRequests`, `WorkOrdersController.cs` (`request-cancellation`/`reject-cancellation`), `WorkOrderRules.cs`, `WorkOrderService.cs`, test `WorkOrderCancellationTests.cs`, và `docs/api-contract-v1.1.md` mục "Xin hủy / từ chối nhận lịch — v1.19, WO-CANCEL-REQUEST". Backend tự ghi là **SELF-SIGNED** (chốt D-1…D-14 ngày 26/10/2026), nền tạm tới FW kế tiếp, WP5/WP6 còn phải xác nhận — coi là nguồn đúng để code theo, cập nhật lại mục này nếu BE đổi sau vòng duyệt đó.

- `POST /api/v1/work-orders/{id}/request-cancellation` — policy `ExecuteWorkOrders`, body `{"reason": "..."}` (bắt buộc, trim 1–500 ký tự UTF-16, thiếu/trắng/quá dài → 400). Chỉ kỹ sư đang được giao, và chỉ khi `wo_status == assigned` (**không áp dụng khi đã `in_progress`**) — đúng điều kiện `WorkOrderRules.Allows("request_cancellation", ...)`, không tự suy diễn từ `wo_status` phía mobile.
- `POST /api/v1/work-orders/{id}/reject-cancellation` — policy `ManageWorkOrders`, **ngoài phạm vi mobile** (Manager dùng Web GIS riêng).
- 5 field mới trên `WorkOrderDetail` (và item/agenda/bundle, mobile chỉ cần detail): `cancellation_request_status` (`none|pending|rejected`), `cancellation_request_note`, `cancellation_requested_at`, `cancellation_reject_note`, `cancellation_rejected_at`.
- `allowed_actions` tự thêm `request_cancellation` khi đủ điều kiện (none/rejected → pending) — mobile dùng đúng cơ chế `"request_cancellation" in allowed_actions` đã có sẵn cho `start`/`complete`, không gọi thêm endpoint riêng để biết khi nào hiện nút.
- **Phía server, `pending` không khóa `start`/`complete`** — `allowed_actions` vẫn giữ `start` khi đang chờ duyệt hủy (`WorkOrderRules.Allows("start", ...)` chỉ xét `wo_status`, không xét `cancellation`). **ĐẢO LẠI phía mobile, 2026-11-11** (quyết định trực tiếp của chủ dự án, có cân nhắc rủi ro): ban đầu mobile cũng không khóa CTA chính khi có yêu cầu hủy đang chờ (chỉ hiện banner riêng) — nay mobile tự khóa thêm nút "Bắt đầu" ở client khi `cancellationRequestStatus == "pending"`, kèm hint "Đang chờ duyệt yêu cầu hủy" ngay dưới nút. Đây là khóa riêng phía client, **không phải do server chặn** — nếu Quản lý từ chối yêu cầu hủy sau khi đã quá hạn `scheduled_date`, lệnh sẽ kẹt thật (không còn cách nào bắt đầu được nữa), rủi ro này đã được xác nhận chấp nhận.
- Đã code xong ở mobile (nhánh `style/redesign-workorder-detail-inspection-v2`, F09 Lệnh kiểm tra): nút "Yêu cầu hủy" (chỉ hiện khi `request_cancellation` có trong `allowed_actions`) → bottom sheet chọn 1 trong 4 lý do có sẵn + ghi chú tuỳ chọn, gộp thành 1 chuỗi gửi lên field `reason` duy nhất (BE không có field ghi chú riêng). Banner `pending`/`rejected` hiện trong vùng ghi chú của màn chi tiết. Field/model: `WorkOrderDetailDto`/`WorkOrderDetail` (`cancellationRequestStatus`/`cancellationRequestNote`/`cancellationRejectNote` — 2 field timestamp không đưa vào domain model vì UI chưa cần hiển thị), `RequestCancellationRequestDto`, `WorkOrdersApi.requestCancellation`, `WorkOrderDetailRepository.requestCancellation` (Real, không có Fake — theo đúng pattern đã có của repo này). **Chưa áp dụng cho F10 Lệnh sửa chữa** (cùng điều kiện `wo_status == assigned` nên về lý sẽ dùng được, nhưng chưa code/test UI riêng cho màn đó) và **chưa kiểm tra tay trên máy thật**.

### Ảnh bắt buộc theo từng cột trong lịch `pole_gps_update` — THÊM 2026-11-25, đã code xong ở mobile

Xác nhận trực tiếp bằng cách đọc source `luxmap_backend` (`dev`, PR #299 đã merge 2026-11-25, `WorkOrderService.cs`, `docs/POLE-GPS-PHOTOS-FE.md`, drift POLE-GPS-PHOTO PGP-1…PGP-8) và chạy thật trên máy SM-A075F/R83L50A7C9H với BE local. BE tự ghi là **SELF-SIGNED**, migration `AddWorkOrderPhotoCopy` và deploy VPS còn chờ; BE chưa báo WP5/WP6.

- `POST /work-orders/{id}/complete` của lịch GPS: mỗi `pole_id` trong `pole_locations` phải có ít nhất 1 ảnh bằng chứng `observation` gắn đúng `pole_id` (ảnh từ vòng trước `return` vẫn tính). Thiếu → `409 POLE_EVIDENCE_REQUIRED`, `details {work_order_id, pole_ids}`; qua `/sync/push` lỗi nằm trong `conflicts[].reason`, **không có `details`**.
- Phải đẩy hết ảnh lên trước khi gửi `complete` — sau `complete` phiếu là `done` và `evidence` trả `409 WORK_ORDER_NOT_IN_PROGRESS`.
- GPS là trường riêng (`pole_locations`, `lat`/`lng` của ảnh), BE không đọc EXIF. Không gọi `/pole-photos` trong lịch này (Q-3 BE còn mở).
- Sau `verify`, job nền của BE (mỗi phút) chép ảnh sang gallery ảnh tài sản của cột (tối đa 10 ảnh/cột) — mobile không làm gì.
- **Mobile:** nút "Nộp báo cáo" khoá tới khi mọi cột đã đo đều có ảnh; `CompleteWorkOrderSyncHandler` chờ mọi op ảnh `done` (`WaitingForDependency`), ảnh lỗi thì `Failed`; gặp `POLE_EVIDENCE_REQUIRED` thì `PoleGpsRepository.markPhotosMissingOnServer(poleIds)` mở lại báo cáo. Test: `CompleteWorkOrderSyncHandlerTest`, `GpsCompletionSnapshotTest`.
- **Đã kiểm tra trên máy thật (2026-11-25, BE local):** online: 2 × `POST /evidence` 201 rồi `POST /complete` 200. Offline (WO-0076): đo 2 cột + nộp khi mất mạng → "Đã nộp — Chờ đồng bộ", không có request nào; bật lại mạng → 2 × `evidence` 201 rồi `complete` 200, đúng thứ tự. **Chưa kiểm tra:** nhánh 409 thật, `verify` + job chép ảnh (máy cách tuyến ~35 km nên `verify` sẽ `POLE_OFF_SEGMENT`).

### Cửa sổ hành động Bắt đầu/Yêu cầu hủy theo lịch + hoàn thành trong ngày — THÊM 2026-10-29, ĐẢO LẠI 2026-11-09, chỉ ở mobile, CHƯA xác nhận với backend

Quyết định sản phẩm trao đổi trực tiếp với chủ dự án (2026-10-29, đảo lại 2026-11-09). **Khác các mục khác ở trên, mục này KHÔNG có xác nhận bằng cách đọc source `luxmap_backend`** — chỉ là quyết định UI/UX cho mobile, chưa chạm tới `WorkOrderRules.cs` phía server. Coi là **rào chắn UI-only**, chưa phải chặn thật ở tầng API.

**Cập nhật 2026-11-09 — đảo ngược hoàn toàn 2 quyết định "không chặn trễ" ban đầu**, theo yêu cầu trực tiếp của chủ dự án: để task quá hạn mà không làm gì cũng vô nghĩa, nên giờ quá hạn phải **khoá thật**, không chỉ cảnh báo.

- Nút "Bắt đầu" và "Yêu cầu hủy" ở F09 (Chi tiết lệnh) chỉ hiện/bấm được khi còn tối đa 1 ngày nữa là tới `scheduled_date` của `WorkOrder` đó — mở từ `scheduled_date − 1 ngày`, giữ mở tới hết `scheduled_date`, rồi **đóng lại** (khác bản ghi cũ "không đóng lại giữa đường" — giờ có đóng). Trước mốc mở hoặc sau khi đã quá hạn, 2 nút bị khoá **dù `allowed_actions` của server có `start`/`request_cancellation` hay không** — đây là lớp khoá thêm ở client, không thay thế điều kiện gốc của server (`wo_status`, người được giao...). Hint hiển thị phân biệt rõ 2 lý do khoá: "Mở vào ..." (quá sớm) vs "Đã quá hạn bắt đầu · liên hệ Kỹ sư bảo trì để được giao lại" (quá hạn).
- **Nộp báo cáo chỉ mở đúng ngày `scheduled_date` (THÊM 2026-11-17, quyết định trực tiếp của chủ dự án)** — VD lịch ngày 18: ngày 17 đã được Bắt đầu/Yêu cầu hủy và nhập form (ghi chú, ảnh, vật tư), nhưng nút "Hoàn thành lệnh"/"Nộp báo cáo kiểm tra"/"Nộp báo cáo" bị khoá kèm hint "Nộp được từ 18/..." tới đúng ngày 18. Code: `WorkOrderScheduleWindow.submitOpensOn` + `scheduleSubmitLock()` trong `WorkOrderCta.kt`. Cũng là rào chắn UI-only như phần còn lại của mục này.
- Nếu `scheduled_date` là `null` (Manager chưa đặt lịch): không áp dụng luật này, giữ hành vi cũ (theo đúng `allowed_actions`).
- Field Engineer được kỳ vọng `complete` lệnh trong đúng ngày `scheduled_date`. **Giờ CÓ chặn nộp trễ** (đảo lại so với bản ghi cũ) — qua ngày mà chưa `complete`: khoá toàn bộ form nhập (ghi chú, ảnh bằng chứng, vật tư đã thay, chọn kết quả từng sự cố) **và** nút "Hoàn thành lệnh"/"Nộp báo cáo kiểm tra", kèm banner giải thích lý do khoá + hướng xử lý (liên hệ Kỹ sư bảo trì). Nút "Chụp lại" trong dialog xem ảnh bằng chứng cũng ẩn khi khoá (chỉ cho xem, không cho sửa).
- Tương tự cho lệnh còn `assigned` (chưa từng bấm Bắt đầu) mà đã qua `scheduled_date`: khoá nút "Bắt đầu" (đảo lại so với bản ghi cũ "vẫn hoạt động bình thường"), banner đổi nội dung giải thích lý do khoá.
- Việc phát hiện "bỏ lỡ" (quá `scheduled_date` mà vẫn `assigned`, chưa từng bấm Bắt đầu) không xử lý ở mobile (không tự huỷ, không thêm trạng thái/enum mới). **Cập nhật 2026-10-30 — xác nhận trực tiếp bằng cách đọc source `luxmap_backend` (`WorkOrderOverdueJob.cs`, BE-26):** backend có thật 1 job nền chạy mỗi 15 phút, gửi `NotificationType.WorkOrderOverdue` (xem mục F14) cho cả kỹ sư được giao lẫn Manager phụ trách xã — nhưng job này xét theo **`due_date`** (`DueDate != null && DueDate < đêm cắt mốc`, trạng thái còn `Open/Assigned/InProgress`), **không phải `scheduled_date`**. `WorkOrder` có 2 field `DateOnly?` tách biệt (`DueDate` và `ScheduledDate`). Nghĩa là: 1 WO có `scheduled_date` đã qua nhưng `due_date` còn hạn (hoặc null) sẽ **không** sinh notification `work_order_overdue` nào — ý định ban đầu "tận dụng lại notification có sẵn để báo Manager khi bỏ lỡ theo lịch" **không hoạt động được** như mong đợi. Banner "Quá hạn bắt đầu" ở mobile vẫn đúng về mặt hiển thị UI (tự so `scheduled_date` phía client), chỉ là **không có thông báo nào tới Manager** cho đúng trường hợp đó — nếu muốn Manager được báo khi bỏ lỡ theo lịch, cần backend thêm job/notification type riêng dựa trên `scheduled_date`, chưa có hiện nay.
- So sánh theo ngày (`yyyy-MM-dd`) bằng giờ thiết bị (`LocalDate.now()`) để biết "hôm nay" — chấp nhận rủi ro lệch ngày ở mức UI-only (chỉ ẩn/hiện nút sớm/muộn, không mất dữ liệu, không tự huỷ gì), cùng loại rủi ro đã né khi bỏ "quá hạn N ngày" tính theo giờ máy ở `WorkOrderSummaryCard` (commit `46e25b1`, nhánh `style/redesign-workorder-detail-repair-v2`).
- **Chưa chặn thật ở API**: nếu sau này cần chặn ở tầng server (không chỉ ẩn UI), phải xác nhận với backend để thêm điều kiện ngày vào `WorkOrderRules.Allows("start"/"request_cancellation", ...)` và trả đúng qua `allowed_actions`. Tới lúc đó, bypass UI (gọi API trực tiếp) vẫn thành công vì server chưa biết luật này.

**Đã code xong phần đảo lại (2026-11-09), nhánh `feat/workorder-schedule-overdue-lock`, PR #135 vào `dev`.** `workOrderScheduleWindow` (`WorkOrderScheduleWindow.kt`, trước là `WorkOrderLifecycle.kt`) đóng cửa sổ lại sau `scheduled_date`; `WorkOrderDetailScreen.kt` khoá CTA Bắt đầu/Hoàn thành + form nhập theo đúng mô tả trên. **Chưa có test tự động phủ riêng hành vi khoá** (`workOrderCtaState`/`RepairCompletionSection`/`InspectionCompletionSection` là hàm `private`, không unit-test trực tiếp được; `connectedDebugAndroidTest` hiện fail toàn bộ trên emulator local do lỗi môi trường không liên quan — xem reference lỗi `InputManager.getInstance`) — logic gốc `workOrderScheduleWindow` có test đầy đủ ở `WorkOrderScheduleWindowTest.kt`, còn lại dựa vào kiểm tra tay trên máy thật, **chưa làm**.

### Luồng nhập tài sản mới (vùng chưa có bản đồ số) — THÊM 2026-11-05, ĐÃ XOÁ KHỎI MOBILE 2026-11-16

**Cập nhật 2026-11-16 — CODE CỦA LUỒNG NÀY (`feature/assetimport/`, PR #125) ĐÃ BỊ XOÁ HẲN khỏi mobile, theo quyết định trực tiếp của người dùng.** Lý do: xác nhận lại đây và `pole_gps_update` (QR dán cột + thu thập GPS trên `WorkOrder` đã giao, xem mục riêng phía dưới) cùng phục vụ một nghiệp vụ thật, `pole_gps_update` đã code đúng và đầy đủ hơn (có Manager duyệt qua `WorkOrder.verify`, có BE thật) — giữ cả hai là trùng lặp không cần thiết. Đã xoá: toàn bộ `feature/assetimport/` (data/domain/ui, không có test/androidTest), card "Chưa có bản đồ số" + nút "Bắt đầu chuyến" ở `SurveyHubScreen`, `assignedImportAreas`/`onStartImportTrip`/`startedImportTrip` ở `SurveyHubViewModel`/`SurveyHubUiState`, 4 route `AssetImport*` ở `Routes.kt`/`NavGraph.kt`, binding `AssetImportRepository` ở `RepositoryModule`, `AssetImportDao`/2 entity ở `AppDatabase` (v20→v21, `fallbackToDestructiveMigration()` đã có từ trước nên không cần migration riêng). **Nghiệp vụ "chụp cột mới, GPS tự động, Manager duyệt từng cột trước khi lên bản đồ" mô tả dưới đây vẫn đúng về ý tưởng** — chỉ là đã nhận ra nó trùng với `pole_gps_update`, không phải nghiệp vụ bị huỷ hoàn toàn. Nếu sau này thực sự cần 1 luồng số hoá vùng hoàn toàn chưa có dữ liệu (khác hẳn việc đo lại GPS cho cột đã biết), coi mục này là điểm bắt đầu tham khảo, không phải code cũ đã xoá.

Quyết định sản phẩm trao đổi trực tiếp với chủ dự án (2026-11-05). Khác hẳn 2 luồng khảo sát đã có ở trên (quay video liên tục toàn tuyến F03-F06, và chụp ảnh/video ngắn từng cột trên `WorkOrder` đã giao `task_kind=survey`) — luồng này dùng để **tạo tài sản (cột đèn) hoàn toàn mới** cho khu vực chưa có dữ liệu trên bản đồ, không gắn với `WorkOrder` nào. **Chưa có tên bảng/field/endpoint thật ở backend** — chỉ là nghiệp vụ đã chốt với chủ dự án, không tự đoán field/endpoint khi code, xác nhận với WP2/WP5 trước khi đụng tới backend thật.

1. **Bắt đầu chuyến** — FE mở app, thấy khu vực được giao đánh dấu "Chưa có bản đồ số", bấm "Bắt đầu chuyến". Không cần đứng tại giao lộ hay điểm cố định nào.
2. **Chụp tại điểm** — tại mỗi cột đèn gặp trên tuyến: chụp 1 ảnh, GPS tự động gán theo vị trí máy; FE kéo tay chỉnh ghim trên bản đồ vệ tinh thu nhỏ nếu bị lệch. Vì khu vực chưa có dữ liệu, hệ thống luôn tự ghi "chưa xác định tủ điện/mạch" (không bắt FE tự đoán), và bắt buộc 1 dòng ghi chú nhận diện tự do (vd: "cột bê tông, gần quán tạp hoá") để Manager dễ nhận ra cột. Bấm "Lưu & tiếp tục chụp" sang cột kế tiếp, không rời màn hình.
3. **Danh sách chuyến** — xem lại danh sách các cột đã ghi nhận trong chuyến đang chạy; "Chụp tiếp" (quay lại Màn 2) hoặc "Kết thúc chuyến" bất cứ lúc nào.
4. **Xem lại & Gửi** — hệ thống tự động cảnh báo nếu có cột cách nhau **dưới 8m** (nghi trùng toạ độ) hoặc thiếu toạ độ. 8m lấy đúng theo `GpsAccuracyGate.PROPOSED_THRESHOLD_METERS` đã dùng cho ngưỡng "GPS đủ tin cậy" ở luồng khảo sát F03/F04 — 2 điểm đo gần hơn sai số GPS được công nhận thì không thể khẳng định là 2 vị trí khác nhau. Đối chiếu bảng khoảng cách cột đèn tiêu chuẩn theo loại đường (cao tốc 30-36m, quốc lộ 25-30m, tỉnh lộ 20-25m, đường nông thôn trục chính 15-20m, ngõ hẻm/đường nông thôn xóm 10-15m — domain LuxMap rơi vào 2 loại cuối), 8m còn margin so với mức tối thiểu thật (10m ở ngõ hẻm) nên hạn chế báo nhầm. Cảnh báo **không chặn cứng**, chỉ nhắc kiểm tra lại — xem được dạng danh sách hoặc bản đồ, vẫn bấm "Nộp chuyến đi" được dù còn cảnh báo.
5. **Chờ duyệt** — xác nhận gửi thành công, trạng thái chuyển "Chờ duyệt". Manager sẽ duyệt/từ chối riêng từng cột (không duyệt gộp cả chuyến) trên Web GIS dựa vào ảnh làm bằng chứng. Khi 1 cột được duyệt, dữ liệu cột đó mới chính thức được import vào hệ thống bản đồ; đoạn đường (segment) chứa các cột này được xác định dựa trên cột đầu và cột cuối của chuyến.

**Đã code xong (2026-11-05/06, PR #125):** `feature/assetimport/` — 5 màn hình nối thông từ Hub Khảo sát (card "Chưa có bản đồ số" + "Bắt đầu chuyến") → Chụp tại điểm → Danh sách chuyến → Xem lại & Gửi → Chờ duyệt. Data layer local-only (Room `local_asset_import_trip`/`local_asset_import_pole`, DB v13), 1 `FakeAssetImportRepository` duy nhất (chưa có backend thật, không tách Fake/Real). `CalculateTripWarningsUseCase` tính cảnh báo trùng/thiếu toạ độ. **Cố ý đơn giản hoá, không phải thiếu sót:** bản đồ vệ tinh kéo ghim (cả ở Chụp tại điểm lẫn tab Bản đồ của Xem lại & Gửi) còn là placeholder tĩnh "sẽ thêm sau"; nút trên Hub luôn hiện "Bắt đầu chuyến" (chưa đổi "Tiếp tục chuyến" dù logic resume đã đúng); trạng thái duyệt từng cột ở Chờ duyệt luôn tĩnh "Chờ duyệt" (chưa có API poll). Chưa kiểm tra tay trên máy thật.

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
- **Credential Manager** (`androidx.credentials` + `com.google.android.libraries.identity.googleid`) — đăng nhập bằng Google (ACCT-4, xem mục riêng ở trên), thêm 2026-11-16
- ktlint cho format code
- Testing: JUnit + MockK (unit), MockK-Android (`io.mockk:mockk-android`, chỉ dùng trong `androidTest` — bản `mockk` thường không mock được final class/Camera2 type trên runtime Android thật, đã xác nhận với chủ dự án 2026-09-28 khi làm FM-08 Task 3 `ExposureLockController`), `androidx.test:core` (hạ tầng nền tảng AndroidX Test cho `ApplicationProvider`/Context trong `androidTest`, cùng họ với `androidx.test.ext:junit`/`espresso-core` đã có sẵn — đã xác nhận với chủ dự án 2026-09-28 khi làm FM-08 Task 9 Room DAO test, cần cho mọi instrumented test dùng Room in-memory database), Turbine (Flow/StateFlow — dùng được cả ở `test` lẫn `androidTest`), MockWebServer (API), Room Testing (migration/transaction/queue), Compose UI Test (luồng UI chính)

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
        BottomNavBar.kt        # 4 tab: Việc hôm nay · Lịch sử · Bản đồ · Cá nhân
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
      PhotoGpsExif.kt          # ExifInterface: ghi/đọc GPS vào ảnh chụp cột (pole-gps-update)
      # ExposureLockController/CameraSelector/FrameTimestampLogger/SegmentedVideoRecorder/
      # SegmentRotationPolicy đã bị xoá cùng luồng quay video liên tục (xem mục 2026-11-15)
    location/
      LocationTracker.kt       # Fused Location
      # SurveyTrackRecorder (track GPS+heading liên tục) đã bị xoá cùng luồng trên
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
    RepositoryModule.kt        # bind Real repository ở đây, 1 chỗ duy nhất
    SyncModule.kt              # cấu hình WorkManager

  navigation/
    NavGraph.kt                # NavHost, 4 tab + các màn con
    Routes.kt

  feature/
    auth/                     # F01 Đăng nhập
      data/
        AuthRepository.kt
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

    history/                  # Tab Lịch sử: lệnh done/verified/cancelled, lọc theo loại (2026-11-27)
      data/                    # RealHistoryRepository + cache Room local_history_item
      ui/

    survey/                   # Chụp ảnh/video từng cột (task_kind=survey) - Hub/tab Khảo sát ĐÃ XOÁ 2026-11-27
      data/                    # PolePhoto/PoleList/LuxReading repository
      ui/
        polephoto/             # Chụp ảnh/video ngắn từng cột đèn (quyết định 2026-10-15, còn giữ)

    workorder/                 # F08–F10 — lệnh sửa chữa
      data/
        WorkOrderRepository.kt
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

## Design tokens — Design System vNext "Civic Utility" (hiện hành từ 2026-11-16)

**Nguồn đúng khi code UI:** `docs/design-system/vNext/` (README, `COLOR_SYSTEM.md`, `TYPOGRAPHY.md`, `SHAPE_SURFACE.md`, `COMPONENT_SPECIFICATIONS.md`, `GIS_MAP_GUIDELINES.md`, `DARK_NIGHT_FIELD.md`, `ACCESSIBILITY.md`, `COMPOSE_IMPLEMENTATION.md`) + code thật trong `core/theme/`. Hướng đã duyệt: **Light-first + "Night Field" (theme tối có token riêng) + Material 3 tiết chế** — Navy700 `#1F3864` là màu thương hiệu, Be Vietnam Pro, WCAG AA tối thiểu/AAA cho nội dung chính và CTA, **không gradient**, **không M3 Expressive**, **không dynamic color**. Duyệt bổ sung 2026-11-16: T-01 (primary Night Field = Navy300), T-02 (disabled theo code), T-03 (badge ưu tiên dùng cặp nền/chữ), T-06 (radius). Các điểm còn chờ (T-04, T-05, T-07…T-16) liệt kê ở `docs/design-system/vNext/README.md` mục 5 — không tự quyết.

**Lịch sử (chỉ để tham khảo):** v2.0 → thử "Night Ledger" v2.1 (2026-10-11) → rollback về v2.0 (2026-10-14, PR #65–67) → vNext (2026-11-16, nhánh `style/design-system-vnext-tokens`). `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` và `docs/LuxMap_Mobile_Design_System_v2.1.md` **không còn là nguồn token**. **Đã dọn 2026-11-25:** `BrandHeroGradient`, `Navy900`, 7 primitive không còn nơi dùng (`Navy50`, `Amber500`, `Rose600`, `Danger600`, `Gray700`, `DarkText`, `DarkMuted`) và `LuxTabTopBar` đã xoá khỏi code (25 primitive v2.1 `Paper/Ink/Night*` không còn trong `Color.kt`). Đích sau đăng nhập là `Routes.Home` (đảo lại 2026-11-14), không liên quan token.

**Cập nhật 2026-11-25 — Brand visual refresh "Cột đèn":** thêm `LuxTheme.brand` (hero navy, `lumen` `#FFC44D`, hình hoạ tiết). Lumen **chỉ là trang trí thương hiệu**, không làm chữ trên nền sáng và không thay màu trạng thái (quá hạn/cảnh báo giữ `LuxTheme.colors.*`). Tab gốc dùng `LuxTabHero`, header Đăng nhập dùng `LuxHero`, màn con dùng `LuxChildTopBar` (navy), trạng thái rỗng/lỗi có `LuxLampIllustration`. Vẫn không gradient. Bản đồ chưa đổi vì T-09/T-10 chưa duyệt. Spec/plan: `docs/superpowers/specs|plans/2026-11-25-brand-visual-refresh*`.

### Quy tắc dùng token trong code

- `feature/` **chỉ** đọc `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`, `MaterialTheme.shapes.*`, `LuxTheme.colors.*` (success/warning/info/disabled/focus/due/overlay) và `LuxTheme.status.*` / các hàm `@Composable` `*.badgeColors()` — **không** import primitive (`Navy700`, `Gray500`, `Danger600`…) hay viết `Color(0x…)` trong màn hình. Code cũ còn vi phạm sẽ được sửa dần theo từng màn khi redesign.
- `colorScheme.outline` (≥3:1) **chỉ** cho ranh giới control (text field, nút viền, checkbox); viền card/divider trang trí dùng `colorScheme.outlineVariant`.
- Mỗi miền trạng thái có bộ token riêng (`LuxTheme.status.asset/workOrder/priority/sync/iot/kind`), kể cả khi trùng giá trị hex — không mượn màu chéo. Badge luôn có **icon + nhãn**, không chỉ màu.
- `badgeColors()` là hàm `@Composable` tự theo theme (Light/Night Field) — **không** truyền `isDark` và không dùng `isSystemInDarkTheme()` để chọn màu (sai trong màn `forceDark`).

### Màu semantic (M3 `ColorScheme`, `core/theme/Theme.kt`)

| Role | Light | Night Field |
|---|---:|---:|
| `primary` / `onPrimary` | `#1F3864` / `#FFFFFF` | `#9DB7E0` / `#0E1E38` |
| `primaryContainer` / `onPrimaryContainer` | `#D9E2EF` / `#172B4D` | `#21375C` / `#D9E2EF` |
| `secondary` | `#475569` | `#B3B3B3` |
| `tertiary` | `#2D6388` | `#8FB8DA` |
| `background` | `#F8FAFC` | `#0D0D0D` |
| `surface` | `#FFFFFF` | `#121212` |
| `surfaceContainer` (Low → Highest) | `#F8FAFC` · `#F1F5F9` · `#E8EDF3` · `#E2E8F0` | `#121212` · `#1C1C1C` · `#262626` · `#2E2E2E` |
| `onSurface` / `onSurfaceVariant` | `#0F172A` / `#475569` | `#E6E6E6` / `#B3B3B3` |
| `outline` / `outlineVariant` | `#64748B` / `#E2E8F0` | `#8C8C8C` / `#2A2A2A` |
| `error` / `errorContainer` | `#B91C1C` / `#FEE2E2` | `#FF9A9A` / `#4A1717` |
| `surfaceTint` | Transparent | Transparent |

Mở rộng (`LuxTheme.colors`, `core/theme/ExtendedColors.kt`): success `#047857`/`#6FD3A8`, warning `#B45309`/`#F7C66D`, info `#0369A1`/`#7CC4F0`, disabled `#E2E8F0`+`#475569` / `#2A2A2A`+`#A0A0A0`, focusRing `#3E86C9`/`#9DB7E0`, `dueOverdue`/`dueToday`/`dueFuture`. Bảng đầy đủ + tỉ lệ tương phản: `docs/design-system/vNext/COLOR_SYSTEM.md`. Mọi cặp được test khoá ở `ColorSchemeContrastTest`/`ExtendedColorsTest`.

### Badge trạng thái tài sản (Light / Night Field) — giữ nguyên giá trị v2.0

| Trạng thái | Nền Light | Chữ Light | Nền Night Field | Chữ Night Field |
|---|---:|---:|---:|---:|
| Bình thường (`normal`) | `#D1FAE5` | `#065F46` | `#123D34` | `#8CE3D1` |
| Đèn mờ (`dim`) | `#FEF3C7` | `#92400E` | `#4A3310` | `#F7C66D` |
| Hỏng/Tắt (`out`) | `#FEE2E2` | `#991B1B` | `#4A1717` | `#FF9A9A` |
| Chưa xác định | `#EFEFEF` | `#555555` | `#303030` | `#D0D0D0` |

### Badge trạng thái đồng bộ (sync_queue.status) — không gộp `failed` và `conflict`

| Enum | Nhãn UI | Light (nền / chữ) | Night Field (nền / chữ) |
|---|---|---|---|
| `queued` + offline | Chờ mạng | `#EFEFEF` / `#555555` | `#2E2E2E` / `#CFCFCF` |
| `queued` + online | Chờ đồng bộ | `#E8EEF7` / `#1F3864` | `#1C2A44` / `#C3D3EE` |
| `syncing` | Đang đồng bộ | `#FFF3DC` / `#8A5A00` | `#3D2C0B` / `#F3C978` |
| `failed` | Đồng bộ lỗi | `#FBE4E4` / `#9B2C2C` | `#451818` / `#F8A5A5` |
| `conflict` | Xung đột | `#FDE8D0` / `#8A3B00` | `#45250B` / `#F6B27A` |
| `done` | Đã đồng bộ | `#E3F6F1` / `#1E6B5C` | `#12352E` / `#93DCCB` |

"Đã lưu trên thiết bị" (ghi Room thành công) khác với "Đã đồng bộ" (`done`, server đã xác nhận) — không dùng lẫn hai cụm này trong UI.

### Badge ưu tiên Work Order — cặp nền/chữ (T-03, thay bảng màu đơn của v2.0)

| Priority | Nhãn | Light (nền / chữ) | Night Field (nền / chữ) |
|---|---|---|---|
| `low` | Thấp | `#F1F5F9` / `#475569` | `#262626` / `#BDBDBD` |
| `normal` | Bình thường | `#E8EEF7` / `#1F3864` | `#1C2A44` / `#C3D3EE` |
| `high` | Cao | `#FEF3C7` / `#92400E` | `#4A3310` / `#F7C66D` |
| `urgent` | Khẩn | `#FEE2E2` / `#991B1B` | `#4A1717` / `#FF9A9A` |

Icon cho badge ưu tiên (T-03) chưa thêm vì chưa màn nào hiển thị badge này — khi thêm, tránh icon "vạch" kiểu `SignalCellularAlt*` (dễ đọc nhầm là sóng mạng). Trạng thái lệnh (`wo_status`), IoT (`online/offline/none`) và **loại lệnh (trung tính `#F1F5F9`/`#334155`, phân biệt bằng icon + nhãn)** — xem `COLOR_SYSTEM.md` mục 6.

### Typography, shape, kích thước

- **Typography:** đủ 15 slot M3 trong `core/theme/Type.kt`, tất cả Be Vietnam Pro, không slot nào < 14sp; nội dung và chữ hành động ≥ 16sp; line height ≥ 1.35× cho slot chữ; `LineHeightStyle.Trim.None` (không cắt dấu tiếng Việt). Vai trò: tiêu đề tab gốc `headlineMedium`, tiêu đề bar màn con `titleLarge` (H1 v2.0), tiêu đề section/card `titleMedium` (H2 v2.0), nội dung `bodyLarge`/`bodyMedium`, caption `bodySmall`, chữ nút `labelLarge`, badge/chip `labelMedium`, nhãn bottom nav `labelSmall`. **Không tạo `TextStyle` mới ngoài 15 slot** — 10 `*TextStyle` rời cũ (`BadgeTextStyle` 13sp…) sẽ được thay dần khi redesign từng màn. Be Vietnam Pro **không có `tnum`** (chữ số tỉ lệ) — số động đặt trong ô độ rộng cố định.
- **Shapes** (`core/theme/Shape.kt`, T-06): extraSmall 6 (badge) · small 8 (chip) · medium 12 (card, nút, text field) · large 16 (dialog) · extraLarge 24 (bottom sheet).
- Nút bấm và mọi vùng chạm tối thiểu **48×48dp** (không phải 44dp — phù hợp thao tác một tay hoặc đeo găng tay khi khảo sát). Lề màn hình 16dp.

### Dark Mode theo ngữ cảnh — không phải theo màn hình cố định

| Màn hình/ngữ cảnh | Chế độ mặc định |
|---|---|
| F03 Lập kế hoạch khảo sát đêm / F04 Capture Mode | Đã xoá khỏi mobile (mục 2026-11-15) — hàng này chỉ còn giá trị lịch sử |
| F09 khi vào chế độ điều hướng ban đêm | Chuyển Dark |
| F10 Nghiệm thu | Theo hệ thống, không ép Dark |
| Các màn còn lại | Theo hệ thống, mặc định Light |

## Kiến trúc & Design Pattern

- **MVVM** cho lớp UI: mỗi màn hình gồm 1 `@Composable` (chỉ vẽ UI, không chứa logic) + 1 `ViewModel` (giữ state, xử lý logic). State chảy xuống qua `StateFlow`, sự kiện chảy lên qua callback — Unidirectional Data Flow, không hoisting state ngược.
- **Repository Pattern** cho lớp Data, mở rộng cho offline-first: mỗi feature có 1 interface Repository và 1 cài đặt `Real...Repository` gọi API thật (gắn trong `RepositoryModule`). **Cập nhật 2026-11-17 — đã bỏ quy tắc "tối thiểu 2 cài đặt Fake + Real"**: backend đã có đủ API, 7 bản `Fake...Repository` cùng 5 file mock trong `assets/` không còn được gắn ở đâu nên đã xoá theo quyết định của chủ dự án. Test ViewModel tự `mockk` interface Repository, không cần bản Fake. Nếu sau này cần chạy app không có backend, thêm lại 1 bản Fake và đổi 1 dòng trong `RepositoryModule`. Repository là nơi duy nhất biết cả Room lẫn Retrofit: đọc/ghi Room trước, đẩy thay đổi cần đồng bộ vào `sync_queue`, không bao giờ gọi API trực tiếp từ UI/ViewModel. ViewModel chỉ phụ thuộc vào interface Repository.
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
- **Dependency Injection bằng Hilt**: inject Repository (bản Real), Room DAO và WorkManager dependencies vào ViewModel qua constructor, không khởi tạo trực tiếp trong Composable hay ViewModel.

## Nguyên tắc nghiệp vụ cốt lõi — bắt buộc, ảnh hưởng trực tiếp tới kiến trúc code

Danh sách đầy đủ ở mục C5 của đặc tả; các quy tắc dưới đây lặp lại xuyên suốt nhiều màn hình nên cần nắm trước khi code bất kỳ feature nào:

- **Offline-first tuyệt đối**: mọi màn hình thao tác chính (F03, F04, F05, F06, F10, F11) ghi Room trước, không chờ phản hồi mạng; đồng bộ là tiến trình nền độc lập qua `sync_queue` + WorkManager.
- **Không cho bắt đầu khảo sát nếu chưa khoá exposure thật** (F03 chặn cứng, không chỉ nhắc nhở) — ảnh auto-exposure không sửa lại được sau khi chụp.
- **`RepairEvidence` (F10) và `SurveyFrame` (F04) là hai luồng ảnh tách biệt hoàn toàn** (BE-11): không dùng chung API upload, không dùng chung bảng lưu trữ.
- **Không tự động ghi đè khi xung đột đồng bộ** (`conflict`, HTTP 409 dựa trên `version`/`updated_at`): giữ nguyên bản ghi local, đánh dấu xung đột, để người dùng hoặc Kỹ sư bảo trì xử lý qua Web.
- **Upload phải resume được** theo chunk, có checksum SHA-256 và idempotency key — không tải lại từ đầu một phiên khảo sát khi bị ngắt giữa chừng.
- **Đóng lệnh sửa chữa (F10) bắt buộc có ảnh sau + ghi chú kết quả**, không cho phép đóng lệnh nếu thiếu.
- **Dữ liệu theo địa bàn**: chỉ thấy/tải trước dữ liệu thuộc `administrative_unit` được phân công, lọc ở tầng server (BE-08) — mobile không tự lọc lại phía client như một biện pháp phân quyền.

## Tầng API

Backend đã có API thật cho mọi màn của mobile (cập nhật 2026-11-17). Quy tắc:

1. Định nghĩa interface Retrofit đúng theo endpoint trong bảng C3 của đặc tả (VD: `GET /api/v1/work-orders/assigned-to-me`).
2. Data class DTO đặt đúng tên field tiếng Anh như trong đặc tả (VD: `work_order_id`, `status`, không tự đặt tên khác).
3. Viết `RealXxxRepository` gọi API thật, ghi Room trước với các màn thao tác chính (offline-first), và gắn nó vào `RepositoryModule`. Không tạo bản `FakeXxxRepository` nữa (đã bỏ 2026-11-17, xem mục Kiến trúc).
5. F07 (nhập lux thủ công) đã bị loại bỏ khỏi đặc tả. `lux_reading` chỉ được dùng cho luồng đo lux bằng máy ESP32 theo cột (mục "Đo lux theo cột đèn", 2026-11-23), không làm lại màn nhập tay. Với `notification` (F14): đã có endpoint/field thật (BE-27, xem mục "Khoảng trống đã biết" ở trên đã cập nhật 2026-10-13) — code `NotificationRepository`/DTO theo đúng field đó được ngay, không cần hỏi lại WP2.

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

- Trước khi sửa code, nêu ngắn gọn kế hoạch và các file sẽ đụng tới; dừng lại hỏi nếu đặc tả không nói rõ field/behavior nào đó thay vì tự đoán (đặc biệt với `notification` và trạng thái sweep bị từ chối/khảo sát lại — xem "Khoảng trống đã biết"; F07 nhập lux thủ công đã bị loại bỏ, không code lại — đo lux bằng máy ESP32 theo cột là luồng khác, xem mục riêng 2026-11-23).
- Không tự thêm thư viện mới ngoài danh sách ở mục "Ngăn xếp công nghệ" nếu chưa hỏi qua.
- Không refactor code không liên quan tới task đang làm, kể cả khi thấy code cũ chưa đẹp.
- Sau khi code xong 1 task, tự chạy `./gradlew ktlintCheck` và sửa lỗi format trước khi coi là xong.
- Nếu 1 màn hình có 4 trạng thái bắt buộc theo đặc tả (đang tải / có dữ liệu / rỗng / lỗi), phải làm đủ cả 4, không được bỏ qua trạng thái rỗng/lỗi vì "ít quan trọng".
- Với các màn hình thao tác chính (F03, F04, F05, F06, F10, F11), luôn tự kiểm tra lại: thao tác có ghi Room trước khi gọi API không, có bị chặn bởi trạng thái mạng không — nếu có, đó là vi phạm nguyên tắc offline-first.
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
