# Pole Photo Batch-Submit Review + Gallery Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Change the already-shipped pole-photo survey review flow so a Field Engineer's AI-label correction is saved locally per pole and only sent to the server, for every pole in the route at once, when "Hoàn tất" is tapped — and add "choose from gallery" as a second way to supply a pole's photo, alongside the existing camera capture.

**Architecture:** Photo capture → upload → AI classification stay exactly as they are today (immediate, per pole, through the existing `upload_pole_photo` sync op). Only the review-correction step changes: `ReviewCard`'s "confirm" action now writes two new nullable columns (`draftFinalCvState`, `draftReviewNote`) on `LocalPolePhotoEntity` instead of calling the server directly. Tapping "Hoàn tất" scans the route's photos for unsent drafts and enqueues one new `submit_pole_photo_review` sync-queue job per draft (processed by a new `SubmitPolePhotoReviewSyncHandler`, same shape as the existing `UploadPolePhotoSyncHandler`), then proceeds with the existing work-order completion call. The gallery picker is an input-source branch inside the existing `PolePhotoCaptureRoute` — same Room row, same upload pipeline, only where the JPEG file comes from differs.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Retrofit/kotlinx.serialization, Coroutines/StateFlow, MockK + Turbine (unit tests), Compose UI Test (instrumented, real-device only). `androidx.activity`'s `ActivityResultContracts.PickVisualMedia` for the gallery picker — already a transitive dependency via `activity-compose`, no new library.

**Spec:** `docs/superpowers/specs/2026-11-10-pole-photo-batch-submit-design.md`

## Global Constraints

