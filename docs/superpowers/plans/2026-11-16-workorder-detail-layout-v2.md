# F09 Chi tiết lệnh — Layout Round Two Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the F09 header clipping, truncated due date, repeated overdue copy, fault severity badge and "Bắt buộc/Không bắt buộc" labels with a compact one-column layout, without changing any business rule.

**Architecture:** Pure helpers for the header title and the overdue line (unit-tested). The summary card switches from a 2-column grid to label–value rows that wrap. Copy changes live in `WorkOrderMessages.kt` / `WorkOrderCta.kt`; the shared `BottomActionBar` hint becomes left-aligned. Section composables drop the severity badge and render a red `*` for required fields.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01, Material 3), JUnit (unit), Compose UI Test (androidTest).

**Spec:** `docs/design-system/screens/F09_WORK_ORDER_DETAIL.md` §12 (approved 2026-11-16, V1–V3).

## Global Constraints

- Branch `style/workorder-detail-layout-v2` (from `dev`, holds the spec commit). Never commit to `main`.
- Each commit ≤ 400 changed lines; messages `type(workorder): description`, English, imperative, lowercase. **No Claude attribution, no `Co-Authored-By`.** No new code comments.
- No business change: `allowed_actions`, `workOrderScheduleWindow`, pending-cancellation lock, `canSubmit()`, sheets, sync, API, Room stay as is.
- Only theme tokens in `feature/`; no primitive colors, no `Color(0x…)`, no legacy `*TextStyle`.
- Copy (exact):
  - Header: `Lệnh sửa chữa` / `Lệnh kiểm tra` / `Lệnh khảo sát` / `Lệnh đo GPS cột`; Loading/Empty/Error: `Chi tiết lệnh`. No subtitle.
  - Summary rows: `Hạn xử lý`, `Lịch sửa chữa` / `Lịch kiểm tra` / `Lịch khảo sát` / `Lịch đo GPS`, `Giai đoạn hiện tại`, `Mã lệnh`. Overdue line `Quá hạn N ngày`; due today line `Hạn hôm nay`. No due date → `Chưa có hạn`; no schedule → `Chưa có lịch`.
  - Banner overdue-to-complete: title `Quá hạn nộp báo cáo`, body `Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì.`
  - Banner overdue-to-start: title `Quá hạn bắt đầu`, body `Liên hệ Kỹ sư bảo trì để được giao lại.`
  - CTA hint when overdue (start or complete): `Đã khoá do quá hạn`. Other hints unchanged.
  - Required field: label + ` *` in `colorScheme.error`; accessibility text `{label}, bắt buộc`. Optional: label only.
- Unit: `./gradlew testLocalDebugUnitTest --tests "<fqcn>"`. Device (SM-A075F): `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`. Run `./gradlew ktlintCheck` each task. Stop after each task for the user.

## Review Focus

1. **Unknown / unparseable `due_date`** (`"2026-13-40"`): summary must show `Chưa có hạn`, no crash, no overdue line. Unit test in Task 1.
2. **`due_date` null but schedule overdue** (banner by `scheduled_date`): banner still shows; summary shows `Chưa có hạn` without a red line — no contradiction text. UI test in Task 2.
3. **Very long title / long UUID code at 1.5× font, 360dp:** header stays one line; title and `Mã lệnh` wrap fully in the card. Large-font test in Task 4.
4. **Fault without coordinates**: row still shows `Chưa có toạ độ` and no severity badge. UI test in Task 3.
5. **Required asterisk with TalkBack**: the label node reads `…, bắt buộc`, the `*` is not read as "sao". UI test in Task 3.

---

### Task 1: One-line header and label–value summary card

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderSummaryText.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt` (top bar ~176-185, `WorkOrderSummaryCard` ~566-631, delete `SummaryMetric` ~634-667)
- Create: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderSummaryTextTest.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt`

**Interfaces:**
- Produces:
  - `fun workOrderHeaderTitle(kind: WorkOrderTaskKind): String`
  - `fun workOrderScheduleLabel(kind: WorkOrderTaskKind): String`
  - `data class DueSummary(val dateText: String, val statusText: String?, val isOverdue: Boolean, val isToday: Boolean)`
  - `fun dueSummary(dueDate: String?, today: LocalDate): DueSummary`

