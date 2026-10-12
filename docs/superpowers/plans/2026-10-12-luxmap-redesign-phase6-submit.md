# LuxMap Redesign Phase 6: F06 Nộp Đợt Khảo Sát Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle `SubmitScreen` (F06) to the v2.1 redesign and wire the one real feature the roadmap flags as more than cosmetic: a "Chỉ tải lên qua Wi-Fi" toggle that actually constrains the background `SyncWorker`'s `NetworkType`, not a switch that does nothing. Also adds the 2×2 session-metrics grid, a per-file sync-status list, the "Đã lưu trên thiết bị" vs. "Đã đồng bộ" copy distinction, and a "Về Khảo sát" button. Byte-level progress per file stays at today's 0%/100% (confirmed with the user 2026-10-12 — out of scope, already logged in `docs/contract-drift.md`).

**Architecture:** A new `UploadPreferences` DataStore (the Wi-Fi-only flag) and a new `ConnectivityObserver.isOnWifi` flow feed into 3 places: (1) `WorkManagerSyncTrigger`/`SyncScheduler`, which now read the flag to pick `NetworkType.UNMETERED` vs `NetworkType.CONNECTED`; (2) `RealUploadRepository.uploadSession()`, which now skips its own foreground upload attempt (and reports a new `WaitingForWifi` state) when the flag is on and the device isn't on Wi-Fi, leaving the already-enqueued chain for the constrained background worker; (3) `SubmitScreen`, which gets the toggle UI. A new `SyncQueueDao` query + `UploadRepository.observeFileStatuses()` method feed the per-file list.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room (`Flow` query), DataStore, WorkManager (`Constraints`/`NetworkType`). No new dependencies — all of these are already in the approved stack.

**Spec:** `docs/superpowers/plans/2026-10-11-luxmap-redesign-roadmap.md` (Phase 6 row) and `docs/LuxMap_Mobile_Redesign_Spec_v0.1.md` line 198-202 (F06 section, code `1p`). Current-code findings this plan is built from (all read in full during planning): `feature/survey/ui/submit/SubmitScreen.kt`/`SubmitViewModel.kt`/`SubmitUiState.kt`, `feature/survey/data/UploadRepository.kt`/`RealUploadRepository.kt`/`FakeUploadRepository.kt`, `core/sync/SyncTrigger.kt`/`WorkManagerSyncTrigger.kt`/`SyncScheduler.kt`/`SyncQueueManager.kt`/`SyncQueueDao.kt`/`SyncQueueEntity.kt`, `core/network/ConnectivityObserver.kt`, `core/security/TokenStore.kt` (DataStore pattern precedent), `LuxMapApp.kt`, `NavGraph.kt`'s `Routes.SurveySubmit` destination.

**Confirmed with the user (2026-10-12):** per-file byte-level progress is deferred, not built in this phase — the per-file list in this plan shows each file's `sync_queue.status` badge (queued/syncing/failed/conflict/done), not a byte counter. This matches the already-logged `contract-drift.md` row on `upload_survey_clip`/`upload_survey_raw` progress granularity.

## Global Constraints

- Branch off `dev` (`git checkout dev && git pull`), never `main`. Branch: `style/redesign-phase6-submit`.
- Every commit ≤ 400 changed lines (CLAUDE.md). Expect one commit per task (6 total).
- `./gradlew ktlintCheck` after every task, fix before moving on.
- **Offline-first, no exception:** the upload chain is still enqueued into `sync_queue` immediately on submit, every time, regardless of the Wi-Fi-only setting or current connectivity — only the *foreground attempt to drain it right now* is skipped. A user who submits while offline, with Wi-Fi-only off or on, always has their session safely queued.
- The per-file list uses the **real** `opType`/`clientOpId` shape already in `RealUploadRepository.enqueueChain()` (`create_survey_sweep`, `upload_survey_clip` × N, `upload_survey_raw` × 3 — `gps_track`/`lux_log`/`capture_config` separately, not merged — `submit_survey_sweep`). This plan does **not** try to force the spec's illustrative "video 1..n / log GPS+lux / manifest" 3-bucket wording onto data that is actually 3 separate raw ops plus 2 bookend ops — it shows the real 5+N rows with accurate Vietnamese labels instead.
- No new library. `Switch` is a plain M3 component — this is its first use in the app, no shared wrapper needed for one call site.

## Review Focus

- **The Wi-Fi-only constraint must actually change WorkManager's `NetworkType`, not just store a preference nobody reads.** A test must prove `WorkManagerSyncTrigger`/`SyncScheduler` build a `Constraints` object with `NetworkType.UNMETERED` when the flag is on, `NetworkType.CONNECTED` when it's off.
- **Flipping the toggle must take effect on the already-scheduled periodic work, not just future one-time triggers.** `SyncScheduler.schedulePeriodicSync()`'s normal app-startup call uses `ExistingPeriodicWorkPolicy.KEEP` (don't disturb in-flight work on every launch) — a toggle flip must use a path that actually replaces the constraint (`REPLACE`), not silently no-op because the periodic work already exists.
- **Submitting while offline, or with Wi-Fi-only on and no Wi-Fi, must still enqueue the chain and show something other than a spinner forever** — `WaitingForWifi`/the offline `Uploading` badge, not a state that looks identical to "about to start."
- **The per-file list must survive a session with zero video segments** (e.g. a very short recording) — the list still has the 2 raw-op bookends and the raw files, nothing crashes on an empty clip range.
- **The 2×2 metrics grid must not crash on a session whose `durationSeconds`/`distanceMeters` are `null`** (reachable if `PackageSurveySessionUseCase` never set them for some old/edge-case local row) — show a placeholder, not throw.

