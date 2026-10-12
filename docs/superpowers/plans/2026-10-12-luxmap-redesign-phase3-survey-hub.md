# LuxMap Redesign Phase 3: Survey Hub Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the new "Hub Khảo sát" screen (spec code `1f`) as the `survey` tab's landing screen, replacing today's `SurveyPlanScreen` in that slot. The hub shows what needs the Field Engineer's attention (assigned routes ready to check, sessions currently uploading, and two schema-unconfirmed cards gated behind a local flag) and hands off to a new `survey/plan/{surveySweepId}` route for the per-route readiness checklist, which keeps using the existing, unmodified `SurveyPlanScreen`.

**Architecture:** One new screen package `feature/survey/ui/hub/` (UiState + ViewModel + Composable), one new Room DAO query to list sessions currently queued for upload, one new `Routes` entry, and a `NavGraph.kt` rewire that moves `SurveyPlanScreen` off the `survey` route and onto the new per-sweep route. The two schema-unconfirmed cards ("Bị từ chối", "Chờ xác nhận AI") are built against small local-only models and a compile-time flag that defaults to `false`, per the roadmap's decision #2 — they add no real behavior today.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room (`Flow` query), Hilt. No new dependencies.

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (Phase 3 row) and `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` lines 176-181 (Hub Khảo sát section). Current-code findings this plan is built from: a 2026-10-12 research pass reading `SurveyPlanScreen.kt`/`SurveyPlanViewModel.kt`/`SurveyPlanUiState.kt`, `SurveyRepository.kt`/`FakeSurveyRepository.kt`, `SurveySessionDao.kt`/`LocalSurveySessionEntity.kt`, `SubmitViewModel.kt`/`SubmitViewModelTest.kt`, `Routes.kt`/`NavGraph.kt`, `HomeScreen.kt` (card/section patterns), and the Phase 0/1 token files (`Dimens.kt`, `Type.kt`, `Color.kt`, `StatusBadge.kt`, `PrimaryButton.kt`, `SyncPill.kt`, `MessageState.kt`).

## Global Constraints

