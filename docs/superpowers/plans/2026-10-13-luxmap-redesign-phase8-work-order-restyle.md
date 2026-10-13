# Phase 8 — Lệnh kiểm tra / sửa chữa restyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `WorkOrderDetailScreen.kt` (F09) and `WorkOrderCompletionScreen.kt` (F10) to the "Night Ledger" design system (spec `1s–1v`), adding 5 new shared components (`AppTopBar`, `LifecycleTracker`, `OutcomeSegment`, `EvidencePicker`, `RequirementList`) — a pure visual restyle on top of already-real backend data, no new endpoints, no ViewModel changes.

**Architecture:** Both ViewModels (`WorkOrderDetailViewModel`, `WorkOrderCompletionViewModel`) already expose the real wire fields (`task_kind`, `wo_status`, `inspection_outcome`, `fault_outcomes`, `allowed_actions`) end to end — confirmed 2026-10-13 against the real `luxmap_backend` source and already reflected in `CLAUDE.md`. This phase only touches Compose UI: 5 new small, focused components in `core/ui/components/` (reusable building blocks with their own unit tests) plus one small pure mapper function in `feature/workorder/ui/detail/`, then the two screen files are restyled to use them. No new Retrofit calls, no new DTO fields, no new `ViewModel` functions.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (`TopAppBar`, `SingleChoiceSegmentedButtonRow`/`SegmentedButton`, `FlowRow`), Compose UI Test (androidTest, compile-verified per this project's established convention), JUnit (pure-function unit test for the lifecycle mapper).

**Spec:** `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` §3 (component inventory rows for `AppTopBar`/`LifecycleTracker`/`OutcomeSegment`/`EvidencePicker`/`RequirementList`/`StatusBadge`/`MessageState`) and §4 "Lệnh kiểm tra (1s–1t)" / "Lệnh sửa chữa (1u–1v)"; read together with `docs/LuxMap_Mobile_Screen_Review_2026-10-11.md` section A item 7 (per-fault outcome, not per-pole) and `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` row 8 (just corrected 2026-10-13 from "Partially blocked" to "Ready — unblocked").

## Global Constraints

- Branch off `dev`, never `main`.
- **No `ViewModel` changes in this phase.** `WorkOrderDetailViewModel.kt` and `WorkOrderCompletionViewModel.kt` are not modified — every new UI element in this plan derives its values purely from fields the `UiState.Success` data classes already expose.
- **Keep the 3 separate nav screens** (`WorkOrderDetail` → `WorkOrderCompletion` → `WorkOrderEvidenceCapture`) exactly as they are today — confirmed with the project owner 2026-10-13. Do not merge "NGHIỆM THU" into the Detail screen despite spec §4's literal wording describing a merged screen for the common case; `EvidenceCaptureScreen.kt`/`EvidenceCaptureViewModel.kt` are untouched.
- **Touch targets ≥ 48dp always** (CLAUDE.md's hard global rule) — this overrides spec §3's `OutcomeSegment` row saying "≥ 44 dp"; every new interactive element in this plan uses 48dp, not 44dp.
- **No overall-work-order `PriorityDot` in the header.** Spec §4 describes one, but `WorkOrderDetail`/`WorkOrderDetailDto` has no overall priority/severity field (only per-fault `severity`, already shown in `FaultCard`) — do not invent one. Logged as a scope cut, not silently dropped.
- **No "cụm"/cluster line in the header.** Spec §4 describes "id + cụm", but `WorkOrderDetailDto` has no `cluster_id` field (only `WorkOrderItem`, the list-row shape, has one). Show `workOrderId` alone. Logged as a scope cut.
- **PoleStrip / mini-map are explicitly deferred**, per the spec's own fallback (§9 item 4: "nếu chưa có [ordered pole data], ẩn strip"). The ordered-pole endpoint (`GET /work-orders/{id}/poles`) is real but not wired into `WorkOrdersApi.kt` yet — wiring it is new integration work, not a restyle, and is out of scope for this phase.
- No new third-party dependencies. `SingleChoiceSegmentedButtonRow`/`SegmentedButton` and `FlowRow` are already available in this project's Compose BOM (`2024.10.01`) and `FlowRow` already has a precedent call site (`MapStatusFilterRow.kt`).
- `./gradlew ktlintCheck` after every task.
- All code comments in English, plain words, only where the WHY is non-obvious — this user wants zero comments that just restate what the code does.
- Each commit stays under ~400 changed lines per CLAUDE.md's commit-size rule. Tasks 1–4 (new components) are small and should each fit in one commit; Tasks 5–6 (screen restyles) touch existing files with mostly-unchanged logic — check the diff before committing and split into "static UI" + "swap in new components" commits if it runs over.

## Review Focus

- `OutcomeSegment`: tapping a different option while one is already selected must switch the selection, not toggle it off — `selected: String?` is driven entirely by the caller, the component must never manage its own local state that could drift from `faultOutcomes`.
- `LifecycleTracker`'s step mapper (`workOrderLifecycleSteps`) must never be called with `WorkOrderStatus.CANCELLED` — a cancelled work order has no forward progress to show; the screen must branch around it before calling the mapper, not pass a status the mapper can't represent.
- The Completion screen's `RequirementList` for an **inspection** task must show the per-fault-outcome requirement, never the repair-only "has an after photo" requirement, and vice versa for a **repair** task — the two task kinds have entirely different close rules (`canSubmit()`'s existing two branches), and the new checklist must visualize whichever branch actually applies, not always show both.
- A survey-kind work order (`detail.taskKind == "survey"`) must still render `WorkOrderDetailScreen`'s existing stub text ("Lệnh khảo sát — xem trong tab Khảo sát") without crashing on the new `AppTopBar`/`LifecycleTracker` code paths — survey work orders have no faults and currently skip the fault list entirely; the lifecycle tracker and top bar title must handle `WorkOrderTaskKind.SURVEY` as a normal case, not an afterthought.
- Both screens' `Empty`/`Error` states must render the real shared `core.ui.components.MessageState` (icon + title + subtitle + action slot) — the private bare-text `MessageState(text: String)` duplicate in each file must be deleted entirely, not left dead or merely unused.

---

### Task 1: `AppTopBar` shared component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/AppTopBar.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/AppTopBarTest.kt`

**Interfaces:**
- Consumes: `com.luxmap.core.theme.TitleTextStyle`, `BeVietnamPro` (existing).
- Produces: `AppTopBar(title: String, onBack: () -> Unit, subtitle: String? = null, modifier: Modifier = Modifier)` — consumed by Task 5 and Task 6.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppTopBarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun shows_the_title_and_invokes_onBack_when_the_back_button_is_tapped() {
        var backTapped = false
        composeRule.setContent {
            LuxMapTheme { AppTopBar(title = "Lệnh kiểm tra", onBack = { backTapped = true }) }
        }
        composeRule.onNodeWithText("Lệnh kiểm tra").assertExists()
        composeRule.onNodeWithContentDescription("Quay lại").performClick()
        assertTrue(backTapped)
    }

    @Test
    fun shows_the_subtitle_when_provided() {
        composeRule.setContent {
            LuxMapTheme { AppTopBar(title = "Lệnh kiểm tra", subtitle = "WO-1042", onBack = {}) }
        }
        composeRule.onNodeWithText("WO-1042").assertExists()
    }

    @Test
    fun the_back_button_meets_the_minimum_touch_target() {
        composeRule.setContent {
            LuxMapTheme { AppTopBar(title = "Lệnh kiểm tra", onBack = {}) }
        }
        composeRule
            .onNodeWithContentDescription("Quay lại")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `AppTopBar` is unresolved.

- [ ] **Step 3: Implement `AppTopBar`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.luxmap.core.theme.BeVietnamPro
import com.luxmap.core.theme.TitleTextStyle

private val SubtitleTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 16.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(text = title, style = TitleTextStyle)
                if (subtitle != null) {
                    Text(text = subtitle, style = SubtitleTextStyle)
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/AppTopBar.kt app/src/androidTest/java/com/luxmap/core/ui/components/AppTopBarTest.kt
git commit -m "feat(redesign-phase8): add the AppTopBar shared component"
```

---

### Task 2: `LifecycleTracker` shared component + work-order lifecycle mapper

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/LifecycleTracker.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycle.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/LifecycleTrackerTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycleTest.kt`

**Interfaces:**
- Consumes: `com.luxmap.core.theme.WorkOrderStatus`, `WorkOrderTaskKind`, `label()` (existing).
- Produces: `data class LifecycleStep(val label: String, val state: LifecycleStepState)`, `enum class LifecycleStepState { DONE, CURRENT, FUTURE }`, `LifecycleTracker(steps: List<LifecycleStep>, modifier: Modifier = Modifier)` (component); `fun workOrderLifecycleSteps(woStatus: WorkOrderStatus, taskKind: WorkOrderTaskKind): List<LifecycleStep>` (mapper, throws on `CANCELLED`) — both consumed by Task 5.

- [ ] **Step 1: Write the failing component test**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class LifecycleTrackerTest {
    @get:Rule val composeRule = createComposeRule()

    private fun steps() =
        listOf(
            LifecycleStep("Mở", LifecycleStepState.DONE),
            LifecycleStep("Đã giao", LifecycleStepState.DONE),
            LifecycleStep("Kiểm tra", LifecycleStepState.CURRENT),
            LifecycleStep("Hoàn tất", LifecycleStepState.FUTURE),
            LifecycleStep("Đã nghiệm thu", LifecycleStepState.FUTURE),
        )

    @Test
    fun shows_all_step_labels_in_order() {
        composeRule.setContent { LuxMapTheme { LifecycleTracker(steps = steps()) } }
        listOf("Mở", "Đã giao", "Kiểm tra", "Hoàn tất", "Đã nghiệm thu").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun a_done_step_shows_the_check_icon_and_a_future_step_does_not() {
        composeRule.setContent { LuxMapTheme { LifecycleTracker(steps = steps()) } }
        composeRule.onNodeWithTag("lifecycle_check_0").assertExists()
        composeRule.onNodeWithTag("lifecycle_check_3").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: Write the failing mapper test**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.ui.components.LifecycleStepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkOrderLifecycleTest {
    @Test
    fun open_marks_only_the_first_step_current() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.OPEN, WorkOrderTaskKind.INSPECTION)
        assertEquals(LifecycleStepState.CURRENT, steps[0].state)
        assertEquals(LifecycleStepState.FUTURE, steps[1].state)
        assertEquals(LifecycleStepState.FUTURE, steps[4].state)
    }

    @Test
    fun in_progress_marks_the_task_kind_step_current_with_its_label() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.IN_PROGRESS, WorkOrderTaskKind.REPAIR)
        assertEquals(LifecycleStepState.DONE, steps[0].state)
        assertEquals(LifecycleStepState.DONE, steps[1].state)
        assertEquals(LifecycleStepState.CURRENT, steps[2].state)
        assertEquals("Sửa chữa", steps[2].label)
        assertEquals(LifecycleStepState.FUTURE, steps[3].state)
    }

    @Test
    fun verified_marks_every_step_done() {
        val steps = workOrderLifecycleSteps(WorkOrderStatus.VERIFIED, WorkOrderTaskKind.INSPECTION)
        steps.forEach { assertEquals(LifecycleStepState.DONE, it.state) }
    }

    @Test
    fun cancelled_is_rejected() {
        assertThrows(IllegalStateException::class.java) {
            workOrderLifecycleSteps(WorkOrderStatus.CANCELLED, WorkOrderTaskKind.INSPECTION)
        }
    }
}
```

- [ ] **Step 3: Run both tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugUnitTestKotlin`
Expected: FAIL — `LifecycleTracker`/`LifecycleStep`/`LifecycleStepState`/`workOrderLifecycleSteps` are unresolved.

- [ ] **Step 4: Implement `LifecycleTracker`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Spacing

enum class LifecycleStepState { DONE, CURRENT, FUTURE }

data class LifecycleStep(val label: String, val state: LifecycleStepState)

@Composable
fun LifecycleTracker(
    steps: List<LifecycleStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            steps.forEachIndexed { index, step ->
                StepDot(index = index, state = step.state)
                if (index != steps.lastIndex) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(connectorColor(step.state)),
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(top = Spacing.xs)) {
            steps.forEach { step ->
                Text(
                    text = step.label,
                    style = LabelTextStyle,
                    textAlign = TextAlign.Center,
                    color =
                        if (step.state == LifecycleStepState.FUTURE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StepDot(
    index: Int,
    state: LifecycleStepState,
) {
    when (state) {
        LifecycleStepState.DONE ->
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(14.dp).testTag("lifecycle_check_$index"),
                )
            }

        LifecycleStepState.CURRENT ->
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )

        LifecycleStepState.FUTURE ->
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
    }
}

@Composable
private fun connectorColor(previousStepState: LifecycleStepState): Color =
    if (previousStepState == LifecycleStepState.DONE) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
```

- [ ] **Step 5: Implement `workOrderLifecycleSteps`**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.LifecycleStep
import com.luxmap.core.ui.components.LifecycleStepState

private const val STEP_LABEL_OPEN = "Mở"
private const val STEP_LABEL_ASSIGNED = "Đã giao"
private const val STEP_LABEL_DONE = "Hoàn tất"
private const val STEP_LABEL_VERIFIED = "Đã nghiệm thu"

fun workOrderLifecycleSteps(
    woStatus: WorkOrderStatus,
    taskKind: WorkOrderTaskKind,
): List<LifecycleStep> {
    check(woStatus != WorkOrderStatus.CANCELLED) { "A cancelled work order has no lifecycle steps to show" }
    val labels = listOf(STEP_LABEL_OPEN, STEP_LABEL_ASSIGNED, taskKind.label(), STEP_LABEL_DONE, STEP_LABEL_VERIFIED)
    val currentIndex =
        when (woStatus) {
            WorkOrderStatus.OPEN -> 0
            WorkOrderStatus.ASSIGNED -> 1
            WorkOrderStatus.IN_PROGRESS -> 2
            WorkOrderStatus.DONE -> 3
            WorkOrderStatus.VERIFIED -> 4
            WorkOrderStatus.CANCELLED -> error("unreachable, checked above")
        }
    return labels.mapIndexed { index, label ->
        val state =
            when {
                woStatus == WorkOrderStatus.VERIFIED -> LifecycleStepState.DONE
                index < currentIndex -> LifecycleStepState.DONE
                index == currentIndex -> LifecycleStepState.CURRENT
                else -> LifecycleStepState.FUTURE
            }
        LifecycleStep(label = label, state = state)
    }
}
```

- [ ] **Step 6: Run both tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderLifecycleTest"`
Expected: compile BUILD SUCCESSFUL; `WorkOrderLifecycleTest` PASS 4/4.

- [ ] **Step 7: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/LifecycleTracker.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycle.kt app/src/androidTest/java/com/luxmap/core/ui/components/LifecycleTrackerTest.kt app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderLifecycleTest.kt
git commit -m "feat(redesign-phase8): add the LifecycleTracker component and work-order step mapper"
```

---

### Task 3: `OutcomeSegment` shared component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/OutcomeSegment.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/OutcomeSegmentTest.kt`

**Interfaces:**
- Consumes: nothing new (the 3 wire values `fault_present`/`fault_absent`/`inconclusive` are the same strings already used throughout `feature/workorder`).
- Produces: `OutcomeSegment(selected: String?, onSelected: (String) -> Unit, modifier: Modifier = Modifier)` — consumed by Task 6.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OutcomeSegmentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun shows_all_three_options() {
        composeRule.setContent { LuxMapTheme { OutcomeSegment(selected = null, onSelected = {}) } }
        composeRule.onNodeWithText("Có sự cố").assertExists()
        composeRule.onNodeWithText("Không có").assertExists()
        composeRule.onNodeWithText("Chưa xác định").assertExists()
    }

    @Test
    fun tapping_an_option_invokes_onSelected_with_the_wire_value() {
        var picked: String? = null
        composeRule.setContent { LuxMapTheme { OutcomeSegment(selected = null, onSelected = { picked = it }) } }
        composeRule.onNodeWithText("Không có").performClick()
        assertEquals("fault_absent", picked)
    }

    @Test
    fun the_selected_option_is_marked_selected_and_the_others_are_not() {
        composeRule.setContent { LuxMapTheme { OutcomeSegment(selected = "fault_absent", onSelected = {}) } }
        composeRule.onNodeWithText("Không có").assertIsSelected()
        composeRule.onNodeWithText("Có sự cố").assertIsNotSelected()
        composeRule.onNodeWithText("Chưa xác định").assertIsNotSelected()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `OutcomeSegment` is unresolved.

- [ ] **Step 3: Implement `OutcomeSegment`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val OUTCOME_OPTIONS =
    listOf(
        "fault_present" to "Có sự cố",
        "fault_absent" to "Không có",
        "inconclusive" to "Chưa xác định",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutcomeSegment(
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        OUTCOME_OPTIONS.forEachIndexed { index, (value, text) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index, OUTCOME_OPTIONS.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.onSurface,
                        activeContentColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = text)
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/OutcomeSegment.kt app/src/androidTest/java/com/luxmap/core/ui/components/OutcomeSegmentTest.kt
git commit -m "feat(redesign-phase8): add the OutcomeSegment shared component"
```

---

### Task 4: `EvidencePicker` + `RequirementList` shared components

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/EvidencePicker.kt`
- Create: `app/src/main/java/com/luxmap/core/ui/components/RequirementList.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/EvidencePickerTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/ui/components/RequirementListTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `EvidencePicker(hasPhoto: Boolean, onTileClick: () -> Unit, modifier: Modifier = Modifier)`; `data class Requirement(val label: String, val met: Boolean)`, `RequirementList(items: List<Requirement>, modifier: Modifier = Modifier)` — both consumed by Task 6.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EvidencePickerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun when_there_is_no_photo_shows_the_add_tile_and_invokes_onTileClick() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme { EvidencePicker(hasPhoto = false, onTileClick = { tapped = true }) }
        }
        composeRule.onNodeWithContentDescription("Chụp ảnh sau").performClick()
        assertTrue(tapped)
    }

    @Test
    fun when_there_is_a_photo_shows_the_filled_tile_and_invokes_onTileClick_to_retake() {
        var tapped = false
        composeRule.setContent {
            LuxMapTheme { EvidencePicker(hasPhoto = true, onTileClick = { tapped = true }) }
        }
        composeRule.onNodeWithText("Đã chụp").performClick()
        assertTrue(tapped)
    }
}
```

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Rule
import org.junit.Test

class RequirementListTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun renders_each_requirement_label_in_order() {
        composeRule.setContent {
            LuxMapTheme {
                RequirementList(
                    items =
                        listOf(
                            Requirement("Có ảnh sau sửa chữa", met = true),
                            Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = false),
                        ),
                )
            }
        }
        composeRule.onNodeWithText("Có ảnh sau sửa chữa").assertExists()
        composeRule.onNodeWithText("Đã ghi chú kết quả (≥ 10 ký tự)").assertExists()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `EvidencePicker`/`RequirementList`/`Requirement` are unresolved.

- [ ] **Step 3: Implement `EvidencePicker`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing

private val TileSize = 88.dp

@Composable
fun EvidencePicker(
    hasPhoto: Boolean,
    onTileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (hasPhoto) {
            FilledTile(onClick = onTileClick)
        } else {
            AddTile(onClick = onTileClick)
        }
    }
}

@Composable
private fun FilledTile(onClick: () -> Unit) {
    Column(
        modifier =
            Modifier
                .size(TileSize)
                .clickable(onClick = onClick)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.control)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = "Đã chụp", style = LabelTextStyle)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Column(
        modifier =
            Modifier
                .size(TileSize)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Chụp ảnh sau" }
                .drawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        cornerRadius = CornerRadius(Radius.control.toPx()),
                        style =
                            Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                            ),
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "Chụp ảnh sau", style = LabelTextStyle)
    }
}
```

- [ ] **Step 4: Implement `RequirementList`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Spacing

data class Requirement(val label: String, val met: Boolean)

@Composable
fun RequirementList(
    items: List<Requirement>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.met) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (item.met) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(text = item.label, style = LabelTextStyle, modifier = Modifier.padding(start = Spacing.sm))
            }
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/EvidencePicker.kt app/src/main/java/com/luxmap/core/ui/components/RequirementList.kt app/src/androidTest/java/com/luxmap/core/ui/components/EvidencePickerTest.kt app/src/androidTest/java/com/luxmap/core/ui/components/RequirementListTest.kt
git commit -m "feat(redesign-phase8): add the EvidencePicker and RequirementList shared components"
```

---

### Task 5: Restyle `WorkOrderDetailScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt`

**Interfaces:**
- Consumes: Task 1's `AppTopBar`; Task 2's `LifecycleTracker`/`workOrderLifecycleSteps`; existing `core.ui.components.MessageState`/`StatusBadge`/`PriorityDot`/`PrimaryButton`; existing `WorkOrderDetailUiState`/`WorkOrderDetailViewModel` (unchanged).
- Produces: `WorkOrderDetailScreen` keeps its exact existing signature (`uiState`, `onBack`, `onStart`, `onNavigateFault`, `onComplete`, `modifier`) — no caller elsewhere needs to change.

- [ ] **Step 1: Write the failing screen test**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun repairDetail(woStatus: String = "in_progress") =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = woStatus,
        taskKind = "repair",
        dueDate = "2026-10-10",
        scheduledDate = null,
        note = null,
        allowedActions = emptyList(),
        faults =
            listOf(
                WorkOrderFaultDetail(
                    faultId = "FAULT-1",
                    poleId = "POLE-0047",
                    lat = 10.97,
                    lng = 106.49,
                    faultType = "lamp_out",
                    faultStatus = "confirmed",
                    severity = "high",
                    inspectionOutcome = "fault_present",
                ),
            ),
    )

class WorkOrderDetailScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Loading,
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithTag("work_order_detail_loading").assertExists()
    }

    @Test
    fun a_repair_in_progress_shows_the_app_top_bar_title_and_every_lifecycle_step_label() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = repairDetail()),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Lệnh sửa chữa").assertExists()
        listOf("Mở", "Đã giao", "Sửa chữa", "Hoàn tất", "Đã nghiệm thu").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
    }

    @Test
    fun a_cancelled_work_order_hides_the_lifecycle_tracker() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = repairDetail(woStatus = "cancelled")),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Đã huỷ").assertExists()
        composeRule.onNodeWithText("Đã nghiệm thu").assertDoesNotExist()
    }

    @Test
    fun tapping_dieu_huong_invokes_onNavigateFault_with_the_fault_coordinates() {
        var navigated: Pair<Double, Double>? = null
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Success(detail = repairDetail()),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { lat, lng -> navigated = lat to lng },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Điều hướng").performClick()
        assertEquals(10.97 to 106.49, navigated)
    }

    @Test
    fun empty_state_shows_the_no_work_order_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Empty,
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tìm thấy lệnh này").assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderDetailScreen(
                    uiState = WorkOrderDetailUiState.Error("Không tải được dữ liệu lệnh"),
                    onBack = {},
                    onStart = {},
                    onNavigateFault = { _, _ -> },
                    onComplete = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được dữ liệu lệnh").assertExists()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: compiles (current screen already renders text for most of these), but conceptually fails on-device for the new title ("Lệnh sửa chữa") and lifecycle-step-label assertions — the current screen shows "Chi tiết lệnh" and no tracker at all. Confirm this compiles first, matching this project's established androidTest convention (compile-checked here, behavior verified on-device later).

