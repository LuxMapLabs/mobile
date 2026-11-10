# Pole Video Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Field Engineer record one short video clip per pole (system camera, 3–30s), watch it back, and upload it through the now-confirmed-ready backend endpoint — enabling the "Quay video" option that has sat disabled in the report-method sheet since the pole-photo feature shipped.

**Architecture:** Reuses the existing pole-photo pipeline end to end — the same `LocalPolePhotoEntity`/`PolePhotoDao`/`PolePhotoRepository`, the same `sync_queue` offline-first mechanism, the same review/decision UI — with one new discriminator column (`mediaType`) routing to a new upload op and a new, separate capture screen (video recording + ExoPlayer preview instead of a still-image preview). No CameraX, no manual exposure lock: the backend's own contract-drift.md explicitly marks this clip's `capture_config = "not_verified"` and excludes it from the continuous-survey flow's locked-camera rule.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Retrofit/kotlinx.serialization, OkHttp (raw-body PUT), Coroutines, `androidx.media3` ExoPlayer (already a dependency, already used in `CoverageScreen.kt`), MockK/Turbine (tests). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-11-10-pole-video-capture-design.md`

## Global Constraints

- No new third-party dependency — the system video-capture intent uses a small custom `ActivityResultContract` (standard `androidx.activity` API, same family already used for `TakePicture()`/`PickVisualMedia()`); the preview uses `androidx.media3` ExoPlayer, already present and already used this way in `CoverageScreen.kt`.
- No `video` sub-object field added to any DTO — `ignoreUnknownKeys = true` is already set globally (`ApiClient.kt`), so the server sending it is harmless to ignore (spec section 2's scope cut).
- Run `./gradlew ktlintCheck` before every commit in this plan and fix any violation first — do not commit with it failing.
- Keep each commit under ~400 changed lines (`CLAUDE.md`'s commit-size rule) — every task below fits well inside that on its own.
- This repo has `local`/`server` product flavors (confirmed this session) — Gradle task names are `testLocalDebugUnitTest` and `connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<FQCN>`, not the plain `testDebugUnitTest`/`connectedDebugAndroidTest` names a stock Android project would use.
- DB version bump via `fallbackToDestructiveMigration()` (already configured in `DatabaseModule.kt`) — no `Migration` object, no migration test, matching the existing convention for every prior version bump in this app.
- No instrumented (`androidTest`) test for the `PoleVideoCaptureScreen` composable itself (system camera intent + ExoPlayer playback, not reliably mockable) — same reasoning already applied to the photo/gallery capture screens; verify by hand on a real device instead.

## Review Focus

- Backing out of the system video-capture intent (or it returning no result) must return to the Pole List screen without writing a Room row or crashing — same as the existing photo/gallery cancel paths.
- A clip shorter than 3s or longer than 30.5s must be caught client-side (the confirm button disabled, an inline message shown) before any upload is attempted — the server's `VIDEO_TOO_SHORT`/`VIDEO_TOO_LONG` must be a backstop, not the primary guard, or a Field Engineer wastes a 100 MB upload on a clip that was always going to be rejected.
- `UploadPoleVideoSyncHandler` must bucket a 409 (idempotency race, recoverable) separately from 400/413/415 (permanent, e.g. a corrupt or oversized clip) the same way `UploadPolePhotoSyncHandler` already does — collapsing them either spins forever on a clip that can never succeed, or gives up on a conflict a retry would resolve.
- `captureVideo()` must write `mediaType = "video"` on the Room row and enqueue `upload_pole_video` (never `upload_pole_photo`) — a video routed through the photo multipart endpoint fails server-side validation with a confusing error, not a clean rejection.
- `isValidPoleVideoDuration`'s boundary must match the server's accepted window exactly (3000–30500 ms inclusive, confirmed from `contract-drift.md` PV-7: floor 3s, ceiling 30s + 0.5s grace) — an off-by-one either rejects a clip the server would accept, or lets one through that the server will 400 after a full upload.

---

### Task 1: Room — `mediaType` column, `captureVideo()` on the repository

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalPolePhotoEntity.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/PolePhotoRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/RealPolePhotoRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/FakePolePhotoRepository.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/RealPolePhotoRepositoryTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/FakePolePhotoRepositoryTest.kt`

**Interfaces:**
- Produces: `LocalPolePhotoEntity.mediaType: String` (default `"photo"`); `PolePhotoRepository.captureVideo(workOrderId: String, poleId: String, filePath: String, lat: Double, lng: Double): LocalPolePhotoEntity` (suspend) — writes a row with `mediaType = "video"` and enqueues an `"upload_pole_video"` sync op (clientOpId = the new row's `photoId`, same payload shape as `capture()`'s `"upload_pole_photo"` op).

- [ ] **Step 1: Add the column**

In `LocalPolePhotoEntity.kt`, add after `draftReviewNote`:

```kotlin
    // "photo" (camera or gallery) or "video" - decides which sync op capture()/captureVideo()
    // enqueues. Added for the pole-video feature; existing rows default to "photo".
    val mediaType: String = "photo",
```

- [ ] **Step 2: Bump the database version**

In `AppDatabase.kt`, change `version = 14` to `version = 15`.

- [ ] **Step 3: Add `captureVideo` to the interface**

In `PolePhotoRepository.kt`, add after `capture`:

```kotlin
    // Same shape as capture(), for a recorded video clip instead of a still photo. Writes
    // mediaType = "video" and enqueues "upload_pole_video" instead of "upload_pole_photo".
    suspend fun captureVideo(
        workOrderId: String,
        poleId: String,
        filePath: String,
        lat: Double,
        lng: Double,
    ): LocalPolePhotoEntity
```

- [ ] **Step 4: Implement in `RealPolePhotoRepository`**

Add after `capture`:

```kotlin
        override suspend fun captureVideo(
            workOrderId: String,
            poleId: String,
            filePath: String,
            lat: Double,
            lng: Double,
        ): LocalPolePhotoEntity {
            val entity =
                LocalPolePhotoEntity(
                    photoId = UUID.randomUUID().toString(),
                    workOrderId = workOrderId,
                    poleId = poleId,
                    filePath = filePath,
                    capturedAt = Instant.now(),
                    lat = lat,
                    lng = lng,
                    mediaType = "video",
                )
            dao.insert(entity)
            syncQueueManager.enqueue(
                opType = "upload_pole_video",
                payloadJson = json.encodeToString(PolePhotoSyncPayload(entity.photoId)),
                clientOpId = entity.photoId,
            )
            return entity
        }
```

- [ ] **Step 5: Implement in `FakePolePhotoRepository`**

Add after `capture` (no sync simulation, matching how `capture()` itself behaves in this Fake — it has no `SyncQueueManager` dependency at all):

```kotlin
        override suspend fun captureVideo(
            workOrderId: String,
            poleId: String,
            filePath: String,
            lat: Double,
            lng: Double,
        ): LocalPolePhotoEntity {
            val entity =
                LocalPolePhotoEntity(
                    photoId = UUID.randomUUID().toString(),
                    workOrderId = workOrderId,
                    poleId = poleId,
                    filePath = filePath,
                    capturedAt = Instant.now(),
                    lat = lat,
                    lng = lng,
                    mediaType = "video",
                )
            dao.insert(entity)
            return entity
        }
```

- [ ] **Step 6: Write the failing tests**

Add to `RealPolePhotoRepositoryTest.kt`:

```kotlin
    @Test
    fun `captureVideo writes the local row with mediaType video then enqueues an upload_pole_video op`() =
        runTest {
            val api = mockk<WorkOrdersApi>(relaxed = true)
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val result =
                repository.captureVideo(
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/clip.mp4",
                    lat = 10.97,
                    lng = 106.49,
                )

            coVerify {
                dao.insert(
                    match {
                        it.photoId == result.photoId && it.workOrderId == "WO-1" &&
                            it.poleId == "POLE-1" && it.filePath == "/data/clip.mp4" && it.mediaType == "video"
                    },
                )
            }
            coVerify {
                syncQueueManager.enqueue(
                    opType = "upload_pole_video",
                    payloadJson = "{\"photo_id\":\"${result.photoId}\"}",
                    clientOpId = result.photoId,
                )
            }
        }
```

Add to `FakePolePhotoRepositoryTest.kt`:

```kotlin
    @Test
    fun `captureVideo writes a row with mediaType video`() =
        runTest {
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val repository = FakePolePhotoRepository(dao)

            val result =
                repository.captureVideo(
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/pole-videos/x.mp4",
                    lat = 10.0,
                    lng = 106.0,
                )

            assertEquals("video", result.mediaType)
            assertEquals("/data/pole-videos/x.mp4", result.filePath)
            coVerify(exactly = 1) { dao.insert(result) }
        }
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.data.RealPolePhotoRepositoryTest" --tests "com.luxmap.feature.survey.data.FakePolePhotoRepositoryTest"`
Expected: PASS, all cases (old ones unaffected, 2 new ones green).

- [ ] **Step 8: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/entity/LocalPolePhotoEntity.kt app/src/main/java/com/luxmap/core/database/AppDatabase.kt app/src/main/java/com/luxmap/feature/survey/data/PolePhotoRepository.kt app/src/main/java/com/luxmap/feature/survey/data/RealPolePhotoRepository.kt app/src/main/java/com/luxmap/feature/survey/data/FakePolePhotoRepository.kt app/src/test/java/com/luxmap/feature/survey/data/RealPolePhotoRepositoryTest.kt app/src/test/java/com/luxmap/feature/survey/data/FakePolePhotoRepositoryTest.kt
git commit -m "feat(pole-video): add mediaType column and captureVideo()"
```

---

### Task 2: Network — `uploadPoleVideo`, `UploadPoleVideoSyncHandler`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/sync/UploadPoleVideoSyncHandler.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/data/sync/UploadPoleVideoSyncHandlerTest.kt`
- Modify: `app/src/main/java/com/luxmap/di/SyncModule.kt`

**Interfaces:**
- Consumes: `PolePhotoDao.byId`/`.updateServerPhotoId` (existing); `PolePhotoSyncPayload(photoId: String)` (existing, reused as-is — same shape the photo and review ops already use).
- Produces: `WorkOrdersApi.uploadPoleVideo(...): PolePhotoUploadResponseDto`; `UploadPoleVideoSyncHandler : SyncOpHandler` with `opType = "upload_pole_video"`, bound into the `Set<SyncOpHandler>` multibinding.

- [ ] **Step 1: Add the Retrofit method**

In `WorkOrdersApi.kt`, add the imports `retrofit2.http.Header` and `retrofit2.http.PUT`, then add after `uploadPolePhoto`:

```kotlin
    // POLE-VIDEO PV-4: raw MP4 body (not multipart), query params instead of form fields,
    // client_op_id in the path. See docs/contract-drift.md "POLE-VIDEO".
    @PUT("api/v1/work-orders/{id}/pole-videos/{client_op_id}")
    suspend fun uploadPoleVideo(
        @Path("id") id: String,
        @Path("client_op_id") clientOpId: String,
        @Query("pole_id") poleId: String,
        @Query("captured_at") capturedAt: String,
        @Query("lat") lat: String,
        @Query("lng") lng: String,
        @Header("X-Content-SHA256") sha256: String,
        @Body body: RequestBody,
    ): PolePhotoUploadResponseDto
```

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoUploadResponseDto
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
import java.io.File
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

private fun videoEntity(filePath: String) =
    LocalPolePhotoEntity(
        photoId = "PHOTO-1",
        workOrderId = "WO-1",
        poleId = "POLE-1",
        filePath = filePath,
        capturedAt = Instant.parse("2026-11-10T10:00:00Z"),
        lat = 10.97,
        lng = 106.49,
        mediaType = "video",
    )

class UploadPoleVideoSyncHandlerTest {
    @Test
    fun `a successful upload returns Done`() =
        runTest {
            val tempFile = File.createTempFile("pole-video", ".mp4").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns videoEntity(tempFile.absolutePath)
            coEvery {
                api.uploadPoleVideo(any(), any(), any(), any(), any(), any(), any(), any())
            } returns PolePhotoUploadResponseDto(photoId = "PPH-000002")
            coJustRun { dao.updateServerPhotoId("PHOTO-1", "PPH-000002") }
            val handler = UploadPoleVideoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateServerPhotoId("PHOTO-1", "PPH-000002") }
        }

    @Test
    fun `a missing local row returns Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns null
            val handler = UploadPoleVideoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-video", ".mp4").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns videoEntity(tempFile.absolutePath)
            coEvery {
                api.uploadPoleVideo(any(), any(), any(), any(), any(), any(), any(), any())
            } throws httpException(500)
            val handler = UploadPoleVideoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }

    @Test
    fun `a 409 idempotency conflict maps to Conflict, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-video", ".mp4").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns videoEntity(tempFile.absolutePath)
            coEvery {
                api.uploadPoleVideo(any(), any(), any(), any(), any(), any(), any(), any())
            } throws httpException(409, "IDEMPOTENCY_CONFLICT")
            val handler = UploadPoleVideoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Conflict)
        }

    @Test
    fun `a 400 video-too-short maps to Failed, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-video", ".mp4").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns videoEntity(tempFile.absolutePath)
            coEvery {
                api.uploadPoleVideo(any(), any(), any(), any(), any(), any(), any(), any())
            } throws httpException(400, "VIDEO_TOO_SHORT")
            val handler = UploadPoleVideoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 413 maps to Failed, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("pole-video", ".mp4").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            coEvery { dao.byId("PHOTO-1") } returns videoEntity(tempFile.absolutePath)
            coEvery {
                api.uploadPoleVideo(any(), any(), any(), any(), any(), any(), any(), any())
            } throws httpException(413, "UPLOAD_TOO_LARGE")
            val handler = UploadPoleVideoSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(PolePhotoSyncPayload("PHOTO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }
}
```