- Run `./gradlew ktlintCheck` before every commit in this plan and fix any violation first — do not commit with it failing.
- Keep each commit under ~400 changed lines (`CLAUDE.md`'s commit-size rule) — every task below is already split to fit on its own; split a task's steps into two commits if it runs long in practice.
- No new third-party dependency — the gallery picker uses `ActivityResultContracts.PickVisualMedia`, already available via the existing `androidx.activity` dependency.
- DB version bumps via `fallbackToDestructiveMigration()` (already configured in `DatabaseModule.kt`) — no `Migration` object, no migration test. This is pre-release, matching the existing convention for every prior version bump in this app.
- No dedicated per-row sync-status badge for the review-send job after "Hoàn tất" (confirmed cut, spec section 2) — visibility into a failed review-send is the existing F13 Sync Queue screen, not new UI here.
- `clientOpId` for the new sync op is `"$photoId:review"`, never the bare `photoId` (that value is already used by the `upload_pole_photo` op for the same row).
- No instrumented (`androidTest`) test for the gallery-picker composable itself — same reasoning already applied to the camera capture composable in the 2026-10-15 spec (a thin Android-system pipe, not reliably mockable); verify it by hand on a real device instead. The existing `ReviewCard`/`PoleListScreen` instrumented tests DO need updating in this plan (they test real app code, not a system picker).
- Work on the existing branch `feat/pole-photo-batch-submit-review` (already has the spec commit) — do not create a new branch per task.

## Review Focus

- Re-opening a pole's AI-result sheet (which calls `fetchResult`/`persist()`) after a draft was already saved must never clear that unsent draft — `updateAiResult`'s SQL must stay scoped to the columns it already touches, never the two new draft columns.
- `enqueuePendingReviews` must read the Field Engineer's current, latest-saved draft from Room at the moment "Hoàn tất" is tapped, not a value captured earlier in the screen's lifetime — it must query fresh each call, not cache.
- A 409 `POLE_PHOTO_ALREADY_DECIDED` on one pole's review-send job must not stop sibling review-send jobs, or the `complete_work_order` job, from proceeding — no `dependsOnClientOpId` chaining between any of these ops (by design, per spec section 5).
- A pole the Field Engineer never reviewed (no draft) must never be enqueued by `enqueuePendingReviews` — only rows with `draftFinalCvState != null` qualify.
- Backing out of the gallery picker (no image selected) must return to the Pole List screen exactly like backing out of the camera does today — no Room row written, no crash.

---

### Task 1: Room — draft review columns, DAO query, DB version bump

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalPolePhotoEntity.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/dao/PolePhotoDao.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/PolePhotoDaoTest.kt`

**Interfaces:**
- Produces: `LocalPolePhotoEntity.draftFinalCvState: String?`, `LocalPolePhotoEntity.draftReviewNote: String?`; `PolePhotoDao.updateDraftReview(photoId: String, draftFinalCvState: String?, draftReviewNote: String?)` (suspend) — used both to save a draft (non-null args) and to clear one after a successful send (null args).

- [ ] **Step 1: Add the two columns to the entity**

In `LocalPolePhotoEntity.kt`, add after the existing `decidedAt` field:

```kotlin
    // Field Engineer's not-yet-sent review correction, saved by ReviewCard's "Lưu xác nhận"
    // action. Separate from finalCvState/reviewNote, which only hold a server-confirmed review.
    // Cleared back to null by SubmitPolePhotoReviewSyncHandler once the send succeeds.
    val draftFinalCvState: String? = null,
    val draftReviewNote: String? = null,
```

- [ ] **Step 2: Add the DAO query**

In `PolePhotoDao.kt`, add after `updateAiResult`:

```kotlin
    @Query(
        """
        UPDATE local_pole_photo SET
            draftFinalCvState = :draftFinalCvState,
            draftReviewNote = :draftReviewNote
        WHERE photoId = :photoId
        """,
    )
    suspend fun updateDraftReview(
        photoId: String,
        draftFinalCvState: String?,
        draftReviewNote: String?,
    )
```

- [ ] **Step 3: Bump the database version**

In `AppDatabase.kt`, change `version = 13` to `version = 14`.

- [ ] **Step 4: Write the DAO tests**

Add to `PolePhotoDaoTest.kt`:

```kotlin
    @Test
    fun updateDraftReviewSetsBothDraftColumns() =
        runTest {
            dao.insert(photo("PH-1"))

            dao.updateDraftReview("PH-1", "off", "Thấy đèn tắt khi kiểm tra")

            val row = dao.byId("PH-1")
            assertEquals("off", row?.draftFinalCvState)
            assertEquals("Thấy đèn tắt khi kiểm tra", row?.draftReviewNote)
        }

    @Test
    fun updateDraftReviewWithNullsClearsAPreviouslySavedDraft() =
        runTest {
            dao.insert(photo("PH-1"))
            dao.updateDraftReview("PH-1", "off", "Thấy đèn tắt khi kiểm tra")

            dao.updateDraftReview("PH-1", null, null)

            val row = dao.byId("PH-1")
            assertEquals(null, row?.draftFinalCvState)
            assertEquals(null, row?.draftReviewNote)
        }

    @Test
    fun updateAiResultDoesNotTouchAPreviouslySavedDraft() =
        runTest {
            dao.insert(photo("PH-1"))
            dao.updateDraftReview("PH-1", "off", "Thấy đèn tắt khi kiểm tra")

            dao.updateAiResult(
                photoId = "PH-1",
                aiStatus = "available",
                cvState = "on",
                confidence = 0.92,
                modelVersion = "cv-v1",
                analyzedAt = Instant.parse("2026-10-16T10:00:10Z"),
                finalCvState = null,
                reviewNote = null,
                reviewedBy = null,
                reviewedAt = null,
                decision = null,
                decisionNote = null,
                decidedBy = null,
                decidedAt = null,
            )

            val row = dao.byId("PH-1")
            assertEquals("off", row?.draftFinalCvState)
            assertEquals("Thấy đèn tắt khi kiểm tra", row?.draftReviewNote)
        }
```

- [ ] **Step 5: Run the DAO tests**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.PolePhotoDaoTest"` on a real device (the emulator-only `InputManager` failure tracked in memory does not affect this DAO-only test class, but confirm on whichever device is available).
Expected: all `PolePhotoDaoTest` cases pass, including the 3 new ones above.

- [ ] **Step 6: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/entity/LocalPolePhotoEntity.kt app/src/main/java/com/luxmap/feature/survey/data/dao/PolePhotoDao.kt app/src/main/java/com/luxmap/core/database/AppDatabase.kt app/src/androidTest/java/com/luxmap/feature/survey/data/dao/PolePhotoDaoTest.kt
git commit -m "feat(pole-photo): add local draft review columns"
```

---

### Task 2: Sync — `submit_pole_photo_review` op handler

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/SubmitPolePhotoReviewSyncHandler.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/data/sync/SubmitPolePhotoReviewSyncHandlerTest.kt`
- Modify: `app/src/main/java/com/luxmap/di/SyncModule.kt`

**Interfaces:**
- Consumes: `PolePhotoSyncPayload(photoId: String)` (existing, from `UploadPolePhotoSyncHandler`'s file); `PolePhotoDao.byId`, `.updateAiResult`, `.updateDraftReview` (Task 1); `WorkOrdersApi.reviewPolePhoto(id: String, photoId: String, body: PolePhotoReviewRequestDto): PolePhotoItemDto` (existing).
- Produces: `SubmitPolePhotoReviewSyncHandler : SyncOpHandler` with `opType = "submit_pole_photo_review"`, bound into the `Set<SyncOpHandler>` multibinding.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import com.luxmap.feature.survey.data.dto.PolePhotoReviewRequestDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant

private fun httpException(
    code: Int,
    errorCode: String? = null,
) = HttpException(
    Response.error<Any>(
        code,
        (if (errorCode == null) "" else """{"error":{"code":"$errorCode","message":"x"}}""")
            .toResponseBody("application/json".toMediaType()),
    ),
)

private fun draftPhoto(
    serverPhotoId: String? = "PPH-000001",
    draftFinalCvState: String? = "off",
    draftReviewNote: String? = "Thấy đèn tắt khi kiểm tra",
) = LocalPolePhotoEntity(
    photoId = "PHOTO-1",
    workOrderId = "WO-1",
    poleId = "POLE-1",
    filePath = "/data/photo.jpg",
    capturedAt = Instant.parse("2026-10-16T10:00:00Z"),
    lat = 10.97,
    lng = 106.49,
    serverPhotoId = serverPhotoId,
    cvState = "on",
    draftFinalCvState = draftFinalCvState,
    draftReviewNote = draftReviewNote,
)

private fun reviewedItemDto() =
    PolePhotoItemDto(
        photoId = "PPH-000001",
        workOrderId = "WO-1",
        poleId = "POLE-1",
        dataSource = "pole_photo",
        capturedAt = "2026-10-16T10:00:00Z",
        lat = 10.97,
        lng = 106.49,
        uploadedBy = "U-1",
        uploadedAt = "2026-10-16T10:00:05Z",
        aiStatus = "available",
        cvState = "on",
        confidence = 0.92,
        modelVersion = "cv-v1",
        artifactVersion = "art-v1",
        analyzedAt = "2026-10-16T10:00:10Z",
        finalCvState = "off",
        reviewNote = "Thấy đèn tắt khi kiểm tra",
        reviewedBy = "U-2",
        reviewedAt = "2026-11-10T08:00:00Z",
    )

class SubmitPolePhotoReviewSyncHandlerTest {
    @Test
    fun `a successful send writes the confirmed fields and clears the draft`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>(relaxed = true)
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto()
            coEvery {
                api.reviewPolePhoto(
                    "WO-1",
                    "PPH-000001",
                    PolePhotoReviewRequestDto(finalCvState = "off", reviewNote = "Thấy đèn tắt khi kiểm tra"),
                )
            } returns reviewedItemDto()
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify {
                dao.updateAiResult(
                    photoId = "PHOTO-1",
                    aiStatus = "available",
                    cvState = "on",
                    confidence = 0.92,
                    modelVersion = "cv-v1",
                    analyzedAt = Instant.parse("2026-10-16T10:00:10Z"),
                    finalCvState = "off",
                    reviewNote = "Thấy đèn tắt khi kiểm tra",
                    reviewedBy = "U-2",
                    reviewedAt = Instant.parse("2026-11-10T08:00:00Z"),
                    decision = null,
                    decisionNote = null,
                    decidedBy = null,
                    decidedAt = null,
                )
            }
            coVerify { dao.updateDraftReview("PHOTO-1", null, null) }
        }

    @Test
    fun `a missing local row returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns null
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a row with no serverPhotoId returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto(serverPhotoId = null)
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a row with no draft returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto(draftFinalCvState = null)
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 409 already-decided error maps to Failed, not RetryLater`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto()
            coEvery { api.reviewPolePhoto(any(), any(), any()) } throws
                httpException(409, "POLE_PHOTO_ALREADY_DECIDED")
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns draftPhoto()
            coEvery { api.reviewPolePhoto(any(), any(), any()) } throws httpException(500)
            val handler = SubmitPolePhotoReviewSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.SubmitPolePhotoReviewSyncHandlerTest"`
Expected: FAIL — `SubmitPolePhotoReviewSyncHandler` does not exist yet.

- [ ] **Step 3: Write the handler**

```kotlin
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoReviewRequestDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

class SubmitPolePhotoReviewSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
    ) : SyncOpHandler {
        override val opType = "submit_pole_photo_review"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<PolePhotoSyncPayload>(payloadJson)
            val photo = dao.byId(payload.photoId) ?: return SyncOpResult.Failed("Local pole photo row missing")
            val serverPhotoId =
                photo.serverPhotoId ?: return SyncOpResult.Failed("Local pole photo missing server photo id")
            val draftFinalCvState =
                photo.draftFinalCvState ?: return SyncOpResult.Failed("No draft review to send")

            return try {
                val response =
                    api.reviewPolePhoto(
                        photo.workOrderId,
                        serverPhotoId,
                        PolePhotoReviewRequestDto(finalCvState = draftFinalCvState, reviewNote = photo.draftReviewNote),
                    )
                dao.updateAiResult(
                    photoId = photo.photoId,
                    aiStatus = response.aiStatus,
                    cvState = response.cvState,
                    confidence = response.confidence,
                    modelVersion = response.modelVersion,
                    analyzedAt = response.analyzedAt?.let(Instant::parse),
                    finalCvState = response.finalCvState,
                    reviewNote = response.reviewNote,
                    reviewedBy = response.reviewedBy,
                    reviewedAt = response.reviewedAt?.let(Instant::parse),
                    decision = response.decision,
                    decisionNote = response.decisionNote,
                    decidedBy = response.decidedBy,
                    decidedAt = response.decidedAt?.let(Instant::parse),
                )
                dao.updateDraftReview(photo.photoId, null, null)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409, 400 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.SubmitPolePhotoReviewSyncHandlerTest"`
Expected: PASS, all 6 cases.

- [ ] **Step 5: Bind the handler in `SyncModule`**

In `SyncModule.kt`, add the import `com.luxmap.feature.survey.data.sync.SubmitPolePhotoReviewSyncHandler` and, after `bindUploadPolePhotoSyncHandler`, add:

```kotlin
    @Binds
    @IntoSet
    abstract fun bindSubmitPolePhotoReviewSyncHandler(impl: SubmitPolePhotoReviewSyncHandler): SyncOpHandler
```

- [ ] **Step 6: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/sync/SubmitPolePhotoReviewSyncHandler.kt app/src/test/java/com/luxmap/feature/survey/data/sync/SubmitPolePhotoReviewSyncHandlerTest.kt app/src/main/java/com/luxmap/di/SyncModule.kt
git commit -m "feat(pole-photo): add submit_pole_photo_review sync handler"
```

---

### Task 3: Repository — `saveDraftReview` and `enqueuePendingReviews`, remove `reviewPhoto`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/PolePhotoRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/RealPolePhotoRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/FakePolePhotoRepository.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/data/RealPolePhotoRepositoryTest.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/data/FakePolePhotoRepositoryTest.kt`

**Interfaces:**
- Consumes: `PolePhotoDao.updateDraftReview` (Task 1), `SyncQueueManager.enqueue(opType: String, payloadJson: String, clientOpId: String)` (existing).
- Produces: `PolePhotoRepository.saveDraftReview(serverPhotoId: String, finalCvState: String, reviewNote: String?)` (suspend, `Unit`); `PolePhotoRepository.enqueuePendingReviews(workOrderId: String): Int` (suspend, returns how many drafts were enqueued). `reviewPhoto` is removed from the interface and both implementations — nothing outside this task calls it after Task 4.

- [ ] **Step 1: Update the interface**

In `PolePhotoRepository.kt`, delete the entire `reviewPhoto` method and its doc comment, and add in its place:

```kotlin
    // Saves the Field Engineer's correction/agreement on cvState locally only — nothing is sent
    // to the server here. See RealPolePhotoRepository.enqueuePendingReviews for when it is sent.
    suspend fun saveDraftReview(
        serverPhotoId: String,
        finalCvState: String,
        reviewNote: String?,
    )

    // Scans this work order's photos for an unsent draft review and enqueues one
    // "submit_pole_photo_review" sync op per draft found. Returns how many were enqueued, so the
    // caller (PoleListViewModel.onComplete) can report it. Called exactly once, when "Hoàn tất"
    // is tapped — never at saveDraftReview time, so a draft is never sent before that.
    suspend fun enqueuePendingReviews(workOrderId: String): Int
```

- [ ] **Step 2: Update `RealPolePhotoRepository`**

Delete the `reviewPhoto` override entirely, and delete the now-unused `import com.luxmap.feature.survey.data.dto.PolePhotoReviewRequestDto` line. Add the import `import kotlinx.coroutines.flow.first`, then add, after `capture`:

```kotlin
        override suspend fun saveDraftReview(
            serverPhotoId: String,
            finalCvState: String,
            reviewNote: String?,
        ) {
            val localPhotoId = dao.byServerPhotoId(serverPhotoId)?.photoId ?: return
            dao.updateDraftReview(localPhotoId, finalCvState, reviewNote)
        }

        override suspend fun enqueuePendingReviews(workOrderId: String): Int {
            val pending = dao.observeByWorkOrder(workOrderId).first().filter { it.draftFinalCvState != null }
            pending.forEach { photo ->
                syncQueueManager.enqueue(
                    opType = "submit_pole_photo_review",
                    payloadJson = json.encodeToString(PolePhotoSyncPayload(photo.photoId)),
                    clientOpId = "${photo.photoId}:review",
                )
            }
            return pending.size
        }
```

- [ ] **Step 3: Update `FakePolePhotoRepository`**

Delete the `reviewPhoto` override entirely. Add the import `import kotlinx.coroutines.flow.first`, then add, after `capture`:

```kotlin
        override suspend fun saveDraftReview(
            serverPhotoId: String,
            finalCvState: String,
            reviewNote: String?,
        ) {
            val localPhotoId = dao.byServerPhotoId(serverPhotoId)?.photoId ?: return
            dao.updateDraftReview(localPhotoId, finalCvState, reviewNote)
        }

        override suspend fun enqueuePendingReviews(workOrderId: String): Int {
            val pending = dao.observeByWorkOrder(workOrderId).first().filter { it.draftFinalCvState != null }
            pending.forEach { photo ->
                dao.updateAiResult(
                    photoId = photo.photoId,
                    aiStatus = photo.aiStatus ?: "available",
                    cvState = photo.cvState,
                    confidence = photo.confidence,
                    modelVersion = photo.modelVersion,
                    analyzedAt = photo.analyzedAt,
                    finalCvState = photo.draftFinalCvState,
                    reviewNote = photo.draftReviewNote,
                    reviewedBy = "fake-user",
                    reviewedAt = Instant.now(),
                    decision = photo.decision,
                    decisionNote = photo.decisionNote,
                    decidedBy = photo.decidedBy,
                    decidedAt = photo.decidedAt,
                )
                dao.updateDraftReview(photo.photoId, null, null)
            }
            return pending.size
        }
```

- [ ] **Step 4: Replace the `reviewPhoto` tests in `RealPolePhotoRepositoryTest.kt`**

Delete the 2 tests `reviewPhoto sends final_cv_state and review_note then writes the response onto the local row` and `reviewPhoto fails when no local row matches that serverPhotoId`, and delete the now-unused `polePhotoItemDto`-based `PolePhotoReviewRequestDto` import if it is otherwise unused (it is still used by `polePhotoItemDto`'s own file, but check the import list in this test file specifically). Add in their place:

```kotlin
    @Test
    fun `saveDraftReview writes both draft columns onto the row matching that serverPhotoId`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            coEvery { dao.byServerPhotoId("PPH-000001") } returns localPhoto(serverPhotoId = "PPH-000001")
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            repository.saveDraftReview("PPH-000001", "off", "Thấy đèn tắt khi kiểm tra")

            coVerify { dao.updateDraftReview("PHOTO-1", "off", "Thấy đèn tắt khi kiểm tra") }
        }

    @Test
    fun `saveDraftReview does nothing when no local row matches that serverPhotoId`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            coEvery { dao.byServerPhotoId("PPH-000001") } returns null
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            repository.saveDraftReview("PPH-000001", "off", "note")

            coVerify(exactly = 0) { dao.updateDraftReview(any(), any(), any()) }
        }

    @Test
    fun `enqueuePendingReviews enqueues one op per photo with a draft, using a review-suffixed clientOpId`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>()
            every { dao.observeByWorkOrder("WO-1") } returns
                flowOf(
                    listOf(
                        localPhoto(photoId = "PHOTO-1").copy(draftFinalCvState = "off"),
                        localPhoto(photoId = "PHOTO-2"),
                    ),
                )
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val count = repository.enqueuePendingReviews("WO-1")

            assertEquals(1, count)
            coVerify {
                syncQueueManager.enqueue(
                    opType = "submit_pole_photo_review",
                    payloadJson = "{\"photo_id\":\"PHOTO-1\"}",
                    clientOpId = "PHOTO-1:review",
                )
            }
        }

    @Test
    fun `enqueuePendingReviews enqueues nothing when no photo has a draft`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>()
            every { dao.observeByWorkOrder("WO-1") } returns flowOf(listOf(localPhoto()))
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val count = repository.enqueuePendingReviews("WO-1")

            assertEquals(0, count)
            coVerify(exactly = 0) { syncQueueManager.enqueue(any(), any(), any()) }
        }
```

`localPhoto` in this file already takes a `photoId: String = "PHOTO-1"` parameter — no signature change needed, `localPhoto(photoId = "PHOTO-1").copy(draftFinalCvState = "off")` works as-is.

- [ ] **Step 5: Add the `FakePolePhotoRepository` tests**

Add to `FakePolePhotoRepositoryTest.kt`:

```kotlin
    @Test
    fun `saveDraftReview writes both draft columns`() =
        runTest {
            val dao = mockk<PolePhotoDao>(relaxed = true)
            coEvery { dao.byServerPhotoId("SERVER-1") } returns
                LocalPolePhotoEntity(
                    photoId = "PH-1",
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/x.jpg",
                    capturedAt = java.time.Instant.parse("2026-10-15T10:00:00Z"),
                    lat = 10.0,
                    lng = 106.0,
                    serverPhotoId = "SERVER-1",
                )
            val repository = FakePolePhotoRepository(dao)

            repository.saveDraftReview("SERVER-1", "off", "Thấy đèn tắt")

            coVerify { dao.updateDraftReview("PH-1", "off", "Thấy đèn tắt") }
        }

    @Test
    fun `enqueuePendingReviews resolves every draft and clears it`() =
        runTest {
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val withDraft =
                LocalPolePhotoEntity(
                    photoId = "PH-1",
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/x.jpg",
                    capturedAt = java.time.Instant.parse("2026-10-15T10:00:00Z"),
                    lat = 10.0,
                    lng = 106.0,
                    draftFinalCvState = "off",
                    draftReviewNote = "Thấy đèn tắt",
                )
            every { dao.observeByWorkOrder("WO-1") } returns flowOf(listOf(withDraft))
            val repository = FakePolePhotoRepository(dao)

            val count = repository.enqueuePendingReviews("WO-1")

            assertEquals(1, count)
            coVerify { dao.updateDraftReview("PH-1", null, null) }
        }
```

This needs `import io.mockk.coEvery` and `import io.mockk.coVerify` added to the test file (`coVerify` is already imported; add `coEvery`).

- [ ] **Step 6: Run the repository tests**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealPolePhotoRepositoryTest" --tests "com.luxmap.feature.survey.data.FakePolePhotoRepositoryTest"`
Expected: PASS, all cases (old `reviewPhoto` tests removed, new ones green).

- [ ] **Step 7: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/PolePhotoRepository.kt app/src/main/java/com/luxmap/feature/survey/data/RealPolePhotoRepository.kt app/src/main/java/com/luxmap/feature/survey/data/FakePolePhotoRepository.kt app/src/test/java/com/luxmap/feature/survey/data/RealPolePhotoRepositoryTest.kt app/src/test/java/com/luxmap/feature/survey/data/FakePolePhotoRepositoryTest.kt
git commit -m "feat(pole-photo): replace reviewPhoto with saveDraftReview and enqueuePendingReviews"
```

---

### Task 4: ViewModel — draft-aware `onSubmitReview`, batch-enqueue on `onComplete`, pending-review flag

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoResultUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModel.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModelTest.kt`

**Interfaces:**
- Consumes: `PolePhotoRepository.saveDraftReview`/`enqueuePendingReviews` (Task 3).
- Produces: `PolePhotoResultUiState.Success.draftFinalCvState: String?`, `.draftReviewNote: String?` (replacing the removed `isSubmittingReview`/`reviewError`); `PoleListRowState.hasPendingReview: Boolean = false`; `PoleListViewModel.surveyCompleted: SharedFlow<Int>` (type changed from `SharedFlow<Unit>` — emits how many reviews were enqueued).

- [ ] **Step 1: Update `PolePhotoResultUiState.Success`**

In `PolePhotoResultUiState.kt`, remove the `isSubmittingReview` and `reviewError` fields and their comments, and add in their place (right after `reviewedAt`):

```kotlin
        // The Field Engineer's not-yet-sent draft correction (ReviewCard's local save) - distinct
        // from finalCvState/reviewNote above, which only hold a server-confirmed review. Null
        // once sent (SubmitPolePhotoReviewSyncHandler clears it) or if never reviewed.
        val draftFinalCvState: String? = null,
        val draftReviewNote: String? = null,
```

- [ ] **Step 2: Add `hasPendingReview` to `PoleListRowState`**

In `PoleListUiState.kt`, add a trailing field to `PoleListRowState`:

```kotlin
    // True when this pole's latest photo has a draft review not yet sent (Task 3's
    // enqueuePendingReviews is what sends it, on "Hoàn tất").
    val hasPendingReview: Boolean = false,
```

- [ ] **Step 3: Update `PoleListViewModel`**

Replace the `toResultUiState` extension's body to pass through the two new fields:

```kotlin
        private fun LocalPolePhotoEntity.toResultUiState(isStale: Boolean): PolePhotoResultUiState? =
            if (aiStatus == null) {
                null
            } else {
                PolePhotoResultUiState.Success(
                    filePath = filePath,
                    cvState = cvState,
                    confidence = confidence,
                    modelVersion = modelVersion,
                    analyzedAt = analyzedAt,
                    finalCvState = finalCvState,
                    reviewNote = reviewNote,
                    reviewedBy = reviewedBy,
                    reviewedAt = reviewedAt,
                    decision = decision,
                    decisionNote = decisionNote,
                    decidedBy = decidedBy,
                    decidedAt = decidedAt,
                    draftFinalCvState = draftFinalCvState,
                    draftReviewNote = draftReviewNote,
                    isStale = isStale,
                )
            }
```

Replace `onComplete` and `onSubmitReview`, and delete `reviewErrorMessage` entirely, along with the now-unused imports `com.luxmap.core.network.errorCodeOrNull` and `retrofit2.HttpException`:

```kotlin
        fun onComplete() {
            val state = _uiState.value as? PoleListUiState.Success ?: return
            if (state.isCompleted) return
            val captured = state.poles.count { it.syncStatus != null }
            if (captured == 0) return
            viewModelScope.launch {
                val pendingReviews = polePhotoRepository.enqueuePendingReviews(workOrderId)
                completionRepository.submitCompletion(
                    workOrderId = workOrderId,
                    taskKind = "survey",
                    reportNote = "Đã khảo sát $captured/${state.poles.size} cột đèn bằng ảnh chụp.",
                    materialsUsed = null,
                    faultOutcomes = null,
                )
                _surveyCompleted.tryEmit(pendingReviews)
            }
        }

        // Saves the Field Engineer's correction locally only - sent later, in bulk, by
        // enqueuePendingReviews when "Hoan tat" is tapped (ticket batch-submit-review, 2026-11-10).
        fun onSubmitReview(
            serverPhotoId: String,
            finalCvState: String,
            reviewNote: String?,
        ) {
            val current = _aiResultState.value as? PolePhotoResultUiState.Success ?: return
            viewModelScope.launch {
                polePhotoRepository.saveDraftReview(serverPhotoId, finalCvState, reviewNote)
                _aiResultState.value = current.copy(draftFinalCvState = finalCvState, draftReviewNote = reviewNote)
                _reviewSubmitted.tryEmit(Unit)
            }
        }
```

Change the `_surveyCompleted`/`surveyCompleted` declarations' type parameter from `Unit` to `Int`:

```kotlin
        private val _surveyCompleted = MutableSharedFlow<Int>(extraBufferCapacity = 1)
        val surveyCompleted: SharedFlow<Int> = _surveyCompleted.asSharedFlow()
```

Replace the `load()` function's pole-mapping section with one that also carries `hasPendingReview`, and add the small private `PoleSyncInfo` holder above `load()`:

```kotlin
        private data class PoleSyncInfo(
            val serverPhotoId: String?,
            val status: String?,
            val hasPendingReview: Boolean,
        )

        private fun load() {
            viewModelScope.launch {
                poleListRepository.poles(workOrderId).fold(
                    onSuccess = { result ->
                        val poles = result.poles
                        if (poles.isEmpty()) {
                            _uiState.value = PoleListUiState.Empty
                            return@launch
                        }
                        val infoByPoleFlow =
                            polePhotoRepository
                                .observePhotos(workOrderId)
                                .flatMapLatest { photos ->
                                    val latestByPole = photos.groupBy { it.poleId }.mapValues { it.value.first() }
                                    polePhotoRepository
                                        .observeUploadStatuses(latestByPole.values.map { it.photoId })
                                        .map { statuses ->
                                            latestByPole.mapValues { (_, photo) ->
                                                PoleSyncInfo(
                                                    serverPhotoId = photo.serverPhotoId,
                                                    status = statuses[photo.photoId],
                                                    hasPendingReview = photo.draftFinalCvState != null,
                                                )
                                            }
                                        }
                                }
                        combine(
                            infoByPoleFlow,
                            completionRepository.observeCompletion(workOrderId),
                        ) { infoByPole, completion ->
                            PoleListUiState.Success(
                                poles =
                                    poles.map { pole ->
                                        val info = infoByPole[pole.poleId]
                                        PoleListRowState(
                                            poleId = pole.poleId,
                                            position = pole.position,
                                            fixtureStatus = pole.fixtureStatus,
                                            serverPhotoId = info?.serverPhotoId,
                                            syncStatus = info?.status?.let(::toSyncStatus),
                                            hasPendingReview = info?.hasPendingReview ?: false,
                                        )
                                    },
                                isStale = result.isStale,
                                isCompleted = completion != null,
                            )
                        }
                            .catch { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Lỗi") }
                            .collect { _uiState.value = it }
                    },
                    onFailure = { e ->
                        _uiState.value = PoleListUiState.Error(e.message ?: "Không tải được danh sách cột")
                    },
                )
            }
        }
```

- [ ] **Step 4: Replace the stale `onSubmitReview` tests**

Delete the 2 existing tests `onSubmitReview sends final_cv_state and review_note then replaces the state with the server response` and `onSubmitReview shows a Vietnamese message when the photo is already decided` from `PoleListViewModelTest.kt`. Add in their place:

```kotlin
    @Test
    fun `onSubmitReview saves the draft locally and reflects it in aiResultState without calling the server`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(emptyList(), isStale = false))
            coEvery { polePhotoRepository.cachedResult("SERVER-PH-1") } returns null
            coEvery { polePhotoRepository.fetchResult(WORK_ORDER_ID, "SERVER-PH-1") } returns
                Result.success(photo("POLE-1", serverPhotoId = "SERVER-PH-1", aiStatus = "available", cvState = "on"))
            val vm =
                PoleListViewModel(
                    savedStateHandle(),
                    poleListRepository,
                    polePhotoRepository,
                    mockk<WorkOrderCompletionRepository>(relaxed = true),
                )

            vm.aiResultState.test {
                assertEquals(null, awaitItem())
                vm.onViewAiResult("SERVER-PH-1")
                assertEquals(PolePhotoResultUiState.Loading, awaitItem())
                awaitItem() // the fetched result, not under test here

                vm.onSubmitReview("SERVER-PH-1", "off", "Thấy đèn tắt")
                val saved = awaitItem() as PolePhotoResultUiState.Success
                assertEquals("off", saved.draftFinalCvState)
                assertEquals("Thấy đèn tắt", saved.draftReviewNote)
            }
            coVerify { polePhotoRepository.saveDraftReview("SERVER-PH-1", "off", "Thấy đèn tắt") }
            coVerify(exactly = 0) { polePhotoRepository.enqueuePendingReviews(any()) }
        }

    @Test
    fun `onComplete enqueues pending reviews before submitting completion`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(listOf(photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1")))
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            coEvery { polePhotoRepository.enqueuePendingReviews(WORK_ORDER_ID) } returns 2
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)
            vm.uiState.test {
                awaitItem() // Loading
                awaitItem() // Success
            }

            vm.surveyCompleted.test {
                vm.onComplete()
                assertEquals(2, awaitItem())
            }
            coVerify { polePhotoRepository.enqueuePendingReviews(WORK_ORDER_ID) }
            coVerify { completionRepository.submitCompletion(any(), any(), any(), any(), any()) }
        }
```

- [ ] **Step 5: Add a test for `hasPendingReview` in the row mapping**

Add to `PoleListViewModelTest.kt`:

```kotlin
    @Test
    fun `a pole's row carries hasPendingReview when its latest photo has an unsent draft`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(PoleListResult(listOf(PoleListItem("POLE-1", 1, "normal")), isStale = false))
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns
                flowOf(
                    listOf(
                        photo("POLE-1", serverPhotoId = "SERVER-PH-POLE-1").copy(draftFinalCvState = "off"),
                    ),
                )
            every { polePhotoRepository.observeUploadStatuses(listOf("PH-POLE-1")) } returns
                flowOf(mapOf("PH-POLE-1" to "done"))
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm =
                PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository, completionRepository)

            vm.uiState.test {
                awaitItem() // Loading
                val success = awaitItem() as PoleListUiState.Success
                assertEquals(true, success.poles.single().hasPendingReview)
            }
        }
```

- [ ] **Step 6: Run the ViewModel tests**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleListViewModelTest"`
Expected: PASS, all cases (the 2 old `reviewPhoto`-based tests are gone, replaced and new ones green).

- [ ] **Step 7: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoResultUiState.kt app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListUiState.kt app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModel.kt app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModelTest.kt
git commit -m "feat(pole-photo): wire batch review enqueue into onComplete"
```

---

### Task 5: UI — `ReviewCard` reads/writes drafts, button and toast text

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoResultBottomSheet.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoResultBottomSheetTest.kt`

**Interfaces:**
- Consumes: `PolePhotoResultUiState.Success.draftFinalCvState`/`.draftReviewNote` (Task 4).

- [ ] **Step 1: Update `ReviewCard`**

In `PolePhotoResultBottomSheet.kt`, replace the `ReviewCard` function body's state/derived-value section and submit button:

```kotlin
@Composable
private fun ReviewCard(
    poleId: String,
    state: PolePhotoResultUiState.Success,
    onSubmit: (finalCvState: String, reviewNote: String?) -> Unit,
) {
    val baselineCvState = state.draftFinalCvState ?: state.finalCvState
    val baselineNote = state.draftReviewNote ?: state.reviewNote
    var selected by
        rememberSaveable(poleId) {
            mutableStateOf(baselineCvState ?: state.cvState?.takeIf { it == "on" || it == "off" })
        }
    var note by rememberSaveable(poleId) { mutableStateOf(baselineNote.orEmpty()) }
    val noteRequired = selected != null && selected != state.cvState
    val normalizedNote = note.trim().ifBlank { null }
    val hasChange = selected != baselineCvState || normalizedNote != baselineNote
    val canSubmit = selected != null && (!noteRequired || note.isNotBlank()) && hasChange

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "KỸ SƯ XÁC NHẬN NHÃN",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ReviewChoiceSegment(selected = selected, aiCvState = state.cvState, onSelected = { selected = it })

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Ghi chú") },
            isError = noteRequired && note.isBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (noteRequired && note.isBlank()) {
            Text(
                text = "Cần ghi chú vì khác với nhận định của AI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (state.reviewedBy != null) {
            Text(
                text = "Đã review lúc ${DateFormatUtils.formatInstant(state.reviewedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        PrimaryButton(
            text = "Lưu xác nhận",
            onClick = { selected?.let { onSubmit(it, note.trim().ifBlank { null }) } },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 2: Update the toast text in `PoleListScreen.kt`**

Replace the `reviewSubmitted` toast line:

```kotlin
    LaunchedEffect(Unit) {
        viewModel.reviewSubmitted.collect {
            scope.launch { toastState.show("Đã lưu, sẽ gửi khi Hoàn tất") }
        }
    }
```

- [ ] **Step 3: Update the stale `PolePhotoResultBottomSheetTest.kt` cases**

Rename `review_card_disables_submit_right_after_a_successful_submit_with_no_further_edit` to seed via the draft fields instead of the confirmed ones:

```kotlin
    @Test
    fun review_card_disables_submit_right_after_saving_a_draft_with_no_further_edit() {
        composeRule.setContent {
            LuxMapTheme {
                PolePhotoResultBottomSheet(
                    poleId = "P-0103",
                    position = 3,
                    state = successState(cvState = "on").copy(draftFinalCvState = "on", draftReviewNote = null),
                    onDismiss = {},
                    onRetry = {},
                    onSubmitReview = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Lưu xác nhận").performScrollTo().assertIsNotEnabled()
    }
```

Rename every other `Gửi xác nhận` text lookup in this file to `Lưu xác nhận` (`review_card_does_not_require_a_note_when_agreeing_with_the_ai_label` and `review_card_blocks_submit_until_a_note_is_given_for_a_label_different_from_ai`, both currently call `composeRule.onNodeWithText("Gửi xác nhận")`).

Delete the test `review_card_shows_a_message_when_the_submission_fails` entirely — `reviewError` no longer exists, and there is no longer a network call from this screen that could produce that message.

- [ ] **Step 4: Run the instrumented tests on a real device**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.ui.polephoto.PolePhotoResultBottomSheetTest"` on a real device (per the memory note, the emulator's `InputManager` bug fails this whole suite for unrelated reasons — a real device is required to trust the result).
Expected: PASS, all remaining cases.

- [ ] **Step 5: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoResultBottomSheet.kt app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoResultBottomSheetTest.kt
git commit -m "style(pole-photo): make ReviewCard draft-aware and rename its action to Luu xac nhan"
```

---

### Task 6: UI — pending-review tag on `PoleRow`, "Hoàn tất" toast count

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreenTest.kt`

**Interfaces:**
- Consumes: `PoleListRowState.hasPendingReview` (Task 4), `PoleListViewModel.surveyCompleted: SharedFlow<Int>` (Task 4).

- [ ] **Step 1: Add the tag to `PoleRow`**

In `PoleListScreen.kt`'s `PoleRow`, inside the badges `Row` (right after the existing `if (pole.syncStatus != null) { StatusBadge(...) }` block), add:

```kotlin
                if (pole.hasPendingReview) {
                    StatusBadge(
                        text = "Đã review, chờ gửi",
                        colors = SyncStatus.QUEUED_ONLINE.badgeColors(),
                        icon = SyncStatus.QUEUED_ONLINE.icon(),
                    )
                }
```

- [ ] **Step 2: Update the "Hoàn tất" toast to report the pending-review count**

Replace the `surveyCompleted` collector in `PoleListScreen.kt`'s Route:

```kotlin
    LaunchedEffect(Unit) {
        viewModel.surveyCompleted.collect { pendingReviews ->
            val message =
                if (pendingReviews > 0) {
                    "Đã hoàn tất $workOrderId · $pendingReviews review đang gửi lên Kỹ sư bảo trì"
                } else {
                    "Đã hoàn tất $workOrderId · Kết quả đã gửi Kỹ sư bảo trì"
                }
            scope.launch { toastState.show(message) }
        }
    }
```

- [ ] **Step 3: Write the new instrumented test**

Add to `PoleListScreenTest.kt`:

```kotlin
    @Test
    fun success_state_shows_the_pending_review_tag_when_hasPendingReview_is_true() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state =
                        PoleListUiState.Success(
                            poles =
                                listOf(
                                    pole(serverPhotoId = "SERVER-PH-1", syncStatus = SyncStatus.DONE).copy(
                                        hasPendingReview = true,
                                    ),
                                ),
                        ),
                    onCapturePole = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã review, chờ gửi").assertExists()
    }
```

`pole(...)` returns a `PoleListRowState`, and `.copy(hasPendingReview = true)` works once Task 4 adds that field with a default — no change needed to the `pole()` helper itself.

- [ ] **Step 4: Run the instrumented tests on a real device**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleListScreenTest"` on a real device.
Expected: PASS, all cases including the new one.

- [ ] **Step 5: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreenTest.kt
git commit -m "feat(pole-photo): show a pending-review tag and report the review count on Hoan tat"
```

---

### Task 7: Gallery picker — second capture-method option

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureSource.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureScreen.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreenTest.kt`

**Interfaces:**
- Produces: `enum class PolePhotoCaptureSource { CAMERA, GALLERY }`; `PoleListScreen`'s `onCapturePole: (poleId: String, source: PolePhotoCaptureSource) -> Unit` (signature changed, was `(poleId: String) -> Unit`); `PolePhotoCaptureRoute(poleId: String, source: PolePhotoCaptureSource = PolePhotoCaptureSource.CAMERA, onDone: () -> Unit, ...)` (new `source` parameter).

- [ ] **Step 1: Add the enum**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

enum class PolePhotoCaptureSource {
    CAMERA,
    GALLERY,
}
```

- [ ] **Step 2: Add the `source` route argument**

In `Routes.kt`, change the `PolePhotoCapture` route:

```kotlin
    data object PolePhotoCapture : Routes {
        override val route = "survey/pole-photos/{workOrderId}/capture/{poleId}?source={source}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
            source: String = "camera",
        ) = "survey/pole-photos/$workOrderId/capture/$poleId?source=$source"
    }
```

- [ ] **Step 3: Update `NavGraph.kt`**

Add the import `com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureSource`. Change the `Routes.PolePhotoList` composable's `onCapturePole` callback:

```kotlin
                PoleListRoute(
                    workOrderId = polePhotoWorkOrderId,
                    onCapturePole = { poleId, source ->
                        navController.navigate(
                            Routes.PolePhotoCapture.createRoute(polePhotoWorkOrderId, poleId, source.name.lowercase()),
                        )
                    },
                    onDone = { navController.popBackStack() },
                )
```

Change the `Routes.PolePhotoCapture` composable to add the `source` nav argument and pass it through:

```kotlin
            composable(
                route = Routes.PolePhotoCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                        navArgument("source") {
                            type = NavType.StringType
                            defaultValue = "camera"
                        },
                    ),
            ) { backStackEntry ->
                val capturePoleId = backStackEntry.arguments?.getString("poleId") ?: return@composable
                val captureSource =
                    PolePhotoCaptureSource.entries.firstOrNull {
                        it.name.equals(backStackEntry.arguments?.getString("source"), ignoreCase = true)
                    } ?: PolePhotoCaptureSource.CAMERA
                PolePhotoCaptureRoute(
                    poleId = capturePoleId,
                    source = captureSource,
                    onDone = { navController.popBackStack() },
                )
            }
```

- [ ] **Step 4: Add the sheet option and change `onCapturePole`'s signature in `PoleListScreen.kt`**

Change every `onCapturePole: (poleId: String) -> Unit` signature (on `PoleListRoute` and the 2-param `PoleListScreen`) to `onCapturePole: (poleId: String, source: PolePhotoCaptureSource) -> Unit`.

In `PoleListRoute`, update the pass-through:

```kotlin
            onCapturePole = onCapturePole,
```

(unchanged line — it already forwards the callback as-is; only its type signature changes, inherited from the composable parameter above it.)

In the 2-param `PoleListScreen`, update `ReportMethodSheet`'s invocation:

```kotlin
    val methodSheetPoleId = reportMethodSheetPoleId
    if (methodSheetPoleId != null) {
        ReportMethodSheet(
            poleId = methodSheetPoleId,
            onDismiss = { reportMethodSheetPoleId = null },
            onChoosePhoto = {
                reportMethodSheetPoleId = null
                onCapturePole(methodSheetPoleId, PolePhotoCaptureSource.CAMERA)
            },
            onChooseGallery = {
                reportMethodSheetPoleId = null
                onCapturePole(methodSheetPoleId, PolePhotoCaptureSource.GALLERY)
            },
        )
    }
```

Replace `ReportMethodSheet` to add the 3rd option:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportMethodSheet(
    poleId: String,
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Báo lỗi cột",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = poleId, style = MaterialTheme.typography.titleLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ReportMethodOption(
                    icon = Icons.Filled.PhotoCamera,
                    label = "Chụp ảnh",
                    enabled = true,
                    onClick = onChoosePhoto,
                    modifier = Modifier.weight(1f),
                )
                ReportMethodOption(
                    icon = Icons.Filled.PhotoLibrary,
                    label = "Chọn từ thư viện",
                    enabled = true,
                    onClick = onChooseGallery,
                    modifier = Modifier.weight(1f),
                )
                ReportMethodOption(
                    icon = Icons.Filled.Videocam,
                    label = "Quay video",
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "Chưa hỗ trợ gửi video, dùng ảnh thay thế",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Add the imports `androidx.compose.material.icons.filled.PhotoLibrary` and `com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureSource` is unnecessary (same file/package) — only the `PhotoLibrary` icon import is new.

- [ ] **Step 5: Update `PoleListScreenTest.kt` for the 2 changed call sites and the new option**

The `pole()` helper is unaffected (it builds `PoleListRowState`, not the callback). Every test that currently passes `onCapturePole = { capturedPoleId = it }` must become `onCapturePole = { poleId, _ -> capturedPoleId = poleId }` (the type changed). Update `report_method_sheet_choosing_chup_anh_calls_onCapturePole_with_its_id` and `report_method_sheet_quay_video_is_disabled` accordingly, and every other test's `onCapturePole = {}` becomes `onCapturePole = { _, _ -> }`.

Update `success_state_tapping_a_pole_row_opens_the_report_method_sheet` to also assert the 3rd option exists:

```kotlin
    @Test
    fun success_state_tapping_a_pole_row_opens_the_report_method_sheet() {
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, _ -> },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chụp ảnh").assertExists()
        composeRule.onNodeWithText("Chọn từ thư viện").assertExists()
        composeRule.onNodeWithText("Quay video").assertExists()
    }
```

Add a new test:

```kotlin
    @Test
    fun report_method_sheet_choosing_the_gallery_option_calls_onCapturePole_with_GALLERY() {
        var capturedSource: PolePhotoCaptureSource? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, source -> capturedSource = source },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Chọn từ thư viện").performClick()
        assertEquals(PolePhotoCaptureSource.GALLERY, capturedSource)
    }
```

- [ ] **Step 6: Run the instrumented tests on a real device**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleListScreenTest"` on a real device.
Expected: PASS, all cases including the 2 new/updated ones.

- [ ] **Step 7: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureSource.kt app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreenTest.kt
git commit -m "feat(pole-photo): add a gallery option to the report-method sheet"
```

---

### Task 8: Gallery picker — branch `PolePhotoCaptureRoute` on source

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureScreen.kt`

**Interfaces:**
- Consumes: `PolePhotoCaptureSource` (Task 7).

- [ ] **Step 1: Add the `source` parameter and branch the capture `LaunchedEffect`**

In `PolePhotoCaptureScreen.kt`, add the imports `androidx.activity.result.PickVisualMediaRequest` and `com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureSource` is unnecessary (same package) — only `PickVisualMediaRequest` is new. Change `PolePhotoCaptureRoute`'s signature and body:

```kotlin
@Composable
fun PolePhotoCaptureRoute(
    poleId: String,
    source: PolePhotoCaptureSource = PolePhotoCaptureSource.CAMERA,
    onDone: () -> Unit,
    viewModel: PolePhotoCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                previewFile = file
            } else {
                onDone()
            }
        }

    val pickMediaLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val file = viewModel.outputFile(context.filesDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                previewFile = file
            } else {
                onDone()
            }
        }

    fun closePreview() {
        previewFile?.delete()
        onDone()
    }

    if (previewFile != null) {
        BackHandler { closePreview() }
    }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    fun launchGalleryPicker() {
        pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[Manifest.permission.CAMERA] == true) {
                launchCamera()
            } else {
                onDone()
            }
        }

    LaunchedEffect(Unit) {
        when (source) {
            PolePhotoCaptureSource.CAMERA -> {
                val cameraGranted =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                when {
                    cameraGranted -> launchCamera()
                    hasRequestedPermissions -> onDone()
                    else -> {
                        hasRequestedPermissions = true
                        permissionsLauncher.launch(
                            arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
                        )
                    }
                }
            }
            PolePhotoCaptureSource.GALLERY -> launchGalleryPicker()
        }
    }

    LuxMapTheme(forceDark = true) {
        val fileToPreview = previewFile
        if (fileToPreview != null) {
            PolePhotoPreview(
                file = fileToPreview,
                poleId = poleId,
                isSaving = isSaving,
                retakeLabel = if (source == PolePhotoCaptureSource.GALLERY) "Chọn ảnh khác" else "Chụp lại",
                onConfirm = {
                    isSaving = true
                    viewModel.onPhotoCaptured(fileToPreview, onDone)
                },
                onRetake = {
                    fileToPreview.delete()
                    previewFile = null
                    when (source) {
                        PolePhotoCaptureSource.CAMERA -> launchCamera()
                        PolePhotoCaptureSource.GALLERY -> launchGalleryPicker()
                    }
                },
                onClose = ::closePreview,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(PolePhotoPreviewBackground)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
```

Gallery-sourced photos are GPS-tagged with the device's current location at save time, exactly like camera-sourced ones (`PolePhotoCaptureViewModel.onPhotoCaptured` is unchanged by this task) — an accepted limitation already recorded in the spec (section 2/6), not a gap to fix here.

- [ ] **Step 2: Thread `retakeLabel` through `PolePhotoPreview` and `RetakeButton`**

Change `PolePhotoPreview`'s signature to accept and forward `retakeLabel: String`:

```kotlin
@Composable
private fun PolePhotoPreview(
    file: File,
    poleId: String,
    isSaving: Boolean,
    retakeLabel: String,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
```

In its body, change the `RetakeButton` call:

```kotlin
            RetakeButton(label = retakeLabel, enabled = !isSaving, onClick = onRetake, modifier = Modifier.weight(1f))
```

Change `RetakeButton` to take the label instead of hardcoding it:

```kotlin
@Composable
private fun RetakeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = PrimaryButtonHeight).alpha(if (enabled) 1f else 0.4f),
        shape = RoundedCornerShape(Radius.control),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, disabledContentColor = Color.White),
        contentPadding = PolePhotoButtonContentPadding,
    ) {
        Icon(imageVector = Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(label)
    }
}
```

- [ ] **Step 3: Build and manually verify on a real device**

Run: `./gradlew assembleDebug` to confirm it compiles.
On a real device: open a pole, choose "Chọn từ thư viện", pick an image from the device gallery, confirm the preview shows it with a "Chọn ảnh khác" button (not "Chụp lại"), confirm saving it behaves exactly like a camera-captured photo (uploads, gets an AI result, is reviewable). Then back out of the picker without picking anything and confirm it returns to the Pole List screen with nothing new in the list (Review Focus item: gallery cancel must behave like camera cancel).

- [ ] **Step 4: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureScreen.kt
git commit -m "feat(pole-photo): launch the gallery picker when the source is GALLERY"
```

---

### After all 8 tasks

Push the branch and open a PR into `dev` per `CLAUDE.md`'s git workflow (never `main`). Flag in the PR description the one real-device-only check this plan cannot run for you: Task 8 Step 3's manual gallery-pick verification, plus the full `connectedDebugAndroidTest` run for `PolePhotoResultBottomSheetTest` and `PoleListScreenTest` if it has not already been run on a real device during Tasks 5–7.
