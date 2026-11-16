# F02 Việc hôm nay — Redesign vNext Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the F02 "Việc hôm nay" screen to Design System vNext, with correct counts/empty rules and real sync-state badges, without changing API, Room schema or sync flow.

**Architecture:** Pure list logic moves out of the composable into `HomeTaskList.kt` (unit-tested). `WorkOrderCompletionRepository` gains a read-only `observeCompletionSyncStates()` that joins local completions with `sync_queue` op status. `HomeScreen` is rebuilt from vNext components (`LuxTabTopBar`, `LuxSegmented`, `LuxCard`, `LuxEmptyState`, `LuxErrorState`, `LuxLoadingRegion`, `LuxBanner`); the global floating bell is hidden on Home and the bell moves into the top bar.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01, Material 3), Hilt, Room, Coroutines/Flow, JUnit + MockK + Turbine (unit), Compose UI Test (androidTest).

**Spec:** `docs/design-system/screens/F02_HOME.md` (approved 2026-11-16, incl. review R1–R5).

## Global Constraints

- Branch: `style/redesign-home-vnext` (already created from `dev`, holds the 2 spec commits). Never commit to `main`.
- Each commit ≤ 400 changed lines. Commit messages `type(home): description` in English, imperative, lowercase. **No `Co-Authored-By` trailer, no Claude attribution anywhere.**
- No new code comments in code you write.
- No new libraries. No API, Room schema or sync-flow change.
- `feature/` reads only `MaterialTheme.colorScheme/typography/shapes`, `LuxTheme.colors`, `LuxTheme.status`/`*.badgeColors()`. No primitive colors, no `Color(0x…)`, no legacy `*TextStyle`, no `BrandHeroGradient`.
- Touch targets ≥ 48dp. Screen margin 16dp (`Spacing.lg`).
- Survey (`survey`) and pole GPS (`pole_gps_update`) work orders never appear on Home.
- Copy (exact):
  - Top bar title `Việc hôm nay`; subtitle `Xin chào, {username} · {Thứ…, dd/MM/yyyy}` (no username → `Xin chào · {date}`).
  - Stats labels `Tổng`, `Quá hạn`, `Đang làm`.
  - Segments `Sửa chữa · {n}`, `Kiểm tra · {n}`.
  - Empty: title `Chưa có việc được giao`, message `Khi Kỹ sư bảo trì giao lệnh sửa chữa hoặc kiểm tra, lệnh sẽ hiện ở đây.`, action `Làm mới`.
  - Error network: title `Không tải được danh sách lệnh`, message `Kiểm tra kết nối rồi thử lại.`; error generic: title `Không tải được danh sách lệnh`, message `Vui lòng thử lại sau ít phút.`; button `Thử lại` (from `LuxErrorState`).
  - Tab empty: repair `Không có nhiệm vụ sửa chữa` / `Lệnh sửa chữa và bảo trì được giao sẽ hiện ở đây.`; inspection `Không có nhiệm vụ kiểm tra` / `Lệnh kiểm tra cột đèn và thiết bị được giao sẽ hiện ở đây.`
  - Badges: `Đã nộp · chờ đồng bộ`, `Đồng bộ lỗi`, `Xung đột`, `Quá hạn`, else `wo_status` label.
  - Dates: `Làm: dd/MM/yyyy`; due text from `dueDateLabel()`; neither date → `Chưa có lịch`.
- Unit tests: `./gradlew testLocalDebugUnitTest --tests "<class>"`. Device tests (SM-A075F): `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`. Do not run the whole unit suite without asking.
- After each task: `./gradlew ktlintCheck`, then stop and summarize for the user before the next task (CLAUDE.md step-by-step rule).

## Review Focus

1. **Unknown `task_kind`** from a future backend (e.g. `"audit"`): must not appear on Home and must not be counted. `workOrderTaskKindFromWire` maps unknown values to `INSPECTION`, so filtering must use the raw wire strings. Test in Task 1.
2. **Sync op row missing** (completion row `pending` but no `sync_queue` row yet, or already pruned): must show `PENDING`, not crash or vanish. Test in Task 2.
3. **Completion for a work order that is no longer on the list** (synced and server already `done`): the map may contain ids not on screen; Home must ignore them silently. Covered by lookup-by-id in Task 4 badge function test.
4. **Malformed dates** (`due_date = "2026-13-40"`, `scheduled_date = ""`): no crash; treated as missing. Test in Task 4 date-line function.
5. **Retry while a sync-state flow is live**: `retry()` must cancel the previous combined collector (existing `loadJob?.cancel()`), so states do not interleave. Test in Task 2.

---

## File Map

| File | Responsibility | Task |
|---|---|---|
| `feature/home/data/HomeModels.kt` | add `communeId`, `title`, `scheduledDate`, `faultCount` to `WorkOrderSummaryItem` | 1 |
| `feature/home/data/RealHomeRepository.kt` | map the 4 new fields | 1 |
| `feature/home/data/FakeHomeRepository.kt` | sample data with new fields | 1 |
| `feature/home/ui/HomeTaskList.kt` (new) | `HomeTaskTab`, `HomeSummary`, `visibleTasks()`, `homeSummary()`, `countFor()`, `sortedFor()` | 1 |
| `feature/home/ui/HomeViewModel.kt` | empty rule on visible tasks; sync states; network error kind | 1, 2 |
| `feature/home/ui/HomeUiState.kt` | `completionSyncStates`, `isNetworkError` | 2 |
| `feature/workorder/data/CompletionSyncState.kt` (new) | `enum class CompletionSyncState { PENDING, FAILED, CONFLICT }` | 2 |
| `feature/workorder/data/WorkOrderCompletionRepository.kt` + `RealWorkOrderCompletionRepository.kt` | `observeCompletionSyncStates()` | 2 |
| `navigation/NavGraph.kt` | hide floating bell on Home, pass unread count + open action | 3 |
| `feature/home/ui/HomeScreen.kt` | frame, top bar, stats, segmented, 4 states | 3, 4, 5 |
| `feature/home/ui/HomeTaskCard.kt` (new) | card, `homeTaskBadge()`, `homeDateLine()` | 4 |
| tests | see each task | 1–6 |

---

### Task 1: Data fields, pure list logic and the new empty rule

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/home/data/HomeModels.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/data/RealHomeRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/data/FakeHomeRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/home/ui/HomeTaskList.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (swap old private helpers for the new functions only; no visual change)
- Create: `app/src/test/java/com/luxmap/feature/home/ui/HomeTaskListTest.kt`
- Delete: `app/src/test/java/com/luxmap/feature/home/ui/HomeTaskKindSectionsTest.kt`
- Modify: `app/src/test/java/com/luxmap/feature/home/data/RealHomeRepositoryTest.kt`
- Modify: `app/src/test/java/com/luxmap/feature/home/ui/HomeViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt` (constructor calls only, so it compiles)