---

### Task 1: `UploadPreferences` (Wi-Fi-only flag) and `ConnectivityObserver.isOnWifi`

**Files:**
- Create: `app/src/main/java/com/luxmap/core/sync/UploadPreferences.kt`
- Modify: `app/src/main/java/com/luxmap/core/network/ConnectivityObserver.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/sync/UploadPreferencesTest.kt` (new file)

**Interfaces:**
- Produces: `UploadPreferences.observeWifiOnlyUpload(): Flow<Boolean>` (default `false`), `suspend fun setWifiOnlyUpload(enabled: Boolean)` — consumed by Task 2/3/5. `ConnectivityObserver.isOnWifi: Flow<Boolean>` — consumed by Task 3.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.sync

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadPreferencesTest {
    @Test
    fun defaults_to_wifi_only_off() =
        runTest {
            val prefs = UploadPreferences(ApplicationProvider.getApplicationContext())
            assertEquals(false, prefs.observeWifiOnlyUpload().first())
        }

    @Test
    fun setWifiOnlyUpload_persists_the_value() =
        runTest {
            val prefs = UploadPreferences(ApplicationProvider.getApplicationContext())
            prefs.setWifiOnlyUpload(true)
            assertTrue(prefs.observeWifiOnlyUpload().first())
            prefs.setWifiOnlyUpload(false)
        }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin` (compile check only — no device in this environment, same as every prior phase).
Expected: FAIL — `UploadPreferences` does not exist yet.

- [ ] **Step 3: Create `UploadPreferences.kt`**

```kotlin
package com.luxmap.core.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.uploadPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "upload_prefs")

@Singleton
class UploadPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun observeWifiOnlyUpload(): Flow<Boolean> =
            context.uploadPrefsDataStore.data.map { prefs -> prefs[WIFI_ONLY_KEY] ?: false }

        suspend fun setWifiOnlyUpload(enabled: Boolean) {
            context.uploadPrefsDataStore.edit { prefs -> prefs[WIFI_ONLY_KEY] = enabled }
        }

        private companion object {
            val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only_upload")
        }
    }
```

- [ ] **Step 4: Add `isOnWifi` to `ConnectivityObserver`**

Add, after the existing `isOnline` property:

```kotlin
        val isOnWifi: Flow<Boolean> =
            callbackFlow {
                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(hasWifiTransport())
                        }

                        override fun onLost(network: Network) {
                            trySend(hasWifiTransport())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(hasWifiTransport())
                        }
                    }
                connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
                trySend(hasWifiTransport())
                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()

        private fun hasWifiTransport(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
```

(`isOnline` and its own `hasInternetCapability()` are untouched — this is a second, independent flow/check, not a refactor of the existing one.)

- [ ] **Step 5: Build and ktlint**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/UploadPreferences.kt app/src/main/java/com/luxmap/core/network/ConnectivityObserver.kt app/src/androidTest/java/com/luxmap/core/sync/UploadPreferencesTest.kt
git commit -m "feat(redesign-phase6): add UploadPreferences wifi-only flag and ConnectivityObserver.isOnWifi"
```

---

### Task 2: Wire the real `NetworkType` constraint

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncTrigger.kt`
- Modify: `app/src/main/java/com/luxmap/core/sync/WorkManagerSyncTrigger.kt`
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncScheduler.kt`
- Modify: `app/src/test/java/com/luxmap/core/sync/SyncQueueManagerTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/sync/WorkManagerSyncTriggerTest.kt` (new file)

**Interfaces:**
- Produces: `SyncTrigger.triggerNow()` is now `suspend fun triggerNow()`. `SyncScheduler.schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP)` (new optional param) and a new `suspend fun reschedulePeriodicSync()` — consumed by Task 3 (the toggle handler calls `reschedulePeriodicSync()`).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import androidx.work.NetworkType

class WorkManagerSyncTriggerTest {
    @Before
    fun setUp() {
        val config = Configuration.Builder().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(ApplicationProvider.getApplicationContext(), config)
    }

    @Test
    fun triggerNow_uses_UNMETERED_when_wifi_only_is_on() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val prefs = UploadPreferences(context)
            prefs.setWifiOnlyUpload(true)
            val trigger = WorkManagerSyncTrigger(context, prefs)

            trigger.triggerNow()

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(NetworkType.UNMETERED, work.constraints.requiredNetworkType)
            prefs.setWifiOnlyUpload(false)
        }

    @Test
    fun triggerNow_uses_CONNECTED_when_wifi_only_is_off() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val prefs = UploadPreferences(context)
            val trigger = WorkManagerSyncTrigger(context, prefs)

            trigger.triggerNow()

            val work =
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(WorkManagerSyncTrigger.SYNC_WORK_NAME)
                    .get()
                    .first()
            assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        }
}
```

(Needs `androidx.work:work-testing` — check `app/build.gradle.kts`'s `androidTestImplementation` block; if it is not already present, add `androidTestImplementation("androidx.work:work-testing:<same version as the androidx.work:work-runtime-ktx already in the project>")` and flag the exact version used in the commit message, since this is the one new test dependency this plan needs — everything else is already in the approved stack.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `WorkManagerSyncTrigger(context, prefs)` doesn't match today's constructor (`@ApplicationContext context: Context` only, no `UploadPreferences` param, and `triggerNow()` is not `suspend`).

- [ ] **Step 3: Update `SyncTrigger.kt`**

```kotlin
package com.luxmap.core.sync

