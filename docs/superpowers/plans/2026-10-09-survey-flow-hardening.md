# Survey Flow Hardening (fm-40 follow-up audit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the three real issues a post-merge audit of the F03→F06 survey flow found on `dev` after fm-40 (PR #51, #52) landed: a check-then-act race in the upload-retry path, `SyncQueueProcessor` not being serialized across its two callers, and leftover Vietnamese-language code comments.

**Architecture:** No new classes or libraries. Task 2 moves the "has this session already been enqueued?" decision from an in-memory `LocalSurveySessionEntity.syncState` read into a single atomic conditional `UPDATE ... WHERE syncState IS NULL` in `SurveySessionDao`, so two concurrent callers can never both see "not yet enqueued". Task 3 makes `SyncQueueProcessor` a Hilt `@Singleton` and wraps its one public method's body in a `kotlinx.coroutines.sync.Mutex`, so the same queue is never drained by two coroutines at once regardless of whether the caller is `SyncWorker` (background) or `RealUploadRepository` (foreground retry). Task 1 is pure comment translation, no behavior change.

**Tech Stack:** Kotlin, Room (DAO `@Query` UPDATE with `Int` return for affected-row count), kotlinx.coroutines (`Mutex`, `withLock`, `async`/`awaitAll` in the instrumented test), MockK, JUnit, Room Testing (in-memory `AndroidJUnit4` instrumented test) — all already in the project's approved stack, nothing new.

**Spec:** This plan has no separate spec doc — it implements the findings from the audit reported to the user on 2026-10-09 (see conversation; the three findings are restated in each task below) plus the pre-existing project rules in `CLAUDE.md` (offline-first, comment-language rule, git branch/commit conventions).

## Global Constraints

