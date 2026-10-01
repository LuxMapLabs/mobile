# Survey UI Completion (F03/F04/F06) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the placeholder UI on F03 (`SurveyPlanScreen`), F04 (`CaptureScreen`), and F06
(`SubmitScreen`) with real UI matching Design System v2.0, including the live state data
(heading readiness, GPS accuracy, lux, duration, distance, storage, upload summary, pause/resume)
those screens need but do not yet have.

**Architecture:** Screen-by-screen (F03 → F04 → F06), each split into state/ViewModel work first
(TDD), then a new shared `core/ui/components` component, then the screen's own Composable wiring.
No new architecture layers — existing MVVM + Repository pattern, StateFlow, Hilt DI.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, Room, Coroutines/StateFlow, JUnit+MockK,
Turbine.

**Spec:** `docs/superpowers/specs/2026-10-01-survey-ui-design.md`

## Global Constraints

- Comments in code: English, simple everyday words, no Vietnamese (CLAUDE.md).
- `./gradlew ktlintCheck` must pass before any step is considered done; fix format issues with
  `./gradlew ktlintFormat` and re-check.
- No commit exceeds 400 changed lines (added+removed, excluding generated files) — split further
  if a task's diff estimate goes over.
- No new third-party library outside CLAUDE.md's "Ngăn xếp công nghệ" list.
- Every `sealed interface` UiState `when` in a Composable is exhaustive — no `else` branch.
- Offline-first: no step in this plan may make a step block on network access; all new local
  state comes from Room/local sensors, matching the existing pattern.
- Design tokens only: use `Spacing`/`Dimens`/`Typography`/`MaterialTheme.colorScheme`/
  `badgeColors()` — no hardcoded `Color(0x...)` or raw `.dp`/`.sp` literals in a screen file.
- Each task branches off `dev` per CLAUDE.md's git rules; never commit to `main`.

## Review Focus

- **Heading sensor missing on the device** (confirmed real bug, see `contract-drift.md`): F03's
  readiness checklist must show this row as `Fail` and actually block "Vào chế độ khảo sát", not
  just log it silently. Task 1/5's tests cover this.
- **GPS accuracy indicator and the GPS-lost warning banner disagreeing with each other**: both
  derive from the same live GPS state in F04; a stale/duplicated read could show "GPS tốt" and
  the red "Mất tín hiệu GPS" banner at once. Task 11's tests assert both come from one collected
  value, not two independent reads.
- **Haptic/warning spam**: GPS-lost and BLE-gap warnings must vibrate once on the false→true
  transition, not on every recomposition or every repeated `true` emission. Task 13 tests this
  explicitly (two consecutive `true` emissions → one vibration call).
- **Upload paused, app process killed, then reopened**: `FakeUploadRepository.resumePoints` is an
  in-memory `Map`, lost on process death. Reopening `SubmitScreen` for that `sessionId` must
  restart the upload from 0 without crashing (there is no persisted resume point to read) — not a
  silent data-loss bug, just the known limit of a Fake repo with no backing store. Task 15's test
  covers a fresh `FakeUploadRepository` instance uploading a previously-"interrupted" session from
  0, confirming it does not throw.
- **Zero-duration/zero-distance session reaching F06** (user stopped recording almost
  immediately): `SessionSummary` and the upload progress fraction (`bytesSent.toFloat() /
  totalBytes.toFloat()`) must not divide by zero or crash the Composable. Task 15/18 cover a
  session whose `totalBytes` is a small positive number (never zero, since at least the 5 log/
  config files always exist once a session is packaged).

---

## Phase 1 — F03 Tuyến khảo sát được giao

### Task 1: Add `headingAvailable` to the readiness decision

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCaseTest.kt`

**Interfaces:**
- Produces: `SurveyReadinessInput.headingAvailable: Boolean`,
  `SurveyReadinessResult.headingAvailable: Boolean`, both required (non-nullable) constructor
  params — every call site must supply them.

- [ ] **Step 1: Write the failing test**

Open the existing test file and add:

```kotlin
    @Test
    fun `is not ready when heading sensor is unavailable`() {
        val input = readyInput().copy(headingAvailable = false)

        val result = CheckSurveyReadinessUseCase()(input)

        assertFalse(result.isReady)
        assertFalse(result.headingAvailable)
    }
```

If the file has no shared `readyInput()` helper yet, add one at the top of the test class built
from today's all-true input, then rewrite any existing all-true test to use it:

```kotlin
    private fun readyInput() =
        SurveyReadinessInput(
            cameraPermissionGranted = true,
            exposureLockSupported = true,
            timestampSourceRealtime = true,
            gpsAvailable = true,
            headingAvailable = true,
            freeStorageBytes = 1_000_000_000L,
            requiredStorageBytes = 500_000_000L,
            batteryPercent = 80,
        )
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCaseTest"`
Expected: FAIL — `SurveyReadinessInput` has no `headingAvailable` parameter, compile error.

- [ ] **Step 3: Add the field and wire it into `isReady`**

In `CheckSurveyReadinessUseCase.kt`, add `val headingAvailable: Boolean` to both
`SurveyReadinessInput` and `SurveyReadinessResult` (right after `gpsAvailable` in both), add
`headingAvailable &&` to the `isReady` chain right after the `gpsAvailable &&` line, and pass
`headingAvailable = input.headingAvailable` through in `CheckSurveyReadinessUseCase.invoke()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCaseTest"`
Expected: PASS, including every pre-existing test in the file (they now need `headingAvailable`
added to whatever input literal they build — update any that still construct `SurveyReadinessInput`
directly instead of through `readyInput()`).

- [ ] **Step 5: ktlint and commit**

Run: `./gradlew ktlintCheck` (fix with `ktlintFormat` if needed)

```bash
git checkout dev && git pull && git checkout -b feat/fm-09-survey-plan-ui
git add app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt \
  app/src/test/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCaseTest.kt
git commit -m "feat(fm-09): add heading sensor check to survey readiness"
```

### Task 2: Gather the real `headingAvailable` value on-device

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt`

**Interfaces:**
- Consumes: `SurveyReadinessInput` (Task 1's new shape).
- Produces: nothing new for later tasks — this only fills in the real value.

No unit test (CLAUDE.md/this file's own existing comment: real `CameraManager`/`SensorManager`
calls are verified on a real device, not unit tested — matches the existing pattern for every
other field in this same function).

- [ ] **Step 1: Add the sensor check**

In `RealSurveyReadinessInputProvider.gather()`, add right after the existing
`locationManager`/`gpsProviderEnabled` block:

```kotlin
            val sensorManager = context.getSystemService(android.hardware.SensorManager::class.java)
            val headingAvailable =
                sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) != null
```

Add `headingAvailable = headingAvailable,` to the returned `SurveyReadinessInput(...)`, right
after `gpsAvailable = ...,`.

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt
git commit -m "feat(fm-09): gather real heading sensor availability on device"
```

### Task 3: `CameraReadinessPanel` shared component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/CameraReadinessPanel.kt`
- Test: none (pure Composable layout, no logic to unit test — matches `PrimaryButton`/`StatusBadge`,
  neither of which has a test file today).

**Interfaces:**
- Produces: `ReadinessCheckStatus` enum (`Checking`, `Pass`, `Fail`), `ReadinessCheckItem` data
  class, `CameraReadinessPanel(items: List<ReadinessCheckItem>, modifier: Modifier = Modifier)`
  composable — Task 5 consumes both.

- [ ] **Step 1: Write the component**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.Success600

enum class ReadinessCheckStatus { CHECKING, PASS, FAIL }

// fixHint is shown only on FAIL - a passed or still-checking row has nothing to tell the user to
// do yet.
data class ReadinessCheckItem(
    val label: String,
    val status: ReadinessCheckStatus,
    val fixHint: String? = null,
)

// Design System v2.0 §6.7 - one row per automatic check, each with its own
// Checking/Pass/Fail state and an inline fix hint on failure.
@Composable
fun CameraReadinessPanel(
    items: List<ReadinessCheckItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            CameraReadinessRow(item)
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun CameraReadinessRow(item: ReadinessCheckItem) {
    val (icon, color) =
        when (item.status) {
            ReadinessCheckStatus.CHECKING -> Icons.Filled.HourglassEmpty to Gray500
            ReadinessCheckStatus.PASS -> Icons.Filled.Check to Success600
            ReadinessCheckStatus.FAIL -> Icons.Filled.Close to Danger600
        }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(Spacing.sm))
            Text(text = item.label, style = MaterialTheme.typography.bodyLarge, color = color)
        }
        if (item.status == ReadinessCheckStatus.FAIL && item.fixHint != null) {
            Text(
                text = item.fixHint,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.padding(start = Spacing.xl),
            )
        }
    }
}
```

Add the missing `import androidx.compose.foundation.layout.width` and
`import androidx.compose.foundation.layout.padding` (both used above, neither in the initial
import block).

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/CameraReadinessPanel.kt
git commit -m "feat(fm-09): add CameraReadinessPanel shared component"
```

