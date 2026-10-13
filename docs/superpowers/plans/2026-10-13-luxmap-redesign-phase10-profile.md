# Phase 10 — F15 Cá nhân Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `ProfileScreen.kt` (F15) with real THIẾT BỊ / ĐỒNG BỘ / ỨNG DỤNG groups (spec `1y`), backed by real data wherever it already exists (`sync_queue`, BLE connection state, device/app info) — small, justified new data-layer queries where the table already exists but no query reads it yet, not a new feature from scratch.

**Architecture:** `sync_queue` (Room) already exists and is the real source of truth for "hàng đợi F13 + badge lỗi, lần tải cuối" — this phase adds 3 small aggregate queries to `SyncQueueDao`, exposes them through the existing `SyncQueueManager` (the established Repository-like abstraction over that DAO), and reads them from a new `ProfileRoute` wrapper that this phase also adds (every other feature already has one; Profile never did). `LuxSensorBleClient.connectionState` and `ConnectivityObserver.isOnline` are already real and already `@Singleton`/Hilt-injectable — reused as-is, zero changes to either. Device model and app version are static platform values (`Build.MANUFACTURER`/`Build.MODEL`, `BuildConfig.VERSION_NAME`), already used elsewhere in this codebase in exactly this form.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Room (new `@Query` methods), Hilt, StateFlow, JUnit + MockK + Turbine-free `first()` (unit), Room Testing (androidTest, in-memory DB, existing pattern).