**Interfaces:**
- Produces:
  - `data class WorkOrderSummaryItem(workOrderId: String, woStatus: String, dueDate: String?, priorityScore: Double?, taskKind: String, communeId: String, title: String, scheduledDate: String?, faultCount: Int)`
  - `enum class HomeTaskTab(val wireKind: String, val label: String) { REPAIR("repair", "Sửa chữa"), INSPECTION("inspection", "Kiểm tra") }`
  - `data class HomeSummary(val total: Int, val overdue: Int, val inProgress: Int)`
  - `fun HomeData.visibleTasks(): List<WorkOrderSummaryItem>`
  - `fun List<WorkOrderSummaryItem>.homeSummary(today: LocalDate): HomeSummary`
  - `fun List<WorkOrderSummaryItem>.countFor(tab: HomeTaskTab): Int`
  - `fun List<WorkOrderSummaryItem>.sortedFor(tab: HomeTaskTab, today: LocalDate): List<WorkOrderSummaryItem>`

Note: `communeId` is added on top of the 3 fields in spec §8.1 so the card no longer has to search clusters for its commune label (`clusterLabelFor`). Same data, no API change.

- [ ] **Step 1: Write the failing pure-logic tests**

Create `app/src/test/java/com/luxmap/feature/home/ui/HomeTaskListTest.kt`:

```kotlin
package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-16")

private fun item(
    id: String,
    kind: String = "repair",
    status: String = "assigned",
    due: String? = null,
    score: Double? = null,
) = WorkOrderSummaryItem(
    workOrderId = id,
    woStatus = status,
    dueDate = due,
    priorityScore = score,
    taskKind = kind,
    communeId = "COM-001",
    title = "Lệnh $id",
    scheduledDate = null,
    faultCount = 0,
)

private fun data(vararg items: WorkOrderSummaryItem) =
    HomeData(
        metrics = HomeMetrics(assignedCount = 99, inProgressCount = 99, overdueCount = 99, plannedSweepCount = null),
        clusters = listOf(WorkOrderCluster(clusterLabel = "COM-001", items = items.toList())),
    )

class HomeTaskListTest {
    @Test
    fun `visible tasks keep only repair and inspection, dropping survey, pole gps and unknown kinds`() {
        val visible =
            data(
                item("R", kind = "repair"),
                item("I", kind = "inspection"),
                item("S", kind = "survey"),
                item("G", kind = "pole_gps_update"),
                item("X", kind = "audit"),
            ).visibleTasks()
        assertEquals(listOf("R", "I"), visible.map { it.workOrderId })
    }

    @Test
    fun `summary counts only the visible tasks, not HomeMetrics`() {
        val visible =
            data(
                item("R1", status = "in_progress", due = "2026-11-15"),
                item("R2", status = "assigned", due = "2026-11-20"),
                item("I1", kind = "inspection", status = "in_progress"),
                item("S1", kind = "survey", status = "in_progress", due = "2026-11-01"),
            ).visibleTasks()
        assertEquals(HomeSummary(total = 3, overdue = 1, inProgress = 2), visible.homeSummary(TODAY))
    }

    @Test
    fun `count per tab`() {
        val visible = data(item("R1"), item("R2"), item("I1", kind = "inspection")).visibleTasks()
        assertEquals(2, visible.countFor(HomeTaskTab.REPAIR))
        assertEquals(1, visible.countFor(HomeTaskTab.INSPECTION))
    }

    @Test
    fun `sorted for a tab puts overdue first, then due today, then higher priority score`() {
        val visible =
            data(
                item("LOW", score = 10.0, due = "2026-11-30"),
                item("HIGH", score = 90.0, due = "2026-11-30"),
                item("TODAY", score = 1.0, due = "2026-11-16"),
                item("OVERDUE", score = 0.0, due = "2026-11-10"),
                item("NO_SCORE", score = null),
                item("INSPECTION", kind = "inspection", due = "2026-11-01"),
            ).visibleTasks()
        assertEquals(
            listOf("OVERDUE", "TODAY", "HIGH", "LOW", "NO_SCORE"),
            visible.sortedFor(HomeTaskTab.REPAIR, TODAY).map { it.workOrderId },
        )
    }
}
```

Delete `app/src/test/java/com/luxmap/feature/home/ui/HomeTaskKindSectionsTest.kt` (its `TASK_KIND_SECTION_ORDER` constant is removed in Step 4).

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.ui.HomeTaskListTest"`
Expected: compilation FAIL (`communeId`, `HomeTaskTab`, `visibleTasks` unresolved).

- [ ] **Step 3: Add the fields and map them**

`HomeModels.kt` — replace `WorkOrderSummaryItem` with:

```kotlin
data class WorkOrderSummaryItem(
    val workOrderId: String,
    val woStatus: String,
    val dueDate: String?,
    val priorityScore: Double?,
    val taskKind: String,
    val communeId: String,
    val title: String,
    val scheduledDate: String?,
    val faultCount: Int,
)
```

`RealHomeRepository.kt` — replace `toSummaryItem()` with:

```kotlin
        private fun WorkOrderItemDto.toSummaryItem() =
            WorkOrderSummaryItem(
                workOrderId = workOrderId,
                woStatus = woStatus,
                dueDate = dueDate,
                priorityScore = priorityScore,
                taskKind = taskKind,
                communeId = communeId,
                title = title,
                scheduledDate = scheduledDate,
                faultCount = faultIds.size,
            )
```

`FakeHomeRepository.kt` — add to each of the 3 `WorkOrderSummaryItem(...)` calls (keep existing args):
- WO-2031: `communeId = "COM-001", title = "Kiểm tra đèn tuyến liên xã Phước Hiệp", scheduledDate = "2026-09-27", faultCount = 3,`
- WO-2028: `communeId = "COM-001", title = "Thay bóng đèn đoạn qua chợ", scheduledDate = "2026-09-28", faultCount = 1,`
- WO-2019: `communeId = "COM-002", title = "Sửa tủ điện ấp 3", scheduledDate = null, faultCount = 2,`

- [ ] **Step 4: Create `HomeTaskList.kt`**

```kotlin
package com.luxmap.feature.home.ui

import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import java.time.LocalDate

enum class HomeTaskTab(val wireKind: String, val label: String) {
    REPAIR("repair", "Sửa chữa"),
    INSPECTION("inspection", "Kiểm tra"),
}

data class HomeSummary(val total: Int, val overdue: Int, val inProgress: Int)

private val VISIBLE_KINDS = HomeTaskTab.entries.map { it.wireKind }.toSet()

fun HomeData.visibleTasks(): List<WorkOrderSummaryItem> = clusters.flatMap { it.items }.filter { it.taskKind in VISIBLE_KINDS }

fun List<WorkOrderSummaryItem>.homeSummary(today: LocalDate): HomeSummary =
    HomeSummary(
        total = size,
        overdue = count { dueDateLabel(it.dueDate, today).isOverdue },
        inProgress = count { it.woStatus == "in_progress" },
    )

fun List<WorkOrderSummaryItem>.countFor(tab: HomeTaskTab): Int = count { it.taskKind == tab.wireKind }

fun List<WorkOrderSummaryItem>.sortedFor(
    tab: HomeTaskTab,
    today: LocalDate,
): List<WorkOrderSummaryItem> =
    filter { it.taskKind == tab.wireKind }.sortedWith(
        compareByDescending<WorkOrderSummaryItem> { dueDateLabel(it.dueDate, today).isOverdue }
            .thenByDescending { dueDateLabel(it.dueDate, today).isToday }
            .thenByDescending { it.priorityScore ?: 0.0 },
    )
```

- [ ] **Step 5: Point `HomeScreen.kt` at the new functions (no visual change)**

