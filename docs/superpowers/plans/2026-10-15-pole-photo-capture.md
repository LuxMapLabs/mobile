# Pole Photo Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Field Engineer open an assigned survey work order, pick a pole from its pole list, take one photo of that pole with the system camera, and save it locally with a GPS fix — a fast, demo-ready v1 with no backend upload yet.

**Architecture:** Two new local-first pieces reuse existing patterns end to end: a real `PoleListRepository` (the `GET /work-orders/{id}/poles` endpoint already exists on BE, just never called from mobile) feeds a new Pole List screen; a `PolePhotoRepository` (Fake only, no BE endpoint yet) backs a new Capture screen that is a near-exact copy of F10's `EvidenceCaptureScreen` — the system camera app via `ActivityResultContracts.TakePicture()`, no CameraX, no EXIF, GPS from the existing `LocationTracker`. Everything lands in a new Room table (`local_pole_photo`); nothing touches `sync_queue` yet (there's no `SyncOpHandler` for it, and queuing without one gets the row marked `failed` immediately — see the spec).

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Retrofit/kotlinx.serialization, Coroutines/StateFlow, Turbine (tests), MockK (tests). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-15-pole-photo-capture-design.md`

## Global Constraints

- No comments in any new code (explicit user preference recorded this session, overrides `CLAUDE.md`'s normal WHY-comment allowance for code written by Claude).
- Comment style, where `CLAUDE.md` itself is edited (not code): N/A for this plan — no `CLAUDE.md` edits in these tasks, it was already updated during brainstorming.
- Run `./gradlew ktlintCheck` before every commit in this plan and fix any violation first — do not commit with it failing.
- Keep each commit under ~400 changed lines (`CLAUDE.md`'s commit-size rule); every task below fits well inside that on its own.
- No manual exposure lock, no EXIF writing — both are explicit, recorded v1 exceptions (see spec section 2 and section 4). Do not add either without the user's sign-off first.
- No `sync_queue` entry, no `SyncOpHandler`, for the pole photo in v1 (spec section 7) — `capture()` only writes to Room.
- DTOs use `ignoreUnknownKeys = true` (already set globally in `ApiClient.kt`) — declare only the fields a task actually uses, matching the real backend field names (`snake_case` via `@SerialName`).
- Work continues on the existing branch `docs/fm-pole-photo-capture-design` (already has the spec commits) — flag to the user whether to rename it (e.g. `feat/fm-pole-photo-capture`) before opening a PR, since it now carries code, not just docs.

## Review Focus

- Recapturing an already-captured pole (tapping a pole marked "đã chụp" again) writes a second `local_pole_photo` row for the same `poleId` — the Pole List's "captured" marker must dedupe by `poleId`, not assume one row per pole, or it will not break but could look wrong if ever listed row-by-row instead of as a marker.
- GPS permission fully denied (not just a stale fix) must still let the photo save with `lat = 0.0, lng = 0.0`, matching `EvidenceCaptureViewModel`'s existing fallback — never block or crash the capture flow over a missing fix.
- `GET /work-orders/{id}/poles` returning an HTTP error (404/500) for a work order the Field Engineer can't actually see must land in `PoleListUiState.Error` with a retry, not crash the screen or show a blank list.
- Backing out of the system camera (`TakePicture()` returns `success = false`) must return to the Pole List screen with nothing written to Room — no half-saved row, no crash.
- A work order whose pole list comes back empty must show `PoleListUiState.Empty`, not a blank `LazyColumn` or a stuck `Loading` spinner.

---

### Task 1: Local storage — `LocalPolePhotoEntity`, `PolePhotoDao`, database wiring

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/entity/LocalPolePhotoEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/dao/PolePhotoDao.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/PolePhotoDaoTest.kt`

**Interfaces:**
- Produces: `LocalPolePhotoEntity(photoId: String, workOrderId: String, poleId: String, filePath: String, capturedAt: Instant, lat: Double, lng: Double)`; `PolePhotoDao.insert(photo: LocalPolePhotoEntity)` (suspend); `PolePhotoDao.observeByWorkOrder(workOrderId: String): Flow<List<LocalPolePhotoEntity>>`.

