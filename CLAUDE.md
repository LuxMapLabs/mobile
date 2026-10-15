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
- **F14 (Thông báo) — ĐÃ CÓ API THẬT ở backend, cập nhật 2026-10-13, phần ghi dưới đây (2026-10-05) đã lỗi thời.** Xác nhận trực tiếp bằng cách đọc source code repo `luxmap_backend` (nhánh `dev`) và `docs/api-contract-v1.1.md` của repo đó: module `LuxMap.Modules.Notifications` **đã code xong và implemented** (BE-27, v1.10) — `NotificationsController.cs` có đủ 4 endpoint `GET /api/v1/notifications` (`?unread_only=&page=&page_size=`, trả `{page, page_size, total, unread_count, items[]}`), `GET /api/v1/notifications/unread-count` (`{unread_count}`), `POST /api/v1/notifications/{notification_id}/read` (204, idempotent), `POST /api/v1/notifications/read-all` (204). Mỗi item: `notification_id, type, title, body, entity_type, entity_id, created_at, read_at` — `title`/`body` do server soạn sẵn tiếng Việt, client hiển thị nguyên văn không tự dịch; `type` chọn icon (11 giá trị đã liệt kê, VD `work_order_assigned`/`work_order_returned`/`survey_ready_for_review`/`fault_reported`...; giá trị lạ vẫn hiển thị được, không lỗi); `entity_type`+`entity_id` dùng để mở đúng màn chi tiết tương ứng. **Đây là polling, không phải push** — BE ghi rõ "client polling: gọi `unread-count` mỗi 30–60 giây khi app mở, tải danh sách khi người dùng mở chuông. Một kênh push (Firebase) thêm sau sẽ đọc cùng dữ liệu — bốn endpoint này giữ nguyên." Nghĩa là Firebase Cloud Messaging trong mục "Ngăn xếp công nghệ" **vẫn là việc chưa làm** (không cần cho F14 chạy được, chỉ cần cho đẩy thông báo tức thời sau này).
  **Vẫn khoảng trống thật, chỉ ở phía mobile:** chưa có `feature/notification`, chưa có dependency Firebase Cloud Messaging nào trong `build.gradle.kts` (không cần tới khi chỉ polling), và `ProfileScreen`/các màn khác chưa gọi tới API này. Trước khi code F14 thật (list + badge + mark-read), dùng đúng field/endpoint/enum `type` ở trên — không tự đặt tên khác, không cần hỏi lại WP2/WP5 vì đã có hợp đồng thật.
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

**Chưa chốt — dừng lại hỏi, không tự đoán khi code:**
- Chưa có endpoint backend nào cho việc upload ảnh/video ngắn theo cột rồi AI quét (đã kiểm tra trực tiếp source code `luxmap_backend` nhánh `dev` ngày 2026-10-15: chỉ có `POST /api/v1/sweeps` nhận 1 video + track GPS/lux cho cả phiên, và `SurveyReviewController` chỉ duyệt/trả cả sweep — không có endpoint nào nhận ảnh/video rời theo từng cột). Người dùng đã gửi đề xuất endpoint cho BE (xem mục 8 của spec dưới đây) — chờ BE xác nhận trước khi viết `RealPolePhotoRepository`.
- Kiến trúc màn hình phần **video ngắn** (nửa còn lại của yêu cầu mentor, chưa làm) vẫn chưa quyết — xem mục "Ngoài phạm vi v1" của spec.

**Đã code xong phần chụp ảnh (v1, ưu tiên demo) — 2026-10-15, xem `docs/superpowers/specs/2026-10-15-pole-photo-capture-design.md` và `docs/superpowers/plans/2026-10-15-pole-photo-capture.md`:**
- Màn hình: `feature/survey/ui/polephoto/` — `PoleListScreen`/`PoleListViewModel` (danh sách cột, gọi `GET /work-orders/{id}/poles` thật) và `PolePhotoCaptureScreen`/`PolePhotoCaptureViewModel` (chụp 1 ảnh/cột). Routes: `survey/pole-photos/{workOrderId}` và `survey/pole-photos/{workOrderId}/capture/{poleId}`. Vào từ nút "Chụp ảnh cột đèn" mới trong `SurveyHubScreen`.
- Bảng Room cục bộ: `local_pole_photo` (entity `LocalPolePhotoEntity`, DAO `PolePhotoDao`). Repository: `PolePhotoRepository` interface + `FakePolePhotoRepository` (chưa có `Real...` vì chưa có endpoint BE).
- **Camera dùng camera hệ thống qua Intent (`ActivityResultContracts.TakePicture()`), KHÔNG dùng CameraX.** Đây là thay đổi so với lần ghi đầu tiên (từng định dùng CameraX `ImageCapture`) — khi code mới phát hiện màn "ảnh sau" của F10 (`EvidenceCaptureScreen`/`EvidenceCaptureViewModel`) đã làm đúng việc này rồi (1 ảnh, gắn GPS, lưu Room), nên tái dùng pattern đó thay vì viết mới. Vẫn giữ nguyên lý do ban đầu: khoá exposure thật **KHÔNG áp dụng ở v1** cho màn chụp ảnh theo cột — đây là **ngoại lệ có chủ đích, đã xác nhận với người dùng**, không phải quên, vì khoá exposure thật đòi hỏi viết thêm 1 pipeline Camera2 thủ công như `VideoCaptureSession.kt` (992 dòng). **Nợ kỹ thuật cần làm sau demo**, không áp dụng cho luồng video liên tục hiện có (luồng đó vẫn khoá exposure thật, không đổi). Không ghi EXIF vào ảnh (GPS/thời gian chỉ lưu trong Room, giống hệt F10).
- Chưa có `SyncOpHandler` cho ảnh cột đèn vì chưa có endpoint BE — ảnh lưu local, **không đẩy vào `sync_queue` thật** ở v1 (đẩy vào mà không có handler sẽ bị `SyncQueueProcessor` đánh dấu `failed` ngay — xem `SyncQueueProcessor.kt`). Việc này nối vào `sync_queue` thật khi `RealPolePhotoRepository` được viết (sau khi BE xác nhận endpoint).
- **Khoảng trống đã biết, chưa làm**: màn Danh sách cột chỉ gọi API trực tiếp, không cache lại Room — nếu mất mạng đúng lúc mở màn này thì không chụp được, vi phạm nguyên tắc offline-first ở trên cho riêng màn này. Chấp nhận tạm thời cho bản demo (xem code review ngày 2026-10-15 trong `.superpowers/sdd/2026-10-15-pole-photo-capture/progress.md`); cần cache + `isStale` trước khi dùng thật ngoài hiện trường.

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

