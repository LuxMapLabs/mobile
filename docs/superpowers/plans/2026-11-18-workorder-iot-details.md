# WorkOrder IoT Details Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hiển thị rõ thiết bị cần kiểm tra/sửa chữa cho sự cố IoT, dùng dữ liệu BE đã cung cấp.

**Architecture:** Bổ sung metadata nullable vào DTO và domain model, truyền qua mapper hiện có. Mở rộng dòng sự cố dùng chung cho Inspection và Repair, giữ nguyên luồng báo cáo và đồng bộ.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, JUnit, Gradle; JDK 17, Android SDK 35.

**Spec:** Phạm vi 3 việc đã được người dùng chọn trong chat; thiết kế và tiêu chí cụ thể dưới đây. Tài liệu đối chiếu: `E:/HUONG_DAN_TEST_IOT_MOBILE.md`; source tham chiếu chỉ đọc: `../luxmap_backend/src/LuxMap.Modules.WorkOrders/WorkOrderResponses.cs`.

## Thiết kế đề xuất

- Dùng metadata riêng của từng Fault để hiển thị thông tin thiết bị; không phân tích chuỗi title/note.
- Đặt thông tin ngay trong dòng sự cố hiện có để nhiều Fault trong một WorkOrder không bị lẫn thiết bị. Áp dụng cả màn kiểm tra và sửa chữa, mọi trạng thái xem/sửa hiện có.
- Ví dụ nội dung: `Điện áp bất thường`, `Tủ điện: CAB-xxx`, `Node: NODE-xxx`, `Mạch: FDR-xxx`, `Mã lỗi: voltage_loss`, rồi mã Fault hiện có. Đây là ví dụ bố cục, không phải dữ liệu mặc định.
- Metadata thiếu/null/rỗng: ẩn dòng tương ứng; không suy diễn từ loại lỗi, không thêm mã giả. Nếu không có metadata IoT, giữ nội dung cột đèn/Fault hiện tại.
- Giữ mã anomaly nguyên văn vì chưa có bộ nhãn nghiệp vụ được chốt cho mọi mã. Chỉ dịch hai fault_type đã xác nhận.

## Global Constraints

- Chỉ thay đổi repo mobile. Tuyệt đối không sửa, thêm, xóa file hoặc chạy thao tác ghi dữ liệu ở BE.
- Phạm vi gồm metadata, hiển thị thiết bị và hai nhãn lỗi. Không thay đổi dẫn đường, API complete, điều kiện nộp, ảnh, vật tư hay trạng thái nghiệp vụ.
- Dùng component, semantic theme token, typography và spacing hiện có; tuân thủ `docs/design-system/vNext/` theo AGENTS.md.
- Không thêm dependency hoặc bảng Room; cache DTO hiện tại được giữ tương thích bằng default null.
- Kotlin bốn dấu cách, tối đa 120 ký tự mỗi dòng, newline cuối file.

## Review Focus

- Response cũ/cache thiếu cả bốn field: vẫn deserialize và mở được chi tiết (Task 1).
- Metadata chỉ có một phần hoặc chuỗi trắng: không hiện dòng trống hay chữ null (Task 2).
- Nhiều Fault: mỗi dòng hiển thị đúng thiết bị của Fault đó (Task 2).
- Fault cột đèn thông thường và loại lỗi chưa biết: giữ thông tin hiện có và fallback nhãn (Tasks 2, 3).
- Mã thiết bị dài, font lớn, Light/Night: không cắt mất thông tin và không che nút bản đồ/kết quả (Task 2, kiểm tra UI).