**Spec:** `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` §4 "F15 Cá nhân (1y)" (the entire section is one line: "Nhóm THIẾT BỊ (cảm biến BLE đã ghép, bản đồ offline), ĐỒNG BỘ (hàng đợi F13 + badge lỗi, lần tải cuối), ỨNG DỤNG (giao diện, Thông báo = 'Chưa hỗ trợ trong phiên bản này'), nút Đăng xuất viền đỏ.") — read together with the roadmap row 10 in `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (just corrected 2026-10-13: the "`isLoggingOut` missing" premise was wrong — it already exists — and the F14 reasoning was wrong — the backend has a real API, mobile just doesn't consume it yet).

## Global Constraints

- Branch off `dev`, never `main`.
- **No `SettingsRow`/`SettingsGroup` component exists anywhere in this codebase today** (confirmed by grepping for `ListItem(` and any title+subtitle+chevron row pattern — zero hits) — this phase builds small `private` composables local to `ProfileScreen.kt`, not a new shared `core/ui/components` entry, since nothing else needs one yet (YAGNI).
- **The logout confirmation dialog already exists and is correct** (`isLoggingOut`, the `AlertDialog`, both `TextButton`s at `Dimens.minTouchTarget`) — Screen Review's claim that it's missing is a factual error in that doc (the code predates the doc by 3 weeks). Do not rebuild it; only touch it if a token/spacing change is needed for visual consistency with the new groups.
- **"Bản đề ngoại tuyến" (offline map region) has no real data anywhere** — `OfflineRegionManager` was never built, confirmed by grep. Show the exact same "Chưa hỗ trợ trong phiên bản này" copy as the Thông báo row; do not invent a status. Logged in `docs/contract-drift.md`.
- **"Giao diện" (theme) has no real toggle** — this app has no light/dark preference switch anywhere; it always follows the system (per CLAUDE.md's Dark Mode table, F15 is unlisted = default). Show it as a static informational row ("Theo hệ thống"), not a toggle control that implies a setting exists.
- **"Thông báo" row keeps saying "Chưa hỗ trợ trong phiên bản này"** — correct wording, but for the right reason now: the backend's notification API (BE-27, 4 real endpoints) exists, mobile's `feature/notification` does not. Do not say or imply the backend doesn't exist.
- No new third-party dependencies.
- `./gradlew ktlintCheck` after every task.
- All code comments in English, plain words, only where the WHY is non-obvious — zero new comments unless something is genuinely non-obvious (this user's standing preference).
- Each commit stays under ~400 changed lines per CLAUDE.md's commit-size rule.

## Review Focus

- `SyncQueueDao.observePendingCount()` must count both `queued` **and** `syncing` rows — counting only `queued` would under-report while a row is actively mid-upload.
- `SyncQueueDao.observeErrorCount()` must count both `failed` **and** `conflict` rows, matching CLAUDE.md's explicit rule not to gộp `failed`/`conflict` visually but still surface both as "cần chú ý" in one badge count here — counting only one status would hide the other failure mode from the Profile screen's summary.
- `observeLastSyncedAt()` must return `null` when zero rows have `status = 'done'` — a `MAX()` over zero matching rows in SQL returns `NULL`, not `0` or an exception; the screen must show "Chưa cập nhật" (via the already-existing `DateFormatUtils.formatInstant(null)`), not crash or show an epoch date.
- `bleConnectionLabel` must map all three `BleConnectionState` cases (`Disconnected`/`Connecting`/`Connected`) — a `when` that only handles Connected/Disconnected and forgets Connecting would not compile (sealed interface, exhaustive `when` required) but is worth a dedicated test anyway since it's the one place this phase touches BLE state.
- The logout confirmation dialog must still work exactly as before after the screen's layout changes around it — a regression here would block every Field Engineer from signing out.

---

### Task 1: `SyncQueueDao` aggregate queries

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncQueueDao.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/sync/SyncQueueDaoTest.kt` (add to existing file)

**Interfaces:**
- Consumes: existing `SyncQueueEntity` (unchanged).
- Produces: `SyncQueueDao.observePendingCount(): Flow<Int>`, `observeErrorCount(): Flow<Int>`, `observeLastSyncedAt(): Flow<Instant?>` — consumed by Task 2's `SyncQueueManager`.

- [ ] **Step 1: Write the failing tests (append to the existing test file)**

```kotlin
    @Test
    fun observePendingCountCountsOnlyQueuedAndSyncingRows() =
        runTest {
            dao.insert(row("OP-1", status = "queued"))
            dao.insert(row("OP-2", status = "syncing"))
            dao.insert(row("OP-3", status = "done"))
            dao.insert(row("OP-4", status = "failed"))

            assertEquals(2, dao.observePendingCount().first())
        }

    @Test
    fun observeErrorCountCountsOnlyFailedAndConflictRows() =
        runTest {
            dao.insert(row("OP-1", status = "failed"))
            dao.insert(row("OP-2", status = "conflict"))
            dao.insert(row("OP-3", status = "queued"))

            assertEquals(2, dao.observeErrorCount().first())
        }

    @Test
    fun observeLastSyncedAtReturnsTheLatestDoneUpdatedAt() =
        runTest {
            dao.insert(row("OP-1", status = "done").copy(updatedAt = Instant.parse("2026-10-06T10:00:00Z")))
            dao.insert(row("OP-2", status = "done").copy(updatedAt = Instant.parse("2026-10-06T12:00:00Z")))
            dao.insert(row("OP-3", status = "queued").copy(updatedAt = Instant.parse("2026-10-06T14:00:00Z")))

            assertEquals(Instant.parse("2026-10-06T12:00:00Z"), dao.observeLastSyncedAt().first())
        }

    @Test
    fun observeLastSyncedAtReturnsNullWhenNothingIsDone() =
        runTest {
            dao.insert(row("OP-1", status = "queued"))

            assertNull(dao.observeLastSyncedAt().first())
        }
```

(The existing file already imports `kotlinx.coroutines.flow.first`, `org.junit.Assert.assertNull`, `java.time.Instant` — no new imports needed.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `observePendingCount`/`observeErrorCount`/`observeLastSyncedAt` are unresolved.

- [ ] **Step 3: Add the queries to `SyncQueueDao.kt`**

Add inside the `interface SyncQueueDao` block, after `queuedRows()`:

```kotlin
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('queued', 'syncing')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('failed', 'conflict')")
    fun observeErrorCount(): Flow<Int>

    @Query("SELECT MAX(updatedAt) FROM sync_queue WHERE status = 'done'")
    fun observeLastSyncedAt(): Flow<Instant?>
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL (compile-verified per this project's established androidTest convention; the 4 new tests themselves run on-device later).

- [ ] **Step 5: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/SyncQueueDao.kt app/src/androidTest/java/com/luxmap/core/sync/SyncQueueDaoTest.kt
git commit -m "feat(redesign-phase10): add sync_queue pending/error/last-synced queries"
```

---

### Task 2: `SyncQueueManager` exposes the new queries

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncQueueManager.kt`
- Test: `app/src/test/java/com/luxmap/core/sync/SyncQueueManagerTest.kt` (add to existing file)

**Interfaces:**
- Consumes: Task 1's `SyncQueueDao.observePendingCount()`/`observeErrorCount()`/`observeLastSyncedAt()`.
- Produces: `SyncQueueManager.pendingCount: Flow<Int>`, `errorCount: Flow<Int>`, `lastSyncedAt: Flow<Instant?>` — consumed by Task 3's `ProfileViewModel`.

- [ ] **Step 1: Write the failing tests (append to the existing test file)**

```kotlin
    @Test
    fun `pendingCount delegates to the dao`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            every { dao.observePendingCount() } returns flowOf(3)
            val manager = SyncQueueManager(dao, mockk(relaxed = true))

            assertEquals(3, manager.pendingCount.first())
        }

    @Test
    fun `errorCount delegates to the dao`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            every { dao.observeErrorCount() } returns flowOf(2)
            val manager = SyncQueueManager(dao, mockk(relaxed = true))

            assertEquals(2, manager.errorCount.first())
        }

    @Test
    fun `lastSyncedAt delegates to the dao`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            val instant = Instant.parse("2026-10-06T12:00:00Z")
            every { dao.observeLastSyncedAt() } returns flowOf(instant)
            val manager = SyncQueueManager(dao, mockk(relaxed = true))

            assertEquals(instant, manager.lastSyncedAt.first())
        }
