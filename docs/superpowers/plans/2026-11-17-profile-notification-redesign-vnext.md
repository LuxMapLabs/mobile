# Cá nhân + Thông báo — Redesign vNext Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign F15 Cá nhân and F14 Thông báo to Design System vNext, remove the last floating bell, and give both screens proper retry/error states — no API, polling, navigation-rule, Room or sync change.

**Architecture:** Pure text helpers (`profileSyncSummary`) and ViewModel changes first (Profile `retry()`, `isNetworkError` on both Error states). Then Profile moves to `LuxTabTopBar` (NavGraph drops `NotificationBellBar` entirely) with vNext cards; Notification moves to `LuxChildTopBar` with a "Đọc tất cả" action, `LuxSegmented` filter and role-aware rows.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.10.01, M3), Hilt, Coroutines/Flow, JUnit + MockK + Turbine, Compose UI Test.

**Spec:** `docs/design-system/screens/PROFILE_NOTIFICATION.md` (approved 2026-11-17, T1–T3).

## Global Constraints

- Branch `style/redesign-profile-notification-vnext` (from `dev`, spec commit `35ba87e`). Never commit to `main`.
- Commits ≤ 400 changed lines, `type(profile|notification): description`, English, lowercase, imperative. **No Claude attribution.** No new code comments.
- No API, polling (45s), navigation-rule (only `entity_type = "work_order"` navigates), Room or sync change.
- Only theme tokens in `feature/`; no primitives (`Navy700`, `Success600`, `AttentionColor`…), no `Color(0x…)`, no legacy `*TextStyle`, no `BrandHeroGradient`/`NotificationHeroGradient`, no ALL-CAPS section labels.
- Touch targets ≥ 48dp; margin 16dp.
- Copy (exact):
  - Profile: title `Cá nhân`; section `Tình trạng làm việc`; rows `Kết nối mạng` (`Trực tuyến` / `Ngoại tuyến`), `Đồng bộ dữ liệu`; sync summary per `profileSyncSummary`; `Đăng xuất`; caption `Còn N mục chưa đồng bộ trên thiết bị này`; dialog title `Đăng xuất khỏi thiết bị này?`, message `Bạn cần đăng nhập lại để tiếp tục sử dụng ứng dụng.`, confirm `Đăng xuất`, dismiss `Huỷ`.
  - Profile error title `Không tải được thông tin cá nhân`; message network `Kiểm tra kết nối rồi thử lại.`, generic `Vui lòng thử lại sau ít phút.`; button `Thử lại`; loading description `Đang tải thông tin cá nhân`.
  - Notification: title `Thông báo`; action `Đọc tất cả`; filter `Tất cả · n`, `Chưa đọc · n`; empty `Chưa có thông báo nào` / `Thông báo về việc được giao, khảo sát và sự cố sẽ xuất hiện ở đây.`; unread-empty `Đã đọc hết thông báo`; error title `Không tải được thông báo` + same two messages; loading description `Đang tải thông báo`; unread a11y suffix `chưa đọc`.
- `profileSyncSummary(pending, errors, lastSyncedAt)`: parts in order `Chờ N`, `Lỗi N` (only when > 0), then `Lần cuối HH:mm` (via `DateFormatUtils.formatInstant`) — joined by ` · `; nothing pending/errored → `Đã đồng bộ · Lần cuối …`; `lastSyncedAt == null` and nothing pending/errored → `Chưa đồng bộ lần nào`; `lastSyncedAt == null` with pending/errors → only the counts.
- Unit: `./gradlew testLocalDebugUnitTest --tests "<fqcn>"`. Device (SM-A075F): `./gradlew connectedLocalDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true "-Pandroid.testInstrumentationRunnerArguments.class=<fqcn>"`. `./gradlew ktlintCheck` each task. Stop after each task for the user.

## Review Focus

