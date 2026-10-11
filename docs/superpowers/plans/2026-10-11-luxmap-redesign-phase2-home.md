# LuxMap Redesign Phase 2: F02 Việc Hôm Nay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign `HomeScreen.kt` (F02) to the v2.1 look — drop the gradient hero block, add a 3-metric row, replace `WorkOrderCard` with `TaskRow` inside a `ClusterSection`, add a `NextUpCard` that surfaces the single most urgent task, and replace the bottom "Bắt đầu đợt khảo sát mới" button with a conditional "Khảo sát tối nay" row — while staying strictly inside what `HomeData`/`WorkOrderSummaryItem` actually carry today.

**Architecture:** Four tasks. Task 1 restructures the header (removes `BrandHeroGradient`/`HeroStat`, adds `MetricRow` + a new `SyncPill`). Task 2 restyles the per-item presentation (`TaskRow` replaces `WorkOrderCard`, `ClusterSection` replaces the `ClusterHeader`+`ClusterCard` pair). Task 3 adds `NextUpCard` and the selection logic that picks one task to feature, plus the "Khảo sát tối nay" row. Task 4 is the manual visual pass.

**A real data gap, confirmed with the user before writing this plan (2026-10-11):** the redesign spec's `NextUpCard`/`TaskRow` assume data `WorkOrderSummaryItem` does not have — no lat/lng (so no "khoảng cách"/distance line, no "Điều hướng" button), no pole/fault condition list (so no `PoleStrip`), and no severity/priority enum (only a raw `priorityScore: Double?`, so no colored `PriorityDot`). Per the user's decision: build the reduced version with only real fields (badges, work order id, due date, cluster label, one "Xem chi tiết" CTA for `NextUpCard`; no priority dot on `TaskRow`). This mirrors the precedent already in `docs/contract-drift.md` ("F02 thẻ việc vượt dữ liệu có thật").

