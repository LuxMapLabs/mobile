# Pole GPS Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Task đo GPS không mất ảnh khi đồng bộ, đo vị trí có chất lượng rõ ràng và vẫn xuất hiện khi offline.

**Architecture:** Dùng Room transaction và dependency ảnh của sync queue hiện có; mở rộng payload cục bộ để giữ snapshot tọa độ. Nối GPS observer vào route camera, thêm policy chất lượng và cache danh sách GPS theo user; giữ nguyên API BE/Web.

**Tech Stack:** Kotlin, Compose, Room, WorkManager, FusedLocationProvider, kotlinx.serialization, JUnit/MockK và Compose instrumentation; JDK 17, SDK 35.

**Spec:** `docs/superpowers/specs/2026-11-18-pole-gps-hardening-design.md` — đọc toàn bộ trước khi triển khai. Các ngưỡng GPS trong spec là đề xuất kỹ thuật để review.

## Global Constraints

- Chỉ sửa mobile; tuyệt đối không sửa/thêm/xóa source hoặc chạy tác vụ ghi ở BE/Web.
- Giữ task độc lập, partial report >= 1 cột, ghi chú >= 10 ký tự; Manager verify mới cập nhật GIS.
- Không thêm dependency, API, quét QR, hay tự tạo ngưỡng BE. Không đổi luồng survey/inspection/repair.
- GPS: timeout 10 giây; tuổi mẫu 0…10 giây; cảnh báo accuracy > 20 m và cho xác nhận lưu.
- Room hiện v22: cache mới dùng migration 22→23, bảo toàn các bảng/dữ liệu đang có. Không dựa vào destructive migration.
- Kotlin 4 spaces, 120 ký tự, newline cuối file; semantic tokens và component hiện có.
- PR #172 chỉ chứa thay đổi trước đó. Chưa triển khai GPS trong lượt lên kế hoạch này; triển khai sau trên nhánh riêng theo spec.

## Review Focus

- Upload ảnh lỗi nhưng complete được gửi: chặn complete và giữ snapshot (Task 1).
- Payload cũ hoặc app bị dừng giữa lúc nộp: phục hồi không mất ảnh, không gửi trùng (Task 1).
- Mẫu cũ, clock đổi, quyền bị thu hồi/camera đóng: không nhận GPS giả và không rò observer (Task 2).
- Sai số lớn, NaN, âm, hoặc mẫu đổi sau khi mở xác nhận: áp đúng policy cho đúng mẫu (Task 3).
- Offline, kết quả live rỗng, đổi user, hoặc survey lỗi: cache không rò tài khoản và không bị xóa nhầm (Task 4).

## Task 1: Chốt báo cáo GPS và dependencies ảnh trong một transaction

**Files — modify:**
- `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsRepository.kt`
- `app/src/main/java/com/luxmap/feature/workorder/polegps/data/PoleGpsDao.kt`
- `app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepository.kt`
- `app/src/main/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDao.kt`
- `app/src/main/java/com/luxmap/feature/workorder/data/sync/WorkOrderSyncPayloads.kt`
- `app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt`
- `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailViewModel.kt`

**Tests:** Existing `app/src/test/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandlerTest.kt`, `.../data/RealWorkOrderCompletionRepositoryTest.kt`, `.../polegps/data/PoleGpsRepositoryTest.kt`, `.../ui/detail/WorkOrderDetailViewModelTest.kt`; create `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/data/PoleGpsSubmissionTest.kt` for actual Room atomicity.

**Interfaces:** Add `poleLocations: List<PoleLocationRequestDto>? = null` to local `CompletionSyncPayload` with SerialName `pole_locations`. Add `suspend fun stageCompletion(completion: LocalWorkOrderCompletionEntity): Boolean` on PoleGpsRepository. RealWorkOrderCompletionRepository routes pole_gps_update to it; ViewModel no longer calls enqueuePendingPhotoUploads separately. API CompleteWorkOrderRequestDto is unchanged.