1. **Profile fails to load while offline**: Error must still show Tình trạng làm việc + Đăng xuất (engineer can still sign out). UI test in Task 3.
2. **`retry()` called repeatedly / while a load is running**: only one fetch in flight, last result wins. VM test in Task 1.
3. **Unread notification read by TalkBack**: row announces title + `chưa đọc`; navigable row exposes a click action, non-navigable row still clickable (marks read) without a chevron. UI test in Task 4.
4. **"Đọc tất cả" when everything is already read**: action hidden; tapping it once hides it. UI test in Task 4.
5. **Unknown notification `type`**: neutral icon, label `Thông báo`, no crash (existing fallback must survive the restyle). UI test kept in Task 4.

---

### Task 1: Text helper and ViewModels

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileText.kt`
- Create: `app/src/test/java/com/luxmap/feature/profile/ui/ProfileTextTest.kt`
- Modify: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileUiState.kt`, `ProfileViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/notification/ui/NotificationListUiState.kt`, `NotificationListViewModel.kt`
- Modify: `app/src/test/java/com/luxmap/feature/profile/ui/ProfileViewModelTest.kt`, `app/src/test/java/com/luxmap/feature/notification/ui/NotificationListViewModelTest.kt`

**Interfaces — Produces:**
- `fun profileSyncSummary(pending: Int, errors: Int, lastSyncedAt: Instant?): String`
- `ProfileUiState.Error(message: String, isNetworkError: Boolean = false)`; `ProfileViewModel.retry()`
- `NotificationListUiState.Error(message: String, isNetworkError: Boolean = false)`

- [ ] **Step 1: Failing tests** — `ProfileTextTest.kt`:

```kotlin
package com.luxmap.feature.profile.ui

import com.luxmap.core.common.DateFormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ProfileTextTest {
    private val at = Instant.parse("2026-11-17T03:24:00Z")
    private val last = "Lần cuối ${DateFormatUtils.formatInstant(at)}"

    @Test
    fun `pending and errors with a last sync`() {
        assertEquals("Chờ 2 · Lỗi 1 · $last", profileSyncSummary(2, 1, at))
    }

    @Test
    fun `zero counts are left out`() {
        assertEquals("Chờ 2 · $last", profileSyncSummary(2, 0, at))
        assertEquals("Lỗi 1 · $last", profileSyncSummary(0, 1, at))
    }

    @Test
    fun `nothing left means synced`() {
        assertEquals("Đã đồng bộ · $last", profileSyncSummary(0, 0, at))
    }

    @Test
    fun `never synced`() {
        assertEquals("Chưa đồng bộ lần nào", profileSyncSummary(0, 0, null))
        assertEquals("Chờ 3", profileSyncSummary(3, 0, null))
    }
}
```