In `HomeScreen.kt`:
- Delete `TaskFilterTab`, `TASK_KIND_SECTION_ORDER`, `allVisibleItems()`, `taskFilterTabCounts()`, `filteredSortedItems()`, `clusterLabelFor()`.
- In `HomeContent`, replace the tab state/counts/list lines with:

```kotlin
    var selectedTab by rememberSaveable { mutableStateOf(HomeTaskTab.REPAIR) }
    val today = remember { LocalDate.now() }
    val tasks = remember(data) { data.visibleTasks() }
    val summary = remember(tasks, today) { tasks.homeSummary(today) }
```
  - `SummaryStatsRow(total = summary.total, overdue = summary.overdue, inProgress = summary.inProgress)`
  - `TaskFilterTabs(selected = selectedTab, counts = HomeTaskTab.entries.associateWith { tasks.countFor(it) }, onSelect = { selectedTab = it })`
  - `val visibleItems = remember(tasks, selectedTab, today) { tasks.sortedFor(selectedTab, today) }`
  - `TaskRow(item = item, clusterLabel = item.communeId, ...)`
- Change every `TaskFilterTab` type reference in `TaskFilterTabs`/`TabEmptyState` to `HomeTaskTab`, and `HomeTaskTab.entries.forEach` in `TaskFilterTabs`.

- [ ] **Step 6: Empty rule in `HomeViewModel`**

Replace the `if (data.isEmpty())` condition with `if (data.visibleTasks().isEmpty())` and delete the private `HomeData.isEmpty()` extension at the bottom of the file.

- [ ] **Step 7: Update existing tests to the new model**

`HomeScreenTest.kt`: add `communeId = "COM-001", title = "Sửa đèn", scheduledDate = null, faultCount = 0,` to both `WorkOrderSummaryItem(...)` calls.

`RealHomeRepositoryTest.kt`: in the `workOrder(...)` helper add params `kind: String = "repair"`, `title: String = "Fix lamp"`, `scheduledDate: String? = null`, `faultIds: List<String> = listOf("FAULT-1")` and pass them as `taskKind = kind`, `title = title`, `faultIds = faultIds`, `scheduledDate = scheduledDate`. Add:

```kotlin
    @Test
    fun `maps title, scheduled_date, fault count and commune into the summary item`() =
        runTest {
            val items =
                listOf(
                    workOrder(
                        "WO-1",
                        communeId = "COM-009",
                        title = "Sửa đèn chợ",
                        scheduledDate = "2026-11-17",
                        faultIds = listOf("F-1", "F-2", "F-3"),
                    ),
                )
            repository(items).observeHomeData().test {
                val item = awaitItem().clusters.single().items.single()
                assertEquals("Sửa đèn chợ", item.title)
                assertEquals("2026-11-17", item.scheduledDate)
                assertEquals(3, item.faultCount)
                assertEquals("COM-009", item.communeId)
                awaitComplete()
            }
        }
```

`HomeViewModelTest.kt`: add a helper and replace the Empty / Success tests:

```kotlin
private fun summaryItem(
    id: String,
    kind: String,
) = WorkOrderSummaryItem(
    workOrderId = id,
    woStatus = "assigned",
    dueDate = null,
    priorityScore = null,
    taskKind = kind,
    communeId = "COM-001",
    title = "Lệnh $id",
    scheduledDate = null,
    faultCount = 0,
)

private fun homeData(vararg items: WorkOrderSummaryItem) =
    HomeData(
        metrics = HomeMetrics(assignedCount = items.size, inProgressCount = 0, overdueCount = 0, plannedSweepCount = null),
        clusters = listOf(WorkOrderCluster(clusterLabel = "COM-001", items = items.toList())),
    )
```

Tests (replace `emits Empty when repository has no work orders and no planned sweeps` and `emits Success with the repository data when there is at least one work order`; in `carries locally completed…` and `retry…` change their `data` to `homeData(summaryItem("WO-1", "repair"))`):

```kotlin
    @Test
    fun `emits Empty when there are no work orders at all`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData())
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Empty when the API only returns survey and pole gps work orders`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns
                flowOf(homeData(summaryItem("S1", "survey"), summaryItem("G1", "pole_gps_update")))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(HomeUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `emits Success when only one tab has work orders`() =
        runTest {
            val data = homeData(summaryItem("R1", "repair"))
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(data)
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(data, (awaitItem() as HomeUiState.Success).data)
            }
        }
```

Add import `com.luxmap.feature.home.data.WorkOrderSummaryItem`.

- [ ] **Step 8: Run unit tests and compile androidTest**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.*" compileLocalDebugAndroidTestKotlin ktlintCheck`
Expected: PASS (HomeTaskListTest 4, RealHomeRepositoryTest 7, HomeViewModelTest 7), build OK.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/home app/src/test/java/com/luxmap/feature/home app/src/androidTest/java/com/luxmap/feature/home
git commit -m "refactor(home): count and empty-check only visible repair and inspection work orders"
```

Stop and summarize for the user.

---

### Task 2: Real sync-state badges data and network error kind

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/CompletionSyncState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderCompletionRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (rename usage only)
- Modify: `app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepositoryTest.kt`
- Modify: `app/src/test/java/com/luxmap/feature/home/ui/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `SyncQueueDao.observeStatusesFor(clientOpIds: List<String>): Flow<List<SyncQueueEntity>>` (exists), `WorkOrderCompletionDao.observeAllCompletions()` (exists).
- Produces:
  - `enum class CompletionSyncState { PENDING, FAILED, CONFLICT }`
  - `WorkOrderCompletionRepository.observeCompletionSyncStates(): Flow<Map<String, CompletionSyncState>>` (key = `workOrderId`; synced rows absent)
  - `HomeUiState.Success(data: HomeData, isStale: Boolean = false, lastSyncedAt: Instant? = null, completionSyncStates: Map<String, CompletionSyncState> = emptyMap())`
  - `HomeUiState.Error(message: String, isNetworkError: Boolean = false)`

- [ ] **Step 1: Write the failing repository tests**

`RealWorkOrderCompletionRepositoryTest.kt`: every `RealWorkOrderCompletionRepository(dao, syncQueueManager)` becomes `RealWorkOrderCompletionRepository(dao, syncQueueManager, mockk(relaxed = true))`. Add:

