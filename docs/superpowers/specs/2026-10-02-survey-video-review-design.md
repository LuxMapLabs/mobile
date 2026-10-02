# Xem lại video khảo sát trước khi nộp (F05 reinterpreted) — Design Spec

Ngày viết: 2026-10-02
Tác giả: Claude Code (phối hợp với thinh2509, vai trò WP6)
Trạng thái nguồn tham chiếu: `CLAUDE.md` (cấu trúc thư mục `feature/survey/ui/coverage/` dành cho F05), `docs/superpowers/specs/2026-09-28-survey-capture-design.md` (mục 1 đã liệt kê F05 là khoảng trống ngoài phạm vi đợt trước), bộ nhớ phiên làm việc `project_f05_coverage_spec_stale.md` (đặc tả F05 gốc mô tả UI ảnh rời, không khớp luồng quay video hiện tại).

## 1. Bối cảnh và phạm vi

Sau khi dừng quay ở F04, video + track GPS/heading + log lux đã nằm trên máy (chưa upload). Luồng hiện tại (`CaptureScreen.onSessionPackaged`) điều hướng thẳng sang F06 (`SurveySubmit`) để nộp — không có bước nào cho Field Engineer xem lại video trước khi nộp.

**Trong phạm vi:**
- 1 màn hình mới cho Field Engineer phát lại video (nối liền các segment) ngay sau khi dừng quay.
- Nút "Quay lại": xoá phiên vừa quay (video + mọi log đi kèm + row Room), quay về F04 bắt đầu phiên mới cho cùng tuyến khảo sát.
- Nút "Nộp": đi tiếp sang F06 như hành vi hiện tại.
- Thêm thư viện Media3 ExoPlayer vào ngăn xếp công nghệ (đã được chủ dự án đồng ý 2026-10-02).

**Ngoài phạm vi (giữ nguyên là khoảng trống):**
- % độ phủ tuyến theo track GPS, kiểm tra tính toàn vẹn log lux/GPS, cảnh báo thiếu dữ liệu — đây là nội dung F05 gốc trong đặc tả chi tiết, **không** nằm trong đợt này. Chủ dự án xác nhận 2026-10-02: F05 bản này chỉ cần xem lại video thô, không cần các nội dung trên.
- Review/so sánh nhiều phiên cùng lúc, chỉnh sửa/cắt video — không có trong nghiệp vụ hiện tại.

**Ghi chú lệch đặc tả (sẽ đưa vào `docs/contract-drift.md`):** màn hình này dùng đúng vị trí trong luồng và đúng thư mục code (`feature/survey/ui/coverage/`) mà CLAUDE.md đã quy hoạch cho F05, nhưng nội dung không phải "Kiểm tra độ phủ & chất lượng" như tên gốc — chỉ là xem lại video. Tên hiển thị/route nên phản ánh đúng việc nó làm (ví dụ "Xem lại video"), không dùng từ "độ phủ"/"coverage" trong UI để tránh hiểu nhầm, dù tên thư mục code vẫn là `coverage/` cho khớp cấu trúc đã tài liệu hoá.

## 2. Kiến trúc & vị trí trong luồng

```
CaptureScreen (F04, dừng quay xong)
  → onSessionPackaged(sessionId)
        │
        ▼
  SurveyReview(sessionId)   ◄── MÀN MỚI (feature/survey/ui/coverage/)
        │
        ├─ "Nộp"      → SurveySubmit(sessionId), popUpTo(Routes.Survey.route)
        │                 (giữ nguyên hành vi điều hướng hiện có của onSessionPackaged)
        │
        └─ "Quay lại" → SurveyCapture(surveySweepId), popUpTo(Routes.Survey.route)
                          (surveySweepId lấy lại từ Room qua sessionId, không cần
                          người dùng chọn lại tuyến ở F03)
```

Thành phần mới:
- `feature/survey/ui/coverage/CoverageScreen.kt` — 1 `@Composable`, chỉ vẽ UI (player + 2 nút + dialog xác nhận).
- `feature/survey/ui/coverage/CoverageViewModel.kt` — độc lập hoàn toàn với `CaptureViewModel` (không tái sử dụng, không đụng vào camera/BLE/GPS đang chạy ở F04, vì lúc này phiên quay đã kết thúc).
- `feature/survey/ui/coverage/CoverageUiState.kt` — sealed interface theo đúng khuôn 4 trạng thái bắt buộc.
- `navigation/Routes.kt` — thêm `Routes.SurveyReview` (path param `sessionId`, cùng khuôn với `Routes.SurveySubmit`).
- `navigation/NavGraph.kt` — sửa đích đến của `onSessionPackaged` (từ `SurveySubmit` sang `SurveyReview`), thêm composable mới cho `SurveyReview`.