- [ ] **Step 1: Failing unit tests** — `WorkOrderSummaryTextTest.kt`:

```kotlin
package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.theme.WorkOrderTaskKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-11-16")

class WorkOrderSummaryTextTest {
    @Test
    fun `header title names the work order kind`() {
        assertEquals("Lệnh sửa chữa", workOrderHeaderTitle(WorkOrderTaskKind.REPAIR))
        assertEquals("Lệnh kiểm tra", workOrderHeaderTitle(WorkOrderTaskKind.INSPECTION))
        assertEquals("Lệnh khảo sát", workOrderHeaderTitle(WorkOrderTaskKind.SURVEY))
        assertEquals("Lệnh đo GPS cột", workOrderHeaderTitle(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }

    @Test
    fun `schedule label per kind`() {
        assertEquals("Lịch sửa chữa", workOrderScheduleLabel(WorkOrderTaskKind.REPAIR))
        assertEquals("Lịch đo GPS", workOrderScheduleLabel(WorkOrderTaskKind.POLE_GPS_UPDATE))
    }

    @Test
    fun `overdue due date has its own status line`() {
        val due = dueSummary("2026-11-15", TODAY)
        assertEquals("15/11/2026", due.dateText)
        assertEquals("Quá hạn 1 ngày", due.statusText)
        assertTrue(due.isOverdue)
    }

    @Test
    fun `due today`() {
        val due = dueSummary("2026-11-16", TODAY)
        assertEquals("Hạn hôm nay", due.statusText)
        assertTrue(due.isToday)
    }

    @Test
    fun `future due date has no status line`() {
        val due = dueSummary("2026-11-20", TODAY)
        assertEquals("20/11/2026", due.dateText)
        assertNull(due.statusText)
        assertFalse(due.isOverdue)
    }

    @Test
    fun `missing or malformed due date`() {
        assertEquals(DueSummary("Chưa có hạn", null, isOverdue = false, isToday = false), dueSummary(null, TODAY))
        assertEquals(DueSummary("Chưa có hạn", null, isOverdue = false, isToday = false), dueSummary("2026-13-40", TODAY))
    }
}
```

- [ ] **Step 2: Run, expect FAIL** (`Unresolved reference`): `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.WorkOrderSummaryTextTest"`

- [ ] **Step 3: Implement `WorkOrderSummaryText.kt`**

```kotlin
package com.luxmap.feature.workorder.ui.detail

import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.WorkOrderTaskKind
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

fun workOrderHeaderTitle(kind: WorkOrderTaskKind): String =
    when (kind) {
        WorkOrderTaskKind.REPAIR -> "Lệnh sửa chữa"
        WorkOrderTaskKind.INSPECTION -> "Lệnh kiểm tra"
        WorkOrderTaskKind.SURVEY -> "Lệnh khảo sát"
        WorkOrderTaskKind.POLE_GPS_UPDATE -> "Lệnh đo GPS cột"
    }

fun workOrderScheduleLabel(kind: WorkOrderTaskKind): String =
    when (kind) {
        WorkOrderTaskKind.REPAIR -> "Lịch sửa chữa"
        WorkOrderTaskKind.POLE_GPS_UPDATE -> "Lịch đo GPS"
        WorkOrderTaskKind.SURVEY -> "Lịch khảo sát"
        WorkOrderTaskKind.INSPECTION -> "Lịch kiểm tra"
    }

data class DueSummary(
    val dateText: String,
    val statusText: String?,
    val isOverdue: Boolean,
    val isToday: Boolean,
)

fun dueSummary(
    dueDate: String?,
    today: LocalDate,
): DueSummary {
    val parsed =
        try {
            dueDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
        } catch (e: DateTimeParseException) {
            null
        } ?: return DueSummary("Chưa có hạn", null, isOverdue = false, isToday = false)
    val dateText = DateFormatUtils.formatPlainDate(dueDate)
    return when {
        parsed == today -> DueSummary(dateText, "Hạn hôm nay", isOverdue = false, isToday = true)
        parsed.isBefore(today) ->
            DueSummary(dateText, "Quá hạn ${ChronoUnit.DAYS.between(parsed, today)} ngày", isOverdue = true, isToday = false)
        else -> DueSummary(dateText, null, isOverdue = false, isToday = false)
    }
}
```