- [ ] **Step 1: Write the entity**

```kotlin
package com.luxmap.feature.survey.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_pole_photo")
data class LocalPolePhotoEntity(
    @PrimaryKey val photoId: String,
    val workOrderId: String,
    val poleId: String,
    val filePath: String,
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
)
```

- [ ] **Step 2: Write the DAO**

```kotlin
package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PolePhotoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: LocalPolePhotoEntity)

    @Query("SELECT * FROM local_pole_photo WHERE workOrderId = :workOrderId ORDER BY capturedAt DESC")
    fun observeByWorkOrder(workOrderId: String): Flow<List<LocalPolePhotoEntity>>
}
```

- [ ] **Step 3: Register the entity and DAO in `AppDatabase`**

In `AppDatabase.kt`, add the import for `LocalPolePhotoEntity` and `PolePhotoDao`, add `LocalPolePhotoEntity::class` to the `entities` list, bump `version = 6` to `version = 7`, and add:

```kotlin
    abstract fun polePhotoDao(): PolePhotoDao
```

- [ ] **Step 4: Provide the DAO in `DatabaseModule`**

Add the import for `PolePhotoDao`, then add:

```kotlin
    @Provides
    @Singleton
    fun providePolePhotoDao(db: AppDatabase): PolePhotoDao = db.polePhotoDao()
```

- [ ] **Step 5: Write the DAO test**

```kotlin
package com.luxmap.feature.survey.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class PolePhotoDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: PolePhotoDao

    private fun photo(
        photoId: String,
        workOrderId: String = "WO-1",
        poleId: String = "POLE-1",
        capturedAt: Instant = Instant.parse("2026-10-15T10:00:00Z"),
    ) = LocalPolePhotoEntity(
        photoId = photoId,
        workOrderId = workOrderId,
        poleId = poleId,
        filePath = "/data/pole-photos/$photoId.jpg",
        capturedAt = capturedAt,
        lat = 10.97,
        lng = 106.49,
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.polePhotoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeByWorkOrderReturnsOnlyRowsForThatWorkOrder() =
        runTest {
            dao.insert(photo("PH-1", workOrderId = "WO-1"))
            dao.insert(photo("PH-2", workOrderId = "WO-2"))

            dao.observeByWorkOrder("WO-1").test {
                assertEquals(listOf("PH-1"), awaitItem().map { it.photoId })
            }
        }

    @Test
    fun observeByWorkOrderOrdersNewestFirst() =
        runTest {
            dao.insert(photo("OLD", capturedAt = Instant.parse("2026-10-15T09:00:00Z")))
            dao.insert(photo("NEW", capturedAt = Instant.parse("2026-10-15T10:00:00Z")))

            dao.observeByWorkOrder("WO-1").test {
                assertEquals(listOf("NEW", "OLD"), awaitItem().map { it.photoId })
            }
        }

    @Test
    fun observeByWorkOrderCanReturnTwoRowsForTheSamePoleWhenRecaptured() =
        runTest {
            dao.insert(photo("FIRST", poleId = "POLE-1", capturedAt = Instant.parse("2026-10-15T09:00:00Z")))
            dao.insert(photo("SECOND", poleId = "POLE-1", capturedAt = Instant.parse("2026-10-15T10:00:00Z")))

            dao.observeByWorkOrder("WO-1").test {
                val rows = awaitItem()
                assertEquals(2, rows.size)
                assertEquals(setOf("POLE-1"), rows.map { it.poleId }.toSet())
            }
        }
}
```

- [ ] **Step 6: Run the DAO test**

Run: `./gradlew connectedAndroidTest --tests "com.luxmap.feature.survey.data.dao.PolePhotoDaoTest"` (or run it from the IDE against a connected device/emulator — this is an `androidTest`, not a plain JVM `test`).
Expected: all 3 tests PASS.

- [ ] **Step 7: ktlint and commit**

