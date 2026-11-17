# Kế hoạch nhiều ảnh cho lệnh sửa chữa và kiểm tra

> Khi triển khai, dùng skill executing-plans hoặc subagent-driven-development theo cách đã thống nhất. Các ô kiểm dùng để theo dõi từng bước.

**Mục tiêu.** Cho phép đính kèm nhiều ảnh sau sửa chữa và thêm ảnh quan sát vào lệnh kiểm tra. Người dùng vẫn nộp được khi offline.

**Thiết kế.** Hai loại lệnh dùng chung giao diện thêm ảnh từ camera hoặc thư viện. Ảnh mới được lưu nháp trong Room. Khi nộp, app chốt danh sách ảnh, xếp từng lượt tải lên và chỉ gửi yêu cầu hoàn thành sau khi các ảnh trong danh sách đã đồng bộ.

**Công nghệ.** Kotlin, Jetpack Compose, Room, WorkManager, Retrofit, camera hệ thống và Android Photo Picker.

**Tham chiếu.** Xem mục quy tắc nghiệp vụ và BE-24/EV-1 trong [CLAUDE.md](../../../CLAUDE.md). Điều kiện hoàn thành và giới hạn tải ảnh đã đối chiếu với `WorkOrderService.cs` và `WorkOrderEvidence.cs` trong repo backend kế bên.

## Phạm vi và quyết định

- Sửa chữa cần ít nhất một ảnh `after` và ghi chú tối thiểu 10 ký tự sau khi trim. Kiểm tra có thể nộp với 0 hoặc nhiều ảnh `observation`, vẫn cần ghi chú và kết quả cho từng sự cố được liên kết.
- Người dùng có thể chụp từng tấm hoặc chọn nhiều ảnh một lượt từ thư viện, kết hợp hai nguồn trong cùng danh sách và bỏ từng ảnh nháp trước khi nộp. Giới hạn chọn mỗi lượt theo trình chọn hệ thống. Người dùng có thể mở lại trình chọn để thêm ảnh. API nhận một JPEG tối đa 16 MiB mỗi lần và dùng `client_op_id` để tránh tạo ảnh trùng khi thử lại.
- Với ảnh thư viện, ưu tiên thời gian chụp có múi giờ và GPS hợp lệ từ EXIF. Nếu thiếu thời gian dùng được, ghi thời điểm nhập và cho biết đây là thời điểm thay thế. Nếu thiếu GPS, cho người dùng xác nhận dùng vị trí hiện tại cho các ảnh đó. Nếu chưa lấy được vị trí, báo ảnh nào chưa thể đính kèm và giữ các ảnh đã thêm thành công. API hiện yêu cầu `captured_at`, `lat` và `lng`.
- Dùng bảng `local_work_order_evidence` hiện có, mỗi ảnh một `clientOpId`. Dùng chung endpoint `POST /api/v1/work-orders/{id}/evidence`. Định dạng API hoàn thành giữ như hiện tại.
- Chọn lưu nháp đến lúc nộp để người dùng có thể bỏ ảnh trước khi ảnh lên server. Cách gửi ngay khi chụp tận dụng cơ chế cũ, nhưng cần xử lý riêng ảnh đã gửi nếu người dùng muốn bỏ ảnh.
- Giữ các ảnh `pending` và `synced` từ bản hiện hành. Đọc được payload nộp cũ khi app nâng cấp. Luồng khảo sát và cập nhật GPS tiếp tục dùng cấu trúc hàng đợi hiện có.

## 1. Lưu và đọc danh sách ảnh

**File.** `feature/workorder/data/dao/WorkOrderCompletionDao.kt`, `WorkOrderCompletionRepository.kt`, `RealWorkOrderCompletionRepository.kt` và các test DAO/repository tương ứng. Đường dẫn mã ứng dụng tính từ `app/src/main/java/com/luxmap/`.

- [ ] Thêm test DAO ghi hai ảnh cho một lệnh, một ảnh cho lệnh khác rồi kiểm tra danh sách đúng lệnh và đúng thứ tự `capturedAt DESC, clientOpId DESC`. Thêm test repository cho hai loại ảnh và kiểm tra chụp nháp chưa tạo lượt tải lên.
- [ ] Thêm `observeEvidenceList(workOrderId: String): Flow<List<LocalWorkOrderEvidenceEntity>>`, `captureEvidence(workOrderId, kind, clientOpId, filePath, lat, lng, capturedAt)` và `removeDraftEvidence(workOrderId: String, clientOpId: String)`. Dùng kiểu enum cho `kind` với hai giá trị wire `after` và `observation`. Lưu nháp với trạng thái `draft`. Chỉ xoá bản ghi đúng lệnh còn là nháp và chưa được nộp. Xoá file sau khi xoá bản ghi thành công.
- [ ] Giữ API ảnh đơn đến khi chuyển xong các caller. Test xác nhận bỏ một ảnh không ảnh hưởng ảnh khác và ảnh đã vào hàng đợi vẫn giữ file.