- [ ] **Step 3: Restyle `WorkOrderDetailScreen.kt`**

Replace the whole file:

```kotlin
package com.luxmap.feature.workorder.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.common.openInMaps
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.HeadlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.WorkOrderStatus
import com.luxmap.core.theme.WorkOrderTaskKind
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.color
import com.luxmap.core.theme.label
import com.luxmap.core.theme.severityFromWire
import com.luxmap.core.theme.workOrderStatusFromWire
import com.luxmap.core.theme.workOrderTaskKindFromWire
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.LifecycleTracker
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.PriorityDot
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.core.ui.components.dueDateLabel
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation

@Composable
fun WorkOrderDetailRoute(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    onStartSurvey: (workOrderId: String, surveySweepId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startedSurveySession.collect { event ->
            onStartSurvey(event.workOrderId, event.surveySweepId)
        }
    }

    WorkOrderDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onStart = viewModel::start,
        onNavigateFault = { lat, lng -> openInMaps(context, lat, lng) },
        onComplete = onComplete,
        modifier = modifier,
    )
}

@Composable
fun WorkOrderDetailScreen(
    uiState: WorkOrderDetailUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topBarTitle =
        if (uiState is WorkOrderDetailUiState.Success) {
            workOrderDetailTopBarTitle(uiState.detail.taskKind)
        } else {
            "Chi tiết lệnh"
        }
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = topBarTitle, onBack = onBack) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is WorkOrderDetailUiState.Loading -> LoadingState()
                is WorkOrderDetailUiState.Success ->
                    WorkOrderDetailContent(
                        state = uiState,
                        onStart = onStart,
                        onNavigateFault = onNavigateFault,
                        onComplete = onComplete,
                    )
                is WorkOrderDetailUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.Assignment,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Không tìm thấy lệnh này",
                        subtitle = "Lệnh có thể đã bị xoá hoặc không thuộc phạm vi của bạn.",
                        action = {},
                    )
                is WorkOrderDetailUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = uiState.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        action = {},
                    )
            }
        }
    }
}

private fun workOrderDetailTopBarTitle(taskKindWire: String): String =
    when (workOrderTaskKindFromWire(taskKindWire)) {
        WorkOrderTaskKind.INSPECTION -> "Lệnh kiểm tra"
        WorkOrderTaskKind.REPAIR -> "Lệnh sửa chữa"
        WorkOrderTaskKind.SURVEY -> "Lệnh khảo sát"
    }

@Composable
private fun WorkOrderDetailContent(
    state: WorkOrderDetailUiState.Success,
    onStart: () -> Unit,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onComplete: () -> Unit,
) {
    val detail = state.detail
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        WorkOrderHeaderSection(detail)
        if (detail.taskKind == "survey") {
            Text(text = "Lệnh khảo sát — xem trong tab Khảo sát", style = MaterialTheme.typography.bodyMedium)
        } else {
            FaultListSection(faults = detail.faults, onNavigateFault = onNavigateFault)
        }
        if (state.startError != null) {
            Text(
                text = state.startError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if ("start" in detail.allowedActions) {
            PrimaryButton(
                text = "Bắt đầu",
                onClick = onStart,
                enabled = !state.isStarting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if ("complete" in detail.allowedActions) {
            PrimaryButton(
                text = "Hoàn thành",
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WorkOrderHeaderSection(detail: WorkOrderDetail) {
    val status = workOrderStatusFromWire(detail.woStatus)
    val kind = workOrderTaskKindFromWire(detail.taskKind)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = kind.label(), colors = kind.badgeColors())
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Text(text = detail.title, style = HeadlineTextStyle)
        Text(text = detail.workOrderId, style = MaterialTheme.typography.bodyMedium)
        Text(text = dueDateLabel(detail.dueDate).text, style = MaterialTheme.typography.bodyMedium)
        if (!detail.scheduledDate.isNullOrBlank()) {
            Text(
                text = "Lịch khảo sát/sửa chữa: ${DateFormatUtils.formatPlainDate(detail.scheduledDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!detail.note.isNullOrBlank()) {
            Text(text = detail.note, style = MaterialTheme.typography.bodyMedium)
        }
        if (status != WorkOrderStatus.CANCELLED) {
            LifecycleTracker(
                steps = workOrderLifecycleSteps(status, kind),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun FaultListSection(
    faults: List<WorkOrderFaultDetail>,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (faults.isEmpty()) {
            Text(
                text = "Không có sự cố nào trong lệnh này",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            faults.forEach { fault ->
                FaultCard(fault = fault, onNavigate = { onNavigateFault(fault.lat, fault.lng) })
            }
        }
    }
}

@Composable
private fun FaultCard(
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
) {
    val priority = severityFromWire(fault.severity)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.card))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(text = fault.poleId ?: fault.faultId, style = MaterialTheme.typography.titleMedium)
        PriorityDot(text = priority.label(), dotColor = priority.color())
        Text(text = faultTypeLabel(fault.faultType), style = MaterialTheme.typography.bodyMedium)
        Text(text = faultStatusLabel(fault.faultStatus), style = MaterialTheme.typography.bodyMedium)
        if (fault.inspectionOutcome != null) {
            Text(text = inspectionOutcomeLabel(fault.inspectionOutcome), style = MaterialTheme.typography.bodyMedium)
        }
        if (fault.hasLocation()) {
            OutlinedButton(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            ) {
                Icon(imageVector = Icons.Filled.Navigation, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Điều hướng")
            }
        } else {
            Text(
                text = "Chưa có toạ độ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag("work_order_detail_loading"))
}

private fun sampleRepairWithFaults() =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = "assigned",
        taskKind = "repair",
        dueDate = "2026-10-10",
        scheduledDate = "2026-10-09",
        note = "Ưu tiên xử lý trước khi mưa lớn",
        allowedActions = listOf("start"),
        faults =
            listOf(
                WorkOrderFaultDetail(
                    faultId = "FAULT-1",
                    poleId = "POLE-0047",
                    lat = 10.97,
                    lng = 106.49,
                    faultType = "lamp_out",
                    faultStatus = "confirmed",
                    severity = "high",
                    inspectionOutcome = "fault_present",
                ),
            ),
    )

private fun sampleRepairNoFaults() = sampleRepairWithFaults().copy(workOrderId = "WO-1099", faults = emptyList())

private fun sampleSurvey() =
    sampleRepairWithFaults().copy(workOrderId = "WO-2001", taskKind = "survey", faults = emptyList())

@Preview(name = "Success - Repair with faults", showBackground = true, heightDp = 900)
@Composable
private fun WorkOrderDetailScreenRepairPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleRepairWithFaults()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(name = "Success - Repair, no faults", showBackground = true)
@Composable
private fun WorkOrderDetailScreenNoFaultsPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleRepairNoFaults()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(name = "Success - Survey", showBackground = true)
@Composable
private fun WorkOrderDetailScreenSurveyPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Success(detail = sampleSurvey()),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenLoadingPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Loading,
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenEmptyPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Empty,
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun WorkOrderDetailScreenErrorPreview() {
    WorkOrderDetailScreen(
        uiState = WorkOrderDetailUiState.Error(message = "Không tải được dữ liệu"),
        onBack = {},
        onStart = {},
        onNavigateFault = { _, _ -> },
        onComplete = {},
    )
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt
git commit -m "feat(redesign-phase8): restyle WorkOrderDetailScreen with AppTopBar and LifecycleTracker"
```