Run: `./gradlew ktlintCheck` and fix anything it flags.

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/entity/LocalPolePhotoEntity.kt \
        app/src/main/java/com/luxmap/feature/survey/data/dao/PolePhotoDao.kt \
        app/src/main/java/com/luxmap/core/database/AppDatabase.kt \
        app/src/main/java/com/luxmap/di/DatabaseModule.kt \
        app/src/androidTest/java/com/luxmap/feature/survey/data/dao/PolePhotoDaoTest.kt
git commit -m "feat(fm-pole-photo): add local_pole_photo Room table"
```

---

### Task 2: Photo capture repository — `PolePhotoRepository` + `FakePolePhotoRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/data/PolePhotoRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/FakePolePhotoRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/FakePolePhotoRepositoryTest.kt`

**Interfaces:**
- Consumes: `PolePhotoDao.insert`, `PolePhotoDao.observeByWorkOrder` (Task 1).
- Produces: `PolePhotoRepository.observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>>`; `PolePhotoRepository.capture(workOrderId: String, poleId: String, filePath: String, lat: Double, lng: Double): LocalPolePhotoEntity` (suspend) — both consumed by Task 4 and Task 5.

- [ ] **Step 1: Write the repository interface**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow

interface PolePhotoRepository {
    fun observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>>

    suspend fun capture(
        workOrderId: String,
        poleId: String,
        filePath: String,
        lat: Double,
        lng: Double,
    ): LocalPolePhotoEntity
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePolePhotoRepositoryTest {
    @Test
    fun `capture writes a row with the given fields and a fresh photoId`() =
        runTest {
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val repository = FakePolePhotoRepository(dao)

            val result = repository.capture(
                workOrderId = "WO-1",
                poleId = "POLE-1",
                filePath = "/data/pole-photos/x.jpg",
                lat = 10.0,
                lng = 106.0,
            )

            assertEquals("WO-1", result.workOrderId)
            assertEquals("POLE-1", result.poleId)
            assertEquals("/data/pole-photos/x.jpg", result.filePath)
            assertEquals(10.0, result.lat, 0.0)
            assertEquals(106.0, result.lng, 0.0)
            coVerify(exactly = 1) { dao.insert(result) }
        }

    @Test
    fun `observePhotos delegates straight to the DAO`() =
        runTest {
            val dao = mockk<PolePhotoDao>()
            val rows = listOf(
                LocalPolePhotoEntity(
                    photoId = "PH-1",
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/x.jpg",
                    capturedAt = java.time.Instant.parse("2026-10-15T10:00:00Z"),
                    lat = 10.0,
                    lng = 106.0,
                ),
            )
            every { dao.observeByWorkOrder("WO-1") } returns flowOf(rows)
            val repository = FakePolePhotoRepository(dao)

            repository.observePhotos("WO-1").collect { assertEquals(rows, it) }
        }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakePolePhotoRepositoryTest"`
Expected: FAIL — `FakePolePhotoRepository` is not defined yet.