interface SyncTrigger {
    suspend fun triggerNow()
}
```

- [ ] **Step 4: Update `WorkManagerSyncTrigger.kt`**

```kotlin
package com.luxmap.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncTrigger
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val uploadPreferences: UploadPreferences,
    ) : SyncTrigger {
        private val workManager = WorkManager.getInstance(context)

        override suspend fun triggerNow() {
            val wifiOnly = uploadPreferences.observeWifiOnlyUpload().first()
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                    .build()
            workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        companion object {
            const val SYNC_WORK_NAME = "sync_queue"
        }
    }
```

- [ ] **Step 5: Update `SyncScheduler.kt`**

```kotlin
package com.luxmap.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val uploadPreferences: UploadPreferences,
    ) {
        private val workManager = WorkManager.getInstance(context)

        // KEEP by default so an ordinary app launch never disturbs an in-flight periodic job -
        // only a real preference change (reschedulePeriodicSync) needs REPLACE to actually pick
        // up a new NetworkType, since WorkManager ignores a KEEP request against existing work.
        suspend fun schedulePeriodicSync(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
            val wifiOnly = uploadPreferences.observeWifiOnlyUpload().first()
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK_NAME, policy, request)
        }

        suspend fun reschedulePeriodicSync() = schedulePeriodicSync(policy = ExistingPeriodicWorkPolicy.REPLACE)

        companion object {
            const val PERIODIC_SYNC_WORK_NAME = "sync_queue_periodic"
        }
    }
```

- [ ] **Step 6: Update the 2 callers that now need a suspend context**

`LuxMapApp.kt` calls `syncScheduler.schedulePeriodicSync()` from `onCreate()` (not a coroutine today) — wrap it:

```kotlin
        CoroutineScope(Dispatchers.Default).launch { syncScheduler.schedulePeriodicSync() }
```

(Add imports `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch` if not already present in `LuxMapApp.kt`.) `SyncQueueManager.enqueue()` already calls `syncTrigger.triggerNow()` from inside a `suspend fun` — no change needed there, only the interface/impl signature changed underneath it.

- [ ] **Step 7: Fix `SyncQueueManagerTest.kt`'s now-wrong verification style**

Replace `verify(exactly = 1) { trigger.triggerNow() }` with `coVerify(exactly = 1) { trigger.triggerNow() }` (the `coVerify` import already exists in this file for the other assertion).

- [ ] **Step 8: Run to verify it passes**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest" ktlintCheck`
Expected: BUILD SUCCESSFUL, `SyncQueueManagerTest` passes. (`WorkManagerSyncTriggerTest` itself is `androidTest` — compile-verified only, same as every prior phase.)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/SyncTrigger.kt app/src/main/java/com/luxmap/core/sync/WorkManagerSyncTrigger.kt app/src/main/java/com/luxmap/core/sync/SyncScheduler.kt app/src/main/java/com/luxmap/LuxMapApp.kt app/src/test/java/com/luxmap/core/sync/SyncQueueManagerTest.kt app/src/androidTest/java/com/luxmap/core/sync/WorkManagerSyncTriggerTest.kt app/build.gradle.kts
git commit -m "feat(redesign-phase6): make SyncTrigger wifi-only aware, add reschedulePeriodicSync"
```

---

### Task 3: Gate the foreground upload attempt, add `WaitingForWifi`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt` (constructor arity change — see Step 4b)

**Interfaces:**
- Produces: `UploadProgress.WaitingForWifi` (new sealed variant), `SubmitUiState.WaitingForWifi` (new) — consumed by Task 5 (Screen).

- [ ] **Step 1: Write the failing test**

Add to `SubmitViewModelTest.kt` (the file already has a `session(recordingState)` helper):

```kotlin
    @Test
    fun `wifi-only on and not on wifi reports WaitingForWifi instead of uploading`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.WaitingForWifi)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity)

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.WaitingForWifi)
            }
        }
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: FAIL to compile — `UploadProgress.WaitingForWifi` and `SubmitUiState.WaitingForWifi` don't exist yet.

- [ ] **Step 3: Add the new states**

In `UploadRepository.kt`, add to the sealed interface:

```kotlin
    data object WaitingForWifi : UploadProgress
```

In `SubmitUiState.kt`, add:

```kotlin
    data object WaitingForWifi : SubmitUiState
```

In `SubmitViewModel.kt`'s `when (progress)` block, add:

```kotlin
                                is UploadProgress.WaitingForWifi -> SubmitUiState.WaitingForWifi
```

- [ ] **Step 4: Gate the foreground drain in `RealUploadRepository`**

Add constructor params `uploadPreferences: UploadPreferences` and `connectivityObserver: ConnectivityObserver`. Right after the `justClaimed`/`resetFailedOps` block (the chain is now guaranteed enqueued either way) and before computing `totalBytes`/calling `processQueuedOps`, insert:

```kotlin
                if (uploadPreferences.observeWifiOnlyUpload().first() && !connectivityObserver.isOnWifi.first()) {
                    emit(UploadProgress.WaitingForWifi)
                    return@flow
                }
