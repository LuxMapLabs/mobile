# FM-11 — Xem lại video khảo sát trước khi nộp (F05 reinterpreted) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After stopping a recording in F04, let the Field Engineer play back the recorded video (all segments joined into one continuous playback) before it uploads, and either confirm it ("Nộp") or discard it and start a fresh recording for the same route ("Quay lại").

**Architecture:** One new screen+ViewModel pair (`feature/survey/ui/coverage/`) inserted into the nav graph between the existing `CaptureScreen` and `SubmitScreen`. Two new methods on the existing `SurveyRepository` interface read the session's segment file paths and discard a session's local files + Room rows. Media3 ExoPlayer plays the ordered segment list as one gapless playlist.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (existing `SurveySessionDao`), Media3 ExoPlayer (`androidx.media3:media3-exoplayer`/`media3-ui`/`media3-common`, new to this project, approved 2026-10-02), JUnit + MockK + Turbine for ViewModel tests, Room in-memory DB for DAO/repository instrumented tests.

**Spec:** `docs/superpowers/specs/2026-10-02-survey-video-review-design.md`

## Global Constraints

- Comments in code are English only, simple everyday words, no Vietnamese (CLAUDE.md).
- Every tappable control is at least 48×48dp (Design System v2.0).
- Any screen with async data uses the 4-state `sealed interface` pattern (Loading/Success/Empty/Error) with an exhaustive `when`, no `else` branch (CLAUDE.md architecture section).
- No library beyond the approved stack except `androidx.media3` (version `1.11.1`, approved by the project owner 2026-10-02 specifically for this feature).
- Run `./gradlew ktlintCheck` before every commit; fix any violation before moving on.
- No single commit changes more than 400 lines; split a task's commit further if it would exceed that.
- Branch from `dev` (never from `main`), PR targets `dev`. Branch/commit scope uses `fm-11` (confirmed against `docs/LuxMap_TaskList_v2.xlsx` 2026-10-02 — see spec §7).
- Room schema: no new entity, no new column, no migration — `AppDatabase` stays at `version = 2`. Do not bump it.

## Review Focus

