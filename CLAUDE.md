# LuxMap — Mobile App (Android)

Đây là phần **Mobile — Field Operations**, ứng dụng di động hiện trường dành cho vai trò duy nhất **Tổ khảo sát/sửa chữa** (Field Crew) của hệ thống **LuxMap** (GIS + IoT + Computer Vision để quản lý tài sản và sự cố chiếu sáng đường giao thông nông thôn). Nền tảng **Android, Kotlin + Jetpack Compose**. Chỉ làm 1 nền tảng (không làm iOS song song).

Vai trò "Người dân" kiểu hệ thống cũ (CivicFlow) — có tài khoản, đăng nhập, dùng app riêng để theo dõi tiến độ phản ánh — **không còn tồn tại**. Tuy nhiên theo hồ sơ đăng ký đề tài (`docs/FA26SE222 v1.2.docx`, mục 3.2.c), LuxMap có vai trò **Citizen** ở dạng tối giản: quét QR code gắn trên trụ đèn để gửi một báo cáo sự cố, không tài khoản, không app, không theo dõi trạng thái. Bề mặt quét QR này **không thuộc phạm vi repo mobile này** (không phải app Android cho công dân) — nhưng khi làm F11/BE liên quan tới việc tạo `Fault`, cần biết `field_report` không phải nguồn ngoại lệ duy nhất, còn có nguồn từ QR công dân.

Bốn vai trò còn lại theo đúng tên trong hồ sơ đăng ký: **Superior** (giám sát tổng quan — Cơ quan quản lý), **Manager** (= Maintenance Engineer, Kỹ sư bảo trì), **System Admin** (Quản trị) — dùng nền tảng Web GIS riêng (không thuộc phạm vi repo này) — và **Field Engineer** (= Tổ khảo sát/sửa chữa), vai trò duy nhất của app mobile này. Mọi sự cố (`Fault`) trong LuxMap do engine IoT/CV sinh ra hoặc do Citizen gửi qua QR; Field Engineer chỉ báo cáo bổ sung tại hiện trường như một nguồn ngoại lệ (`field_report`).

## Tài liệu tham chiếu bắt buộc đọc trước khi code

- `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx` — đặc tả từng màn hình (mã F01–F15), bảng ánh xạ Màn hình → API → Bảng dữ liệu (mục C2–C3), enum trạng thái dùng chung (mục C4), quy tắc nghiệp vụ bắt buộc (mục C5), ma trận truy vết UI/trạng thái (mục P9)
- `docs/LuxMap_Mobile_Design_System_v2.0.md` — design tokens 3 lớp (primitive/semantic/component), typography, spacing, master components, quy tắc Dark Mode theo ngữ cảnh
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