```

(Written as one `&&` condition, not two separate `val`s, so Kotlin's short-circuit evaluation means `connectivityObserver.isOnWifi.first()` is only ever read when `wifiOnly` is true — every existing test, which never sets up Wi-Fi-only, only needs `observeWifiOnlyUpload()` stubbed, never `isOnWifi`, see Step 4b. The chain stays enqueued in `sync_queue` regardless — only the immediate foreground `processQueuedOps()` call below this point is skipped. The already-triggered background `SyncWorker`, now wifi-only-aware per Task 2, picks it up once a Wi-Fi network is actually available.)

Add imports: `com.luxmap.core.network.ConnectivityObserver`, `com.luxmap.core.sync.UploadPreferences`, `kotlinx.coroutines.flow.first`.

- [ ] **Step 5: Update `FakeUploadRepository`**

No change needed to its `uploadSession()` body (it never reaches a wifi-only decision point) — this step only confirms the interface addition doesn't break it, since `UploadProgress.WaitingForWifi` is a new sealed-interface member the `Fake` implementation's own `when` (if any) does not need to touch; `FakeUploadRepository.uploadSession()` never emits it, which is fine — it is a simulator for the happy path, not every real-world branch.

- [ ] **Step 4b: Fix `RealUploadRepositoryTest.kt` for the new constructor arity**

All 7 existing tests in this file construct `RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)` (5 args) — this stops compiling once Step 4 adds 2 more required constructor params. Add this helper near the top of the file, right after the existing `videoSegment(...)` helper:

```kotlin
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.UploadPreferences
import kotlinx.coroutines.flow.flowOf

private fun wifiOnlyOffPreferences(): UploadPreferences {
    val prefs = mockk<UploadPreferences>()
    every { prefs.observeWifiOnlyUpload() } returns flowOf(false)
    return prefs
}
```

(Add the `io.mockk.every` import too if this file doesn't already have it — check first, `coEvery` is already imported but plain `every` may not be.) Then, in every one of the 7 `RealUploadRepository(database, sessionDao, syncQueueDao, syncQueueManager, processor)` call sites in this file, change it to:

```kotlin
            val repository =
                RealUploadRepository(
                    database,
                    sessionDao,
                    syncQueueDao,
                    syncQueueManager,
                    processor,
                    wifiOnlyOffPreferences(),
                    mockk<ConnectivityObserver>(),
                )
```

(The `ConnectivityObserver` mock is passed unstubbed on purpose — with `wifiOnlyOffPreferences()` making `observeWifiOnlyUpload()` return `false`, the short-circuit in Step 4 means `isOnWifi` is never actually called on it in any of these 7 tests, so a bare, non-relaxed `mockk<ConnectivityObserver>()` is enough; stubbing something that's never invoked would just be dead code.)

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest" --tests "com.luxmap.feature.survey.data.RealUploadRepositoryTest" ktlintCheck`
Expected: PASS (6 tests in `SubmitViewModelTest`, 7 in `RealUploadRepositoryTest`).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitUiState.kt app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt
git commit -m "feat(redesign-phase6): skip the foreground upload attempt when wifi-only and not on wifi"
```

---

### Task 4: Per-file sync-status list and session summary

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncQueueDao.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/sync/SyncQueueDaoTest.kt`

**Interfaces:**
- Produces: `SyncQueueDao.observeOpsForSession(sessionIdPrefix: String): Flow<List<SyncQueueEntity>>`, `UploadRepository.observeFileStatuses(sessionId: String): Flow<List<SessionFileStatus>>` where `SessionFileStatus(val label: String, val status: String)`, `UploadRepository.sessionSummary(sessionId: String): SessionUploadSummary?` where `SessionUploadSummary(val durationSeconds: Long?, val distanceMeters: Double?, val totalBytes: Long, val segmentCount: Int)` — all consumed by Task 5 (ViewModel/Screen).

- [ ] **Step 1: Write the failing DAO test**