- An empty segment list (crash-recovery removed every segment before packaging finished) must render `Empty`, not a blank screen or a crash from an empty `MediaItem` list — Task 4.
- An unexpected repository failure while loading segments (e.g. a Room read error) must render `Error` with a message, not throw past the ViewModel and crash the screen. Note: a `sessionId` with zero segment rows (including one that does not exist at all) is indistinguishable from a session that legitimately has none, and correctly renders `Empty`, not `Error` — `segmentFilePathsFor` only queries the segment table, it never validates the session row itself — Task 4.
- `discardSession` must delete the Room rows even when deleting the on-disk files throws (a stuck, un-redoable session is worse than an orphaned file) — Task 3.
- A segment file missing or corrupted on disk despite its Room row existing must surface as an inline warning on the existing `Success` state, not flip the whole screen to `Error`, and "Quay lại" must still work from that state — Task 4 (ViewModel) + Task 6 (wiring the player's error callback).
- Leaving this screen via the system Back button must not be possible — it would abandon a packaged session nobody ever submits or discards, silently wasting device storage forever — Task 6.

---

## Task 1: Add Media3 ExoPlayer dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Produces: `libs.androidx.media3.exoplayer`, `libs.androidx.media3.ui`, `libs.androidx.media3.common` (version catalog entries), available to `app/build.gradle.kts` and any Kotlin file in `app/src/main`.

This task has no code to unit test — it only makes the library resolvable. Verify with a compile check instead of a test.

- [ ] **Step 1: Add the version and library entries to the version catalog**

In `gradle/libs.versions.toml`, add to `[versions]` (keep alphabetical placement near the other `androidx*` entries):

```toml
media3 = "1.11.1"
```

Add to `[libraries]` (near the other `androidx-*` entries):

```toml
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version.ref = "media3" }
```

- [ ] **Step 2: Add the dependencies to the app module**

In `app/build.gradle.kts`, inside the existing `dependencies { ... }` block, add after the `implementation(libs.maplibre.android.sdk)` / `implementation(libs.play.services.location)` lines:

```kotlin
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
```

- [ ] **Step 3: Verify the dependency resolves and the project still compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` (confirms Maven Central/Google's repo actually has `1.11.1` for all three artifacts and nothing else broke).

- [ ] **Step 4: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: `BUILD SUCCESSFUL` (these are non-Kotlin files, this should already pass, but confirm before committing).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "chore(fm-11): add media3 exoplayer dependency"
```

---

## Task 2: Add session+segment deletion to `SurveySessionDao`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt`
- Modify (add a test method to the existing file): `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt`

**Interfaces:**
- Produces: `suspend fun SurveySessionDao.deleteSessionAndSegments(sessionId: String)` — deletes all `local_survey_video_segment` rows for the session, then the `local_survey_session` row itself, in one Room transaction.

- [ ] **Step 1: Write the failing test**

Add this test to the bottom of the existing `SurveySessionDaoTest` class (before the closing `}`), reusing the file's existing `session(...)` helper:

```kotlin
    @Test
    fun deleteSessionAndSegmentsRemovesBothTheSessionAndItsSegments() =
        runTest {
            dao.insertSession(session("SESSION-1"))
            dao.insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = "/data/segment_0.mp4",
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = 180_000_000_000L,
                    sizeBytes = 4096L,
                    checksumSha256 = "abc",
                ),
            )

            dao.deleteSessionAndSegments("SESSION-1")

            assertNull(dao.sessionById("SESSION-1"))
            assertEquals(0, dao.segmentsFor("SESSION-1").size)
        }
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest.deleteSessionAndSegmentsRemovesBothTheSessionAndItsSegments"`
Expected: FAIL — compile error, `deleteSessionAndSegments` is unresolved.

- [ ] **Step 3: Implement the DAO method**

In `SurveySessionDao.kt`, add after the existing `deleteSegment` query:

```kotlin
    @Query("DELETE FROM local_survey_video_segment WHERE sessionId = :sessionId")
    suspend fun deleteSegmentsForSession(sessionId: String)

    @Query("DELETE FROM local_survey_session WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)

    // Wraps both deletes in one transaction so a session row never outlives its segments or
    // the other way around, even if the process dies mid-call.
    @Transaction
    suspend fun deleteSessionAndSegments(sessionId: String) {
        deleteSegmentsForSession(sessionId)
        deleteSession(sessionId)
    }
```

Add the import `androidx.room.Transaction` at the top of the file.

- [ ] **Step 4: Run the test again to confirm it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest.deleteSessionAndSegmentsRemovesBothTheSessionAndItsSegments"`
Expected: PASS (needs a connected device/emulator, same as every other test in this file).

- [ ] **Step 5: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt
git commit -m "feat(fm-11): add session and segment deletion to survey session dao"
```

---

## Task 3: Extend `SurveyRepository` with segment paths and discard

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt`
- Create: `app/src/androidTest/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt`

**Interfaces:**
- Consumes: `SurveySessionDao.segmentsFor(sessionId): List<LocalSurveyVideoSegmentEntity>` (existing), `SurveySessionDao.sessionById(sessionId): LocalSurveySessionEntity?` (existing), `SurveySessionDao.deleteSessionAndSegments(sessionId)` (Task 2).
- Produces: `SurveyRepository.segmentFilePathsFor(sessionId: String): List<String>`, `SurveyRepository.discardSession(sessionId: String): String` (returns the session's `surveySweepId`) — both used by `CoverageViewModel` in Task 4.

This task needs real Room + real file I/O (the whole point of `discardSession` is deleting real files), so its test is an instrumented test, not a plain JVM unit test — same reason `SurveySessionDaoTest` is in `androidTest`.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt`:

```kotlin
package com.luxmap.feature.survey.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class FakeSurveyRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: FakeSurveyRepository
    private lateinit var sessionDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = FakeSurveyRepository(database.surveySessionDao(), context)
        sessionDir = File(context.getExternalFilesDir(null), "survey/SESSION-1").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        sessionDir.deleteRecursively()
        database.close()
    }

    private fun insertSessionWithOneSegment() =
        runTest {
            database.surveySessionDao().insertSession(
                LocalSurveySessionEntity(
                    sessionId = "SESSION-1",
                    surveySweepId = "SWEEP-1",
                    recordingState = "packaged",
                    syncState = null,
                    startedAtUtc = Instant.parse("2026-10-02T20:00:00Z"),
                    startedAtElapsedNs = 0L,
                    endedAtUtc = Instant.parse("2026-10-02T20:03:00Z"),
                    durationSeconds = 180L,
                    distanceMeters = null,
                    gpsTrackFilePath = null,
                    luxLogFilePath = null,
                    headingLogFilePath = null,
                    frameTimestampLogFilePath = null,
                    captureConfigFilePath = null,
                    manifestFilePath = null,
                    packageSchemaVersion = "v0",
                    timestampSourceRealtime = true,
                    bleGapDetected = false,
                    createdAt = Instant.parse("2026-10-02T20:00:00Z"),
                    updatedAt = Instant.parse("2026-10-02T20:03:00Z"),
                ),
            )
            database.surveySessionDao().insertSegment(
                LocalSurveyVideoSegmentEntity(
                    segmentId = "SEG-0",
                    sessionId = "SESSION-1",
                    segmentIndex = 0,
                    filePath = File(sessionDir, "segment_0.mp4").absolutePath,
                    startedAtElapsedNs = 0L,
                    endedAtElapsedNs = 180_000_000_000L,
                    sizeBytes = 4096L,
                    checksumSha256 = "abc",
                ),
            )
        }

    @Test
    fun segmentFilePathsForReturnsPathsInSegmentOrder() =
        runTest {
            insertSessionWithOneSegment()

            val paths = repository.segmentFilePathsFor("SESSION-1")

            assertEquals(listOf(File(sessionDir, "segment_0.mp4").absolutePath), paths)
        }

    @Test
    fun discardSessionDeletesTheSessionDirectoryAndReturnsItsSurveySweepId() =
        runTest {
            insertSessionWithOneSegment()
            File(sessionDir, "segment_0.mp4").writeText("fake video bytes")
            assertTrue(sessionDir.exists())

            val surveySweepId = repository.discardSession("SESSION-1")

            assertEquals("SWEEP-1", surveySweepId)
            assertFalse(sessionDir.exists())
            assertNull(database.surveySessionDao().sessionById("SESSION-1"))
        }

    @Test
    fun discardSessionStillClearsRoomRowsWhenTheSessionDirectoryIsAlreadyGone() =
        runTest {
            insertSessionWithOneSegment()
            // Simulates a user who already wiped app storage manually - the directory is gone
            // but the Room rows are not. discardSession must not get stuck here.
            sessionDir.deleteRecursively()

            val surveySweepId = repository.discardSession("SESSION-1")

            assertEquals("SWEEP-1", surveySweepId)
            assertNull(database.surveySessionDao().sessionById("SESSION-1"))
        }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.FakeSurveyRepositoryTest"`
Expected: FAIL — compile error, `segmentFilePathsFor`/`discardSession` unresolved and `FakeSurveyRepository`'s constructor does not take a `SurveySessionDao`/`Context` yet.

- [ ] **Step 3: Extend the repository interface**

In `SurveyRepository.kt`, add to the interface (after `observeAssignedRoutes`):

```kotlin
    // Pure local data - no network involved, so Fake and Real do the same thing here (see
    // the design spec §3 for why this still lives on the one repository interface).
    suspend fun segmentFilePathsFor(sessionId: String): List<String>

    // Deletes the session's local video/log files and its Room rows, returning the
    // surveySweepId so the caller can start a fresh recording for the same route.
    suspend fun discardSession(sessionId: String): String
```

- [ ] **Step 4: Implement both methods in `FakeSurveyRepository`**

Replace the full content of `FakeSurveyRepository.kt` with:

```kotlin
package com.luxmap.feature.survey.data

import android.content.Context
import android.util.Log
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FakeSurveyRepository"

@Singleton
class FakeSurveyRepository
    @Inject
    constructor(
        private val sessionDao: SurveySessionDao,
        @ApplicationContext private val context: Context,
    ) : SurveyRepository {
        override fun observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>> =
            flow {
                emit(
                    listOf(
                        AssignedSurveyRoute(
                            surveySweepId = "SWEEP-2031",
                            assignedByName = "Kỹ sư bảo trì Trần Văn A",
                            plannedDate = "2026-10-01",
                            status = SurveySweepStatus.PLANNED,
                            roadSegments =
                                listOf(
                                    AssignedRoadSegment(
                                        roadSegmentId = "RS-1001",
                                        name = "Đường liên thôn 3",
                                        lengthMeters = 1200.0,
                                    ),
                                    AssignedRoadSegment(
                                        roadSegmentId = "RS-1002",
                                        name = "Đường liên thôn 4",
                                        lengthMeters = 800.0,
                                    ),
                                ),
                        ),
                    ),
                )
            }

        override suspend fun segmentFilePathsFor(sessionId: String): List<String> =
            sessionDao.segmentsFor(sessionId).map { it.filePath }

        override suspend fun discardSession(sessionId: String): String {
            val session =
                requireNotNull(sessionDao.sessionById(sessionId)) {
                    "No local_survey_session row for sessionId=$sessionId"
                }
            // File deletion failing must never block the Room cleanup below - a stuck session
            // the user can never redo is worse than a leftover file to clean up later.
            runCatching {
                File(context.getExternalFilesDir(null), "survey/$sessionId").deleteRecursively()
            }.onFailure { error ->
                Log.w(TAG, "Failed to delete session files for $sessionId", error)
            }
            sessionDao.deleteSessionAndSegments(sessionId)
            return session.surveySweepId
        }
    }
```

- [ ] **Step 5: Run the test again to confirm it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.FakeSurveyRepositoryTest"`
Expected: PASS (all 3 tests).

- [ ] **Step 6: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/SurveyRepository.kt app/src/main/java/com/luxmap/feature/survey/data/FakeSurveyRepository.kt app/src/androidTest/java/com/luxmap/feature/survey/data/FakeSurveyRepositoryTest.kt
git commit -m "feat(fm-11): add segment lookup and session discard to survey repository"
```

---

## Task 4: `CoverageUiState` + `CoverageViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/coverage/CoverageUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/coverage/CoverageViewModel.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/ui/coverage/CoverageViewModelTest.kt`

**Interfaces:**
- Consumes: `SurveyRepository.segmentFilePathsFor(sessionId)`, `SurveyRepository.discardSession(sessionId)` (Task 3).
- Produces: `CoverageViewModel.uiState: StateFlow<CoverageUiState>`, `CoverageViewModel.redoCompleted: SharedFlow<String>` (emits the `surveySweepId` once discard finishes), `fun loadSegments(sessionId: String)`, `fun onPlayerError(message: String)`, `fun onRedoConfirmed(sessionId: String)` — all consumed by `CoverageScreen` in Task 6.

- [ ] **Step 1: Write `CoverageUiState.kt`**

```kotlin
package com.luxmap.feature.survey.ui.coverage

// 4 required states (CLAUDE.md architecture section) for the F05-reinterpreted video review
// screen. playerErrorMessage on Success is an inline warning flag, not a 5th state - same
// pattern CaptureUiState.Recording uses for gpsSignalLost/bleGapDetected.
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

- [ ] **Step 2: Write the failing test for `CoverageViewModel`**

Create `app/src/test/java/com/luxmap/feature/survey/ui/coverage/CoverageViewModelTest.kt`:

```kotlin
package com.luxmap.feature.survey.ui.coverage

import app.cash.turbine.test
import com.luxmap.feature.survey.data.SurveyRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoverageViewModelTest {
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
    fun `loading segments with at least one file emits Success`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } returns listOf("/data/segment_0.mp4")
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                assertEquals(CoverageUiState.Loading, awaitItem())
                viewModel.loadSegments("S1")
                assertEquals(CoverageUiState.Success(listOf("/data/segment_0.mp4")), awaitItem())
            }
        }

    @Test
    fun `loading segments with an empty list emits Empty`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } returns emptyList()
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                assertEquals(CoverageUiState.Loading, awaitItem())
                viewModel.loadSegments("S1")
                assertEquals(CoverageUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repository failure emits Error`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } throws IllegalStateException("no such session")
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                assertEquals(CoverageUiState.Loading, awaitItem())
                viewModel.loadSegments("S1")
                val error = awaitItem() as CoverageUiState.Error
                assertEquals("no such session", error.message)
            }
        }

    @Test
    fun `a player error sets the flag on the existing Success state instead of leaving it`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.segmentFilePathsFor("S1") } returns listOf("/data/segment_0.mp4")
            val viewModel = CoverageViewModel(repository)

            viewModel.uiState.test {
                awaitItem() // Loading
                viewModel.loadSegments("S1")
                awaitItem() // Success, no error yet

                viewModel.onPlayerError("file missing")
                val withError = awaitItem() as CoverageUiState.Success
                assertEquals("file missing", withError.playerErrorMessage)
            }
        }

    @Test
    fun `confirming redo discards the session and emits the survey sweep id`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            coEvery { repository.discardSession("S1") } returns "SWEEP-1"
            val viewModel = CoverageViewModel(repository)

            viewModel.redoCompleted.test {
                viewModel.onRedoConfirmed("S1")
                assertEquals("SWEEP-1", awaitItem())
            }
        }
}
```

- [ ] **Step 3: Run the test to confirm it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.coverage.CoverageViewModelTest"`
Expected: FAIL — compile error, `CoverageViewModel` does not exist yet.

