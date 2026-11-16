# Hub Khảo sát — Redesign vNext Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the Survey Hub to Design System vNext with status-grouped work, schedule-window locks like F09, a real "Chờ đồng bộ" group, and the four data states — without API/Room/sync changes.

**Architecture:** Pure grouping + start-gate logic in `SurveyHubList.kt` (unit-tested). `SurveyHubViewModel` adds real sync states and `isNetworkError`. The screen becomes `LuxTabTopBar` + one keyed `LazyColumn` of grouped `LuxCard`s (cards in `SurveyHubCards.kt`); the floating bell is hidden on Survey like Home.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01, M3), Hilt, Coroutines/Flow, JUnit + MockK + Turbine, Compose UI Test.

**Spec:** `docs/design-system/screens/SURVEY_HUB.md` (approved 2026-11-17, H1–H4).

## Global Constraints

- Branch `style/redesign-survey-hub-vnext` (from `dev`, has the spec commit). Never commit to `main`.
- Commits ≤ 400 changed lines, `type(survey): description`, English, imperative, lowercase. **No Claude attribution / Co-Authored-By.** No new code comments.
- No API, Room schema, or sync change. Reuse `workOrderScheduleWindow` (F09) and `workOrderScheduleLabel` (`feature/workorder/ui/detail/WorkOrderSummaryText.kt`); reuse `WorkOrderCompletionRepository.observeCompletionSyncStates()`.
- Only theme tokens in `feature/`; no primitives (`Navy700`…), no `Color(0x…)`, no legacy `*TextStyle`, no `colorScheme.copy(...)` override.
- Touch targets ≥ 48dp; margin 16dp.
- Copy (exact):
  - Top bar title `Khảo sát`, subtitle `{Thứ…, dd/MM/yyyy}` (capitalised weekday).
  - Groups: `Đang làm`, `Chưa bắt đầu`, `Chờ đồng bộ` (header shows `{title} · {n}`).
  - Kind labels: `Tuyến khảo sát`, `Đo GPS cột`.
  - Schedule row label `Lịch khảo sát` / `Lịch đo GPS`; value `dd/MM/yyyy`, `dd/MM/yyyy · Hôm nay`, or `Chưa có lịch`; passed-schedule line `Đã qua lịch`.
  - Buttons `Bắt đầu`, `Đang bắt đầu…`, `Yêu cầu hủy`, `Chụp ảnh/video từng cột`; cancelled line `Đã gửi yêu cầu hủy · chờ duyệt`.
  - Lock hints `Mở vào dd/MM/yyyy`, `Đã khoá do quá hạn`.
  - Sync badges `Chờ đồng bộ` (QUEUED_ONLINE), `Đồng bộ lỗi` (FAILED), `Xung đột` (CONFLICT).
  - Empty: `Chưa có việc khảo sát được giao` / `Khi Kỹ sư bảo trì giao tuyến khảo sát hoặc lệnh đo GPS cột, lệnh sẽ hiện ở đây.` + `Làm mới`.
  - Error title `Không tải được việc khảo sát`; message network `Kiểm tra kết nối rồi thử lại.`, generic `Vui lòng thử lại sau ít phút.`; `Thử lại`.
  - Stale banner `Đang xem danh sách đã lưu trên máy, có thể chưa phải bản mới nhất`.
- Unit: `./gradlew testLocalDebugUnitTest --tests "<fqcn>"`. Device (SM-A075F): `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`. `./gradlew ktlintCheck` each task. Stop after each task for the user.

## Review Focus

1. **A work order appears in both survey routes and pole GPS lists** (same id): must render once (dedupe by id, survey wins). Unit test in Task 1.
2. **`scheduled_date` malformed or null**: start/cancel not locked (no window), schedule row `Chưa có lịch`, no crash. Unit test in Task 1.
3. **Highlighted id not in any visible group** (synced/hidden, or unknown): no scroll, no crash. UI test in Task 4.
4. **Sync-state flow or pole-GPS flow errors**: screen shows Error with Home-style copy, never raw text. ViewModel test in Task 2.
5. **Retry / "Làm mới" from Empty**: must reload (old `onRetry` only worked from Error). ViewModel test in Task 2.

---