### Task 4: Compose UI test for F03's 4 states

**Files:**
- Create: `app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt`

**Interfaces:**
- Consumes: `SurveyPlanUiState` (existing sealed interface, unchanged shape).

This is written now, against the CURRENT screen, and must still pass unmodified after Task 5
restyles the screen (Task 5 changes visuals, not the text this test asserts on) — written first
here so Task 5 has a regression net while it edits the Composable.

- [ ] **Step 1: Write the test**

```kotlin
package com.luxmap.feature.survey.ui.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.data.SurveySweepStatus
import org.junit.Rule
import org.junit.Test

class SurveyPlanScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun route() =
        AssignedSurveyRoute(
            surveySweepId = "SWEEP-1",
            assignedByName = "Nguyen Van A",
            plannedDate = "2026-10-02",
            status = SurveySweepStatus.PLANNED,
            roadSegments = emptyList(),
        )

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Loading, onRouteSelected = {}) }
        }
        composeRule.onNodeWithTag("survey_plan_loading").assertExists()
    }

    @Test
    fun success_state_shows_the_route_name() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("SWEEP-1").assertExists()
    }

    @Test
    fun empty_state_shows_the_no_routes_message() {
        composeRule.setContent {
            LuxMapTheme { SurveyPlanContent(state = SurveyPlanUiState.Empty, onRouteSelected = {}) }
        }
        composeRule.onNodeWithText("Chưa có tuyến nào được phân công").assertExists()
    }

    @Test
    fun error_state_shows_the_message_and_a_retry_action() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Error("Không tải được tuyến khảo sát"),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được tuyến khảo sát").assertExists()
        composeRule.onNodeWithText("Thử lại").assertExists()
    }
}
```

Add `import androidx.compose.ui.test.onNodeWithTag` to the imports.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanScreenTest"`
Expected: FAIL to compile — `SurveyPlanContent` does not exist yet (today everything lives
directly in `SurveyPlanScreen`, which takes a `hiltViewModel()` and cannot be tested without one).
Task 5 introduces `SurveyPlanContent` as the state-driven, ViewModel-free inner composable
`SurveyPlanScreen` delegates to — the same split every other tested screen in this codebase needs
for a Compose UI test to construct a state directly.

- [ ] **Step 3: Leave failing — Task 5 makes it pass**

Commit this test alongside Task 5's screen changes (same branch), not as its own commit — it has
no independent deliverable without `SurveyPlanContent` existing.

### Task 5: Wire `SurveyPlanScreen` to the real design

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt`

**Interfaces:**
- Consumes: `ReadinessCheckItem`/`ReadinessCheckStatus`/`CameraReadinessPanel` (Task 3),
  `SurveyReadinessResult.headingAvailable` (Task 1).
- Produces: `SurveyPlanContent(state: SurveyPlanUiState, onRouteSelected: (String) -> Unit, ...)` —
  the ViewModel-free composable Task 4's test calls directly.

- [ ] **Step 1: Add retry to the ViewModel**

In `SurveyPlanViewModel.kt`, extract the `init` block's body into a private `loadRoutes()` and
call it from both `init` and a new public `onRetry()`:

```kotlin
        init {
            loadRoutes()
        }

        fun onRetry() {
            if (_uiState.value !is SurveyPlanUiState.Error) return
            _uiState.value = SurveyPlanUiState.Loading
            loadRoutes()
        }

        private fun loadRoutes() {
            viewModelScope.launch {
                repository
                    .observeAssignedRoutes()
                    .catch { e ->
                        _uiState.value = SurveyPlanUiState.Error(e.message ?: "Không tải được tuyến khảo sát")
                    }.collect { routes ->
                        _uiState.value =
                            if (routes.isEmpty()) SurveyPlanUiState.Empty else SurveyPlanUiState.Success(routes)
                    }
            }
        }
```

- [ ] **Step 2: Split `SurveyPlanScreen` into a thin ViewModel host and a stateless `SurveyPlanContent`**

Rewrite `SurveyPlanScreen.kt`'s top of file so `SurveyPlanScreen` only collects state and
delegates, with the rest of today's `when` body moved into `SurveyPlanContent`:

```kotlin
@Composable
fun SurveyPlanScreen(
    onEnterCaptureMode: (surveySweepId: String) -> Unit,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LuxMapTheme(forceDark = true) {
        SurveyPlanContent(
            state = uiState,
            onRouteSelected = viewModel::onRouteSelected,
            onRetry = viewModel::onRetry,
            onEnterCaptureMode = onEnterCaptureMode,
        )
    }
}

@Composable
fun SurveyPlanContent(
    state: SurveyPlanUiState,
    onRouteSelected: (String) -> Unit,
    onRetry: () -> Unit = {},
    onEnterCaptureMode: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var hasBlePermissions by remember { mutableStateOf(hasBlePermissions(context)) }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Tuyến khảo sát được giao",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(Spacing.lg),
        )
        when (state) {
            is SurveyPlanUiState.Loading ->
                Box(
                    Modifier.fillMaxSize().testTag("survey_plan_loading"),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

            is SurveyPlanUiState.Success -> {
                val permissionLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                        hasBlePermissions = hasBlePermissions(context)
                        state.selectedSurveySweepId?.let(onRouteSelected)
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.routes) { route ->
                        SurveyRouteCard(
                            route = route,
                            isSelected = route.surveySweepId == state.selectedSurveySweepId,
                            readiness = if (route.surveySweepId == state.selectedSurveySweepId) state.readiness else null,
                            hasBlePermissions = hasBlePermissions,
                            onClick = { onRouteSelected(route.surveySweepId) },
                            onEnterCaptureMode = { onEnterCaptureMode(route.surveySweepId) },
                            onRequestPermissions = { permissionLauncher.launch(CAPTURE_PERMISSIONS) },
                        )
                    }
                }
            }

            is SurveyPlanUiState.Empty ->
                Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Text(
                        "Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

            is SurveyPlanUiState.Error ->
                Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Spacing.md))
                        PrimaryButton(text = "Thử lại", onClick = onRetry)
                    }
                }
        }
    }
}
```

Add imports: `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height`,
`androidx.compose.ui.platform.testTag` (as `androidx.compose.ui.test.*` is androidTest-only;
the production tag comes from `androidx.compose.ui.platform.testTag`), `androidx.compose.ui.text.style.TextAlign`,
`com.luxmap.core.theme.LuxMapTheme`.

- [ ] **Step 3: Replace `ReadinessChecklist` with `CameraReadinessPanel`**

Replace the whole `ReadinessChecklist`/`ReadinessRow` private composables with a mapper and the
new panel:

```kotlin
@Composable
private fun ReadinessChecklist(readiness: SurveyReadinessResult) {
    val items =
        listOf(
            ReadinessCheckItem("Quyền camera", readiness.cameraPermissionGranted.toStatus()),
            ReadinessCheckItem("Khoá exposure hỗ trợ", readiness.exposureLockSupported.toStatus()),
            ReadinessCheckItem(
                "Đồng hồ cảm biến REALTIME",
                readiness.timestampSourceRealtime.toStatus(),
                fixHint = if (!readiness.timestampSourceRealtime) {
                    "Thiết bị này không được hỗ trợ khảo sát (đồng hồ cảm biến camera không đạt yêu cầu)"
                } else {
                    null
                },
            ),
            ReadinessCheckItem("GPS", readiness.gpsAvailable.toStatus()),
            ReadinessCheckItem(
                "Heading",
                readiness.headingAvailable.toStatus(),
                fixHint = if (!readiness.headingAvailable) "Thiết bị không có cảm biến la bàn" else null,
            ),
            ReadinessCheckItem(
                "Đủ dung lượng trống",
                (readiness.freeStorageBytes >= readiness.requiredStorageBytes).toStatus(),
            ),
            ReadinessCheckItem(
                "Pin đủ (>= 20%)",
                (readiness.batteryPercent >= SurveyReadinessResult.MIN_BATTERY_PERCENT).toStatus(),
            ),
        )
    CameraReadinessPanel(items)
}