- [ ] **Step 4: Run unit tests, expect PASS.**

- [ ] **Step 5: Failing UI tests** — in `WorkOrderDetailScreenTest.kt` replace `a_loaded_work_order_shows_its_title_and_kind_with_code_in_the_top_bar` and `the_summary_shows_the_current_stage_without_a_multi_step_tracker` with:

```kotlin
    @Test
    fun the_top_bar_shows_only_the_kind_and_the_card_holds_title_and_full_code() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        composeRule.onNodeWithText("Lệnh sửa chữa").assertExists()
        composeRule.onNodeWithText("Sửa chữa · WO-1042").assertDoesNotExist()
        composeRule.onNodeWithText("Sửa đèn tuyến A").assertExists()
        composeRule.onNodeWithText("Mã lệnh").assertExists()
        composeRule.onNodeWithText("WO-1042").assertExists()
        composeRule.onNodeWithContentDescription("Quay lại").assertExists()
    }

    @Test
    fun the_summary_shows_the_current_stage_as_a_row_without_a_multi_step_tracker() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        composeRule.onNodeWithText("Giai đoạn hiện tại").assertExists()
        composeRule.onAllNodesWithText("Sửa chữa").onFirst().assertExists()
        listOf("Khảo sát", "Duyệt #1", "Kiểm tra", "Duyệt #2").forEach { label ->
            composeRule.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test
    fun an_overdue_due_date_shows_the_date_and_a_separate_overdue_line() {
        val detail = repairDetail().copy(dueDate = LocalDate.now().minusDays(2).toString())
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Quá hạn 2 ngày").assertExists()
    }
```

In `a_cancelled_work_order_shows_its_status_but_no_current_stage` keep `assertDoesNotExist()` for `"Giai đoạn hiện tại"` (substring). Run the class on device; expect the 3 new tests to FAIL.

- [ ] **Step 6: Implement in `WorkOrderDetailScreen.kt`**

Top bar:

```kotlin
            topBar = {
                val title =
                    if (uiState is WorkOrderDetailUiState.Success) {
                        workOrderHeaderTitle(workOrderTaskKindFromWire(uiState.detail.taskKind))
                    } else {
                        "Chi tiết lệnh"
                    }
                LuxChildTopBar(title = title, onBack = onBack)
            },
```

Replace the body of `WorkOrderSummaryCard` after the badge row with:

```kotlin
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = detail.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.md))
        val due = remember(detail.dueDate) { dueSummary(detail.dueDate, LocalDate.now()) }
        val dueColor =
            when {
                due.isOverdue -> LuxTheme.colors.dueOverdue
                due.isToday -> LuxTheme.colors.dueToday
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SummaryRow(label = "Hạn xử lý") {
                Text(text = due.dateText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                due.statusText?.let { status ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, tint = dueColor, modifier = Modifier.size(16.dp))
                        Text(text = status, style = MaterialTheme.typography.bodyMedium, color = dueColor)
                    }
                }
            }
            SummaryRow(label = workOrderScheduleLabel(kind)) {
                Text(text = scheduleValue, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            if (status != WorkOrderStatus.CANCELLED) {
                SummaryRow(label = "Giai đoạn hiện tại") {
                    Text(text = kind.label(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            SummaryRow(label = "Mã lệnh") {
                Text(
                    text = detail.workOrderId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
```

Add:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryRow(
    label: String,
    value: @Composable ColumnScope.() -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.md),
        )
        Column(horizontalAlignment = Alignment.End, content = value)
    }
}
```

Delete `SummaryMetric`, the old `due`/`dueColor`/`scheduleLabel` locals (keep `scheduleValue`), the divider + "Giai đoạn hiện tại: …" text. Remove now-unused imports (`ktlintFormat`).

- [ ] **Step 7: Run unit + device class + ktlint, expect PASS.** Update any remaining assertion in `WorkOrderDetailScreenTest` that relied on `"Sửa chữa · WO-1042"` or `"Giai đoạn hiện tại: Sửa chữa"`.

- [ ] **Step 8: Commit** `style(workorder): show the kind in a one-line header and summary details as wrapping rows`

---

### Task 2: Short overdue copy and a left-aligned CTA hint

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderMessages.kt:39-55`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderCta.kt` (lines with `Đã quá hạn bắt đầu…` and `Đã quá hạn nộp báo cáo…`, 3 places)
- Modify: `app/src/main/java/com/luxmap/core/ui/components/BottomActionBar.kt:100-123`
- Modify: `app/src/test/java/com/luxmap/feature/workorder/ui/detail/WorkOrderMessagesTest.kt`
- Modify: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt`