### Task 1: Pure grouping and start gate

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubList.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/ui/hub/SurveyHubListTest.kt`

**Interfaces — Produces:**
- `enum class HubSection(val title: String) { IN_PROGRESS("Đang làm"), TODO("Chưa bắt đầu"), PENDING_SYNC("Chờ đồng bộ") }`
- `enum class HubItemKind { SURVEY_ROUTE, POLE_GPS }`
- `data class HubItem(val order: AssignedSurveyRoute, val kind: HubItemKind, val syncState: CompletionSyncState?)`
- `data class HubGroup(val section: HubSection, val items: List<HubItem>)`
- `fun surveyHubGroups(routes: List<AssignedSurveyRoute>, poleGpsOrders: List<AssignedSurveyRoute>, syncStates: Map<String, CompletionSyncState>, completedIds: Set<String>): List<HubGroup>`
- `sealed interface StartGate { data object Open; data class NotYet(val opensOn: LocalDate); data object Overdue }`
- `fun startGate(scheduledDate: String?, today: LocalDate): StartGate`
- `fun StartGate.hint(): String?`
- `fun scheduleValue(scheduledDate: String?, today: LocalDate): String`

- [ ] **Step 1: Failing tests** — `SurveyHubListTest.kt`:

```kotlin
package com.luxmap.feature.survey.ui.hub

import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.workorder.data.CompletionSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-17")

private fun order(
    id: String,
    status: String = "assigned",
    scheduled: String? = null,
) = AssignedSurveyRoute(
    workOrderId = id,
    title = "Lệnh $id",
    communeId = "COM-001",
    woStatus = status,
    dueDate = null,
    scheduledDate = scheduled,
)

class SurveyHubListTest {
    @Test
    fun `groups by status mixing both kinds, routes before pole gps, empty groups dropped`() {
        val groups =
            surveyHubGroups(
                routes = listOf(order("R1", "in_progress"), order("R2")),
                poleGpsOrders = listOf(order("G1", "in_progress")),
                syncStates = emptyMap(),
                completedIds = emptySet(),
            )
        assertEquals(listOf(HubSection.IN_PROGRESS, HubSection.TODO), groups.map { it.section })
        assertEquals(listOf("R1", "G1"), groups[0].items.map { it.order.workOrderId })
        assertEquals(listOf(HubItemKind.SURVEY_ROUTE, HubItemKind.POLE_GPS), groups[0].items.map { it.kind })
        assertEquals(listOf("R2"), groups[1].items.map { it.order.workOrderId })
    }

    @Test
    fun `a submitted but unsynced order moves to the pending sync group, a synced one is hidden`() {
        val groups =
            surveyHubGroups(
                routes = listOf(order("R1", "in_progress"), order("R2", "in_progress")),
                poleGpsOrders = emptyList(),
                syncStates = mapOf("R1" to CompletionSyncState.FAILED),
                completedIds = setOf("R1", "R2"),
            )
        assertEquals(listOf(HubSection.PENDING_SYNC), groups.map { it.section })
        assertEquals(CompletionSyncState.FAILED, groups.single().items.single().syncState)
    }

    @Test
    fun `an id present in both lists is shown once as a survey route`() {
        val groups =
            surveyHubGroups(
                routes = listOf(order("X")),
                poleGpsOrders = listOf(order("X")),
                syncStates = emptyMap(),
                completedIds = emptySet(),
            )
        assertEquals(1, groups.single().items.size)
        assertEquals(HubItemKind.SURVEY_ROUTE, groups.single().items.single().kind)
    }

    @Test
    fun `start gate follows the F09 schedule window`() {
        assertEquals(StartGate.NotYet(LocalDate.parse("2026-11-19")), startGate("2026-11-20", TODAY))
        assertEquals(StartGate.Open, startGate("2026-11-18", TODAY))
        assertEquals(StartGate.Open, startGate("2026-11-17", TODAY))
        assertEquals(StartGate.Overdue, startGate("2026-11-16", TODAY))
        assertEquals(StartGate.Open, startGate(null, TODAY))
        assertEquals(StartGate.Open, startGate("not-a-date", TODAY))
    }

    @Test
    fun `gate hints`() {
        assertEquals("Mở vào 19/11/2026", StartGate.NotYet(LocalDate.parse("2026-11-19")).hint())
        assertEquals("Đã khoá do quá hạn", StartGate.Overdue.hint())
        assertNull(StartGate.Open.hint())
    }