- [ ] **Step 4: Implement `CoverageViewModel`**

```kotlin
package com.luxmap.feature.survey.ui.coverage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CoverageViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<CoverageUiState>(CoverageUiState.Loading)
        val uiState: StateFlow<CoverageUiState> = _uiState.asStateFlow()

        // One-shot: the screen collects this to navigate back into F04 once the old session is
        // gone, instead of this ViewModel reaching into a NavController it does not own.
        private val _redoCompleted = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val redoCompleted: SharedFlow<String> = _redoCompleted.asSharedFlow()

        fun loadSegments(sessionId: String) {
            viewModelScope.launch {
                runCatching { repository.segmentFilePathsFor(sessionId) }
                    .onSuccess { paths ->
                        _uiState.value =
                            if (paths.isEmpty()) CoverageUiState.Empty else CoverageUiState.Success(paths)
                    }
                    .onFailure { error ->
                        _uiState.value = CoverageUiState.Error(error.message ?: "Không đọc được phiên khảo sát")
                    }
            }
        }

        // Called when the player hits a mid-playback error (e.g. a segment file missing on
        // disk). Kept as a flag on the existing Success state, not a new top-level state - same
        // pattern CaptureUiState.Recording uses for gpsSignalLost/bleGapDetected.
        fun onPlayerError(message: String) {
            (_uiState.value as? CoverageUiState.Success)?.let { current ->
                _uiState.value = current.copy(playerErrorMessage = message)
            }
        }

        fun onRedoConfirmed(sessionId: String) {
            viewModelScope.launch {
                val surveySweepId = repository.discardSession(sessionId)
                _redoCompleted.emit(surveySweepId)
            }
        }
    }
```