```kotlin
    private fun completion(
        workOrderId: String,
        clientOpId: String,
        submitStatus: String,
    ) = LocalWorkOrderCompletionEntity(
        workOrderId = workOrderId,
        reportNote = "note",
        materialsUsed = null,
        faultOutcomesJson = null,
        clientOpId = clientOpId,
        submitStatus = submitStatus,
    )

    private fun op(
        clientOpId: String,
        status: String,
    ) = SyncQueueEntity(
        clientOpId = clientOpId,
        opType = "complete_work_order",
        payloadJson = "{}",
        dependsOnClientOpId = null,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `sync states map queued, failed, conflict and a missing op, and leave out synced rows`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>()
            val syncQueueDao = mockk<SyncQueueDao>()
            every { dao.observeAllCompletions() } returns
                flowOf(
                    listOf(
                        completion("WO-Q", "OP-Q", "pending"),
                        completion("WO-F", "OP-F", "pending"),
                        completion("WO-C", "OP-C", "pending"),
                        completion("WO-M", "OP-M", "pending"),
                        completion("WO-S", "OP-S", "synced"),
                    ),
                )
            every { syncQueueDao.observeStatusesFor(listOf("OP-Q", "OP-F", "OP-C", "OP-M")) } returns
                flowOf(listOf(op("OP-Q", "queued"), op("OP-F", "failed"), op("OP-C", "conflict")))
            val repository = RealWorkOrderCompletionRepository(dao, mockk(relaxed = true), syncQueueDao)

            assertEquals(
                mapOf(
                    "WO-Q" to CompletionSyncState.PENDING,
                    "WO-F" to CompletionSyncState.FAILED,
                    "WO-C" to CompletionSyncState.CONFLICT,
                    "WO-M" to CompletionSyncState.PENDING,
                ),
                repository.observeCompletionSyncStates().first(),
            )
        }

    @Test
    fun `a completion moving from pending to synced leaves the sync state map`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>()
            val syncQueueDao = mockk<SyncQueueDao>()
            val completions = MutableStateFlow(listOf(completion("WO-1", "OP-1", "pending")))
            every { dao.observeAllCompletions() } returns completions
            every { syncQueueDao.observeStatusesFor(listOf("OP-1")) } returns flowOf(listOf(op("OP-1", "syncing")))
            val repository = RealWorkOrderCompletionRepository(dao, mockk(relaxed = true), syncQueueDao)

            repository.observeCompletionSyncStates().test {
                assertEquals(mapOf("WO-1" to CompletionSyncState.PENDING), awaitItem())
                completions.value = listOf(completion("WO-1", "OP-1", "synced"))
                assertEquals(emptyMap<String, CompletionSyncState>(), awaitItem())
            }
        }
```

Imports to add: `app.cash.turbine.test`, `com.luxmap.core.sync.SyncQueueDao`, `com.luxmap.core.sync.SyncQueueEntity`, `io.mockk.every`, `kotlinx.coroutines.flow.MutableStateFlow`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`
Expected: compilation FAIL (constructor arity, `CompletionSyncState`, `observeCompletionSyncStates`).

- [ ] **Step 3: Implement**

`CompletionSyncState.kt`:

```kotlin
package com.luxmap.feature.workorder.data

enum class CompletionSyncState { PENDING, FAILED, CONFLICT }
```

`WorkOrderCompletionRepository.kt` — add below `observeCompletedWorkOrderIds()`:

```kotlin
    fun observeCompletionSyncStates(): Flow<Map<String, CompletionSyncState>>
```

`RealWorkOrderCompletionRepository.kt` — constructor gains `private val syncQueueDao: SyncQueueDao,` as the third parameter; add imports `com.luxmap.core.sync.SyncQueueDao`, `kotlinx.coroutines.ExperimentalCoroutinesApi`, `kotlinx.coroutines.flow.flatMapLatest`, `kotlinx.coroutines.flow.flowOf`; add:

```kotlin
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeCompletionSyncStates(): Flow<Map<String, CompletionSyncState>> =
            dao.observeAllCompletions().flatMapLatest { completions ->
                val unsynced = completions.filter { it.submitStatus != "synced" }
                if (unsynced.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    syncQueueDao.observeStatusesFor(unsynced.map { it.clientOpId }).map { ops ->
                        val statusByOp = ops.associate { it.clientOpId to it.status }
                        unsynced.associate { it.workOrderId to completionSyncState(statusByOp[it.clientOpId]) }
                    }
                }
            }

        private fun completionSyncState(opStatus: String?): CompletionSyncState =
            when (opStatus) {
                "failed" -> CompletionSyncState.FAILED
                "conflict" -> CompletionSyncState.CONFLICT
                else -> CompletionSyncState.PENDING
            }
```

Hilt already provides `SyncQueueDao` (`DatabaseModule.provideSyncQueueDao`), no DI change.

- [ ] **Step 4: Run repository tests**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Write the failing ViewModel tests**

`HomeViewModelTest.kt` — replace `fakeCompletionRepository` with:

```kotlin
private fun fakeCompletionRepository(
    states: Flow<Map<String, CompletionSyncState>> = flowOf(emptyMap()),
): WorkOrderCompletionRepository {
    val repository = mockk<WorkOrderCompletionRepository>()
    every { repository.observeCompletionSyncStates() } returns states
    return repository
}
```

Replace `carries locally completed work order ids into the success state` with:

```kotlin
    @Test
    fun `carries sync states into Success and drops the badge once synced`() =
        runTest {
            val states = MutableStateFlow(mapOf("WO-1" to CompletionSyncState.PENDING))
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData(summaryItem("WO-1", "repair")))
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(states), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(
                    mapOf("WO-1" to CompletionSyncState.PENDING),
                    (awaitItem() as HomeUiState.Success).completionSyncStates,
                )
                states.value = emptyMap()
                assertEquals(emptyMap<String, CompletionSyncState>(), (awaitItem() as HomeUiState.Success).completionSyncStates)
            }
        }

    @Test
    fun `an IOException is a network error, anything else is not`() =
        runTest {
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returnsMany
                listOf(
                    flow { throw java.io.IOException("timeout") },
                    flow { throw IllegalStateException("boom") },
                )
            val viewModel = HomeViewModel(repository, fakeCompletionRepository(), fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as HomeUiState.Error).isNetworkError)
                viewModel.retry()
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(false, (awaitItem() as HomeUiState.Error).isNetworkError)
            }
        }

    @Test
    fun `retry cancels the previous collector so an old sync state cannot overwrite the new one`() =
        runTest {
            val oldStates = MutableStateFlow(mapOf("WO-1" to CompletionSyncState.PENDING))
            val newStates = MutableStateFlow(mapOf("WO-1" to CompletionSyncState.FAILED))
            val completion = mockk<WorkOrderCompletionRepository>()
            every { completion.observeCompletionSyncStates() } returnsMany listOf(oldStates, newStates)
            val repository = mockk<HomeRepository>()
            every { repository.observeHomeData() } returns flowOf(homeData(summaryItem("WO-1", "repair")))
            val viewModel = HomeViewModel(repository, completion, fakeAuthRepository())

            viewModel.uiState.test {
                assertEquals(HomeUiState.Loading, awaitItem())
                awaitItem()
                viewModel.retry()
                assertEquals(HomeUiState.Loading, awaitItem())
                assertEquals(
                    mapOf("WO-1" to CompletionSyncState.FAILED),
                    (awaitItem() as HomeUiState.Success).completionSyncStates,
                )
                oldStates.value = mapOf("WO-1" to CompletionSyncState.CONFLICT)
                expectNoEvents()
            }
        }
```

Imports: `com.luxmap.feature.workorder.data.CompletionSyncState`, `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.MutableStateFlow`.

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.ui.HomeViewModelTest"`
Expected: compilation FAIL (`completionSyncStates`, `isNetworkError`).

- [ ] **Step 7: Implement UiState + ViewModel**

`HomeUiState.kt`:

```kotlin
package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.workorder.data.CompletionSyncState
import java.time.Instant

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Success(
        val data: HomeData,
        val isStale: Boolean = false,
        val lastSyncedAt: Instant? = null,
        val completionSyncStates: Map<String, CompletionSyncState> = emptyMap(),
    ) : HomeUiState

    data object Empty : HomeUiState

    data class Error(val message: String, val isNetworkError: Boolean = false) : HomeUiState
}
```