    @Test
    fun `schedule value`() {
        assertEquals("17/11/2026 · Hôm nay", scheduleValue("2026-11-17", TODAY))
        assertEquals("20/11/2026", scheduleValue("2026-11-20", TODAY))
        assertEquals("Chưa có lịch", scheduleValue(null, TODAY))
        assertEquals("Chưa có lịch", scheduleValue("bad", TODAY))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (unresolved references).

- [ ] **Step 3: Implement `SurveyHubList.kt`**

```kotlin
package com.luxmap.feature.survey.ui.hub

import com.luxmap.core.common.DateFormatUtils
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleWindow
import java.time.LocalDate
import java.time.format.DateTimeParseException

enum class HubSection(val title: String) {
    IN_PROGRESS("Đang làm"),
    TODO("Chưa bắt đầu"),
    PENDING_SYNC("Chờ đồng bộ"),
}

enum class HubItemKind { SURVEY_ROUTE, POLE_GPS }

data class HubItem(
    val order: AssignedSurveyRoute,
    val kind: HubItemKind,
    val syncState: CompletionSyncState?,
)

data class HubGroup(val section: HubSection, val items: List<HubItem>)

fun surveyHubGroups(
    routes: List<AssignedSurveyRoute>,
    poleGpsOrders: List<AssignedSurveyRoute>,
    syncStates: Map<String, CompletionSyncState>,
    completedIds: Set<String>,
): List<HubGroup> {
    val routeIds = routes.map { it.workOrderId }.toSet()
    val all =
        routes.map { it to HubItemKind.SURVEY_ROUTE } +
            poleGpsOrders.filterNot { it.workOrderId in routeIds }.map { it to HubItemKind.POLE_GPS }
    val bySection = HubSection.entries.associateWith { mutableListOf<HubItem>() }
    for ((order, kind) in all) {
        val sync = syncStates[order.workOrderId]
        val section =
            when {
                sync != null -> HubSection.PENDING_SYNC
                order.workOrderId in completedIds -> null
                order.woStatus == "in_progress" -> HubSection.IN_PROGRESS
                else -> HubSection.TODO
            } ?: continue
        bySection.getValue(section) += HubItem(order, kind, sync)
    }
    return HubSection.entries.mapNotNull { section ->
        bySection.getValue(section).takeIf { it.isNotEmpty() }?.let { HubGroup(section, it) }
    }
}

sealed interface StartGate {
    data object Open : StartGate

    data class NotYet(val opensOn: LocalDate) : StartGate

    data object Overdue : StartGate
}

fun startGate(
    scheduledDate: String?,
    today: LocalDate,
): StartGate {
    val window = workOrderScheduleWindow(scheduledDate, today)
    return when {
        window.canActOnSchedule -> StartGate.Open
        window.isOverdueToComplete -> StartGate.Overdue
        window.unlocksOn != null -> StartGate.NotYet(window.unlocksOn)
        else -> StartGate.Open
    }
}

fun StartGate.hint(): String? =
    when (this) {
        StartGate.Open -> null
        is StartGate.NotYet -> "Mở vào " + DateFormatUtils.formatPlainDate(opensOn.toString())
        StartGate.Overdue -> "Đã khoá do quá hạn"
    }

fun scheduleValue(
    scheduledDate: String?,
    today: LocalDate,
): String {
    val parsed =
        try {
            scheduledDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        } ?: return "Chưa có lịch"
    val text = DateFormatUtils.formatPlainDate(scheduledDate)
    return if (parsed == today) "$text · Hôm nay" else text
}
```

(`workOrderScheduleWindow` lives in `WorkOrderScheduleWindow.kt` and returns `canActOnSchedule = true` for null/unparseable dates.)

- [ ] **Step 4: Run, expect PASS; ktlint.**
- [ ] **Step 5: Commit** `feat(survey): add survey hub grouping and schedule start gate`

---

### Task 2: ViewModel — sync states, network error, retry from Empty

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModel.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/hub/SurveyHubViewModelTest.kt`

**Interfaces — Produces:**
- `SurveyHubUiState.Success` gains `completionSyncStates: Map<String, CompletionSyncState> = emptyMap()`.
- `SurveyHubUiState.Error(message: String, isNetworkError: Boolean = false)`.
- `onRetry()` reloads from `Error` **or** `Empty`.
- Empty iff `surveyHubGroups(...)` is empty.

- [ ] **Step 1: Failing tests** — in the test helper `viewModel(...)` add params `syncStates: Map<String, CompletionSyncState> = emptyMap()`, `poleGpsThrows: Boolean = false`, `routesThrowable: Throwable = IllegalStateException("offline")`; stub `every { completionRepository.observeCompletionSyncStates() } returns flowOf(syncStates)`; make the routes flow throw `routesThrowable` when `repositoryFlowThrows`; make pole flow `flow { throw IllegalStateException("x") }` when `poleGpsThrows`. Add:

```kotlin
    @Test
    fun `carries completion sync states into Success`() =
        runTest {
            viewModel(
                routes = listOf(route.copy(woStatus = "in_progress")),
                syncStates = mapOf("WO-1" to CompletionSyncState.PENDING),
                locallyCompletedWorkOrderIds = setOf("WO-1"),
            ).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                val success = awaitItem() as SurveyHubUiState.Success
                assertEquals(mapOf("WO-1" to CompletionSyncState.PENDING), success.completionSyncStates)
            }
        }