- [ ] **Step 3: Run the tests to see them fail**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.UploadPoleVideoSyncHandlerTest"`
Expected: FAIL — `UploadPoleVideoSyncHandler` does not exist yet.

- [ ] **Step 4: Write the handler**

```kotlin
package com.luxmap.feature.survey.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

class UploadPoleVideoSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: PolePhotoDao,
    ) : SyncOpHandler {
        override val opType = "upload_pole_video"

        override suspend fun handle(
            payloadJson: String,
            onProgress: suspend (Long, Long) -> Unit,
        ): SyncOpResult {
            val payload = Json.decodeFromString<PolePhotoSyncPayload>(payloadJson)
            val photo = dao.byId(payload.photoId) ?: return SyncOpResult.Failed("Local pole photo row missing")

            return try {
                val file = File(photo.filePath)
                val response =
                    api.uploadPoleVideo(
                        id = photo.workOrderId,
                        clientOpId = photo.photoId,
                        poleId = photo.poleId,
                        capturedAt = photo.capturedAt.toString(),
                        lat = photo.lat.toString(),
                        lng = photo.lng.toString(),
                        sha256 = sha256Of(file),
                        body = file.asRequestBody("video/mp4".toMediaType()),
                    )
                dao.updateServerPhotoId(photo.photoId, response.photoId)
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 413, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }

        private fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead = input.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
```