- [ ] **Step 4: Write the minimal implementation**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakePolePhotoRepository
    @Inject
    constructor(
        private val dao: PolePhotoDao,
    ) : PolePhotoRepository {
        override fun observePhotos(workOrderId: String): Flow<List<LocalPolePhotoEntity>> =
            dao.observeByWorkOrder(workOrderId)

        override suspend fun capture(
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
                )
            dao.insert(entity)
            return entity
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakePolePhotoRepositoryTest"`
Expected: PASS.

- [ ] **Step 6: Bind it in `RepositoryModule`**

Add imports for `PolePhotoRepository` and `FakePolePhotoRepository`, then add:

```kotlin
    @Binds
    abstract fun bindPolePhotoRepository(impl: FakePolePhotoRepository): PolePhotoRepository
```

- [ ] **Step 7: ktlint and commit**

Run: `./gradlew ktlintCheck` and fix anything it flags.

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/PolePhotoRepository.kt \
        app/src/main/java/com/luxmap/feature/survey/data/FakePolePhotoRepository.kt \
        app/src/main/java/com/luxmap/di/RepositoryModule.kt \
        app/src/test/java/com/luxmap/feature/survey/data/FakePolePhotoRepositoryTest.kt
git commit -m "feat(fm-pole-photo): add fake pole photo capture repository"
```

---

### Task 3: Pole list API — `WorkOrdersApi.poles()`, `PoleListRepository`, `RealPoleListRepository`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/dto/PoleListItemDto.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/PoleListRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/data/RealPoleListRepository.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/RealPoleListRepositoryTest.kt`

**Interfaces:**
- Produces: `PoleListItem(poleId: String, position: Int, fixtureStatus: String)`; `PoleListRepository.poles(workOrderId: String): Result<List<PoleListItem>>` (suspend) — consumed by Task 4.

- [ ] **Step 1: Add the Retrofit method**

In `WorkOrdersApi.kt`, add the import `com.luxmap.feature.survey.data.dto.PoleListItemDto`, then add inside the interface:

```kotlin
    @GET("api/v1/work-orders/{id}/poles")
    suspend fun poles(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): PagedResultDto<PoleListItemDto>
```

- [ ] **Step 2: Write the DTO**

```kotlin
package com.luxmap.feature.survey.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PoleListItemDto(
    @SerialName("pole_id") val poleId: String,
    val position: Int,
    @SerialName("fixture_status") val fixtureStatus: String,
)
```

- [ ] **Step 3: Write the repository interface and domain model**

```kotlin
package com.luxmap.feature.survey.data

data class PoleListItem(
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
)

interface PoleListRepository {
    suspend fun poles(workOrderId: String): Result<List<PoleListItem>>
}
```

- [ ] **Step 4: Write the failing test**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.survey.data.dto.PoleListItemDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

class RealPoleListRepositoryTest {
    @Test
    fun `maps the paged DTO items into domain items in the same order`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            coEvery { api.poles("WO-1") } returns
                PagedResultDto(
                    page = 1,
                    pageSize = 200,
                    total = 2,
                    items =
                        listOf(
                            PoleListItemDto(poleId = "POLE-1", position = 1, fixtureStatus = "normal"),
                            PoleListItemDto(poleId = "POLE-2", position = 2, fixtureStatus = "unknown"),
                        ),
                )
            val repository = RealPoleListRepository(api)

            val result = repository.poles("WO-1")

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(
                    PoleListItem("POLE-1", 1, "normal"),
                    PoleListItem("POLE-2", 2, "unknown"),
                ),
                result.getOrNull(),
            )
        }

    @Test
    fun `returns failure when the backend rejects the request`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            coEvery { api.poles("WO-1") } throws httpException(404)
            val repository = RealPoleListRepository(api)

            assertTrue(repository.poles("WO-1").isFailure)
        }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealPoleListRepositoryTest"`
Expected: FAIL — `RealPoleListRepository` is not defined yet.

- [ ] **Step 6: Write the minimal implementation**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealPoleListRepository
    @Inject
    constructor(
        private val api: WorkOrdersApi,
    ) : PoleListRepository {
        override suspend fun poles(workOrderId: String): Result<List<PoleListItem>> =
            runCatching {
                api.poles(workOrderId).items.map { PoleListItem(it.poleId, it.position, it.fixtureStatus) }
            }
    }
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealPoleListRepositoryTest"`
Expected: PASS.

- [ ] **Step 8: Bind it in `RepositoryModule`**

Add imports for `PoleListRepository` and `RealPoleListRepository`, then add:

```kotlin
    @Binds
    abstract fun bindPoleListRepository(impl: RealPoleListRepository): PoleListRepository
```

- [ ] **Step 9: ktlint and commit**

Run: `./gradlew ktlintCheck` and fix anything it flags.

```bash
git add app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt \
        app/src/main/java/com/luxmap/feature/survey/data/dto/PoleListItemDto.kt \
        app/src/main/java/com/luxmap/feature/survey/data/PoleListRepository.kt \
        app/src/main/java/com/luxmap/feature/survey/data/RealPoleListRepository.kt \
        app/src/main/java/com/luxmap/di/RepositoryModule.kt \
        app/src/test/java/com/luxmap/feature/survey/data/RealPoleListRepositoryTest.kt
git commit -m "feat(fm-pole-photo): call GET /work-orders/{id}/poles"
```

---