- Branch off `dev` (never `main`); one PR per logical change if reasonable, but these three tasks are small enough and related enough to ship as one PR per this plan's branch.
- Every commit stays under 400 changed lines (CLAUDE.md commit-size rule). Each task below is well under that on its own.
- All code comments in English only, simple everyday words (CLAUDE.md comment-language rule) — this plan's own Task 1 exists to fix past violations of this rule, so new code written in Tasks 2-3 must not add new ones.
- No new third-party dependencies — everything used here (`kotlinx.coroutines.sync.Mutex`, `async`/`awaitAll`) is already transitively available via the existing `kotlinx-coroutines-core`/`-test` dependencies.
- Run `./gradlew ktlintCheck` after every task and fix any formatting violation before moving on.
- Commit messages follow `<type>(scope): description`, scope `fm-40` (this is direct follow-up work on the fm-40 feature's own code), English, imperative, no trailing period.

## Review Focus

- Two near-simultaneous `uploadSession()` calls for the same `sessionId` (double-tap on "Nộp lại", or a quick screen re-entry) must result in exactly one enqueued `sync_queue` chain, never two. Pinned by Task 2's instrumented DAO test.
- A background `SyncWorker` run and a foreground `uploadSession()` call landing at the same moment must never execute two handler bodies concurrently against the same `sync_queue` table. Pinned by Task 3's unit test.
- After Task 2's change, a genuine first-time upload (freshly `packaged` session, `syncState` still `null`, no prior `sync_queue` rows) must still enqueue the chain exactly once, not zero times. Already covered by the existing "first submit enqueues the full chain" test, re-stubbed in Task 2 Step 1.
- A retry after a real `failed` op must still reset only `failed` rows (never `conflict`) even with the new claim-based gating replacing the old `syncState == null` check. Already covered by the existing "a retry resets failed ops back to queued" test, re-stubbed in Task 2 Step 1.
- Marking `SyncQueueProcessor` `@Singleton` must not break its existing direct-construction use in unit tests (every `SyncQueueProcessorTest`/`RealUploadRepositoryTest` test constructs it directly with `SyncQueueProcessor(dao, handlers)`, never through Hilt) — confirmed by keeping all pre-existing tests in both files green after Task 3.

---

### Task 1: Translate leftover Vietnamese code comments to English

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt:24`
- Modify: `app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt:5`
- Modify: `app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt:34`
- Modify: `app/src/main/java/com/luxmap/core/ble/LuxDeviceScanner.kt:24`
- Modify: `app/src/main/java/com/luxmap/core/ble/LuxDevicePreferences.kt:16`
- Modify: `app/src/main/java/com/luxmap/core/location/LocationTracker.kt:18-20,33-34`

**Interfaces:** None — comment-only changes, no signature or behavior changes.

- [ ] **Step 1: Translate each flagged comment**

In `CaptureConfigWriter.kt`, replace line 24:
```kotlin
    // Tạm để là 1 per BE — no registry exists yet to resolve a real ID (spec: survey-ingest-p2a.md §Tạo phiên)
```
with:
```kotlin
    // Hardcoded to 1 per BE - no registry exists yet to resolve a real ID (spec: survey-ingest-p2a.md's session-creation section)
```

In `CaptureUiState.kt`, replace line 5:
```kotlin
// Bước 0-4 of F04 (spec §8/C7) as explicit states. Recording carries its own warning flags
```
with:
```kotlin
// Step 0-4 of F04 (spec §8/C7) as explicit states. Recording carries its own warning flags
```

In `LuxSensorBleClient.kt`, replace line 34:
```kotlin
// connect()/disconnect() are separate from `samples` (review feedback) — Bước 0 calls connect()
```
with:
```kotlin
// connect()/disconnect() are separate from `samples` (review feedback) - step 0 calls connect()
```

In `LuxDeviceScanner.kt`, replace line 24:
```kotlin
// Replaces Bước 0's old hardcoded device address (spec's own open point) with a real scan the
```
with:
```kotlin
// Replaces step 0's old hardcoded device address (spec's own open point) with a real scan the
```

In `LuxDevicePreferences.kt`, replace line 16:
```kotlin
// Remembers the last BLE lux sensor picked in Bước 0, so a Field Engineer using the same sensor
```
with:
```kotlin
// Remembers the last BLE lux sensor picked in step 0, so a Field Engineer using the same sensor
```

In `LocationTracker.kt`, replace lines 18-20:
```kotlin
// Fused Location, lấy vị trí hiện tại một lần (không theo dõi liên tục) — đủ cho nút "định vị
// về vị trí hiện tại" của F12. Theo dõi vị trí liên tục khi khảo sát đêm (F03/F04) là việc
// khác, chưa tới lượt trong task này.
```
with:
```kotlin
// Fused Location, gets the current location once (not continuous tracking) - enough for F12's
// "locate me" button. Continuous location tracking during night survey (F03/F04) is a
// different job, not part of this task yet.
```

And replace lines 33-34:
```kotlin
        // Trả về null nếu chưa có quyền hoặc không lấy được vị trí — người gọi (ViewModel) tự
        // quyết định hiển thị gì khi null, không throw exception cho một luồng có thể đoán trước.
```
with:
```kotlin
        // Returns null when there is no permission or the location fetch fails - the caller
        // (ViewModel) decides what to show on null, instead of throwing for a predictable case.
```

- [ ] **Step 2: Run ktlint and compile to confirm nothing broke**

Run: `./gradlew ktlintCheck compileDebugKotlin --console=plain -q`
Expected: no output, exit code 0 (comment-only changes cannot fail a test, but ktlint checks comment line length/formatting too)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/capture/CaptureConfigWriter.kt \
        app/src/main/java/com/luxmap/feature/survey/ui/capture/CaptureUiState.kt \
        app/src/main/java/com/luxmap/core/ble/LuxSensorBleClient.kt \
        app/src/main/java/com/luxmap/core/ble/LuxDeviceScanner.kt \
        app/src/main/java/com/luxmap/core/ble/LuxDevicePreferences.kt \
        app/src/main/java/com/luxmap/core/location/LocationTracker.kt
git commit -m "docs(fm-40): translate remaining Vietnamese code comments to English"
```

---

### Task 2: Make "has this session already been enqueued" an atomic DB claim

**Context:** `RealUploadRepository.uploadSession` currently decides whether to call `enqueueChain(...)` by reading `session.syncState` from an in-memory `LocalSurveySessionEntity` fetched moments earlier, then separately writing `syncState = "queued"` back. Two near-simultaneous calls (double-tap "Nộp lại", or a fast screen re-entry re-firing `SubmitViewModel.onSubmit`) can both read `syncState == null` before either write lands, both call `enqueueChain`, and insert a full duplicate `sync_queue` chain for the same session (no DB uniqueness constraint exists on `clientOpId`).

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt`
- Modify: `app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt:28-46`
- Modify: `app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt`

**Interfaces:**
- Produces: `SurveySessionDao.claimForUpload(sessionId: String, updatedAt: Instant): Int` — returns `1` if this call just flipped `syncState` from `null` to `"queued"` (caller must run `enqueueChain`), `0` if `syncState` was already non-null (caller must only call `resetFailedOps`, the chain already exists).

- [ ] **Step 1: Write the failing instrumented test for the atomic claim**

Add to `app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt`, inside the imports add `import kotlinx.coroutines.async` and `import kotlinx.coroutines.awaitAll`, then add this test:

```kotlin
    @Test
    fun claimForUploadOnlyLetsOneConcurrentCallerClaimASession() =
        runTest {
            dao.insertSession(session("SESSION-1"))

            val results =
                listOf(
                    async { dao.claimForUpload("SESSION-1", Instant.parse("2026-10-09T10:00:00Z")) },
                    async { dao.claimForUpload("SESSION-1", Instant.parse("2026-10-09T10:00:01Z")) },
                ).awaitAll()

            assertEquals(1, results.count { it == 1 })
            assertEquals(1, results.count { it == 0 })
            assertEquals("queued", dao.sessionById("SESSION-1")?.syncState)
        }
```

This will not compile yet because `claimForUpload` does not exist on `SurveySessionDao`.

- [ ] **Step 2: Add `claimForUpload` to `SurveySessionDao`**

In `app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt`, add this method to the interface (next to `updateSession`):

```kotlin
    // Atomic conditional UPDATE, not a read-then-write from the caller - two callers racing to
    // upload the same session can both read syncState == null before either writes, but only
    // one of two concurrent calls to this single SQL statement can match the WHERE clause.
    @Query(
        "UPDATE local_survey_session SET syncState = 'queued', updatedAt = :updatedAt " +
            "WHERE sessionId = :sessionId AND syncState IS NULL",
    )
    suspend fun claimForUpload(
        sessionId: String,
        updatedAt: Instant,
    ): Int
```

Add `import java.time.Instant` to this file's imports if not already present (it is not — the file currently only imports Room and entity types).

- [ ] **Step 3: Run the instrumented test to verify it passes**

Run: `./gradlew connectedDebugAndroidTest --tests "com.luxmap.feature.survey.data.dao.SurveySessionDaoTest"`
Expected: PASS (requires a connected device/emulator - if none is available, skip this step and rely on Step 5's unit-level coverage plus manual confirmation that the SQL is a single atomic statement; note this in the task's commit message if skipped)

- [ ] **Step 4: Replace the in-memory syncState check in `RealUploadRepository.uploadSession`**

In `app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt`, replace:

```kotlin
                val opIds = opIdsFor(session, segments.size)
                if (session.syncState == null) {
                    enqueueChain(session, segments.size, opIds)
                    sessionDao.updateSession(session.copy(syncState = "queued", updatedAt = Instant.now()))
                } else {
                    // Retry of an earlier failed attempt: queuedRows() only ever reprocesses rows
                    // already in 'queued' state, so a 'failed' row needs this reset or it stays
                    // stuck forever even after the user taps "Nộp lại".
                    syncQueueDao.resetFailedOps(opIds, Instant.now())
                }
```

with:

```kotlin
                val opIds = opIdsFor(session, segments.size)
                val justClaimed = sessionDao.claimForUpload(sessionId, Instant.now()) == 1
                if (justClaimed) {
                    enqueueChain(session, segments.size, opIds)
                } else {
                    // Either a genuine retry (syncState was already non-null), or this call lost
                    // a race against a concurrent uploadSession call that claimed the session
                    // first - either way the chain is already enqueued, only a failed op (if any)
                    // needs resetting so queuedRows() picks it up again.
                    syncQueueDao.resetFailedOps(opIds, Instant.now())
                }
```

- [ ] **Step 5: Update `RealUploadRepositoryTest` to stub `claimForUpload` instead of relying on `syncState`**

In `app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt`:

In `` `first submit enqueues the full chain and emits Done once everything succeeds` ``, add right after the existing `coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()` line:
```kotlin
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
```

In `` `a retry does not enqueue again when syncState is already set` ``, add right after its `coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()` line:
```kotlin
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 0
```

In `` `a retry resets failed ops back to queued so the processor can pick them up again` ``, add right after its `coEvery { sessionDao.segmentsFor("SESSION-1") } returns emptyList()` line:
```kotlin
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 0
```

In `` `each clip op depends on the immediately preceding op, not on create_sweep directly` ``, add right after its `coEvery { sessionDao.segmentsFor("SESSION-1") } returns listOf(...)` line:
```kotlin
            coEvery { sessionDao.claimForUpload(any(), any()) } returns 1
```

Leave `` `onRowProgress invocations from the processor are forwarded as InProgress` `` unchanged - it does not assert anything about enqueueing, and the relaxed mock's default `0` return for the unstubbed call is harmless there.

- [ ] **Step 6: Run the unit tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealUploadRepositoryTest" --console=plain -q`
Expected: PASS, all 5 tests green

- [ ] **Step 7: Run ktlint and full compile**

Run: `./gradlew ktlintCheck compileDebugKotlin compileDebugUnitTestKotlin --console=plain -q`
Expected: no output, exit code 0

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luxmap/feature/survey/data/dao/SurveySessionDao.kt \
        app/src/main/java/com/luxmap/feature/survey/data/RealUploadRepository.kt \
        app/src/test/java/com/luxmap/feature/survey/data/RealUploadRepositoryTest.kt \
        app/src/androidTest/java/com/luxmap/feature/survey/data/dao/SurveySessionDaoTest.kt
git commit -m "fix(fm-40): close race where two uploadSession calls can double-enqueue"
```

---

### Task 3: Serialize `SyncQueueProcessor.processQueuedOps` across all callers

**Context:** `RealUploadRepository.uploadSession` calls `syncQueueProcessor.processQueuedOps()` directly, bypassing `WorkManagerSyncTrigger`'s `enqueueUniqueWork(..., ExistingWorkPolicy.KEEP, ...)` serialization that background `SyncWorker` runs go through. `SyncQueueProcessor` also has no Hilt scope annotation today, so even adding an instance-level lock would not help, because Hilt would hand out a fresh unscoped instance per injection site (one for `SyncWorker`, a different one for whatever ViewModel chain reaches `RealUploadRepository`). If a background `SyncWorker` run and a foreground retry land at the same moment, two `processQueuedOps()` calls can run concurrently against the same `sync_queue` rows with no mutex between them.

**Files:**
- Modify: `app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt`
- Modify: `app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `SyncQueueProcessor` becomes a Hilt `@Singleton` (same public API otherwise) - any code constructing it directly in tests is unaffected, since direct construction never goes through Hilt scoping.

- [ ] **Step 1: Write the failing test proving two concurrent calls can overlap today**

Add to `app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt`. First add these imports:
```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
```

Then add this test:
```kotlin
    @Test
    fun `two concurrent processQueuedOps calls on the same instance never run handler bodies at once`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(1, "OP-1"))
            var concurrentEntries = 0
            var maxConcurrentEntries = 0
            val slowHandler =
                object : SyncOpHandler {
                    override val opType = "upload_work_order_evidence"

                    override suspend fun handle(
                        payloadJson: String,
                        onProgress: suspend (Long, Long) -> Unit,
                    ): SyncOpResult {
                        concurrentEntries += 1
                        maxConcurrentEntries = maxOf(maxConcurrentEntries, concurrentEntries)
                        delay(50)
                        concurrentEntries -= 1
                        return SyncOpResult.Done
                    }
                }
            val processor = SyncQueueProcessor(dao, setOf(slowHandler))

            coroutineScope {
                launch { processor.processQueuedOps() }
                launch { processor.processQueuedOps() }
            }

            assertEquals(1, maxConcurrentEntries)
        }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueProcessorTest.two concurrent processQueuedOps calls on the same instance never run handler bodies at once" --console=plain -q`
Expected: FAIL, `maxConcurrentEntries` is `2`, not `1`

- [ ] **Step 3: Add the Mutex and make the class a Hilt singleton**

In `app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt`, replace:

```kotlin
package com.luxmap.core.sync

import java.time.Instant
import javax.inject.Inject

class SyncQueueProcessor
    @Inject
    constructor(
        private val dao: SyncQueueDao,
        private val handlers: Set<@JvmSuppressWildcards SyncOpHandler>,
    ) {
        suspend fun processQueuedOps(
            onRowProgress: suspend (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        ): Boolean {
            var stillPending = false
            for (row in dao.queuedRows()) {
```

with:

```kotlin
package com.luxmap.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

// Singleton so every caller (the background SyncWorker and RealUploadRepository's foreground
// retry) shares the same Mutex - an unscoped instance per injection site would give each caller
// its own lock, which would not stop two callers from draining sync_queue at the same time.
@Singleton
class SyncQueueProcessor
    @Inject
    constructor(
        private val dao: SyncQueueDao,
        private val handlers: Set<@JvmSuppressWildcards SyncOpHandler>,
    ) {
        private val mutex = Mutex()

        suspend fun processQueuedOps(
            onRowProgress: suspend (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        ): Boolean =
            mutex.withLock {
                var stillPending = false
                for (row in dao.queuedRows()) {
```

Then, at the end of the function body, change the closing so the `for` loop and the final `return stillPending` are inside the `withLock` block. The full function becomes:

```kotlin
        suspend fun processQueuedOps(
            onRowProgress: suspend (clientOpId: String, bytesSent: Long, totalBytes: Long) -> Unit = { _, _, _ -> },
        ): Boolean =
            mutex.withLock {
                var stillPending = false
                for (row in dao.queuedRows()) {
                    if (row.dependsOnClientOpId != null) {
                        when (dao.statusOf(row.dependsOnClientOpId)) {
                            "done" -> Unit
                            "failed", "conflict" ->
                                dao.updateStatus(
                                    row.id,
                                    "failed",
                                    row.attemptCount,
                                    "Dependency op failed",
                                    Instant.now(),
                                )
                            else -> stillPending = true
                        }
                        if (dao.statusOf(row.dependsOnClientOpId) != "done") continue
                    }

                    if (row.attemptCount >= MAX_ATTEMPTS) {
                        dao.updateStatus(row.id, "failed", row.attemptCount, "Exceeded retry attempts", Instant.now())
                        continue
                    }

                    val handler = handlers.firstOrNull { it.opType == row.opType }
                    if (handler == null) {
                        dao.updateStatus(row.id, "failed", row.attemptCount, "No handler for ${row.opType}", Instant.now())
                        continue
                    }

                    when (
                        val result =
                            handler.handle(row.payloadJson) { sent, total -> onRowProgress(row.clientOpId, sent, total) }
                    ) {
                        is SyncOpResult.Done -> dao.updateStatus(row.id, "done", row.attemptCount, null, Instant.now())
                        is SyncOpResult.RetryLater -> {
                            dao.updateStatus(row.id, "queued", row.attemptCount + 1, null, Instant.now())
                            stillPending = true
                        }
                        is SyncOpResult.Failed ->
                            dao.updateStatus(row.id, "failed", row.attemptCount, result.message, Instant.now())
                        is SyncOpResult.Conflict ->
                            dao.updateStatus(row.id, "conflict", row.attemptCount, result.message, Instant.now())
                    }
                }
                stillPending
            }

        companion object {
            const val MAX_ATTEMPTS = 8
        }
    }
```

(Note the trailing `return stillPending` becomes a plain `stillPending` expression, since it is now the last expression of the `withLock { }` lambda, which is itself the last expression of the function body - no explicit `return` needed or allowed there.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueProcessorTest" --console=plain -q`
Expected: PASS, all tests green including the new concurrency test

- [ ] **Step 5: Run ktlint and full compile**

Run: `./gradlew ktlintCheck compileDebugKotlin compileDebugUnitTestKotlin --console=plain -q`
Expected: no output, exit code 0

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt \
        app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt
git commit -m "fix(fm-40): serialize SyncQueueProcessor across background and foreground callers"
```

---

### Task 4: Push branch and open PR

**Files:** None - git/GitHub operations only.

- [ ] **Step 1: Confirm all three tasks' tests still pass together**

Run: `./gradlew ktlintCheck compileDebugKotlin compileDebugUnitTestKotlin --console=plain -q && ./gradlew testDebugUnitTest --tests "com.luxmap.feature.survey.data.RealUploadRepositoryTest" --tests "com.luxmap.core.sync.SyncQueueProcessorTest" --console=plain -q`
Expected: no output, exit code 0

- [ ] **Step 2: Push the branch**

```bash
git push -u origin HEAD
```

- [ ] **Step 3: Open the PR into `dev`**

```bash
gh pr create --base dev --title "fix(fm-40): close upload race conditions, translate remaining Vietnamese comments" --body "Follow-up from the post-merge survey-flow audit (2026-10-09). Fixes two race conditions found in RealUploadRepository/SyncQueueProcessor and translates 6 leftover Vietnamese code comments. See commit messages for details of each of the 3 fixes."
```