- [ ] **Step 5: Run the tests to see them pass**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.data.sync.UploadPoleVideoSyncHandlerTest"`
Expected: PASS, all 6 cases.

- [ ] **Step 6: Bind the handler in `SyncModule`**

In `SyncModule.kt`, add the import `com.luxmap.feature.survey.data.sync.UploadPoleVideoSyncHandler` and, after `bindSubmitPolePhotoReviewSyncHandler`, add:

```kotlin
    @Binds
    @IntoSet
    abstract fun bindUploadPoleVideoSyncHandler(impl: UploadPoleVideoSyncHandler): SyncOpHandler
```

- [ ] **Step 7: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt app/src/main/java/com/luxmap/feature/survey/data/sync/UploadPoleVideoSyncHandler.kt app/src/test/java/com/luxmap/feature/survey/data/sync/UploadPoleVideoSyncHandlerTest.kt app/src/main/java/com/luxmap/di/SyncModule.kt
git commit -m "feat(pole-video): add uploadPoleVideo endpoint and sync handler"
```

---

### Task 3: Duration validation — `isValidPoleVideoDuration`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoDuration.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoDurationTest.kt`

**Interfaces:**
- Produces: `isValidPoleVideoDuration(durationMs: Long): Boolean` — true for 3000..30500 ms inclusive (server's 3s floor, 30s ceiling + 0.5s grace, per `contract-drift.md` PV-7).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoleVideoDurationTest {
    @Test
    fun `a clip just under 3 seconds is invalid`() {
        assertFalse(isValidPoleVideoDuration(2_999))
    }

    @Test
    fun `a clip at exactly 3 seconds is valid`() {
        assertTrue(isValidPoleVideoDuration(3_000))
    }

    @Test
    fun `a clip at exactly 30_5 seconds is valid`() {
        assertTrue(isValidPoleVideoDuration(30_500))
    }

    @Test
    fun `a clip just over 30_5 seconds is invalid`() {
        assertFalse(isValidPoleVideoDuration(30_501))
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleVideoDurationTest"`
Expected: FAIL — `isValidPoleVideoDuration` does not exist yet.

- [ ] **Step 3: Write the function**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

private const val MinimumDurationMs = 3_000L
private const val MaximumDurationMs = 30_500L

fun isValidPoleVideoDuration(durationMs: Long): Boolean = durationMs in MinimumDurationMs..MaximumDurationMs
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleVideoDurationTest"`
Expected: PASS, all 4 cases.

- [ ] **Step 5: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoDuration.kt app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoDurationTest.kt
git commit -m "feat(pole-video): add isValidPoleVideoDuration"
```

---

### Task 4: `PoleVideoCaptureViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoCaptureViewModel.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoCaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `PolePhotoRepository.captureVideo` (Task 1); `LocationTracker.getCurrentLocation(): LatLng?` (existing).
- Produces: `PoleVideoCaptureViewModel.outputFile(filesDir: File): File`; `PoleVideoCaptureViewModel.onVideoCaptured(file: File, onDone: () -> Unit)`; companion constants `WORK_ORDER_ID_ARG`, `POLE_ID_ARG`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.PolePhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File

private const val WORK_ORDER_ID = "WO-1"
private const val POLE_ID = "POLE-1"

private fun savedStateHandle() =
    SavedStateHandle(
        mapOf(
            PoleVideoCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID,
            PoleVideoCaptureViewModel.POLE_ID_ARG to POLE_ID,
        ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PoleVideoCaptureViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onVideoCaptured sends the real GPS fix to the repository and calls onDone`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PoleVideoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = File("x.mp4")
            var done = false

            vm.onVideoCaptured(file) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureVideo(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 10.0,
                    lng = 106.0,
                )
            }
            assertTrue(done)
        }

    @Test
    fun `onVideoCaptured falls back to 0,0 when the GPS fix is unavailable`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns null
            val vm = PoleVideoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = File("x.mp4")

            vm.onVideoCaptured(file) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureVideo(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = file.absolutePath,
                    lat = 0.0,
                    lng = 0.0,
                )
            }
        }

    @Test
    fun `onVideoCaptured still calls onDone when the repository write throws`() =
        runTest {
            val repository = mockk<PolePhotoRepository>()
            coEvery {
                repository.captureVideo(workOrderId = any(), poleId = any(), filePath = any(), lat = any(), lng = any())
            } throws RuntimeException("disk full")
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PoleVideoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            var done = false

            vm.onVideoCaptured(File("x.mp4")) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(done)
        }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleVideoCaptureViewModelTest"`
Expected: FAIL — `PoleVideoCaptureViewModel` does not exist yet.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.survey.data.PolePhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PoleVideoCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PolePhotoRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])
        private val videoFileName: String = UUID.randomUUID().toString()

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-videos/$workOrderId")
            dir.mkdirs()
            return File(dir, "$videoFileName.mp4")
        }

        fun onVideoCaptured(
            file: File,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val location = locationTracker.getCurrentLocation()
                runCatching {
                    repository.captureVideo(
                        workOrderId = workOrderId,
                        poleId = poleId,
                        filePath = file.absolutePath,
                        lat = location?.latitude ?: 0.0,
                        lng = location?.longitude ?: 0.0,
                    )
                }
                onDone()
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
            const val POLE_ID_ARG = "poleId"
        }
    }