**Tech Stack:** Kotlin, Jetpack Compose Material3 (`VerticalDivider`, available since material3 1.2 — confirmed 1.3.1 is in use by Phase 1's final review). No new dependencies.

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (Phase 2 row), `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` §3 (`MetricRow`, `TaskRow`, `ClusterSection`, `NextUpCard` rows) and §4 ("F02 Việc hôm nay"). Current-code facts this plan is built from: `app/src/main/java/com/luxmap/feature/home/{data,ui}/*` read in full 2026-10-11, plus `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt` (confirms `WorkOrderFaultDetail` doesn't give `PoleStrip` clean data either, so deferring it isn't just a Home-specific gap).

## Global Constraints

- Branch off `dev` (fresh `git checkout dev && git pull` — includes Phase 0 (PR #54) and Phase 1 (PR #55), both merged). Branch: `style/redesign-phase2-home`. Commit scope `redesign-phase2` (still no real `FM-xx` code).
- Every commit ≤ 400 changed lines. Expect one commit per task.
- `./gradlew ktlintCheck` after every task.
- No new comments unless the WHY is genuinely non-obvious — default to none (standing user feedback, reinforced by Phase 1's final review).
- `WorkOrderSummaryItem`/`HomeData`/`HomeMetrics` (the data models) are **not touched** — this phase is UI-only; no new field is added to pretend data exists that the API doesn't send.
- Keep the mandatory 4-state `HomeUiState` handling (`Loading`/`Success`/`Empty`/`Error`) exactly as it is — this phase changes what `Success` renders, not the state machine.
- `onStartSurvey`/`onOpenWorkOrder`/`onRetry` callbacks keep their existing signatures and existing wiring in `HomeRoute` — this phase only changes which UI elements call them.

## Review Focus

- `NextUpCard`'s selection logic literally cannot implement the spec's stated order ("quá hạn > urgent > due today > priority_score") because there is no "urgent" field on `WorkOrderSummaryItem` — Task 3 drops that tier (overdue > due today > priority_score descending) and this must be visible in the code as a 3-tier comparator, not a 4-tier one silently missing a branch.
- The item chosen as `NextUpCard` must be excluded from its cluster's rendered list (spec: "Lệnh nằm trong NextUp không lặp lại ở cụm") — verify this with a cluster that has exactly 1 item (the NextUp item): that cluster should disappear entirely from the `ClusterSection` list, not render with an empty item list and a dangling header.
- `SyncPill`'s color mapping (`SyncStatus` per `HomeUiState`) is a judgment call this plan makes (not given directly by an existing field) — Task 1 documents the exact mapping so it's reviewable, not implicit.
- The "Khảo sát tối nay" row's visibility condition (`plannedSweepCount != null && plannedSweepCount > 0`) must not collapse `null` (no data source yet) and `0` (confirmed zero) into the same behavior — `FakeHomeRepository` always returns `null` today, so this row is untestable by eye without a temporary local edit; note this in the PR rather than silently skip verifying the condition itself compiles and reads correctly.
- Deleting `HeroStat`/`HeroStatDivider`/`HeroStats` must not leave any dangling private function unused — grep after Task 1 to confirm no leftover dead code ktlint/compiler didn't catch (unused *private* functions don't always trigger a warning the same way unused imports do). `EMPTY_METRICS` is kept deliberately (see Task 1 Step 2) — its continued presence is correct, not a miss.
- The metric row must stay visible across all 4 `HomeUiState` branches (zeros for `Empty`, dashes for `Loading`/`Error`), matching the pre-Phase-2 behavior of always showing the stat row — hiding it entirely for non-`Success` states would make the header jump in height every time the screen changes state, which nothing in the spec asks for.

---

### Task 1: Replace the gradient hero header with a plain header + `MetricRow` + `SyncPill`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/SyncPill.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (the `HomeHeroHeader`/`HeroStat`/`HeroStatDivider`/`HeroStats` block, current lines 194-339, and the `EMPTY_METRICS`/`heroStats` wiring at lines 121-128,148-149)

**Interfaces:**
- Produces: `SyncPill(status: SyncStatus, lastSyncedAt: Instant?, onClick: () -> Unit, modifier: Modifier = Modifier)` — a pill showing an icon + "Đã đồng bộ"/time text, colored via the existing `SyncStatus.badgeColors()`. Consumed by `HomeScreen.kt` in this same task.
- Produces (in `HomeScreen.kt`): a new private `MetricRow(metrics: HomeMetrics, modifier: Modifier = Modifier)` replacing the deleted `HeroStat` row — consumed by `HomeScreen`'s `Column` in this same task, and by Task 3 (no interface change needed there, `MetricRow` is self-contained).

- [ ] **Step 1: Create `SyncPill`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import java.time.Instant

@Composable
fun SyncPill(
    status: SyncStatus,
    lastSyncedAt: Instant?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = status.badgeColors()
    val icon =
        when (status) {
            SyncStatus.DONE -> Icons.Filled.CloudDone
            SyncStatus.SYNCING -> Icons.Filled.CloudSync
            SyncStatus.FAILED, SyncStatus.CONFLICT -> Icons.Filled.ErrorOutline
            SyncStatus.QUEUED_OFFLINE, SyncStatus.QUEUED_ONLINE -> Icons.Filled.CloudOff
        }
    val text = if (lastSyncedAt != null) DateFormatUtils.formatInstant(lastSyncedAt) else status.label()
    Row(
        modifier =
            modifier
                .background(colors.background, CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = LabelTextStyle, color = colors.text)
    }
}
```

Note: this needs `androidx.compose.foundation.layout.padding` and `androidx.compose.foundation.layout.size` imports too (`.padding(...)` and `Modifier.size(16.dp)`) — add both.

- [ ] **Step 2: Replace the hero header in `HomeScreen.kt`**

Keep `EMPTY_METRICS` (lines 148-149) as-is — it's still needed below, for the same reason it existed before this phase: the metric row stays visible across all 4 states (so the header doesn't jump in height when switching between them), it just shows zeros for `Empty` and dashes for `Loading`/`Error`. Delete only the `HeroStats`/`HomeHeroHeader`/`HeroStats.valueFor`/`HeroStat`/`HeroStatDivider` block (lines 194-339). Replace the `val heroStats = when (uiState) { ... }` block and `HomeHeroHeader(stats = heroStats, onRefresh = onRetry)` call (lines 121-128) with:

```kotlin
            val metrics =
                when (uiState) {
                    is HomeUiState.Success -> uiState.data.metrics
                    is HomeUiState.Empty -> EMPTY_METRICS
                    is HomeUiState.Loading, is HomeUiState.Error -> null
                }
            val syncStatus =
                when (uiState) {
                    is HomeUiState.Loading -> SyncStatus.SYNCING
                    is HomeUiState.Success -> if (uiState.isStale) SyncStatus.QUEUED_OFFLINE else SyncStatus.DONE
                    is HomeUiState.Empty -> SyncStatus.DONE
                    is HomeUiState.Error -> SyncStatus.FAILED
                }
            val lastSyncedAt = (uiState as? HomeUiState.Success)?.lastSyncedAt
            HomeHeader(metrics = metrics, syncStatus = syncStatus, lastSyncedAt = lastSyncedAt, onRefresh = onRetry)
```

Then add the new `HomeHeader`/`MetricRow` composables (placed where `HomeHeroHeader` used to be):

```kotlin
@Composable
private fun HomeHeader(
    metrics: HomeMetrics?,
    syncStatus: SyncStatus,
    lastSyncedAt: Instant?,
    onRefresh: () -> Unit,
) {
    val today =
        remember {
            LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi")),
            )
        }
    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = today.replaceFirstChar { it.titlecase(Locale.forLanguageTag("vi")) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = "Việc hôm nay", style = DisplayTextStyle, color = MaterialTheme.colorScheme.onSurface)
            }
            SyncPill(status = syncStatus, lastSyncedAt = lastSyncedAt, onClick = onRefresh)
        }
        Spacer(Modifier.height(Spacing.lg))
        MetricRow(metrics = metrics)
    }
}