private fun Boolean.toStatus() = if (this) ReadinessCheckStatus.PASS else ReadinessCheckStatus.FAIL
```

Keep `SurveyRouteCard` calling `ReadinessChecklist(readiness)` exactly as it does today — only
this helper's internals changed.

- [ ] **Step 4: Build and run the Task 4 UI test**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanScreenTest"`
Expected: PASS (requires a connected device/emulator, same as every other Compose UI test in this
project).

- [ ] **Step 5: ktlint, run the full unit test suite, and commit**

Run: `./gradlew ktlintCheck` then `./gradlew :app:testDebugUnitTest`
Expected: both BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt \
  app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt
git commit -m "feat(fm-09): redesign survey plan screen with readiness panel and dark mode"
git push -u origin feat/fm-09-survey-plan-ui
```

Open a PR from `feat/fm-09-survey-plan-ui` into `dev` once this task's checks are green. Phase 2
starts from a fresh `dev` pull after this merges (or can branch off this branch if the PR is
still open — confirm with the project owner which they prefer before starting Phase 2).

---

## Phase 2 — F04 Chế độ chụp khảo sát (Capture Mode)

### Task 6: Cumulative distance in `SurveyTrackRecorder`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt`
- Modify: `app/src/test/java/com/luxmap/core/location/SurveyTrackRecorderTest.kt`

**Interfaces:**
- Produces: `SurveyTrackRecorder.totalDistanceMeters: StateFlow<Float>`, updated as a side effect
  of every `onLocationUpdate()` call — Task 7 forwards this up.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `total distance starts at zero before any fix`() {
        val recorder = SurveyTrackRecorder()
        assertEquals(0f, recorder.totalDistanceMeters.value)
    }

    @Test
    fun `total distance stays zero after only one fix`() {
        val recorder = SurveyTrackRecorder()
        recorder.onLocationUpdate(fakeLocation(elapsedRealtimeNs = 0L))
        assertEquals(0f, recorder.totalDistanceMeters.value)
    }

    @Test
    fun `total distance accumulates the real distance between two fixes`() {
        val recorder = SurveyTrackRecorder()
        val first = fakeLocation(elapsedRealtimeNs = 0L, lat = 10.0, lng = 106.0)
        val second = fakeLocation(elapsedRealtimeNs = 1_000_000_000L, lat = 10.0001, lng = 106.0)
        every { first.distanceTo(second) } returns 11.1f

        recorder.onLocationUpdate(first)
        recorder.onLocationUpdate(second)

        assertEquals(11.1f, recorder.totalDistanceMeters.value)
    }

    @Test
    fun `total distance keeps accumulating across three or more fixes`() {
        val recorder = SurveyTrackRecorder()
        val a = fakeLocation(elapsedRealtimeNs = 0L)
        val b = fakeLocation(elapsedRealtimeNs = 1_000_000_000L)
        val c = fakeLocation(elapsedRealtimeNs = 2_000_000_000L)
        every { a.distanceTo(b) } returns 10f
        every { b.distanceTo(c) } returns 5f

        recorder.onLocationUpdate(a)
        recorder.onLocationUpdate(b)
        recorder.onLocationUpdate(c)

        assertEquals(15f, recorder.totalDistanceMeters.value)
    }
```

`Location.distanceTo()` is an instance method on the real Android `Location` class and works on a
MockK `mockk<Location>()` the same way the existing `every { this@apply.latitude }` stubs do —
no new mocking pattern needed.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.location.SurveyTrackRecorderTest"`
Expected: FAIL — `totalDistanceMeters` does not exist yet, compile error.

- [ ] **Step 3: Implement**

In `SurveyTrackRecorder.kt`, add the two new imports and the field + accumulation:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
```

```kotlin
        private val _totalDistanceMeters = MutableStateFlow(0f)
        val totalDistanceMeters: StateFlow<Float> = _totalDistanceMeters.asStateFlow()

        // Null only before the first fix of the session - nothing to measure a distance against yet.
        @Volatile
        private var lastLocation: Location? = null
```

In `onLocationUpdate()`, right before `lastFixElapsedRealtimeNs = location.elapsedRealtimeNanos`,
add:

```kotlin
            lastLocation?.let { previous -> _totalDistanceMeters.value += previous.distanceTo(location) }
            lastLocation = location
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.location.SurveyTrackRecorderTest"`
Expected: PASS, all tests including the pre-existing ones.

- [ ] **Step 5: ktlint and commit**

```bash
git checkout dev && git pull && git checkout -b feat/fm-30-capture-mode-ui
git add app/src/main/java/com/luxmap/core/location/SurveyTrackRecorder.kt \
  app/src/test/java/com/luxmap/core/location/SurveyTrackRecorderTest.kt
git commit -m "feat(fm-30): accumulate total distance in SurveyTrackRecorder"
```

### Task 7: Expose live GPS point and distance through the capture pipeline

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt`

**Interfaces:**
- Consumes: `SurveyTrackRecorder.totalDistanceMeters` (Task 6).
- Produces: `SurveyCaptureController.liveGpsPoint: StateFlow<TrackPoint?>`,
  `SurveyCaptureController.distanceMeters: StateFlow<Float>` — Task 10 consumes both.
- Persists: `LocalSurveySessionEntity.distanceMeters` gets a real value at stop time (was always
  `null` before).

No new unit test for this task: it is pure plumbing through classes this codebase already
documents as real-device-only (`LocationHeadingRecorder`, `SurveyCaptureService`,
`SurveyCaptureController` — same `bindService`/real-sensor reasoning as the rest of FM-08).

- [ ] **Step 1: Expose the live point from `LocationHeadingRecorder`**

Add to `LocationHeadingRecorder`, right after the existing `_gpsSignalState` field:

```kotlin
        private val _livePoint = MutableStateFlow<com.luxmap.core.location.TrackPoint?>(null)
        val livePoint: StateFlow<com.luxmap.core.location.TrackPoint?> = _livePoint.asStateFlow()

        val distanceMeters: StateFlow<Float> get() = trackRecorder.totalDistanceMeters
```

Inside the existing `LocationCallback.onLocationResult()`, right after
`val point = trackRecorder.onLocationUpdate(location)`, add `_livePoint.value = point`.

- [ ] **Step 2: Expose both from `SurveyCaptureService`**

Add next to the existing `gpsSignalState` getter:

```kotlin
    val liveGpsPoint: StateFlow<com.luxmap.core.location.TrackPoint?>
        get() = locationHeadingRecorder.livePoint

    val distanceMeters: StateFlow<Float>
        get() = locationHeadingRecorder.distanceMeters
```

- [ ] **Step 3: Persist the final distance at stop time**

In `writeSessionResult()`, add `distanceMeters = locationHeadingRecorder.distanceMeters.value.toDouble(),`
to the `session.copy(...)` call, right after the existing `durationSeconds = durationSeconds,` line.

- [ ] **Step 4: Thread both through `SurveyCaptureController`**

Add to the `SurveyCaptureController` interface, next to the existing `gpsSignalState`:

```kotlin
    val liveGpsPoint: StateFlow<com.luxmap.core.location.TrackPoint?>
    val distanceMeters: StateFlow<Float>
```

In `RealSurveyCaptureController`, add backing state and forwarding, following the exact pattern
`_gpsSignalState`/`gpsForwardingJob` already uses:

```kotlin
        private val _liveGpsPoint = MutableStateFlow<com.luxmap.core.location.TrackPoint?>(null)
        override val liveGpsPoint: StateFlow<com.luxmap.core.location.TrackPoint?> = _liveGpsPoint.asStateFlow()

        private val _distanceMeters = MutableStateFlow(0f)
        override val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()
```

In `onServiceConnected`, inside the same `gpsForwardingJob = controllerScope.launch { ... }` block
(or a second `launch` right next to it — either is fine, keep it next to the existing one for
readability), add:

```kotlin
                    controllerScope.launch { service.liveGpsPoint.collect { _liveGpsPoint.value = it } }
                    controllerScope.launch { service.distanceMeters.collect { _distanceMeters.value = it } }
```

- [ ] **Step 5: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Update the existing `CaptureViewModelTest` mock helper**

`CaptureViewModelTest.kt`'s `viewModel()` helper builds a `mockk<SurveyCaptureController>(relaxed = true)`
and stubs `gpsSignalState`/`recordingStartResult` individually — add two more `every` stubs so
Task 9's new collectors in `CaptureViewModel` have something to collect in every existing test,
not just new ones:

```kotlin
        every { controller.liveGpsPoint } returns MutableStateFlow(null)
        every { controller.distanceMeters } returns MutableStateFlow(0f)
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: PASS (relaxed mock already returns something for these before the explicit stub too,
but the explicit stub keeps the test's intent clear for Task 9's reviewer).