---

### Task 6: Restyle `WorkOrderCompletionScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreenTest.kt`

**Interfaces:**
- Consumes: Task 1's `AppTopBar`; Task 3's `OutcomeSegment`; Task 4's `EvidencePicker`/`RequirementList`/`Requirement`; existing `core.ui.components.MessageState`/`InlineBanner`/`StatusBadge`/`PrimaryButton`; existing `WorkOrderCompletionUiState.canSubmit()`/`syncStatus()` (unchanged).
- Produces: `WorkOrderCompletionScreen` keeps its exact existing signature — no caller elsewhere needs to change.

- [ ] **Step 1: Write the failing screen test**

```kotlin
package com.luxmap.feature.workorder.ui.completion

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private fun repairDetail() =
    WorkOrderDetail(
        workOrderId = "WO-1042",
        title = "Sửa đèn tuyến A",
        woStatus = "in_progress",
        taskKind = "repair",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = emptyList(),
        faults = emptyList(),
    )

private fun inspectionDetail() =
    repairDetail().copy(
        taskKind = "inspection",
        faults =
            listOf(
                WorkOrderFaultDetail(
                    faultId = "FAULT-1",
                    poleId = "POLE-0047",
                    lat = 10.97,
                    lng = 106.49,
                    faultType = "lamp_out",
                    faultStatus = "confirmed",
                    severity = "high",
                    inspectionOutcome = null,
                ),
                WorkOrderFaultDetail(
                    faultId = "FAULT-2",
                    poleId = "POLE-0048",
                    lat = 10.98,
                    lng = 106.50,
                    faultType = "lamp_dim",
                    faultStatus = "confirmed",
                    severity = "low",
                    inspectionOutcome = null,
                ),
            ),
    )

private fun successState(
    detail: WorkOrderDetail,
    faultOutcomes: Map<String, String> = emptyMap(),
    hasEvidence: Boolean = false,
) = WorkOrderCompletionUiState.Success(
    detail = detail,
    localEvidence =
        if (hasEvidence) {
            com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity(
                clientOpId = "CLIENT-OP-1",
                workOrderId = detail.workOrderId,
                kind = "after",
                filePath = "/tmp/evidence.jpg",
                capturedAt = java.time.Instant.parse("2026-10-13T00:00:00Z"),
                lat = 10.97,
                lng = 106.49,
                uploadStatus = "pending",
            )
        } else {
            null
        },
    localCompletion = null,
    isOnline = true,
    reportNote = "",
    materialsUsed = "",
    faultOutcomes = faultOutcomes,
)

class WorkOrderCompletionScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loading_state_shows_progress_indicator() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = WorkOrderCompletionUiState.Loading,
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithTag("work_order_completion_loading").assertExists()
    }

    @Test
    fun a_repair_without_evidence_shows_the_add_tile_and_the_unmet_requirement() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = successState(repairDetail()),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Chụp ảnh sau").assertExists()
        composeRule.onNodeWithText("Có ảnh sau sửa chữa").assertExists()
    }

    @Test
    fun an_inspection_shows_an_outcome_segment_per_fault_and_the_progress_hint() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = successState(inspectionDetail(), faultOutcomes = mapOf("FAULT-1" to "fault_present")),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithText("1/2 cột đã ghi nhận").assertExists()
        composeRule.onNodeWithText("POLE-0047").assertExists()
        composeRule.onNodeWithText("POLE-0048").assertExists()
    }

    @Test
    fun selecting_an_outcome_invokes_onFaultOutcomeSelected_with_the_fault_and_wire_value() {
        var picked: Pair<String, String>? = null
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = successState(inspectionDetail()),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { faultId, outcome -> picked = faultId to outcome },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onAllNodesWithText("Có sự cố")[0].performClick()
        assertEquals("FAULT-1" to "fault_present", picked)
    }

    @Test
    fun empty_state_shows_the_no_work_order_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = WorkOrderCompletionUiState.Empty,
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tìm thấy lệnh này").assertExists()
    }

    @Test
    fun error_state_shows_the_message() {
        composeRule.setContent {
            LuxMapTheme {
                WorkOrderCompletionScreen(
                    uiState = WorkOrderCompletionUiState.Error("Không tải được dữ liệu lệnh"),
                    onBack = {},
                    onReportNoteChanged = {},
                    onMaterialsUsedChanged = {},
                    onFaultOutcomeSelected = { _, _ -> },
                    onSubmit = {},
                    onCaptureEvidence = {},
                    onRetakeEvidence = {},
                )
            }
        }
        composeRule.onNodeWithText("Không tải được dữ liệu lệnh").assertExists()
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: compiles; conceptually fails on-device against the current screen (no `EvidencePicker`, no `OutcomeSegment`, no progress-hint text). Confirm compile success first, per this project's established androidTest convention.

- [ ] **Step 3: Restyle `WorkOrderCompletionScreen.kt`**

Replace the whole file:

```kotlin
package com.luxmap.feature.workorder.ui.completion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.EvidencePicker
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.OutcomeSegment
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.Requirement
import com.luxmap.core.ui.components.RequirementList
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail

@Composable
fun WorkOrderCompletionRoute(
    onBack: () -> Unit,
    onCaptureEvidence: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderCompletionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    WorkOrderCompletionScreen(
        uiState = uiState,
        onBack = onBack,
        onReportNoteChanged = viewModel::onReportNoteChanged,
        onMaterialsUsedChanged = viewModel::onMaterialsUsedChanged,
        onFaultOutcomeSelected = viewModel::onFaultOutcomeSelected,
        onSubmit = viewModel::submit,
        onCaptureEvidence = onCaptureEvidence,
        onRetakeEvidence = { viewModel.retakeEvidence(onCaptureEvidence) },
        modifier = modifier,
    )
}

@Composable
fun WorkOrderCompletionScreen(
    uiState: WorkOrderCompletionUiState,
    onBack: () -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = "Hoàn thành lệnh", onBack = onBack) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is WorkOrderCompletionUiState.Loading -> LoadingState()
                is WorkOrderCompletionUiState.Success ->
                    WorkOrderCompletionContent(
                        state = uiState,
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onFaultOutcomeSelected = onFaultOutcomeSelected,
                        onSubmit = onSubmit,
                        onCaptureEvidence = onCaptureEvidence,
                        onRetakeEvidence = onRetakeEvidence,
                    )
                is WorkOrderCompletionUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.Assignment,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Không tìm thấy lệnh này",
                        subtitle = "Lệnh có thể đã bị xoá hoặc không thuộc phạm vi của bạn.",
                        action = {},
                    )
                is WorkOrderCompletionUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = uiState.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        action = {},
                    )
            }
        }
    }
}