- [ ] **Step 1: Failing unit tests** — in `WorkOrderMessagesTest.kt`, next to the existing title assertions at lines ~55 and ~62, add:

```kotlin
        assertEquals("Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì.", result.prominent?.body)
```
(overdue-to-complete case) and
```kotlin
        assertEquals("Liên hệ Kỹ sư bảo trì để được giao lại.", result.prominent?.body)
```
(overdue-to-start case).

- [ ] **Step 2: Failing UI tests** — add to `WorkOrderDetailScreenTest.kt`:

```kotlin
    @Test
    fun an_overdue_report_shows_one_short_hint_above_the_locked_button() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val detail = repairDetail(allowedActions = listOf("complete")).copy(scheduledDate = yesterday)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Đã khoá do quá hạn").assertExists()
        composeRule.onNodeWithText("liên hệ Kỹ sư bảo trì để xử lý", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì.").assertExists()
    }

    @Test
    fun a_schedule_overdue_order_without_a_due_date_shows_the_banner_and_no_overdue_line() {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val detail = repairDetail(allowedActions = listOf("complete")).copy(scheduledDate = yesterday, dueDate = null)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = detail))
        composeRule.onNodeWithText("Quá hạn nộp báo cáo").assertExists()
        composeRule.onNodeWithText("Chưa có hạn").assertExists()
        composeRule.onNodeWithText("Quá hạn 1 ngày").assertDoesNotExist()
    }
```

Run unit + device; expect the new assertions to FAIL.

- [ ] **Step 3: Implement**
- `WorkOrderMessages.kt`: overdue-to-complete `body = "Biểu mẫu đã khoá. Liên hệ Kỹ sư bảo trì."`; overdue-to-start `body = "Liên hệ Kỹ sư bảo trì để được giao lại."`. Delete the now-unused `dueText` local.
- `WorkOrderCta.kt`: replace `"Đã quá hạn bắt đầu · liên hệ Kỹ sư bảo trì để được giao lại"` and all three `"Đã quá hạn nộp báo cáo · liên hệ Kỹ sư bảo trì để xử lý"` with `"Đã khoá do quá hạn"`.
- `BottomActionBar.kt` hint row: add `.fillMaxWidth()` to the Row modifier chain (first), and change the Text `textAlign = TextAlign.Center` to `TextAlign.Start`.

- [ ] **Step 4: Run unit + device + ktlint, expect PASS.**

- [ ] **Step 5: Commit** `style(workorder): shorten the overdue banner and lock hint`

---

### Task 3: Fault rows without severity and red asterisks for required fields

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/FaultList.kt:85-118`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/sections/CompletionFields.kt:39-57`
- Modify: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreenTest.kt`

- [ ] **Step 1: Failing UI tests** — replace in `a_repair_in_progress_lists_only_the_real_completion_fields_without_fake_steps` the two lines asserting `"Bắt buộc"` count 2 and `"Không bắt buộc"` exists with:

```kotlin
        composeRule.onNodeWithText("Bắt buộc").assertDoesNotExist()
        composeRule.onNodeWithText("Không bắt buộc").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Kết quả sửa chữa, bắt buộc").assertExists()
        composeRule.onNodeWithContentDescription("Ảnh sau sửa chữa, bắt buộc").assertExists()
        composeRule.onNodeWithText("Vật tư đã thay").assertExists()
        composeRule.onNodeWithContentDescription("Vật tư đã thay, bắt buộc").assertDoesNotExist()