```

Add these imports to the top of the test file: `import io.mockk.every`, `import kotlinx.coroutines.flow.first`, `import kotlinx.coroutines.flow.flowOf`, `import org.junit.Assert.assertEquals`, `import java.time.Instant` (the file already imports `io.mockk.mockk`, `kotlinx.coroutines.test.runTest`, `org.junit.Test`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest"`
Expected: FAIL — `manager.pendingCount`/`errorCount`/`lastSyncedAt` are unresolved.

- [ ] **Step 3: Add the properties to `SyncQueueManager.kt`**

Add inside the `class SyncQueueManager` block, after the constructor and before `enqueue`:

```kotlin
        val pendingCount: Flow<Int> = dao.observePendingCount()
        val errorCount: Flow<Int> = dao.observeErrorCount()
        val lastSyncedAt: Flow<Instant?> = dao.observeLastSyncedAt()

```

Add the import `import kotlinx.coroutines.flow.Flow` (the file already imports `java.time.Instant`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest"`
Expected: PASS (5/5 — 2 existing + 3 new).

- [ ] **Step 5: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/SyncQueueManager.kt app/src/test/java/com/luxmap/core/sync/SyncQueueManagerTest.kt
git commit -m "feat(redesign-phase10): expose sync_queue pending/error/last-synced from SyncQueueManager"
```

---

### Task 3: `ProfileViewModel` — device/sync/network state

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/profile/ui/ProfileViewModelTest.kt` (new — none exists today)

**Interfaces:**
- Consumes: Task 2's `SyncQueueManager.pendingCount`/`errorCount`/`lastSyncedAt`; existing `com.luxmap.core.network.ConnectivityObserver.isOnline: Flow<Boolean>`; existing `com.luxmap.core.ble.LuxSensorBleClient.connectionState: StateFlow<BleConnectionState>`; existing `com.luxmap.feature.auth.data.AuthRepository` (unchanged).
- Produces: `ProfileViewModel` adds `val deviceModel: String`, `val appVersion: String`, `val bleConnectionState: StateFlow<BleConnectionState>`, `val pendingSyncCount: StateFlow<Int>`, `val errorSyncCount: StateFlow<Int>`, `val lastSyncedAt: StateFlow<Instant?>`, `val isOnline: StateFlow<Boolean>` — all consumed by Task 4's `ProfileRoute`. `username`/`isLoggingOut`/`onConfirmLogout()` unchanged.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.profile.ui

import app.cash.turbine.test
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.auth.data.AuthRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private fun viewModel(
        pendingCount: Int = 0,
        errorCount: Int = 0,
        lastSyncedAt: Instant? = null,
        isOnline: Boolean = true,
        bleState: BleConnectionState = BleConnectionState.Disconnected,
    ): ProfileViewModel {
        val authRepository = mockk<AuthRepository>()
        every { authRepository.observeUsername() } returns flowOf("fe_thinh")
        val syncQueueManager = mockk<SyncQueueManager>()
        every { syncQueueManager.pendingCount } returns flowOf(pendingCount)
        every { syncQueueManager.errorCount } returns flowOf(errorCount)
        every { syncQueueManager.lastSyncedAt } returns flowOf(lastSyncedAt)
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        val bleClient = mockk<LuxSensorBleClient>()
        every { bleClient.connectionState } returns MutableStateFlow(bleState)
        return ProfileViewModel(authRepository, syncQueueManager, connectivityObserver, bleClient)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pendingSyncCount and errorSyncCount reflect the sync queue manager`() =
        runTest {
            val vm = viewModel(pendingCount = 3, errorCount = 1)

            vm.pendingSyncCount.test { assertEquals(3, awaitItem()) }
            vm.errorSyncCount.test { assertEquals(1, awaitItem()) }
        }

    @Test
    fun `lastSyncedAt reflects the sync queue manager`() =
        runTest {
            val instant = Instant.parse("2026-10-06T12:00:00Z")
            val vm = viewModel(lastSyncedAt = instant)

            vm.lastSyncedAt.test { assertEquals(instant, awaitItem()) }
        }

    @Test
    fun `isOnline reflects the connectivity observer`() =
        runTest {
            val vm = viewModel(isOnline = false)

            vm.isOnline.test { assertEquals(false, awaitItem()) }
        }

    @Test
    fun `bleConnectionState reflects the ble client`() =
        runTest {
            val vm = viewModel(bleState = BleConnectionState.Connected)

            assertEquals(BleConnectionState.Connected, vm.bleConnectionState.value)
        }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.profile.ui.ProfileViewModelTest"`
Expected: FAIL — `ProfileViewModel` has no 4-argument constructor and no `pendingSyncCount`/`errorSyncCount`/`lastSyncedAt`/`isOnline`/`bleConnectionState`.

- [ ] **Step 3: Implement the `ProfileViewModel` additions**

Replace the whole file:

```kotlin
package com.luxmap.feature.profile.ui

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.BuildConfig
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.ble.LuxSensorBleClient
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.auth.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        syncQueueManager: SyncQueueManager,
        connectivityObserver: ConnectivityObserver,
        bleClient: LuxSensorBleClient,
    ) : ViewModel() {
        val username: StateFlow<String?> =
            authRepository.observeUsername().stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                null,
            )

        val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
        val appVersion: String = BuildConfig.VERSION_NAME

        val bleConnectionState: StateFlow<BleConnectionState> = bleClient.connectionState

        val pendingSyncCount: StateFlow<Int> =
            syncQueueManager.pendingCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)
        val errorSyncCount: StateFlow<Int> =
            syncQueueManager.errorCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)
        val lastSyncedAt: StateFlow<Instant?> =
            syncQueueManager.lastSyncedAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)
        val isOnline: StateFlow<Boolean> =
            connectivityObserver.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), true)

        var isLoggingOut by mutableStateOf(false)
            private set

        // This only clears the session. Going back to the Login screen is done by NavGraph when
        // it sees that the session ended, so the same path also covers a forced logout.
        fun onConfirmLogout() {
            if (isLoggingOut) return
            viewModelScope.launch {
                isLoggingOut = true
                try {
                    authRepository.logout()
                } finally {
                    isLoggingOut = false
                }
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.profile.ui.ProfileViewModelTest"`
Expected: PASS (4/4).

- [ ] **Step 5: Run ktlint**

Run: `./gradlew ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/profile/ui/ProfileViewModel.kt app/src/test/java/com/luxmap/feature/profile/ui/ProfileViewModelTest.kt
git commit -m "feat(redesign-phase10): add device/sync/network state to ProfileViewModel"
```

---

### Task 4: Restyle `ProfileScreen.kt` with THIẾT BỊ / ĐỒNG BỘ / ỨNG DỤNG groups

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/profile/ui/ProfileScreenTest.kt` (new — none exists today)

**Interfaces:**
- Consumes: Task 3's `ProfileViewModel`; existing `com.luxmap.core.ble.BleConnectionState`; existing `com.luxmap.core.common.DateFormatUtils.formatInstant(Instant?): String`; existing `com.luxmap.core.theme.OverlineTextStyle`/`Radius`/`Spacing`/`Dimens.minTouchTarget`; existing `com.luxmap.core.ui.components.StatusBadge`; existing `com.luxmap.core.theme.SyncStatus`/`badgeColors()`.
- Produces: `ProfileRoute(modifier: Modifier = Modifier, viewModel: ProfileViewModel = hiltViewModel())` (new — every other feature already has one, Profile never did) and `ProfileScreen(username: String, isLoggingOut: Boolean, deviceModel: String, appVersion: String, bleConnectionState: BleConnectionState, pendingSyncCount: Int, errorSyncCount: Int, lastSyncedAt: Instant?, isOnline: Boolean, onConfirmLogout: () -> Unit, modifier: Modifier = Modifier)` — `NavGraph.kt`'s `Routes.Profile.route` composable now just calls `ProfileRoute()`.

- [ ] **Step 1: Write the failing screen test**

```kotlin
package com.luxmap.feature.profile.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.theme.LuxMapTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ProfileScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setContent(
        isOnline: Boolean = true,
        pendingSyncCount: Int = 0,
        errorSyncCount: Int = 0,
        lastSyncedAt: Instant? = null,
        bleConnectionState: BleConnectionState = BleConnectionState.Disconnected,
        onConfirmLogout: () -> Unit = {},
    ) {
        composeRule.setContent {
            LuxMapTheme {
                ProfileScreen(
                    username = "fe_thinh",
                    isLoggingOut = false,
                    deviceModel = "Pixel 7a",
                    appVersion = "1.0.0",
                    bleConnectionState = bleConnectionState,
                    pendingSyncCount = pendingSyncCount,
                    errorSyncCount = errorSyncCount,
                    lastSyncedAt = lastSyncedAt,
                    isOnline = isOnline,
                    onConfirmLogout = onConfirmLogout,
                )
            }
        }
    }

    @Test
    fun shows_the_username_and_device_model() {
        setContent()
        composeRule.onNodeWithText("fe_thinh").assertExists()
        composeRule.onNodeWithText("Pixel 7a").assertExists()
    }

    @Test
    fun shows_an_error_badge_only_when_there_are_sync_errors() {
        setContent(errorSyncCount = 0)
        composeRule.onNodeWithText("0 lỗi", substring = true).assertDoesNotExist()
    }

    @Test
    fun shows_the_error_count_when_there_are_sync_errors() {
        setContent(errorSyncCount = 2)
        composeRule.onNodeWithText("2 lỗi").assertExists()
    }

    @Test
    fun shows_chua_cap_nhat_when_never_synced() {
        setContent(lastSyncedAt = null)
        composeRule.onNodeWithText("Chưa cập nhật").assertExists()
    }

    @Test
    fun shows_offline_when_not_online() {
        setContent(isOnline = false)
        composeRule.onNodeWithText("Offline").assertExists()
    }

    @Test
    fun the_notification_row_says_not_supported_in_this_version() {
        setContent()
        composeRule.onNodeWithText("Chưa hỗ trợ trong phiên bản này", substring = true).assertExists()
    }

    @Test
    fun confirming_logout_still_invokes_onConfirmLogout() {
        var loggedOut = false
        setContent(onConfirmLogout = { loggedOut = true })
        composeRule.onNodeWithText("Đăng xuất").performClick()
        composeRule.onNodeWithText("Đăng xuất khỏi thiết bị này?").assertExists()
        composeRule.onAllNodesWithText("Đăng xuất")[1].performClick()
        assertTrue(loggedOut)
    }
}
```

Add `import androidx.compose.ui.test.onAllNodesWithText` alongside the others.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `ProfileScreen` has no `deviceModel`/`appVersion`/`bleConnectionState`/`pendingSyncCount`/`errorSyncCount`/`lastSyncedAt`/`isOnline` parameters yet.

- [ ] **Step 3: Restyle `ProfileScreen.kt`**

Replace the whole file:

```kotlin
package com.luxmap.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.ble.BleConnectionState
import com.luxmap.core.common.DateFormatUtils
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.OverlineTextStyle
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.StatusBadge
import java.time.Instant

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val username by viewModel.username.collectAsState()
    val bleConnectionState by viewModel.bleConnectionState.collectAsState()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsState()
    val errorSyncCount by viewModel.errorSyncCount.collectAsState()
    val lastSyncedAt by viewModel.lastSyncedAt.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    ProfileScreen(
        // Null only for a session saved before the app kept the username.
        username = username ?: "Tài khoản",
        isLoggingOut = viewModel.isLoggingOut,
        deviceModel = viewModel.deviceModel,
        appVersion = viewModel.appVersion,
        bleConnectionState = bleConnectionState,
        pendingSyncCount = pendingSyncCount,
        errorSyncCount = errorSyncCount,
        lastSyncedAt = lastSyncedAt,
        isOnline = isOnline,
        onConfirmLogout = viewModel::onConfirmLogout,
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    username: String,
    isLoggingOut: Boolean,
    deviceModel: String,
    appVersion: String,
    bleConnectionState: BleConnectionState,
    pendingSyncCount: Int,
    errorSyncCount: Int,
    lastSyncedAt: Instant?,
    isOnline: Boolean,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Survives rotation, so the dialog does not disappear while the user is deciding.
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // Opaque background so nothing behind this screen (for example the map) can show
                // through.
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(text = "Cá nhân", style = MaterialTheme.typography.titleLarge)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(username = username)
            Spacer(Modifier.height(Spacing.md))
            Text(text = username, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Tổ khảo sát/sửa chữa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(title = "THIẾT BỊ") {
            SettingsRow(label = "Thiết bị", value = deviceModel)
            SettingsRow(label = "Cảm biến lux (Bluetooth)", value = bleConnectionLabel(bleConnectionState))
            SettingsRow(label = "Bản đồ ngoại tuyến", value = "Chưa hỗ trợ trong phiên bản này")
        }

        SettingsGroup(title = "ĐỒNG BỘ") {
            SyncQueueRow(pendingSyncCount = pendingSyncCount, errorSyncCount = errorSyncCount)
            SettingsRow(label = "Lần đồng bộ cuối", value = DateFormatUtils.formatInstant(lastSyncedAt))
            SettingsRow(label = "Kết nối mạng", value = if (isOnline) "Online" else "Offline")
        }

        SettingsGroup(title = "ỨNG DỤNG") {
            SettingsRow(label = "Giao diện", value = "Theo hệ thống")
            SettingsRow(label = "Thông báo", value = "Chưa hỗ trợ trong phiên bản này")
            SettingsRow(label = "Phiên bản", value = appVersion)
        }

        // Outlined (not filled) red: logout only ends the session on this device, so it should
        // be easy to find but not look like the main action of the screen.
        OutlinedButton(
            onClick = { showConfirmDialog = true },
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Đăng xuất")
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Đăng xuất khỏi thiết bị này?") },
            text = { Text("Bạn cần đăng nhập lại để tiếp tục sử dụng ứng dụng.") },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
                ) {
                    Text("Huỷ")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onConfirmLogout()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Đăng xuất")
                }
            },
        )
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = title, style = OverlineTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SyncQueueRow(
    pendingSyncCount: Int,
    errorSyncCount: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Hàng đợi đồng bộ", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$pendingSyncCount mục",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (errorSyncCount > 0) {
                StatusBadge(text = "$errorSyncCount lỗi", colors = SyncStatus.FAILED.badgeColors())
            }
        }
    }
}