@Composable
private fun WorkOrderCompletionContent(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (!state.detail.reviewNote.isNullOrBlank()) {
            Text(
                text = "Manager yêu cầu bổ sung: ${state.detail.reviewNote}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.syncStatus()?.let { status ->
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        if (state.detail.taskKind == "repair") {
            RepairCompletionSection(
                state = state,
                onReportNoteChanged = onReportNoteChanged,
                onMaterialsUsedChanged = onMaterialsUsedChanged,
                onCaptureEvidence = onCaptureEvidence,
                onRetakeEvidence = onRetakeEvidence,
            )
        } else {
            InspectionCompletionSection(
                state = state,
                onReportNoteChanged = onReportNoteChanged,
                onFaultOutcomeSelected = onFaultOutcomeSelected,
            )
        }
        if (state.submitError != null) {
            InlineBanner(message = state.submitError, tone = InlineBannerTone.ERROR)
        }
        PrimaryButton(
            text = "Nộp",
            onClick = onSubmit,
            enabled = state.canSubmit() && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RepairCompletionSection(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    val hasPhoto = state.localEvidence != null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EvidencePicker(
            hasPhoto = hasPhoto,
            onTileClick = if (hasPhoto) onRetakeEvidence else onCaptureEvidence,
        )
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Kết quả sửa chữa") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.materialsUsed,
            onValueChange = onMaterialsUsedChanged,
            label = { Text("Vật tư đã dùng (không bắt buộc)") },
            modifier = Modifier.fillMaxWidth(),
        )
        RequirementList(
            items =
                listOf(
                    Requirement("Có ảnh sau sửa chữa", met = hasPhoto),
                    Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
                ),
        )
    }
}

@Composable
private fun InspectionCompletionSection(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
) {
    val faults = state.detail.faults
    val recordedCount = faults.count { state.faultOutcomes.containsKey(it.faultId) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = "$recordedCount/${faults.size} cột đã ghi nhận", style = MaterialTheme.typography.bodyMedium)
        faults.forEach { fault ->
            FaultOutcomeSection(
                fault = fault,
                selected = state.faultOutcomes[fault.faultId],
                onSelected = onFaultOutcomeSelected,
            )
        }
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Ghi chú kiểm tra") },
            modifier = Modifier.fillMaxWidth(),
        )
        RequirementList(
            items =
                listOf(
                    Requirement("Đã ghi nhận đủ kết quả kiểm tra từng cột", met = recordedCount == faults.size),
                    Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
                ),
        )
    }
}