- **F07 (Nhập lux) — ĐÃ BỊ LOẠI BỎ khỏi đặc tả (cập nhật 2026-09-27):** không còn màn nhập lux thủ công. Không dùng `lux_reading`/`local_lux_reading`/`POST /api/v1/lux-readings`. Nguồn lux cho RQ1/CV-12 chuyển sang log lux BLE ghi trong phiên quay ở F04 (xem mục C7 của `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`), còn chờ WP4 xác nhận cách dùng lại dữ liệu này cho đối chiếu độ chính xác.
- **F14 (Thông báo) — ĐÃ CODE XONG VÀ MERGE VÀO `dev` (2026-10-18, PR #87, đã qua review độc lập + fix pass).** Phần ghi dưới đây (API backend, 2026-10-13) vẫn đúng; phần "vẫn khoảng trống thật" cũ đã lỗi thời, xoá. Xác nhận trực tiếp bằng cách đọc source code repo `luxmap_backend` (nhánh `dev`) và `docs/api-contract-v1.1.md` của repo đó: module `LuxMap.Modules.Notifications` **đã code xong và implemented** (BE-27, v1.10) — `NotificationsController.cs` có đủ 4 endpoint `GET /api/v1/notifications` (`?unread_only=&page=&page_size=`, trả `{page, page_size, total, unread_count, items[]}`), `GET /api/v1/notifications/unread-count` (`{unread_count}`), `POST /api/v1/notifications/{notification_id}/read` (204, idempotent), `POST /api/v1/notifications/read-all` (204). Mỗi item: `notification_id, type, title, body, entity_type, entity_id, created_at, read_at` — `title`/`body` do server soạn sẵn tiếng Việt, client hiển thị nguyên văn không tự dịch; `type` chọn icon (**13 giá trị thật, không phải 11 như bản ghi cũ** — đã sửa khi đọc lại source để code F14: `work_order_assigned/unassigned/rescheduled/returned/cancelled/completed/verified/overdue`, `survey_returned/ready_for_review/processing_failed`, `fault_reported`, `citizen_report_received`; giá trị lạ vẫn hiển thị được, không lỗi — `NotificationType.UNKNOWN` fallback); `entity_type`+`entity_id` dùng để mở đúng màn chi tiết tương ứng. **Đây là polling, không phải push** — BE ghi rõ "client polling: gọi `unread-count` mỗi 30–60 giây khi app mở, tải danh sách khi người dùng mở chuông. Một kênh push (Firebase) thêm sau sẽ đọc cùng dữ liệu — bốn endpoint này giữ nguyên." Firebase Cloud Messaging trong mục "Ngăn xếp công nghệ" **vẫn là việc chưa làm** (không cần cho F14 chạy được, chỉ cần cho đẩy thông báo tức thời sau này).
  **Mobile đã có:** `feature/notification/` (data + ui, `NotificationRepository` Fake/Real, `NotificationListViewModel`/`Screen`, `NotificationBadgeViewModel`), chuông + badge số chưa đọc hiện ở `NavGraph.kt`'s Scaffold trên cả 4 tab (không phải `AppTopBar`), polling 45s chỉ chạy khi app foreground + đã đăng nhập (`repeatOnLifecycle` + gate theo `isLoggedIn`, tự reset badge khi logout), `ProfileScreen`'s dòng "Thông báo" đã đổi text trỏ sang chuông. **Phạm vi điều hướng v1 — có chủ đích, không phải thiếu sót:** bấm vào 1 thông báo chỉ mở màn chi tiết thật khi `entity_type = "work_order"` (dùng `Routes.WorkOrderDetail`); `survey_sweep`/`fault`/`citizen_report` chỉ đánh dấu đã đọc, không mở màn — vì `survey_sweep` chưa có màn đích thật trong mobile (các màn khảo sát hiện dùng UUID cục bộ, không phải `SweepId` thật của server; xem mục "Giao tuyến khảo sát" phía dưới và mục F06/F13 ở trên), còn `fault`/`citizen_report` trên thực tế chỉ gửi cho Manager. Không Room cache (F14 vẫn thuộc nhóm màn "đơn giản" theo mục Kiến trúc & Design Pattern, không bắt buộc offline-first như F03/F04/F05/F06/F10/F11).
- **F06/F13 (Phiên khảo sát bị từ chối do chất lượng → khảo sát lại):** hồ sơ đăng ký đề tài (`FA26SE222 v1.2.docx`, mục 3.2.c) mô tả nghiệp vụ Manager "review... accept or reject submitted sessions" và Field Engineer "re-survey a segment when a session is rejected for quality", nhưng đặc tả chi tiết hiện tại (`LuxMap_Mobile_DacTaChiTiet_v2.2.docx` mục C4, D1) chỉ có `survey_sweep.status`: `planned/in_progress/pending_upload/submitted/processed` — không có trạng thái "bị từ chối"/"cần khảo sát lại", và luồng D1 dừng ở bước Kỹ sư bảo trì xác nhận sự cố, không có bước từ chối cả phiên. Trước khi code phần xử lý trạng thái sweep sau khi nộp (F06/F13), xác nhận với WP2/WP5 có bổ sung trạng thái/API mới hay không — không tự thêm enum hay màn hình cho việc này.
- **"Giao tuyến khảo sát" (Survey Hub assigned routes) — SỬA 2026-10-14, bản ghi cũ ở mục này sai.** Trước đó mobile code (`SurveyRepository`/`FakeSurveyRepository`/comment "GET /api/v1/survey-sweeps/planned") giả định có 1 endpoint riêng cho việc này — **endpoint đó không tồn tại**, xác nhận trực tiếp bằng cách đọc source code repo `luxmap_backend` (nhánh `dev`, 2026-10-14): `SweepsController.cs` (route thật `api/v1/sweeps`) và `SurveyIngestService.LockOrder()` (dòng 36–52) bắt buộc `WorkOrder.TaskKind == Survey` + `AssignedTo == người gọi` + `WoStatus == InProgress` trước khi cho tạo sweep. Nghĩa là "giao tuyến khảo sát" dùng **đúng y hệt `WorkOrder`** như kiểm tra/sửa chữa: Manager tạo `WorkOrder(task_kind=survey, assigned_to=FE)` → FE thấy qua **cùng endpoint** `GET /work-orders?task_kind=survey&assigned_to=me` mà Home (F02) đã dùng → FE bấm "Bắt đầu" (action `start` chung) → `wo_status=in_progress` → lúc đó mới được `POST /sweeps` với `work_order_id` đó. Đã sửa `SurveyRepository`/`SurveyHubViewModel`/`SurveyPlanScreen` đọc đúng nguồn này (khoá chọn tuyến đổi từ `surveySweepId` giả sang `workOrderId` thật), và sửa luôn 1 lỗi phát sinh từ giả định cũ: `WorkOrderDetailScreen` (F09) khi bấm "Bắt đầu" ở 1 lệnh `task_kind=survey` trước đây nhảy **thẳng** vào F04 Capture, bỏ qua hoàn toàn checklist readiness bắt buộc ở F03 — nay route qua `Routes.SurveyPlan` trước, đúng quy tắc "không cho bắt đầu khảo sát nếu chưa khoá exposure thật" ở trên. **Vẫn còn khoảng trống thật:** `POST /sweeps` (đăng ký sweep thật phía server) chưa được gọi ở đâu trong luồng upload mobile — `surveySweepId` hiện vẫn chỉ là UUID sinh tại máy cho phiên Room cục bộ, theo dõi riêng ở `docs/contract-drift.md`, không phải lỗi mới do thay đổi này.
- **Cảm biến lux qua BLE trong phiên quay khảo sát (F03/F04) — ĐÃ XÁC NHẬN với chủ dự án (2026-09-27), không còn là mâu thuẫn nghiệp vụ:** mâu thuẫn nêu ở bản trước (mobile tự kết nối BLE theo `docs/nghiep_vu_khao_sat_den.docx` so với `iot_node`/`runtime_history` là hạ tầng IoT cố định tự báo cáo lên server theo `docs/Backend_API_Requirements_For_Mobile.docx` mục 2.1) đã được xác nhận: đây là 1 nguồn dữ liệu thứ ba thật — video + track GPS/heading/timestamp (đã có ở mục C6) **cộng thêm** log lux liên tục qua BLE, gán timestamp bằng đồng hồ điện thoại — chứ không phải cách diễn đạt khác của IoT cố định. Luồng đầy đủ (chặn cứng "Bắt đầu quay" tới khi kết nối BLE ổn định → 3 luồng dữ liệu video/GPS/lux chạy song song độc lập trong phiên quay → dừng quay đóng gói video + 1 file log GPS+lux theo thời gian → upload cả bộ lên server, mobile không tự xử lý gì thêm) đã ghi vào mục **C7** của `docs/LuxMap_Mobile_DacTaChiTiet_v2.2.docx`. **Cập nhật 2026-09-28 (mục C8.5 của đặc tả):** phân quyền đặt tên đã chốt — bảng Room cục bộ do WP6 (mobile) tự đặt tên chính thức, không cần chờ WP2/WP5; định dạng file trong "Session package" (log lux, GPS track, log timestamp frame, file cấu hình quay, manifest) do mobile soạn bản đầu (`v0`), WP4 duyệt sau; chỉ endpoint upload và tên field phía server vẫn chờ Backend (WP2/WP5). Mọi tên tạm và điểm lệch được theo dõi ở `docs/contract-drift.md`.
Xem `docs/contract-drift.md` để biết chính xác tên nào đã chốt, tên nào còn tạm.

- `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` (2026-10-10, copy từ Claude Design project `82538650-3a87-4ecd-8cbc-37aaf17badc1`, bản gốc `LuxMap Redesign.dc.html`) — spec thiết kế lại giao diện ("Night Ledger"), token/component/màn hình chi tiết. **Đã duyệt chuyển token thương hiệu/semantic sang v2.1 (2026-10-11)** — xem mục "Design tokens" dưới đây đã cập nhật theo spec này. Lộ trình triển khai theo từng màn: `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md`.
- `docs/LuxMap_Mobile_Design_System_v2.1.md` (đề xuất, phần token đã duyệt 2026-10-11) — bổ sung cho `LuxMap_Mobile_Design_System_v2.0.md`: token `Disabled` mới, typography bổ sung, component mới. Đọc cùng với `LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2 vì 2 file có 1 điểm xung đột giá trị (token `Disabled`) và 1 điểm xung đột màu hero F01 — cả hai được ghi rõ trong roadmap ở trên, chưa tự chọn một bên.
- `docs/LuxMap_Mobile_Screen_Review_2026-10-11.md` — đối chiếu trực tiếp giữa prototype thiết kế và code/CLAUDE.md hiện tại tại thời điểm viết; dùng để biết phần nào của redesign là style thuần, phần nào đụng vào field/endpoint chưa chốt.

### Quyết định nghiệp vụ mới đã chốt (2026-09-25) — chưa cập nhật vào đặc tả chi tiết / api-contract v1.1

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
- Repository **đã là Real, không còn Fake**: `RepositoryModule.kt` bind `RealPolePhotoRepository`/`RealPoleListRepository`. Ghi Room (`local_pole_photo`, entity `LocalPolePhotoEntity`, DAO `PolePhotoDao`) trước, rồi đẩy `sync_queue` qua `UploadPolePhotoSyncHandler` — handler này **đã đăng ký thật** vào `Set<SyncOpHandler>` (`SyncModule.kt`) và chạy qua `SyncQueueProcessor`/WorkManager, dùng `client_op_id` = photoId local làm idempotency key, phân biệt đúng lỗi tạm thời (retry)/vĩnh viễn (`failed`)/409 (`conflict`). `PoleListScreen` đã cache Room (`local_pole_list_item`, PR #77) và sheet kết quả AI đã cache-first + `isStale` (PR #76) — cả 2 gap "chưa cache Room" ghi ở bản cũ đã đóng. `FakePolePhotoRepository` vẫn còn trong repo (đúng pattern Fake/Real của CLAUDE.md) nhưng không còn được bind.
- **Camera dùng camera hệ thống qua Intent (`ActivityResultContracts.TakePicture()`), KHÔNG dùng CameraX.** Đây là thay đổi so với lần ghi đầu tiên (từng định dùng CameraX `ImageCapture`) — khi code mới phát hiện màn "ảnh sau" của F10 (`EvidenceCaptureScreen`/`EvidenceCaptureViewModel`) đã làm đúng việc này rồi (1 ảnh, gắn GPS, lưu Room), nên tái dùng pattern đó thay vì viết mới. Vẫn giữ nguyên lý do ban đầu: khoá exposure thật **KHÔNG áp dụng ở v1** cho màn chụp ảnh theo cột — đây là **ngoại lệ có chủ đích, đã xác nhận với người dùng**, không phải quên, vì khoá exposure thật đòi hỏi viết thêm 1 pipeline Camera2 thủ công như `VideoCaptureSession.kt` (992 dòng). **Nợ kỹ thuật cần làm sau demo**, không áp dụng cho luồng video liên tục hiện có (luồng đó vẫn khoá exposure thật, không đổi). Không ghi EXIF vào ảnh (GPS/thời gian chỉ lưu trong Room, giống hệt F10).
  - **Câu hỏi mở, chưa hỏi mentor/BE (ghi lại 2026-10-18, dừng lại hỏi trước khi tự quyết định có cần làm hay không):** model AI xử lý `cv_state` cho endpoint `POST .../pole-photos` có yêu cầu ảnh phơi sáng cố định (khoá AE) không? Nếu model nhạy với việc camera tự bù sáng, 1 cột đèn mờ/gần tắt có thể bị auto-exposure "chụp sáng lên" trông giống đèn bình thường, làm sai kết quả phân loại — lúc đó bắt buộc phải làm pipeline Camera2 khoá exposure thật trước khi dùng ngoài hiện trường thật (không chỉ demo). Nếu model không nhạy (train trên ảnh auto-exposure, hoặc BE/AI tự chuẩn hoá độ sáng) thì giữ nguyên camera hệ thống như hiện tại là đủ. **Không tự quyết định hướng nào khi chưa có câu trả lời.**
- **Test coverage, cập nhật 2026-10-18**: `PoleListScreen` nay đã có Compose UI test (`PoleListScreenTest.kt`, 11 case — 4 trạng thái UiState, tap cột, badge sync, icon xem kết quả AI, banner stale, nút Hoàn tất/Thử lại), chạy thật trên máy (SM-A075F) 11/11 pass. **Chủ động không viết test cho Composable `PolePhotoCaptureScreen`/`PolePhotoCaptureRoute`** (quyết định, không phải thiếu sót): logic nghiệp vụ thật đã có `PolePhotoCaptureViewModelTest` che (3 case); phần còn lại trong composable chỉ là ống dẫn Android thuần (check quyền `CAMERA` qua `ContextCompat`, dựng URI `FileProvider`, bắn Intent camera hệ thống) — không mock được quyền thật nên test sẽ phập phù theo máy, và cách làm đáng tin cần thêm dependency `espresso-intents` (chưa có trong "Ngăn xếp công nghệ", đã hỏi và người dùng chọn không thêm, 2026-10-18). Dựa vào test ViewModel + kiểm tra thủ công trên máy thật (PR #74) cho phần này.

### Xin hủy lệnh (WO-CANCEL-REQUEST) — THÊM 2026-10-27, code được ngay, đã viết ở mobile

Xác nhận trực tiếp bằng cách đọc source `luxmap_backend` (nhánh `dev`, tới commit `aa0d0ba`): migration `20261026100552_AddWorkOrderCancellationRequests`, `WorkOrdersController.cs` (`request-cancellation`/`reject-cancellation`), `WorkOrderRules.cs`, `WorkOrderService.cs`, test `WorkOrderCancellationTests.cs`, và `docs/api-contract-v1.1.md` mục "Xin hủy / từ chối nhận lịch — v1.19, WO-CANCEL-REQUEST". Backend tự ghi là **SELF-SIGNED** (chốt D-1…D-14 ngày 26/10/2026), nền tạm tới FW kế tiếp, WP5/WP6 còn phải xác nhận — coi là nguồn đúng để code theo, cập nhật lại mục này nếu BE đổi sau vòng duyệt đó.

- `POST /api/v1/work-orders/{id}/request-cancellation` — policy `ExecuteWorkOrders`, body `{"reason": "..."}` (bắt buộc, trim 1–500 ký tự UTF-16, thiếu/trắng/quá dài → 400). Chỉ kỹ sư đang được giao, và chỉ khi `wo_status == assigned` (**không áp dụng khi đã `in_progress`**) — đúng điều kiện `WorkOrderRules.Allows("request_cancellation", ...)`, không tự suy diễn từ `wo_status` phía mobile.
- `POST /api/v1/work-orders/{id}/reject-cancellation` — policy `ManageWorkOrders`, **ngoài phạm vi mobile** (Manager dùng Web GIS riêng).
- 5 field mới trên `WorkOrderDetail` (và item/agenda/bundle, mobile chỉ cần detail): `cancellation_request_status` (`none|pending|rejected`), `cancellation_request_note`, `cancellation_requested_at`, `cancellation_reject_note`, `cancellation_rejected_at`.
- `allowed_actions` tự thêm `request_cancellation` khi đủ điều kiện (none/rejected → pending) — mobile dùng đúng cơ chế `"request_cancellation" in allowed_actions` đã có sẵn cho `start`/`complete`, không gọi thêm endpoint riêng để biết khi nào hiện nút.
- **Lưu ý quan trọng, khác với suy đoán ban đầu khi đọc mockup thiết kế**: `pending` **không** khóa `start`/`complete` — `allowed_actions` vẫn giữ `start` khi đang chờ duyệt hủy. Mobile không tự khóa CTA chính khi có yêu cầu hủy đang chờ, chỉ hiện banner thông báo riêng.
- Đã code xong ở mobile (nhánh `style/redesign-workorder-detail-inspection-v2`, F09 Lệnh kiểm tra): nút "Yêu cầu hủy" (chỉ hiện khi `request_cancellation` có trong `allowed_actions`) → bottom sheet chọn 1 trong 4 lý do có sẵn + ghi chú tuỳ chọn, gộp thành 1 chuỗi gửi lên field `reason` duy nhất (BE không có field ghi chú riêng). Banner `pending`/`rejected` hiện trong vùng ghi chú của màn chi tiết. Field/model: `WorkOrderDetailDto`/`WorkOrderDetail` (`cancellationRequestStatus`/`cancellationRequestNote`/`cancellationRejectNote` — 2 field timestamp không đưa vào domain model vì UI chưa cần hiển thị), `RequestCancellationRequestDto`, `WorkOrdersApi.requestCancellation`, `WorkOrderDetailRepository.requestCancellation` (Real, không có Fake — theo đúng pattern đã có của repo này). **Chưa áp dụng cho F10 Lệnh sửa chữa** (cùng điều kiện `wo_status == assigned` nên về lý sẽ dùng được, nhưng chưa code/test UI riêng cho màn đó) và **chưa kiểm tra tay trên máy thật**.

### Cửa sổ hành động Bắt đầu/Yêu cầu hủy theo lịch + hoàn thành trong ngày — THÊM 2026-10-29, chỉ ở mobile, CHƯA xác nhận với backend

Quyết định sản phẩm trao đổi trực tiếp với chủ dự án (2026-10-29). **Khác các mục khác ở trên, mục này KHÔNG có xác nhận bằng cách đọc source `luxmap_backend`** — chỉ là quyết định UI/UX cho mobile, chưa chạm tới `WorkOrderRules.cs` phía server. Coi là **rào chắn UI-only**, chưa phải chặn thật ở tầng API.

- Nút "Bắt đầu" và "Yêu cầu hủy" ở F09 (Chi tiết lệnh) chỉ hiện khi còn tối đa 1 ngày nữa là tới `scheduled_date` của `WorkOrder` đó — mở từ `scheduled_date − 1 ngày` và giữ mở xuyên suốt tới hết `scheduled_date`, không đóng lại giữa đường. Trước mốc đó, 2 nút bị ẩn **dù `allowed_actions` của server có `start`/`request_cancellation` hay không** — đây là lớp ẩn thêm ở client, không thay thế điều kiện gốc của server (`wo_status`, người được giao...).
- Nếu `scheduled_date` là `null` (Manager chưa đặt lịch): không áp dụng luật này, giữ hành vi cũ (theo đúng `allowed_actions`).
- Field Engineer được kỳ vọng `complete` lệnh trong đúng ngày `scheduled_date`. **Không chặn nộp trễ** — qua ngày mà chưa `complete` chỉ hiện banner cảnh báo "Quá hạn hoàn thành", nút Hoàn thành vẫn hoạt động theo đúng `allowed_actions` như cũ.
- Tương tự cho lệnh còn `assigned` (chưa từng bấm Bắt đầu) mà đã qua `scheduled_date`: hiện banner "Quá hạn bắt đầu", nút "Bắt đầu" vẫn hoạt động bình thường (không khoá) — cùng nguyên tắc không chặn cứng như trên, chỉ cảnh báo.
- Việc phát hiện "bỏ lỡ" (quá `scheduled_date` mà vẫn `assigned`, chưa từng bấm Bắt đầu) không xử lý ở mobile (không tự huỷ, không thêm trạng thái/enum mới) — dự định tận dụng lại notification type `work_order_overdue` đã có sẵn (xem mục F14 ở trên) để báo Manager. **Chưa xác nhận backend có tự gửi notification này khi quá hạn hay không** — cần hỏi lại WP2/WP5 trước khi dựa vào đó.
- So sánh theo ngày (`yyyy-MM-dd`) bằng giờ thiết bị (`LocalDate.now()`) để biết "hôm nay" — chấp nhận rủi ro lệch ngày ở mức UI-only (chỉ ẩn/hiện nút sớm/muộn, không mất dữ liệu, không tự huỷ gì), cùng loại rủi ro đã né khi bỏ "quá hạn N ngày" tính theo giờ máy ở `WorkOrderSummaryCard` (commit `46e25b1`, nhánh `style/redesign-workorder-detail-repair-v2`).
- **Chưa chặn thật ở API**: nếu sau này cần chặn ở tầng server (không chỉ ẩn UI), phải xác nhận với backend để thêm điều kiện ngày vào `WorkOrderRules.Allows("start"/"request_cancellation", ...)` và trả đúng qua `allowed_actions`. Tới lúc đó, bypass UI (gọi API trực tiếp) vẫn thành công vì server chưa biết luật này.

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

    survey/                   # F03–F06, feature lớn nhất — khảo sát đêm, đường găng dự án
      data/
        SurveyRepository.kt
        FakeSurveyRepository.kt
        RealSurveyRepository.kt
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

## Design tokens — 3 lớp

Token tổ chức theo 3 lớp: **Primitive** (giá trị gốc) → **Semantic** (ý nghĩa sử dụng, khác nhau theo Light/Dark) → **Component** (giá trị theo component cụ thể).

**Cập nhật 2026-10-11:** lớp brand/semantic (background/surface/text/border/focus/navigation) từng được chủ dự án duyệt chuyển từ Design System v2.0 sang v2.1 ("Night Ledger" redesign, `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2, roadmap `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` quyết định #1, đã lên code ở Phase 0).

**Cập nhật 2026-10-14 — ĐÃ ROLLBACK, mục trên đã lỗi thời.** Quyết định sản phẩm đổi ý: "Claude Design project turn 3 (Đồng bộ toàn bộ giao diện theo Design System v2.0)" yêu cầu bỏ bảng màu Night Ledger, trả lớp brand/semantic về đúng Design System v2.0 — đã code xong (PR #66 `style/redesign-rollback-tokens-v2.0`, theo sau là PR #65 `style/redesign-phase11-login` và PR #67 `style/redesign-phase2-home-v2.0` chỉnh 2 màn còn dính token v2.1), merge vào `dev` ngày 2026-10-14. `LightSemanticColors`/`DarkSemanticColors` và M3 color scheme (`Theme.kt`) hiện trỏ lại `Navy700`/`Blue500`/`Gray*`/`Dark*` — **không còn dùng** `Paper`/`Ink`/`Lumen`/`Night*`. Theo đó: màn Đăng nhập (F01) dùng hero `navy-700`/`BrandHeroGradient` (không phải hero `inverseSurface`/Ink như spec v2.1), và đích sau đăng nhập là `Routes.Map` (không phải `Routes.Home`) — 2 điểm roadmap từng đánh dấu "cần quyết định sản phẩm" nay đã chốt theo hướng này. Token v2.1 (Paper/Ink/Lumen/Night*...) **vẫn còn khai báo trong `Color.kt`** (không dùng ở đâu khác trong code, xác nhận bằng grep toàn bộ `app/src/main/java/com/luxmap` ngày 2026-10-18) — giữ lại để tham khảo lịch sử, không phải nguồn token khi code mới, có thể dọn sau nếu muốn. **Lớp status (asset condition/sync/work-order status/priority/task-kind) không bị ảnh hưởng, vẫn là v2.0 như trước giờ** — các bảng badge ở dưới vẫn là nguồn đúng. Khi code token brand/semantic mới, lấy đúng từ mục 2.1 Design System v2.0 + code thật trong `core/theme/Color.kt`/`Theme.kt` — **không dùng** `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` hay `docs/LuxMap_Mobile_Design_System_v2.1.md` cho lớp này nữa (2 file đó vẫn còn trong `docs/`, không xoá, nhưng đã bị quyết định 2026-10-14 vượt qua).

### Primitive colors — brand/semantic (v2.0, hiện hành trong `Color.kt`)

```kotlin
val Navy700 = Color(0xFF1F3864)  // Thương hiệu, điều hướng active Light
val Blue500 = Color(0xFF3E86C9)  // Focus Light
val Gray25  = Color(0xFFF8FAFC)  // Nền Light
val Gray50  = Color(0xFFF1F5F9)  // Surface phụ Light
val Gray200 = Color(0xFFE2E8F0)  // Viền/outline Light
val Gray500 = Color(0xFF64748B)  // Chữ phụ Light, điều hướng inactive
val Gray900 = Color(0xFF0F172A)  // Chữ chính Light
val Dark950 = Color(0xFF0D0D0D)  // Nền Dark
val Dark900 = Color(0xFF121212)  // Surface phụ Dark
val Dark800 = Color(0xFF1A1A1A)  // Surface Dark
val Dark700 = Color(0xFF2A2A2A)  // Viền/outline Dark
val DarkText  = Color(0xFFF5F5F5) // Chữ chính Dark
val DarkMuted = Color(0xFFA0A0A0) // Chữ phụ Dark, điều hướng inactive Dark
```

Bảng đầy đủ xem mục 2.1 Design System v2.0 và code thật trong `core/theme/Color.kt`. `Navy900`/`Blue600` (thêm từ Global Design System v1.0 mục 3.1) chỉ dùng cho `BrandHeroGradient` ở hero F01, không phải token semantic chung.

### Primitive colors — status (v2.0, không đổi)

```kotlin
val Green400 = Color(0xFF5FC4B0)   // Đèn bình thường, điểm nhấn Dark Mode
val Amber500 = Color(0xFFE9A23B)   // Cảnh báo trung bình
val Rose600 = Color(0xFFD64545)    // Hỏng/tắt, SLA khẩn
val Success600 = Color(0xFF059669) // Hoàn tất thành công
val Danger600 = Color(0xFFDC2626)  // Hành động nguy hiểm
```

Bảng đầy đủ (gray-50/200/500/700, dark-900/700, dark-muted...) xem mục 2.1 Design System v2.0.

### Semantic colors (Light / Dark) — v2.0, hiện hành trong `LuxMapSemanticColors`/`Theme.kt`

| Semantic token | Light | Dark |
|---|---:|---:|
| `color.background` | `#F8FAFC` (Gray25) | `#0D0D0D` (Dark950) |
| `color.surface` | `#FFFFFF` | `#1A1A1A` (Dark800) |
| `color.surface.subtle` | `#F1F5F9` (Gray50) | `#121212` (Dark900) |
| `color.text.primary` | `#0F172A` (Gray900) | `#F5F5F5` (DarkText) |
| `color.text.secondary` | `#64748B` (Gray500) | `#A0A0A0` (DarkMuted) |
| `color.border` / `outline` | `#E2E8F0` (Gray200) | `#2A2A2A` (Dark700) |
| `color.focus` | `#3E86C9` (Blue500) | `#5FC4B0` (Green400) |
| `color.navigation.active` | `#1F3864` (Navy700) | `#5FC4B0` (Green400) |
| `color.navigation.inactive` | `#64748B` (Gray500) | `#A0A0A0` (DarkMuted) |

Bảng đầy đủ xem mục 2.1 Design System v2.0 và code thật trong `core/theme/Theme.kt`. M3 `ColorScheme` ở đây chỉ dùng các role cơ bản (`primary/onPrimary/secondary/.../outline/error`) — các role từng thêm cho redesign v2.1 (`primaryContainer`, `inverseSurface`, `surfaceContainerHigh`, `outlineVariant`) đã bị bỏ cùng đợt rollback 2026-10-14, không phải chỉ đổi giá trị như ghi chú `contract-drift.md` cũ (mục đó giờ chỉ còn giá trị lịch sử).

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

- **Offline-first tuyệt đối**: mọi màn hình thao tác chính (F03, F04, F05, F06, F10, F11) ghi Room trước, không chờ phản hồi mạng; đồng bộ là tiến trình nền độc lập qua `sync_queue` + WorkManager.
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
5. F07 đã bị loại bỏ khỏi đặc tả — không tạo `LuxReadingRepository`/entity `lux_reading` nữa (xem "Khoảng trống đã biết"). Với `notification` (F14): đã có endpoint/field thật (BE-27, xem mục "Khoảng trống đã biết" ở trên đã cập nhật 2026-10-13) — code `NotificationRepository`/DTO theo đúng field đó được ngay, không cần hỏi lại WP2.

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

- Trước khi sửa code, nêu ngắn gọn kế hoạch và các file sẽ đụng tới; dừng lại hỏi nếu đặc tả không nói rõ field/behavior nào đó thay vì tự đoán (đặc biệt với `notification` và trạng thái sweep bị từ chối/khảo sát lại — xem "Khoảng trống đã biết"; F07/`lux_reading` đã bị loại bỏ khỏi đặc tả, không code lại).
- Không tự thêm thư viện mới ngoài danh sách ở mục "Ngăn xếp công nghệ" nếu chưa hỏi qua.
- Không refactor code không liên quan tới task đang làm, kể cả khi thấy code cũ chưa đẹp.
- Sau khi code xong 1 task, tự chạy `./gradlew ktlintCheck` và sửa lỗi format trước khi coi là xong.
- Nếu 1 màn hình có 4 trạng thái bắt buộc theo đặc tả (đang tải / có dữ liệu / rỗng / lỗi), phải làm đủ cả 4, không được bỏ qua trạng thái rỗng/lỗi vì "ít quan trọng".
- Với các màn hình thao tác chính (F03, F04, F05, F06, F10, F11), luôn tự kiểm tra lại: thao tác có ghi Room trước khi gọi API không, có bị chặn bởi trạng thái mạng không — nếu có, đó là vi phạm nguyên tắc offline-first.
- **Comment trong code viết bằng tiếng Anh, không dùng tiếng Việt** — kể cả comment giải thích lý do/quyết định kỹ thuật (dạng "vì sao chọn cách này"), không chỉ comment mô tả đơn thuần. Áp dụng cho mọi file code (`.kt`, `.kts`...). Tài liệu (`CLAUDE.md`, đặc tả, PR description, commit message) vẫn viết tiếng Việt/tiếng Anh như quy ước riêng của từng loại đã nêu ở trên, không đổi.
  - **Dùng từ tiếng Anh đơn giản, thông dụng** — loại ai trong nhóm cũng biết nghĩa ngay không cần tra từ điển (VD: `use`/`need`/`because`/`before`/`instead of`), tránh từ học thuật/ít gặp (VD: tránh `leverage`, `utilize`, `henceforth`, `aforementioned` — dùng `use`, `use`, `from now on`, `mentioned above`). Câu ngắn, đúng ngữ pháp cơ bản, không viết văn hoa. Mục tiêu: người đọc code (kể cả người không giỏi tiếng Anh) đọc lướt qua là hiểu ngay, không phải để chứng tỏ vốn từ.

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