private fun bleConnectionLabel(state: BleConnectionState): String =
    when (state) {
        BleConnectionState.Connected -> "Đã kết nối"
        BleConnectionState.Connecting -> "Đang kết nối"
        BleConnectionState.Disconnected -> "Chưa kết nối"
    }

// Round avatar showing the first letter of the username (no profile photo in the data yet).
@Composable
private fun Avatar(username: String) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
```

- [ ] **Step 4: Simplify `NavGraph.kt`'s `Routes.Profile` block**

Change:

```kotlin
            composable(Routes.Profile.route) {
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val username by profileViewModel.username.collectAsState()
                ProfileScreen(
                    // Null only for a session saved before the app kept the username.
                    username = username ?: "Tài khoản",
                    isLoggingOut = profileViewModel.isLoggingOut,
                    onConfirmLogout = profileViewModel::onConfirmLogout,
                )
            }
```

to:

```kotlin
            composable(Routes.Profile.route) {
                ProfileRoute()
            }
```

Update the import from `com.luxmap.feature.profile.ui.ProfileScreen` to `com.luxmap.feature.profile.ui.ProfileRoute`. If `ProfileViewModel`/`hiltViewModel`/`collectAsState` are no longer referenced anywhere else in `NavGraph.kt`, remove their now-unused imports — check with a grep over the rest of the file first, other routes may still use `hiltViewModel`/`collectAsState` for their own ViewModels.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/profile/ui/ProfileScreen.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/androidTest/java/com/luxmap/feature/profile/ui/ProfileScreenTest.kt
git commit -m "feat(redesign-phase10): restyle ProfileScreen with device/sync/app settings groups"
```