### Task 4: Pole List screen

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModelTest.kt`

**Interfaces:**
- Consumes: `PoleListRepository.poles` (Task 3), `PolePhotoRepository.observePhotos` (Task 2).
- Produces: `Routes.PolePhotoList.createRoute(workOrderId: String)`; `PoleListRoute(onCapturePole: (poleId: String) -> Unit, onDone: () -> Unit)` — consumed by Task 5's `NavGraph` wiring.

- [ ] **Step 1: Write the UI state**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

data class PoleListRowState(
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
    val isCaptured: Boolean,
)

sealed interface PoleListUiState {
    data object Loading : PoleListUiState

    data class Success(val poles: List<PoleListRowState>) : PoleListUiState

    data object Empty : PoleListUiState

    data class Error(val message: String) : PoleListUiState
}
```

- [ ] **Step 2: Write the failing ViewModel test**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.feature.survey.data.PoleListItem
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() = SavedStateHandle(mapOf(PoleListViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

private fun photo(poleId: String) =
    LocalPolePhotoEntity(
        photoId = "PH-$poleId",
        workOrderId = WORK_ORDER_ID,
        poleId = poleId,
        filePath = "/data/$poleId.jpg",
        capturedAt = Instant.parse("2026-10-15T10:00:00Z"),
        lat = 10.0,
        lng = 106.0,
    )

class PoleListViewModelTest {
    @Test
    fun `emits Loading then Success with isCaptured true only for poles already photographed`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.success(
                    listOf(
                        PoleListItem("POLE-1", 1, "normal"),
                        PoleListItem("POLE-2", 2, "dim"),
                    ),
                )
            every { polePhotoRepository.observePhotos(WORK_ORDER_ID) } returns flowOf(listOf(photo("POLE-1")))
            val vm = PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                val success = awaitItem() as PoleListUiState.Success
                assertEquals(
                    listOf(
                        PoleListRowState("POLE-1", 1, "normal", isCaptured = true),
                        PoleListRowState("POLE-2", 2, "dim", isCaptured = false),
                    ),
                    success.poles,
                )
            }
        }

    @Test
    fun `emits Empty when the work order has no poles`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns Result.success(emptyList())
            val vm = PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                assertEquals(PoleListUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Error when the pole list request fails`() =
        runTest {
            val poleListRepository = mockk<PoleListRepository>()
            val polePhotoRepository = mockk<PolePhotoRepository>()
            coEvery { poleListRepository.poles(WORK_ORDER_ID) } returns
                Result.failure(IllegalStateException("network down"))
            val vm = PoleListViewModel(savedStateHandle(), poleListRepository, polePhotoRepository)

            vm.uiState.test {
                assertEquals(PoleListUiState.Loading, awaitItem())
                val error = awaitItem() as PoleListUiState.Error
                assertEquals("network down", error.message)
            }
        }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleListViewModelTest"`
Expected: FAIL — `PoleListViewModel` is not defined yet.

- [ ] **Step 4: Write the ViewModel**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.PoleListRepository
import com.luxmap.feature.survey.data.PolePhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoleListViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val poleListRepository: PoleListRepository,
        private val polePhotoRepository: PolePhotoRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val _uiState = MutableStateFlow<PoleListUiState>(PoleListUiState.Loading)
        val uiState: StateFlow<PoleListUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun onRetry() {
            _uiState.value = PoleListUiState.Loading
            load()
        }

        private fun load() {
            viewModelScope.launch {
                poleListRepository.poles(workOrderId).fold(
                    onSuccess = { poles ->
                        if (poles.isEmpty()) {
                            _uiState.value = PoleListUiState.Empty
                            return@launch
                        }
                        polePhotoRepository
                            .observePhotos(workOrderId)
                            .catch { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Lỗi") }
                            .collect { photos ->
                                val capturedIds = photos.map { it.poleId }.toSet()
                                _uiState.value =
                                    PoleListUiState.Success(
                                        poles.map { pole ->
                                            PoleListRowState(
                                                poleId = pole.poleId,
                                                position = pole.position,
                                                fixtureStatus = pole.fixtureStatus,
                                                isCaptured = pole.poleId in capturedIds,
                                            )
                                        },
                                    )
                            }
                    },
                    onFailure = { e -> _uiState.value = PoleListUiState.Error(e.message ?: "Không tải được danh sách cột") },
                )
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PoleListViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Write the screen**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.DisplayTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge

@Composable
fun PoleListRoute(
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    viewModel: PoleListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    PoleListScreen(
        state = uiState,
        onCapturePole = onCapturePole,
        onDone = onDone,
        onRetry = viewModel::onRetry,
    )
}

@Composable
fun PoleListScreen(
    state: PoleListUiState,
    onCapturePole: (poleId: String) -> Unit,
    onDone: () -> Unit,
    onRetry: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.statusBarsPadding().padding(Spacing.lg)) {
            Text(text = "Chụp ảnh cột đèn", style = DisplayTextStyle, color = MaterialTheme.colorScheme.onBackground)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                is PoleListUiState.Loading -> CircularProgressIndicator()

                is PoleListUiState.Success ->
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            items(state.poles) { pole ->
                                PoleRow(pole = pole, onClick = { onCapturePole(pole.poleId) })
                            }
                        }
                        PrimaryButton(
                            text = "Hoàn tất",
                            onClick = onDone,
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                        )
                    }

                is PoleListUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Tuyến này chưa có cột đèn nào",
                        subtitle = "Liên hệ Kỹ sư bảo trì nếu bạn cho rằng đây là nhầm lẫn.",
                        action = {},
                    )

                is PoleListUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.ErrorOutline,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = "Không tải được danh sách cột",
                        subtitle = state.message,
                        action = { PrimaryButton(text = "Thử lại", onClick = onRetry) },
                    )
            }
        }
    }
}