- [ ] **Step 7: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/LocationHeadingRecorder.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureService.kt \
  app/src/main/java/com/luxmap/feature/survey/capture/SurveyCaptureController.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(fm-30): expose live gps point and distance from the capture service"
```

### Task 8: Storage monitor

**Files:**
- Create: `app/src/main/java/com/luxmap/core/common/StorageMonitor.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt`
  (reuse, not duplicate, the `StatFs` call F03 already makes)

**Interfaces:**
- Produces: `StorageMonitor.freeBytes(): Long` — Task 10 calls this from `CaptureViewModel`.

- [ ] **Step 1: Write the class**

```kotlin
package com.luxmap.core.common

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Wraps StatFs so ViewModels never take a raw Context (matches LocationTracker/HeadingSensor's
// existing pattern of wrapping an Android system call instead of injecting Context directly).
@Singleton
class StorageMonitor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun freeBytes(): Long = StatFs(context.filesDir.path).availableBytes
    }
```

- [ ] **Step 2: Reuse it from `SurveyReadinessInputProvider`**

Replace `RealSurveyReadinessInputProvider`'s own `val statFs = StatFs(context.filesDir.path)` and
`freeStorageBytes = statFs.availableBytes` with an injected `StorageMonitor` and
`freeStorageBytes = storageMonitor.freeBytes()`. Add `private val storageMonitor: StorageMonitor`
to its constructor.

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/core/common/StorageMonitor.kt \
  app/src/main/java/com/luxmap/feature/survey/domain/SurveyReadinessInputProvider.kt
git commit -m "feat(fm-30): add shared StorageMonitor, reuse it from the readiness check"
```

### Task 9: `GpsAccuracyIndicator` shared component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/GpsAccuracyIndicator.kt`

**Interfaces:**
- Produces: `gpsAccuracyLabel(accuracyMeters: Float?): String` and
  `GpsAccuracyIndicator(accuracyMeters: Float?, modifier: Modifier = Modifier)` — Task 11 consumes
  both.

- [ ] **Step 1: Write the component**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Success600

// Thresholds per the 2026-10-01 survey UI design spec §3 - not pulled from an existing document
// (Design System v2.0 §6.9 defers the exact numbers to "đặc tả kỹ thuật cấu hình"), confirmed
// with the project owner during that spec's review.
private const val GOOD_ACCURACY_METERS = 10f
private const val WEAK_ACCURACY_METERS = 30f

fun gpsAccuracyLabel(accuracyMeters: Float?): String =
    when {
        accuracyMeters == null -> "Không có vị trí hợp lệ"
        accuracyMeters <= GOOD_ACCURACY_METERS -> "GPS tốt · ±${accuracyMeters.toInt()} m"
        accuracyMeters <= WEAK_ACCURACY_METERS -> "GPS yếu · ±${accuracyMeters.toInt()} m"
        else -> "Không có vị trí hợp lệ"
    }

@Composable
fun GpsAccuracyIndicator(
    accuracyMeters: Float?,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            accuracyMeters == null || accuracyMeters > WEAK_ACCURACY_METERS -> Danger600
            accuracyMeters <= GOOD_ACCURACY_METERS -> Success600
            else -> Gray500
        }
    Text(text = gpsAccuracyLabel(accuracyMeters), style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}