Add to `SyncQueueDaoTest.kt` (check its current imports/helpers first — if it has no existing `row(...)` helper, add one matching `SyncQueueEntity`'s fields):

```kotlin
    @Test
    fun observeOpsForSessionOnlyReturnsRowsForThatSession() =
        runTest {
            val now = Instant.parse("2026-10-12T10:00:00Z")
            dao.insert(
                SyncQueueEntity(
                    clientOpId = "SESSION-1:clip:0",
                    opType = "upload_survey_clip",
                    payloadJson = "{}",
                    dependsOnClientOpId = null,
                    status = "queued",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            dao.insert(
                SyncQueueEntity(
                    clientOpId = "SESSION-2:clip:0",
                    opType = "upload_survey_clip",
                    payloadJson = "{}",
                    dependsOnClientOpId = null,
                    status = "queued",
                    createdAt = now,
                    updatedAt = now,
                ),
            )

            val rows = dao.observeOpsForSession("SESSION-1:%").first()

            assertEquals(1, rows.size)
            assertEquals("SESSION-1:clip:0", rows.first().clientOpId)
        }
```

Add import `kotlinx.coroutines.flow.first` if this test file doesn't already have it.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `observeOpsForSession` is unresolved.

- [ ] **Step 3: Add the DAO query**

```kotlin
    import kotlinx.coroutines.flow.Flow
```

(if not already imported — check first, `SyncQueueDao.kt` may not need it added if a prior phase already did; this project's `SurveySessionDao` already imports it for the same reason). Add the query:

```kotlin
    @Query("SELECT * FROM sync_queue WHERE clientOpId LIKE :sessionIdPrefix ORDER BY createdAt ASC")
    fun observeOpsForSession(sessionIdPrefix: String): Flow<List<SyncQueueEntity>>
```

- [ ] **Step 4: Add the repository methods**

In `UploadRepository.kt`:

```kotlin
data class SessionFileStatus(val label: String, val status: String)

data class SessionUploadSummary(
    val durationSeconds: Long?,
    val distanceMeters: Double?,
    val totalBytes: Long,
    val segmentCount: Int,
)
```

and to the interface:

```kotlin
    fun observeFileStatuses(sessionId: String): Flow<List<SessionFileStatus>>

    suspend fun sessionSummary(sessionId: String): SessionUploadSummary?
```

In `RealUploadRepository.kt`:

```kotlin
        override fun observeFileStatuses(sessionId: String): Flow<List<SessionFileStatus>> =
            syncQueueDao.observeOpsForSession("$sessionId:%").map { rows ->
                rows.map { row -> SessionFileStatus(label = labelForOp(row.clientOpId, sessionId), status = row.status) }
            }

        override suspend fun sessionSummary(sessionId: String): SessionUploadSummary? {
            val session = sessionDao.sessionById(sessionId) ?: return null
            val segments = sessionDao.segmentsFor(sessionId)
            val totalBytes =
                segments.sumOf { it.sizeBytes ?: 0L } +
                    listOfNotNull(session.gpsTrackFilePath, session.luxLogFilePath, session.captureConfigFilePath)
                        .sumOf { File(it).length() }
            return SessionUploadSummary(
                durationSeconds = session.durationSeconds,
                distanceMeters = session.distanceMeters,
                totalBytes = totalBytes,
                segmentCount = segments.size,
            )
        }

        private fun labelForOp(
            clientOpId: String,
            sessionId: String,
        ): String =
            when (val suffix = clientOpId.removePrefix("$sessionId:")) {
                "create_sweep" -> "Tạo phiên khảo sát"
                "submit" -> "Nộp phiên khảo sát"
                "raw:gps_track" -> "Log GPS"
                "raw:lux_log" -> "Log lux"
                "raw:capture_config" -> "Cấu hình quay"
                else ->
                    suffix.removePrefix("clip:").toIntOrNull()?.let { "Video đoạn ${it + 1}" }
                        ?: suffix
            }
```

Add import `kotlinx.coroutines.flow.map` to `RealUploadRepository.kt` if not already present.

In `FakeUploadRepository.kt`:

```kotlin
        override fun observeFileStatuses(sessionId: String): Flow<List<SessionFileStatus>> = flowOf(emptyList())

        override suspend fun sessionSummary(sessionId: String): SessionUploadSummary? =
            SessionUploadSummary(durationSeconds = 600L, distanceMeters = 1200.0, totalBytes = totalBytes, segmentCount = 2)
```

Add import `kotlinx.coroutines.flow.flowOf`.

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/SyncQueueDao.kt app/src/main/java/com/luxmap/feature/survey/data/UploadRepository.kt app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt app/src/main/java/com/luxmap/feature/survey/data/FakeUploadRepository.kt app/src/androidTest/java/com/luxmap/core/sync/SyncQueueDaoTest.kt
git commit -m "feat(redesign-phase6): add per-file sync status list and session upload summary"
```

---

### Task 5: Restyle `SubmitScreen` — 2×2 grid, file list, wifi-only toggle, "Về Khảo sát"

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/test/java/com/luxmap/feature/survey/ui/submit/SubmitViewModelTest.kt` (constructor arity changes — see Step 3b)
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/ui/submit/SubmitScreenTest.kt` (new file)

**Interfaces:**
- Produces: `SubmitViewModel.fileStatuses: StateFlow<List<SessionFileStatus>>`, `SubmitViewModel.summary: StateFlow<SessionUploadSummary?>`, `SubmitViewModel.wifiOnlyUpload: StateFlow<Boolean>`, `fun onWifiOnlyToggled(enabled: Boolean)`. `SubmitScreen(sessionId: String, onBackToSurveyHub: () -> Unit, viewModel: SubmitViewModel = hiltViewModel())` — the `onBackToSurveyHub` param is new; `NavGraph.kt`'s one call site is updated in this task.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.survey.ui.submit

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.luxmap.feature.survey.data.SessionFileStatus
import org.junit.Rule
import org.junit.Test

class SubmitScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun file_status_list_shows_each_files_label() {
        composeRule.setContent {
            SubmitFileStatusList(
                files =
                    listOf(
                        SessionFileStatus(label = "Video đoạn 1", status = "done"),
                        SessionFileStatus(label = "Log GPS", status = "queued"),
                    ),
            )
        }
        composeRule.onNodeWithText("Video đoạn 1").assertExists()
        composeRule.onNodeWithText("Log GPS").assertExists()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: FAIL — `SubmitFileStatusList` does not exist yet.

- [ ] **Step 3: Update `SubmitViewModel.kt`**

Replace the whole class body with (adds the `uploadPreferences` constructor param, the `fileStatuses`/`summary`/`wifiOnlyUpload` state, `onWifiOnlyToggled`, and moves the summary/file-status fetch inside `onSubmit` to *after* the packaged check, so a `NotPackagedYet` session never calls the two new repository methods at all):

```kotlin
@HiltViewModel
class SubmitViewModel
    @Inject
    constructor(
        private val repository: UploadRepository,
        private val sessionDao: SurveySessionDao,
        private val connectivityObserver: ConnectivityObserver,
        private val uploadPreferences: UploadPreferences,
        private val syncScheduler: SyncScheduler,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SubmitUiState>(SubmitUiState.Idle)
        val uiState: StateFlow<SubmitUiState> = _uiState.asStateFlow()

        private val _fileStatuses = MutableStateFlow<List<SessionFileStatus>>(emptyList())
        val fileStatuses: StateFlow<List<SessionFileStatus>> = _fileStatuses.asStateFlow()

        private val _summary = MutableStateFlow<SessionUploadSummary?>(null)
        val summary: StateFlow<SessionUploadSummary?> = _summary.asStateFlow()

        val wifiOnlyUpload: StateFlow<Boolean> =
            uploadPreferences.observeWifiOnlyUpload().stateIn(viewModelScope, SharingStarted.Eagerly, false)

        // Writing the flag alone only affects the next one-time trigger (WorkManagerSyncTrigger
        // reads it fresh every time). The periodic job stays scheduled with whatever NetworkType
        // it started with until something calls reschedulePeriodicSync - this toggle is that
        // something, so flipping it takes effect immediately, not just "next app launch".
        fun onWifiOnlyToggled(enabled: Boolean) {
            viewModelScope.launch {
                uploadPreferences.setWifiOnlyUpload(enabled)
                syncScheduler.reschedulePeriodicSync()
            }
        }

        fun onSubmit(sessionId: String) {
            viewModelScope.launch {
                val session = sessionDao.sessionById(sessionId)
                if (session == null || session.recordingState != "packaged") {
                    _uiState.value = SubmitUiState.NotPackagedYet
                    return@launch
                }

                _summary.value = repository.sessionSummary(sessionId)
                launch { repository.observeFileStatuses(sessionId).collect { _fileStatuses.value = it } }

                repository
                    .uploadSession(sessionId)
                    .catch { e -> _uiState.value = SubmitUiState.Failed(e.message ?: "Tải lên thất bại") }
                    .collect { progress ->
                        _uiState.value =
                            when (progress) {
                                is UploadProgress.InProgress -> {
                                    val isOffline = !connectivityObserver.isOnline.first()
                                    SubmitUiState.Uploading(progress.bytesSent, progress.totalBytes, isOffline)
                                }
                                is UploadProgress.Done -> SubmitUiState.Done
                                is UploadProgress.Failed -> SubmitUiState.Failed(progress.reason)
                                is UploadProgress.Conflict -> SubmitUiState.Conflict(progress.reason)
                                is UploadProgress.WaitingForWifi -> SubmitUiState.WaitingForWifi
                            }
                    }
            }
        }
    }
```

Add imports: `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.stateIn`, `com.luxmap.core.sync.SyncScheduler`, `com.luxmap.core.sync.UploadPreferences`, `com.luxmap.feature.survey.data.SessionFileStatus`, `com.luxmap.feature.survey.data.SessionUploadSummary`.

- [ ] **Step 3b: Update `SubmitViewModelTest.kt` for the new constructor arity**

This file now has 5 tests (4 pre-existing + Task 3's `wifi-only on and not on wifi...` one), all calling `SubmitViewModel(repository, sessionDao, connectivity)`, which no longer compiles once Step 3 lands (5 params now, `onSubmit` calls 2 new `UploadRepository` methods on a *non-relaxed* mock, and `onWifiOnlyToggled` needs a `SyncScheduler`). Replace the whole file:

```kotlin
package com.luxmap.feature.survey.ui.submit

import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.sync.SyncScheduler
import com.luxmap.core.sync.UploadPreferences
import com.luxmap.feature.survey.data.UploadProgress
import com.luxmap.feature.survey.data.UploadRepository
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalSurveySessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
import java.time.Instant

private fun session(recordingState: String) =
    LocalSurveySessionEntity(
        sessionId = "SESSION-1",
        surveySweepId = "SWEEP-1",
        workOrderId = "WO-1",
        recordingState = recordingState,
        syncState = null,
        startedAtUtc = Instant.parse("2026-10-08T10:00:00Z"),
        startedAtElapsedNs = 0L,
        endedAtUtc = null,
        durationSeconds = null,
        distanceMeters = null,
        gpsTrackFilePath = null,
        luxLogFilePath = null,
        frameTimestampLogFilePath = null,
        captureConfigFilePath = null,
        manifestFilePath = null,
        packageSchemaVersion = "v1",
        timestampSourceRealtime = true,
        bleGapDetected = false,
        createdAt = Instant.parse("2026-10-08T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-08T10:00:00Z"),
    )

// Every test whose session is "packaged" reaches the new sessionSummary()/observeFileStatuses()
// calls in onSubmit() - repository is a strict (non-relaxed) mock, so each such test must stub
// both, even though this plan's assertions don't care about their values.
private fun stubSummaryAndFileStatuses(repository: UploadRepository) {
    coEvery { repository.sessionSummary("SESSION-1") } returns null
    every { repository.observeFileStatuses("SESSION-1") } returns emptyFlow()
}

private fun wifiOnlyOffPreferences(): UploadPreferences {
    val prefs = mockk<UploadPreferences>()
    every { prefs.observeWifiOnlyUpload() } returns flowOf(false)
    return prefs
}

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a session that is not packaged yet shows NotPackagedYet and onSubmit is a no-op`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "recording")
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel =
                SubmitViewModel(repository, sessionDao, connectivity, wifiOnlyOffPreferences(), mockk(relaxed = true))

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.NotPackagedYet)
            }
            coVerify(exactly = 0) { repository.uploadSession(any()) }
        }

    @Test
    fun `a Conflict from the repository maps to SubmitUiState Conflict`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.Conflict("x"))
            stubSummaryAndFileStatuses(repository)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel =
                SubmitViewModel(repository, sessionDao, connectivity, wifiOnlyOffPreferences(), mockk(relaxed = true))

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.Conflict)
            }
        }

    @Test
    fun `moves from Idle to Uploading to Done as the repository flow progresses`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns
                flowOf(UploadProgress.InProgress(10_000L, 50_000L), UploadProgress.Done)
            stubSummaryAndFileStatuses(repository)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel =
                SubmitViewModel(repository, sessionDao, connectivity, wifiOnlyOffPreferences(), mockk(relaxed = true))

            viewModel.uiState.test {
                assertEquals(SubmitUiState.Idle, awaitItem())
                viewModel.onSubmit("SESSION-1")
                assertEquals(SubmitUiState.Uploading(10_000L, 50_000L, isOffline = false), awaitItem())
                assertEquals(SubmitUiState.Done, awaitItem())
            }
        }

    @Test
    fun `a Failed from the repository maps to SubmitUiState Failed`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.Failed("boom"))
            stubSummaryAndFileStatuses(repository)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val viewModel =
                SubmitViewModel(repository, sessionDao, connectivity, wifiOnlyOffPreferences(), mockk(relaxed = true))

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertEquals(SubmitUiState.Failed("boom"), awaitItem())
            }
        }

    @Test
    fun `wifi-only on and not on wifi reports WaitingForWifi instead of uploading`() =
        runTest {
            val repository = mockk<UploadRepository>()
            val sessionDao = mockk<SurveySessionDao>()
            coEvery { sessionDao.sessionById("SESSION-1") } returns session(recordingState = "packaged")
            every { repository.uploadSession("SESSION-1") } returns flowOf(UploadProgress.WaitingForWifi)
            stubSummaryAndFileStatuses(repository)
            val connectivity = mockk<ConnectivityObserver>()
            every { connectivity.isOnline } returns flowOf(true)
            val prefs = mockk<UploadPreferences>()
            every { prefs.observeWifiOnlyUpload() } returns flowOf(true)
            val viewModel = SubmitViewModel(repository, sessionDao, connectivity, prefs, mockk(relaxed = true))

            viewModel.uiState.test {
                assertTrue(awaitItem() is SubmitUiState.Idle)
                viewModel.onSubmit("SESSION-1")
                assertTrue(awaitItem() is SubmitUiState.WaitingForWifi)
            }
        }
}
```

- [ ] **Step 4: Rewrite `SubmitScreen.kt`**

```kotlin
package com.luxmap.feature.survey.ui.submit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.survey.data.SessionFileStatus
import com.luxmap.feature.survey.data.SessionUploadSummary

@Composable
fun SubmitScreen(
    sessionId: String,
    onBackToSurveyHub: () -> Unit,
    viewModel: SubmitViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fileStatuses by viewModel.fileStatuses.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val wifiOnlyUpload by viewModel.wifiOnlyUpload.collectAsState()

    LaunchedEffect(sessionId) { viewModel.onSubmit(sessionId) }

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        Text(text = "Nộp đợt khảo sát", style = MaterialTheme.typography.titleLarge)
        SubmitStatusBadge(uiState)
        summary?.let { SubmitSummaryGrid(it) }
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Chỉ tải lên qua Wi-Fi", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = wifiOnlyUpload, onCheckedChange = viewModel::onWifiOnlyToggled)
        }
        when (val state = uiState) {
            is SubmitUiState.Uploading -> {
                val fraction =
                    if (state.totalBytes == 0L) 0f else state.bytesSent.toFloat() / state.totalBytes.toFloat()
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            is SubmitUiState.Failed -> PrimaryButton(text = "Nộp lại", onClick = { viewModel.onSubmit(sessionId) })
            else -> Unit
        }
        SubmitFileStatusList(fileStatuses)
        PrimaryButton(text = "Về Khảo sát", onClick = onBackToSurveyHub, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SubmitStatusBadge(state: SubmitUiState) {
    when (state) {
        is SubmitUiState.Idle ->
            StatusBadge(text = "Đã lưu trên thiết bị", colors = SyncStatus.QUEUED_ONLINE.badgeColors())

        is SubmitUiState.Uploading ->
            StatusBadge(
                text = if (state.isOffline) SyncStatus.QUEUED_OFFLINE.label() else SyncStatus.SYNCING.label(),
                colors = if (state.isOffline) SyncStatus.QUEUED_OFFLINE.badgeColors() else SyncStatus.SYNCING.badgeColors(),
            )

        is SubmitUiState.WaitingForWifi ->
            StatusBadge(text = "Chờ Wi-Fi để tải lên", colors = SyncStatus.QUEUED_OFFLINE.badgeColors())

        is SubmitUiState.Done -> StatusBadge(text = SyncStatus.DONE.label(), colors = SyncStatus.DONE.badgeColors())
        is SubmitUiState.Failed -> StatusBadge(text = SyncStatus.FAILED.label(), colors = SyncStatus.FAILED.badgeColors())
        is SubmitUiState.Conflict -> StatusBadge(text = SyncStatus.CONFLICT.label(), colors = SyncStatus.CONFLICT.badgeColors())
        is SubmitUiState.NotPackagedYet ->
            Text("Phiên khảo sát chưa đóng gói xong, chưa thể nộp", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SubmitSummaryGrid(summary: SessionUploadSummary) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SummaryCell("Thời lượng", summary.durationSeconds?.let { "${it / 60} phút" } ?: "—")
            SummaryCell("Số đoạn video", summary.segmentCount.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            SummaryCell("Quãng đường", summary.distanceMeters?.let { "%.1f km".format(it / 1000) } ?: "—")
            SummaryCell("Dung lượng", "${summary.totalBytes / 1_000_000} MB")
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
) {
    Column(Modifier.padding(Spacing.sm)) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SubmitFileStatusList(files: List<SessionFileStatus>) {
    Column {
        files.forEach { file ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(file.label, style = MaterialTheme.typography.bodyMedium)
                val syncStatus = syncQueueStatusToSyncStatus(file.status)
                StatusBadge(text = syncStatus.label(), colors = syncStatus.badgeColors())
            }
        }
    }
}

private fun syncQueueStatusToSyncStatus(status: String): SyncStatus =
    when (status) {
        "syncing" -> SyncStatus.SYNCING
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        "done" -> SyncStatus.DONE
        else -> SyncStatus.QUEUED_ONLINE
    }
```

(The file-status badge deliberately always resolves a bare `"queued"` row to `QUEUED_ONLINE`, not an online/offline split — unlike the Hub's own queued-session badge in Phase 3, which has a live `ConnectivityObserver` reading next to it, this per-file list has no natural per-row connectivity context without duplicating `SubmitScreen`'s own top-level `isOffline` flag onto every row; the single top `SubmitStatusBadge` already carries that distinction for the whole screen. Documented as a deliberate simplification, not forgotten, in Task 6's drift log.)

- [ ] **Step 5: Wire `NavGraph.kt`**

Replace:

```kotlin
                SubmitScreen(sessionId = sessionId)
```

with:

```kotlin
                SubmitScreen(
                    sessionId = sessionId,
                    onBackToSurveyHub = {
                        navController.navigate(Routes.Survey.route) {
                            popUpTo(Routes.Survey.route) { inclusive = true }
                        }
                    },
                )
```

- [ ] **Step 6: Run to verify it passes**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitViewModel.kt app/src/main/java/com/luxmap/feature/survey/ui/submit/SubmitScreen.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/androidTest/java/com/luxmap/feature/survey/ui/submit/SubmitScreenTest.kt
git commit -m "feat(redesign-phase6): restyle SubmitScreen with the 2x2 grid, file list, wifi-only toggle"
```

---

### Task 6: Log known gaps, final build, open PR

**Files:**
- Modify: `docs/contract-drift.md`

- [ ] **Step 1: Log the 2 judgment calls from this phase**

```markdown
| F06 per-file status list doesn't match the spec's illustrative grouping | `SessionFileStatus` list (Phase 6) shows the real `create_survey_sweep`/`upload_survey_clip`×N/`upload_survey_raw`×3/`submit_survey_sweep` ops, not the spec's "video 1..n / log GPS+lux / manifest" 3-bucket wording | **Deliberate, not an oversight (2026-10-12)** — the real chain has `gps_track`/`lux_log`/`capture_config` as 3 separate raw ops plus 2 bookend ops, which don't group into exactly 3 rows; showing the real op-level breakdown with accurate labels was judged more honest than forcing a grouping the data doesn't have | — |
| F06 per-file list's "queued" rows never show offline vs. online | `SubmitScreen.kt`'s `syncQueueStatusToSyncStatus` always maps bare `"queued"` to `QUEUED_ONLINE` | **Deliberate simplification (2026-10-12)** — the screen's one top-level `SubmitStatusBadge` already carries the offline/online distinction for the whole submission; duplicating `ConnectivityObserver` onto every file row was judged not worth the complexity for this phase | — |
| F06 byte-level per-file progress | `SessionFileStatus` carries a `sync_queue.status` string, not a byte counter | **Deferred, confirmed with the user (2026-10-12)** — matches the existing `upload_survey_clip`/`upload_survey_raw` progress-granularity row above; needs those sync handlers' own work first, not a Phase 6 UI change | WP6 |
```

- [ ] **Step 2: Full build, ktlint, androidTest compile check, affected unit tests**

Run: `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin ktlintCheck testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest" --tests "com.luxmap.feature.survey.ui.submit.SubmitViewModelTest"`
Expected: BUILD SUCCESSFUL, both test classes pass.

- [ ] **Step 3: Commit**

```bash
git add docs/contract-drift.md
git commit -m "docs(redesign-phase6): log 3 known gaps from the submit screen restyle"
```

- [ ] **Step 4: Open PR**

Target `dev`. Title: `feat(redesign-phase6): restyle F06 submit screen, wire wifi-only upload constraint`. Body must flag: no adb/emulator in this environment (every `androidTest` file in this plan, including the `WorkManagerSyncTrigger`/`UploadPreferences`/`SyncQueueDao` tests, is compile-verified only), and that the full `testDebugUnitTest` suite was not run end-to-end (pre-existing, already-documented `CaptureViewModelTest` hang, unrelated to this branch's files).