@Composable
private fun PoleRow(
    pole: PoleListRowState,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val condition =
        when (pole.fixtureStatus) {
            "normal" -> AssetCondition.NORMAL
            "dim" -> AssetCondition.DIM
            "out" -> AssetCondition.OUT
            else -> AssetCondition.UNKNOWN
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = "Cột #${pole.position} — ${pole.poleId}", style = MaterialTheme.typography.titleSmall)
            StatusBadge(text = condition.label(), colors = condition.badgeColors(isDark))
        }
        if (pole.isCaptured) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Đã chụp",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Chụp ảnh",
                modifier = Modifier.padding(Spacing.sm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 7: Add the routes**

In `Routes.kt`, add:

```kotlin
    data object PolePhotoList : Routes {
        override val route = "survey/pole-photos/{workOrderId}"

        fun createRoute(workOrderId: String) = "survey/pole-photos/$workOrderId"
    }
```

- [ ] **Step 8: Wire the route into `NavGraph`**

Add the import `com.luxmap.feature.survey.ui.polephoto.PoleListRoute`, then add inside the `NavHost`:

```kotlin
            composable(
                route = Routes.PolePhotoList.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) {
                PoleListRoute(
                    onCapturePole = { poleId -> },
                    onDone = { navController.popBackStack() },
                )
            }
```

(`onCapturePole`'s navigation to the Capture screen is filled in at the end of Task 5, once `Routes.PolePhotoCapture` exists — leaving the lambda empty here is intentional for this task, not a placeholder left unfinished: Task 5's Step 7 is where it gets its real body.)

- [ ] **Step 9: Add the button to the Survey Hub card**

In `SurveyHubScreen.kt`, change `AssignedRouteCard`'s signature and body to add a second button:

```kotlin
private fun AssignedRouteCard(
    route: AssignedSurveyRoute,
    onCheckReadiness: () -> Unit,
    onCapturePolePhotos: () -> Unit,
) {
```

and, right after the existing `Button` block (the one with `text = "Kiểm tra sẵn sàng"`), add:

```kotlin
        OutlinedButton(
            onClick = onCapturePolePhotos,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
            shape = RoundedCornerShape(Radius.control),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        ) {
            Text(text = "Chụp ảnh cột đèn")
        }
```

Then thread a new `onCapturePolePhotos: (workOrderId: String) -> Unit` parameter through the three wrapper functions, right next to each one's existing `onCheckReadiness` parameter:

In `SurveyHubRoute`'s signature, add `onCapturePolePhotos: (workOrderId: String) -> Unit,` right after `onCheckReadiness: (workOrderId: String) -> Unit,`, and pass it through to its inner `SurveyHubScreen(...)` call as `onCapturePolePhotos = onCapturePolePhotos,`.

In `SurveyHubScreen`'s signature, add the same parameter right after its `onCheckReadiness`, and pass it through to its inner `SurveyHubContent(...)` call (inside the `is SurveyHubUiState.Success ->` branch) as `onCapturePolePhotos = onCapturePolePhotos,`.

In `SurveyHubContent`'s signature, add the same parameter right after its `onCheckReadiness`. Inside its body, change the line `AssignedRouteCard(route, onCheckReadiness = { onCheckReadiness(route.workOrderId) })` to:

```kotlin
                AssignedRouteCard(
                    route,
                    onCheckReadiness = { onCheckReadiness(route.workOrderId) },
                    onCapturePolePhotos = { onCapturePolePhotos(route.workOrderId) },
                )
```

- [ ] **Step 10: Wire `SurveyHubRoute`'s new callback in `NavGraph`**

Find the existing `composable(Routes.Survey.route) { ... }` block that constructs `SurveyHubRoute(...)`, and add:

```kotlin
                    onCapturePolePhotos = { workOrderId ->
                        navController.navigate(Routes.PolePhotoList.createRoute(workOrderId))
                    },
```

- [ ] **Step 11: ktlint and commit**

Run: `./gradlew ktlintCheck` and fix anything it flags.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListUiState.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModel.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PoleListScreen.kt \
        app/src/main/java/com/luxmap/navigation/Routes.kt \
        app/src/main/java/com/luxmap/navigation/NavGraph.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt \
        app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PoleListViewModelTest.kt
git commit -m "feat(fm-pole-photo): add pole list screen"
```

---

### Task 5: Pole Photo Capture screen

**Files:**
- Modify: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `PolePhotoRepository.capture` (Task 2), `LocationTracker.getCurrentLocation` (existing, `core/location`), `Routes.PolePhotoList` (Task 4, for the back-navigation target).
- Produces: `Routes.PolePhotoCapture.createRoute(workOrderId: String, poleId: String)`; completes the `onCapturePole` wiring left open in Task 4 Step 8.

- [ ] **Step 1: Add the path to `FileProvider`**

In `app/src/main/res/xml/file_paths.xml`, add a second entry next to the existing `evidence` one:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="evidence" path="evidence/" />
    <files-path name="pole-photos" path="pole-photos/" />
</paths>
```

- [ ] **Step 2: Write the failing ViewModel test**

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
            PolePhotoCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID,
            PolePhotoCaptureViewModel.POLE_ID_ARG to POLE_ID,
        ),
    )