- Branch off `dev` (`git checkout dev && git pull`), never `main`. Branch: `style/redesign-phase3-survey-hub`.
- Every commit ≤ 400 changed lines (CLAUDE.md). Expect one commit per task (7 total).
- `./gradlew ktlintCheck` after every task, fix before moving on.
- Comments only where the WHY is genuinely non-obvious, in plain English — matching the existing codebase style (CLAUDE.md's comment rule), not a ban on all comments.
- `AssetCondition`, `SyncStatus` and their `badgeColors()`/`label()` functions in `core/theme/Color.kt` are **not touched** — this plan only reads them.
- The rejected/AI-pending cards ("Bị từ chối", "Chờ xác nhận AI") must stay behind `SHOW_UNCONFIRMED_HUB_CARDS = false` — no production code path flips it to `true`. Their data classes use unambiguous local names (`LocalRejectedSweep`, `LocalPendingAiConfirmation`), never a guessed real entity/field name, per the roadmap decision #2 and CLAUDE.md's "don't invent field/endpoint names" rule.
- `SurveyPlanScreen.kt`/`SurveyPlanViewModel.kt`/`SurveyPlanUiState.kt` internals are **not modified** in this phase — only their navigation entry point moves. Per-sweep filtering/preselection for the new route is Phase 4's job (roadmap table).
- Offline-first behavior is unchanged: this phase only reads existing Room/Flow data (`observeAssignedRoutes()`, the new DAO query), it writes nothing new.

## Review Focus

- **Empty vs. Success-with-empty-sections.** `SurveyHubUiState.Empty` must only fire when *every* section is empty (no assigned routes, no uploading sessions, and — if ever flipped on — no rejected/pending cards). A route list that's empty while a session is uploading must still render `Success` with just the "CẦN BẠN XỬ LÝ" section, the same way `HomeUiState` keeps `nextUp` optional inside `Success` rather than forcing `Empty`. Covered by a ViewModel test with routes empty but one queued session present.
- **Flag-off must be byte-for-byte today's behavior.** With `SHOW_UNCONFIRMED_HUB_CARDS = false` (the shipped default), `rejectedSweeps`/`pendingAiConfirmation` must always be `emptyList()` regardless of the fake data existing in the file — a reviewer should be able to delete the fake lists and the flag check entirely and see no behavior change. Covered by a ViewModel test asserting both lists are empty even though the fake data constants are non-empty.
- **The "uploading" section only reflects what the entity can actually say.** `LocalSurveySessionEntity.syncState` is only ever written as `null`, `"queued"`, or `"done"` in the current codebase (no code path writes `"syncing"`/`"failed"`/`"conflict"` back to this field) — the new DAO query must filter on `"queued"` specifically, not invent a broader "any non-done, non-null" query that implies states the entity never actually reaches.
- **Repository/Flow error still reaches `Error`, not a crash.** `observeAssignedRoutes()` can throw (same as today's `SurveyPlanViewModel`) — the new `combine(...)` pipeline must still route that through `.catch` into `SurveyHubUiState.Error`, not propagate and crash the collecting coroutine. Covered by a ViewModel test.
- **New route doesn't orphan the back stack.** `Routes.Survey.route` stays the `popUpTo` target used by the capture/review/submit flows (`NavGraph.kt` lines 184/198/214) — only the composable *content* at that route changes (Hub instead of `SurveyPlanScreen`). The new `Routes.SurveyPlan` route is a plain child screen (not added to `bottomNavItems`), so `showBottomBar` logic needs no change. Verified by a manual back-button walkthrough in the final task (Hub → "Kiểm tra sẵn sàng" → Back → Hub).

---

### Task 1: Add the `SurveyPlan` route

**Files:**
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`

**Interfaces:**
- Produces: `Routes.SurveyPlan` (`route = "survey/plan/{surveySweepId}"`, `createRoute(surveySweepId: String): String`) — consumed by Task 6 (NavGraph) and Task 5 (Hub screen's "Kiểm tra sẵn sàng" callback).

- [ ] **Step 1: Add the route object**

In `Routes.kt`, insert this new `data object` right after `SurveyCapture` (before `SurveyReview`):

```kotlin
    data object SurveyPlan : Routes {
        override val route = "survey/plan/{surveySweepId}"

        fun createRoute(surveySweepId: String) = "survey/plan/$surveySweepId"
    }
```

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/navigation/Routes.kt
git commit -m "feat(redesign-phase3): add survey/plan/{surveySweepId} route"
```

---

### Task 2: Add a Room query for sessions currently queued for upload

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt`

**Interfaces:**
- Produces: `SurveySessionDao.observeSessionsBySyncState(syncState: String): Flow<List<LocalSurveySessionEntity>>` — consumed by Task 4 (`SurveyHubViewModel`).

- [ ] **Step 1: Write the failing test**

Add to `SurveySessionDaoTest.kt` (needs a `kotlinx.coroutines.flow.first` import added at the top of the file):

```kotlin
    @Test
    fun observeSessionsBySyncStateOnlyReturnsSessionsInThatState() =
        runTest {
            dao.insertSession(session("SESSION-1").copy(syncState = "queued"))
            dao.insertSession(session("SESSION-2").copy(syncState = "done"))
            dao.insertSession(session("SESSION-3").copy(syncState = null))

            val queued = dao.observeSessionsBySyncState("queued").first()

            assertEquals(1, queued.size)
            assertEquals("SESSION-1", queued.first().sessionId)
        }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest"` (needs a connected device/emulator; if none is available in this environment, skip running it and note that in the final task's manual-check list instead).
Expected: FAIL — `observeSessionsBySyncState` is unresolved.

- [ ] **Step 3: Add the query**

In `SurveySessionDao.kt`, add this import:

```kotlin
import kotlinx.coroutines.flow.Flow
```

and this query (placed after `sessionById`):

```kotlin
    @Query("SELECT * FROM local_survey_session WHERE syncState = :syncState ORDER BY updatedAt DESC")
    fun observeSessionsBySyncState(syncState: String): Flow<List<LocalSurveySessionEntity>>
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest"`
Expected: PASS (or, with no device available, confirm the file compiles via `./gradlew :app:compileDebugAndroidTestKotlin` and flag the run as pending a real-device/emulator check).

- [ ] **Step 5: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt
git commit -m "feat(redesign-phase3): add observeSessionsBySyncState query to SurveySessionDao"
```

---

### Task 3: `SurveyHubUiState` and its local models

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubUiState.kt`

**Interfaces:**
- Consumes: `AssignedSurveyRoute` (`com.luxmap.feature.survey.data`), `SyncStatus` (`com.luxmap.core.theme`).
- Produces: `SurveyHubUiState` (`Loading`/`Success`/`Empty`/`Error`), `UploadingSurveySession(sessionId, surveySweepId, syncStatus)`, `LocalRejectedSweep(surveySweepId, rejectionReason, rejectedByName)`, `LocalPendingAiConfirmation(surveySweepId, normalCount, dimCount, outCount)` — all consumed by Task 4 (ViewModel) and Task 5 (Screen).

- [ ] **Step 1: Create the file**

```kotlin
package com.luxmap.feature.survey.ui.hub

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedSurveyRoute

// Local-only placeholder models, not real backend entities - the "Bị từ chối"/"Chờ xác nhận AI"
// cards have no confirmed sweep-status schema yet (see docs/contract-drift.md and the redesign
// roadmap's decision #2). Rewire to real entities once WP2/WP5 confirm the schema.
data class LocalRejectedSweep(
    val surveySweepId: String,
    val rejectionReason: String,
    val rejectedByName: String,
)

data class LocalPendingAiConfirmation(
    val surveySweepId: String,
    val normalCount: Int,
    val dimCount: Int,
    val outCount: Int,
)

data class UploadingSurveySession(
    val sessionId: String,
    val surveySweepId: String,
    val syncStatus: SyncStatus,
)

sealed interface SurveyHubUiState {
    data object Loading : SurveyHubUiState

    data class Success(
        val assignedRoutes: List<AssignedSurveyRoute>,
        val uploadingSessions: List<UploadingSurveySession> = emptyList(),
        val rejectedSweeps: List<LocalRejectedSweep> = emptyList(),
        val pendingAiConfirmation: List<LocalPendingAiConfirmation> = emptyList(),
    ) : SurveyHubUiState

    data object Empty : SurveyHubUiState

    data class Error(val message: String) : SurveyHubUiState
}
```

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubUiState.kt
git commit -m "feat(redesign-phase3): add SurveyHubUiState"
```

---

### Task 4: `SurveyHubViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModelTest.kt`

**Interfaces:**
- Consumes: `SurveyRepository.observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>>`, `SurveySessionDao.observeSessionsBySyncState(String): Flow<List<LocalSurveySessionEntity>>` (Task 2), `ConnectivityObserver.isOnline: Flow<Boolean>`.
- Produces: `SurveyHubViewModel(repository, sessionDao, connectivityObserver)` with `val uiState: StateFlow<SurveyHubUiState>` and `fun onRetry()` — consumed by Task 5 (`SurveyHubRoute`).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.feature.survey.ui.hub

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.AssignedRoadSegment
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.data.SurveySweepStatus
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SurveyHubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val route =
        AssignedSurveyRoute(
            surveySweepId = "SWEEP-1",
            assignedByName = "Kỹ sư bảo trì A",
            plannedDate = "2026-10-01",
            status = SurveySweepStatus.PLANNED,
            roadSegments = listOf(AssignedRoadSegment("RS-1", "Đường A", 500.0)),
        )

    private fun queuedSession(sessionId: String) =
        LocalSurveySessionEntity(
            sessionId = sessionId,
            surveySweepId = "SWEEP-2",
            workOrderId = "WO-1",
            recordingState = "packaged",
            syncState = "queued",
            startedAtUtc = Instant.parse("2026-10-01T20:00:00Z"),
            startedAtElapsedNs = 0L,
            endedAtUtc = null,
            durationSeconds = null,
            distanceMeters = null,
            gpsTrackFilePath = null,
            luxLogFilePath = null,
            frameTimestampLogFilePath = null,
            captureConfigFilePath = null,
            manifestFilePath = null,
            packageSchemaVersion = "v0",
            timestampSourceRealtime = true,
            bleGapDetected = false,
            createdAt = Instant.parse("2026-10-01T20:00:00Z"),
            updatedAt = Instant.parse("2026-10-01T20:00:00Z"),
        )

    private fun viewModel(
        routes: List<AssignedSurveyRoute> = emptyList(),
        queuedSessions: List<LocalSurveySessionEntity> = emptyList(),
        isOnline: Boolean = true,
        repositoryFlowThrows: Boolean = false,
    ): SurveyHubViewModel {
        val repository = mockk<SurveyRepository>()
        every { repository.observeAssignedRoutes() } returns
            if (repositoryFlowThrows) {
                flow { throw IllegalStateException("offline") }
            } else {
                flowOf(routes)
            }
        val sessionDao = mockk<SurveySessionDao>()
        every { sessionDao.observeSessionsBySyncState("queued") } returns flowOf(queuedSessions)
        val connectivity = mockk<ConnectivityObserver>()
        every { connectivity.isOnline } returns flowOf(isOnline)
        return SurveyHubViewModel(repository, sessionDao, connectivity)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits Empty when there are no routes and nothing uploading`() =
        runTest {
            viewModel().uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success with assigned routes when there is at least one`() =
        runTest {
            viewModel(routes = listOf(route)).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(listOf(route), success.assignedRoutes)
            }
        }

    @Test
    fun `emits Success with an uploading session even when there are no assigned routes`() =
        runTest {
            viewModel(queuedSessions = listOf(queuedSession("SESSION-1"))).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertTrue(success.assignedRoutes.isEmpty())
                assertEquals(1, success.uploadingSessions.size)
                assertEquals(SyncStatus.QUEUED_ONLINE, success.uploadingSessions.first().syncStatus)
            }
        }

    @Test
    fun `an uploading session is marked QUEUED_OFFLINE when the device is offline`() =
        runTest {
            viewModel(queuedSessions = listOf(queuedSession("SESSION-1")), isOnline = false).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(SyncStatus.QUEUED_OFFLINE, success.uploadingSessions.first().syncStatus)
            }
        }

    @Test
    fun `the unconfirmed cards are always empty while SHOW_UNCONFIRMED_HUB_CARDS is false`() =
        runTest {
            viewModel(routes = listOf(route)).uiState.test {
                awaitItem()
                val success = awaitItem() as SurveyHubUiState.Success
                assertTrue(success.rejectedSweeps.isEmpty())
                assertTrue(success.pendingAiConfirmation.isEmpty())
            }
        }

    @Test
    fun `emits Error when the assigned-routes flow fails`() =
        runTest {
            viewModel(repositoryFlowThrows = true).uiState.test {
                awaitItem()
                val error = awaitItem() as SurveyHubUiState.Error
                assertEquals("offline", error.message)
            }
        }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.hub.SurveyHubViewModelTest"`
Expected: FAIL to compile — `SurveyHubViewModel` does not exist yet.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.luxmap.feature.survey.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

// Keeps the 2 unconfirmed-schema cards off by default - see docs/contract-drift.md and the
// redesign roadmap's decision #2. No production code path sets this to true today.
internal const val SHOW_UNCONFIRMED_HUB_CARDS = false

private const val UPLOADING_SYNC_STATE = "queued"

private val FAKE_REJECTED_SWEEPS =
    listOf(
        LocalRejectedSweep(
            surveySweepId = "SWEEP-2030",
            rejectionReason = "Video bị rung mạnh ở đoạn đầu, không đọc được cột đèn",
            rejectedByName = "Kỹ sư bảo trì Trần Văn A",
        ),
    )

private val FAKE_PENDING_AI_CONFIRMATION =
    listOf(
        LocalPendingAiConfirmation(surveySweepId = "SWEEP-2029", normalCount = 11, dimCount = 2, outCount = 1),
    )

@HiltViewModel
class SurveyHubViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
        private val sessionDao: SurveySessionDao,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SurveyHubUiState>(SurveyHubUiState.Loading)
        val uiState: StateFlow<SurveyHubUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun onRetry() {
            if (_uiState.value !is SurveyHubUiState.Error) return
            _uiState.value = SurveyHubUiState.Loading
            load()
        }

        private fun load() {
            viewModelScope.launch {
                combine(
                    repository.observeAssignedRoutes(),
                    sessionDao.observeSessionsBySyncState(UPLOADING_SYNC_STATE),
                    connectivityObserver.isOnline,
                ) { routes, queuedSessions, isOnline ->
                    val uploading =
                        queuedSessions.map { session ->
                            UploadingSurveySession(
                                sessionId = session.sessionId,
                                surveySweepId = session.surveySweepId,
                                syncStatus = if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE,
                            )
                        }
                    val rejected = if (SHOW_UNCONFIRMED_HUB_CARDS) FAKE_REJECTED_SWEEPS else emptyList()
                    val pending = if (SHOW_UNCONFIRMED_HUB_CARDS) FAKE_PENDING_AI_CONFIRMATION else emptyList()
                    if (routes.isEmpty() && uploading.isEmpty() && rejected.isEmpty() && pending.isEmpty()) {
                        SurveyHubUiState.Empty
                    } else {
                        SurveyHubUiState.Success(
                            assignedRoutes = routes,
                            uploadingSessions = uploading,
                            rejectedSweeps = rejected,
                            pendingAiConfirmation = pending,
                        )
                    }
                }.catch { e ->
                    _uiState.value = SurveyHubUiState.Error(e.message ?: "Không tải được Khảo sát")
                }.collect { state ->
                    _uiState.value = state
                }
            }
        }
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.hub.SurveyHubViewModelTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModel.kt app/src/test/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModelTest.kt
git commit -m "feat(redesign-phase3): add SurveyHubViewModel"
```

---

### Task 5: `SurveyHubScreen` Composable

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt`

**Interfaces:**
- Consumes: `SurveyHubUiState` (Task 3), `SurveyHubViewModel` (Task 4), shared components `MessageState`, `PrimaryButton`, `StatusBadge`, `SyncStatus.badgeColors()`/`.label()`, `AssetCondition.badgeColors()`/`.label()`, tokens `Spacing`/`Radius`/`OverlineTextStyle`/`HeadlineTextStyle`/`LuxMapTheme`.
- Produces: `SurveyHubRoute(onCheckReadiness: (surveySweepId: String) -> Unit, viewModel: SurveyHubViewModel = hiltViewModel())` (stateful) and `SurveyHubScreen(state, onCheckReadiness, onRetry, onReviewAiConfirmation, onResurvey, modifier)` (stateless, for previews/tests) — consumed by Task 6 (`NavGraph.kt`).

- [ ] **Step 1: Create the file**

```kotlin
package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.AssignedSurveyRoute

@Composable
fun SurveyHubRoute(
    onCheckReadiness: (surveySweepId: String) -> Unit,
    viewModel: SurveyHubViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SurveyHubScreen(
        state = uiState,
        onCheckReadiness = onCheckReadiness,
        onRetry = viewModel::onRetry,
    )
}

@Composable
fun SurveyHubScreen(
    state: SurveyHubUiState,
    onCheckReadiness: (surveySweepId: String) -> Unit,
    onRetry: () -> Unit = {},
    onReviewAiConfirmation: (surveySweepId: String) -> Unit = {},
    onResurvey: (surveySweepId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LuxMapTheme(forceDark = true) {
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = "Khảo sát",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(Spacing.lg),
            )
            Box(Modifier.fillMaxSize()) {
                when (state) {
                    is SurveyHubUiState.Loading ->
                        CircularProgressIndicator(
                            modifier =
                                Modifier
                                    .padding(Spacing.lg)
                                    .testTag("survey_hub_loading"),
                        )

                    is SurveyHubUiState.Success ->
                        SurveyHubContent(
                            state = state,
                            onCheckReadiness = onCheckReadiness,
                            onReviewAiConfirmation = onReviewAiConfirmation,
                            onResurvey = onResurvey,
                        )

                    is SurveyHubUiState.Empty ->
                        MessageState(
                            icon = Icons.Filled.EventAvailable,
                            iconColors = SyncStatus.DONE.badgeColors(),
                            title = "Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì",
                            subtitle = "Khi có tuyến mới được giao, danh sách sẽ hiển thị tại đây.",
                            action = {},
                        )

                    is SurveyHubUiState.Error ->
                        MessageState(
                            icon = Icons.Filled.CloudOff,
                            iconColors = SyncStatus.FAILED.badgeColors(),
                            title = state.message,
                            subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        ) {
                            OutlinedButton(
                                onClick = onRetry,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Text("Thử lại")
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    onCheckReadiness: (surveySweepId: String) -> Unit,
    onReviewAiConfirmation: (surveySweepId: String) -> Unit,
    onResurvey: (surveySweepId: String) -> Unit,
) {
    val hasAttentionCards =
        state.rejectedSweeps.isNotEmpty() || state.pendingAiConfirmation.isNotEmpty() || state.uploadingSessions.isNotEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (hasAttentionCards) {
            item {
                Text(
                    text = "CẦN BẠN XỬ LÝ",
                    style = OverlineTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.rejectedSweeps) { rejected ->
                RejectedSweepCard(rejected, onResurvey = { onResurvey(rejected.surveySweepId) })
            }
            items(state.pendingAiConfirmation) { pending ->
                AiPendingCard(pending, onReview = { onReviewAiConfirmation(pending.surveySweepId) })
            }
            items(state.uploadingSessions) { session -> UploadingSessionRow(session) }
        }
        item {
            Text(
                text = "TUYẾN ĐƯỢC GIAO",
                style = OverlineTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.assignedRoutes) { route ->
            AssignedRouteCard(route, onCheckReadiness = { onCheckReadiness(route.surveySweepId) })
        }
    }
}

@Composable
private fun RejectedSweepCard(
    sweep: LocalRejectedSweep,
    onResurvey: () -> Unit,
) {
    val colors = AssetCondition.OUT.badgeColors(isDark = true)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, colors.background, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "BỊ TỪ CHỐI · ${sweep.surveySweepId}", style = OverlineTextStyle, color = colors.text)
        Text(text = sweep.rejectionReason, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Bởi ${sweep.rejectedByName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Khảo sát lại", onClick = onResurvey, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AiPendingCard(
    pending: LocalPendingAiConfirmation,
    onReview: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = "CHỜ XÁC NHẬN AI · ${pending.surveySweepId}", style = OverlineTextStyle)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(
                text = "${AssetCondition.NORMAL.label()} ${pending.normalCount}",
                colors = AssetCondition.NORMAL.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.DIM.label()} ${pending.dimCount}",
                colors = AssetCondition.DIM.badgeColors(isDark = true),
            )
            StatusBadge(
                text = "${AssetCondition.OUT.label()} ${pending.outCount}",
                colors = AssetCondition.OUT.badgeColors(isDark = true),
            )
        }
        PrimaryButton(text = "Xem & xác nhận", onClick = onReview, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun UploadingSessionRow(session: UploadingSurveySession) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Đang tải lên · ${session.surveySweepId}", style = MaterialTheme.typography.bodyMedium)
        StatusBadge(text = session.syncStatus.label(), colors = session.syncStatus.badgeColors())
    }
}

@Composable
private fun AssignedRouteCard(
    route: AssignedSurveyRoute,
    onCheckReadiness: () -> Unit,
) {
    val totalLengthMeters = route.roadSegments.mapNotNull { it.lengthMeters }.sum()
    val segmentsLabel =
        if (totalLengthMeters > 0) {
            "${route.roadSegments.size} đoạn · ${totalLengthMeters.toInt()} m"
        } else {
            "${route.roadSegments.size} đoạn"
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = route.surveySweepId, style = MaterialTheme.typography.titleMedium)
        Text(
            text = segmentsLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Ngày dự kiến: ${route.plannedDate} · Giao bởi: ${route.assignedByName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Kiểm tra sẵn sàng", onClick = onCheckReadiness, modifier = Modifier.fillMaxWidth())
    }
}
```

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt
git commit -m "feat(redesign-phase3): add SurveyHubScreen"
```

---

### Task 6: Wire `NavGraph.kt` — Hub becomes the `survey` landing, `SurveyPlanScreen` moves to the new route

**Files:**
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`

**Interfaces:**
- Consumes: `SurveyHubRoute` (Task 5), `Routes.SurveyPlan` (Task 1), existing `SurveyPlanScreen`.

- [ ] **Step 1: Update imports**

Replace:

```kotlin
import com.luxmap.feature.survey.ui.plan.SurveyPlanScreen
```

with:

```kotlin
import com.luxmap.feature.survey.ui.hub.SurveyHubRoute
import com.luxmap.feature.survey.ui.plan.SurveyPlanScreen
```

- [ ] **Step 2: Replace the `Routes.Survey` composable, add the new `Routes.SurveyPlan` composable**

Replace this block (current lines 153-165):

```kotlin
            composable(Routes.Survey.route) {
                SurveyPlanScreen(
                    onEnterCaptureMode = { surveySweepId ->
                        // F03's standalone survey-plan entry has no work order in scope at all -
                        // threading a real workOrderId here is out of scope for this plan (fm-40
                        // only covers the Work Order "Bắt đầu" entry into capture). Same gap shape
                        // and same tracking as the onRedo path below.
                        navController.navigate(
                            Routes.SurveyCapture.createRoute(workOrderId = "", surveySweepId = surveySweepId),
                        )
                    },
                )
            }
```

with:

```kotlin
            composable(Routes.Survey.route) {
                SurveyHubRoute(
                    onCheckReadiness = { surveySweepId ->
                        navController.navigate(Routes.SurveyPlan.createRoute(surveySweepId))
                    },
                )
            }
            composable(
                route = Routes.SurveyPlan.route,
                arguments = listOf(navArgument("surveySweepId") { type = NavType.StringType }),
            ) {
                SurveyPlanScreen(
                    onEnterCaptureMode = { surveySweepId ->
                        // F03's standalone survey-plan entry has no work order in scope at all -
                        // threading a real workOrderId here is out of scope for this plan (fm-40
                        // only covers the Work Order "Bắt đầu" entry into capture). Same gap shape
                        // and same tracking as the onRedo path below.
                        navController.navigate(
                            Routes.SurveyCapture.createRoute(workOrderId = "", surveySweepId = surveySweepId),
                        )
                    },
                )
            }
```

(The `surveySweepId` path argument is read by the nav graph but not yet passed into `SurveyPlanScreen` — it still shows every assigned route and lets the user pick one, same as before. Phase 4 is where `SurveyPlanScreen` starts actually using it. Tracked in `docs/contract-drift.md`, see Task 7.)

- [ ] **Step 3: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/navigation/NavGraph.kt
git commit -m "feat(redesign-phase3): make SurveyHub the survey tab landing screen"
```

---

### Task 7: Log the new gap in `docs/contract-drift.md`, final build/manual check, open PR

**Files:**
- Modify: `docs/contract-drift.md`

- [ ] **Step 1: Add 2 new rows to the drift table**

Append these 2 rows to the table in `docs/contract-drift.md` (same format as the existing rows):

```markdown
| `survey/plan/{surveySweepId}` route doesn't use its own argument yet | `NavGraph.kt`'s new `Routes.SurveyPlan` destination reads `surveySweepId` as a nav argument but passes it nowhere — `SurveyPlanScreen` still shows every assigned route and lets the user (re-)pick one | Known gap (redesign Phase 3) — `SurveyPlanScreen`/`SurveyPlanViewModel` internals are untouched in Phase 3 by design (visual/navigation-only phase); Phase 4 of the redesign roadmap (`docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md`) is where this screen is expected to start using the id, e.g. to preselect or filter to that one route | WP6 (Phase 4) |
| Hub "Bị từ chối"/"Chờ xác nhận AI" cards | `feature/survey/ui/hub/SurveyHubViewModel.kt`'s `SHOW_UNCONFIRMED_HUB_CARDS` (`false`) and the `LocalRejectedSweep`/`LocalPendingAiConfirmation` local models | UI-only, flag defaults off, per redesign roadmap decision #2 — no real sweep-status enum/API backs these yet; same open item as the roadmap's own "⚠" note on the Hub Khảo sát spec section | WP2/WP5 |
```

- [ ] **Step 2: Full build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck testDebugUnitTest --tests "com.luxmap.feature.survey.ui.hub.*"`
Expected: BUILD SUCCESSFUL, all `hub` tests pass.

- [ ] **Step 3: Manual walkthrough (ledger as skipped if no device/emulator is available, same pattern as Phase 0/1/2)**

Check, in both the online and offline case if possible:
- Survey tab shows the Hub (not the old full-screen checklist) with the "TUYẾN ĐƯỢC GIAO" section and the fake route from `FakeSurveyRepository`.
- Tapping "Kiểm tra sẵn sàng" navigates to `survey/plan/SWEEP-2031` and shows the existing readiness-checklist screen.
- System Back from that screen returns to the Hub, not further back.
- With `SHOW_UNCONFIRMED_HUB_CARDS` temporarily flipped to `true` locally (never committed): the "CẦN BẠN XỬ LÝ" section renders the rejected-sweep card (red border) and the AI-pending card, each with a working (snackbar/no-op) CTA, then revert the flag before committing anything else.
- Empty state (temporarily stub `FakeSurveyRepository.observeAssignedRoutes()` to emit `emptyList()`) shows the "Chưa có tuyến nào được phân công" message, not a blank screen.

- [ ] **Step 4: Commit the drift-log update**

```bash
git add docs/contract-drift.md
git commit -m "docs(redesign-phase3): log survey hub's 2 known gaps in contract-drift"
```

- [ ] **Step 5: Open PR**

Target `dev`. Title: `feat(redesign-phase3): add Survey Hub landing screen`.