```

- [ ] **Step 4: Run the tests to see them pass**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleVideoCaptureViewModelTest"`
Expected: PASS, all 3 cases.

- [ ] **Step 5: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoCaptureViewModel.kt app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoCaptureViewModelTest.kt
git commit -m "feat(pole-video): add PoleVideoCaptureViewModel"
```

---

### Task 5: `PoleVideoCaptureScreen` — record, preview, confirm

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoCaptureScreen.kt`

**Interfaces:**
- Consumes: `PoleVideoCaptureViewModel` (Task 4); `isValidPoleVideoDuration` (Task 3).
- Produces: `@Composable fun PoleVideoCaptureRoute(poleId: String, onDone: () -> Unit, viewModel: PoleVideoCaptureViewModel = hiltViewModel())`.

- [ ] **Step 1: Write the screen**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.luxmap.core.theme.Blue500
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Navy900
import com.luxmap.core.theme.PrimaryButtonHeight
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val PoleVideoPreviewBackground = Color(0xFF0B1526)
private val PoleVideoConfirmSavingColor = Color(0xFF3B5A8A)
private val PoleVideoButtonContentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.md)
private const val MaxDurationLimitSeconds = 30

// MediaStore.ACTION_VIDEO_CAPTURE via a custom contract, because the stock
// ActivityResultContracts.CaptureVideo() has no way to set EXTRA_DURATION_LIMIT. Same shape
// (createIntent/parseResult, FLAG_GRANT_*_URI_PERMISSION) as AOSP's own TakePicture() contract.
private class CaptureVideoWithDurationLimit(
    private val durationLimitSeconds: Int,
) : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(
        context: Context,
        input: Uri,
    ): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, input)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, durationLimitSeconds)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?,
    ): Boolean = resultCode == Activity.RESULT_OK
}