    @Test
    fun `only synced work orders left means Empty`() =
        runTest {
            viewModel(routes = listOf(route), locallyCompletedWorkOrderIds = setOf("WO-1")).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `an IOException is a network error`() =
        runTest {
            viewModel(repositoryFlowThrows = true, routesThrowable = java.io.IOException("timeout")).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as SurveyHubUiState.Error).isNetworkError)
            }
        }

    @Test
    fun `a pole gps flow failure shows Error, not a crash`() =
        runTest {
            viewModel(routes = listOf(route), poleGpsThrows = true).uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(false, (awaitItem() as SurveyHubUiState.Error).isNetworkError)
            }
        }

    @Test
    fun `onRetry reloads from Empty`() =
        runTest {
            val vm = viewModel()
            vm.uiState.test {
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
                vm.onRetry()
                assertEquals(SurveyHubUiState.Loading, awaitItem())
                assertEquals(SurveyHubUiState.Empty, awaitItem())
            }
        }
```

Delete `emits Success with locallyCompletedWorkOrderIds from the completion repository` only if it conflicts (route in completed set is now hidden → Empty); otherwise adapt its route to also be in `syncStates`.

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**

`SurveyHubUiState`: add `val completionSyncStates: Map<String, CompletionSyncState> = emptyMap(),` to `Success`; change `Error` to `data class Error(val message: String, val isNetworkError: Boolean = false)`.

`SurveyHubViewModel.onRetry()`:

```kotlin
        fun onRetry() {
            val current = _uiState.value
            if (current !is SurveyHubUiState.Error && current !is SurveyHubUiState.Empty) return
            _uiState.value = SurveyHubUiState.Loading
            load()
        }
```

`load()`:

```kotlin
        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    combine(
                        repository.observeAssignedRoutes(),
                        repository.observeAssignedPoleGpsOrders(),
                        completionRepository.observeCompletedWorkOrderIds(),
                        completionRepository.observeCompletionSyncStates(),
                    ) { routesResult, poleGpsOrders, completedIds, syncStates ->
                        val groups = surveyHubGroups(routesResult.routes, poleGpsOrders, syncStates, completedIds)
                        if (groups.isEmpty()) {
                            SurveyHubUiState.Empty
                        } else {
                            SurveyHubUiState.Success(
                                assignedRoutes = routesResult.routes,
                                assignedPoleGpsOrders = poleGpsOrders,
                                isStale = routesResult.isStale,
                                cancelledWorkOrderIds = cancelledWorkOrderIds,
                                locallyCompletedWorkOrderIds = completedIds,
                                completionSyncStates = syncStates,
                            )
                        }
                    }.catch { e ->
                        _uiState.value =
                            SurveyHubUiState.Error(
                                message = e.message ?: "Không tải được Khảo sát",
                                isNetworkError = e is IOException,
                            )
                    }.collect { state ->
                        _uiState.value = state
                    }
                }
        }
```

Imports `java.io.IOException`. Note: other `copy(...)` sites in the ViewModel keep working (new field defaults).

- [ ] **Step 4: Run class + `SurveyHubScreenTest` compile (`compileLocalDebugAndroidTestKotlin`), ktlint; expect PASS.**
- [ ] **Step 5: Commit** `feat(survey): read real sync states and network errors in the survey hub`

---

### Task 3: Top bar with bell, NavGraph, remove the pole-location entry and theme override

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt` (`SurveyHubRoute` ~87-145, frame ~148-243, delete `SurveyHubHeader`, `PoleLocationEntryCard`)
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt` (bell bar ~157-170, Survey destination ~234-243)
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreenTest.kt`

**Interfaces — Produces:**
- `SurveyHubRoute(onCapturePolePhotos, onOpenWorkOrderDetail, unreadNotificationCount: Int, onOpenNotifications: () -> Unit, highlightWorkOrderId, modifier, viewModel)` (no `onOpenPoleLocationList`).
- `SurveyHubScreen(state, unreadNotificationCount: Int = 0, onOpenNotifications: () -> Unit = {}, …)` (no `onOpenPoleLocationList`).
- `internal fun surveyHubDate(today: LocalDate): String` → `"Thứ Hai, 17/11/2026"`.

- [ ] **Step 1: Failing tests**
  - Add unit test in `SurveyHubListTest`: `assertEquals("Thứ Hai, 17/11/2026", surveyHubDate(TODAY))`.
  - `SurveyHubScreenTest`: change the `route(...)` fixture's `scheduledDate` to `LocalDate.now().toString()` (old `2026-10-01` would now be locked by H3). Replace `loading_state_shows_hero_header` with:

```kotlin
    @Test
    fun the_top_bar_has_the_title_and_a_bell_that_opens_notifications() {
        var opened = 0
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Loading,
                    unreadNotificationCount = 2,
                    onOpenNotifications = { opened++ },
                )
            }
        }
        composeRule.onNodeWithText("Khảo sát").assertExists()
        composeRule.onNodeWithContentDescription("Thông báo, 2 chưa đọc").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun the_missing_gps_entry_is_gone() {
        composeRule.setContent {
            LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route()))) }
        }
        composeRule.onNodeWithText("Cột thiếu GPS").assertDoesNotExist()
    }
```

Run unit + device class; expect the new ones to FAIL.