```

- [ ] **Step 2: Write the label test**

```kotlin
package com.luxmap.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsAccuracyIndicatorTest {
    @Test
    fun `no fix reports no valid position`() {
        assertEquals("Không có vị trí hợp lệ", gpsAccuracyLabel(null))
    }

    @Test
    fun `accuracy at the good threshold reports good`() {
        assertEquals("GPS tốt · ±10 m", gpsAccuracyLabel(10f))
    }

    @Test
    fun `accuracy just past the good threshold reports weak`() {
        assertEquals("GPS yếu · ±11 m", gpsAccuracyLabel(11f))
    }

    @Test
    fun `accuracy at the weak threshold reports weak`() {
        assertEquals("GPS yếu · ±30 m", gpsAccuracyLabel(30f))
    }

    @Test
    fun `accuracy past the weak threshold reports no valid position`() {
        assertEquals("Không có vị trí hợp lệ", gpsAccuracyLabel(31f))
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.ui.components.GpsAccuracyIndicatorTest"`
Expected: PASS.

- [ ] **Step 4: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/GpsAccuracyIndicator.kt \
  app/src/test/java/com/luxmap/core/ui/components/GpsAccuracyIndicatorTest.kt
git commit -m "feat(fm-30): add GpsAccuracyIndicator shared component"
```

### Task 10: Wire live data into `CaptureUiState.Recording` and `CaptureViewModel`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `SurveyCaptureController.liveGpsPoint`/`distanceMeters` (Task 7),
  `StorageMonitor.freeBytes()` (Task 8), `LuxSensorBleClient.samples` (already existing).
- Produces: the final `CaptureUiState.Recording` shape — Task 13 (the screen) consumes it.

- [ ] **Step 1: Extend `CaptureUiState.Recording`**

```kotlin
    data class Recording(
        val gpsSignalLost: Boolean = false,
        val bleGapDetected: Boolean = false,
        val durationSeconds: Long = 0,
        val distanceMeters: Float = 0f,
        val gpsAccuracyMeters: Float? = null,
        val headingDeg: Float? = null,
        val latestLuxValue: Float? = null,
        val freeStorageBytes: Long? = null,
    ) : CaptureUiState
```

- [ ] **Step 2: Write the failing tests**

Add to `CaptureViewModelTest.kt` (the `viewModel()` helper needs a `storageMonitor` param added
to its signature too — see Step 3 below):

```kotlin
    @Test
    fun `recording state picks up the latest lux sample`() =
        runTest {
            val samples = MutableSharedFlow<LuxSample>(extraBufferCapacity = 1)
            val (viewModel, luxClient, controller) =
                viewModel(
                    connectionState = MutableStateFlow(BleConnectionState.Connected),
                    recordingStartResult = MutableStateFlow(RecordingStartResult.Ready),
                )
            every { luxClient.samples } returns samples
            viewModel.onStartRecording("SWEEP-1")
            dispatcher.scheduler.advanceUntilIdle()

            samples.emit(LuxSample(seq = 0, moduleMs = 0, phoneElapsedNs = 0, lux = 42.5f, bootId = 0))
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is CaptureUiState.Recording)
            assertEquals(42.5f, (state as CaptureUiState.Recording).latestLuxValue)
        }

    @Test
    fun `recording state picks up live gps accuracy and distance`() =
        runTest {
            val liveGpsPoint = MutableStateFlow<TrackPoint?>(null)
            val distanceMeters = MutableStateFlow(0f)
            val (viewModel, _, controller) =
                viewModel(recordingStartResult = MutableStateFlow(RecordingStartResult.Ready))
            every { controller.liveGpsPoint } returns liveGpsPoint
            every { controller.distanceMeters } returns distanceMeters
            viewModel.onStartRecording("SWEEP-1")
            dispatcher.scheduler.advanceUntilIdle()

            liveGpsPoint.value = TrackPoint(0L, 10.0, 106.0, accuracyM = 7.5f, gpsBearingDeg = 90f, speedMps = null)
            distanceMeters.value = 123f
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as CaptureUiState.Recording
            assertEquals(7.5f, state.gpsAccuracyMeters)
            assertEquals(90f, state.headingDeg)
            assertEquals(123f, state.distanceMeters)
        }

    @Test
    fun `recording state duration advances with each tick`() =
        runTest {
            val (viewModel, _, _) = viewModel(recordingStartResult = MutableStateFlow(RecordingStartResult.Ready))
            viewModel.onStartRecording("SWEEP-1")
            dispatcher.scheduler.advanceUntilIdle()

            dispatcher.scheduler.advanceTimeBy(3_000)
            dispatcher.scheduler.runCurrent()

            val state = viewModel.uiState.value as CaptureUiState.Recording
            assertEquals(3L, state.durationSeconds)
        }

    @Test
    fun `recording state reports free storage from the storage monitor`() =
        runTest {
            val storageMonitor = mockk<StorageMonitor>()
            every { storageMonitor.freeBytes() } returns 2_000_000_000L
            val (viewModel, _, _) =
                viewModel(
                    recordingStartResult = MutableStateFlow(RecordingStartResult.Ready),
                    storageMonitor = storageMonitor,
                )
            viewModel.onStartRecording("SWEEP-1")
            dispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value as CaptureUiState.Recording
            assertEquals(2_000_000_000L, state.freeStorageBytes)
        }
```

Add imports: `com.luxmap.core.ble.LuxSample`, `com.luxmap.core.common.StorageMonitor`,
`com.luxmap.core.location.TrackPoint`, `kotlinx.coroutines.flow.MutableSharedFlow`.

- [ ] **Step 3: Update the `viewModel()` test helper's signature**

Add a `storageMonitor: StorageMonitor = mockk<StorageMonitor>().also { every { it.freeBytes() } returns 0L }`
parameter, pass it into the `CaptureViewModel(...)` constructor call Step 4 below adds the
parameter to.

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: FAIL to compile — `CaptureViewModel` has no `storageMonitor` constructor param yet, and
`Recording` has no new fields yet.

- [ ] **Step 5: Implement in `CaptureViewModel`**

Add `private val storageMonitor: StorageMonitor` to the constructor (Hilt injects it
automatically — `StorageMonitor` is already `@Singleton` with no module binding needed).

Replace the `RecordingStartResult.Ready ->` branch inside the `recordingStartResult.collect`
block with a call to a new `startRecordingTimersAndCollectors()`:

```kotlin
                        RecordingStartResult.Ready -> {
                            _uiState.value =
                                CaptureUiState.Recording(
                                    gpsSignalLost = captureController.gpsSignalState.value == GpsSignalState.Lost,
                                    bleGapDetected = luxClient.connectionState.value == BleConnectionState.Disconnected,
                                    freeStorageBytes = storageMonitor.freeBytes(),
                                )
                            startRecordingTimersAndCollectors()
                        }
```

Add the new private function and a `recordingJobs` holder, following the existing `scanJob`
pattern:

```kotlin
        private var recordingJobs: MutableList<Job> = mutableListOf()

        private fun startRecordingTimersAndCollectors() {
            recordingJobs.forEach { it.cancel() }
            recordingJobs =
                mutableListOf(
                    viewModelScope.launch {
                        luxClient.samples.collect { sample ->
                            updateRecording { it.copy(latestLuxValue = sample.lux) }
                        }
                    },
                    viewModelScope.launch {
                        captureController.liveGpsPoint.collect { point ->
                            updateRecording {
                                it.copy(gpsAccuracyMeters = point?.accuracyM, headingDeg = point?.gpsBearingDeg)
                            }
                        }
                    },
                    viewModelScope.launch {
                        captureController.distanceMeters.collect { distance ->
                            updateRecording { it.copy(distanceMeters = distance) }
                        }
                    },
                    viewModelScope.launch {
                        var elapsedSeconds = 0L
                        while (true) {
                            delay(1_000)
                            elapsedSeconds += 1
                            val freeBytes = storageMonitor.freeBytes()
                            updateRecording { it.copy(durationSeconds = elapsedSeconds, freeStorageBytes = freeBytes) }
                        }
                    },
                )
        }

        // No-op once the screen has left Recording (packaging/packaged/failed) - a collector that
        // fires one more time right after the state already moved on must not resurrect Recording.
        private fun updateRecording(transform: (CaptureUiState.Recording) -> CaptureUiState.Recording) {
            val current = _uiState.value
            if (current is CaptureUiState.Recording) _uiState.value = transform(current)
        }
```

Cancel `recordingJobs` wherever the screen leaves `Recording` — in `onStopRecording()`, add
`recordingJobs.forEach { it.cancel() }` as the first line of the function (before
`_uiState.value = CaptureUiState.Packaging`).

Add imports: `com.luxmap.core.common.StorageMonitor`, `kotlinx.coroutines.delay`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: PASS, all tests in the file (old and new).

- [ ] **Step 7: ktlint and commit**

Estimate this diff before committing — `CaptureViewModel.kt` + its test are likely to land close
to 400 lines combined; if `git diff --stat` shows over 400, split the test file changes into
their own commit after the implementation commit.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(fm-30): wire live lux, gps, distance, duration and storage into capture state"
```

### Task 11: Haptic feedback on GPS-lost / BLE-gap transitions

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt`

**Interfaces:**
- Produces: `CaptureUiState.Recording` already carries `gpsSignalLost`/`bleGapDetected`
  (unchanged) — this task only adds a side-effect callback the screen (Task 13) observes to
  trigger the actual vibration, keeping `CaptureViewModel` free of `android.os.Vibrator` (matches
  CLAUDE.md: ViewModel holds state, Composable/Activity performs device-level side effects).

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `warning pulse fires once when gps signal is first lost`() =
        runTest {
            val gpsSignalState = MutableStateFlow(GpsSignalState.Ok)
            val (viewModel, _, controller) =
                viewModel(recordingStartResult = MutableStateFlow(RecordingStartResult.Ready))
            every { controller.gpsSignalState } returns gpsSignalState
            viewModel.onStartRecording("SWEEP-1")
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.warningPulses.test {
                gpsSignalState.value = GpsSignalState.Lost
                dispatcher.scheduler.advanceUntilIdle()
                assertEquals(Unit, awaitItem())
            }
        }

    @Test
    fun `warning pulse does not repeat while gps signal stays lost`() =
        runTest {
            val gpsSignalState = MutableStateFlow(GpsSignalState.Lost)
            val (viewModel, _, controller) =
                viewModel(recordingStartResult = MutableStateFlow(RecordingStartResult.Ready))
            every { controller.gpsSignalState } returns gpsSignalState
            viewModel.onStartRecording("SWEEP-1")
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.warningPulses.test {
                awaitItem() // the transition into Recording already picked up the lost signal once
                gpsSignalState.value = GpsSignalState.Lost // no real transition - same value again
                dispatcher.scheduler.advanceUntilIdle()
                expectNoEvents()
            }
        }
```

Add `import app.cash.turbine.test` (already used elsewhere in this project per CLAUDE.md's
testing stack).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: FAIL to compile — `warningPulses` does not exist.

- [ ] **Step 3: Implement**

Add a `SharedFlow` emitter and fold both warning sources through it, replacing the two `collect`
blocks for `gpsSignalState`/`bleGapDetected`-ish wiring in `init` (the existing
`captureController.gpsSignalState.collect { ... }` block and the `bleGapDetected` branch inside
the `luxClient.connectionState.collect { ... }` block) with versions that also pulse on a real
false→true transition:

```kotlin
        private val _warningPulses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val warningPulses: SharedFlow<Unit> = _warningPulses.asSharedFlow()
```

The `captureController.gpsSignalState.collect { state -> ... }` block today reads:

```kotlin
                captureController.gpsSignalState.collect { state ->
                    val current = _uiState.value
                    if (current is CaptureUiState.Recording) {
                        _uiState.value = current.copy(gpsSignalLost = state == GpsSignalState.Lost)
                    }
                }
```

Change its body to:

```kotlin
                captureController.gpsSignalState.collect { state ->
                    val current = _uiState.value
                    if (current is CaptureUiState.Recording) {
                        val lost = state == GpsSignalState.Lost
                        if (lost && !current.gpsSignalLost) _warningPulses.tryEmit(Unit)
                        _uiState.value = current.copy(gpsSignalLost = lost)
                    }
                }
```

The `luxClient.connectionState.collect { ... }` block today uses a `when (val current = _uiState.value)`
with an `is CaptureUiState.Recording ->` branch reading
`_uiState.value = current.copy(bleGapDetected = state == BleConnectionState.Disconnected)`. Change
that one branch to:

```kotlin
                        is CaptureUiState.Recording -> {
                            val gapDetected = state == BleConnectionState.Disconnected
                            if (gapDetected && !current.bleGapDetected) _warningPulses.tryEmit(Unit)
                            _uiState.value = current.copy(bleGapDetected = gapDetected)
                        }
```

Leave every other branch in that `when` (`is CaptureUiState.Connecting -> ...`, `else -> Unit`)
unchanged.

Add imports: `kotlinx.coroutines.flow.MutableSharedFlow`, `kotlinx.coroutines.flow.SharedFlow`,
`kotlinx.coroutines.flow.asSharedFlow`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.capture.CaptureViewModelTest"`
Expected: PASS.

- [ ] **Step 5: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureViewModel.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/capture/CaptureViewModelTest.kt
git commit -m "feat(fm-30): emit a warning pulse on gps/ble transitions, not every update"
```

### Task 12: Redesign `CaptureScreen`'s overlay UI

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt`

**Interfaces:**
- Consumes: `CaptureUiState.Recording`'s new fields (Task 10), `GpsAccuracyIndicator`/
  `gpsAccuracyLabel` (Task 9), `CaptureViewModel.warningPulses` (Task 11).

- [ ] **Step 1: Wrap the screen in forced Dark Mode**

Wrap the existing `Box(Modifier.fillMaxSize(), ...)` root (and everything inside it) in
`LuxMapTheme(forceDark = true) { ... }`.

- [ ] **Step 2: Add the top status strip for `Recording`**

Inside the `Box`, as a new sibling positioned above the existing bottom `Column`, add (only
rendered while `uiState is CaptureUiState.Recording`):

```kotlin
        if (uiState is CaptureUiState.Recording) {
            val state = uiState as CaptureUiState.Recording
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                        .padding(Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GpsAccuracyIndicator(accuracyMeters = state.gpsAccuracyMeters)
                Text(
                    text = state.latestLuxValue?.let { "%.1f lux".format(it) } ?: "Chưa có dữ liệu lux",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.freeStorageBytes?.let { "${it / 1_000_000} MB trống" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
```

- [ ] **Step 3: Add duration/distance/REC indicator and enlarge the stop button in the bottom panel**

In the existing `is CaptureUiState.Recording ->` branch inside the bottom `Column`'s `when`, add
duration/distance above the existing warnings and replace the plain `Button` with a larger one:

```kotlin
                is CaptureUiState.Recording -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("● REC", color = Danger600, style = MaterialTheme.typography.labelLarge)
                        Text(formatDuration(state.durationSeconds), style = MaterialTheme.typography.labelLarge)
                        Text("%.2f km".format(state.distanceMeters / 1000f), style = MaterialTheme.typography.labelLarge)
                    }
                    if (state.gpsSignalLost) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "Mất tín hiệu GPS",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    if (state.bleGapDetected) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                "Mất kết nối cảm biến ánh sáng — vẫn tiếp tục quay",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::onStopRecording,
                        modifier = Modifier.defaultMinSize(minHeight = 64.dp),
                    ) { Text("Dừng quay") }
                }
```

Add a private helper above `DeviceRow`:

```kotlin
private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
```

Add imports: `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.Arrangement`,
`androidx.compose.foundation.layout.defaultMinSize`, `androidx.compose.material.icons.Icons`,
`androidx.compose.material.icons.filled.WarningAmber`, `androidx.compose.material3.Icon`,
`androidx.compose.ui.unit.dp`, `com.luxmap.core.theme.Danger600`,
`com.luxmap.core.ui.components.GpsAccuracyIndicator`, `com.luxmap.core.theme.LuxMapTheme`.

- [ ] **Step 4: Trigger real vibration from `warningPulses`**

Add right after the existing `val context = LocalContext.current`:

```kotlin
    LaunchedEffect(Unit) {
        viewModel.warningPulses.collect {
            val vibrator = context.getSystemService(android.os.Vibrator::class.java)
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
```

- [ ] **Step 5: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: ktlint, run the full unit test suite, and commit**

Run: `./gradlew ktlintCheck` then `./gradlew :app:testDebugUnitTest`
Expected: both BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureScreen.kt
git commit -m "feat(fm-30): redesign capture overlay with live indicators and haptic warnings"
git push -u origin feat/fm-30-capture-mode-ui
```

Open a PR from `feat/fm-30-capture-mode-ui` into `dev`. This is the real-device-dependent part of
the plan — add the following to the existing real-device checklist before treating Phase 2 as
done: confirm the top strip does not overlap the existing preview controls at common screen
sizes, confirm haptic actually fires once per warning (not per recomposition) on a physical
device, confirm `freeBytes()`/lux/GPS values update visibly during a real recording.

---

## Phase 3 — F06 Nộp đợt khảo sát

### Task 13: `SessionSummary` and real-size uploads in `UploadRepository`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/FakeUploadRepositoryTest.kt`

**Interfaces:**
- Consumes: `SurveySessionDao.sessionById()`/`segmentsFor()` (existing).
- Produces: `UploadRepository.sessionSummary(sessionId: String): SessionSummary?`,
  `SessionSummary(durationSeconds: Long, distanceMeters: Double, totalBytes: Long)` — Task 15
  consumes both.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import com.luxmap.feature.survey.data.entity.LocalSurveyVideoSegmentEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant

class FakeUploadRepositoryTest {
    private fun session(sessionId: String) =
        LocalSurveySessionEntity(
            sessionId = sessionId,
            surveySweepId = "SWEEP-1",
            recordingState = "packaged",
            syncState = null,
            startedAtUtc = Instant.EPOCH,
            startedAtElapsedNs = 0,
            endedAtUtc = Instant.EPOCH.plusSeconds(600),
            durationSeconds = 600,
            distanceMeters = 2500.0,
            gpsTrackFilePath = null,
            luxLogFilePath = null,
            headingLogFilePath = null,
            frameTimestampLogFilePath = null,
            captureConfigFilePath = null,
            manifestFilePath = null,
            packageSchemaVersion = "v0",
            timestampSourceRealtime = true,
            bleGapDetected = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    @Test
    fun `session summary returns null for an unknown session`() =
        runTest {
            val dao = mockk<SurveySessionDao>()
            coEvery { dao.sessionById("missing") } returns null
            val repository = FakeUploadRepository(dao)

            assertNull(repository.sessionSummary("missing"))
        }

    @Test
    fun `session summary reports the real duration and distance from room`() =
        runTest {
            val dao = mockk<SurveySessionDao>()
            coEvery { dao.sessionById("SESSION-1") } returns session("SESSION-1")
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val repository = FakeUploadRepository(dao)

            val summary = repository.sessionSummary("SESSION-1")

            assertEquals(600L, summary?.durationSeconds)
            assertEquals(2500.0, summary?.distanceMeters)
        }

    @Test
    fun `session summary total bytes sums the real session files`() =
        runTest {
            val tempFile = File.createTempFile("gps_track", ".ndjson").apply { writeText("x".repeat(100)) }
            val dao = mockk<SurveySessionDao>()
            coEvery { dao.sessionById("SESSION-1") } returns
                session("SESSION-1").copy(gpsTrackFilePath = tempFile.absolutePath)
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val repository = FakeUploadRepository(dao)

            val summary = repository.sessionSummary("SESSION-1")

            assertEquals(100L, summary?.totalBytes)
            tempFile.delete()
        }

    @Test
    fun `upload total bytes matches the session summary, not a fixed constant`() =
        runTest {
            val tempFile = File.createTempFile("gps_track", ".ndjson").apply { writeText("x".repeat(100)) }
            val segmentFile = File.createTempFile("segment_0", ".mp4").apply { writeText("y".repeat(900)) }
            val dao = mockk<SurveySessionDao>()
            coEvery { dao.sessionById("SESSION-1") } returns
                session("SESSION-1").copy(gpsTrackFilePath = tempFile.absolutePath)
            coEvery { dao.segmentsFor("SESSION-1") } returns
                listOf(
                    LocalSurveyVideoSegmentEntity(
                        segmentId = "SEG-0",
                        sessionId = "SESSION-1",
                        segmentIndex = 0,
                        filePath = segmentFile.absolutePath,
                        startedAtElapsedNs = 0,
                        endedAtElapsedNs = 1_000_000_000L,
                        sizeBytes = null,
                        checksumSha256 = null,
                    ),
                )
            val repository = FakeUploadRepository(dao)

            var lastTotal = 0L
            repository.uploadSession("SESSION-1").collect { progress ->
                if (progress is UploadProgress.InProgress) lastTotal = progress.totalBytes
            }

            assertEquals(1_000L, lastTotal)
            tempFile.delete()
            segmentFile.delete()
        }

    @Test
    fun `upload does not divide by zero for a session with no files yet`() =
        runTest {
            val dao = mockk<SurveySessionDao>()
            coEvery { dao.sessionById("SESSION-1") } returns session("SESSION-1") // every path field null
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val repository = FakeUploadRepository(dao)

            val results = repository.uploadSession("SESSION-1").toList()

            // totalBytesFor() is 0 here (no files), coerced to 1 - this must finish, not hang or throw.
            assertEquals(UploadProgress.Done, results.last())
        }

    @Test
    fun `a fresh repository instance uploads a known session from zero after process death`() =
        runTest {
            // Simulates reopening the app: FakeUploadRepository.resumePoints is in-memory and does
            // not survive process death, so a brand new instance has no resume point for a session
            // that was previously interrupted in a different instance - it must restart from 0
            // without throwing, not silently skip or crash.
            val tempFile = File.createTempFile("gps_track", ".ndjson").apply { writeText("x".repeat(1_000)) }
            val dao = mockk<SurveySessionDao>()
            coEvery { dao.sessionById("SESSION-1") } returns
                session("SESSION-1").copy(gpsTrackFilePath = tempFile.absolutePath)
            coEvery { dao.segmentsFor("SESSION-1") } returns emptyList()
            val freshRepository = FakeUploadRepository(dao)

            val firstProgress = freshRepository.uploadSession("SESSION-1").first() as UploadProgress.InProgress

            // No resume point exists for this fresh instance, so the very first emission must be
            // somewhere between 0 (exclusive) and the full size (inclusive) - never negative, never
            // a value that implies it silently picked up a resume point it could not possibly have.
            assertTrue(firstProgress.bytesSent in 1..firstProgress.totalBytes)
            tempFile.delete()
        }
}
```

Add `import kotlinx.coroutines.flow.first` and `import kotlinx.coroutines.flow.toList` to this
test file's imports (used by the two new tests above).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeUploadRepositoryTest"`
Expected: FAIL to compile — `sessionSummary` does not exist, `FakeUploadRepository` has no `dao`
constructor param.

- [ ] **Step 3: Implement**

In `UploadRepository.kt`, add:

```kotlin
data class SessionSummary(val durationSeconds: Long, val distanceMeters: Double, val totalBytes: Long)

interface UploadRepository {
    suspend fun sessionSummary(sessionId: String): SessionSummary?

    fun uploadSession(sessionId: String): Flow<UploadProgress>
}
```

In `FakeUploadRepository.kt`, add `private val dao: SurveySessionDao` to the `@Inject constructor`,
add a shared file-size helper, and replace the hardcoded `totalBytes` with a per-session
computed value:

```kotlin
        private suspend fun totalBytesFor(sessionId: String): Long {
            val session = dao.sessionById(sessionId) ?: return 0L
            val segmentSizes = dao.segmentsFor(sessionId).sumOf { File(it.filePath).length() }
            val logSizes =
                listOfNotNull(
                    session.gpsTrackFilePath,
                    session.luxLogFilePath,
                    session.headingLogFilePath,
                    session.frameTimestampLogFilePath,
                    session.captureConfigFilePath,
                ).sumOf { File(it).length() }
            return segmentSizes + logSizes
        }

        override suspend fun sessionSummary(sessionId: String): SessionSummary? {
            val session = dao.sessionById(sessionId) ?: return null
            return SessionSummary(
                durationSeconds = session.durationSeconds ?: 0L,
                distanceMeters = session.distanceMeters ?: 0.0,
                totalBytes = totalBytesFor(sessionId),
            )
        }

        override fun uploadSession(sessionId: String): Flow<UploadProgress> =
            flow {
                val totalBytes = totalBytesFor(sessionId).coerceAtLeast(1L)
                var bytesSent = resumePoints[sessionId] ?: 0L
                while (bytesSent < totalBytes) {
                    delay(10)
                    bytesSent = (bytesSent + chunkBytes).coerceAtMost(totalBytes)
                    resumePoints[sessionId] = bytesSent
                    emit(UploadProgress.InProgress(bytesSent, totalBytes))
                }
                emit(UploadProgress.Done)
            }
```

Remove the now-unused `private val totalBytes = 50_000_000L` field. Keep `chunkBytes` and
`resumePoints` as they are. Add `import java.io.File` and
`import com.luxmap.feature.survey.data.dao.SurveySessionDao`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.data.FakeUploadRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Check for other `FakeUploadRepository()` call sites**

Run: `grep -rn "FakeUploadRepository(" app/src/main app/src/test app/src/androidTest`
Expected: only the Hilt `@Inject constructor` and this plan's new test file — if any manual
`FakeUploadRepository()` call exists elsewhere (for example in `RepositoryModule.kt`'s binding,
which uses `@Binds` and does not construct it directly, or in `SubmitViewModel`'s own tests if
any exist yet), it does not need a `dao` argument since Hilt provides it — only direct `new`-style
constructions in test code would need updating, and none currently do.

- [ ] **Step 6: ktlint and commit**

```bash
git checkout dev && git pull && git checkout -b feat/fm-12-submit-screen-ui
git add app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt \
  app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt \
  app/src/test/java/com/luxmap/feature/survey/data/FakeUploadRepositoryTest.kt
git commit -m "feat(fm-12): add session summary and real file sizes to upload repository"
```

### Task 14: Network type detection

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/network/ConnectivityObserver.kt`

**Interfaces:**
- Produces: `NetworkType` enum (`WIFI`, `MOBILE`, `OFFLINE`),
  `ConnectivityObserver.networkType: Flow<NetworkType>` — Task 16 consumes this.

No new test file: this task only adds a second derived `Flow` next to the existing untested
`isOnline` (a `callbackFlow` over `ConnectivityManager`, same real-device-only reasoning already
established for this file).

- [ ] **Step 1: Add the enum and the second flow**

```kotlin
enum class NetworkType { WIFI, MOBILE, OFFLINE }
```

Add next to `isOnline`, reusing the same `callbackFlow`/`NetworkCallback` registration by
emitting both from one combined flow instead of registering two separate callbacks:

```kotlin
        val networkType: Flow<NetworkType> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(currentNetworkType())
                        }

                        override fun onLost(network: Network) {
                            trySend(currentNetworkType())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(currentNetworkType())
                        }
                    }
                connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
                trySend(currentNetworkType())
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        private fun currentNetworkType(): NetworkType {
            val network = connectivityManager.activeNetwork ?: return NetworkType.OFFLINE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.OFFLINE
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return NetworkType.OFFLINE
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                else -> NetworkType.OFFLINE
            }
        }