@Composable
private fun MetricRow(metrics: HomeMetrics?) {
    val overdueColor =
        if (metrics != null && metrics.overdueCount > 0) Color(0xFFC53030) else MaterialTheme.colorScheme.onSurface
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Metric(label = "Được giao", value = metrics?.assignedCount, modifier = Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Metric(label = "Đang xử lý", value = metrics?.inProgressCount, modifier = Modifier.weight(1f))
            VerticalDivider(color = MaterialTheme.colorScheme.outline)
            Metric(
                label = "Quá hạn",
                value = metrics?.overdueCount,
                valueColor = overdueColor,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Metric(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        modifier = modifier.padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value?.toString() ?: "—",
            style = MaterialTheme.typography.headlineMedium,
            color = if (value != null) valueColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
```

Update imports: add `androidx.compose.foundation.layout.IntrinsicSize` (likely already imported — check), `androidx.compose.material3.HorizontalDivider`, `androidx.compose.material3.VerticalDivider`, `com.luxmap.core.theme.DisplayTextStyle`, `com.luxmap.core.ui.components.SyncPill`; remove `com.luxmap.core.theme.BrandHeroGradient`, `androidx.compose.foundation.border` and `androidx.compose.ui.graphics.Color`'s `Color.White` uses if `Color` itself becomes otherwise unused (it is not — `Color(0xFFC53030)` and `MaterialTheme.colorScheme.onSurface` still need the `Color` type in `valueColor: Color`, so keep the import).

- [ ] **Step 3: Grep for dead code**

Run: `grep -n "HeroStat\|BrandHeroGradient" app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt`
Expected: no matches. (`EMPTY_METRICS` is expected to still appear — it's intentionally kept, see Step 2.)

- [ ] **Step 4: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/SyncPill.kt app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt
git commit -m "style(redesign-phase2): replace gradient hero header with MetricRow and SyncPill"
```

---

### Task 2: Replace `WorkOrderCard` with `TaskRow`, `ClusterHeader`+`ClusterCard` with `ClusterSection`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (`ClusterHeader`/`ClusterCard`, current lines 341-377, and their one call site in `HomeContent`)
- Delete: `app/src/main/java/com/luxmap/core/ui/components/WorkOrderCard.kt` (one caller, the `ClusterCard` this task replaces)

**Interfaces:**
- Produces (private to `HomeScreen.kt`): `ClusterSection(label: String, items: List<WorkOrderSummaryItem>, onOpenWorkOrder: (String) -> Unit)` and `TaskRow(item: WorkOrderSummaryItem, onClick: () -> Unit)` — consumed by `HomeContent` in this same task, and by Task 3 (the cluster list with the NextUp item filtered out).

- [ ] **Step 1: Replace `ClusterHeader`+`ClusterCard` with `ClusterSection`, and `WorkOrderCard`'s content with `TaskRow`**

Replace the whole block (current lines 341-377):

```kotlin
@Composable
private fun ClusterHeader(
    label: String,
    count: Int,
) {
    Text(
        text = "$label ($count)",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

@Composable
private fun ClusterCard(
    items: List<WorkOrderSummaryItem>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Dimens.radiusLarge)),
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            WorkOrderCard(
                workOrderId = item.workOrderId,
                woStatus = item.woStatus,
                dueDate = item.dueDate,
                taskKind = item.taskKind,
                onClick = { onOpenWorkOrder(item.workOrderId) },
            )
        }
    }
}
```

with:

```kotlin
@Composable
private fun ClusterSection(
    label: String,
    items: List<WorkOrderSummaryItem>,
    onOpenWorkOrder: (workOrderId: String) -> Unit,
) {
    Column {
        Text(
            text = "$label (${items.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card)),
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 68.dp))
                }
                TaskRow(item = item, onClick = { onOpenWorkOrder(item.workOrderId) })
            }
        }
    }
}

@Composable
private fun TaskRow(
    item: WorkOrderSummaryItem,
    onClick: () -> Unit,
) {
    val status = workOrderStatusFromWire(item.woStatus)
    val kind = workOrderTaskKindFromWire(item.taskKind)
    val due = dueDateLabel(item.dueDate)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(onClick = onClick)
                .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(kind.badgeColors().background, RoundedCornerShape(Radius.control)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = kind.icon(), contentDescription = null, tint = kind.badgeColors().text)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = item.workOrderId, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                StatusBadge(text = status.label(), colors = status.badgeColors())
                Text(
                    text = due.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (due.isToday) DueTodayColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}
```

`dueDateLabel`/`DueTodayColor` were defined in the now-deleted `WorkOrderCard.kt` — move both (verbatim) into `HomeScreen.kt` as private top-level declarations, since `TaskRow` is now their only remaining caller (`WorkOrderDetailScreen.kt` and `WorkOrderCompletionScreen.kt` have their own due-date rendering — confirm with a grep in Step 3 before assuming this).

Update the one call site in `HomeContent` (currently `data.clusters.forEach { cluster -> item { ClusterHeader(...) }; item { ClusterCard(...) } }`) to `item { ClusterSection(label = cluster.clusterLabel, items = cluster.items, onOpenWorkOrder = onOpenWorkOrder) }`.

Update imports: add `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.heightIn`, `androidx.compose.material.icons.filled.ChevronRight`, `com.luxmap.core.theme.Radius`, `com.luxmap.core.theme.workOrderStatusFromWire`, `com.luxmap.core.theme.workOrderTaskKindFromWire`, `com.luxmap.core.ui.components.StatusBadge`; remove `com.luxmap.core.ui.components.WorkOrderCard`.

- [ ] **Step 2: Delete `WorkOrderCard.kt` and grep for other callers of its exported helpers**

Run: `grep -rn "dueDateLabel\|DueTodayColor" app/src/main/java --include=*.kt | grep -v HomeScreen.kt`
If this finds callers outside `HomeScreen.kt` (e.g. `WorkOrderDetailScreen.kt` may import `dueDateLabel` from `core.ui.components` directly, per Phase 1's research notes), **do not delete `WorkOrderCard.kt` entirely** — instead keep only `dueDateLabel`/`DueTodayColor` in a trimmed file (rename to `DueDateLabel.kt`) and delete only the `WorkOrderCard`/`StatusTile` composables. Ledger this as a ruling either way, since the plan can't know the grep result in advance.

```bash
rm app/src/main/java/com/luxmap/core/ui/components/WorkOrderCard.kt   # or trim, per the grep result above
```

- [ ] **Step 3: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "style(redesign-phase2): replace WorkOrderCard with TaskRow inside ClusterSection"
```

---

### Task 3: Add `NextUpCard`, next-up selection logic, and the "Khảo sát tối nay" row

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/theme/Theme.kt` (completes a Phase 0 gap — see Step 0)
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt` (`HomeContent`, current lines 151-192)

**Interfaces:**
- Produces (private to `HomeScreen.kt`): `fun HomeData.nextUp(): WorkOrderSummaryItem?` (pure function, the selection logic) and `NextUpCard(item: WorkOrderSummaryItem, clusterLabel: String, onClick: () -> Unit)`.

- [ ] **Step 0: Wire `inverseSurface`/`inverseOnSurface` in `Theme.kt` — a Phase 0 gap, caught during this plan's self-review**

Phase 0's plan (`2026-10-11-luxmap-redesign-phase0-tokens.md`) quoted the spec's M3 role table, which maps `inverseSurface`/`inverseOnSurface` to `Ink`/`Paper` in Light (and leaves Dark unspecified, "—") — but Phase 0's actual `Theme.kt` edit never added this role. Nothing has consumed it yet, so it silently fell back to M3's baseline default. `NextUpCard` (this task) is the first real consumer, so fix it now rather than have `NextUpCard` either render M3's unstyled default purple-gray, or hardcode the `Ink`/`Paper` primitives directly (which would make the card nearly invisible in dark mode, since `Ink` and the dark background `Night` are both near-black — `inverseSurface` exists specifically so it inverts correctly per theme, which a hardcoded primitive cannot do).

In `Theme.kt`'s `LuxMapLightColorScheme`, add two lines (after `onPrimaryContainer = LumenOnCont,`):

```kotlin
        inverseSurface = Ink,
        inverseOnSurface = Paper,
```

Leave `LuxMapDarkColorScheme` untouched — the spec table says "—" for dark, and M3's baseline dark-mode `inverseSurface` default (a light tone against the dark `Night` background) already gives the correct "inverts against current background" behavior the component needs; there's no v2.1-specific dark value to apply.

- [ ] **Step 0b: Build and ktlint, commit this fix on its own**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

```bash
git add app/src/main/java/com/luxmap/core/theme/Theme.kt
git commit -m "fix(redesign-phase2): wire inverseSurface/inverseOnSurface to Ink/Paper in light scheme"
```

- [ ] **Step 1: Write the selection function**

```kotlin
// Spec order is "overdue > urgent > due today > priority_score", but WorkOrderSummaryItem has
// no severity/urgent field (only a raw priorityScore) - the urgent tier is dropped, not guessed.
private fun HomeData.nextUp(): WorkOrderSummaryItem? {
    val today = LocalDate.now()
    val all = clusters.flatMap { it.items }
    fun dueDateOf(item: WorkOrderSummaryItem): LocalDate? = item.dueDate?.let(LocalDate::parse)
    return all.maxWithOrNull(
        compareBy(
            { item -> dueDateOf(item)?.let { it < today } == true },
            { item -> dueDateOf(item)?.let { it == today } == true },
            { item -> item.priorityScore ?: 0.0 },
        ),
    )
}
```

- [ ] **Step 2: Write `NextUpCard`**

```kotlin
@Composable
private fun NextUpCard(
    item: WorkOrderSummaryItem,
    clusterLabel: String,
    onClick: () -> Unit,
) {
    val status = workOrderStatusFromWire(item.woStatus)
    val kind = workOrderTaskKindFromWire(item.taskKind)
    val due = dueDateLabel(item.dueDate)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(Radius.block))
                .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "TIẾP THEO",
            style = OverlineTextStyle,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Text(
            text = item.workOrderId,
            style = HeadlineTextStyle,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StatusBadge(text = kind.label(), colors = kind.badgeColors())
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        Text(
            text = "$clusterLabel · ${due.text}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        PrimaryButton(
            text = "Xem chi tiết",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

Note: this deliberately has only one button ("Xem chi tiết"), not the spec's two — there is no second action with real data behind it (no coordinates to navigate to from this list's fields). `inverseSurface`/`inverseOnSurface` give the "ink block" look the spec wants without inventing a new token (both M3 roles are already available from `MaterialTheme.colorScheme`, unused elsewhere in this app today).

- [ ] **Step 3: Wire `NextUpCard` and the "Khảo sát tối nay" row into `HomeContent`, excluding the NextUp item from its cluster**

Replace `HomeContent`'s body (current lines 158-191, inside the `LazyColumn { ... }`):

```kotlin
        val nextUp = data.nextUp()
        if (data.metrics.overdueCount > 0) {
            item {
                InlineBanner(
                    message = "${data.metrics.overdueCount} lệnh đã quá hạn xử lý",
                    tone = InlineBannerTone.ERROR,
                )
            }
        }
        if (isStale) {
            item {
                InlineBanner(
                    message = "Đang xem dữ liệu đã lưu, cập nhật lúc ${DateFormatUtils.formatInstant(lastSyncedAt)}",
                    tone = InlineBannerTone.OFFLINE,
                )
            }
        }
        if (nextUp != null) {
            val clusterLabel = data.clusters.first { cluster -> cluster.items.any { it.workOrderId == nextUp.workOrderId } }.clusterLabel
            item {
                NextUpCard(item = nextUp, clusterLabel = clusterLabel, onClick = { onOpenWorkOrder(nextUp.workOrderId) })
            }
        }
        if (data.metrics.plannedSweepCount != null && data.metrics.plannedSweepCount > 0) {
            item {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onStartSurvey)
                            .padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Khảo sát tối nay", style = MaterialTheme.typography.titleMedium)
                    Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
        data.clusters.forEach { cluster ->
            val remaining = cluster.items.filterNot { nextUp != null && it.workOrderId == nextUp.workOrderId }
            if (remaining.isNotEmpty()) {
                item { ClusterSection(label = cluster.clusterLabel, items = remaining, onOpenWorkOrder = onOpenWorkOrder) }
            }
        }
```

The old bottom `PrimaryButton("Bắt đầu đợt khảo sát mới", ...)` is gone — replaced by the "Khảo sát tối nay" row above, per spec §4 F02: "Bỏ PrimaryButton... thay bằng hàng 'Khảo sát tối nay'... (giữ một lối vào, không nhân đôi)."

Update imports: add `com.luxmap.core.theme.HeadlineTextStyle`, `com.luxmap.core.theme.OverlineTextStyle`.

- [ ] **Step 4: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt
git commit -m "style(redesign-phase2): add NextUpCard, next-up selection, and the Khao sat toi nay row"
```

---

### Task 4: Full-screen visual regression pass

**Files:** none (manual verification task — no code changes)

- [ ] **Step 1: Walk F02 in all 4 UI states, light theme (F02 is not in CLAUDE.md's forced-dark list)**

Same environment constraint as Phases 0-1 (no `adb`/emulator). If a device is available: Loading (skeleton still unaffected by this phase — confirm it still renders), Success with an overdue item (NextUpCard shows, overdue banner shows, MetricRow's "Quá hạn" number is red), Success with `isStale = true` (offline banner shows), Empty (unaffected — still the shared `MessageState`), Error (unaffected). Specifically check: the cluster that contained the NextUp item either disappears (if it only had that one item) or still shows its other items without the NextUp one duplicated.

- [ ] **Step 2: Open PR**

Target `dev`. Title: `style(redesign-phase2): redesign F02 home screen`. Body notes the data-gap decisions (no PoleStrip/distance/priority-dot — confirmed with the user 2026-10-11) and the Task 4 manual-check gap, same pattern as Phases 0-1.