@OptIn(ExperimentalCoroutinesApi::class)
class PolePhotoCaptureViewModelTest {
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
    fun `onPhotoCaptured sends the real GPS fix to the repository and calls onDone`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.0, 106.0)
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker)
            var done = false

            vm.onPhotoCaptured(File("/data/pole-photos/WO-1/x.jpg")) { done = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.capture(
                    workOrderId = WORK_ORDER_ID,
                    poleId = POLE_ID,
                    filePath = "/data/pole-photos/WO-1/x.jpg",
                    lat = 10.0,
                    lng = 106.0,
                )
            }
            assertTrue(done)
        }

    @Test
    fun `onPhotoCaptured falls back to 0,0 when the GPS fix is unavailable`() =
        runTest {
            val repository = mockk<PolePhotoRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            coEvery { locationTracker.getCurrentLocation() } returns null
            val vm = PolePhotoCaptureViewModel(savedStateHandle(), repository, locationTracker)

            vm.onPhotoCaptured(File("/data/pole-photos/WO-1/x.jpg")) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.capture(workOrderId = WORK_ORDER_ID, poleId = POLE_ID, filePath = "/data/pole-photos/WO-1/x.jpg", lat = 0.0, lng = 0.0) }
        }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureViewModelTest"`
Expected: FAIL — `PolePhotoCaptureViewModel` is not defined yet.