- [ ] RED: Handler tests: one dependency queued/retryable → WaitingForDependency, api.complete never called; failed/missing → Failed; conflict → Conflict; all done → complete called once with exact snapshotted coordinates. Change live measurement after enqueue and assert payload stays unchanged.
- [ ] RED: Repository/Room tests: selected measurements + per-pole evidence + upload ops + complete op are persisted together; injected failure rolls everything back. Duplicate submit does not create extra uploads/completions. Retaken photo uses current file, not prior photo for same pole. Partial submission contains exactly measured poles.
- [ ] Implement snapshot validation and staging inside database.withTransaction. Reuse upload op identity by exact `(workOrderId, poleId, filePath, capturedAt)` evidence match; otherwise allocate UUID once in the transaction. Include every selected photo op ID in evidenceClientOpIds. Preserve existing retry/conflict behavior; trigger WorkManager only after commit.
- [ ] Legacy payload tests: GPS complete without snapshot/dependencies reconstructs and durably replaces queued payload from matching local measurements/evidence before sending; insufficient data fails clearly without deleting evidence. Non-GPS payloads follow existing behavior; synced completion stays a no-op. Implement explicit legacy preparation in handler/repository before network call.
- [ ] Cover returned task/resubmit: only one active submission, correct new photo references, no duplicate already-uploaded operations. Report errors to the existing UI instead of leaving isSubmitting stuck.
- [ ] GREEN: `.\gradlew.bat testLocalDebugUnitTest --tests 'com.luxmap.feature.workorder.*'`; run connectedLocalDebugAndroidTest filtered to PoleGpsSubmissionTest. Expected BUILD SUCCESSFUL, no failed assertions.

## Task 2: GPS observer và timestamp thực

**Modify:**
- `app/src/main/java/com/luxmap/feature/workorder/polegps/data/GpsFix.kt`
- `app/src/main/java/com/luxmap/feature/workorder/polegps/data/FusedGpsFixSource.kt`
- `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModel.kt`
- `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureViewModelTest.kt`
- Create: `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureLifecycleTest.kt`

**Interfaces:** GpsFix gains `elapsedRealtimeNanos: Long` (update fixture constructors). `capturedAt` is Instant.ofEpochMilli(location.time). Add injectable `GpsClock` interface with `fun elapsedRealtimeNanos(): Long`, implementation uses SystemClock, tests use fixed clock. ViewModel exposes idempotent `startWatchingLocation()` and `stopWatchingLocation()`; route owns subscription lifetime and stops on disposal.

- [ ] RED: Start before camera, repeated start keeps one active subscription, stop/dispose cancels it; permission denial/revocation does not crash. Fresh sample is used, >10 s sample is ignored and a fresh sample is awaited; no valid sample after 10 s holds photo for retry.
- [ ] Implement actual Location timestamps and monotonic age checks, injecting GpsClock via Hilt. Callback/currentFix no longer turns old Location into a fresh one using Instant.now. Do not rewrite captured_at later to conceal a stale sample.
- [ ] Wire start when fine-location permission is known and before camera launch; handle permission granted on retry and returning from external camera. Catch location subscription errors and camera launch failures into existing recoverable UI. Cancel on navigation/disposal and ViewModel clear.
- [ ] Device lifecycle tests with controllable fixSource/camera intent: denied permission, cancel camera, retry, recreation; no fake coordinates, duplicate save or leaked collector. Unit test wall-clock changes separately from monotonic elapsed time.
- [ ] GREEN: `.\gradlew.bat testLocalDebugUnitTest --tests 'com.luxmap.feature.workorder.polegps.*'`; device lifecycle test passes with fake providers, no BE calls.

## Task 3: Hiển thị sai số và xác nhận phép đo kém

**Create:** `app/src/main/java/com/luxmap/feature/workorder/polegps/data/GpsFixQuality.kt` and `app/src/test/java/com/luxmap/feature/workorder/polegps/data/GpsFixQualityTest.kt`.
**Modify:** GpsFix/ViewModel/screen from Task 2, `app/src/main/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsMeasureUiState.kt`, `app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/PoleGpsSection.kt`.
**Device test:** create `app/src/androidTest/java/com/luxmap/feature/workorder/polegps/ui/measure/PoleGpsQualityUiTest.kt`.

**Interfaces:** `enum class GpsFixQuality { INVALID, STALE, ACCEPTABLE, LOW_ACCURACY }`; `fun GpsFix.quality(nowElapsedRealtimeNanos: Long): GpsFixQuality`. INVALID for non-finite/out-of-range lat/lng, non-finite/negative accuracy or invalid time; STALE for age >10 s; valid accuracy <=20 m is ACCEPTABLE, >20 m LOW_ACCURACY. ViewModel holds the exact candidate sample/photo while asking confirmation; continuing saves that candidate or revalidates freshness and asks again when it expired.