- [ ] **Step 2: Implement**
  - `surveyHubDate(today)` = `today.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", vi)).replaceFirstChar { it.titlecase(vi) }`.
  - `SurveyHubRoute`: remove the `LuxMapTheme { MaterialTheme(colorScheme = …copy(primary = Navy700…)) { … } }` wrapper — call `SurveyHubScreen(...)` directly; drop `onOpenPoleLocationList`; add and pass `unreadNotificationCount`, `onOpenNotifications`.
  - `SurveyHubScreen`: replace the `Column { SurveyHubHeader(); Box { when … } }` with a `Scaffold(contentWindowInsets = WindowInsets(0,0,0,0), topBar = { LuxTabTopBar(title = "Khảo sát", subtitle = surveyHubDate(remember { LocalDate.now() }), unreadNotificationCount = unreadNotificationCount, onNotificationsClick = onOpenNotifications) }, containerColor = MaterialTheme.colorScheme.background) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { when (state) { … } } }`; keep the `TopSlideToast` overlay and the cancel sheet logic as is. Delete `SurveyHubHeader`, `PoleLocationEntryCard`, the `item { PoleLocationEntryCard(...) }` line and `onOpenPoleLocationList` params.
  - `NavGraph`: bell bar `visible = showBottomBar && currentRoute != Routes.Map.route && currentRoute != Routes.Home.route && currentRoute != Routes.Survey.route`; `onDarkHero = currentRoute == Routes.Profile.route`; Survey destination: remove `onOpenPoleLocationList = …`, add `unreadNotificationCount = unreadNotificationCount, onOpenNotifications = { navController.navigate(Routes.Notification.route) }`.
  - Verify `currentRoute` for the Survey destination equals `Routes.Survey.route` (route has a query arg `survey?highlightWorkOrderId={…}`); if `currentRoute` is the full pattern string, compare with that exact value — read how `Routes.Survey.route` is defined and how `currentRoute` is computed in `NavGraph.kt` first.
- [ ] **Step 3: Run unit + device class + ktlint; expect PASS** (other existing Hub tests must still pass with the new fixture date).
- [ ] **Step 4: Commit** `style(survey): replace the hub hero with a tab top bar and drop the missing gps entry`

---

### Task 4: Cards, grouped keyed list, highlight

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubCards.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreen.kt` (`SurveyHubContent`, delete `PoleGpsUpdateOrderCard`, `AssignedRouteCard`, `RouteInfoCell`, `SurveyHubSectionHeader`, `scheduledDate*` helpers, `RouteTile*` colors)
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/hub/SurveyHubScreenTest.kt`

**Interfaces — Consumes:** Task 1 (`HubGroup`, `HubItem`, `HubItemKind`, `startGate`, `hint`, `scheduleValue`), Task 2 (`completionSyncStates`), `workOrderScheduleLabel`, `workOrderScheduleWindow`.
**Produces:** `@Composable fun SurveyRouteCard(item: HubItem, today: LocalDate, isStarting: Boolean, isCancelled: Boolean, isHighlighted: Boolean, onStart: () -> Unit, onCapture: () -> Unit, onRequestCancellation: () -> Unit)`, `@Composable fun PoleGpsCard(item: HubItem, today: LocalDate, isHighlighted: Boolean, onOpen: () -> Unit)`, `@Composable fun PendingSyncCard(item: HubItem, today: LocalDate)`.

- [ ] **Step 1: Failing UI tests** (add to `SurveyHubScreenTest`; `today = LocalDate.now()`):