`CoverageScreen` chặn nút Back hệ thống (giống cách `CaptureScreen` đã chặn Back trong lúc quay) — không cho rời màn mà chưa chọn "Nộp" hay "Quay lại". Không có chặn này, người dùng có thể thoát ngang bằng Back, để lại 1 session đã đóng gói nhưng không ai quyết định số phận (không nộp, không xoá) — chiếm dung lượng máy vĩnh viễn, không có màn nào khác hiển thị lại nó để xử lý tiếp.

## 3. Data flow — phát video

`SurveyRepository` hiện tại chỉ có 1 hàm (`observeAssignedRoutes()`, dữ liệu tuyến từ server — lý do có Fake/Real là để giả lập mạng chưa sẵn backend). Việc đọc danh sách segment và xoá phiên ở đây là dữ liệu **cục bộ thuần tuý** (không gọi mạng), nên thêm 2 hàm mới vào cùng interface này (đúng nguyên tắc "1 feature = 1 interface Repository"), nhưng `FakeSurveyRepository` và `RealSurveyRepository` **cài đặt giống hệt nhau** cho 2 hàm này — cả hai cùng đọc/ghi Room và file hệ thống thật, không có gì để giả lập mạng (cùng tinh thần "Fake vẫn ghi Room thật" đã ghi trong CLAUDE.md mục "Tầng API").

- `CoverageViewModel` gọi `SurveyRepository.segmentFilePathsFor(sessionId): List<String>` (hàm mới) — bên trong đọc `SurveySessionDao.segmentsFor(sessionId)` (đã có sẵn, trả về đúng thứ tự `segmentIndex ASC`).
- Build danh sách `MediaItem` theo đúng thứ tự segment (mỗi `MediaItem.fromUri(File(path).toUri())`), gọi `exoPlayer.setMediaItems(list)` — Media3 tự chuyển tiếp liền mạch giữa các item trong playlist (không cần `ConcatenatingMediaSource` của API cũ).
- UI bọc `PlayerView` (Media3) qua `AndroidView`, theo đúng khuôn mẫu `CaptureScreen` đang bọc `TextureView` (factory 1 lần, giữ nguyên instance qua recomposition).
- `ExoPlayer` instance sống trong `CoverageViewModel`, giải phóng (`player.release()`) ở `onCleared()`.

## 4. Luồng "Quay lại" (xoá phiên + điều hướng)

Thêm vào `SurveyRepository` (interface + cả 2 cài đặt `Fake`/`Real`):
```kotlin
// Returns the surveySweepId so the caller can re-enter F04 for the same route.
suspend fun discardSession(sessionId: String): String
```

Cài đặt (giống hệt nhau ở cả `FakeSurveyRepository` và `RealSurveyRepository`, lý do xem mục 3):
1. Đọc `local_survey_session` lấy `surveySweepId` (lỗi nếu không tìm thấy — sessionId phải luôn hợp lệ ở bước này).
2. Xoá nguyên thư mục `survey/<sessionId>/` trên app-specific storage — mọi file của phiên (`segment_*.mp4`, `gps_track.ndjson`, `heading_log.ndjson`, `lux_log.ndjson`, `frame_timestamp_log.ndjson`, `capture_config.json`, `manifest.json`) đều nằm chung thư mục này (xem `SurveyCaptureService.kt:203`), nên xoá thư mục là đủ, không cần liệt kê từng file.
3. Xoá row Room: `local_survey_video_segment` (theo `sessionId`) rồi `local_survey_session` — DAO method mới, bọc `@Transaction` để 2 bảng luôn nhất quán.
4. Nếu bước 2 (xoá file) thất bại (lỗi I/O), **không chặn** bước 3 — log lại và tiếp tục, giống cách `VideoCaptureSession.releaseCaptureResources()` xử lý từng bước độc lập hiện có trong codebase. Mục tiêu: Field Engineer luôn quay lại được, file rác (nếu có) là vấn đề dọn dẹp sau, không phải thứ chặn công việc hiện trường.