- [ ] RED: Assertions for accuracy 20.0/20.1, zero/negative/NaN, lat/lng bounds, age 10 s / 10 s + 1 ns, future elapsed time. None of invalid/stale paths writes EXIF or Room. LOW_ACCURACY does not persist without explicit confirmation.
- [ ] Implement policy and show `Sai số GPS: ±… m` and sample time in photo confirmation; LOW_ACCURACY text explains waiting/remeasuring. Actions: `Đo lại` and `Vẫn lưu vị trí này`. Do not add hard 20 m rejection to API or BE.
- [ ] Device tests: warning appears for 35 m; cancel/retry retains photo; confirm saves exact candidate once; candidate changes/expires while dialog open cannot silently save a different fix. Show persisted lat/lng/accuracy on measured pole rows so user can review or retake.
- [ ] Check Light/Night and font 2.0; text accessible, labels wrap, no overlap with camera actions. Keep threshold constants together for later product decision.
- [ ] GREEN: targeted GPS unit tests and PoleGpsQualityUiTest pass. Store screenshots as evidence.

## Task 4: Cache danh sách GPS theo tài khoản và hiển thị stale

**Create:**
- `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalAssignedPoleGpsOrderEntity.kt`
- `app/src/main/java/com/luxmap/feature/survey/data/dao/PoleGpsOrderCacheDao.kt`
- `app/src/androidTest/java/com/luxmap/core/database/Migration22To23Test.kt`
- `app/src/androidTest/java/com/luxmap/feature/survey/data/PoleGpsOrderCacheDaoTest.kt`

**Modify:**
- `app/src/main/java/com/luxmap/core/database/AppDatabase.kt` (v22→23) and `AppMigrations.kt`
- `app/src/main/java/com/luxmap/di/DatabaseModule.kt`
- `app/src/main/java/com/luxmap/core/security/TokenStore.kt`
- `app/src/main/java/com/luxmap/feature/auth/data/RealAuthRepository.kt`
- `app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt`, `RealSurveyRepository.kt`
- `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModel.kt`, `SurveyHubUiState.kt`, `SurveyHubScreen.kt`
- Existing repository/Hub/TokenStore tests and Android migration-chain tests; regenerate Room schema v23 if schema export is enabled.

**Interfaces:** New entity table `local_assigned_pole_gps_order`, primary key `(ownerUserId, workOrderId)`; fields title, communeId, woStatus, dueDate, scheduledDate. DAO `suspend fun all(ownerUserId: String): List<LocalAssignedPoleGpsOrderEntity>` and transactional `replaceAll(ownerUserId: String, items: List<LocalAssignedPoleGpsOrderEntity>)` delete only that user's GPS rows. `observeAssignedPoleGpsOrders(): Flow<AssignedRoutesResult>` reuses routes/isStale result shape. TokenStore adds `suspend fun cachedUserId(): String?`, `suspend fun saveCachedUserId(userId: String)`, `suspend fun clearCachedUserId()`; clear on successful new login/loginWithGoogle and clear(), preserve on token refresh.

- [ ] RED: successful GPS fetch caches user A; /auth/me offline uses cached identity and GPS list marked stale. Successful live empty response clears only A GPS cache; survey cache stays intact. User B/login/logout cannot load A GPS cache. Fetch all pages before replace; interrupted pagination retains old cache marked stale.
- [ ] Implement identity/cache/migration. Persist verified user_id from /auth/me before dependent fetches; read cached identity before attempting network so failure there does not bypass fallback. No cached identity means no user-owned cache access. 401/403 propagate through session/error path rather than stale fallback.
- [ ] Update combine/Hub: preserve GPS cache even if survey source fails; show stale when either source stale. Distinguish offline no-cache from live no assigned work. Retry on one source never deletes the other. Existing scheduled-date/action restrictions remain.
- [ ] Migration test: upgrade seeded v22 DB containing survey rows, measurements, evidence and queue; verify all remain and new cache works. DAO test replacement user isolation. Token identity test covers refresh vs new login and clear.
- [ ] Device test airplane mode + reopen tab/app after one successful fetch (fixture server permitted): GPS tasks remain visible with stale notice; details/PDF only available if cached. Restore device connectivity afterwards. Do not claim offline start works.
- [ ] GREEN: auth/repository/Hub unit tests, migration-chain/DAO/device tests all pass; no destructive recreation on v22→23.

## Final verification and handoff