```kotlin
    @Test
    fun routes_and_pole_gps_orders_are_grouped_by_status() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route(woStatus = "in_progress")),
                            assignedPoleGpsOrders = listOf(route(woStatus = "assigned").copy(workOrderId = "WO-G", title = "Đo GPS ấp 3")),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Đang làm · 1").assertExists()
        composeRule.onNodeWithText("Chưa bắt đầu · 1").assertExists()
        composeRule.onNodeWithText("Đo GPS ấp 3").assertExists()
    }

    @Test
    fun a_route_scheduled_later_has_locked_buttons_and_an_opens_on_hint() {
        val later = LocalDate.now().plusDays(3)
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route().copy(scheduledDate = later.toString()))))
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertIsNotEnabled()
        composeRule.onNodeWithText("Yêu cầu hủy").assertIsNotEnabled()
        composeRule.onNodeWithText("Mở vào", substring = true).assertExists()
    }

    @Test
    fun a_route_past_its_schedule_is_locked_and_flags_the_schedule_row() {
        val yesterday = LocalDate.now().minusDays(1)
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route().copy(scheduledDate = yesterday.toString()))))
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertIsNotEnabled()
        composeRule.onNodeWithText("Đã khoá do quá hạn").assertExists()
        composeRule.onNodeWithText("Đã qua lịch").assertExists()
    }

    @Test
    fun a_pending_sync_route_has_a_sync_badge_and_no_buttons() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state =
                        SurveyHubUiState.Success(
                            assignedRoutes = listOf(route(woStatus = "in_progress")),
                            locallyCompletedWorkOrderIds = setOf("WO-1"),
                            completionSyncStates = mapOf("WO-1" to CompletionSyncState.FAILED),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Chờ đồng bộ · 1").assertExists()
        composeRule.onNodeWithText("Đồng bộ lỗi").assertExists()
        composeRule.onNodeWithText("Chụp ảnh/video từng cột").assertDoesNotExist()
    }

    @Test
    fun tapping_a_pole_gps_card_opens_the_detail() {
        var opened: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(
                    state = SurveyHubUiState.Success(assignedRoutes = emptyList(), assignedPoleGpsOrders = listOf(route().copy(workOrderId = "WO-G"))),
                    onOpenWorkOrderDetail = { opened = it },
                )
            }
        }
        composeRule.onNode(hasClickAction() and hasText("Đo GPS cột", substring = true)).performClick()
        assertEquals("WO-G", opened)
    }

    @Test
    fun an_unknown_highlight_id_does_not_crash() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(state = SurveyHubUiState.Success(assignedRoutes = listOf(route())), highlightWorkOrderId = "NOPE")
            }
        }
        composeRule.onNodeWithText("Bắt đầu").assertExists()
    }
```

Update older tests: `success_state_with_an_assigned_route_shows_the_route_name_and_the_todo_section_header` expects `"Chưa bắt đầu · 1"`; `a_locally_completed_route_does_not_appear_in_either_section` keeps (completed id, no sync state → hidden); `a_done_route_shows_da_hoan_tat_disabled_on_the_merged_cta` → delete (done routes have no button; Hub lists assigned/in_progress only). Run; expect new tests FAIL.

- [ ] **Step 2: Implement `SurveyHubCards.kt`**

```kotlin
package com.luxmap.feature.survey.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.icon
import com.luxmap.core.theme.label
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxDestructiveButton
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.CompletionSyncState
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleLabel
import com.luxmap.feature.workorder.ui.detail.workOrderScheduleWindow
import java.time.LocalDate

private fun HubItemKind.taskKind(): WorkOrderTaskKind =
    if (this == HubItemKind.SURVEY_ROUTE) WorkOrderTaskKind.SURVEY else WorkOrderTaskKind.POLE_GPS_UPDATE

private fun HubItemKind.label(): String = if (this == HubItemKind.SURVEY_ROUTE) "Tuyến khảo sát" else "Đo GPS cột"

private val SYNC =
    mapOf(
        CompletionSyncState.PENDING to SyncStatus.QUEUED_ONLINE,
        CompletionSyncState.FAILED to SyncStatus.FAILED,
        CompletionSyncState.CONFLICT to SyncStatus.CONFLICT,
    )

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardHeader(item: HubItem) {
    val kind = item.kind.taskKind()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Icon(kind.icon(), contentDescription = null, tint = kind.badgeColors().text, modifier = Modifier.size(20.dp))
            Text(item.kind.label(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val sync = item.syncState?.let { SYNC.getValue(it) }
        if (sync != null) {
            StatusBadge(text = sync.label(), colors = sync.badgeColors(), icon = sync.icon())
        } else {
            val status = workOrderStatusFromWire(item.order.woStatus)
            StatusBadge(text = status.label(), colors = status.badgeColors(), icon = status.icon())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.CardBody(
    item: HubItem,
    today: LocalDate,
) {
    Text(item.order.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            workOrderScheduleLabel(item.kind.taskKind()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.md),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(scheduleValue(item.order.scheduledDate, today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (workOrderScheduleWindow(item.order.scheduledDate, today).isOverdueToComplete) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Filled.Schedule, null, tint = LuxTheme.colors.dueOverdue, modifier = Modifier.size(16.dp))
                    Text("Đã qua lịch", style = MaterialTheme.typography.bodyMedium, color = LuxTheme.colors.dueOverdue)
                }
            }
        }
    }
}

@Composable
fun SurveyRouteCard(
    item: HubItem,
    today: LocalDate,
    isStarting: Boolean,
    isCancelled: Boolean,
    isHighlighted: Boolean,
    onStart: () -> Unit,
    onCapture: () -> Unit,
    onRequestCancellation: () -> Unit,
) {
    LuxCard(selected = isHighlighted) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardHeader(item)
            CardBody(item, today)
            val isInProgress = item.order.woStatus == "in_progress"
            when {
                isInProgress -> PrimaryButton(text = "Chụp ảnh/video từng cột", onClick = onCapture)
                isCancelled ->
                    Text(
                        "Đã gửi yêu cầu hủy · chờ duyệt",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                else -> {
                    val gate = startGate(item.order.scheduledDate, today)
                    gate.hint()?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        LuxDestructiveButton(
                            text = "Yêu cầu hủy",
                            onClick = onRequestCancellation,
                            filled = false,
                            enabled = gate == StartGate.Open,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = if (isStarting) "Đang bắt đầu…" else "Bắt đầu",
                            onClick = onStart,
                            enabled = gate == StartGate.Open && !isStarting,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PoleGpsCard(
    item: HubItem,
    today: LocalDate,
    isHighlighted: Boolean,
    onOpen: () -> Unit,
) {
    LuxCard(onClick = onOpen, selected = isHighlighted) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardHeader(item)
            CardBody(item, today)
        }
    }
}

@Composable
fun PendingSyncCard(
    item: HubItem,
    today: LocalDate,
) {
    LuxCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardHeader(item)
            CardBody(item, today)
        }
    }
}
```