`HomeViewModel.load()` — use `completionRepository.observeCompletionSyncStates()` in the `combine`, name the pair `(data, syncStates)`, set `completionSyncStates = syncStates`, and the catch becomes:

```kotlin
                        .catch { e ->
                            _uiState.value =
                                HomeUiState.Error(
                                    message = e.message ?: "Không tải được dữ liệu",
                                    isNetworkError = e is IOException,
                                )
                        }
```

Add `import java.io.IOException`.

`HomeScreen.kt`: `locallyCompletedWorkOrderIds = uiState.locallyCompletedWorkOrderIds` → pass `uiState.completionSyncStates`; parameter type becomes `Map<String, CompletionSyncState>`; `isLocallyCompleted = item.workOrderId in completionSyncStates` (temporary until Task 4 replaces the badge).

- [ ] **Step 8: Run tests**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.*" --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest" --tests "com.luxmap.feature.survey.ui.hub.SurveyHubViewModelTest" --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderDetailViewModelTest" compileLocalDebugAndroidTestKotlin ktlintCheck`
Expected: all PASS (Survey Hub and F09 tests confirm `observeCompletedWorkOrderIds()` callers are untouched).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/data app/src/main/java/com/luxmap/feature/home app/src/test/java/com/luxmap/feature/workorder/data app/src/test/java/com/luxmap/feature/home
git commit -m "feat(home): read real completion sync states and classify network errors"
```

Stop and summarize for the user.

---

### Task 3: Top bar with the bell, NavGraph hides the floating bell on Home

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `LuxTabTopBar(title, unreadNotificationCount, onNotificationsClick, modifier, subtitle)`.
- Produces:
  - `HomeRoute(onOpenWorkOrder: (String) -> Unit, unreadNotificationCount: Int, onOpenNotifications: () -> Unit, modifier, viewModel)`
  - `HomeScreen(uiState, username, onOpenWorkOrder, onRetry, unreadNotificationCount: Int, onOpenNotifications: () -> Unit, modifier, snackbarHostState)`
  - `internal fun homeGreeting(username: String?, today: LocalDate): String`

- [ ] **Step 1: Write the failing UI tests**

Rewrite `HomeScreenTest.kt` with a shared setter (keep the existing 4 tests but route them through it):

```kotlin
    private fun setContent(
        uiState: HomeUiState,
        username: String? = "kysu1",
        unread: Int = 0,
        onOpenNotifications: () -> Unit = {},
        onOpenWorkOrder: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            LuxMapTheme {
                HomeScreen(
                    uiState = uiState,
                    username = username,
                    onOpenWorkOrder = onOpenWorkOrder,
                    onRetry = onRetry,
                    unreadNotificationCount = unread,
                    onOpenNotifications = onOpenNotifications,
                )
            }
        }
    }

    @Test
    fun the_top_bar_is_the_same_in_loading_and_has_the_greeting() {
        setContent(uiState = HomeUiState.Loading)
        composeTestRule.onNodeWithText("Việc hôm nay").assertExists()
        composeTestRule.onNodeWithText("Xin chào, kysu1", substring = true).assertExists()
    }

    @Test
    fun the_bell_reads_its_unread_count_and_opens_notifications() {
        var opened = 0
        setContent(uiState = HomeUiState.Loading, unread = 3, onOpenNotifications = { opened++ })
        composeTestRule.onNodeWithContentDescription("Thông báo, 3 chưa đọc").performClick()
        assertEquals(1, opened)
    }