```

This registers a second `NetworkCallback` independent of `isOnline`'s — acceptable duplication
(both are cheap system registrations) rather than refactoring `isOnline` itself, which F12/FM-38
already depends on and this plan must not touch.

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/core/network/ConnectivityObserver.kt
git commit -m "feat(fm-12): add network type detection alongside the existing online check"
```

### Task 15: `SubmitUiState`/`SubmitViewModel` — summary, network type, pause/resume

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt`
- Create: `app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt`

**Interfaces:**
- Consumes: `UploadRepository.sessionSummary()` (Task 13), `ConnectivityObserver.networkType`
  (Task 14).
- Produces: the final `SubmitUiState` shape and `SubmitViewModel.onPause()`/`onResume()`/
  `onRetry()` — Task 16 (the screen) consumes all of these.

- [ ] **Step 1: Rewrite `SubmitUiState.kt`**

```kotlin
package com.luxmap.feature.survey.ui.submit

import com.luxmap.core.network.NetworkType
import com.luxmap.feature.survey.data.SessionSummary

sealed interface SubmitUiState {
    data class Idle(val summary: SessionSummary, val networkType: NetworkType) : SubmitUiState

    data class Uploading(
        val summary: SessionSummary,
        val bytesSent: Long,
        val totalBytes: Long,
        val networkType: NetworkType,
    ) : SubmitUiState