## Task 1: Nhận và ánh xạ metadata IoT

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderDetailMappingTest.kt`
- Create test: `app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderIotDtoTest.kt`

**Interfaces:** Thêm vào cuối constructor `WorkOrderFaultDetailDto` và `WorkOrderFaultDetail`: `originNodeId`, `feederId`, `cabinetId`, `iotAnomalyCode`, đều `String? = null`. SerialName lần lượt `origin_node_id`, `feeder_id`, `cabinet_id`, `iot_anomaly_code`; mapper giữ nguyên giá trị.

- [ ] Viết test JSON có đủ metadata và pole_id null: assert bốn giá trị được deserialize/map chính xác, tọa độ không thay đổi.
- [ ] Viết test response cũ thiếu metadata và response có null: assert bốn giá trị null, mapping vẫn thành công.
- [ ] Chạy test mới, xác nhận fail do thiếu field; triển khai DTO/model/mapper tối thiểu.
- [ ] Chạy ` .\gradlew.bat testLocalDebugUnitTest --tests 'com.luxmap.feature.workorder.data.WorkOrderIotDtoTest' --tests 'com.luxmap.feature.workorder.data.WorkOrderDetailMappingTest'` (bỏ khoảng trắng đầu lệnh); kỳ vọng BUILD SUCCESSFUL, không failure.

## Task 2: Hiển thị metadata trong dòng sự cố dùng chung

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/FaultList.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultSupportingText.kt`
- Create test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultSupportingTextTest.kt`
- Modify previews: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt`

**Interfaces:** `internal fun faultSupportingText(fault: WorkOrderFaultDetail): String`. Dùng metadata từ Task 1; trả các dòng không rỗng theo thứ tự Tủ điện, Node, Mạch, Mã lỗi, rồi poleId (nếu có) và faultId. Prefix chính xác: `Tủ điện: `, `Node: `, `Mạch: `, `Mã lỗi: `. Trim metadata khi hiển thị.

- [ ] Viết test metadata đầy đủ: assert chuỗi có đúng thứ tự/prefix và mã Fault; test thiếu một phần/chuỗi trắng: chỉ có dòng có dữ liệu.
- [ ] Test Fault thông thường: supporting text giữ poleId và faultId như cũ; test hai Fault khác nhau: không lẫn metadata.
- [ ] Chạy test để thấy fail; triển khai helper và thay supporting text của FaultItem bằng helper. Không thay icon hay callback điều hướng ở task này.
- [ ] Chạy ` .\gradlew.bat testLocalDebugUnitTest --tests 'com.luxmap.feature.workorder.ui.detail.WorkOrderFaultSupportingTextTest'` (bỏ khoảng trắng đầu lệnh); kỳ vọng BUILD SUCCESSFUL.
- [ ] Thêm preview Inspection/Repair có IoT và dữ liệu thiếu một phần. Kiểm tra LuxListItem có giới hạn maxLines; nếu có, cho phép nội dung metadata hiển thị hết tại FaultItem mà không thay mặc định các màn khác.
- [ ] Kiểm tra Light/Night, font scale 1.0 và 2.0, mã dài, hai Fault; thông tin đọc đủ, nút bản đồ và lựa chọn kết quả vẫn thao tác được. Chụp screenshot khi có emulator/device.