```

Add:

```kotlin
    @Test
    fun fault_rows_have_no_severity_badge() {
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail()))
        composeRule.onNodeWithText("Cao").assertDoesNotExist()
        composeRule.onNodeWithText("POLE-0047", substring = true).assertExists()
    }

    @Test
    fun a_fault_without_coordinates_says_so_and_has_no_severity_badge() {
        val noLocation = repairDetail().faults.single().copy(lat = 0.0, lng = 0.0)
        setContent(uiState = WorkOrderDetailUiState.Success(detail = repairDetail().copy(faults = listOf(noLocation))))
        composeRule.onNodeWithText("Chưa có toạ độ").assertExists()
        composeRule.onNodeWithText("Cao").assertDoesNotExist()
    }
```

(`hasLocation()` is `lat != 0.0 || lng != 0.0` in `WorkOrderDetail.kt`, so `0.0, 0.0` means no coordinates.) Run on device; expect FAIL.

- [ ] **Step 2: Implement**
- `FaultList.kt` `FaultItem`: delete `val priority = …` and the `StatusBadge(text = priority.label(), …)` line; remove unused imports (`severityFromWire`, `StatusBadge` if no longer used — `InspectionOutcomeBadge` still uses `StatusBadge`, keep it then).
- `CompletionFields.kt` `CompletionFieldHeader`:

```kotlin
@Composable
internal fun CompletionFieldHeader(
    title: String,
    required: Boolean,
) {
    val label =
        buildAnnotatedString {
            append(title)
            if (required) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append(" *") }
            }
        }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier.fillMaxWidth().semantics {
                heading()
                contentDescription = if (required) "$title, bắt buộc" else title
            },
    )
}
```

Imports: `androidx.compose.ui.text.SpanStyle`, `buildAnnotatedString`, `withStyle`, `androidx.compose.ui.semantics.contentDescription`.

- [ ] **Step 3: Run device class + ktlint, expect PASS.**

- [ ] **Step 4: Commit** `style(workorder): drop fault severity badges and mark required fields with a red asterisk`

---

### Task 4: Large-font coverage, full device run, doc status

**Files:**
- Modify: `app/src/androidTest/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailLargeFontTest.kt`
- Modify: `docs/design-system/screens/F09_WORK_ORDER_DETAIL.md` (§12 status line)

- [ ] **Step 1: Update and extend the large-font test** — replace `the_summary_card_stays_readable_at_large_font_on_a_narrow_screen` with:

```kotlin
    @Test
    fun header_stays_one_line_and_due_rows_are_fully_visible_at_large_font() {
        screen(
            detail(taskKind = "repair", woStatus = "in_progress", allowedActions = emptyList())
                .copy(dueDate = java.time.LocalDate.now().minusDays(3).toString(), workOrderId = "a3f9c2e1-7b4d-4c1e-9f2a-1234567890ab"),
        )
        val header = composeRule.onNodeWithText("Lệnh sửa chữa", useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("header clipped", header.boundsInRoot.height >= header.size.height - 1f)
        composeRule.onNodeWithText("Hạn xử lý").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Quá hạn 3 ngày").performScrollTo().assertIsDisplayed()
        val code = composeRule.onNodeWithText("a3f9c2e1-7b4d-4c1e-9f2a-1234567890ab").performScrollTo().fetchSemanticsNode()
        assertTrue("code clipped", code.boundsInRoot.width >= code.size.width - 1f)
    }
```

Add `import org.junit.Assert.assertTrue`.

- [ ] **Step 2: Run the whole detail androidTest package on device**

`./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.package=com.luxmap.feature.workorder.ui.detail"` — expect all PASS. Also run `./gradlew testLocalDebugUnitTest --tests "com.luxmap.feature.workorder.ui.detail.*" ktlintCheck`.

- [ ] **Step 3: Update spec status** — in §12 change `**CHỜ DUYỆT BẢN VIẾT.**` line to `**ĐÃ DUYỆT VÀ ĐÃ CODE XONG 2026-11-16** trên nhánh style/workorder-detail-layout-v2. Còn nợ: kiểm tra tay trên máy đã đăng nhập (Light/Night Field, 200%).`

- [ ] **Step 4: Commit** `test(workorder): cover the round two detail layout at large font`