**Cập nhật 2026-10-11:** lớp brand/semantic (background/surface/text/border/focus/navigation) đã được chủ dự án duyệt chuyển từ Design System v2.0 sang v2.1 ("Night Ledger" redesign, `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2, roadmap `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` quyết định #1, đã lên code ở Phase 0). **Lớp status (asset condition/sync/work-order status/priority/task-kind) giữ nguyên từ v2.0, không đổi** — các bảng badge ở dưới vẫn là nguồn đúng. Khi code token mới, lấy đúng từ `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2 + `docs/LuxMap_Mobile_Design_System_v2.1.md`, không tự chọn màu khác.

### Primitive colors — brand/semantic (v2.1, hiện hành trong `Color.kt`)

```kotlin
val Paper  = Color(0xFFF5F4F0)   // Nền Light
val Ink    = Color(0xFF15171C)   // Chữ chính Light, khối "ink" (NextUpCard...)
val Ink2   = Color(0xFF5B5F68)   // Chữ phụ Light
val Line   = Color(0xFFDEDBD3)   // Viền/outline Light
val Lumen  = Color(0xFF3448D6)   // Thương hiệu, CTA, focus Light
val Night      = Color(0xFF0B0C0F) // Nền Dark
val NightText  = Color(0xFFEDEBE6) // Chữ chính Dark
val NightText2 = Color(0xFF9A9DA6) // Chữ phụ Dark
val NightLine  = Color(0xFF2C2F38) // Viền/outline Dark
val LumenNight = Color(0xFFA3ADFF) // Thương hiệu, CTA, focus Dark
```

Bảng đầy đủ (LineSoft, Disabled*, LumenCont/LumenOnCont, NightSurf/NightSurf2, LumenNightCont/LumenNightOnCont, ErrorDark...) xem `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2.1 và code thật trong `core/theme/Color.kt`.

**Primitive v2.0 vẫn còn sống trong code**, dùng cho phần chưa tới lượt redesign — không xoá khi gặp: `Navy700`/`Navy900`/`Blue600` (`BrandHeroGradient`, chờ Phase 11 quyết màu hero F01 — xem mục "Khoảng trống đã biết" ở trên), `Green400`/`Amber500`/`Rose600`/`Gray500`/`Danger600` (badge trạng thái, bản đồ, chart lux — xem "Primitive colors — status" ngay dưới).

### Primitive colors — status (v2.0, không đổi)

```kotlin
val Green400 = Color(0xFF5FC4B0)   // Đèn bình thường, điểm nhấn Dark Mode
val Amber500 = Color(0xFFE9A23B)   // Cảnh báo trung bình
val Rose600 = Color(0xFFD64545)    // Hỏng/tắt, SLA khẩn
val Success600 = Color(0xFF059669) // Hoàn tất thành công
val Danger600 = Color(0xFFDC2626)  // Hành động nguy hiểm
```

Bảng đầy đủ (gray-50/200/500/700, dark-900/700, dark-muted...) xem mục 2.1 Design System v2.0.

### Semantic colors (Light / Dark) — v2.1, hiện hành trong `LuxMapSemanticColors`/`Theme.kt`

| Semantic token | Light | Dark |
|---|---:|---:|
| `color.background` | `#F5F4F0` (Paper) | `#0B0C0F` (Night) |
| `color.surface` | `#FFFFFF` | `#16181D` (NightSurf) |
| `color.text.primary` | `#15171C` (Ink) | `#EDEBE6` (NightText) |
| `color.text.secondary` | `#5B5F68` (Ink2) | `#9A9DA6` (NightText2) |
| `color.border` / `outline` | `#DEDBD3` (Line) | `#2C2F38` (NightLine) |
| `color.focus` | `#3448D6` (Lumen) | `#A3ADFF` (LumenNight) |
| `color.navigation.active` | `#3448D6` (Lumen) | `#A3ADFF` (LumenNight) |

Bảng đầy đủ (surfaceContainerHigh, primaryContainer, outlineVariant, disabled.container/content...) xem `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` mục 2.1 và code thật trong `core/theme/Theme.kt`. **Giá trị `outlineVariant` trong spec gốc (`LineSoft`/`#23262D`) đã sửa thành `Line`/`NightLine` khi code Phase 0** — divider gần như vô hình với giá trị gốc, phát hiện qua code review; xem `docs/contract-drift.md`.

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
