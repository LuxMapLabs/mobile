# Củng cố task đo GPS cột — thiết kế đề xuất

## Mục tiêu và nghiệp vụ giữ nguyên

`pole_gps_update` là task độc lập để ghi nhận/đo lại vị trí cột đã có. Manager chọn tuyến/cột, giao kỹ sư và gửi PDF QR. Kỹ sư chụp ảnh từng cột, ghi nhận vị trí, nộp báo cáo. Manager duyệt thì BE mới ghi vào GIS; return cho phép đo lại. Cho phép báo cáo một phần: ít nhất một cột và ghi chú tối thiểu 10 ký tự. Không thêm bước quét QR hoặc bắt buộc hoàn thành mọi cột.

Nguồn đã đối chiếu: BE `docs/contract-drift.md` mục WO-POLE-GPS PG-1…PG-13; `WorkOrderService.cs` (Report, MovePolesAsync); `WorkOrderEvidence.cs` (chỉ upload khi in_progress); Web CreateManualModal, FieldReportDetailModal và handleApproveGpsUpdate. Tất cả nguồn BE/Web chỉ đọc.

## Phạm vi

Chỉ mobile: (1) báo cáo chờ ảnh, (2) lấy GPS sớm và quản lý vòng đời, (3) đánh giá chất lượng GPS, (4) danh sách GPS offline. Không thay API, quyền, trạng thái BE hoặc màn Web. Không thay luồng survey, inspection, repair. Không sửa trong PR #172; triển khai bằng nhánh/PR tiếp theo từ dev sau khi #172 được merge, hoặc dùng nhánh phụ thuộc và ghi rõ nếu được yêu cầu triển khai sớm.

## Đồng bộ

Tại lúc nộp, ghi trong một Room transaction: snapshot tọa độ, liên kết ảnh theo pole_id và các operation upload/complete. Complete phải phụ thuộc đầy đủ các ảnh thuộc báo cáo. Ảnh retryable tiếp tục chờ, không tiêu hao số lần retry của complete; ảnh failed/conflict thì báo cáo không được gửi lên BE. Dùng cơ chế phục hồi/retry hiện có, không tự bỏ ảnh để đóng lệnh.

Snapshot tọa độ nằm trong payload complete cục bộ, không đổi JSON API. Retry không lấy lại tọa độ mới từ bảng đang chỉnh sửa. Payload GPS cũ chưa có dependency phải được nâng cấp từ dữ liệu cục bộ trước khi gửi. Nếu không đủ bằng chứng để nối tọa độ với ảnh hợp lệ, giữ dữ liệu, chặn gửi và hướng dẫn phục hồi; không xoá bản ghi để lách lỗi. Báo cáo đã synced không bị gửi lại hay hủy.

## Lấy GPS và chất lượng

Đề xuất kỹ thuật, chưa phải tiêu chuẩn nghiệp vụ đã được nhóm phê duyệt:

- Bắt đầu theo dõi khi có quyền vị trí chính xác, trước khi mở camera. Khi quay về/nhấn dùng ảnh, kiểm tra mẫu mới nhất; không có mẫu hợp lệ thì chờ tối đa 10 giây. Dừng khi rời route, tránh nhiều subscription sau retry/recompose.
- Dùng timestamp thực của Location (`location.time`) cho captured_at, không thay bằng thời điểm callback. Dùng thời gian monotonic (`elapsedRealtimeNanos`) để tính tuổi mẫu, tránh thay đổi đồng hồ điện thoại.
- Mẫu hợp lệ: lat/lng hữu hạn trong phạm vi, accuracy hữu hạn và >= 0, tuổi mẫu 0…10 giây. Mẫu cũ/không hợp lệ không được lưu; GPS thiếu thì giữ ảnh cho thử lại hoặc bỏ.
- accuracy <= 20 m: đạt ngưỡng đề xuất. accuracy > 20 m: hiện sai số và cảnh báo; cho `Đo lại` hoặc `Vẫn lưu vị trí này` có xác nhận. Không đặt luật cứng loại bỏ GPS kém khi BE chưa có ngưỡng nghiệp vụ tương ứng.
- Hiện sai số/thời gian mẫu ở bước xác nhận và giá trị đã lưu trên dòng cột. Khi mẫu đổi, xác nhận sai số lớn phải gắn với đúng mẫu đang lưu.
- Giữ EXIF và payload đồng nhất tọa độ; ảnh/GPS vẫn được lưu local trước, không gọi API cập nhật GIS trực tiếp.

## Danh sách offline

Thêm cache GPS riêng trong Room, có ownerUserId; cache survey hiện tại không bị delete/replace bởi lần tải GPS. Lưu user_id đã được /auth/me xác thực cho phiên hiện tại; không phải gọi /auth/me thành công mới đọc được cache. Phiên đăng nhập mới/đăng xuất phải xóa identity cũ; refresh token cùng phiên không xóa identity. Không dùng tên hiển thị làm khóa tài khoản.

Khi có mạng, thay danh sách GPS của user đó bằng response thành công (kể cả response rỗng). Khi lỗi kết nối/tạm thời, dùng cache cùng user và gắn isStale; 401/403 không được giả thành cache mới hoặc giữ quyền thao tác. Cache rỗng offline hiện thông báo rõ chưa có dữ liệu đã lưu. Một nguồn survey lỗi không làm mất nguồn GPS có cache, và ngược lại. Tải đủ các trang trước khi thay cache.

Danh sách không có nghĩa toàn bộ chi tiết/PDF đã tải. Hiện hướng dẫn mở lệnh và tải PDF trước khi đi hiện trường; trạng thái cache không cấp quyền start offline. Giữ việc start cần mạng như hiện tại.

## Tiêu chí nghiệm thu

Ảnh lỗi không thể làm task thành done; retry dùng đúng snapshot. Lấy GPS có vòng đời rõ, không nhận mẫu cũ và hiển thị sai số; mẫu sai số lớn cần người dùng xác nhận. Bật máy bay/mở lại tab vẫn thấy task GPS đã cache đúng tài khoản và cảnh báo dữ liệu cũ. Migration giữ ảnh, phép đo và hàng đợi đang có. Duyệt/return/nhập GIS vẫn do BE/Web quản lý.

## Giới hạn kiểm chứng

Unit/instrumentation dùng fixture không chứng minh luồng backend thật. Test Web → Mobile → Web và ghi dữ liệu test chỉ thực hiện khi có task/tài khoản test và quyền phù hợp; không chạy BE migration/test hoặc sửa BE/Web trong phạm vi này.