---

### Task 5: Docs and final verification

**Files:**
- Modify: `docs/contract-drift.md`

**Interfaces:**
- Consumes: nothing new.
- Produces: nothing new — this is the phase's wrap-up task.

- [ ] **Step 1: Add a row to `docs/contract-drift.md`**

Append this row to the table (after the last existing row):

```markdown
| Phase 10's "Bản đồ ngoại tuyến" and "Giao diện" rows are informational only, not real settings | `ProfileScreen.kt`'s THIẾT BỊ group shows "Bản đồ ngoại tuyến: Chưa hỗ trợ trong phiên bản này" and ỨNG DỤNG group shows "Giao diện: Theo hệ thống" — neither is backed by a real toggle or real offline-map-region data | **Deliberate, by design (2026-10-13, Phase 10)** — `OfflineRegionManager` was never built (no offline map region download/storage exists anywhere in this codebase, confirmed by grep), so the offline-map row can only say it's unsupported, same as the Thông báo row. There is also no light/dark theme preference switch anywhere in this app (it always follows the system) — the Giao diện row is informational, not a control. Building either as a real feature is new work, not a restyle | WP6 |
```

- [ ] **Step 2: Run the full scoped verification**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugUnitTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest" --tests "com.luxmap.feature.profile.ui.ProfileViewModelTest"`
Expected: BUILD SUCCESSFUL, all tests pass (5 + 4 = 9).

