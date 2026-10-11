# LuxMap Redesign Phase 1: Shared Component Restyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the shared `core/ui/components` that already have 2+ real call sites today to the v2.1 redesign look, and consolidate `MessageState` (today duplicated across 6 screens, one complete version and five weaker ones) into one shared component — without touching any component that has zero current consumers (those move to the screen phase that will actually build them first; see the roadmap's Phase 1 row for the full re-scoping rationale).

**Architecture:** Five independent tasks, each touching 1-3 files with a small, contained blast radius:
1. `StatusBadge` gets Phase 0's new radius/typography tokens; `TextOnlyStatusBadge` (1 call site) is replaced outright by a new `PriorityDot` that fixes the exact low-contrast bug the spec calls out (priority-colored text on a light background).
2. `PrimaryButton` gets an explicit height (`PrimaryButtonHeight`, 56dp — defined in Phase 0 for exactly this, but never applied until now) and shape (`Radius.control`).
3. `BottomNavBar` gets the new pill-style selected indicator (`primaryContainer`/`onPrimaryContainer`, both wired in Phase 0's `Theme.kt`) and the new badge-scale label text.
4. A new `InlineBanner(message, tone)` replaces `OfflineBanner` and `ErrorBanner` (not `InlineErrorText`, which is a different UI shape for field-level errors and stays untouched) at all 4 real call sites.
5. `MessageState` (today a complete `BoxScope` composable private to `HomeScreen.kt`) is promoted to `core/ui/components/MessageState.kt` unchanged in shape, and `HomeScreen.kt`'s private copy is deleted in favor of the shared one. The five other screens with weaker bare-text duplicates are **not** touched here — they adopt the shared component in their own redesign phase (tracked in the roadmap), since touching them now would be redesigning a screen this plan doesn't own.

**Tech Stack:** Kotlin, Jetpack Compose Material3. No new dependencies. Everything referenced (`Radius`, `PrimaryButtonHeight`, `BadgeTextStyle`, `LabelTextStyle`, `SyncStatus.badgeColors()`) already exists after Phase 0 (merged to `dev`, see `docs/superpowers/plans/2026-10-11-luxmap-redesign-phase0-tokens.md`).

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (Phase 1 row, re-scoped 2026-10-11 after a real-call-site audit — see that row's note for why `PoleStrip`/`TaskRow`/`AppTopBar`/`BottomActionBar`/etc. are NOT in this plan). Component-level detail: `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` §3 (component inventory — `StatusBadge`, `PriorityDot`, `InlineBanner`, `MessageState` rows) and §2.4 (`Radius`/`Dimens` tokens this plan applies). Current-code findings this plan is built from: a 2026-10-11 research pass that read every file this plan touches and grepped every call site (not re-cited file-by-file below; the file/line references in each task are from that pass).

## Global Constraints

- Branch off `dev` (fresh `git checkout dev && git pull` — already includes Phase 0's merged tokens, PR #54), never `main`. Branch: `style/redesign-phase1-shared-components`. No real `FM-xx` code exists yet for this work either (same situation as Phase 0) — commit scope stays `redesign-phase1`.
- Every commit ≤ 400 changed lines (CLAUDE.md). Each task below is small; expect one commit per task (5 total), same pattern as Phase 0.
- `./gradlew ktlintCheck` after every task, fix before moving on.
- No new comments unless the WHY is genuinely non-obvious (standing user feedback) — prefer none.
- `AssetCondition`, `SyncStatus`, `WorkOrderStatus`, `WorkOrderPriority`, `WorkOrderTaskKind` and their `badgeColors()`/`label()`/`icon()` functions in `core/theme/Color.kt` are **not touched** — this phase only changes how `StatusBadge`/`InlineBanner` *use* those existing color pairs, never the pairs themselves.
- Every screen whose call site changes (`WorkOrderDetailScreen`, `HomeScreen`, `LoginScreen`, `WorkOrderCompletionScreen`) must still compile and its existing behavior (which banner/badge shows, when) must be unchanged — only the visual presentation changes, not the logic that decides what to show.

## Review Focus

- Task 4's `InlineBanner` deliberately changes `OfflineBanner`'s color source from `MaterialTheme.colorScheme.surfaceVariant`/`onSurfaceVariant` (theme-aware, changes with Light/Dark) to `SyncStatus.QUEUED_OFFLINE.badgeColors()` (fixed hex, same in both themes today — this matches the existing, pre-Phase-1 behavior of every other `SyncStatus`-colored badge in the app, e.g. `SubmitScreen.kt`, so it is not a new inconsistency, but it IS a real behavior change for this specific banner). Confirm this was a deliberate spec-driven choice (§3's `InlineBanner` row literally says "màu FAILED hoặc QUEUED_OFFLINE"), not an accidental contrast regression, when reviewing Task 4.
- Task 1 removes `TextOnlyStatusBadge` entirely once its one call site (`WorkOrderDetailScreen.kt:230`) moves to `PriorityDot` — grep for `TextOnlyStatusBadge` after Task 1 to confirm zero remaining references before deleting the function (not just updating the one known call site).
- Task 3's `BottomNavBar` restyle deliberately does **not** implement "icon FILL khi active" from the spec's component table — today's `BottomNavItem` carries one `ImageVector` per tab (already `Icons.Filled.*`, no outline variant exists), so an active/inactive icon swap would need a `BottomNavItem` API change (a second icon field) cascading into its one call site (`NavGraph.kt:50-55`). Scoped out of this task as a separate, larger change; flagged here so it isn't mistaken for an oversight.
- Task 5 keeps `MessageState` as a `BoxScope` extension function (matching `HomeScreen.kt`'s current receiver type) rather than a plain `@Composable` — confirm `HomeScreen.kt`'s call sites are still inside a `Box` after the move (they are: `HomeScreen.kt:129`), since a receiver-type change would be a silent breaking change for the one real caller.
- No commit in this plan should touch `app/src/main/java/com/luxmap/core/theme/*` — all tokens this plan uses already exist from Phase 0. If any task needs a token that doesn't exist, that's a plan defect to ledger as a ruling, not a reason to add a new token inline.
- Task 2 changes `PrimaryButton` from `defaultMinSize(minHeight = ...)` (can grow taller for multi-line content) to a fixed `.height(PrimaryButtonHeight)` (clips content taller than 56dp). All 21 current call sites pass short, single-line button text today, so this should be a non-issue, but a build/visual check should specifically look at the longest button labels in the app (e.g. `WorkOrderCompletionScreen.kt`'s submit button, `SurveyPlanScreen.kt`'s CTAs) for clipped text, not just confirm the app compiles.

---

### Task 1: Restyle `StatusBadge`, replace `TextOnlyStatusBadge` with `PriorityDot`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/ui/components/StatusBadge.kt` (49 lines, full content read 2026-10-11)
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt:230` (the only `TextOnlyStatusBadge` call site)

**Interfaces:**
- Consumes: `Radius.badge` (6.dp), `BadgeTextStyle` (13sp/18sp/600), `Spacing.sm`/`Spacing.xs` — all from Phase 0.
- Produces: `StatusBadge(text, colors, modifier)` (signature unchanged, only internal styling changes — every existing call site in `SubmitScreen.kt`, `WorkOrderDetailScreen.kt`, `PoleQuickViewBottomSheet.kt`, `WorkOrderCompletionScreen.kt`, `PoleDetailScreen.kt`, `WorkOrderCard.kt` keeps working with no call-site change needed). New `PriorityDot(text: String, dotColor: Color, modifier: Modifier = Modifier)` — consumed by `WorkOrderDetailScreen.kt` in this same task; `TextOnlyStatusBadge` is deleted.

- [ ] **Step 1: Restyle `StatusBadge`**

In `StatusBadge.kt`, replace the `StatusBadge` composable body (current lines 24-32):

```kotlin
    Text(
        text = text,
        color = colors.text,
        style = MaterialTheme.typography.bodySmall,
        modifier =
            modifier
                .background(color = colors.background, shape = RoundedCornerShape(Dimens.radiusSmall))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
```

with:

```kotlin
    Text(
        text = text,
        color = colors.text,
        style = BadgeTextStyle,
        modifier =
            modifier
                .background(color = colors.background, shape = RoundedCornerShape(Radius.badge))
                .padding(horizontal = Spacing.sm, vertical = 3.dp),
    )
```

(3dp vertical padding has no matching named `Spacing` token — the spec's component table gives "padding 3×8" exactly, and the codebase already uses bare `.dp` literals for one-off values like this elsewhere, e.g. `HomeScreen.kt`'s `MessageState` icon sizes.)

- [ ] **Step 2: Delete `TextOnlyStatusBadge`, add `PriorityDot`**

Delete the whole `TextOnlyStatusBadge` function (current lines 35-49) and replace it with:

```kotlin
// PriorityDot — 8dp colored dot + onSurface-colored text, replaces TextOnlyStatusBadge.
// The spec explicitly calls out not using the priority color as text color on a light
// background (e.g. Amber500 text is ~2.2:1 contrast) — the dot carries the color instead.
@Composable
fun PriorityDot(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .background(color = dotColor, shape = CircleShape),
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
```

Update the file's imports: add `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.layout.width`, `androidx.compose.foundation.shape.CircleShape`, `androidx.compose.ui.Alignment`, `com.luxmap.core.theme.BadgeTextStyle`, `com.luxmap.core.theme.Radius`, `com.luxmap.core.theme.Spacing`; remove `androidx.compose.ui.unit.dp` only if nothing else in the file still needs a raw `.dp` (it does — `8.dp`/`3.dp` above — keep it); `com.luxmap.core.theme.Dimens` import is no longer used once `radiusSmall` is gone — remove it.

- [ ] **Step 3: Update the one `TextOnlyStatusBadge` call site**

In `WorkOrderDetailScreen.kt`, replace line 230:

```kotlin
        TextOnlyStatusBadge(text = priority.label(), color = priority.color())
```

with:

```kotlin
        PriorityDot(text = priority.label(), dotColor = priority.color())
```

- [ ] **Step 4: Grep to confirm no other `TextOnlyStatusBadge` references remain**

Run: `grep -rn "TextOnlyStatusBadge" app/src/main/java`
Expected: no matches (the function was deleted in Step 2 and its only caller updated in Step 3).

- [ ] **Step 5: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/StatusBadge.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt
git commit -m "style(redesign-phase1): restyle StatusBadge and replace TextOnlyStatusBadge with PriorityDot"
```

---

### Task 2: Restyle `PrimaryButton`

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/ui/components/PrimaryButton.kt` (43 lines, full content read 2026-10-11)

**Interfaces:**
- Consumes: `PrimaryButtonHeight` (56.dp), `Radius.control` (14.dp) — both from Phase 0.
- Produces: `PrimaryButton(text, onClick, modifier, enabled, trailingIcon)` — signature unchanged, so all 21 existing call sites (`HomeScreen`, `LoginScreen`, `SubmitScreen`, `SurveyPlanScreen`, `CoverageScreen`, `WorkOrderDetailScreen`, `WorkOrderCompletionScreen`, `MapScreen`, `PoleDetailScreen`) keep working with no call-site change.

- [ ] **Step 1: Give the button an explicit height and shape**

Replace the `Button(...)` call (current lines 27-35):

```kotlin
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
```

with:

```kotlin
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(PrimaryButtonHeight),
        shape = RoundedCornerShape(Radius.control),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
```

(This is a 56dp fixed height, not a 48dp *minimum* — the spec's `PrimaryButtonHeight` token is specifically "primary button height," and every current call site either fills a row/column width with no competing height constraint or is already inside a `fillMaxWidth()` column, so a fixed height here does not conflict with any existing layout.)

Update imports: add `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.shape.RoundedCornerShape`, `com.luxmap.core.theme.PrimaryButtonHeight`, `com.luxmap.core.theme.Radius`; remove `androidx.compose.foundation.layout.defaultMinSize` and `com.luxmap.core.theme.Dimens` (its only use, `minTouchTarget`, is gone — `PrimaryButtonHeight` is a separate top-level `val`, not a member of the `Dimens` object).

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/PrimaryButton.kt
git commit -m "style(redesign-phase1): give PrimaryButton an explicit v2.1 height and shape"
```

---

### Task 3: Restyle `BottomNavBar`'s selected-tab indicator and label

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/ui/components/BottomNavBar.kt` (47 lines, full content read 2026-10-11)

**Interfaces:**
- Consumes: `MaterialTheme.colorScheme.primaryContainer`/`onPrimaryContainer` (wired in Phase 0's `Theme.kt`), `BadgeTextStyle`, `NavBarHeight` (76.dp).
- Produces: `BottomNavBar(items, currentRoute, onItemSelected)` — signature unchanged, one call site (`navigation/NavGraph.kt:110`) needs no change.

- [ ] **Step 1: Update the stale comment and the colors/label style**

Replace lines 19-47:

```kotlin
// 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân (mục A5 đặc tả) — danh sách item
// do navigation/ truyền vào khi wire thật, tránh đoán trước icon/thứ tự tab ở đây.
// Colors follow the Design System: the active item uses color.navigation.active (this is the
// theme's primary: navy in Light, green in Dark) on a normal surface, not a filled navy bar.
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onItemSelected(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
```

with:

```kotlin
// 4 tab: Việc hôm nay · Khảo sát · Bản đồ · Cá nhân (mục A5 đặc tả) — danh sách item
// do navigation/ truyền vào khi wire thật, tránh đoán trước icon/thứ tự tab ở đây.
// The selected tab's pill indicator uses primaryContainer/onPrimaryContainer (v2.1 token,
// Phase 0) instead of the old plain primary/surfaceVariant pair. Icon FILL-when-active
// (spec §3) is not implemented here — see this plan's Review Focus for why.
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    currentRoute: String?,
    onItemSelected: (BottomNavItem) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.height(NavBarHeight),
    ) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onItemSelected(item) },
                icon = { Icon(imageVector = item.icon, contentDescription = null) },
                label = { Text(item.label, style = BadgeTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            )
        }
    }
}
```

Update imports: add `androidx.compose.foundation.layout.height`, `androidx.compose.ui.Modifier`, `com.luxmap.core.theme.BadgeTextStyle`, `com.luxmap.core.theme.NavBarHeight`.

- [ ] **Step 2: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/BottomNavBar.kt
git commit -m "style(redesign-phase1): restyle BottomNavBar selected-tab pill and label"
```

---

### Task 4: Add `InlineBanner`, retire `OfflineBanner`/`ErrorBanner`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/InlineBanner.kt`
- Delete: `app/src/main/java/com/luxmap/core/ui/components/OfflineBanner.kt` (55 lines, one call site)
- Modify: `app/src/main/java/com/luxmap/core/ui/components/ErrorBanner.kt` (77 lines) — remove only the `ErrorBanner` function, keep `InlineErrorText` (used by `LoginScreen.kt:211,257` for field-level errors, a different UI shape, out of this task's scope)
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt:166,170` (both banner call sites)
- Modify: `app/src/main/java/com/luxmap/feature/auth/ui/LoginScreen.kt:280`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt:144`

**Interfaces:**
- Consumes: `SyncStatus.QUEUED_OFFLINE.badgeColors()`, `SyncStatus.FAILED.badgeColors()` (existing, unchanged), `Radius.control`, `LabelTextStyle`, `Spacing.sm`/`Spacing.md`.
- Produces: `InlineBanner(message: String, tone: InlineBannerTone, modifier: Modifier = Modifier)` and `enum class InlineBannerTone { OFFLINE, ERROR }` — consumed by the 4 call sites in this same task.

- [ ] **Step 1: Create `InlineBanner.kt`**

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.LabelTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors

// Replaces OfflineBanner + ErrorBanner with one component that only differs by tone — the
// spec's component table (§3, InlineBanner row) describes exactly these two cases (quá hạn /
// ngoại tuyến) sharing one shape, colored from the existing SyncStatus pairs (FAILED / QUEUED_
// OFFLINE) instead of generic surfaceVariant or the AssetCondition.OUT pair used before.
// InlineErrorText (field-level error, a different shape) is not part of this consolidation.
enum class InlineBannerTone { OFFLINE, ERROR }

@Composable
fun InlineBanner(
    message: String,
    tone: InlineBannerTone,
    modifier: Modifier = Modifier,
) {
    val colors =
        when (tone) {
            InlineBannerTone.OFFLINE -> SyncStatus.QUEUED_OFFLINE.badgeColors()
            InlineBannerTone.ERROR -> SyncStatus.FAILED.badgeColors()
        }
    val icon =
        when (tone) {
            InlineBannerTone.OFFLINE -> Icons.Filled.CloudOff
            InlineBannerTone.ERROR -> Icons.Filled.ErrorOutline
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.background, RoundedCornerShape(Radius.control))
                .padding(Spacing.md)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(text = message, style = LabelTextStyle, color = colors.text)
    }
}
```

- [ ] **Step 2: Delete `OfflineBanner.kt`**

```bash
rm app/src/main/java/com/luxmap/core/ui/components/OfflineBanner.kt
```

- [ ] **Step 3: Remove `ErrorBanner` from `ErrorBanner.kt`, keep `InlineErrorText`**

Delete lines 28-53 (the file header comment explaining the color choice, and the whole `ErrorBanner` function) from `ErrorBanner.kt`, leaving only the imports, the `InlineErrorText` function (current lines 55-77), and a trimmed header comment. Remove now-unused imports (`androidx.compose.foundation.layout.fillMaxWidth`, `androidx.compose.foundation.shape.RoundedCornerShape`, `com.luxmap.core.theme.Dimens` — `InlineErrorText` does not use `Dimens.radiusLarge` or `fillMaxWidth`).

- [ ] **Step 4: Update `HomeScreen.kt`'s two call sites**

Replace line 166:
```kotlin
                ErrorBanner(message = "${data.metrics.overdueCount} lệnh đã quá hạn xử lý")
```
with:
```kotlin
                InlineBanner(message = "${data.metrics.overdueCount} lệnh đã quá hạn xử lý", tone = InlineBannerTone.ERROR)
```

Replace line 170:
```kotlin
        if (isStale) {
            item { OfflineBanner(lastSyncedAt = lastSyncedAt) }
        }
```
with:
```kotlin
        if (isStale) {
            item {
                InlineBanner(
                    message = "Đang xem dữ liệu đã lưu, cập nhật lúc ${DateFormatUtils.formatInstant(lastSyncedAt)}",
                    tone = InlineBannerTone.OFFLINE,
                )
            }
        }
```

(`DateFormatUtils` is already imported in `HomeScreen.kt` — `OfflineBanner` used to call it internally; now the call site does, matching how every other banner/badge in this file already formats its own message string.)

- [ ] **Step 5: Update `LoginScreen.kt`'s call site**

Replace line 280:
```kotlin
                    ErrorBanner(lastBannerMessage.value)
```
with:
```kotlin
                    InlineBanner(message = lastBannerMessage.value, tone = InlineBannerTone.ERROR)
```

- [ ] **Step 6: Update `WorkOrderCompletionScreen.kt`'s call site**

Replace line 144:
```kotlin
            ErrorBanner(message = state.submitError)
```
with:
```kotlin
            InlineBanner(message = state.submitError, tone = InlineBannerTone.ERROR)
```

- [ ] **Step 7: Grep to confirm no stale references remain**

Run: `grep -rn "OfflineBanner(\|ErrorBanner(" app/src/main/java`
Expected: no matches (both old composables are gone; `InlineErrorText` is a different name and is unaffected).

- [ ] **Step 8: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/InlineBanner.kt app/src/main/java/com/luxmap/core/ui/components/ErrorBanner.kt app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt app/src/main/java/com/luxmap/feature/auth/ui/LoginScreen.kt app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt
git rm app/src/main/java/com/luxmap/core/ui/components/OfflineBanner.kt
git commit -m "style(redesign-phase1): add InlineBanner, retire OfflineBanner and ErrorBanner"
```

---

### Task 5: Promote `MessageState` to a shared component

**Files:**
- Create: `app/src/main/java/com/luxmap/core/ui/components/MessageState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt:433-505` (delete the private `MessageState`, keep `EmptyState`/`ErrorState` calling the shared one)

**Interfaces:**
- Produces: `fun BoxScope.MessageState(icon: ImageVector, iconColors: BadgeColors, title: String, subtitle: String, action: @Composable () -> Unit)` — same signature as the private version it replaces, so `HomeScreen.kt`'s `EmptyState`/`ErrorState` call it with zero changes to their own bodies, only their import.

- [ ] **Step 1: Create the shared `MessageState.kt`**

Move the current `HomeScreen.kt:474-505` function verbatim into a new file:

```kotlin
package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.BadgeColors
import com.luxmap.core.theme.Spacing

// Shared empty/error-state layout: icon badge + title + subtitle + an action slot (usually a
// button). Was private to HomeScreen.kt; promoted here so other screens stop hand-rolling a
// weaker bare-text version of the same pattern (each phase that redesigns one of those screens
// switches it to this component instead of keeping its own copy).
@Composable
fun BoxScope.MessageState(
    icon: ImageVector,
    iconColors: BadgeColors,
    title: String,
    subtitle: String,
    action: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(iconColors.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColors.text, modifier = Modifier.size(34.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        action()
    }
}
```

- [ ] **Step 2: Delete the private copy from `HomeScreen.kt`**

Delete the whole private `MessageState` function (current lines 473-505). `EmptyState` (434-447) and `ErrorState` (449-471) need no body changes — they already call `MessageState(...)` by name; it now resolves to the shared one via import instead of the file-local private function. Remove now-unused imports from `HomeScreen.kt` that only existed for the deleted function, if any are flagged by the compiler/ktlint after this change (`BoxScope` itself stays, since `EmptyState`/`ErrorState` are still `BoxScope` extensions).

- [ ] **Step 3: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL. (No import statement needs adding to `HomeScreen.kt` for `MessageState` itself — it's in the same `com.luxmap.core.ui.components` ... actually `HomeScreen.kt` is in `com.luxmap.feature.home.ui`, a different package, so an explicit `import com.luxmap.core.ui.components.MessageState` is needed. Add it.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luxmap/core/ui/components/MessageState.kt app/src/main/java/com/luxmap/feature/home/ui/HomeScreen.kt
git commit -m "style(redesign-phase1): promote MessageState to a shared component"
```

---

### Task 6: Full-component visual regression pass

**Files:** none (manual verification task — no code changes)

- [ ] **Step 1: Walk every screen whose call site changed, both themes where applicable**

Same constraint as Phase 0: this environment has no `adb`/emulator. If a device is available, check: Home (badges, overdue/offline banners, empty/error states), Login (login-failure banner), Work Order Detail (status/kind badges, priority dot), Work Order Completion (submit-error banner), every screen with a bottom nav bar visible (pill indicator color/size). If no device is available, ledger this as skipped (same pattern as Phase 0's Task 6) and flag it in the PR for the user/reviewer to check before merge.

- [ ] **Step 2: Open PR**

Target `dev`. Title: `style(redesign-phase1): restyle shared components (StatusBadge, PrimaryButton, BottomNavBar, InlineBanner, MessageState)`.