    data class Paused(val summary: SessionSummary, val bytesSent: Long, val totalBytes: Long) : SubmitUiState

    data object Done : SubmitUiState

    data class Error(val message: String, val bytesSent: Long, val totalBytes: Long) : SubmitUiState
}
```

- [ ] **Step 2: Write the failing tests**

```kotlin
package com.luxmap.feature.survey.ui.submit

import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.network.NetworkType
import com.luxmap.feature.survey.data.SessionSummary
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val summary = SessionSummary(durationSeconds = 600, distanceMeters = 2500.0, totalBytes = 1_000L)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: UploadRepository = mockk(),
        connectivity: ConnectivityObserver = mockk(),
    ): SubmitViewModel {
        coEvery { repository.sessionSummary(any()) } returns summary
        every { connectivity.networkType } returns flowOf(NetworkType.WIFI)
        return SubmitViewModel(repository, connectivity)
    }

    @Test
    fun `idle state loads the real session summary and network type`() =
        runTest {
            val vm = viewModel()
            vm.onScreenEntered("SESSION-1")
            dispatcher.scheduler.advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is SubmitUiState.Idle)
            assertEquals(summary, (state as SubmitUiState.Idle).summary)
            assertEquals(NetworkType.WIFI, state.networkType)
        }

    @Test
    fun `pausing an upload keeps the last known bytes sent`() =
        runTest {
            val repository = mockk<UploadRepository>()
            coEvery { repository.sessionSummary(any()) } returns summary
            every { repository.uploadSession("SESSION-1") } returns
                flow {
                    emit(UploadProgress.InProgress(300, 1_000))
                    kotlinx.coroutines.delay(100_000) // never completes on its own - onPause must cancel it
                }
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.networkType } returns flowOf(NetworkType.WIFI)
            val vm = SubmitViewModel(repository, connectivity)
            vm.onScreenEntered("SESSION-1")
            dispatcher.scheduler.advanceUntilIdle()
            vm.onSubmit("SESSION-1")
            dispatcher.scheduler.advanceUntilIdle()

            vm.onPause()

            val state = vm.uiState.value
            assertTrue(state is SubmitUiState.Paused)
            assertEquals(300L, (state as SubmitUiState.Paused).bytesSent)
        }

    @Test
    fun `resuming after a pause calls uploadSession again for the same session`() =
        runTest {
            val repository = mockk<UploadRepository>()
            coEvery { repository.sessionSummary(any()) } returns summary
            every { repository.uploadSession("SESSION-1") } returnsMany
                listOf(
                    flow {
                        emit(UploadProgress.InProgress(300, 1_000))
                        kotlinx.coroutines.delay(100_000) // never completes on its own - onPause must cancel it
                    },
                    flowOf(UploadProgress.Done),
                )
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.networkType } returns flowOf(NetworkType.WIFI)
            val vm = SubmitViewModel(repository, connectivity)
            vm.onScreenEntered("SESSION-1")
            dispatcher.scheduler.advanceUntilIdle()
            vm.onSubmit("SESSION-1")
            dispatcher.scheduler.advanceUntilIdle()
            vm.onPause()

            vm.onResume("SESSION-1")
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(SubmitUiState.Done, vm.uiState.value)
        }
}
```

`onResume()` only acts when the current state is `SubmitUiState.Paused` (it is a no-op from
`Idle`), so this test drives a real `onSubmit` → `onPause` → `onResume` sequence rather than
calling `onResume` straight from `Idle`. `every { ... } returnsMany listOf(...)` makes the first
`uploadSession("SESSION-1")` call (from `onSubmit`) return the never-completing flow `onPause`
cancels, and the second call (from `onResume`) return the flow that completes — this is how MockK
makes two calls to the same stubbed function return different flows in sequence. Add
`import kotlinx.coroutines.flow.flow` and `import io.mockk.returnsMany` to this test file's
imports.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: FAIL to compile — `SubmitViewModel` has no `ConnectivityObserver` param, no
`onScreenEntered`/`onPause`/`onResume` methods yet.

- [ ] **Step 4: Implement `SubmitViewModel`**

```kotlin
package com.luxmap.feature.survey.ui.submit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.survey.data.SessionSummary
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
        private val connectivity: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(
            SubmitUiState.Idle(SessionSummary(0, 0.0, 0), com.luxmap.core.network.NetworkType.OFFLINE),
        )
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        private var uploadJob: Job? = null

        fun onScreenEntered(sessionId: String) {
            viewModelScope.launch {
                val summary = repository.sessionSummary(sessionId) ?: return@launch
                val networkType = connectivity.networkType.first()
                _uiState.value = SubmitUiState.Idle(summary, networkType)
            }
        }

        fun onSubmit(sessionId: String) {
            val summary = currentSummary() ?: return
            uploadJob =
                viewModelScope.launch {
                    repository
                        .uploadSession(sessionId)
                        .catch { e ->
                            val bytesSent = (uiState.value as? SubmitUiState.Uploading)?.bytesSent ?: 0L
                            val totalBytes = (uiState.value as? SubmitUiState.Uploading)?.totalBytes ?: summary.totalBytes
                            _uiState.value = SubmitUiState.Error(e.message ?: "Tải lên thất bại", bytesSent, totalBytes)
                        }.collect { progress ->
                            _uiState.value =
                                when (progress) {
                                    is UploadProgress.InProgress ->
                                        SubmitUiState.Uploading(
                                            summary,
                                            progress.bytesSent,
                                            progress.totalBytes,
                                            connectivity.networkType.first(),
                                        )
                                    is UploadProgress.Done -> SubmitUiState.Done
                                    is UploadProgress.Failed ->
                                        SubmitUiState.Error(progress.reason, 0L, summary.totalBytes)
                                }
                        }
                }
        }

        fun onPause() {
            val state = uiState.value as? SubmitUiState.Uploading ?: return
            uploadJob?.cancel()
            _uiState.value = SubmitUiState.Paused(state.summary, state.bytesSent, state.totalBytes)
        }

        fun onResume(sessionId: String) {
            if (uiState.value !is SubmitUiState.Paused) return
            onSubmit(sessionId)
        }

        fun onRetry(sessionId: String) = onSubmit(sessionId)

        private fun currentSummary(): SessionSummary? =
            when (val state = uiState.value) {
                is SubmitUiState.Idle -> state.summary
                is SubmitUiState.Uploading -> state.summary
                is SubmitUiState.Paused -> state.summary
                else -> null
            }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: PASS.