@Composable
private fun FaultOutcomeSection(
    fault: WorkOrderFaultDetail,
    selected: String?,
    onSelected: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = fault.poleId ?: fault.faultId, style = MaterialTheme.typography.titleMedium)
        OutcomeSegment(selected = selected, onSelected = { outcome -> onSelected(fault.faultId, outcome) })
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag("work_order_completion_loading"))
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt app/src/androidTest/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreenTest.kt
git commit -m "feat(redesign-phase8): restyle WorkOrderCompletionScreen with OutcomeSegment, EvidencePicker, RequirementList"
```

---

### Task 7: Docs and final verification

**Files:**
- Modify: `docs/contract-drift.md`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — this is the phase's wrap-up task.

- [ ] **Step 1: Add a row to `docs/contract-drift.md`**

Append this row to the table (after the last existing row):

```markdown
| Phase 8 restyle skips the header PriorityDot, "cụm" line, PoleStrip, and mini-map | `WorkOrderDetailScreen.kt`'s `WorkOrderHeaderSection` shows only `task_kind`/`wo_status` badges + title + id + due date + the new `LifecycleTracker` — no overall-order priority dot, no cluster line, no ordered pole list, no map | **Deliberate scope cut (2026-10-13, Phase 8)** — spec §4 mocks up all four, but none has a real field backing it on `WorkOrderDetailDto` today: no overall priority/severity field (only per-fault `severity`, already shown in `FaultCard`), no `cluster_id` on the detail response (only on `WorkOrderItem`, the list-row shape), and no ordered-pole data wired (`GET /work-orders/{id}/poles` exists on the real backend but `WorkOrdersApi.kt` doesn't call it yet). Spec §9 item 4 itself sanctions hiding the pole strip when this data isn't available. Wiring any of these is new integration work, not a restyle | WP6 (if/when `GET /work-orders/{id}/poles` and an overall priority/cluster field on the detail response are wanted) |
```

- [ ] **Step 2: Run the full scoped verification**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugUnitTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderLifecycleTest"`
Expected: BUILD SUCCESSFUL, 4/4 pass.