- [ ] **Step 4: Write the ViewModel**

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
class PolePhotoCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PolePhotoRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        private val poleId: String = checkNotNull(savedStateHandle[POLE_ID_ARG])
        private val photoFileName: String = UUID.randomUUID().toString()

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "pole-photos/$workOrderId")
            dir.mkdirs()
            return File(dir, "$photoFileName.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val location = locationTracker.getCurrentLocation()
                repository.capture(
                    workOrderId = workOrderId,
                    poleId = poleId,
                    filePath = file.absolutePath,
                    lat = location?.latitude ?: 0.0,
                    lng = location?.longitude ?: 0.0,
                )
                onDone()
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
            const val POLE_ID_ARG = "poleId"
        }
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Write the screen**

```kotlin
package com.luxmap.feature.survey.ui.polephoto

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@Composable
fun PolePhotoCaptureRoute(
    onDone: () -> Unit,
    viewModel: PolePhotoCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var hasRequestedLocationPermission by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                viewModel.onPhotoCaptured(file, onDone)
            } else {
                onDone()
            }
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchCamera() }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (granted || hasRequestedLocationPermission) {
            launchCamera()
        } else {
            hasRequestedLocationPermission = true
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
```

- [ ] **Step 7: Add the route and wire everything in `NavGraph`**

In `Routes.kt`, add:

```kotlin
    data object PolePhotoCapture : Routes {
        override val route = "survey/pole-photos/{workOrderId}/capture/{poleId}"

        fun createRoute(
            workOrderId: String,
            poleId: String,
        ) = "survey/pole-photos/$workOrderId/capture/$poleId"
    }
```

In `NavGraph.kt`, add the import `com.luxmap.feature.survey.ui.polephoto.PolePhotoCaptureRoute`, then replace Task 4 Step 8's placeholder `onCapturePole = { poleId -> }` with:

```kotlin
                PoleListRoute(
                    onCapturePole = { poleId ->
                        navController.navigate(Routes.PolePhotoCapture.createRoute(workOrderId, poleId))
                    },
                    onDone = { navController.popBackStack() },
                )
```

(`workOrderId` here is the same `backStackEntry.arguments?.getString("workOrderId")` value already extracted for this `composable` block in Task 4 Step 8 — if that extraction used `?: return@composable` as elsewhere in this file, reuse that same local `val`.)

Then add the new composable right after it:

```kotlin
            composable(
                route = Routes.PolePhotoCapture.route,
                arguments =
                    listOf(
                        navArgument("workOrderId") { type = NavType.StringType },
                        navArgument("poleId") { type = NavType.StringType },
                    ),
            ) {
                PolePhotoCaptureRoute(onDone = { navController.popBackStack() })
            }
```

- [ ] **Step 8: ktlint and commit**

Run: `./gradlew ktlintCheck` and fix anything it flags.

```bash
git add app/src/main/res/xml/file_paths.xml \
        app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureViewModel.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureScreen.kt \
        app/src/main/java/com/luxmap/navigation/Routes.kt \
        app/src/main/java/com/luxmap/navigation/NavGraph.kt \
        app/src/test/java/com/luxmap/feature/survey/ui/polephoto/PolePhotoCaptureViewModelTest.kt
git commit -m "feat(fm-pole-photo): add pole photo capture screen"
```

---

## After all 5 tasks

- [ ] Run the full relevant test slice: `./gradlew testDebugUnitTest` for the new unit tests, plus `./gradlew connectedAndroidTest --tests "com.luxmap.feature.survey.data.dao.PolePhotoDaoTest"` on a connected device/emulator for Task 1's DAO test. Do not run the full `connectedAndroidTest`/`testDebugUnitTest` suite unprompted — the user has previously asked not to auto-run the full Gradle test suite.
- [ ] Manually try the flow on an emulator or device: Survey Hub → "Chụp ảnh cột đèn" on an assigned route → pick a pole → take a photo with the system camera → confirm it returns to the Pole List with that pole now showing the captured icon.