- [ ] **Step 6: ktlint and commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt \
  app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt \
  app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt
git commit -m "feat(fm-12): add session summary, network type and pause/resume to submit state"
```

### Task 16: Wire `SubmitScreen` to the real design

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt`

**Interfaces:**
- Consumes: the final `SubmitUiState` shape (Task 15).

- [ ] **Step 1: Call `onScreenEntered` once and rewrite the `when`**

```kotlin
@Composable
fun SubmitScreen(
    sessionId: String,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(sessionId) { viewModel.onScreenEntered(sessionId) }

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        when (val state = uiState) {
            is SubmitUiState.Idle -> {
                SessionSummaryCard(state.summary)
                NetworkTypeLabel(state.networkType)
                Spacer(Modifier.height(Spacing.md))
                PrimaryButton(text = "Nộp ngay", onClick = { viewModel.onSubmit(sessionId) })
            }

            is SubmitUiState.Uploading -> {
                SessionSummaryCard(state.summary)
                NetworkTypeLabel(state.networkType)
                val fraction = state.bytesSent.toFloat() / state.totalBytes.toFloat()
                LinearProgressIndicator(progress = { fraction })
                Text("${state.bytesSent} / ${state.totalBytes} bytes")
                PrimaryButton(text = "Tạm dừng", onClick = viewModel::onPause)
            }

            is SubmitUiState.Paused -> {
                SessionSummaryCard(state.summary)
                Text("Đã tạm dừng — ${state.bytesSent} / ${state.totalBytes} bytes")
                PrimaryButton(text = "Tiếp tục", onClick = { viewModel.onResume(sessionId) })
            }

            is SubmitUiState.Done -> Text("Đã nộp thành công", style = MaterialTheme.typography.bodyLarge)

            is SubmitUiState.Error -> {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                PrimaryButton(text = "Thử lại", onClick = { viewModel.onRetry(sessionId) })
            }
        }
    }
}

@Composable
private fun SessionSummaryCard(summary: com.luxmap.feature.survey.data.SessionSummary) {
    Column {
        Text(
            text = "Thời lượng: %02d:%02d".format(summary.durationSeconds / 60, summary.durationSeconds % 60),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Quãng đường: %.2f km".format(summary.distanceMeters / 1000.0),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Dung lượng: ${summary.totalBytes / 1_000_000} MB",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NetworkTypeLabel(networkType: com.luxmap.core.network.NetworkType) {
    val label =
        when (networkType) {
            com.luxmap.core.network.NetworkType.WIFI -> "Đang dùng Wi-Fi"
            com.luxmap.core.network.NetworkType.MOBILE -> "Đang dùng dữ liệu di động"
            com.luxmap.core.network.NetworkType.OFFLINE -> "Không có mạng"
        }
    Text(text = label, style = MaterialTheme.typography.bodySmall)
}
```

Add imports: `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height`,
`androidx.compose.foundation.layout.padding`, `androidx.compose.runtime.LaunchedEffect`,
`com.luxmap.core.theme.Spacing`, `com.luxmap.core.ui.components.PrimaryButton`.

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: ktlint, run the full unit test suite, and commit**

Run: `./gradlew ktlintCheck` then `./gradlew :app:testDebugUnitTest`
Expected: both BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt
git commit -m "feat(fm-12): wire submit screen to session summary and pause/resume"
git push -u origin feat/fm-12-submit-screen-ui
```

Open a PR from `feat/fm-12-submit-screen-ui` into `dev`.

---

## After all 3 phases

- [ ] Update `docs/superpowers/specs/2026-10-01-survey-ui-design.md`'s out-of-scope section with
  a link to this plan once all 3 PRs are merged, so a future reader finds the implementation
  from the spec.
- [ ] Add a `contract-drift.md` row noting F03's map and F06's queue button are still missing
  against the full spec text, pointing at this plan as the reason (already called out in the
  spec's §1, this just makes it discoverable from the drift log too).