```

Add a unit test file `app/src/test/java/com/luxmap/feature/home/ui/HomeGreetingTest.kt`:

```kotlin
package com.luxmap.feature.home.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeGreetingTest {
    @Test
    fun `greeting has the name and the capitalized vietnamese date`() {
        assertEquals("Xin chào, kysu1 · Thứ Hai, 16/11/2026", homeGreeting("kysu1", LocalDate.parse("2026-11-16")))
    }

    @Test
    fun `a blank name is left out`() {
        assertEquals("Xin chào · Thứ Hai, 16/11/2026", homeGreeting(" ", LocalDate.parse("2026-11-16")))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.ui.HomeGreetingTest"`
Expected: compilation FAIL (`homeGreeting`).

- [ ] **Step 3: Implement the frame**

In `HomeScreen.kt`:
- Add `homeGreeting`:

```kotlin
private val VI = Locale.forLanguageTag("vi")

internal fun homeGreeting(
    username: String?,
    today: LocalDate,
): String {
    val date = today.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", VI)).replaceFirstChar { it.titlecase(VI) }
    val name = username?.takeIf { it.isNotBlank() }
    return if (name != null) "Xin chào, $name · $date" else "Xin chào · $date"
}
```

- `HomeRoute` and `HomeScreen` gain `unreadNotificationCount: Int` and `onOpenNotifications: () -> Unit` (placed right after `onRetry`), `HomeRoute` passes them through.
- `HomeScreen`'s `Scaffold` gets `topBar`:

```kotlin
        topBar = {
            val today = remember { LocalDate.now() }
            LuxTabTopBar(
                title = "Việc hôm nay",
                subtitle = homeGreeting(username, today),
                unreadNotificationCount = unreadNotificationCount,
                onNotificationsClick = onOpenNotifications,
            )
        },
```

- Body becomes `Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) { when (uiState) { … } }` — delete `HomeHeader`, the `offset(-Spacing.xl)`/`clip`/`background` wrapper and the `BrandHeroGradient`, `DisplayTextStyle`, `statusBarsPadding`, `offset` imports.
- Keep `contentWindowInsets = WindowInsets(0, 0, 0, 0)`; `LuxTabTopBar` (M3 `TopAppBar`) applies the status-bar inset itself.

`NavGraph.kt`:
- Bell bar visibility: `visible = showBottomBar && currentRoute != Routes.Map.route && currentRoute != Routes.Home.route`.
- `onDarkHero = currentRoute == Routes.Survey.route || currentRoute == Routes.Profile.route`.
- Home destination:

```kotlin
            composable(Routes.Home.route) {
                HomeRoute(
                    onOpenWorkOrder = { workOrderId ->
                        navController.navigate(Routes.WorkOrderDetail.createRoute(workOrderId))
                    },
                    unreadNotificationCount = unreadNotificationCount,
                    onOpenNotifications = { navController.navigate(Routes.Notification.route) },
                )
            }
```

- [ ] **Step 4: Run unit + device tests**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.ui.HomeGreetingTest" ktlintCheck`
Run: `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.home.ui.HomeScreenTest"`
Expected: PASS. If the Vietnamese weekday renders differently on the JVM (e.g. `thứ hai`), fix the expected string in the test to the JVM's `EEEE` output with the first letter capitalized — do not change the format.

- [ ] **Step 5: Manual check on device**

Install and open Home: bell sits inside the top bar, no second floating bell; Khảo sát and Cá nhân still show their floating bell.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/home app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/test/java/com/luxmap/feature/home app/src/androidTest/java/com/luxmap/feature/home
git commit -m "style(home): replace the gradient hero with a tab top bar that owns the bell"
```

Stop and summarize for the user.

---

### Task 4: Stats row, segmented selector and the new task card

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/home/ui/HomeTaskCard.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt`
- Create: `app/src/test/java/com/luxmap/feature/home/ui/HomeTaskCardLogicTest.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `HomeTaskTab`, `HomeSummary`, `CompletionSyncState`, `LuxCard`, `StatusBadge`, `LuxSegmented`/`LuxSegment`, `dueDateLabel`, `DateFormatUtils.formatPlainDate`.
- Produces:
  - `sealed interface HomeTaskBadge { data class Sync(val state: CompletionSyncState); data object Overdue; data class Status(val woStatus: String) }`
  - `fun homeTaskBadge(item: WorkOrderSummaryItem, syncState: CompletionSyncState?, today: LocalDate): HomeTaskBadge`
  - `data class HomeDateLine(val scheduled: String?, val due: String?, val isOverdue: Boolean, val isDueToday: Boolean)`
  - `fun homeDateLine(item: WorkOrderSummaryItem, today: LocalDate): HomeDateLine`
  - `@Composable fun HomeTaskCard(item: WorkOrderSummaryItem, syncState: CompletionSyncState?, today: LocalDate, onClick: () -> Unit)`

- [ ] **Step 1: Write the failing logic tests**

`HomeTaskCardLogicTest.kt`:

```kotlin
package com.luxmap.feature.home.ui

import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-16")

private fun item(
    due: String? = null,
    scheduled: String? = null,
    status: String = "assigned",
) = WorkOrderSummaryItem(
    workOrderId = "WO-1",
    woStatus = status,
    dueDate = due,
    priorityScore = null,
    taskKind = "repair",
    communeId = "COM-001",
    title = "Sửa đèn",
    scheduledDate = scheduled,
    faultCount = 0,
)

class HomeTaskCardLogicTest {
    @Test
    fun `a sync state wins over overdue`() {
        assertEquals(
            HomeTaskBadge.Sync(CompletionSyncState.FAILED),
            homeTaskBadge(item(due = "2026-11-01"), CompletionSyncState.FAILED, TODAY),
        )
    }

    @Test
    fun `overdue wins over the work order status`() {
        assertEquals(HomeTaskBadge.Overdue, homeTaskBadge(item(due = "2026-11-15"), null, TODAY))
    }

    @Test
    fun `no sync state and not overdue shows the wo_status`() {
        assertEquals(HomeTaskBadge.Status("in_progress"), homeTaskBadge(item(status = "in_progress"), null, TODAY))
    }

    @Test
    fun `both dates present`() {
        val line = homeDateLine(item(due = "2026-11-20", scheduled = "2026-11-17"), TODAY)
        assertEquals("Làm: 17/11/2026", line.scheduled)
        assertEquals("Hạn 20/11/2026", line.due)
        assertFalse(line.isOverdue)
    }

    @Test
    fun `due today is flagged`() {
        assertTrue(homeDateLine(item(due = "2026-11-16"), TODAY).isDueToday)
    }

    @Test
    fun `missing or malformed dates are hidden, not shown as placeholders`() {
        val line = homeDateLine(item(due = "2026-13-40", scheduled = ""), TODAY)
        assertNull(line.scheduled)
        assertNull(line.due)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.ui.HomeTaskCardLogicTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Implement `HomeTaskCard.kt`**

```kotlin
package com.luxmap.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import java.time.LocalDate
import java.time.format.DateTimeParseException

sealed interface HomeTaskBadge {
    data class Sync(val state: CompletionSyncState) : HomeTaskBadge

    data object Overdue : HomeTaskBadge

    data class Status(val woStatus: String) : HomeTaskBadge
}

fun homeTaskBadge(
    item: WorkOrderSummaryItem,
    syncState: CompletionSyncState?,
    today: LocalDate,
): HomeTaskBadge =
    when {
        syncState != null -> HomeTaskBadge.Sync(syncState)
        dueDateLabel(item.dueDate, today).isOverdue -> HomeTaskBadge.Overdue
        else -> HomeTaskBadge.Status(item.woStatus)
    }

data class HomeDateLine(
    val scheduled: String?,
    val due: String?,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
)

private fun String?.isValidDate(): Boolean =
    !isNullOrBlank() &&
        try {
            LocalDate.parse(this)
            true
        } catch (e: DateTimeParseException) {
            false
        }

fun homeDateLine(
    item: WorkOrderSummaryItem,
    today: LocalDate,
): HomeDateLine {
    val scheduled = item.scheduledDate.takeIf { it.isValidDate() }?.let { "Làm: ${DateFormatUtils.formatPlainDate(it)}" }
    if (!item.dueDate.isValidDate()) return HomeDateLine(scheduled, null, isOverdue = false, isDueToday = false)
    val due = dueDateLabel(item.dueDate, today)
    return HomeDateLine(scheduled, due.text, isOverdue = due.isOverdue, isDueToday = due.isToday)
}

private val SYNC_STATUS =
    mapOf(
        CompletionSyncState.PENDING to SyncStatus.QUEUED_ONLINE,
        CompletionSyncState.FAILED to SyncStatus.FAILED,
        CompletionSyncState.CONFLICT to SyncStatus.CONFLICT,
    )

@Composable
private fun HomeTaskBadgeView(badge: HomeTaskBadge) {
    when (badge) {
        is HomeTaskBadge.Sync -> {
            val status = SYNC_STATUS.getValue(badge.state)
            val text = if (badge.state == CompletionSyncState.PENDING) "Đã nộp · chờ đồng bộ" else status.label()
            StatusBadge(text = text, colors = status.badgeColors(), icon = status.icon())
        }
        HomeTaskBadge.Overdue ->
            StatusBadge(
                text = "Quá hạn",
                colors = BadgeColors(MaterialTheme.colorScheme.errorContainer, LuxTheme.colors.dueOverdue),
                icon = Icons.Filled.EventBusy,
            )
        is HomeTaskBadge.Status -> {
            val status = workOrderStatusFromWire(badge.woStatus)
            StatusBadge(text = status.label(), colors = status.badgeColors(), icon = status.icon())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeTaskCard(
    item: WorkOrderSummaryItem,
    syncState: CompletionSyncState?,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val kind = workOrderTaskKindFromWire(item.taskKind)
    val dates = homeDateLine(item, today)
    val dueColor: Color =
        when {
            dates.isOverdue -> LuxTheme.colors.dueOverdue
            dates.isDueToday -> LuxTheme.colors.dueToday
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    LuxCard(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Mở lệnh ${item.workOrderId}" },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(
                        imageVector = kind.icon(),
                        contentDescription = null,
                        tint = kind.badgeColors().text,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "${kind.label()} · ${item.workOrderId}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HomeTaskBadgeView(homeTaskBadge(item, syncState, today))
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (item.faultCount > 0) "${item.faultCount} sự cố · ${item.communeId}" else item.communeId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget).padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f).padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (dates.scheduled == null && dates.due == null) {
                    DateText(Icons.Filled.EventAvailable, "Chưa có lịch", MaterialTheme.colorScheme.onSurfaceVariant)
                }
                dates.scheduled?.let { DateText(Icons.Filled.EventAvailable, it, MaterialTheme.colorScheme.onSurfaceVariant) }
                dates.due?.let {
                    DateText(if (dates.isOverdue) Icons.Filled.EventBusy else Icons.Filled.Schedule, it, dueColor)
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateText(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
```

Replace the fully-qualified `PaddingValues`/`ImageVector` with imports when writing the file (ktlint will flag them otherwise).

- [ ] **Step 4: Rebuild stats row and selector in `HomeScreen.kt`**

- `SummaryStatsRow(summary: HomeSummary)`: three cells `Tổng nhiệm vụ` / `Quá hạn` / `Đang làm`; value `titleMedium` with `Modifier.widthIn(min = 24.dp)`, label `bodyMedium` `onSurfaceVariant`; overdue value color `LuxTheme.colors.dueOverdue` when > 0, else `onSurface`; dividers `outlineVariant`. Wrap the row in `FlowRow` so it wraps at large font instead of clipping.
- Replace `TaskFilterTabs` with:

```kotlin
            LuxSegmented(
                segments = HomeTaskTab.entries.map { LuxSegment(it, "${it.label} · ${tasks.countFor(it)}") },
                selected = selectedTab,
                onSelect = { selectedTab = it },
            )
```

- Replace `TaskRow(...)` in the list with `HomeTaskCard(item = item, syncState = completionSyncStates[item.workOrderId], today = today, onClick = { onOpenWorkOrder(item.workOrderId) })`.
- Delete `TaskFilterTabs`, `TaskRow`, `TaskStatusBadge`, `SummaryStatCell`/`SummaryStatDivider` (if rewritten inline) and now-unused imports.

- [ ] **Step 5: Add UI tests**

Add these top-level helpers to `HomeScreenTest.kt` (replace the inline `HomeData` in `successState_showsRepairWorkOrdersInDefaultTab` with `successWith(item("WO-2031"), item("WO-2019"))`):

```kotlin
private fun item(
    id: String,
    kind: String = "repair",
    status: String = "assigned",
    title: String = "Lệnh $id",
    faultCount: Int = 0,
    scheduled: String? = null,
    due: String? = null,
) = WorkOrderSummaryItem(
    workOrderId = id,
    woStatus = status,
    dueDate = due,
    priorityScore = null,
    taskKind = kind,
    communeId = "COM-001",
    title = title,
    scheduledDate = scheduled,
    faultCount = faultCount,
)

private fun successWith(
    vararg items: WorkOrderSummaryItem,
    states: Map<String, CompletionSyncState> = emptyMap(),
) = HomeUiState.Success(
    data =
        HomeData(
            metrics = HomeMetrics(assignedCount = items.size, inProgressCount = 0, overdueCount = 0, plannedSweepCount = null),
            clusters = listOf(WorkOrderCluster(clusterLabel = "COM-001", items = items.toList())),
        ),
    completionSyncStates = states,
)
```

Imports: `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.SemanticsProperties`, `androidx.compose.ui.test.SemanticsMatcher`, `androidx.compose.ui.test.assert`, `androidx.compose.ui.test.assertIsNotSelected`, `androidx.compose.ui.test.assertIsSelected`, `androidx.compose.ui.test.onNodeWithContentDescription`, `androidx.compose.ui.test.performClick`, `com.luxmap.feature.workorder.data.CompletionSyncState`, `org.junit.Assert.assertEquals`.

Tests:

```kotlin
    @Test
    fun a_card_shows_title_fault_count_schedule_and_due_and_opens_the_order() {
        var opened: String? = null
        setContent(
            uiState = successWith(item("WO-7", title = "Sửa đèn chợ", faultCount = 3, scheduled = "2026-11-17", due = "2030-01-01")),
            onOpenWorkOrder = { opened = it },
        )
        composeTestRule.onNodeWithText("Sửa đèn chợ").assertExists()
        composeTestRule.onNodeWithText("3 sự cố · COM-001").assertExists()
        composeTestRule.onNodeWithText("Làm: 17/11/2026").assertExists()
        composeTestRule.onNodeWithText("Hạn 01/01/2030").assertExists()
        composeTestRule.onNodeWithContentDescription("Mở lệnh WO-7").performClick()
        assertEquals("WO-7", opened)
    }

    @Test
    fun a_card_without_dates_says_no_schedule() {
        setContent(uiState = successWith(item("WO-8")))
        composeTestRule.onNodeWithText("Chưa có lịch").assertExists()
    }

    @Test
    fun sync_badges_follow_the_real_sync_state() {
        setContent(
            uiState =
                successWith(
                    item("WO-P"),
                    item("WO-F"),
                    item("WO-C"),
                    item("WO-S", status = "in_progress"),
                    states =
                        mapOf(
                            "WO-P" to CompletionSyncState.PENDING,
                            "WO-F" to CompletionSyncState.FAILED,
                            "WO-C" to CompletionSyncState.CONFLICT,
                        ),
                ),
        )
        composeTestRule.onNodeWithText("Đã nộp · chờ đồng bộ").assertExists()
        composeTestRule.onNodeWithText("Đồng bộ lỗi").assertExists()
        composeTestRule.onNodeWithText("Xung đột").assertExists()
        composeTestRule.onNodeWithText("Đang thực hiện").assertExists()
    }

    @Test
    fun the_kind_selector_is_a_radio_group_with_counts() {
        setContent(uiState = successWith(item("R1"), item("I1", kind = "inspection")))
        composeTestRule
            .onNodeWithText("Sửa chữa · 1")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        composeTestRule.onNodeWithText("Kiểm tra · 1").assertIsNotSelected().performClick().assertIsSelected()
    }
```

Check the `WorkOrderStatus.IN_PROGRESS.label()` string in `core/theme/Color.kt` before running; use its exact text in the last assertion of `sync_badges_follow_the_real_sync_state`.

- [ ] **Step 6: Run tests**

Run: `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.home.*" ktlintCheck`
Run: `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=com.luxmap.feature.home.ui.HomeScreenTest"`
Expected: PASS.

- [ ] **Step 7: Commit** (if the diff exceeds 400 lines, commit `HomeTaskCard.kt` + its unit test first, then the screen + UI tests)

```bash
git add app/src/main/java/com/luxmap/feature/home app/src/test/java/com/luxmap/feature/home app/src/androidTest/java/com/luxmap/feature/home
git commit -m "style(home): add the vnext task card, stats row and kind selector"
```

Stop and summarize for the user.

---

### Task 5: Four data states

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `LuxLoadingRegion(description, modifier, content)`, `LuxSkeleton(modifier, shape)`, `LuxEmptyState(title, message, modifier, icon, actionLabel, onAction)`, `LuxErrorState(onRetry, modifier, kind, title, message, …)`, `LuxErrorKind.NETWORK/GENERIC`, `LuxBanner(message, tone, …)`, `LuxBannerTone.INFO`.

- [ ] **Step 1: Write the failing UI tests** (replace the old `emptyState_showsEmptyMessage` / `errorState_showsErrorMessage`)

```kotlin
    @Test
    fun empty_offers_refresh() {
        var retries = 0
        setContent(uiState = HomeUiState.Empty, onRetry = { retries++ })
        composeTestRule.onNodeWithText("Chưa có việc được giao").assertExists()
        composeTestRule.onNodeWithText("Làm mới").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_network_error_uses_the_home_copy_and_retries() {
        var retries = 0
        setContent(uiState = HomeUiState.Error("java.net.SocketTimeoutException: timeout", isNetworkError = true), onRetry = { retries++ })
        composeTestRule.onNodeWithText("Không tải được danh sách lệnh").assertExists()
        composeTestRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeTestRule.onNodeWithText("SocketTimeoutException", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("đã lưu", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_generic_error_uses_the_home_copy() {
        setContent(uiState = HomeUiState.Error("boom"))
        composeTestRule.onNodeWithText("Vui lòng thử lại sau ít phút.").assertExists()
        composeTestRule.onNodeWithText("Thử lại").assertExists()
    }

    @Test
    fun loading_is_announced_as_one_region() {
        setContent(uiState = HomeUiState.Loading)
        composeTestRule.onNodeWithContentDescription("Đang tải việc được giao").assertExists()
    }

    @Test
    fun an_empty_tab_shows_its_own_message() {
        setContent(uiState = successWith(item("R1")))
        composeTestRule.onNodeWithText("Kiểm tra · 0").performClick()
        composeTestRule.onNodeWithText("Không có nhiệm vụ kiểm tra").assertExists()
    }
```

- [ ] **Step 2: Run to verify they fail**

Run the HomeScreenTest device command. Expected: the 4 new state tests FAIL (old copy).

- [ ] **Step 3: Implement**

In `HomeScreen.kt`:

```kotlin
                    is HomeUiState.Loading -> HomeLoading()
                    is HomeUiState.Empty ->
                        LuxEmptyState(
                            title = "Chưa có việc được giao",
                            message = "Khi Kỹ sư bảo trì giao lệnh sửa chữa hoặc kiểm tra, lệnh sẽ hiện ở đây.",
                            icon = Icons.Filled.EventAvailable,
                            actionLabel = "Làm mới",
                            onAction = onRetry,
                        )
                    is HomeUiState.Error ->
                        LuxErrorState(
                            onRetry = onRetry,
                            kind = if (uiState.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                            title = "Không tải được danh sách lệnh",
                            message =
                                if (uiState.isNetworkError) {
                                    "Kiểm tra kết nối rồi thử lại."
                                } else {
                                    "Vui lòng thử lại sau ít phút."
                                },
                        )
```

```kotlin
@Composable
private fun HomeLoading() {
    LuxLoadingRegion(description = "Đang tải việc được giao", modifier = Modifier.padding(Spacing.lg)) {
        LuxSkeleton(modifier = Modifier.fillMaxWidth().height(24.dp))
        LuxSkeleton(modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.small)
        repeat(3) { LuxSkeleton(modifier = Modifier.fillMaxWidth().height(120.dp), shape = MaterialTheme.shapes.medium) }
    }
}
```

- Tab empty: replace `TabEmptyState` body with `LuxEmptyState(title = …, message = …, icon = tab kind icon)` using the copy in Global Constraints, no action.
- Stale banner: `LuxBanner(message = "Đang xem dữ liệu đã lưu, cập nhật lúc ${DateFormatUtils.formatInstant(lastSyncedAt)}", tone = LuxBannerTone.INFO)`.
- Delete `LoadingSkeletonState`, `TaskRowSkeleton`, `SkeletonBar`, `EmptyState`, `ErrorState`, `EmptyContent` and imports of `MessageState`, `InlineBanner`, `InlineBannerTone`, `OutlinedButton`, `BorderStroke`, `SyncStatus`, `AssetCondition`, legacy `*TextStyle`.

- [ ] **Step 4: Run tests**

Run: `./gradlew ktlintCheck` and the HomeScreenTest device command.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/home app/src/androidTest/java/com/luxmap/feature/home
git commit -m "style(home): move the four data states to vnext components with home-specific copy"
```

Stop and summarize for the user.

---

### Task 6: Large-font test, full device run, cleanup

**Files:**
- Create: `app/src/androidTest/java/com/luxmap/feature/home/ui/HomeLargeFontTest.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (remove leftovers only)
- Modify: `docs/design-system/screens/F02_HOME.md` (status line)

- [ ] **Step 1: Write the large-font test**

```kotlin
package com.luxmap.feature.home.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.ui.components.assertTouchTargetIsAtLeast
import com.luxmap.feature.home.data.HomeData
import com.luxmap.feature.home.data.HomeMetrics
import com.luxmap.feature.home.data.WorkOrderCluster
import com.luxmap.feature.home.data.WorkOrderSummaryItem
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Rule
import org.junit.Test

class HomeLargeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show() {
        val item =
            WorkOrderSummaryItem(
                workOrderId = "WO-1042",
                woStatus = "assigned",
                dueDate = "2020-01-01",
                priorityScore = null,
                taskKind = "repair",
                communeId = "COM-001",
                title = "Sửa đèn tuyến đường liên xã Phước Hiệp đoạn qua chợ",
                scheduledDate = "2020-01-01",
                faultCount = 3,
            )
        val data =
            HomeData(
                metrics = HomeMetrics(1, 0, 1, null),
                clusters = listOf(WorkOrderCluster("COM-001", listOf(item))),
            )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                LuxMapTheme {
                    Box(modifier = Modifier.width(360.dp)) {
                        HomeScreen(
                            uiState =
                                HomeUiState.Success(
                                    data = data,
                                    completionSyncStates = mapOf("WO-1042" to CompletionSyncState.PENDING),
                                ),
                            username = "kysu1",
                            onOpenWorkOrder = {},
                            onRetry = {},
                            unreadNotificationCount = 0,
                            onOpenNotifications = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun selector_and_card_keep_48dp_targets_at_large_font_on_a_narrow_screen() {
        show()
        composeRule.onNodeWithText("Sửa chữa · 1").assertTouchTargetIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Mở lệnh WO-1042").assertTouchTargetIsAtLeast(48.dp)
    }

    @Test
    fun code_sync_badge_and_due_date_stay_fully_visible_at_large_font() {
        show()
        composeRule.onNodeWithText("Sửa chữa · WO-1042").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Đã nộp · chờ đồng bộ").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("hạn 01/01/2020", substring = true).performScrollTo().assertIsDisplayed()
    }
}
```

Note: the card has `contentDescription = "Mở lệnh …"` merged on the clickable `Card`; if `onNodeWithText` cannot find the inner texts because of merging, use `onNodeWithText(..., useUnmergedTree = true)`.

- [ ] **Step 2: Run on device**

Run: `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.package=com.luxmap.feature.home.ui"`
Expected: all HomeScreenTest + HomeLargeFontTest PASS.

- [ ] **Step 3: Cleanup**

Grep `HomeScreen.kt` for any remaining legacy symbol (`TextStyle`, `BrandHeroGradient`, `Radius.`, `Color.White`, `MessageState`, `InlineBanner`) and remove leftovers. Confirm `NotificationBellBar` still compiles for Survey/Profile. Run: `./gradlew ktlintCheck testLocalDebugUnitTest --tests "com.luxmap.feature.home.*" --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`.

- [ ] **Step 4: Manual check (owed to user, record result)**

On SM-A075F: Light and Night Field; system font at 200% — mã lệnh, badge đồng bộ, ngày hạn fully visible; badge wraps to its own row; the two dates stack.

- [ ] **Step 5: Update spec status and commit**

In `docs/design-system/screens/F02_HOME.md` change the status line to `**ĐÃ CODE XONG 2026-11-16** trên nhánh style/redesign-home-vnext.`

```bash
git add app/src/androidTest/java/com/luxmap/feature/home app/src/main/java/com/luxmap/feature/home docs/design-system/screens/F02_HOME.md
git commit -m "test(home): cover the home screen at large font and remove legacy styling"
```

Stop and summarize for the user; offer push + PR into `dev`.
