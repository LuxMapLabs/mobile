# Ghi chú bảo mật Auth — gửi nhóm Backend

Nguồn: rà soát source `luxmap_backend` (chỉ đọc, không chạy code) khi audit phần Login của app mobile
(FM-05). Phạm vi: `AuthController`, `AuthService`, `JwtOptions`. Mobile không sửa gì ở backend.

## Cần backend xem xét

### 1. Medium — Đăng nhập không giới hạn số lần thử
- `POST /api/v1/auth/login` là `[AllowAnonymous]`, không có rate limit và không có bộ đếm lần sai.
  `IsLocked` chỉ là cờ do quản trị đặt (`AuthService.cs:56`).
- Tác động: brute-force mật khẩu không bị chặn.
- Gợi ý: rate limit theo IP và theo username (ASP.NET Core `AddRateLimiter`), hoặc khoá tạm sau
  N lần sai liên tiếp.

### 2. Medium (ảnh hưởng vận hành) — Mất phản hồi refresh làm mobile bị đăng xuất
- `RotateAsync` thu hồi token cũ ngay khi xoay. Nếu response chứa token mới bị mất (mạng yếu ở
  hiện trường), mobile chỉ còn token cũ.
- Gửi lại token cũ: trong 30 giây grace (`ReuseGraceSeconds`) server chỉ bỏ qua rồi trả 401
  `InvalidRefreshToken`; sau 30 giây server còn huỷ cả chain (`ReuseDetected`).
- Kết quả: mobile nhận 401 và phải đăng xuất, dù người dùng không làm gì sai. Mobile đã giữ
  session khi lỗi mạng nhưng không thể tự cứu trường hợp này.
- Gợi ý: trong grace window, trả lại đúng token mới đã cấp cho request gửi lại (refresh idempotent),
  hoặc kéo dài grace window.

### 3. Low — Dò username qua thời gian phản hồi
- Username không tồn tại: trả 401 ngay, không băm mật khẩu (`AuthService.cs:42-46`). Username tồn
  tại: phải verify hash nên chậm hơn.
- Chưa đo thực tế nên chưa biết độ chênh có khai thác được không.
- Gợi ý: verify với một hash giả khi không tìm thấy user.

### 4. Low — `POST /auth/register` mở, anonymous
- Ai cũng tạo được tài khoản `FieldCrew` (không có commune nên không thấy dữ liệu). Nguy cơ spam tài
  khoản; phản hồi 409 cho biết username hoặc email đã tồn tại.
- App mobile không dùng endpoint này (Field Crew được cấp sẵn tài khoản). Cần xác nhận đây là chủ ý.

### 5. Low — Tài khoản bị khoá vẫn dùng được access token cũ
- Access token sống 60 phút và kiểm tra không cần truy vấn DB. Login và refresh thì bị chặn ngay.
- Có thể chấp nhận nếu 60 phút là rủi ro chấp nhận được; cần quyết định.

## Đã kiểm tra, không có vấn đề
- Logout thu hồi refresh token trên server, idempotent (`LogoutAsync`).
- Refresh token xoay vòng, có phát hiện dùng lại, xử lý concurrent refresh bằng conditional UPDATE.
- Kiểm tra `IsLocked` sau khi verify mật khẩu, nên `ACCOUNT_LOCKED` không lộ tài khoản tồn tại.
- Log không ghi mật khẩu; refresh token được lưu dạng hash.

## Cần xác nhận ngoài source (người phụ trách deploy)
- Môi trường thật dùng HTTPS (`UseHttpsRedirection` đã bật ở `Program.cs:101`), cấu hình CORS.
- `JWT_SIGNING_KEY` được lưu và xoay vòng ở đâu.
- Mobile release sẽ trỏ tới URL HTTPS nào (mobile cần biết để đặt `API_BASE_URL`).