## 2. Đồng bộ ảnh trước khi hoàn thành

**File.** `RealWorkOrderCompletionRepository.kt`, `data/sync/WorkOrderSyncPayloads.kt`, `data/sync/CompleteWorkOrderSyncHandler.kt`, `data/sync/UploadWorkOrderEvidenceSyncHandler.kt`, `core/sync/SyncQueueManager.kt`, `SyncQueueDao.kt`, `SyncOpHandler.kt` và `SyncQueueProcessor.kt`.

- [ ] Viết test với hai ảnh trong đó ảnh thứ nhất tạm lỗi và ảnh thứ hai đã lên server. Xác nhận API hoàn thành chưa được gọi, kể cả khi ảnh thứ hai là ảnh mới nhất. Thêm ca kiểm tra không ảnh, hai ảnh đều thành công, lỗi vĩnh viễn, xung đột và hàng đợi cũ đang chờ từ bản trước.
- [ ] Bổ sung trường `evidenceClientOpIds: List<String>? = null` vào `CompletionSyncPayload`. Khi nộp sửa chữa/kiểm tra, chốt ID các ảnh đúng loại theo lệnh. Trong một transaction Room, tạo bản ghi nộp, chuyển nháp sang `pending`, tạo một lượt tải cho mỗi ảnh nháp và tạo lượt hoàn thành. Tái sử dụng lượt tải có sẵn theo `clientOpId`. Gọi trigger sau khi transaction commit. Hai lần bấm nộp không được tạo hai lượt hoàn thành.
- [ ] Trong handler hoàn thành, kiểm tra trạng thái của các ID đã chốt. Ảnh đã `synced` hoặc lượt tải `done` được tính là đã gửi. Nếu còn lượt tải `queued` hoặc `syncing`, trả kết quả chờ phụ thuộc để processor đưa lượt hoàn thành về `queued` và giữ nguyên số lần thử. Nếu ảnh bị lỗi, xung đột hoặc mất bản ghi, cập nhật trạng thái nộp tương ứng và dừng trước khi gọi API. Payload cũ có danh sách `null` tiếp tục dùng phụ thuộc cũ. Payload mới có danh sách rỗng được hoàn thành ngay.
- [ ] Kiểm thử ngay sát thứ tự thực thi trong processor. Lượt hoàn thành chỉ gọi API sau khi đủ ảnh kể cả khi ảnh và lượt nộp được lấy cùng một lần quét. Ca mất mạng nhiều vòng không làm lượt hoàn thành hết số lần thử. HTTP 413 được ghi nhận là lỗi ảnh, tránh lặp lại cùng file quá cỡ.
- [ ] Kiểm tra file JPEG và dung lượng trước khi lưu nháp để người dùng có thể chụp lại ngay. Hiển thị ảnh nào lỗi và cho thử lại lỗi có thể phục hồi bằng cùng `clientOpId`. Khi thử lại, đưa ảnh lỗi có thể phục hồi và lượt hoàn thành bị chặn về hàng đợi cùng nhau. Với file mất, sai định dạng hoặc bị server trả 413, cho người dùng mở lại bản nộp để thay ảnh lỗi. Trong transaction, huỷ lượt hoàn thành đang bị chặn trước khi cho gửi lại, giữ ảnh đã đồng bộ và giữ nguyên ghi chú cùng kết quả kiểm tra. Xung đột cần thông báo riêng và chờ xử lý. Kiểm thử cả trường hợp nộp offline rồi mở lại app trước khi mạng trở lại.

## 3. Giao diện chụp và nộp

**File.** `feature/workorder/ui/detail/WorkOrderDetailUiState.kt`, `WorkOrderDetailViewModel.kt`, `WorkOrderDetailScreen.kt`, `WorkOrderCta.kt`, `sections/RepairSection.kt`, `sections/InspectionSection.kt`, `ui/completion/EvidenceCaptureViewModel.kt`, `EvidenceCaptureScreen.kt`, `navigation/Routes.kt` và `NavGraph.kt`. Các file section và capture nằm trong feature work order.