- [ ] **Step 5: Run the test again to confirm it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.coverage.CoverageViewModelTest"`
Expected: PASS (all 5 tests).

- [ ] **Step 6: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/coverage/CoverageUiState.kt app/src/main/java/com/luxmap/feature/survey/ui/coverage/CoverageViewModel.kt app/src/test/java/com/luxmap/feature/survey/ui/coverage/CoverageViewModelTest.kt
git commit -m "feat(fm-11): add coverage ui state and view model"
```

---

## Task 5: Add the `SurveyReview` route and wire it into `NavGraph`

**Files:**
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `CoverageScreen(sessionId, onApprove, onRedo)` (defined in Task 6 — this task's `NavGraph.kt` change references it before it exists, so Task 5 and Task 6 must land together before either compiles; see Step 4's note).
- Produces: `Routes.SurveyReview.route`, `Routes.SurveyReview.createRoute(sessionId: String): String`.

Compose Navigation wiring has no automated test anywhere in this codebase (same precedent as the rest of `NavGraph.kt`) — verify by compiling and then by the real-device check at the end of Task 6.

- [ ] **Step 1: Add the route**

In `Routes.kt`, add after `SurveyCapture` and before `SurveySubmit`:

```kotlin
    data object SurveyReview : Routes {
        override val route = "survey/review/{sessionId}"

        fun createRoute(sessionId: String) = "survey/review/$sessionId"
    }