Do not run the full `./gradlew test` suite — `CaptureViewModelTest` has a known pre-existing hang unrelated to this branch; the scoped compile+lint above plus this plan's `Review Focus` tests are this project's established substitute verification for a restyle-only phase.

- [ ] **Step 3: Verify no capture-mode files were touched**

Run: `git diff dev..HEAD --stat | grep -i "capture\|CaptureViewModel"`
Expected: no output — this phase never touches `feature/survey/ui/capture`.

- [ ] **Step 4: Commit**

```bash
git add docs/contract-drift.md
git commit -m "docs(redesign-phase8): log the header/PoleStrip scope cuts"
```

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin style/redesign-phase8-work-order-restyle
gh pr create --base dev --head style/redesign-phase8-work-order-restyle --title "style(redesign-phase8): restyle work-order detail and completion screens" --body "..."
```

(The exact PR body is written at execution time, summarizing the actual commits made — follow this project's established PR description pattern from Phases 2–7.)

---

## Self-Review Notes

- **Spec coverage:** `AppTopBar` (64dp height via M3 default, 48dp back button, title 18/600 + optional subtitle — Task 1), `LifecycleTracker` (5 steps, 24dp dots, 2dp connector, done/current/future states — Task 2), `OutcomeSegment` (3-choice, Ink-fill selected — Task 3, using 48dp not spec's 44dp per CLAUDE.md's hard rule, a stated ruling), `EvidencePicker` (88dp tile, dashed add-tile — Task 4), `RequirementList` (check/empty icon + 14sp label — Task 4), header badges + title 24 + id + due date (Task 5), per-fault `OutcomeSegment` + progress hint + inline "NGHIỆM THU"-equivalent requirement checklist (Task 6). Deliberately NOT covered: header `PriorityDot`/"cụm" (no real field — Task 7 drift row), merged nav structure (explicit project-owner decision to keep 3 screens), PoleStrip/mini-map (Task 7 drift row, spec's own sanctioned fallback).
- **Placeholder scan:** none found — every step has concrete, runnable code, verified against the real source for every type referenced (`WorkOrderStatus`/`WorkOrderTaskKind`/`SyncStatus` enums in `Color.kt`, `LocalWorkOrderEvidenceEntity`'s real 8-argument constructor in `app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderEvidenceEntity.kt`).
- **Type consistency:** `LifecycleStep`/`LifecycleStepState` (Task 2) used identically in Task 5; `Requirement` (Task 4) used identically in Task 6; `OutcomeSegment`'s wire-value contract (`fault_present`/`fault_absent`/`inconclusive`) matches `WorkOrderCompletionViewModel.onFaultOutcomeSelected`'s existing signature exactly, verified against the real file.
- **Review Focus:** all 5 items have an owning test — selection-switch behavior (Task 3's "the selected option is marked selected" test plus Task 6's "selecting an outcome" test), `CANCELLED` rejection (Task 2's `cancelled_is_rejected` mapper test plus Task 5's "hides the lifecycle tracker" screen test), inspection-vs-repair requirement branching (Task 6's two distinct Success-state tests), survey task-kind handling (Task 5's `workOrderDetailTopBarTitle`/`workOrderLifecycleSteps` both take `WorkOrderTaskKind.SURVEY` as an ordinary enum case, and the existing survey stub branch in `WorkOrderDetailContent` is left untouched — not independently tested here since it was already working code with existing coverage via `WorkOrderDetailScreenSurveyPreview`), shared `MessageState` on both screens (Task 5 and Task 6's `empty_state_shows_the_no_work_order_message`/`error_state_shows_the_message` tests, which only pass if the real shared component — with its own `icon`/`iconColors`/`title`/`subtitle`/`action` signature — is actually wired in, since the private bare-text duplicate took only a `String`).