- [ ] Mở rộng test ViewModel và Compose cho sửa chữa có 0/1/3 ảnh, kiểm tra có 0/2 ảnh, bỏ một ảnh nháp, huỷ camera hoặc trình chọn, nộp offline và số đếm ảnh trong phần xác nhận nộp.
- [ ] Đổi `Success.localEvidence` thành `localEvidence: List<LocalWorkOrderEvidenceEntity>`. Sửa `canSubmit()` và CTA để đếm ảnh theo loại. Truyền loại ảnh qua route capture, mặc định `after` nếu route sửa chữa cũ thiếu tham số. Giữ ID riêng cho mỗi lần chụp qua tái tạo màn hình.
- [ ] Dùng chung phần ảnh ở hai lệnh. Sửa chữa hiện **Ảnh sau sửa chữa**, kiểm tra hiện **Ảnh hiện trường (tùy chọn)**. Nút **Thêm ảnh** mở hai lựa chọn **Chụp ảnh** và **Chọn từ thư viện**. Cả hai có thumbnail và xem ảnh. Ảnh nháp có **Bỏ ảnh**. Sau khi nộp, khoá chỉnh sửa và hiện trạng thái gửi ảnh hoặc lỗi. Dọn API ảnh đơn khi không còn caller.
- [ ] Dùng `ActivityResultContracts.PickMultipleVisualMedia` với `ImageOnly` và giữ quyền camera riêng cho thao tác chụp. Đọc EXIF từ ảnh gốc trước khi chuyển đổi. Sao chép từng URI vào file riêng của app, chuẩn hóa JPEG, giữ đúng hướng hiển thị và kiểm tra mức 16 MiB trên file đầu ra. Tái sử dụng logic phù hợp từ `feature/survey/ui/polephoto/GalleryImageCopy.kt` qua phần dùng chung. Xử lý từng file với bộ nhớ giới hạn để một ảnh lớn không làm hỏng cả lượt nhập.
- [ ] Chỉ ghi nháp khi file đã sẵn sàng để upload offline. Nếu một URI không đọc được hoặc chuyển đổi lỗi, dọn file tạm, báo số ảnh đã thêm và số ảnh lỗi, giữ các ảnh đã nhập được. Khóa **Nộp** trong lúc đang nhập. Kiểm thử chọn nhiều ảnh, JPEG và PNG, ảnh xoay theo EXIF, ảnh thiếu EXIF, lỗi một ảnh giữa lượt nhập và thumbnail còn đọc được sau khi mở lại app.
- [ ] Kiểm tra trên màn 360dp với chữ lớn. Nút thêm và bỏ ảnh có vùng chạm tối thiểu 48dp, ảnh và trạng thái có nhãn TalkBack.

## 4. Kiểm tra trước khi bàn giao

- [ ] Chạy `.\gradlew.bat ktlintCheck testLocalDebugUnitTest assembleLocalDebug`. Khi có thiết bị, chạy `.\gradlew.bat connectedLocalDebugAndroidTest`.
- [ ] Kiểm thử với backend local. Sửa chữa chụp ba ảnh, bỏ một ảnh, nộp khi tắt mạng, bật mạng và xác nhận server nhận đúng hai ảnh trước khi đóng lệnh. Kiểm tra chạy một lệnh có ảnh và một lệnh không ảnh. Xác nhận kết quả từng sự cố vẫn được lưu.
- [ ] Trên thiết bị, kết hợp một ảnh camera với hai ảnh thư viện trong cùng lệnh, bỏ một ảnh thư viện, mở lại app rồi đồng bộ. Xác nhận backend nhận đúng các ảnh còn lại với loại ảnh tương ứng cho cả sửa chữa và kiểm tra.
- [ ] Giả lập một lượt tải ảnh thất bại rồi thử lại. Xác nhận lệnh vẫn đang làm trong lúc thiếu ảnh và lần thử lại không tạo ảnh trùng. Kiểm tra hồi quy với ảnh đơn và lệnh nộp đang chờ từ bản trước, cùng luồng khảo sát và cập nhật GPS.
- [ ] Chia commit theo lưu ảnh, đồng bộ và giao diện. Giữ mỗi commit trong giới hạn của repo và chạy test liên quan sau mỗi bước.