- [ ] Run `.\gradlew.bat ktlintCheck testLocalDebugUnitTest assembleServerDebug`; expected BUILD SUCCESSFUL and zero test failures.
- [ ] Run relevant connectedLocalDebugAndroidTest classes for submission Room transaction, lifecycle/quality, migration/cache, and regressions in existing inspection/repair evidence submission. Verify actual device test counts/results.
- [ ] Device exercise: deny GPS, acquire poor/good fix, take/retake/cancel photo, restart while queued, failed upload then retry, partial report, return/rework, airplane-mode cache, account switch. Use fixtures and never mutate BE/Web by default.
- [ ] With separately authorized test data: Web creates GPS order/PDF → app measures → upload interruption → resume → Web sees per-pole photos and report → return and remeasure → verify updates GIS. Add off-segment rejection case: no GIS rows changed. This manual test must not be marked passed from unit tests alone.
- [ ] Request independent review focused on the five Review Focus cases; record limitations and screenshot evidence. Commit/PR only when implementation is requested; target dev and use Conventional Commits.

## Self-review of plan

All four audited gaps map to a task. Shared dependency: Tasks 2→3 share GpsFix; Task 1 changes only local payload; Task 4 owns the single new schema migration. Thresholds are proposals and visible in design, not presented as existing contract rules. Cache covers list and account identity, not a promise of uncached PDFs or offline start. Legacy complete operations and partial reports are explicitly covered. No BE/Web changes are required by this plan.

## Kết quả triển khai — 2026-11-19

- Task 1–4 đã triển khai trên `codex/pole-gps-hardening`, nhánh riêng kế thừa PR #172 đã merge; PR GPS target dev.
- Chốt tọa độ/ảnh/operation trong một Room transaction; complete phụ thuộc đầy đủ ảnh; snapshot không đổi khi retry; hỗ trợ payload GPS cũ và mở lại bản nộp lỗi mà giữ ảnh/phép đo.
- Rework: GPS đã được thêm vào luồng chuẩn bị nộp lại. Báo cáo mới gửi các phép đo DEVICE; SERVER chỉ hiển thị là đã báo trước. Một phần vẫn được phép. Hồi phục đồng thời ảnh cần chụp lại và ảnh hết retry đã được kiểm thử.
- GPS dùng Location.time và monotonic age; theo dõi trước camera, hủy đúng vòng đời; mốc 10 giây/20 m theo thiết kế. Người dùng phải xác nhận mẫu sai số lớn; xác nhận gắn với đúng mẫu còn mới. GpsClock dùng open class injectable thay cho interface để tránh thêm binding.
- Room v23 thêm cache GPS theo owner và ownerUserId nullable cho cache survey cũ; dữ liệu v22 được giữ nguyên. Session generation ngăn response tài khoản cũ cập nhật identity mới; login/logout xóa survey-list cache cũ, không xóa ảnh/phép đo/hàng đợi.
- Phân trang không hoàn tất dùng lỗi recoverable riêng: cache còn hiển thị stale, không nuốt lỗi 401/403 hay thay đổi phiên.
- Bổ sung phục hồi GPS complete bị ngắt ở trạng thái syncing: truy vấn queue nhận đúng GPS qua local completion/order join; handler đọc trạng thái BE và so sánh nội dung, tọa độ, thời gian chuẩn microsecond trước khi POST. Báo cáo đã nhận được đánh dấu synced; báo cáo khác giữ conflict. Bản cũ đã được Manager return không tự POST lại; bản thực sự sửa vẫn được nộp.
- RED đã ghi nhận: hai trường hợp rework trên JVM; hai lỗi partial/retry trên thiết bị; lỗi cache khi thiếu trang; hai trường hợp phục hồi/gửi trùng và một trường hợp Manager return sau gián đoạn. Đã sửa và xác nhận GREEN.
- Kiểm chứng cuối với JDK 17: `ktlintCheck testLocalDebugUnitTest assembleServerDebug` BUILD SUCCESSFUL; 562 unit tests, 0 failures, 0 errors. Lượt connected trên Samsung SM-A075F / Android 16: 43 tests pass; kiểm tra UI sáng/Night chữ 200% và lưu ảnh tại `build/gps-device-verification/`.
- Review độc lập đã xử lý các phát hiện quan trọng; không thay source hay ghi dữ liệu ở Web/BE.
- APK mới đã cài thành công lên thiết bị; mở app và tab Khảo sát được, tài khoản crew hiện chưa có công việc khảo sát/GPS được giao.
- Còn cần kiểm thử thủ công khi có task/tài khoản test phù hợp: Web giao GPS/PDF → chụp/lấy GPS ngoài hiện trường thật → Web return/verify/đổi GIS; không coi các fixture tests là bằng chứng luồng BE thật đã pass. Chưa thử bật máy bay với một task GPS thật đã tải; test Room reopen + stale UI và repository offline đã pass.