Do not run the full `./gradlew test` suite — `CaptureViewModelTest` has a known pre-existing hang unrelated to this branch; the scoped compile+lint above plus this plan's `Review Focus` tests are this project's established substitute verification for a restyle-only phase.

- [ ] **Step 3: Verify no capture-mode files were touched**

Run: `git diff dev..HEAD --stat | grep -i "capture\|CaptureViewModel"`
Expected: no output — this phase never touches `feature/survey/ui/capture`.

- [ ] **Step 4: Commit**

```bash
git add docs/contract-drift.md
git commit -m "docs(redesign-phase10): log the informational-only Profile rows"
```

- [ ] **Step 5: Push and open the PR**

```bash
git push -u origin style/redesign-phase10-profile
gh pr create --base dev --head style/redesign-phase10-profile --title "style(redesign-phase10): restyle Profile with device/sync/app settings groups" --body "..."
```

(The exact PR body is written at execution time, summarizing the actual commits made — follow this project's established PR description pattern from Phases 2–8.)

---

## Self-Review Notes

- **Spec coverage:** THIẾT BỊ (cảm biến BLE đã ghép → `bleConnectionLabel` reusing real `LuxSensorBleClient` state; bản đồ offline → informational, logged drift — Task 4), ĐỒNG BỘ (hàng đợi F13 + badge lỗi → real `SyncQueueManager.pendingCount`/`errorCount` from new Task 1/2 queries; lần tải cuối → real `lastSyncedAt` — Task 3/4), ỨNG DỤNG (giao diện → informational; Thông báo → correct "chưa hỗ trợ" copy for the right reason; both — Task 4), nút Đăng xuất viền đỏ (already existed, untouched except surrounding layout — Task 4).
- **Placeholder scan:** none found — every step has concrete, runnable code; the `NavGraph.kt` edit in Task 4 Step 4 explicitly tells the executor to grep before removing an import rather than guessing it's unused.
- **Type consistency:** `BleConnectionState` (existing, from `core.ble`) used identically across Task 3's `ProfileViewModel` and Task 4's `ProfileScreen`/`bleConnectionLabel`; `SyncQueueManager.pendingCount`/`errorCount`/`lastSyncedAt` (Task 2 Produces) match exactly what Task 3's `ProfileViewModel` constructor consumes; `Instant?` for `lastSyncedAt` threaded consistently from Task 1's DAO query through Task 2/3 to Task 4's `DateFormatUtils.formatInstant` call.
- **Review Focus:** all 5 items have an owning test — pending-count queued+syncing (Task 1's dedicated test), error-count failed+conflict (Task 1's dedicated test), last-synced-null-when-none-done (Task 1's dedicated test), BLE 3-state exhaustiveness (Task 3's `bleConnectionState reflects the ble client` test exercises `Connected`; the `when` itself is compiler-enforced exhaustive since `BleConnectionState` is a sealed interface — a missing branch would fail Task 4's compile step, not just a test), logout dialog regression (Task 4's `confirming_logout_still_invokes_onConfirmLogout` test, unchanged from the screen's pre-existing behavior).