`PrimaryButton(text, onClick, modifier, enabled, isLoading, trailingIcon)` is in `core/ui/components/PrimaryButton.kt`; `LuxDestructiveButton(text, onClick, modifier, filled, enabled)` in `LuxButtons.kt`. Do not change the components.

- [ ] **Step 3: Rewrite `SurveyHubContent`** in `SurveyHubScreen.kt`:

```kotlin
@Composable
private fun SurveyHubContent(
    state: SurveyHubUiState.Success,
    highlightWorkOrderId: String?,
    onCapturePolePhotos: (String) -> Unit,
    onStartRoute: (String) -> Unit,
    onOpenCancelSheet: (String) -> Unit,
    onOpenWorkOrderDetail: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    val groups =
        remember(state) {
            surveyHubGroups(
                state.assignedRoutes,
                state.assignedPoleGpsOrders,
                state.completionSyncStates,
                state.locallyCompletedWorkOrderIds,
            )
        }
    val keys =
        buildList {
            if (state.isStale) add("stale")
            if (state.startError != null) add("start_error")
            groups.forEach { group ->
                add("header_${group.section}")
                group.items.forEach { add(it.order.workOrderId) }
            }
        }
    val listState = rememberLazyListState()
    LaunchedEffect(keys, highlightWorkOrderId) {
        val index = keys.indexOf(highlightWorkOrderId)
        if (highlightWorkOrderId != null && index >= 0) listState.animateScrollToItem(index)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (state.isStale) {
            item(key = "stale") {
                LuxBanner(message = "Đang xem danh sách đã lưu trên máy, có thể chưa phải bản mới nhất", tone = LuxBannerTone.INFO)
            }
        }
        state.startError?.let { error ->
            item(key = "start_error") { LuxBanner(message = error, tone = LuxBannerTone.ERROR) }
        }
        groups.forEach { group ->
            item(key = "header_${group.section}") {
                LuxSectionHeader(title = "${group.section.title} · ${group.items.size}")
            }
            items(group.items, key = { it.order.workOrderId }) { item ->
                val id = item.order.workOrderId
                when {
                    group.section == HubSection.PENDING_SYNC -> PendingSyncCard(item, today)
                    item.kind == HubItemKind.POLE_GPS ->
                        PoleGpsCard(item, today, isHighlighted = id == highlightWorkOrderId, onOpen = { onOpenWorkOrderDetail(id) })
                    else ->
                        SurveyRouteCard(
                            item = item,
                            today = today,
                            isStarting = state.startingWorkOrderId == id,
                            isCancelled = id in state.cancelledWorkOrderIds,
                            isHighlighted = id == highlightWorkOrderId,
                            onStart = { onStartRoute(id) },
                            onCapture = { onCapturePolePhotos(id) },
                            onRequestCancellation = { onOpenCancelSheet(id) },
                        )
                }
            }
        }
    }
}
```

`LuxSectionHeader(title, modifier, actionLabel, onAction)` is in `LuxContent.kt`. Delete the old card composables, `RouteTile*` colors and `scheduledDate*` helpers.

- [ ] **Step 4: Run device class + ktlint; expect PASS.** If >400 lines, commit `SurveyHubCards.kt` first, then the screen.
- [ ] **Step 5: Commit** `style(survey): group hub work by status with vnext cards and schedule locks`

---

### Task 5: Four data states

**Files:** `SurveyHubScreen.kt` (Loading/Empty/Error branches, delete `HubLoadingSkeleton`, `RouteCardSkeleton`, `SkeletonBar`), `SurveyHubScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — replace `empty_state_shows_the_no_routes_message` and `error_state_shows_the_message_and_a_retry_action`:

```kotlin
    @Test
    fun empty_offers_refresh() {
        var retries = 0
        composeRule.setContent { LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Empty, onRetry = { retries++ }) } }
        composeRule.onNodeWithText("Chưa có việc khảo sát được giao").assertExists()
        composeRule.onNodeWithText("Làm mới").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun a_network_error_uses_hub_copy_and_hides_the_raw_message() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                SurveyHubScreen(state = SurveyHubUiState.Error("java.net.UnknownHostException", isNetworkError = true), onRetry = { retries++ })
            }
        }
        composeRule.onNodeWithText("Không tải được việc khảo sát").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent { LuxMapTheme { SurveyHubScreen(state = SurveyHubUiState.Loading) } }
        composeRule.onNodeWithContentDescription("Đang tải việc khảo sát").assertExists()
    }