```

- [ ] **Step 2: Point `onSessionPackaged` at the new route**

In `NavGraph.kt`, inside the `Routes.SurveyCapture.route` composable block, change:

```kotlin
                    onSessionPackaged = { sessionId ->
                        navController.navigate(Routes.SurveySubmit.createRoute(sessionId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
```

to:

```kotlin
                    onSessionPackaged = { sessionId ->
                        navController.navigate(Routes.SurveyReview.createRoute(sessionId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
```

- [ ] **Step 3: Add the `SurveyReview` composable**

In `NavGraph.kt`, add a new composable block right before the existing `Routes.SurveySubmit.route` block:

```kotlin
            composable(
                route = Routes.SurveyReview.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                CoverageScreen(
                    sessionId = sessionId,
                    onApprove = { approvedSessionId ->
                        navController.navigate(Routes.SurveySubmit.createRoute(approvedSessionId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
                    onRedo = { surveySweepId ->
                        navController.navigate(Routes.SurveyCapture.createRoute(surveySweepId)) {
                            popUpTo(Routes.Survey.route)
                        }
                    },
                )
            }
```

Add the import `com.luxmap.feature.survey.ui.coverage.CoverageScreen` at the top of `NavGraph.kt`.

- [ ] **Step 4: Note — this will not compile until Task 6 lands**

`CoverageScreen` does not exist until Task 6. Do Task 6 immediately next, in the same working session, before running a build or committing this task's changes — or combine Tasks 5 and 6 into one commit if your execution method does not support leaving a non-compiling intermediate state. If using subagent-driven-development, tell the Task 5 implementer to stop after Step 3 without committing, and let the Task 6 implementer finish and commit both together.

---

## Task 6: `CoverageScreen` (video playback UI)

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/coverage/CoverageScreen.kt`

**Interfaces:**
- Consumes: `CoverageViewModel` (Task 4), `Routes.SurveyReview` (Task 5).
- Produces: `@Composable fun CoverageScreen(sessionId: String, onApprove: (String) -> Unit, onRedo: (String) -> Unit, viewModel: CoverageViewModel = hiltViewModel())`, referenced by `NavGraph.kt` (Task 5).

Camera/MediaCodec/player UI glue has no automated test anywhere in this codebase (established precedent — see `VideoCaptureSession.kt`/`CaptureScreen.kt`, no Robolectric, Android framework types are not mockable on plain JVM tests). Verify this task on a real device per Step 3 below instead of an automated test.

- [ ] **Step 1: Implement `CoverageScreen.kt`**

```kotlin
package com.luxmap.feature.survey.ui.coverage

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

// F05 reinterpreted (see docs/superpowers/specs/2026-10-02-survey-video-review-design.md):
// plain video playback, no coverage percentage. Back is blocked (see BackHandler below) so a
// packaged session always gets an explicit decision instead of being silently abandoned.
@Composable
fun CoverageScreen(
    sessionId: String,
    onApprove: (String) -> Unit,
    onRedo: (String) -> Unit,
    viewModel: CoverageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showRedoConfirm by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {}

    LaunchedEffect(sessionId) { viewModel.loadSegments(sessionId) }
    LaunchedEffect(Unit) { viewModel.redoCompleted.collect { surveySweepId -> onRedo(surveySweepId) } }

    Box(Modifier.fillMaxSize()) {
        when (val state = uiState) {
            is CoverageUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            is CoverageUiState.Success -> {
                val player =
                    remember(state.segmentFilePaths) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItems(state.segmentFilePaths.map { MediaItem.fromUri(Uri.parse("file://$it")) })
                            prepare()
                        }
                    }
                DisposableEffect(player) {
                    val listener =
                        object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                viewModel.onPlayerError(error.message ?: "Lỗi phát video")
                            }
                        }
                    player.addListener(listener)
                    onDispose {
                        player.removeListener(listener)
                        player.release()
                    }
                }

                Column(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        factory = { viewContext -> PlayerView(viewContext).apply { this.player = player } },
                    )

                    state.playerErrorMessage?.let { message ->
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp),
                        )
                    }

                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onApprove(sessionId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Nộp")
                        }
                        OutlinedButton(onClick = { showRedoConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Quay lại")
                        }
                    }
                }
            }

            is CoverageUiState.Empty -> {
                Column(
                    Modifier.align(Alignment.Center).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Không có video nào để xem lại.", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { showRedoConfirm = true }) { Text("Quay lại") }
                }
            }

            is CoverageUiState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }
        }

        if (showRedoConfirm) {
            AlertDialog(
                onDismissRequest = { showRedoConfirm = false },
                title = { Text("Xoá video này và quay lại?") },
                text = { Text("Video và dữ liệu đã quay sẽ bị xoá khỏi máy, không thể hoàn tác.") },
                confirmButton = {
                    Button(onClick = {
                        showRedoConfirm = false
                        viewModel.onRedoConfirmed(sessionId)
                    }) { Text("Xoá và quay lại") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRedoConfirm = false }) { Text("Huỷ") }
                },
            )
        }
    }
}
```

- [ ] **Step 2: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Compile check (this also confirms Task 5's wiring)**

Run: `./gradlew :app:compileDebugKotlin`
Expected: `BUILD SUCCESSFUL` — confirms `NavGraph.kt` (Task 5) and `CoverageScreen.kt` (this task) both resolve against each other.

- [ ] **Step 4: Commit Tasks 5 and 6 together**

```bash
git add app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/main/java/com/luxmap/feature/survey/ui/coverage/CoverageScreen.kt
git commit -m "feat(fm-11): add survey video review screen and wire it into the capture to submit flow"
```

- [ ] **Step 5: Real-device verification checklist**

Needs a physical Android device with the BLE lux sensor connected (recording cannot start otherwise — see project memory on this). Record a short session (long enough to produce at least 2 segments, i.e. over 3 minutes, to actually exercise multi-segment playback), then:

- [ ] After pressing "Dừng quay", the app lands on the new review screen (not directly on the upload screen).
- [ ] The recorded video plays automatically or on tapping play, crossing from one segment into the next with no visible freeze, black flash, or audio/video glitch at the boundary.
- [ ] Pressing the system Back button does nothing (screen stays, per the `BackHandler`).
- [ ] Tapping "Quay lại" shows the confirmation dialog; confirming navigates back into F04 for the same route, and `adb shell run-as com.luxmap ls files/survey/` no longer lists the discarded session's folder.
- [ ] Tapping "Nộp" navigates to the existing upload screen (F06) exactly as before this change.
- [ ] Record a second short session (a single segment, well under 3 minutes) and confirm single-segment playback still works (no crash from a 1-item playlist).

---

## Task 7: Update `docs/contract-drift.md`

**Files:**
- Modify: `docs/contract-drift.md`

- [ ] **Step 1: Add an entry**

Read the existing file first to match its format, then add an entry recording: the `feature/survey/ui/coverage/` screen and `Routes.SurveyReview` route reuse the F05 slot from `LuxMap_Mobile_DacTaChiTiet_v2.2.docx`/CLAUDE.md's documented folder structure, but its content (plain video playback + discard/redo) is not the original "Kiểm tra độ phủ & chất lượng" (coverage %, lux/GPS integrity checks) — that original F05 scope remains an open gap, not resolved by this feature. Cross-reference `docs/superpowers/specs/2026-10-02-survey-video-review-design.md`.

- [ ] **Step 2: Commit**

```bash
git add docs/contract-drift.md
git commit -m "docs(fm-11): record f05 slot reuse for video review in contract drift"
```