UI: `AlertDialog` xác nhận trước khi gọi `discardSession` ("Xoá video này và quay lại?") — hành động không hoàn tác, theo đúng nguyên tắc chung của dự án về xác nhận trước hành động phá huỷ.

## 5. `CoverageUiState` — 4 trạng thái bắt buộc

```kotlin
sealed interface CoverageUiState {
    data object Loading : CoverageUiState
    data class Success(
        val segmentFilePaths: List<String>,
        val playerErrorMessage: String? = null,
    ) : CoverageUiState
    data object Empty : CoverageUiState
    data class Error(val message: String) : CoverageUiState
}
```

- `Loading`: đang đọc danh sách segment từ Room.
- `Success`: có ít nhất 1 segment hợp lệ, sẵn sàng phát. `playerErrorMessage` là cờ cảnh báo inline (không phải state riêng) khi 1 file segment bị mất/hỏng trên đĩa dù Room vẫn còn row — theo đúng tiền lệ `Recording` đang mang `gpsSignalLost`/`bleGapDetected` làm cờ cảnh báo thay vì bịa thêm state. Khi có lỗi này, vẫn cho phép "Quay lại" bình thường.
- `Empty`: phiên không còn segment nào hợp lệ trong Room (trường hợp hiếm — ví dụ luồng khôi phục sau crash ở mục 12 của spec F04 loại bỏ hết segment dở). Chỉ hiện nút "Quay lại", ẩn nút phát và nút "Nộp" (không có gì để nộp).
- `Error`: không đọc được session từ Room (sessionId sai, hỏng dữ liệu) — hiếm, vì sessionId luôn do chính `CaptureScreen` vừa tạo truyền sang.

## 6. Testing

- `CoverageViewModel`: unit test JUnit + MockK, theo đúng khuôn `CaptureViewModelTest` đã có — mock `SurveyRepository`, phủ đủ 4 nhánh `CoverageUiState` + hành vi gọi `discardSession` (đúng tham số, đúng giá trị `surveySweepId` trả về để điều hướng).
- `PlayerView`/`ExoPlayer` thật: **không có test tự động** — cùng tiền lệ Camera2/`MediaCodec`/UI glue trong `VideoCaptureSession`/`CaptureScreen` đã không test tự động được trong dự án này (không có Robolectric, không mock được Android framework type trên JVM thường). Xác minh bằng tay trên máy thật: phát hết các segment liên tục không giật/ngắt ở ranh giới file, nút "Quay lại" xoá đúng xoá đủ (kiểm tra thư mục `survey/<sessionId>/` không còn trên máy qua `adb shell`), nút "Nộp" vẫn vào đúng F06 như cũ.

## 7. Việc kỹ thuật kèm theo (không phải code tính năng)

- Thêm `androidx.media3:media3-exoplayer`, `androidx.media3:media3-ui`, `androidx.media3:media3-common` vào `gradle/libs.versions.toml` — 1 commit `chore` riêng tách khỏi code tính năng, theo đúng quy ước commit của dự án (ví dụ `chore(fm-XX): add media3 exoplayer dependency`).
- Đã tra `docs/LuxMap_TaskList_v2.xlsx` (sheet `Frontend-Mobile`, 2026-10-02): không có mã nào mô tả đúng "xem lại video + quay lại nếu không ưng". Gần nhất là **FM-11** ("Kiểm tra độ phủ và chất lượng ảnh trước khi nộp sweep — Cảnh báo ngay tại xe nếu đoạn nào thiếu SurveyFrame hoặc ảnh mờ") — đúng vị trí trong pipeline (ngay trước khi nộp sweep) nhưng viết theo mô hình ảnh rời cũ. Chủ dự án xác nhận 2026-10-02: **dùng FM-11**, coi là tái định nghĩa cho video (cùng tinh thần tái định nghĩa F05 ở mục 1).
- Cập nhật `docs/contract-drift.md`: ghi lại việc tái sử dụng slot F05/`coverage/` cho nội dung khác với đặc tả gốc (mục 1 ở trên).

## 8. Không thuộc phạm vi / cần xác nhận thêm sau này

- Nội dung "kiểm tra độ phủ & chất lượng" thật sự (F05 gốc) vẫn là khoảng trống nghiệp vụ thật, không được coi là đã giải quyết bởi spec này.
- Giới hạn số lần "Quay lại" cho 1 tuyến (nếu có) — chưa có yêu cầu nào về việc này, spec này không giới hạn.
