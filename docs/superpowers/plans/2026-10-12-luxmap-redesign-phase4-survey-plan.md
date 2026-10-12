# LuxMap Redesign Phase 4: F03 Chuẩn Bị Khảo Sát Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `SurveyPlanScreen` (F03, reached from the Hub's "Kiểm tra sẵn sàng" button at `survey/plan/{surveySweepId}`) to the v2.1 redesign: a new shared `BottomActionBar`, a 7th checklist row for Bluetooth permission, a staggered reveal animation on the checklist, the shared `MessageState` for Empty/Error, and a UI-only/flag-gated "Khảo sát lại" (resurvey-rejected) banner. Also closes the one real behavioral gap left over from Phase 3: the `surveySweepId` nav argument is wired to auto-select that route instead of sitting unused.

**Architecture:** One new shared component (`BottomActionBar`, first real consumer — same pattern as Phase 1's component-ownership rule), visual/structural changes inside `SurveyPlanScreen.kt`, one small `NavGraph.kt` wire-up to pass the real `surveySweepId` through, and one small, additive `SurveyPlanUiState`/`SurveyPlanViewModel` change (an optional `rejectionReason` field, always `null` while its flag is off). `CheckSurveyReadinessUseCase` and `SurveyReadinessInputProvider` are not touched.

**Tech Stack:** Kotlin, Jetpack Compose Material3. No new dependencies.

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (Phase 4 row) and `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` lines 132 (`BottomActionBar` component row) and 183-188 (F03 section). Current-code findings this plan is built from: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`/`SurveyPlanViewModel.kt`/`SurveyPlanUiState.kt`, `app/src/main/java/com/luxmap/feature/survey/domain/usecase/CheckSurveyReadinessUseCase.kt`, `app/src/main/java/com/luxmap/core/ui/components/CameraReadinessPanel.kt`, `app/src/main/java/com/luxmap/navigation/NavGraph.kt` (all read in full during this planning session), plus the `survey/plan/{surveySweepId}` route's known gap logged in `docs/contract-drift.md` (added by Phase 3): "`SurveyPlanScreen` still shows every assigned route and lets the user (re-)pick one... Phase 4 is where this screen is expected to start using the id, e.g. to preselect or filter to that one route."

## Global Constraints

- Branch off `dev` (`git checkout dev && git pull`), never `main`. Branch: `style/redesign-phase4-survey-plan`.
- Every commit ≤ 400 changed lines (CLAUDE.md). Expect one commit per task (5 total).
- `./gradlew ktlintCheck` after every task, fix before moving on.
- Comments only where the WHY is genuinely non-obvious, matching established codebase style (CLAUDE.md's comment rule).
- `CheckSurveyReadinessUseCase`, `SurveyReadinessInput`, `SurveyReadinessResult`, and `SurveyReadinessInputProvider` are **not touched** — every change in this plan is in the UI/ViewModel presentation layer, never the readiness decision itself.
- The "Khảo sát lại" banner's rejection reason is a **local-only, flag-gated** concept — no real "rejected sweep" status exists in `SurveySweepStatus`/`AssignedSurveyRoute` (same unconfirmed-schema gap as the Hub's "Bị từ chối" card from Phase 3). This plan defines its **own** local flag in the `ui/plan` package (not shared with the Hub's `SHOW_UNCONFIRMED_HUB_CARDS`, which lives on an unmerged branch as of this plan — sharing a flag across packages mid-flight risks a merge conflict with that other branch) — log the duplication in `docs/contract-drift.md` as a follow-up unification item.
- `BottomActionBar` has zero other consumers today — build it only for what this phase needs (a hint + one CTA), do not add props for hypothetical future screens.

## Review Focus

- **Bluetooth row stays presentation-only.** The screen already gates the "Vào chế độ khảo sát" button on `hasBlePermissions` today (a local Composable `remember`, not part of `SurveyReadinessResult`) — adding a 7th checklist row for it must read that same existing local state, not invent a new field on the readiness domain model. A test must confirm the CTA's enabled/disabled condition is unchanged (`readiness.isReady && hasBlePermissions`, same as before this plan).
- **Auto-selecting by `surveySweepId` must not break manual selection.** Once the nav argument auto-triggers `onRouteSelected`, a user who taps a *different* route card afterward must still be able to switch — the auto-select is a one-time initial action, not a lock. A test must cover both: arriving with the id auto-selects, and a later manual tap on another route still updates the selection.
- **The resurvey banner defaults to invisible and provably so.** Same bar as Phase 3's Hub cards: with the local flag off (the shipped default), `SurveyPlanUiState.Success.rejectionReason` must always be `null` — a test must assert this regardless of which route is loaded.
- **Staggered reveal must not block the readiness check itself.** The checklist animation (300-500ms per row) is purely visual; `CheckSurveyReadinessUseCase`'s result must already be in `SurveyReadinessResult` the moment the animation starts — the animation must not gate *when* the use case runs, only how its already-known result is revealed row by row. A test must confirm all items are present in the state immediately, independent of animation timing.
- **Empty/Error states keep their exact existing copy.** Swapping the inline `Box` blocks for the shared `MessageState` must not change the user-facing strings ("Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì", the error message, "Thử lại") — a test must assert the same text nodes still exist after the swap.

---

### Task 1: Add the shared `BottomActionBar` component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/BottomActionBar.kt`

**Interfaces:**
- Consumes: `Spacing.md`/`Spacing.lg`, `ScreenMargin` (20.dp, from Phase 0's `Dimens.kt`), `PrimaryButton` (existing).
- Produces: `BottomActionBar(ctaText: String, onCtaClick: () -> Unit, ctaEnabled: Boolean, hint: String? = null, modifier: Modifier = Modifier)` — consumed by Task 2.

- [ ] **Step 1: Create the file**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.luxmap.core.theme.ScreenMargin
import com.luxmap.core.theme.Spacing

// §3 BottomActionBar: a bottom-pinned hint + one primary CTA, used by screens where the main
// action can be locked and the reason must stay visible right above the button, not as a toast.
@Composable
fun BottomActionBar(
    ctaText: String,
    onCtaClick: () -> Unit,
    ctaEnabled: Boolean,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md, start = ScreenMargin, end = ScreenMargin, bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(bottom = Spacing.sm)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                )
            }
            PrimaryButton(
                text = ctaText,
                onClick = onCtaClick,
                enabled = ctaEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/BottomActionBar.kt
git commit -m "feat(redesign-phase4): add BottomActionBar shared component"
```

---

### Task 2: Wire the real `surveySweepId`, add the Bluetooth checklist row, and the staggered reveal

**Files:**
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt` (the `Routes.SurveyPlan` destination, added in Phase 3)
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt`

**Interfaces:**
- Consumes: `BottomActionBar` (Task 1), existing `SurveyPlanViewModel.onRouteSelected(surveySweepId: String)` (unchanged signature).
- Produces: `SurveyPlanScreen(surveySweepId: String, onEnterCaptureMode: (String) -> Unit, viewModel: SurveyPlanViewModel = hiltViewModel())` — the `surveySweepId` parameter is new; `NavGraph.kt`'s one call site is updated in this task.

- [ ] **Step 1: Write the failing test — Bluetooth row renders and the CTA condition is unchanged**

Add to `SurveyPlanScreenTest.kt` (the file already has a `route()` helper and `createComposeRule`). First add this helper next to `route()`:

```kotlin
    private fun readyResult() =
        SurveyReadinessResult(
            cameraPermissionGranted = true,
            exposureLockSupported = true,
            timestampSourceRealtime = true,
            gpsAvailable = true,
            freeStorageBytes = 2_000_000_000L,
            requiredStorageBytes = 1_000_000_000L,
            batteryPercent = 80,
        )
```

Add import: `com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult`. Then add the 2 new tests:

```kotlin
    @Test
    fun success_state_shows_the_bluetooth_permission_row() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state =
                        SurveyPlanUiState.Success(
                            routes = listOf(route()),
                            selectedSurveySweepId = route().surveySweepId,
                            readiness = readyResult(),
                        ),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Quyền Bluetooth").assertExists()
    }

    @Test
    fun arriving_with_a_surveySweepId_auto_selects_that_route() {
        var selected: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanScreenContent(
                    surveySweepId = "SWEEP-1",
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = { selected = it },
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("SWEEP-1", selected)
    }
```

Add import: `org.junit.Assert.assertEquals`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` (compile check only — no device in this environment, same as every prior phase)
Expected: FAIL — `SurveyPlanScreenContent` does not exist yet, and the current screen has no "Quyền Bluetooth" text.

- [ ] **Step 3: Update `NavGraph.kt`'s `Routes.SurveyPlan` destination to read and pass the real id**

Replace (current, from Phase 3):

```kotlin
            composable(
                route = Routes.SurveyPlan.route,
                arguments = listOf(navArgument("surveySweepId") { type = NavType.StringType }),
            ) {
                SurveyPlanScreen(
                    onEnterCaptureMode = { surveySweepId ->
```

with:

```kotlin
            composable(
                route = Routes.SurveyPlan.route,
                arguments = listOf(navArgument("surveySweepId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val planSurveySweepId =
                    backStackEntry.arguments?.getString("surveySweepId") ?: return@composable
                SurveyPlanScreen(
                    surveySweepId = planSurveySweepId,
                    onEnterCaptureMode = { surveySweepId ->
```

(The closing braces/args below this line are unchanged — only the opening of this one `composable` block and the `SurveyPlanScreen(...)` call's first line change.)

- [ ] **Step 4: Restructure `SurveyPlanScreen.kt`**

This step does 4 things in the same file: (a) split the stateful entry into `SurveyPlanScreen` (takes `surveySweepId`, owns the `LaunchedEffect` auto-select) + a new `SurveyPlanScreenContent` (stateless wrapper around the existing `SurveyPlanContent`, taking `surveySweepId` + `state` + `onRouteSelected` so the auto-select effect is directly testable without a ViewModel — this is what Task 2's second test calls); (b) add the Bluetooth row to `ReadinessChecklist`; (c) replace the flat `CameraReadinessPanel` call with a staggered reveal; (d) swap the bottom buttons for `BottomActionBar`.

Replace the file's `SurveyPlanScreen` function and the lines immediately after it:

```kotlin
@Composable
fun SurveyPlanScreen(
    surveySweepId: String,
    onEnterCaptureMode: (surveySweepId: String) -> Unit,
    viewModel: SurveyPlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LuxMapTheme(forceDark = true) {
        SurveyPlanScreenContent(
            surveySweepId = surveySweepId,
            state = uiState,
            onRouteSelected = viewModel::onRouteSelected,
            onRetry = viewModel::onRetry,
            onEnterCaptureMode = onEnterCaptureMode,
        )
    }
}

// Separated from SurveyPlanScreen only so the auto-select effect below can be driven directly in
// a Compose UI test, with no ViewModel/Hilt in the loop - same reason SurveyPlanContent itself is
// already stateless.
@Composable
fun SurveyPlanScreenContent(
    surveySweepId: String,
    state: SurveyPlanUiState,
    onRouteSelected: (String) -> Unit,
    onRetry: () -> Unit = {},
    onEnterCaptureMode: (String) -> Unit = {},
) {
    LaunchedEffect(surveySweepId, state is SurveyPlanUiState.Success) {
        if (state is SurveyPlanUiState.Success && state.selectedSurveySweepId == null) {
            onRouteSelected(surveySweepId)
        }
    }
    SurveyPlanContent(
        state = state,
        onRouteSelected = onRouteSelected,
        onRetry = onRetry,
        onEnterCaptureMode = onEnterCaptureMode,
    )
}
```

Add the `LaunchedEffect` import: `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 5: Add the Bluetooth row and the staggered reveal**

Replace `ReadinessChecklist`'s body (currently a flat list passed straight to `CameraReadinessPanel`) with:

```kotlin
@Composable
private fun ReadinessChecklist(
    readiness: SurveyReadinessResult,
    hasBlePermissions: Boolean,
) {
    val items =
        listOf(
            ReadinessCheckItem("Quyền camera", readiness.cameraPermissionGranted.toStatus()),
            ReadinessCheckItem("Khoá exposure hỗ trợ", readiness.exposureLockSupported.toStatus()),
            ReadinessCheckItem(
                "Đồng hồ cảm biến REALTIME",
                readiness.timestampSourceRealtime.toStatus(),
                fixHint =
                    if (!readiness.timestampSourceRealtime) {
                        "Thiết bị này không được hỗ trợ khảo sát (đồng hồ cảm biến camera không đạt yêu cầu)"
                    } else {
                        null
                    },
            ),
            ReadinessCheckItem("GPS", readiness.gpsAvailable.toStatus()),
            ReadinessCheckItem("Quyền Bluetooth", hasBlePermissions.toStatus(), fixHint = if (!hasBlePermissions) "Cần quyền Bluetooth để kết nối cảm biến lux" else null),
            ReadinessCheckItem(
                "Đủ dung lượng trống",
                (readiness.freeStorageBytes >= readiness.requiredStorageBytes).toStatus(),
            ),
            ReadinessCheckItem(
                "Pin đủ (>= 20%)",
                (readiness.batteryPercent >= SurveyReadinessResult.MIN_BATTERY_PERCENT).toStatus(),
            ),
        )
    var visibleCount by remember(items) { mutableStateOf(0) }
    LaunchedEffect(items) {
        visibleCount = 0
        items.indices.forEach { index ->
            visibleCount = index + 1
            delay(CHECKLIST_ROW_DELAY_MS)
        }
    }
    Column {
        items.take(visibleCount).forEach { item ->
            AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = tween(CHECKLIST_ROW_FADE_MS))) {
                CameraReadinessPanel(listOf(item))
            }
        }
    }
}

private const val CHECKLIST_ROW_DELAY_MS = 350L
private const val CHECKLIST_ROW_FADE_MS = 400
```

(`CameraReadinessPanel` already accepts a `List<ReadinessCheckItem>` and renders each with its own `Spacer` below — calling it once per revealed row, rather than passing the whole list at once, is what makes each row fade in on its own schedule; `CameraReadinessPanel`'s own code is untouched.)

Update the one call site inside `SurveyRouteCard` from `ReadinessChecklist(readiness)` to `ReadinessChecklist(readiness, hasBlePermissions)`.

Add imports: `androidx.compose.animation.AnimatedVisibility`, `androidx.compose.animation.core.tween`, `androidx.compose.animation.fadeIn`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember`, `androidx.compose.runtime.setValue` (if not already present — `getValue`/`setValue`/`remember`/`mutableStateOf` are already imported in this file for `hasBlePermissions`), `kotlinx.coroutines.delay`.

- [ ] **Step 6: Replace the bottom buttons with `BottomActionBar`**

In `SurveyRouteCard`, replace the trailing `PrimaryButton` calls (the "Cấp quyền" + "Vào chế độ khảo sát" pair) with:

```kotlin
            val blockedHint =
                if (!readiness.isReady || !hasBlePermissions) {
                    "Còn mục chưa đạt — chưa thể khảo sát"
                } else {
                    null
                }
            if (!readiness.cameraPermissionGranted || !readiness.gpsAvailable || !hasBlePermissions) {
                PrimaryButton(text = "Cấp quyền", onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth())
            }
            BottomActionBar(
                ctaText = "Vào chế độ khảo sát",
                onCtaClick = onEnterCaptureMode,
                ctaEnabled = readiness.isReady && hasBlePermissions,
                hint = blockedHint,
            )
```

(`BottomActionBar` is bottom-pinned by its own spec, but this call site keeps it inline inside the card's `Column`, same as the buttons it replaces — this screen has no `Scaffold(bottomBar = …)` of its own yet, and adding one is a larger structural change the plan does not ask for. The hint copy matches the spec's exact string, just without the dynamic `n` count — `SurveyReadinessResult` has no per-item failure count to interpolate without changing the domain model, which the Global Constraints forbid.)

Add import: `com.luxmap.core.ui.components.BottomActionBar`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL (compile-verified; no device to execute on, same as every prior phase — note this in the final task).

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt
git commit -m "feat(redesign-phase4): wire surveySweepId auto-select, add Bluetooth row and staggered reveal"
```

---

### Task 3: Replace inline Empty/Error with `MessageState`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt`

**Interfaces:**
- Consumes: `MessageState` (`BoxScope` extension, Phase 1), `SyncStatus.badgeColors()`.

- [ ] **Step 1: Run the existing Empty/Error tests to confirm today's exact copy (baseline)**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` and read `SurveyPlanScreenTest.kt`'s `empty_state_shows_the_no_routes_message`/`error_state_shows_the_message_and_a_retry_action` — these already assert the exact strings this task must keep unchanged. No new test needed for Step 1; Step 4 re-runs these same two as the verification.

- [ ] **Step 2: Replace the inline blocks**

In `SurveyPlanContent`, replace:

```kotlin
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
```

with:

```kotlin
            is SurveyPlanUiState.Empty ->
                Box(Modifier.fillMaxSize()) {
                    MessageState(
                        icon = Icons.Filled.EventAvailable,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Chưa có tuyến nào được phân công — liên hệ Kỹ sư bảo trì",
                        subtitle = "Khi có tuyến mới được giao, danh sách sẽ hiển thị tại đây.",
                        action = {},
                    )
                }

            is SurveyPlanUiState.Error ->
                Box(Modifier.fillMaxSize()) {
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = state.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                    ) {
                        PrimaryButton(text = "Thử lại", onClick = onRetry, modifier = Modifier.fillMaxWidth())
                    }
                }
```

Add imports: `androidx.compose.material.icons.filled.CloudOff`, `androidx.compose.material.icons.filled.EventAvailable`, `com.luxmap.core.theme.SyncStatus`, `com.luxmap.core.theme.badgeColors`, `com.luxmap.core.ui.components.MessageState`. Remove now-unused imports if ktlint/compiler flags them (`androidx.compose.ui.text.style.TextAlign` is still used by `BottomActionBar`'s own file, not this one — check whether `SurveyPlanScreen.kt` still needs it anywhere else before removing; `Spacer`/`Column`'s direct uses in this block are gone, but both are still used elsewhere in the same file for other layouts — do not remove those imports).

- [ ] **Step 3: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Re-verify the baseline tests still describe the same copy**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL — `empty_state_shows_the_no_routes_message` and `error_state_shows_the_message_and_a_retry_action` still compile against the same text assertions from before this task (no test file changes needed in this task; they are the proof this task didn't change user-facing copy).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt
git commit -m "style(redesign-phase4): replace inline Empty/Error with shared MessageState"
```

---

### Task 4: Add the UI-only, flag-gated "Khảo sát lại" banner

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt`

**Interfaces:**
- Produces: `SurveyPlanUiState.Success.rejectionReason: String? = null` — consumed by `SurveyPlanContent` in this same task.

- [ ] **Step 1: Write the failing ViewModel test**

Add to `SurveyPlanViewModelTest.kt`:

```kotlin
    @Test
    fun `rejectionReason is always null while SHOW_RESURVEY_BANNER is off`() =
        runTest {
            val repository = mockk<SurveyRepository>()
            every { repository.observeAssignedRoutes() } returns flowOf(listOf(route))
            val viewModel = SurveyPlanViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))

            viewModel.uiState.test {
                awaitItem() // Loading
                val success = awaitItem() as SurveyPlanUiState.Success
                assertEquals(null, success.rejectionReason)
            }
        }
```

(Needs `kotlinx.coroutines.flow.flowOf` imported if not already — it already is, from the other tests in this file.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanViewModelTest"`
Expected: FAIL to compile — `SurveyPlanUiState.Success` has no `rejectionReason` property yet.

- [ ] **Step 3: Add the field and the flag**

In `SurveyPlanUiState.kt`, add the field to `Success`:

```kotlin
    data class Success(
        val routes: List<AssignedSurveyRoute>,
        val selectedSurveySweepId: String? = null,
        val readiness: SurveyReadinessResult? = null,
        val rejectionReason: String? = null,
    ) : SurveyPlanUiState
```

In `SurveyPlanViewModel.kt`, add near the top of the file (outside the class):

```kotlin
// UI-only, same unconfirmed-sweep-status gap as the Hub's rejected-sweep card (Phase 3) - no
// real "rejected" status exists on SurveySweepStatus yet. Kept as this package's own flag rather
// than sharing SurveyHubViewModel's SHOW_UNCONFIRMED_HUB_CARDS (see docs/contract-drift.md).
internal const val SHOW_RESURVEY_BANNER = false
```

and change `loadRoutes()`'s success branch from:

```kotlin
                    .collect { routes ->
                        _uiState.value =
                            if (routes.isEmpty()) SurveyPlanUiState.Empty else SurveyPlanUiState.Success(routes)
                    }
```

to:

```kotlin
                    .collect { routes ->
                        _uiState.value =
                            if (routes.isEmpty()) {
                                SurveyPlanUiState.Empty
                            } else {
                                SurveyPlanUiState.Success(
                                    routes = routes,
                                    rejectionReason = if (SHOW_RESURVEY_BANNER) FAKE_REJECTION_REASON else null,
                                )
                            }
                    }
```

and add, alongside the flag:

```kotlin
private const val FAKE_REJECTION_REASON = "Video bị rung mạnh ở đoạn đầu, không đọc được cột đèn"
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.SurveyPlanViewModelTest"`
Expected: PASS (5 tests total in this file).

- [ ] **Step 5: Render the banner in the Screen**

In `SurveyPlanContent`'s `Success` branch, before the `LazyColumn` of route cards, add:

```kotlin
            is SurveyPlanUiState.Success -> {
                if (state.rejectionReason != null) {
                    InlineBanner(message = state.rejectionReason, tone = InlineBannerTone.ERROR)
                }
                // ... existing permissionLauncher + LazyColumn code below, unchanged
```

Add imports: `com.luxmap.core.ui.components.InlineBanner`, `com.luxmap.core.ui.components.InlineBannerTone`.

- [ ] **Step 6: Write the failing Screen test**

Add to `SurveyPlanScreenTest.kt`:

```kotlin
    @Test
    fun a_rejection_reason_shows_the_resurvey_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route()), rejectionReason = "Video bị mờ"),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Video bị mờ").assertExists()
    }

    @Test
    fun no_rejection_reason_means_no_banner() {
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanContent(
                    state = SurveyPlanUiState.Success(routes = listOf(route())),
                    onRouteSelected = {},
                )
            }
        }
        composeRule.onNodeWithText("Video bị mờ").assertDoesNotExist()
    }
```

- [ ] **Step 7: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanUiState.kt app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModel.kt app/src/main/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreen.kt app/src/test/java/com/luxmap/feature/survey/ui/plan/SurveyPlanViewModelTest.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt
git commit -m "feat(redesign-phase4): add UI-only, flag-gated resurvey banner"
```

---

### Task 5: Manual-selection regression test, contract-drift log, final build, open PR

**Files:**
- Modify: `app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt`
- Modify: `docs/contract-drift.md`

- [ ] **Step 1: Write the failing test for the Review Focus item on manual override**

```kotlin
    @Test
    fun a_manual_tap_on_a_different_route_overrides_the_auto_selected_one() {
        val routeA = route()
        val routeB = route().copy(surveySweepId = "SWEEP-2")
        var selected: String? = null
        composeRule.setContent {
            LuxMapTheme {
                SurveyPlanScreenContent(
                    surveySweepId = "SWEEP-1",
                    state = SurveyPlanUiState.Success(routes = listOf(routeA, routeB), selectedSurveySweepId = "SWEEP-1"),
                    onRouteSelected = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("SWEEP-2").performClick()
        assertEquals("SWEEP-2", selected)
    }
```

Add imports: `androidx.compose.ui.test.performClick`, `org.junit.Assert.assertEquals`.

- [ ] **Step 2: Run to verify it fails, then passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected (before any code change needed): BUILD SUCCESSFUL — this test exercises only existing `onClick = { onRouteSelected(route.surveySweepId) }` wiring in `SurveyRouteCard`, already correct from before this plan. If it fails to compile or the click assertion would be wrong, that is a real defect in Task 2's `LaunchedEffect` guard (the `state.selectedSurveySweepId == null` check) — fix the guard, not the test.

- [ ] **Step 3: Log the flag-duplication follow-up in `docs/contract-drift.md`**

Add this row to the table:

```markdown
| `SHOW_RESURVEY_BANNER` (F03) duplicates `SHOW_UNCONFIRMED_HUB_CARDS` (Hub, Phase 3) | Two separate local flags, one per package (`feature/survey/ui/plan`, `feature/survey/ui/hub`), for the same underlying gap: no real "rejected sweep" status exists yet | **Known duplication, not unified yet (2026-10-12)** — kept separate to avoid a merge conflict with Phase 3's still-open minor-cleanup PR; unify into one shared flag/location once both branches are merged | WP6 |
```

- [ ] **Step 4: Full build, ktlint, unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck testDebugUnitTest --tests "com.luxmap.feature.survey.ui.plan.*"`
Expected: BUILD SUCCESSFUL, all `plan` unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest/java/com/luxmap/feature/survey/ui/plan/SurveyPlanScreenTest.kt docs/contract-drift.md
git commit -m "test(redesign-phase4): cover manual route override, log flag duplication"
```

- [ ] **Step 6: Open PR**

Target `dev`. Title: `feat(redesign-phase4): restyle F03 survey plan screen`. Body must flag: no adb/emulator in this environment (every `androidTest` file in this plan is compile-verified only), and the full `testDebugUnitTest` suite's pre-existing unrelated `CaptureViewModelTest` issue (already tracked in `docs/contract-drift.md`).