In `ProfileViewModelTest` add (read the file's existing construction helper first and reuse it; stub `profileRepository.fetchCurrentUser()` with `coEvery … returnsMany`):

```kotlin
    @Test
    fun `an IOException is a network error and retry recovers`() =
        runTest {
            coEvery { profileRepository.fetchCurrentUser() } returnsMany
                listOf(Result.failure(java.io.IOException("timeout")), Result.success(profile))
            val vm = viewModel()
            vm.profileState.test {
                assertEquals(ProfileUiState.Loading, awaitItem())
                assertEquals(true, (awaitItem() as ProfileUiState.Error).isNetworkError)
                vm.retry()
                assertEquals(ProfileUiState.Loading, awaitItem())
                assertEquals(ProfileUiState.Success(profile), awaitItem())
            }
        }
```

In `NotificationListViewModelTest` add a case where `repository.list()` returns `Result.failure(IOException("x"))` → `(state as Error).isNetworkError == true`, and the existing failure case asserts `false`.

- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement**

`ProfileText.kt`:

```kotlin
package com.luxmap.feature.profile.ui

import com.luxmap.core.common.DateFormatUtils
import java.time.Instant

fun profileSyncSummary(
    pending: Int,
    errors: Int,
    lastSyncedAt: Instant?,
): String {
    val parts =
        buildList {
            if (pending > 0) add("Chờ $pending")
            if (errors > 0) add("Lỗi $errors")
        }
    val last = lastSyncedAt?.let { "Lần cuối ${DateFormatUtils.formatInstant(it)}" }
    return when {
        parts.isEmpty() && last == null -> "Chưa đồng bộ lần nào"
        parts.isEmpty() -> "Đã đồng bộ · $last"
        last == null -> parts.joinToString(" · ")
        else -> (parts + last).joinToString(" · ")
    }
}
```

`ProfileUiState.Error` → `data class Error(val message: String, val isNetworkError: Boolean = false) : ProfileUiState`.

`ProfileViewModel`:

```kotlin
        private var loadJob: Job? = null

        fun retry() {
            _profileState.value = ProfileUiState.Loading
            loadProfile()
        }

        private fun loadProfile() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    _profileState.value =
                        profileRepository.fetchCurrentUser().fold(
                            onSuccess = { ProfileUiState.Success(it) },
                            onFailure = {
                                ProfileUiState.Error(
                                    message = it.message ?: "Không tải được thông tin cá nhân",
                                    isNetworkError = it is IOException,
                                )
                            },
                        )
                }
        }
```

(Imports `kotlinx.coroutines.Job`, `java.io.IOException`.)

`NotificationListUiState.Error` → add `val isNetworkError: Boolean = false`; in `NotificationListViewModel.load()` `onFailure = { e -> … Error(e.message ?: "…", isNetworkError = e is IOException) }`.

- [ ] **Step 4: Run both VM tests + `ProfileTextTest` + `compileLocalDebugAndroidTestKotlin` + ktlint; PASS.**
- [ ] **Step 5: Commit** `feat(profile): add a sync summary, retry and network errors for profile and notifications`

---

### Task 2: Profile top bar with bell; remove the floating bell

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt` (`ProfileRoute`, `ProfileScreen` frame, delete `ProfileHeader`/`ProfileAvatar`/`RoleBadge`)
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt` (Scaffold `topBar` bell block, Profile destination)
- Delete: `app/src/main/java/com/luxmap/core/ui/components/NotificationBellBar.kt` (and its test if any)
- Modify: `app/src/androidTest/java/com/luxmap/feature/profile/ui/ProfileScreenTest.kt`

**Interfaces — Produces:** `ProfileRoute(unreadNotificationCount: Int = 0, onOpenNotifications: () -> Unit = {}, modifier, viewModel)`; `ProfileScreen(…, unreadNotificationCount: Int = 0, onOpenNotifications: () -> Unit = {}, onRetry: () -> Unit = {}, …)`.

- [ ] **Step 1: Failing UI test** (add to `ProfileScreenTest`; extend its `setContent` helper with `unread`/`onOpenNotifications` params):

```kotlin
    @Test
    fun the_top_bar_has_the_title_and_a_bell_that_opens_notifications() {
        var opened = 0
        setContent(unread = 4, onOpenNotifications = { opened++ })
        composeRule.onNodeWithText("Cá nhân").assertExists()
        composeRule.onNodeWithContentDescription("Thông báo, 4 chưa đọc").performClick()
        assertEquals(1, opened)
    }
```

- [ ] **Step 2: Implement**
  - `ProfileScreen` root becomes `Scaffold(contentWindowInsets = WindowInsets(0,0,0,0), topBar = { LuxTabTopBar(title = "Cá nhân", unreadNotificationCount = unreadNotificationCount, onNotificationsClick = onOpenNotifications) }, containerColor = MaterialTheme.colorScheme.background) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { when (profileState) { … } } }`. Remove the `offset`/`clip` sheet wrapper and `ProfileHeader`. (Identity moves into the profile card in Task 3; keep `ProfileContent` as is for now so tests keep compiling — the name/@username assertions will move with Task 3.)
  - `ProfileRoute` gains and passes `unreadNotificationCount`, `onOpenNotifications`, and `onRetry = viewModel::retry`.
  - `NavGraph`: delete the whole `topBar = { AnimatedVisibility(...) { NotificationBellBar(...) } }` block (no tab uses it now); Profile destination: `ProfileRoute(unreadNotificationCount = unreadNotificationCount, onOpenNotifications = { navController.navigate(Routes.Notification.route) })`. Remove unused imports (`NotificationBellBar`, possibly `AnimatedVisibility`/`fadeIn`/`fadeOut` if no longer used — check the bottom bar still uses them).
  - Delete `NotificationBellBar.kt` (grep first: only `NavGraph` and a comment in `ProfileScreen` reference it).
  - Old tests asserting the hero's name/@username keep passing only if Task 3 is done; if they fail here, move those 2 assertions to Task 3 (record a ledger ruling).
- [ ] **Step 3: Run Profile UI test class + ktlint; PASS (except assertions explicitly deferred to Task 3).**
- [ ] **Step 4: Commit** `style(profile): move the bell into the profile top bar and drop the floating bell`

---

### Task 3: Profile content and states

**Files:** `ProfileScreen.kt` (`ProfileContent`, `AccountSection`, `CommuneChips`, `ReadinessSection`, `NetworkRow`, `SyncRow`, `IconChip`, `StatCell`, `LogoutSection`, `FooterInfo`, `cardOutline`, confirm dialog, Loading/Error branches); `ProfileScreenTest.kt`.

**Interfaces — Consumes:** `profileSyncSummary`, `ProfileUiState.Error.isNetworkError`, `LuxCard`, `LuxSectionHeader`, `LuxDestructiveButton(text, onClick, modifier, filled, enabled)`, `LuxConfirmDialog(title, message, confirmLabel, onConfirm, onDismiss, dismissLabel, destructive)`, `LuxErrorState`, `LuxLoadingRegion`, `LuxSkeleton`.

- [ ] **Step 1: Failing UI tests** — replace `loading_state_shows_the_progress_indicator`, `all_ready_shows_san_sang_label`, `offline_and_a_sync_error_shows_2_items_need_attention`, `shows_chua_cap_nhat_when_never_synced`, `error_state_shows_the_message` with:

```kotlin
    @Test
    fun loading_is_one_announced_region() {
        setContent(profileState = ProfileUiState.Loading)
        composeRule.onNodeWithContentDescription("Đang tải thông tin cá nhân").assertExists()
    }

    @Test
    fun the_status_card_shows_network_and_a_real_sync_summary() {
        setContent(isOnline = false, pendingSyncCount = 2, errorSyncCount = 1, lastSyncedAt = null)
        composeRule.onNodeWithText("Tình trạng làm việc").assertExists()
        composeRule.onNodeWithText("Ngoại tuyến").assertExists()
        composeRule.onNodeWithText("Chờ 2 · Lỗi 1").assertExists()
        composeRule.onNodeWithText("Sẵn sàng").assertDoesNotExist()
        composeRule.onNodeWithText("mục cần kiểm tra", substring = true).assertDoesNotExist()
    }

    @Test
    fun never_synced_says_so() {
        setContent(pendingSyncCount = 0, errorSyncCount = 0, lastSyncedAt = null)
        composeRule.onNodeWithText("Chưa đồng bộ lần nào").assertExists()
    }

    @Test
    fun an_error_offers_retry_and_still_lets_the_engineer_sign_out() {
        var retries = 0
        setContent(profileState = ProfileUiState.Error("java.net.UnknownHostException", isNetworkError = true), onRetry = { retries++ })
        composeRule.onNodeWithText("Không tải được thông tin cá nhân").assertExists()
        composeRule.onNodeWithText("UnknownHostException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithText("Tình trạng làm việc").performScrollTo().assertExists()
        composeRule.onNodeWithText("Đăng xuất").performScrollTo().assertExists()
    }
```

(Extend the test `setContent` helper with `onRetry`. Keep the existing success/communes/footer/pending-caption/logout tests; the caption text stays `Còn N mục chưa đồng bộ trên thiết bị này`.)

- [ ] **Step 2: Implement** — `ProfileContent` becomes a scrolling `Column(Modifier.verticalScroll(...).padding(horizontal = Spacing.lg, vertical = Spacing.md), spacedBy(Spacing.lg))`:
  - `ProfileCard(profile, username)`: `LuxCard { Row { Box(48.dp circle primaryContainer) { Text(initials, titleMedium, onPrimaryContainer) }; Column { Text(fullName, titleMedium); Text("@$username · Tổ khảo sát/sửa chữa", bodyMedium, onSurfaceVariant) } }; HorizontalDivider(outlineVariant); label–value row "Email"/email (FlowRow SpaceBetween like F09 `SummaryRow`); Text("Địa bàn phụ trách · ${n}", bodyMedium, onSurfaceVariant); FlowRow of AssistChip-like chips (`LuxFilterChip(selected=false)` or plain `Surface(shape = shapes.small, color = surfaceContainerHigh)` with `labelMedium`) }`.
  - `StatusSection(isOnline, pending, errors, lastSyncedAt)`: `LuxSectionHeader("Tình trạng làm việc")` + `LuxCard { StatusRow(Icons.Filled.Wifi/WifiOff, "Kết nối mạng", if online "Trực tuyến" else "Ngoại tuyến", valueColor = if online onSurface else error); HorizontalDivider; StatusRow(Icons.Filled.Sync, "Đồng bộ dữ liệu", profileSyncSummary(...), valueColor = if errors > 0 error else onSurfaceVariant) }` — `StatusRow` = `FlowRow` with icon+label left, value right (`bodyMedium`), min height 48dp.
  - `LogoutSection`: `LuxDestructiveButton(text = if (isLoggingOut) "Đang đăng xuất…" else "Đăng xuất", onClick, filled = false, enabled = !isLoggingOut, modifier = fillMaxWidth())` + caption when `pending > 0` (`bodySmall`, `error`).
  - Footer: `Text("$deviceModel · Phiên bản $appVersion", bodySmall, onSurfaceVariant, textAlign = Center, fillMaxWidth)`.
  - Error branch: `Column(verticalScroll) { LuxErrorState(onRetry, kind, title = "Không tải được thông tin cá nhân", message = network/generic); StatusSection(...); LogoutSection(...); Footer }`.
  - Loading branch: `LuxLoadingRegion("Đang tải thông tin cá nhân", Modifier.padding(Spacing.lg)) { LuxSkeleton(Modifier.fillMaxWidth().height(160.dp), shapes.medium); LuxSkeleton(Modifier.fillMaxWidth().height(120.dp), shapes.medium) }`.
  - Dialog: `LuxConfirmDialog(title = "Đăng xuất khỏi thiết bị này?", message = "Bạn cần đăng nhập lại để tiếp tục sử dụng ứng dụng.", confirmLabel = "Đăng xuất", onConfirm = { showConfirmDialog = false; onConfirmLogout() }, onDismiss = { showConfirmDialog = false }, destructive = true)`.
  - Delete old helpers (`AccountSection`, `ReadinessSection`, `NetworkRow`, `SyncRow`, `IconChip`, `StatCell`, `cardOutline`, `SyncDetailIndent`, `AttentionColor`…) and unused imports.
- [ ] **Step 3: Run Profile UI tests + unit + ktlint; PASS.** If >400 lines, split (cards first, then states).
- [ ] **Step 4: Commit** `style(profile): rebuild profile content and states with vnext components`

---

### Task 4: Notification top bar, filter and rows

**Files:** `NotificationListScreen.kt` (`NotificationListScreen` frame, delete `NotificationHeroSection`, `NotificationFilterRow`, `NotificationFilterChip`, `tileColors`; rewrite `NotificationRow`); `NotificationListScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** (add):

```kotlin
    @Test
    fun mark_all_read_is_a_top_bar_action_only_when_something_is_unread() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(state = NotificationListUiState.Success(listOf(row(isRead = true))), onBack = {})
            }
        }
        composeRule.onNodeWithText("Đọc tất cả").assertDoesNotExist()
        composeRule.onNodeWithText("Thông báo").assertExists()
    }

    @Test
    fun the_filter_is_a_radio_group_with_counts() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Success(listOf(row(isRead = false), row(id = "NTF-2", isRead = true))),
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Tất cả · 2")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected()
        composeRule.onNodeWithText("Chưa đọc · 1").assertIsNotSelected()
    }

    @Test
    fun only_work_order_rows_are_buttons_with_a_chevron_and_unread_is_announced() {
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state =
                        NotificationListUiState.Success(
                            listOf(
                                row(id = "WO-ROW", entityType = "work_order", title = "Giao lệnh mới", isRead = false),
                                row(id = "FAULT-ROW", entityType = "fault", title = "Sự cố mới", isRead = false),
                            ),
                        ),
                    onBack = {},
                )
            }
        }
        composeRule.onNode(hasText("Giao lệnh mới", substring = true) and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasContentDescription("chưa đọc", substring = true))
        composeRule.onNodeWithTag("notification_chevron_WO-ROW", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("notification_chevron_FAULT-ROW", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNode(hasText("Sự cố mới", substring = true) and hasClickAction()).assertExists()
    }
```

Adapt the existing `row(...)` test helper to accept `id`, `entityType`, `title`, `isRead` (read it first). Keep existing tests for tap → `onItemClick`, mark-all → `onMarkAllRead` (now the top-bar action), unread dot tag, unknown type, more footer.

- [ ] **Step 2: Implement**
  - Frame: `Scaffold(topBar = { LuxChildTopBar(title = "Thông báo", onBack = onBack, actions = { if (unreadCount > 0) LuxTextButton(text = "Đọc tất cả", onClick = onMarkAllRead) }) }, containerColor = background) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { when (state) { … } } }`. Remove hero + offset/clip wrapper.
  - Filter: `LuxSegmented(segments = listOf(LuxSegment(NotificationFilter.ALL, "Tất cả · $all"), LuxSegment(NotificationFilter.UNREAD, "Chưa đọc · $unread")), selected = state.filter, onSelect = onFilterSelected)` with 16dp horizontal padding.
  - Day group headers: `LuxSectionHeader(title = dayLabel)`.
  - `NotificationRow`:

```kotlin
@Composable
private fun NotificationRow(
    row: NotificationListRowState,
    dayLabel: String,
    onClick: () -> Unit,
) {
    val type = notificationTypeFromWire(row.type)
    val isNavigable = notificationNavigationTarget(row) != null
    val timeLabel =
        if (dayLabel == DAY_GROUP_OLDER) DateFormatUtils.formatIsoDateOnly(row.createdAt) else DateFormatUtils.formatIsoTimeOnly(row.createdAt)
    val spoken = "${type.label()}, $timeLabel, ${row.title}, ${row.body}" + if (row.isRead) "" else ", chưa đọc"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .clickable(onClick = onClick, role = if (isNavigable) Role.Button else null, onClickLabel = if (isNavigable) "Mở" else "Đánh dấu đã đọc")
                .clearAndSetSemantics {
                    contentDescription = spoken
                    if (isNavigable) role = Role.Button
                    onClick(label = if (isNavigable) "Mở" else "Đánh dấu đã đọc") { onClick(); true }
                }
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(LuxTheme.status.kind.background, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon(), contentDescription = null, tint = LuxTheme.status.kind.text)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text("${type.label()} · $timeLabel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(row.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (row.isRead) FontWeight.Normal else FontWeight.SemiBold)
            Text(row.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!row.isRead) {
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, CircleShape).testTag("notification_unread_dot_${row.notificationId}"))
            }
            if (isNavigable) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("notification_chevron_${row.notificationId}"))
            }
        }
    }
}
```

  (`clickable(... role = null ...)`: if the Compose version requires a non-null role, call the overload without `role` for non-navigable rows. The `clearAndSetSemantics` block is what tests and TalkBack see.) Delete `tileColors()` and the custom chip/filter/hero composables; remove the `NotificationHeroGradient` usage and unused imports.
- [ ] **Step 3: Run Notification UI test class + ktlint; PASS.** Split commit if >400 lines (frame+filter, then rows).
- [ ] **Step 4: Commit** `style(notification): use a child top bar, segmented filter and role-aware rows`

---

### Task 5: Notification data states

**Files:** `NotificationListScreen.kt` (Loading/Empty/Error/unread-empty branches, delete `NotificationSkeletonList`), `NotificationListScreenTest.kt`.

- [ ] **Step 1: Failing UI tests** — replace `loading_state_shows_the_progress_indicator`, `empty_state_shows_the_empty_inbox_message`, `error_state_shows_the_message_and_retry_calls_onRetry` with:

```kotlin
    @Test
    fun loading_is_one_announced_region() {
        composeRule.setContent { LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Loading, onBack = {}) } }
        composeRule.onNodeWithContentDescription("Đang tải thông báo").assertExists()
    }

    @Test
    fun empty_says_there_are_no_notifications() {
        composeRule.setContent { LuxMapTheme { NotificationListScreen(state = NotificationListUiState.Empty, onBack = {}) } }
        composeRule.onNodeWithText("Chưa có thông báo nào").assertExists()
    }

    @Test
    fun a_network_error_uses_screen_copy_and_retries() {
        var retries = 0
        composeRule.setContent {
            LuxMapTheme {
                NotificationListScreen(
                    state = NotificationListUiState.Error("java.net.SocketTimeoutException", isNetworkError = true),
                    onBack = {},
                    onRetry = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Không tải được thông báo").assertExists()
        composeRule.onNodeWithText("Kiểm tra kết nối rồi thử lại.").assertExists()
        composeRule.onNodeWithText("SocketTimeoutException", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Thử lại").performClick()
        assertEquals(1, retries)
    }
```

- [ ] **Step 2: Implement** — Loading: `LuxLoadingRegion("Đang tải thông báo", Modifier.padding(Spacing.lg)) { LuxSkeleton(Modifier.fillMaxWidth().height(48.dp), shapes.small); repeat(5) { LuxSkeleton(Modifier.fillMaxWidth().height(72.dp), shapes.medium) } }`; Empty: `LuxEmptyState(title = "Chưa có thông báo nào", message = "Thông báo về việc được giao, khảo sát và sự cố sẽ xuất hiện ở đây.", icon = Icons.Filled.NotificationsNone)`; Error: `LuxErrorState(onRetry, kind, title = "Không tải được thông báo", message = network/generic)`; unread-empty inside Success: `LuxEmptyState(title = "Đã đọc hết thông báo", message = "Không còn thông báo chưa đọc.")`. Delete `NotificationSkeletonList`, `MessageState`/`PrimaryButton` usages, unused imports.
- [ ] **Step 3: Run Notification UI tests + ktlint; PASS.**
- [ ] **Step 4: Commit** `style(notification): move the notification data states to vnext components`

---

### Task 6: Large font, full device run, cleanup, spec status

**Files:** Create `app/src/androidTest/java/com/luxmap/feature/profile/ui/ProfileLargeFontTest.kt`, `app/src/androidTest/java/com/luxmap/feature/notification/ui/NotificationLargeFontTest.kt`; modify spec status line.

- [ ] **Step 1: Large-font tests** (1.5×, 360dp width, same `show()` pattern as `HomeLargeFontTest`):
  - Profile: Success with a long full name and email; assert `"Đăng xuất"` `assertTouchTargetIsAtLeast(48.dp)` after `performScrollTo()`, full name `assertIsDisplayed()`, and `"Chờ 2 · Lỗi 1"` (with `lastSyncedAt = null`) displayed after scroll.
  - Notification: Success with one unread `work_order` row; assert `"Đọc tất cả"` touch target ≥48dp, `"Tất cả · 1"` ≥48dp, and the row node (`hasClickAction() and hasContentDescription(title, substring = true)`) ≥48dp.
- [ ] **Step 2: Run device packages** `com.luxmap.feature.profile.ui` and `com.luxmap.feature.notification.ui` + unit `com.luxmap.feature.profile.*`, `com.luxmap.feature.notification.*` + ktlint; all PASS.
- [ ] **Step 3: Cleanup grep** in both screen files for `Navy700`, `Success600`, `AttentionColor`, `Color(0x`, `TextStyle`, `Radius.`, `Gradient`, `MessageState`, `Color.White`; remove leftovers. Confirm `NotificationHeroGradient`/`BrandHeroGradient` still have other users before leaving them in `core/theme` (do not delete theme tokens in this PR).
- [ ] **Step 4: Spec status** → `**ĐÃ DUYỆT VÀ ĐÃ CODE XONG** trên nhánh style/redesign-profile-notification-vnext. Còn nợ: kiểm tra tay trên máy đã đăng nhập (Light/Night Field, 200%).`
- [ ] **Step 5: Commit** `test(profile): cover profile and notifications at large font`