## Task 3: Nhãn lỗi tiếng Việt

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultLabels.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderFaultLabelsTest.kt`

**Interfaces:** Giữ `fun faultTypeLabel(value: String): String`; thêm `voltage_anomaly` -> `Điện áp bất thường`, `iot_hardware_fault` -> `Lỗi phần cứng IoT`. Giá trị chưa biết trả nguyên văn. Giữ icon ReportProblem hiện tại.

- [ ] Thêm assertions cho hai nhãn trên và fallback mã chưa biết; chạy test và xác nhận fail cho hai nhãn mới.
- [ ] Bổ sung hai nhánh mapping, chạy ` .\gradlew.bat testLocalDebugUnitTest --tests 'com.luxmap.feature.workorder.ui.detail.WorkOrderFaultLabelsTest'` (bỏ khoảng trắng đầu lệnh); kỳ vọng BUILD SUCCESSFUL.

## Kiểm chứng cuối

- [ ] Chạy `.\gradlew.bat ktlintCheck testLocalDebugUnitTest assembleServerDebug`; tất cả phải BUILD SUCCESSFUL. Báo rõ lỗi môi trường nếu không chạy được.
- [ ] Test thực tế khi có URL, tài khoản test và WorkOrder IoT: nhận việc không có cột, kiểm tra metadata và tọa độ, chọn fault_present, thêm ảnh/ghi chú, đồng bộ; Manager duyệt và giao Repair; kiểm tra metadata trên Repair, gửi vật tư và ảnh sau sửa. Cần người dùng cho phép riêng thao tác ghi trên hệ thống test; bước này không thuộc kiểm tra source BE chỉ đọc.
- [ ] Test thêm fault_absent trên case riêng; metadata thiếu một phần; Fault cột đèn thông thường. Phân biệt kiểm chứng source/unit test với kết quả chạy tích hợp thật.
- [ ] Review diff để xác nhận chỉ có thay đổi mobile trong phạm vi; cung cấp ảnh UI và kết quả kiểm chứng. Commit/PR khi người dùng yêu cầu, theo Conventional Commits và target dev.

## Tiêu chí hoàn tất

Kỹ sư nhìn từng Fault biết đúng tủ, Node, mạch và mã anomaly mà không phụ thuộc title/note; hai loại lỗi có nhãn tiếng Việt; response cũ và Fault cột đèn vẫn hoạt động; kiểm tra/sửa chữa tiếp tục dùng luồng báo cáo hiện tại. Không coi bước tích hợp thực tế đã pass khi mới có unit test.

## Tiến độ triển khai 2026-11-18

- Task 1: đã thêm bốn field nullable/default null và mapper. Test mới ban đầu không compile do các field chưa tồn tại; sau bổ sung, test deserialize/map và tương thích dữ liệu cũ đã pass trong lượt test mục tiêu.
- Task 2: đã thêm helper supporting text và nối vào FaultItem dùng chung. Test trước triển khai phát hiện helper chưa tồn tại; sau triển khai, bốn test nội dung đã pass trong lượt test mục tiêu.
- Task 3: test nhãn đã fail với nhãn mã gốc trước khi thêm hai bản dịch. Đã bổ sung mapping.
- Thêm supportingMaxLines (default 2) vào LuxListItem; chỉ FaultItem dùng Int.MAX_VALUE để không cắt metadata. Không thay giới hạn mặc định ở các màn khác.
- Đã thêm preview Inspection/Repair, mã dài, metadata thiếu một phần, font 2.0 và Night với LuxMapTheme.
- Review độc lập không phát hiện lỗi chức năng; hai góp ý về định dạng và theme preview đã xử lý.
- Thực hiện trong checkout hiện tại theo hướng dẫn workspace; không tạo branch/worktree, commit hoặc PR. Không thao tác ghi ở BE.
- Chưa xác minh UI thực tế và luồng tích hợp bằng WorkOrder IoT thật. Có thiết bị ADB kết nối sau khi chạy ngoài sandbox, nhưng chưa cài APK hoặc thao tác dữ liệu trên thiết bị.
- Kiểm chứng cuối: ktlintCheck, testLocalDebugUnitTest (531 tests, 0 failures, 0 errors), assembleServerDebug và git diff --check đều pass. APK: app/build/outputs/apk/server/debug/app-server-debug.apk.
- Kiểm chứng thiết bị: SM-A075F / Android 16; đã cập nhật APK server, tài khoản crew không có việc được giao. 4 Compose instrumentation tests cho Inspection/Repair Light và Night font 2.0 pass trên điện thoại; kiểm tra text layout không overflow, callback tọa độ và fault_present. Screenshot tại build/iot-device-verification/files/. ktlintAndroidTestSourceSetCheck pass. Chưa test tích hợp BE bằng WorkOrder thật; không gửi báo cáo/đổi trạng thái trên BE.