private fun readVideoDurationMs(file: File): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } finally {
        retriever.release()
    }
}

@Composable
fun PoleVideoCaptureRoute(
    poleId: String,
    onDone: () -> Unit,
    viewModel: PoleVideoCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var hasRequestedPermissions by rememberSaveable { mutableStateOf(false) }

    val captureVideoLauncher =
        rememberLauncherForActivityResult(CaptureVideoWithDurationLimit(MaxDurationLimitSeconds)) { success ->
            val file = pendingFile
            if (success && file != null) {
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
        captureVideoLauncher.launch(uri)
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

    LuxMapTheme(forceDark = true) {
        val fileToPreview = previewFile
        if (fileToPreview != null) {
            PoleVideoPreview(
                file = fileToPreview,
                poleId = poleId,
                isSaving = isSaving,
                onConfirm = {
                    isSaving = true
                    viewModel.onVideoCaptured(fileToPreview, onDone)
                },
                onRetake = {
                    fileToPreview.delete()
                    previewFile = null
                    launchCamera()
                },
                onClose = ::closePreview,
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(PoleVideoPreviewBackground)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun PoleVideoPreview(
    file: File,
    poleId: String,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var durationMs by remember(file) { mutableStateOf<Long?>(null) }
    LaunchedEffect(file) {
        durationMs = withContext(Dispatchers.IO) { readVideoDurationMs(file) }
    }
    val isValidDuration = durationMs?.let(::isValidPoleVideoDuration) ?: false

    val player =
        remember(file) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
        }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(modifier = Modifier.fillMaxSize().background(PoleVideoPreviewBackground)) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Navy900, PoleVideoPreviewBackground)))
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.xs, bottom = Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .alpha(if (isSaving) 0.4f else 1f)
                            .clickable(enabled = !isSaving, onClick = onClose)
                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(Radius.control)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
                }
                Column {
                    Text(text = "ID cột đèn", style = LabelTextStyle, color = Color.White.copy(alpha = 0.7f))
                    Text(text = poleId, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.block)),
            )
        }
        if (durationMs != null && !isValidDuration) {
            Text(
                text = "Clip quá ngắn hoặc quá dài (cần 3-30 giây), vui lòng quay lại",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RetakeButton(enabled = !isSaving, onClick = onRetake, modifier = Modifier.weight(1f))
            ConfirmButton(
                isSaving = isSaving,
                enabled = isValidDuration,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RetakeButton(
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
        contentPadding = PoleVideoButtonContentPadding,
    ) {
        Icon(imageVector = Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text("Quay lại")
    }
}

@Composable
private fun ConfirmButton(
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isSaving,
        modifier = modifier.heightIn(min = PrimaryButtonHeight),
        shape = RoundedCornerShape(Radius.control),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Blue500,
                contentColor = Color.White,
                disabledContainerColor = PoleVideoConfirmSavingColor,
                disabledContentColor = Color.White,
            ),
        contentPadding = PoleVideoButtonContentPadding,
    ) {
        if (isSaving) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(if (isSaving) "Đang lưu…" else "Dùng video này")
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew compileLocalDebugKotlin`
Expected: BUILD SUCCESSFUL (no automated test for this screen — see Global Constraints).

- [ ] **Step 3: ktlint and commit**

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleVideoCaptureScreen.kt
git commit -m "feat(pole-video): add PoleVideoCaptureScreen"
```

---

### Task 6: Enable "Quay video" and wire navigation

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreenTest.kt`

**Interfaces:**
- Consumes: `PoleVideoCaptureRoute` (Task 5).
- Produces: `PoleListScreen`'s and `PoleListRoute`'s new `onCaptureVideo: (poleId: String) -> Unit` parameter (default `{}` on `PoleListScreen`, required on `PoleListRoute`); `Routes.PoleVideoCapture`.

- [ ] **Step 1: Add the route**

In `Routes.kt`, add after `PolePhotoCapture`:

```kotlin
    data object PoleVideoCapture : Routes {
        override val route = "survey/pole-videos/{workOrderId}/capture/{poleId}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
        ) = "survey/pole-videos/$workOrderId/capture/$poleId"
    }
```

- [ ] **Step 2: Enable the sheet option in `PoleListScreen.kt`**

Add `onCaptureVideo: (poleId: String) -> Unit = {}` to `PoleListScreen`'s parameter list (right after `onCapturePole`), and `onCaptureVideo: (poleId: String) -> Unit` (no default) to `PoleListRoute`'s parameter list (right after `onCapturePole`). In `PoleListRoute`'s call to the 2-param `PoleListScreen`, add `onCaptureVideo = onCaptureVideo,` right after `onCapturePole = onCapturePole,`.

Replace the `ReportMethodSheet` call site:

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
            onChooseVideo = {
                reportMethodSheetPoleId = null
                onCaptureVideo(methodSheetPoleId)
            },
        )
    }
}
```

Replace `ReportMethodSheet` and its "Quay video" option — remove the disabled state and the now-stale caption:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportMethodSheet(
    poleId: String,
    onDismiss: () -> Unit,
    onChoosePhoto: () -> Unit,
    onChooseGallery: () -> Unit,
    onChooseVideo: () -> Unit,
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
                    enabled = true,
                    onClick = onChooseVideo,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
```

(`ReportMethodOption` itself is unchanged — it already supports `enabled = true` with no lock icon.)

- [ ] **Step 3: Wire `NavGraph.kt`**

Add the import `com.luxmap.feature.survey.ui.polephoto.PoleVideoCaptureRoute`. In the `Routes.PolePhotoList` composable's `PoleListRoute(...)` call, add after `onCapturePole`:

```kotlin
                    onCaptureVideo = { poleId ->
                        navController.navigate(Routes.PoleVideoCapture.createRoute(polePhotoWorkOrderId, poleId))
                    },
```

Add a new composable block, after the `Routes.PolePhotoCapture` one:

```kotlin
            composable(
                route = Routes.PoleVideoCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                val captureVideoPoleId = backStackEntry.arguments?.getString("poleId") ?: return@composable
                PoleVideoCaptureRoute(poleId = captureVideoPoleId, onDone = { navController.popBackStack() })
            }
```

- [ ] **Step 4: Update the stale disabled-video test, add the new enabled-video test**

In `PoleListScreenTest.kt`, delete the test `report_method_sheet_quay_video_is_disabled` entirely (the behavior it pins — tapping "Quay video" does nothing and shows a caption — no longer exists). Add in its place:

```kotlin
    @Test
    fun report_method_sheet_choosing_video_calls_onCaptureVideo_with_its_id() {
        var capturedPoleId: String? = null
        composeRule.setContent {
            LuxMapTheme {
                PoleListScreen(
                    workOrderId = "WO-1",
                    state = PoleListUiState.Success(poles = listOf(pole(poleId = "POLE-1"))),
                    onCapturePole = { _, _ -> },
                    onCaptureVideo = { capturedPoleId = it },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("POLE-1").performClick()
        composeRule.onNodeWithText("Quay video").performClick()
        assertEquals("POLE-1", capturedPoleId)
    }
```

- [ ] **Step 5: Run the instrumented tests on a real device**

Run: `./gradlew connectedLocalDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.survey.ui.polephoto.PoleListScreenTest`
Expected: PASS, all cases (one removed, one added — same total minus the deleted one, plus one).

- [ ] **Step 6: Run the full unit suite, then ktlint, then commit**

Run: `./gradlew testLocalDebugUnitTest`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew ktlintCheck`, fix any violation.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreenTest.kt
git commit -m "feat(pole-video): enable Quay video and wire navigation"
```

- [ ] **Step 7: Manual real-device verification**

Install the app, open a pole, choose "Quay video", record a clip: confirm the system video camera opens, the recording stops at 30s if left running, the preview plays the clip back with "Quay lại"/"Dùng video này", a clip under 3s shows the inline error and blocks "Dùng video này", a valid clip saves and the pole list reflects it (same pending-review/sync badges the photo flow already shows), and the upload succeeds end to end (visible via the existing AI-result sheet once the sync queue processes it). Flag any gap found here in the PR description — this is the one check this plan cannot run for you.

---

### After all 6 tasks

Push the branch and open a PR into `dev` per `CLAUDE.md`'s git workflow (never `main`). Flag in the PR description that Task 6 Step 7's manual on-device recording/upload walkthrough is the one check this plan could not run for you.