```

- [ ] **Step 2: Implement** the three branches:

```kotlin
                is SurveyHubUiState.Loading ->
                    LuxLoadingRegion(description = "Đang tải việc khảo sát", modifier = Modifier.padding(Spacing.lg)) {
                        LuxSkeleton(modifier = Modifier.fillMaxWidth(0.4f).height(20.dp))
                        repeat(3) { LuxSkeleton(modifier = Modifier.fillMaxWidth().height(140.dp), shape = MaterialTheme.shapes.medium) }
                    }
                is SurveyHubUiState.Empty ->
                    LuxEmptyState(
                        title = "Chưa có việc khảo sát được giao",
                        message = "Khi Kỹ sư bảo trì giao tuyến khảo sát hoặc lệnh đo GPS cột, lệnh sẽ hiện ở đây.",
                        icon = Icons.Filled.EventAvailable,
                        actionLabel = "Làm mới",
                        onAction = onRetry,
                    )
                is SurveyHubUiState.Error ->
                    LuxErrorState(
                        onRetry = onRetry,
                        kind = if (state.isNetworkError) LuxErrorKind.NETWORK else LuxErrorKind.GENERIC,
                        title = "Không tải được việc khảo sát",
                        message = if (state.isNetworkError) "Kiểm tra kết nối rồi thử lại." else "Vui lòng thử lại sau ít phút.",
                    )
```

Delete the old skeleton helpers, `MessageState`, `InlineBanner`, `OutlinedButton` usages and unused imports (`ktlintFormat`).

- [ ] **Step 3: Run device class + unit + ktlint; expect PASS.**
- [ ] **Step 4: Commit** `style(survey): move the hub data states to vnext components`

---

### Task 6: Large font, full device run, cleanup, doc status

**Files:** Create `app/src/androidTest/java/com/luxmap/feature/survey/ui/hub/SurveyHubLargeFontTest.kt`; modify `docs/design-system/screens/SURVEY_HUB.md` (status line).

- [ ] **Step 1: Large-font test** (1.5×, 360dp width, pattern of `HomeLargeFontTest`):

```kotlin
    @Test
    fun both_route_buttons_stay_on_one_row_with_48dp_targets_and_the_title_is_visible() {
        show(
            SurveyHubUiState.Success(
                assignedRoutes =
                    listOf(
                        AssignedSurveyRoute(
                            workOrderId = "WO-1",
                            title = "Khảo sát tuyến đường liên xã Phước Hiệp đoạn qua chợ",
                            communeId = "COM-001",
                            woStatus = "assigned",
                            dueDate = null,
                            scheduledDate = LocalDate.now().toString(),
                        ),
                    ),
            ),
        )
        val start = composeRule.onNodeWithText("Bắt đầu").assertTouchTargetIsAtLeast(48.dp).fetchSemanticsNode()
        val cancel = composeRule.onNodeWithText("Yêu cầu hủy").assertTouchTargetIsAtLeast(48.dp).fetchSemanticsNode()
        assertEquals(start.boundsInRoot.top, cancel.boundsInRoot.top, 1f)
        composeRule.onNodeWithText("Khảo sát tuyến đường liên xã Phước Hiệp đoạn qua chợ").assertIsDisplayed()
    }
```

`show(state)` sets `LocalDensity` fontScale 1.5f, `Box(Modifier.width(360.dp))`, `LuxMapTheme { SurveyHubScreen(state = state) }`.

- [ ] **Step 2: Run** `-Pandroid.testInstrumentationRunnerArguments.package=com.luxmap.feature.survey.ui.hub` on device + `testLocalDebugUnitTest --tests "com.luxmap.feature.survey.*"` + ktlint; all PASS.
- [ ] **Step 3: Cleanup** — grep `SurveyHubScreen.kt`/`SurveyHubCards.kt` for `Navy700`, `Color(0x`, `TextStyle`, `Radius.`, `InlineBanner`, `MessageState`, `colorScheme.copy`; remove leftovers.
- [ ] **Step 4: Spec status** → `**ĐÃ DUYỆT VÀ ĐÃ CODE XONG** trên nhánh style/redesign-survey-hub-vnext. Còn nợ: kiểm tra tay trên máy đã đăng nhập (Light/Night Field, 200%).`
- [ ] **Step 5: Commit** `test(survey): cover the hub at large font and tidy leftovers`
