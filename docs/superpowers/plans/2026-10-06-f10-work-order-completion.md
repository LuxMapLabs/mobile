# F10 — Work order completion implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a Field Engineer close out an Inspection or Repair work order from `WorkOrderDetailScreen` (F09) — capture the mandatory "after" photo for a repair, pick an outcome per fault for an inspection, and submit, all offline-first through a new generic `core/sync` outbox.

**Architecture:** One screen (`WorkOrderCompletionScreen`) branches on `task_kind`. Both branches write to Room first and return immediately; a new `core/sync` module (`SyncQueueEntity`/`SyncQueueManager`/`SyncQueueProcessor`/`SyncWorker`) drains the outbox in the background through per-feature `SyncOpHandler`s. The feature's own Room tables (`local_work_order_evidence`, `local_work_order_completion`) hold the business data; `sync_queue` only holds pointers to it.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Retrofit + kotlinx.serialization, WorkManager + Hilt-Work (new to this repo), `ActivityResultContracts.TakePicture()` (system Camera app, no CameraX), Fused Location (one-shot).

**Spec:** `docs/superpowers/specs/2026-10-06-f10-work-order-completion-design.md`

## Global Constraints

- No comments in any new or modified Kotlin file — confirmed user preference (2026-10-05), overrides CLAUDE.md's normal allowance for WHY-style comments elsewhere in this repo.
- `report_note` is required and must be ≥10 characters before "Nộp" is enabled or sent to the backend.
- `fault_outcomes` must be sent as `null`, never `[]`, whenever `task_kind != "inspection"` or the order has no linked faults — the backend 400s if the field is present at all in that case.
- Evidence upload is a single JPEG multipart request, ≤16 MB, no chunking/resume (different from the survey video pipeline).
- Only the assignee, only while `wo_status == "in_progress"`, may upload evidence or complete the order (enforced server-side; mobile does not need its own extra check beyond what the UI already implies from `allowed_actions`).
- Never auto-overwrite on a real sync conflict (`409` state-conflict) — keep the local record, mark it `conflict`, let the Manager/Web resolve it.
- Branch from `dev`, never commit to `main`. One `fix/fm-10-...` or `feat/fm-10-...` branch, conventional commit messages (`feat(fm-10): ...`), ≤400 changed lines per commit — split a task's commit further if it runs over.
- Run `./gradlew ktlintCheck` after every task and fix any violation before moving on.

## Review Focus

- Submitting completion (or capturing evidence) while offline must still write Room and enqueue the sync op — it must never block on connectivity. Owned by Task 9 (`WorkOrderCompletionViewModel`).
- Resubmitting after a Manager `return` must replace the previous local completion row for that work order, not create a second one. Owned by Task 6 (`WorkOrderCompletionDao`).
- An inspection order with zero linked faults must submit `fault_outcomes = null`, never an empty list. Owned by Task 9 (`WorkOrderCompletionViewModel`).
- If the evidence-upload op a `complete_work_order` op depends on ends up permanently `failed` (not just not-yet-done), the dependent op must also be marked `failed` instead of sitting in `queued` forever with no visible error. Owned by Task 3 (`SyncQueueProcessor`).
- The two different `409`s on `complete` — `AFTER_EVIDENCE_REQUIRED` (self-resolving) vs. a real state conflict (another device completed it) — must be told apart using the response body's error `code`, not just the HTTP status, since both arrive as `409`. Owned by Task 8 (`CompleteWorkOrderSyncHandler`).

---

## Task 1: `sync_queue` Room table

**Files:**
- Create: `app/src/main/java/com/luxmap/core/sync/SyncQueueEntity.kt`
- Create: `app/src/main/java/com/luxmap/core/sync/SyncQueueDao.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/luxmap/core/sync/SyncQueueDaoTest.kt`

**Interfaces:**
- Produces: `SyncQueueEntity(id: Long, clientOpId: String, opType: String, payloadJson: String, dependsOnClientOpId: String?, status: String, attemptCount: Int, lastError: String?, createdAt: Instant, updatedAt: Instant)`; `SyncQueueDao.insert/queuedRows/statusOf/updateStatus`.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
package com.luxmap.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class SyncQueueDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: SyncQueueDao

    private fun row(
        clientOpId: String,
        opType: String = "complete_work_order",
        status: String = "queued",
        dependsOnClientOpId: String? = null,
        attemptCount: Int = 0,
    ) = SyncQueueEntity(
        clientOpId = clientOpId,
        opType = opType,
        payloadJson = "{}",
        dependsOnClientOpId = dependsOnClientOpId,
        status = status,
        attemptCount = attemptCount,
        lastError = null,
        createdAt = Instant.parse("2026-10-06T10:00:00Z"),
        updatedAt = Instant.parse("2026-10-06T10:00:00Z"),
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.syncQueueDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun queuedRowsReturnsOnlyQueuedStatusOrderedByCreatedAt() =
        runTest {
            dao.insert(row("OP-1", status = "done"))
            dao.insert(row("OP-2", status = "queued"))

            val queued = dao.queuedRows()

            assertEquals(1, queued.size)
            assertEquals("OP-2", queued.first().clientOpId)
        }

    @Test
    fun statusOfReturnsNullWhenNoRowHasThatClientOpId() =
        runTest {
            assertNull(dao.statusOf("MISSING"))
        }

    @Test
    fun updateStatusChangesStatusAttemptCountAndLastError() =
        runTest {
            val id = dao.insert(row("OP-1"))

            dao.updateStatus(id, "failed", attemptCount = 3, lastError = "boom", updatedAt = Instant.now())

            val rows = dao.queuedRows()
            assertEquals(0, rows.size)
            assertEquals("failed", dao.statusOf("OP-1"))
        }
}
```

- [ ] **Step 2: Run test to verify it fails to compile (types don't exist yet)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.core.sync.SyncQueueDaoTest"`
Expected: FAIL — `SyncQueueEntity`/`SyncQueueDao`/`AppDatabase.syncQueueDao()` unresolved.

- [ ] **Step 3: Create `SyncQueueEntity`**

```kotlin
package com.luxmap.core.sync

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientOpId: String,
    val opType: String,
    val payloadJson: String,
    val dependsOnClientOpId: String?,
    val status: String,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
```

- [ ] **Step 4: Create `SyncQueueDao`**

```kotlin
package com.luxmap.core.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.time.Instant

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(row: SyncQueueEntity): Long

    @Query("SELECT * FROM sync_queue WHERE status = 'queued' ORDER BY createdAt ASC")
    suspend fun queuedRows(): List<SyncQueueEntity>

    @Query("SELECT status FROM sync_queue WHERE clientOpId = :clientOpId LIMIT 1")
    suspend fun statusOf(clientOpId: String): String?

    @Query(
        "UPDATE sync_queue SET status = :status, attemptCount = :attemptCount, lastError = :lastError, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        attemptCount: Int,
        lastError: String?,
        updatedAt: Instant,
    )
}
```

- [ ] **Step 5: Wire into `AppDatabase` and `DatabaseModule`**

In `AppDatabase.kt`, add `com.luxmap.core.sync.SyncQueueEntity::class` to `entities`, bump `version = 3`, and add:

```kotlin
    abstract fun syncQueueDao(): SyncQueueDao
```

In `DatabaseModule.kt`, add:

```kotlin
    @Provides
    @Singleton
    fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.core.sync.SyncQueueDaoTest"`
Expected: PASS

- [ ] **Step 7: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/sync/SyncQueueEntity.kt app/src/main/java/com/luxmap/core/sync/SyncQueueDao.kt app/src/main/java/com/luxmap/core/database/AppDatabase.kt app/src/main/java/com/luxmap/di/DatabaseModule.kt app/src/androidTest/java/com/luxmap/core/sync/SyncQueueDaoTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add sync_queue room table

generic outbox table for core/sync, no work-order knowledge inside it
EOF
)"
```

---

## Task 2: `SyncOpHandler` contract, `SyncTrigger`, `SyncQueueManager`, WorkManager/Hilt-Work dependencies

**Files:**
- Create: `app/src/main/java/com/luxmap/core/sync/SyncOpHandler.kt`
- Create: `app/src/main/java/com/luxmap/core/sync/SyncTrigger.kt`
- Create: `app/src/main/java/com/luxmap/core/sync/SyncQueueManager.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/test/java/com/luxmap/core/sync/SyncQueueManagerTest.kt`

**Interfaces:**
- Consumes: `SyncQueueDao` (Task 1).
- Produces: `interface SyncOpHandler { val opType: String; suspend fun handle(payloadJson: String): SyncOpResult }`; `sealed interface SyncOpResult { Done, RetryLater, Failed(message), Conflict(message) }`; `interface SyncTrigger { fun triggerNow() }`; `SyncQueueManager.enqueue(opType, payloadJson, clientOpId, dependsOnClientOpId = null)`.

- [ ] **Step 1: Add WorkManager + Hilt-Work to the version catalog**

In `gradle/libs.versions.toml`, `[versions]`:

```toml
work = "2.9.1"
hiltWork = "1.2.0"
```

`[libraries]`:

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
androidx-hilt-work = { group = "androidx.hilt", name = "hilt-work", version.ref = "hiltWork" }
androidx-hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hiltWork" }
```

In `app/build.gradle.kts`, inside `dependencies { ... }`, next to the other Hilt lines:

```kotlin
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
```

- [ ] **Step 2: Write the failing unit test**

```kotlin
package com.luxmap.core.sync

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncQueueManagerTest {
    @Test
    fun `enqueue inserts a queued row then triggers sync`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            val trigger = mockk<SyncTrigger>(relaxed = true)
            val manager = SyncQueueManager(dao, trigger)

            manager.enqueue(opType = "complete_work_order", payloadJson = "{}", clientOpId = "OP-1")

            coVerify(exactly = 1) {
                dao.insert(
                    match {
                        it.clientOpId == "OP-1" &&
                            it.opType == "complete_work_order" &&
                            it.payloadJson == "{}" &&
                            it.dependsOnClientOpId == null &&
                            it.status == "queued" &&
                            it.attemptCount == 0
                    },
                )
            }
            verify(exactly = 1) { trigger.triggerNow() }
        }

    @Test
    fun `enqueue carries the dependsOnClientOpId through when given one`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            val trigger = mockk<SyncTrigger>(relaxed = true)
            val manager = SyncQueueManager(dao, trigger)

            manager.enqueue(
                opType = "complete_work_order",
                payloadJson = "{}",
                clientOpId = "OP-2",
                dependsOnClientOpId = "OP-1",
            )

            coVerify(exactly = 1) { dao.insert(match { it.dependsOnClientOpId == "OP-1" }) }
        }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest"`
Expected: FAIL — `SyncQueueManager`/`SyncTrigger` unresolved.

- [ ] **Step 4: Create `SyncOpHandler.kt`**

```kotlin
package com.luxmap.core.sync

interface SyncOpHandler {
    val opType: String

    suspend fun handle(payloadJson: String): SyncOpResult
}

sealed interface SyncOpResult {
    data object Done : SyncOpResult

    data object RetryLater : SyncOpResult

    data class Failed(val message: String) : SyncOpResult

    data class Conflict(val message: String) : SyncOpResult
}
```

- [ ] **Step 5: Create `SyncTrigger.kt`** (interface only — the WorkManager-backed implementation is added in Task 3, once `SyncWorker` exists for it to enqueue)

```kotlin
package com.luxmap.core.sync

interface SyncTrigger {
    fun triggerNow()
}
```

- [ ] **Step 6: Create `SyncQueueManager.kt`**

```kotlin
package com.luxmap.core.sync

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncQueueManager
    @Inject
    constructor(
        private val dao: SyncQueueDao,
        private val syncTrigger: SyncTrigger,
    ) {
        suspend fun enqueue(
            opType: String,
            payloadJson: String,
            clientOpId: String,
            dependsOnClientOpId: String? = null,
        ) {
            val now = Instant.now()
            dao.insert(
                SyncQueueEntity(
                    clientOpId = clientOpId,
                    opType = opType,
                    payloadJson = payloadJson,
                    dependsOnClientOpId = dependsOnClientOpId,
                    status = "queued",
                    attemptCount = 0,
                    lastError = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            syncTrigger.triggerNow()
        }
    }
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueManagerTest"`
Expected: PASS

- [ ] **Step 8: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/luxmap/core/sync/SyncOpHandler.kt app/src/main/java/com/luxmap/core/sync/SyncTrigger.kt app/src/main/java/com/luxmap/core/sync/SyncQueueManager.kt app/src/test/java/com/luxmap/core/sync/SyncQueueManagerTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add syncqueuemanager and the syncophandler contract

adds workmanager and hilt-work to the approved stack per CLAUDE.md
decision 4 - no feature calls workmanager directly, only through this
EOF
)"
```

---

## Task 3: `SyncQueueProcessor`, `SyncWorker`, app-level WorkManager/Hilt wiring

**Files:**
- Create: `app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt`
- Create: `app/src/main/java/com/luxmap/core/sync/SyncWorker.kt`
- Create: `app/src/main/java/com/luxmap/core/sync/WorkManagerSyncTrigger.kt`
- Create: `app/src/main/java/com/luxmap/core/sync/SyncScheduler.kt`
- Create: `app/src/main/java/com/luxmap/di/SyncModule.kt`
- Modify: `app/src/main/java/com/luxmap/LuxMapApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt`

**Interfaces:**
- Consumes: `SyncQueueDao`, `SyncOpHandler`, `SyncOpResult` (Tasks 1–2).
- Produces: `SyncQueueProcessor.processQueuedOps(): Boolean` (returns `true` when something is still waiting to retry); `SyncWorker` (Hilt-injected `CoroutineWorker`); binds `SyncTrigger` to `WorkManagerSyncTrigger`.

- [ ] **Step 1: Write the failing unit test**

```kotlin
package com.luxmap.core.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun row(
    id: Long,
    clientOpId: String,
    opType: String = "upload_work_order_evidence",
    dependsOnClientOpId: String? = null,
    attemptCount: Int = 0,
) = SyncQueueEntity(
    id = id,
    clientOpId = clientOpId,
    opType = opType,
    payloadJson = "{}",
    dependsOnClientOpId = dependsOnClientOpId,
    status = "queued",
    attemptCount = attemptCount,
    lastError = null,
    createdAt = Instant.parse("2026-10-06T10:00:00Z"),
    updatedAt = Instant.parse("2026-10-06T10:00:00Z"),
)

private fun handler(
    opType: String,
    result: SyncOpResult,
) = object : SyncOpHandler {
    override val opType = opType

    override suspend fun handle(payloadJson: String): SyncOpResult = result
}

class SyncQueueProcessorTest {
    @Test
    fun `a Done result marks the row done and does not ask for a retry`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(1, "OP-1"))
            val processor = SyncQueueProcessor(dao, setOf(handler("upload_work_order_evidence", SyncOpResult.Done)))

            val stillPending = processor.processQueuedOps()

            assertFalse(stillPending)
            coVerify { dao.updateStatus(1, "done", 0, null, any()) }
        }

    @Test
    fun `a RetryLater result keeps the row queued, bumps attemptCount, and asks for a retry`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(1, "OP-1"))
            val processor =
                SyncQueueProcessor(dao, setOf(handler("upload_work_order_evidence", SyncOpResult.RetryLater)))

            val stillPending = processor.processQueuedOps()

            assertTrue(stillPending)
            coVerify { dao.updateStatus(1, "queued", 1, null, any()) }
        }

    @Test
    fun `a row is skipped while its dependency has not finished yet`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(2, "OP-2", opType = "complete_work_order", dependsOnClientOpId = "OP-1"))
            coEvery { dao.statusOf("OP-1") } returns "queued"
            val processor = SyncQueueProcessor(dao, setOf(handler("complete_work_order", SyncOpResult.Done)))

            processor.processQueuedOps()

            coVerify(exactly = 0) { dao.updateStatus(2, any(), any(), any(), any()) }
        }

    @Test
    fun `a row whose dependency permanently failed is also marked failed, not left queued forever`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(2, "OP-2", opType = "complete_work_order", dependsOnClientOpId = "OP-1"))
            coEvery { dao.statusOf("OP-1") } returns "failed"
            val processor = SyncQueueProcessor(dao, setOf(handler("complete_work_order", SyncOpResult.Done)))

            processor.processQueuedOps()

            coVerify { dao.updateStatus(2, "failed", 0, "Dependency op failed", any()) }
        }

    @Test
    fun `a row that has exceeded the retry cap is marked failed without calling the handler again`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns listOf(row(1, "OP-1", attemptCount = SyncQueueProcessor.MAX_ATTEMPTS))
            var handlerCalls = 0
            val processor =
                SyncQueueProcessor(
                    dao,
                    setOf(
                        object : SyncOpHandler {
                            override val opType = "upload_work_order_evidence"

                            override suspend fun handle(payloadJson: String): SyncOpResult {
                                handlerCalls += 1
                                return SyncOpResult.RetryLater
                            }
                        },
                    ),
                )

            processor.processQueuedOps()

            assertEquals(0, handlerCalls)
            coVerify { dao.updateStatus(1, "failed", SyncQueueProcessor.MAX_ATTEMPTS, "Exceeded retry attempts", any()) }
        }
}
```

(add `import org.junit.Assert.assertEquals` alongside the other JUnit imports)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueProcessorTest"`
Expected: FAIL — `SyncQueueProcessor` unresolved.

- [ ] **Step 3: Create `SyncQueueProcessor.kt`**

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
        suspend fun processQueuedOps(): Boolean {
            var stillPending = false
            for (row in dao.queuedRows()) {
                if (row.dependsOnClientOpId != null) {
                    when (dao.statusOf(row.dependsOnClientOpId)) {
                        "done" -> Unit
                        "failed", "conflict" ->
                            dao.updateStatus(row.id, "failed", row.attemptCount, "Dependency op failed", Instant.now())
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

                when (val result = handler.handle(row.payloadJson)) {
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
            return stillPending
        }

        companion object {
            const val MAX_ATTEMPTS = 8
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.sync.SyncQueueProcessorTest"`
Expected: PASS

- [ ] **Step 5: Create `SyncWorker.kt`**

```kotlin
package com.luxmap.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val processor: SyncQueueProcessor,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = if (processor.processQueuedOps()) Result.retry() else Result.success()
    }
```

- [ ] **Step 6: Create `WorkManagerSyncTrigger.kt`**

```kotlin
package com.luxmap.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkManagerSyncTrigger
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : SyncTrigger {
        private val workManager = WorkManager.getInstance(context)

        override fun triggerNow() {
            val request =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        companion object {
            const val SYNC_WORK_NAME = "sync_queue"
        }
    }
```

- [ ] **Step 7: Create `SyncScheduler.kt`**

```kotlin
package com.luxmap.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val workManager = WorkManager.getInstance(context)

        fun schedulePeriodicSync() {
            val request =
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        companion object {
            const val PERIODIC_SYNC_WORK_NAME = "sync_queue_periodic"
        }
    }
```

- [ ] **Step 8: Create `di/SyncModule.kt`**

```kotlin
package com.luxmap.di

import com.luxmap.core.sync.SyncTrigger
import com.luxmap.core.sync.WorkManagerSyncTrigger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun bindSyncTrigger(impl: WorkManagerSyncTrigger): SyncTrigger
}
```

- [ ] **Step 9: Make `LuxMapApp` a `Configuration.Provider` and schedule the periodic worker**

```kotlin
package com.luxmap

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.luxmap.core.map.initMapLibre
import com.luxmap.core.sync.SyncScheduler
import com.luxmap.feature.survey.capture.SurveySessionRecoveryUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LuxMapApp : Application(), Configuration.Provider {
    @Inject lateinit var surveySessionRecoveryUseCase: SurveySessionRecoveryUseCase

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        initMapLibre(this)
        CoroutineScope(Dispatchers.Default).launch { surveySessionRecoveryUseCase.recoverAny() }
        syncScheduler.schedulePeriodicSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
```

- [ ] **Step 10: Disable WorkManager's default initializer in the manifest**

In `AndroidManifest.xml`, add `xmlns:tools="http://schemas.android.com/tools"` to the root `<manifest>` tag, and add this `<provider>` as a direct child of `<application>` (alongside the existing `SurveyCaptureService`):

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 11: Run the full test suite to verify nothing broke, then build the app**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all existing + new tests)

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL — confirms the `Configuration.Provider` + manifest change didn't break Hilt/WorkManager initialization.

- [ ] **Step 12: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/sync/SyncQueueProcessor.kt app/src/main/java/com/luxmap/core/sync/SyncWorker.kt app/src/main/java/com/luxmap/core/sync/WorkManagerSyncTrigger.kt app/src/main/java/com/luxmap/core/sync/SyncScheduler.kt app/src/main/java/com/luxmap/di/SyncModule.kt app/src/main/java/com/luxmap/LuxMapApp.kt app/src/main/AndroidManifest.xml app/src/test/java/com/luxmap/core/sync/SyncQueueProcessorTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add syncworker and wire workmanager into the app

a row whose dependency permanently failed now also fails instead of
staying queued forever with no visible error
EOF
)"
```

---

## Task 4: Promote `reviewNote`/`reportNote`/`materialsUsed` onto `WorkOrderDetail`

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt`
- Modify: `app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderDetailMappingTest.kt`

**Interfaces:**
- Produces: `WorkOrderDetail.reviewNote: String?`, `.reportNote: String?`, `.materialsUsed: String?` (all default `null`, so existing call sites that don't pass them keep compiling).

Note: the spec says these 3 fields are "already read by the DTO" from F09 — checking the current `WorkOrderDetailDto.kt`, they are **not** actually there yet (F09 shipped without them). This task adds them for real, not just promotes an unused read.

- [ ] **Step 1: Write the failing test (extend the existing mapping test file)**

Add to `WorkOrderDetailMappingTest.kt`:

```kotlin
    @Test
    fun `maps reviewNote, reportNote and materialsUsed when present`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-4",
                title = "Sửa đèn tuyến D",
                taskKind = "repair",
                woStatus = "in_progress",
                dueDate = null,
                scheduledDate = null,
                note = null,
                reviewNote = "Chưa đủ ảnh sau, bổ sung thêm",
                reportNote = "Đã thay bóng đèn",
                materialsUsed = "1 bóng LED 30W",
                allowedActions = listOf("complete"),
                faults = emptyList(),
            )

        val detail = dto.toWorkOrderDetail()

        assertEquals("Chưa đủ ảnh sau, bổ sung thêm", detail.reviewNote)
        assertEquals("Đã thay bóng đèn", detail.reportNote)
        assertEquals("1 bóng LED 30W", detail.materialsUsed)
    }

    @Test
    fun `reviewNote, reportNote and materialsUsed default to null when absent`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-5",
                title = "Sửa đèn tuyến E",
                taskKind = "repair",
                woStatus = "assigned",
                dueDate = null,
                scheduledDate = null,
                note = null,
                allowedActions = listOf("start"),
                faults = emptyList(),
            )

        val detail = dto.toWorkOrderDetail()

        assertEquals(null, detail.reviewNote)
        assertEquals(null, detail.reportNote)
        assertEquals(null, detail.materialsUsed)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.WorkOrderDetailMappingTest"`
Expected: FAIL — `WorkOrderDetailDto` has no `reviewNote`/`reportNote`/`materialsUsed` parameter.

- [ ] **Step 3: Add the 3 fields to `WorkOrderDetailDto`**

In `WorkOrderDetailDto.kt`, after the `note` field:

```kotlin
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("report_note") val reportNote: String? = null,
    @SerialName("materials_used") val materialsUsed: String? = null,
```

- [ ] **Step 4: Add the 3 fields to `WorkOrderDetail` and the mapper**

In `WorkOrderDetail.kt`, add to the data class (after `note`):

```kotlin
    val reviewNote: String? = null,
    val reportNote: String? = null,
    val materialsUsed: String? = null,
```

And in `toWorkOrderDetail()`, add to the constructor call:

```kotlin
        reviewNote = reviewNote,
        reportNote = reportNote,
        materialsUsed = materialsUsed,
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.WorkOrderDetailMappingTest"`
Expected: PASS

- [ ] **Step 6: Run the full unit test suite** (confirms the new defaulted fields didn't break any other call site that constructs `WorkOrderDetail`)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 7: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderDetailDto.kt app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderDetail.kt app/src/test/java/com/luxmap/feature/workorder/data/WorkOrderDetailMappingTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add review_note, report_note and materials_used to work order detail

f09 read these fields off the wire but never added them to the dto or
domain model - f10 needs reviewNote for the manager-return banner and
reportNote/materialsUsed to prefill a resubmission
EOF
)"
```

---

## Task 5: `local_work_order_evidence` / `local_work_order_completion` Room tables

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderEvidenceEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderCompletionEntity.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDao.kt`
- Modify: `app/src/main/java/com/luxmap/core/database/AppDatabase.kt`
- Modify: `app/src/main/java/com/luxmap/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDaoTest.kt`

**Interfaces:**
- Produces: `LocalWorkOrderEvidenceEntity`, `LocalWorkOrderCompletionEntity`, `WorkOrderCompletionDao` (`insertEvidence`, `observeLatestEvidence`, `evidenceByClientOpId`, `latestEvidenceClientOpId`, `updateEvidenceUploadStatus`, `insertOrReplaceCompletion`, `observeCompletion`, `completionByWorkOrderId`, `updateCompletionSubmitStatus`).

- [ ] **Step 1: Write the failing DAO test**

```kotlin
package com.luxmap.feature.workorder.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WorkOrderCompletionDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: WorkOrderCompletionDao

    private fun evidence(
        clientOpId: String,
        workOrderId: String = "WO-1",
        capturedAt: Instant = Instant.parse("2026-10-06T10:00:00Z"),
    ) = LocalWorkOrderEvidenceEntity(
        clientOpId = clientOpId,
        workOrderId = workOrderId,
        kind = "after",
        filePath = "/data/evidence/$clientOpId.jpg",
        capturedAt = capturedAt,
        lat = 10.97,
        lng = 106.49,
        uploadStatus = "pending",
    )

    private fun completion(
        workOrderId: String = "WO-1",
        clientOpId: String = "COMP-1",
    ) = LocalWorkOrderCompletionEntity(
        workOrderId = workOrderId,
        reportNote = "Đã thay bóng đèn",
        materialsUsed = null,
        faultOutcomesJson = null,
        clientOpId = clientOpId,
        submitStatus = "pending",
    )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.workOrderCompletionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeLatestEvidenceReturnsTheMostRecentCaptureWhenTwoExist() =
        runTest {
            dao.insertEvidence(evidence("OLD", capturedAt = Instant.parse("2026-10-06T09:00:00Z")))
            dao.insertEvidence(evidence("NEW", capturedAt = Instant.parse("2026-10-06T10:00:00Z")))

            dao.observeLatestEvidence("WO-1").test {
                assertEquals("NEW", awaitItem()?.clientOpId)
            }
        }

    @Test
    fun latestEvidenceClientOpIdMatchesObserveLatestEvidence() =
        runTest {
            dao.insertEvidence(evidence("OLD", capturedAt = Instant.parse("2026-10-06T09:00:00Z")))
            dao.insertEvidence(evidence("NEW", capturedAt = Instant.parse("2026-10-06T10:00:00Z")))

            assertEquals("NEW", dao.latestEvidenceClientOpId("WO-1"))
        }

    @Test
    fun updateEvidenceUploadStatusChangesOnlyThatRow() =
        runTest {
            dao.insertEvidence(evidence("OP-1"))

            dao.updateEvidenceUploadStatus("OP-1", "synced")

            assertEquals("synced", dao.evidenceByClientOpId("OP-1")?.uploadStatus)
        }

    @Test
    fun insertOrReplaceCompletionReplacesThePreviousRowForTheSameWorkOrder() =
        runTest {
            dao.insertOrReplaceCompletion(completion(clientOpId = "COMP-1"))

            dao.insertOrReplaceCompletion(completion(clientOpId = "COMP-2"))

            assertEquals("COMP-2", dao.completionByWorkOrderId("WO-1")?.clientOpId)
        }

    @Test
    fun updateCompletionSubmitStatusChangesOnlyThatWorkOrder() =
        runTest {
            dao.insertOrReplaceCompletion(completion())

            dao.updateCompletionSubmitStatus("WO-1", "synced")

            assertEquals("synced", dao.completionByWorkOrderId("WO-1")?.submitStatus)
        }

    @Test
    fun latestEvidenceClientOpIdIsNullWhenNoneExists() =
        runTest {
            assertNull(dao.latestEvidenceClientOpId("WO-NONE"))
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDaoTest"`
Expected: FAIL — types unresolved.

- [ ] **Step 3: Create `LocalWorkOrderEvidenceEntity`**

```kotlin
package com.luxmap.feature.workorder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_work_order_evidence")
data class LocalWorkOrderEvidenceEntity(
    @PrimaryKey val clientOpId: String,
    val workOrderId: String,
    val kind: String,
    val filePath: String,
    val capturedAt: Instant,
    val lat: Double,
    val lng: Double,
    val uploadStatus: String,
)
```

- [ ] **Step 4: Create `LocalWorkOrderCompletionEntity`**

```kotlin
package com.luxmap.feature.workorder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_work_order_completion")
data class LocalWorkOrderCompletionEntity(
    @PrimaryKey val workOrderId: String,
    val reportNote: String,
    val materialsUsed: String?,
    val faultOutcomesJson: String?,
    val clientOpId: String,
    val submitStatus: String,
)
```

- [ ] **Step 5: Create `WorkOrderCompletionDao`**

```kotlin
package com.luxmap.feature.workorder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderCompletionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidence(evidence: LocalWorkOrderEvidenceEntity)

    @Query("SELECT * FROM local_work_order_evidence WHERE workOrderId = :workOrderId ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatestEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?>

    @Query("SELECT * FROM local_work_order_evidence WHERE clientOpId = :clientOpId LIMIT 1")
    suspend fun evidenceByClientOpId(clientOpId: String): LocalWorkOrderEvidenceEntity?

    @Query(
        "SELECT clientOpId FROM local_work_order_evidence WHERE workOrderId = :workOrderId " +
            "ORDER BY capturedAt DESC LIMIT 1",
    )
    suspend fun latestEvidenceClientOpId(workOrderId: String): String?

    @Query("UPDATE local_work_order_evidence SET uploadStatus = :status WHERE clientOpId = :clientOpId")
    suspend fun updateEvidenceUploadStatus(
        clientOpId: String,
        status: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceCompletion(completion: LocalWorkOrderCompletionEntity)

    @Query("SELECT * FROM local_work_order_completion WHERE workOrderId = :workOrderId LIMIT 1")
    fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?>

    @Query("SELECT * FROM local_work_order_completion WHERE workOrderId = :workOrderId LIMIT 1")
    suspend fun completionByWorkOrderId(workOrderId: String): LocalWorkOrderCompletionEntity?

    @Query("UPDATE local_work_order_completion SET submitStatus = :status WHERE workOrderId = :workOrderId")
    suspend fun updateCompletionSubmitStatus(
        workOrderId: String,
        status: String,
    )
}
```

- [ ] **Step 6: Wire into `AppDatabase` and `DatabaseModule`**

In `AppDatabase.kt`, add both entities to `entities`, bump `version = 4`, and add:

```kotlin
    abstract fun workOrderCompletionDao(): WorkOrderCompletionDao
```

In `DatabaseModule.kt`, add:

```kotlin
    @Provides
    @Singleton
    fun provideWorkOrderCompletionDao(db: AppDatabase): WorkOrderCompletionDao = db.workOrderCompletionDao()
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDaoTest"`
Expected: PASS

- [ ] **Step 8: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderEvidenceEntity.kt app/src/main/java/com/luxmap/feature/workorder/data/entity/LocalWorkOrderCompletionEntity.kt app/src/main/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDao.kt app/src/main/java/com/luxmap/core/database/AppDatabase.kt app/src/main/java/com/luxmap/di/DatabaseModule.kt app/src/androidTest/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDaoTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add local_work_order_evidence and local_work_order_completion tables

insertOrReplaceCompletion uses workOrderId as the primary key so a
resubmission after a manager return replaces the old row instead of
leaving two
EOF
)"
```

---

## Task 6: Completion DTOs, `WorkOrdersApi` additions, `WorkOrderCompletionRepository`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderCompletionDto.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/sync/WorkOrderSyncPayloads.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderCompletionRepository.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepository.kt`
- Modify: `app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt`
- Modify: `app/src/main/java/com/luxmap/di/RepositoryModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepositoryTest.kt`

**Interfaces:**
- Consumes: `WorkOrderCompletionDao` (Task 5), `SyncQueueManager` (Task 2).
- Produces: `WorkOrderCompletionRepository.observeEvidence/observeCompletion/captureAfterEvidence/submitCompletion`; `FaultOutcome(faultId, outcome)`, `EvidenceSyncPayload(clientOpId)`, `CompletionSyncPayload(workOrderId)` (shared with Task 8's sync handlers — both sides decode/encode the exact same classes, nothing is duplicated).

Note on `captureAfterEvidence`'s signature: the spec sketches it without a `clientOpId` parameter, but the evidence file's path is `{clientOpId}.jpg` (spec, "Evidence photo capture" section) — something has to decide that id before the photo is even taken, so it can't be generated inside the repository. Task 10 generates it in `EvidenceCaptureViewModel` and passes it in. This is a necessary clarification of the spec's sketch, not a contradiction of it.

- [ ] **Step 1: Write the failing repository test**

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RealWorkOrderCompletionRepositoryTest {
    @Test
    fun `captureAfterEvidence writes the local row then enqueues an upload op with no dependency`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager)

            repository.captureAfterEvidence(
                workOrderId = "WO-1",
                clientOpId = "OP-1",
                filePath = "/data/OP-1.jpg",
                lat = 10.97,
                lng = 106.49,
                capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
            )

            coVerify {
                dao.insertEvidence(match { it.clientOpId == "OP-1" && it.workOrderId == "WO-1" && it.kind == "after" })
            }
            coVerify {
                syncQueueManager.enqueue(
                    opType = "upload_work_order_evidence",
                    payloadJson = "{\"client_op_id\":\"OP-1\"}",
                    clientOpId = "OP-1",
                    dependsOnClientOpId = null,
                )
            }
        }

    @Test
    fun `submitCompletion for a repair depends on the latest evidence clientOpId`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            io.mockk.coEvery { dao.latestEvidenceClientOpId("WO-1") } returns "OP-1"
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager)

            repository.submitCompletion(
                workOrderId = "WO-1",
                taskKind = "repair",
                reportNote = "Đã thay bóng đèn",
                materialsUsed = null,
                faultOutcomes = null,
            )

            coVerify {
                syncQueueManager.enqueue(
                    opType = "complete_work_order",
                    payloadJson = any(),
                    clientOpId = any(),
                    dependsOnClientOpId = "OP-1",
                )
            }
        }

    @Test
    fun `submitCompletion for an inspection does not depend on any evidence op`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager)

            repository.submitCompletion(
                workOrderId = "WO-2",
                taskKind = "inspection",
                reportNote = "Không phát hiện sự cố",
                materialsUsed = null,
                faultOutcomes = listOf(FaultOutcome("FAULT-1", "fault_absent")),
            )

            coVerify {
                syncQueueManager.enqueue(
                    opType = "complete_work_order",
                    payloadJson = any(),
                    clientOpId = any(),
                    dependsOnClientOpId = null,
                )
            }
        }

    @Test
    fun `observeEvidence and observeCompletion pass the DAO flows through unchanged`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>()
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = "/data/OP-1.jpg",
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            val completion =
                LocalWorkOrderCompletionEntity(
                    workOrderId = "WO-1",
                    reportNote = "note",
                    materialsUsed = null,
                    faultOutcomesJson = null,
                    clientOpId = "COMP-1",
                    submitStatus = "pending",
                )
            io.mockk.every { dao.observeLatestEvidence("WO-1") } returns flowOf(evidence)
            io.mockk.every { dao.observeCompletion("WO-1") } returns flowOf(completion)
            val repository = RealWorkOrderCompletionRepository(dao, mockk(relaxed = true))

            assertEquals("OP-1", repository.observeEvidence("WO-1").let { kotlinx.coroutines.flow.first(it) }?.clientOpId)
            assertEquals("COMP-1", repository.observeCompletion("WO-1").let { kotlinx.coroutines.flow.first(it) }?.clientOpId)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`
Expected: FAIL — types unresolved.

- [ ] **Step 3: Create the completion DTOs**

```kotlin
package com.luxmap.feature.workorder.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompleteWorkOrderRequestDto(
    @SerialName("report_note") val reportNote: String,
    @SerialName("materials_used") val materialsUsed: String? = null,
    @SerialName("fault_outcomes") val faultOutcomes: List<FaultOutcomeRequestDto>? = null,
)

@Serializable
data class FaultOutcomeRequestDto(
    @SerialName("fault_id") val faultId: String,
    val outcome: String,
)

@Serializable
data class EvidenceItemDto(
    @SerialName("evidence_id") val evidenceId: String,
    val kind: String,
    @SerialName("captured_at") val capturedAt: String,
    val lat: Double,
    val lng: Double,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("original_url") val originalUrl: String,
)
```

- [ ] **Step 4: Create the shared sync payload/domain types**

```kotlin
package com.luxmap.feature.workorder.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FaultOutcome(
    @SerialName("fault_id") val faultId: String,
    val outcome: String,
)

@Serializable
data class EvidenceSyncPayload(
    @SerialName("client_op_id") val clientOpId: String,
)

@Serializable
data class CompletionSyncPayload(
    @SerialName("work_order_id") val workOrderId: String,
)
```

- [ ] **Step 5: Add `complete` and `uploadEvidence` to `WorkOrdersApi`**

```kotlin
    @POST("api/v1/work-orders/{id}/complete")
    suspend fun complete(
        @Path("id") id: String,
        @Body body: CompleteWorkOrderRequestDto,
    ): WorkOrderDetailDto

    @Multipart
    @POST("api/v1/work-orders/{id}/evidence")
    suspend fun uploadEvidence(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("kind") kind: RequestBody,
        @Part("captured_at") capturedAt: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("client_op_id") clientOpId: RequestBody,
    ): EvidenceItemDto
```

(add imports: `com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto`, `com.luxmap.feature.workorder.data.dto.EvidenceItemDto`, `okhttp3.MultipartBody`, `okhttp3.RequestBody`, `retrofit2.http.Body`, `retrofit2.http.Multipart`, `retrofit2.http.Part`)

- [ ] **Step 6: Create `WorkOrderCompletionRepository` interface**

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkOrderCompletionRepository {
    fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?>

    fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?>

    suspend fun captureAfterEvidence(
        workOrderId: String,
        clientOpId: String,
        filePath: String,
        lat: Double,
        lng: Double,
        capturedAt: Instant,
    )

    suspend fun submitCompletion(
        workOrderId: String,
        taskKind: String,
        reportNote: String,
        materialsUsed: String?,
        faultOutcomes: List<FaultOutcome>?,
    )
}
```

- [ ] **Step 7: Create `RealWorkOrderCompletionRepository`**

```kotlin
package com.luxmap.feature.workorder.data

import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EvidenceSyncPayload
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderCompletionRepository
    @Inject
    constructor(
        private val dao: WorkOrderCompletionDao,
        private val syncQueueManager: SyncQueueManager,
    ) : WorkOrderCompletionRepository {
        override fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?> =
            dao.observeLatestEvidence(workOrderId)

        override fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?> =
            dao.observeCompletion(workOrderId)

        override suspend fun captureAfterEvidence(
            workOrderId: String,
            clientOpId: String,
            filePath: String,
            lat: Double,
            lng: Double,
            capturedAt: Instant,
        ) {
            dao.insertEvidence(
                LocalWorkOrderEvidenceEntity(
                    clientOpId = clientOpId,
                    workOrderId = workOrderId,
                    kind = "after",
                    filePath = filePath,
                    capturedAt = capturedAt,
                    lat = lat,
                    lng = lng,
                    uploadStatus = "pending",
                ),
            )
            syncQueueManager.enqueue(
                opType = "upload_work_order_evidence",
                payloadJson = json.encodeToString(EvidenceSyncPayload(clientOpId)),
                clientOpId = clientOpId,
            )
        }

        override suspend fun submitCompletion(
            workOrderId: String,
            taskKind: String,
            reportNote: String,
            materialsUsed: String?,
            faultOutcomes: List<FaultOutcome>?,
        ) {
            val clientOpId = UUID.randomUUID().toString()
            val dependsOn = if (taskKind == "repair") dao.latestEvidenceClientOpId(workOrderId) else null
            dao.insertOrReplaceCompletion(
                LocalWorkOrderCompletionEntity(
                    workOrderId = workOrderId,
                    reportNote = reportNote,
                    materialsUsed = materialsUsed,
                    faultOutcomesJson = faultOutcomes?.let { json.encodeToString(it) },
                    clientOpId = clientOpId,
                    submitStatus = "pending",
                ),
            )
            syncQueueManager.enqueue(
                opType = "complete_work_order",
                payloadJson = json.encodeToString(CompletionSyncPayload(workOrderId)),
                clientOpId = clientOpId,
                dependsOnClientOpId = dependsOn,
            )
        }

        private companion object {
            val json = Json { ignoreUnknownKeys = true }
        }
    }
```

- [ ] **Step 8: Bind the repository in `RepositoryModule`**

```kotlin
    @Binds
    abstract fun bindWorkOrderCompletionRepository(impl: RealWorkOrderCompletionRepository): WorkOrderCompletionRepository
```

(matches the precedent already set by `bindWorkOrderDetailRepository` — the real backend for F10 is already confirmed, same as F09, so there is no `FakeWorkOrderCompletionRepository`)

- [ ] **Step 9: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`
Expected: PASS

- [ ] **Step 10: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/data/dto/WorkOrderCompletionDto.kt app/src/main/java/com/luxmap/feature/workorder/data/sync/WorkOrderSyncPayloads.kt app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderCompletionRepository.kt app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepository.kt app/src/main/java/com/luxmap/core/network/WorkOrdersApi.kt app/src/main/java/com/luxmap/di/RepositoryModule.kt app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add workordercompletionrepository

writes room first, enqueues the sync op after - never calls the api
directly, matching every other feature repository in this codebase
EOF
)"
```

---

## Task 7: Shared API error-code helper

**Files:**
- Create: `app/src/main/java/com/luxmap/core/network/ApiError.kt`
- Test: `app/src/test/java/com/luxmap/core/network/ApiErrorTest.kt`

**Interfaces:**
- Produces: `ApiErrorEnvelope`, `ApiErrorBody`, `HttpException.errorCodeOrNull(): String?`.

Note: `feature/auth/data/AuthDtos.kt` already has an identical envelope and a private `errorCodeFrom` helper inside `RealAuthRepository`. This task does not touch that file (unrelated to F10, and CLAUDE.md says not to refactor code the task doesn't need) — it adds a second, equivalent, public helper in `core/network` for the two new consumers in Task 8, which live outside the auth feature.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorTest {
    @Test
    fun `errorCodeOrNull reads the code out of the error envelope`() {
        val body = """{"error":{"code":"AFTER_EVIDENCE_REQUIRED","message":"no after photo"}}"""
        val exception = HttpException(Response.error<Any>(409, body.toResponseBody("application/json".toMediaType())))

        assertEquals("AFTER_EVIDENCE_REQUIRED", exception.errorCodeOrNull())
    }

    @Test
    fun `errorCodeOrNull returns null when the body is not the expected shape`() {
        val exception = HttpException(Response.error<Any>(500, "oops".toResponseBody("text/plain".toMediaType())))

        assertNull(exception.errorCodeOrNull())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.network.ApiErrorTest"`
Expected: FAIL — `errorCodeOrNull` unresolved.

- [ ] **Step 3: Create `ApiError.kt`**

```kotlin
package com.luxmap.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

private val errorJson = Json { ignoreUnknownKeys = true }

fun HttpException.errorCodeOrNull(): String? =
    runCatching {
        val body = response()?.errorBody()?.string() ?: return null
        errorJson.decodeFromString<ApiErrorEnvelope>(body).error.code
    }.getOrNull()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.network.ApiErrorTest"`
Expected: PASS

- [ ] **Step 5: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/core/network/ApiError.kt app/src/test/java/com/luxmap/core/network/ApiErrorTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add shared api error-code helper

needed so the two new sync handlers can tell apart two different 409s
on complete by the body's error code, not just the http status
EOF
)"
```

---

## Task 8: `UploadWorkOrderEvidenceSyncHandler`, `CompleteWorkOrderSyncHandler`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandler.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt`
- Modify: `app/src/main/java/com/luxmap/di/SyncModule.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandlerTest.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandlerTest.kt`

**Interfaces:**
- Consumes: `WorkOrdersApi.complete/uploadEvidence` (Task 6), `WorkOrderCompletionDao` (Task 5), `HttpException.errorCodeOrNull()` (Task 7), `SyncOpHandler`/`SyncOpResult` (Task 2).
- Produces: two `SyncOpHandler` implementations, multibound into the `Set<SyncOpHandler>` that `SyncQueueProcessor` (Task 3) already consumes.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dto.EvidenceItemDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.time.Instant

private fun httpException(
    code: Int,
    errorCode: String? = null,
) = HttpException(
    Response.error<Any>(
        code,
        (if (errorCode == null) "" else """{"error":{"code":"$errorCode","message":"x"}}""")
            .toResponseBody("application/json".toMediaType()),
    ),
)

class UploadWorkOrderEvidenceSyncHandlerTest {
    @Test
    fun `a successful upload marks the local evidence row synced and returns Done`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } returns
                EvidenceItemDto("EV-1", "after", "2026-10-06T10:00:00Z", 10.97, 106.49, "t", "o")
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateEvidenceUploadStatus("OP-1", "synced") }
        }

    @Test
    fun `a 500 maps to RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } throws httpException(500)
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }

    @Test
    fun `a 415 maps to Failed, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } throws
                httpException(415, "UNSUPPORTED_IMAGE_FORMAT")
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.Failed)
        }

    @Test
    fun `a 409 on evidence upload maps to Conflict, not RetryLater`() =
        runTest {
            val tempFile = File.createTempFile("evidence", ".jpg").apply { writeBytes(byteArrayOf(1)) }
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            coEvery { dao.evidenceByClientOpId("OP-1") } returns evidence
            coEvery { api.uploadEvidence(any(), any(), any(), any(), any(), any(), any()) } throws
                httpException(409, "WORK_ORDER_NOT_IN_PROGRESS")
            val handler = UploadWorkOrderEvidenceSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(EvidenceSyncPayload("OP-1")))

            assertTrue(result is SyncOpResult.Conflict)
        }
}
```

```kotlin
package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private fun httpException(
    code: Int,
    errorCode: String? = null,
) = HttpException(
    Response.error<Any>(
        code,
        (if (errorCode == null) "" else """{"error":{"code":"$errorCode","message":"x"}}""")
            .toResponseBody("application/json".toMediaType()),
    ),
)

private fun completion(workOrderId: String = "WO-1") =
    LocalWorkOrderCompletionEntity(
        workOrderId = workOrderId,
        reportNote = "Đã thay bóng đèn",
        materialsUsed = null,
        faultOutcomesJson = null,
        clientOpId = "COMP-1",
        submitStatus = "pending",
    )

private fun detailDto(workOrderId: String = "WO-1") =
    WorkOrderDetailDto(
        workOrderId = workOrderId,
        title = "t",
        taskKind = "repair",
        woStatus = "done",
        dueDate = null,
        scheduledDate = null,
        note = null,
        allowedActions = emptyList(),
        faults = emptyList(),
    )

class CompleteWorkOrderSyncHandlerTest {
    @Test
    fun `a successful complete marks the local completion row synced and returns Done`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } returns detailDto()
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.Done)
            coVerify { dao.updateCompletionSubmitStatus("WO-1", "synced") }
        }

    @Test
    fun `409 AFTER_EVIDENCE_REQUIRED maps to RetryLater, not Conflict`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } throws httpException(409, "AFTER_EVIDENCE_REQUIRED")
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.RetryLater)
        }

    @Test
    fun `a 409 with a different code is a real state Conflict, not RetryLater`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } throws httpException(409, "ALREADY_COMPLETED")
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.Conflict)
        }

    @Test
    fun `a 400 maps to Failed`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            coEvery { dao.completionByWorkOrderId("WO-1") } returns completion()
            coEvery { api.complete("WO-1", any()) } throws httpException(400, "VALIDATION_FAILED")
            val handler = CompleteWorkOrderSyncHandler(api, dao)

            val result = handler.handle(Json.encodeToString(CompletionSyncPayload("WO-1")))

            assertTrue(result is SyncOpResult.Failed)
        }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.sync.*"`
Expected: FAIL — handler classes unresolved.

- [ ] **Step 3: Create `UploadWorkOrderEvidenceSyncHandler`**

```kotlin
package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.asRequestBody
import okhttp3.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UploadWorkOrderEvidenceSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: WorkOrderCompletionDao,
    ) : SyncOpHandler {
        override val opType = "upload_work_order_evidence"

        override suspend fun handle(payloadJson: String): SyncOpResult {
            val payload = Json.decodeFromString<EvidenceSyncPayload>(payloadJson)
            val evidence =
                dao.evidenceByClientOpId(payload.clientOpId) ?: return SyncOpResult.Failed("Local evidence row missing")

            return try {
                val file = File(evidence.filePath)
                api.uploadEvidence(
                    id = evidence.workOrderId,
                    file = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaType())),
                    kind = evidence.kind.toRequestBody(),
                    capturedAt = evidence.capturedAt.toString().toRequestBody(),
                    lat = evidence.lat.toString().toRequestBody(),
                    lng = evidence.lng.toString().toRequestBody(),
                    clientOpId = evidence.clientOpId.toRequestBody(),
                )
                dao.updateEvidenceUploadStatus(evidence.clientOpId, "synced")
                SyncOpResult.Done
            } catch (e: HttpException) {
                when (e.code()) {
                    409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    400, 403, 404, 415 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

- [ ] **Step 4: Create `CompleteWorkOrderSyncHandler`**

```kotlin
package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.network.errorCodeOrNull
import com.luxmap.core.sync.SyncOpHandler
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.FaultOutcomeRequestDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class CompleteWorkOrderSyncHandler
    @Inject
    constructor(
        private val api: WorkOrdersApi,
        private val dao: WorkOrderCompletionDao,
    ) : SyncOpHandler {
        override val opType = "complete_work_order"

        override suspend fun handle(payloadJson: String): SyncOpResult {
            val payload = Json.decodeFromString<CompletionSyncPayload>(payloadJson)
            val completion =
                dao.completionByWorkOrderId(payload.workOrderId) ?: return SyncOpResult.Failed("Local completion row missing")
            val outcomes = completion.faultOutcomesJson?.let { Json.decodeFromString<List<FaultOutcome>>(it) }

            return try {
                api.complete(
                    id = payload.workOrderId,
                    body =
                        CompleteWorkOrderRequestDto(
                            reportNote = completion.reportNote,
                            materialsUsed = completion.materialsUsed,
                            faultOutcomes = outcomes?.map { FaultOutcomeRequestDto(it.faultId, it.outcome) },
                        ),
                )
                dao.updateCompletionSubmitStatus(payload.workOrderId, "synced")
                SyncOpResult.Done
            } catch (e: HttpException) {
                when {
                    e.code() == 409 && e.errorCodeOrNull() == "AFTER_EVIDENCE_REQUIRED" -> SyncOpResult.RetryLater
                    e.code() == 409 -> SyncOpResult.Conflict(e.errorCodeOrNull() ?: "conflict")
                    e.code() == 400 || e.code() == 404 -> SyncOpResult.Failed(e.errorCodeOrNull() ?: "HTTP ${e.code()}")
                    else -> SyncOpResult.RetryLater
                }
            } catch (e: IOException) {
                SyncOpResult.RetryLater
            }
        }
    }
```

- [ ] **Step 5: Multibind both handlers in `SyncModule`**

```kotlin
    @Binds
    @IntoSet
    abstract fun bindUploadWorkOrderEvidenceSyncHandler(impl: UploadWorkOrderEvidenceSyncHandler): SyncOpHandler

    @Binds
    @IntoSet
    abstract fun bindCompleteWorkOrderSyncHandler(impl: CompleteWorkOrderSyncHandler): SyncOpHandler
```

(add imports `com.luxmap.core.sync.SyncOpHandler`, `com.luxmap.feature.workorder.data.sync.CompleteWorkOrderSyncHandler`, `com.luxmap.feature.workorder.data.sync.UploadWorkOrderEvidenceSyncHandler`, `dagger.multibindings.IntoSet`)

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.sync.*"`
Expected: PASS

- [ ] **Step 7: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandler.kt app/src/main/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandler.kt app/src/main/java/com/luxmap/di/SyncModule.kt app/src/test/java/com/luxmap/feature/workorder/data/sync/UploadWorkOrderEvidenceSyncHandlerTest.kt app/src/test/java/com/luxmap/feature/workorder/data/sync/CompleteWorkOrderSyncHandlerTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add sync handlers for work order evidence upload and complete

complete tells apart the two different 409s by the error body's code,
not just the http status, since after_evidence_required and a real
state conflict both arrive as 409
EOF
)"
```

---

## Task 9: `WorkOrderCompletionUiState`, `WorkOrderCompletionViewModel`

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionUiState.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionViewModel.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionViewModelTest.kt`

**Interfaces:**
- Consumes: `WorkOrderDetailRepository` (existing), `WorkOrderCompletionRepository` (Task 6), `ConnectivityObserver` (existing).
- Produces: `WorkOrderCompletionUiState` (Loading/Success/Empty/Error, 4 states); `WorkOrderCompletionUiState.Success.canSubmit()`, `.syncStatus()`; `WorkOrderCompletionViewModel.onReportNoteChanged/onMaterialsUsedChanged/onFaultOutcomeSelected/submit`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() =
    SavedStateHandle(mapOf(WorkOrderCompletionViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

private fun repairDetail(
    faults: List<WorkOrderFaultDetail> = emptyList(),
    reviewNote: String? = null,
) = WorkOrderDetail(
    workOrderId = WORK_ORDER_ID,
    title = "Sửa đèn tuyến A",
    woStatus = "in_progress",
    taskKind = "repair",
    dueDate = null,
    scheduledDate = null,
    note = null,
    reviewNote = reviewNote,
    reportNote = null,
    materialsUsed = null,
    allowedActions = listOf("complete"),
    faults = faults,
)

private fun inspectionDetail(faults: List<WorkOrderFaultDetail>) = repairDetail(faults).copy(taskKind = "inspection")

private fun fault(id: String) =
    WorkOrderFaultDetail(
        faultId = id,
        poleId = null,
        lat = 10.0,
        lng = 106.0,
        faultType = "lamp_out",
        faultStatus = "confirmed",
        severity = "high",
        inspectionOutcome = null,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class WorkOrderCompletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        detail: WorkOrderDetail?,
        completionRepository: WorkOrderCompletionRepository,
        isOnline: Boolean = true,
    ): WorkOrderCompletionViewModel {
        val detailRepository = mockk<WorkOrderDetailRepository>()
        every { detailRepository.observeWorkOrderDetail(WORK_ORDER_ID) } returns flowOf(detail)
        val connectivityObserver = mockk<ConnectivityObserver>()
        every { connectivityObserver.isOnline } returns flowOf(isOnline)
        return WorkOrderCompletionViewModel(savedStateHandle(), detailRepository, completionRepository, connectivityObserver)
    }

    @Test
    fun `emits Loading then Empty when the work order does not exist`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = null, completionRepository = completionRepository)

            vm.uiState.test {
                assertEquals(WorkOrderCompletionUiState.Loading, awaitItem())
                assertEquals(WorkOrderCompletionUiState.Empty, awaitItem())
            }
        }

    @Test
    fun `a repair cannot submit until an after photo exists and the note is at least 10 characters`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(loaded.canSubmit())

                vm.onReportNoteChanged("short")
                assertFalse((awaitItem() as WorkOrderCompletionUiState.Success).canSubmit())
            }
        }

    @Test
    fun `a repair can submit once an after photo exists and the note is long enough`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            val evidence =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = WORK_ORDER_ID,
                    kind = "after",
                    filePath = "/data/OP-1.jpg",
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.0,
                    lng = 106.0,
                    uploadStatus = "pending",
                )
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(evidence)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(loaded.canSubmit())

                vm.onReportNoteChanged("Đã thay bóng đèn mới")
                val withNote = awaitItem() as WorkOrderCompletionUiState.Success
                assertTrue(withNote.canSubmit())
            }
        }

    @Test
    fun `an inspection with no linked faults only needs the note, and sends fault_outcomes as null`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = inspectionDetail(emptyList()), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Không phát hiện sự cố nào")
                val withNote = awaitItem() as WorkOrderCompletionUiState.Success
                assertTrue(withNote.canSubmit())

                vm.submit()
                dispatcher.scheduler.advanceUntilIdle()
            }
            coVerify {
                completionRepository.submitCompletion(
                    workOrderId = WORK_ORDER_ID,
                    taskKind = "inspection",
                    reportNote = "Không phát hiện sự cố nào",
                    materialsUsed = null,
                    faultOutcomes = null,
                )
            }
        }

    @Test
    fun `an inspection with linked faults cannot submit until every fault has an outcome`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val faults = listOf(fault("FAULT-1"), fault("FAULT-2"))
            val vm = viewModel(detail = inspectionDetail(faults), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Đã kiểm tra tại hiện trường")
                val withNote = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(withNote.canSubmit())

                vm.onFaultOutcomeSelected("FAULT-1", "fault_present")
                val oneDone = awaitItem() as WorkOrderCompletionUiState.Success
                assertFalse(oneDone.canSubmit())

                vm.onFaultOutcomeSelected("FAULT-2", "fault_absent")
                val bothDone = awaitItem() as WorkOrderCompletionUiState.Success
                assertTrue(bothDone.canSubmit())

                vm.submit()
                dispatcher.scheduler.advanceUntilIdle()
            }
            coVerify {
                completionRepository.submitCompletion(
                    workOrderId = WORK_ORDER_ID,
                    taskKind = "inspection",
                    reportNote = "Đã kiểm tra tại hiện trường",
                    materialsUsed = null,
                    faultOutcomes = listOf(FaultOutcome("FAULT-1", "fault_present"), FaultOutcome("FAULT-2", "fault_absent")),
                )
            }
        }

    @Test
    fun `submit writes through the repository even while offline, never blocking on connectivity`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = inspectionDetail(emptyList()), completionRepository = completionRepository, isOnline = false)

            vm.uiState.test {
                awaitItem()
                awaitItem()
                vm.onReportNoteChanged("Không phát hiện sự cố nào")
                awaitItem()

                vm.submit()
                dispatcher.scheduler.advanceUntilIdle()
            }
            coVerify(exactly = 1) { completionRepository.submitCompletion(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a non-blank reviewNote prefills reportNote and materialsUsed from the previous submission`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val detail =
                repairDetail(reviewNote = "Chưa đủ ảnh sau, bổ sung thêm")
                    .copy(reportNote = "Đã thay bóng đèn", materialsUsed = "1 bóng LED")
            val vm = viewModel(detail = detail, completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertEquals("Đã thay bóng đèn", loaded.reportNote)
                assertEquals("1 bóng LED", loaded.materialsUsed)
                assertEquals("Chưa đủ ảnh sau, bổ sung thêm", loaded.detail.reviewNote)
            }
        }

    @Test
    fun `syncStatus is null until a local completion row exists`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(null)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertNull(loaded.syncStatus())
            }
        }

    @Test
    fun `syncStatus reflects a pending local completion as queued-online or queued-offline`() =
        runTest {
            val completionRepository = mockk<WorkOrderCompletionRepository>()
            every { completionRepository.observeEvidence(WORK_ORDER_ID) } returns flowOf(null)
            val pending =
                LocalWorkOrderCompletionEntity(
                    workOrderId = WORK_ORDER_ID,
                    reportNote = "note",
                    materialsUsed = null,
                    faultOutcomesJson = null,
                    clientOpId = "COMP-1",
                    submitStatus = "pending",
                )
            every { completionRepository.observeCompletion(WORK_ORDER_ID) } returns flowOf(pending)
            val vm = viewModel(detail = repairDetail(), completionRepository = completionRepository, isOnline = false)

            vm.uiState.test {
                awaitItem()
                val loaded = awaitItem() as WorkOrderCompletionUiState.Success
                assertEquals(SyncStatus.QUEUED_OFFLINE, loaded.syncStatus())
            }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.completion.WorkOrderCompletionViewModelTest"`
Expected: FAIL — types unresolved.

- [ ] **Step 3: Create `WorkOrderCompletionUiState.kt`**

```kotlin
package com.luxmap.feature.workorder.ui.completion

import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.workorder.data.WorkOrderDetail
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity

sealed interface WorkOrderCompletionUiState {
    data object Loading : WorkOrderCompletionUiState

    data class Success(
        val detail: WorkOrderDetail,
        val localEvidence: LocalWorkOrderEvidenceEntity?,
        val localCompletion: LocalWorkOrderCompletionEntity?,
        val isOnline: Boolean,
        val reportNote: String,
        val materialsUsed: String,
        val faultOutcomes: Map<String, String>,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
    ) : WorkOrderCompletionUiState

    data object Empty : WorkOrderCompletionUiState

    data class Error(val message: String) : WorkOrderCompletionUiState
}

fun WorkOrderCompletionUiState.Success.canSubmit(): Boolean {
    val noteValid = reportNote.trim().length >= 10
    return when (detail.taskKind) {
        "repair" -> noteValid && localEvidence != null
        "inspection" -> noteValid && detail.faults.all { faultOutcomes.containsKey(it.faultId) }
        else -> false
    }
}

fun WorkOrderCompletionUiState.Success.syncStatus(): SyncStatus? =
    when (localCompletion?.submitStatus) {
        null -> null
        "pending" -> if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE
        "synced" -> SyncStatus.DONE
        "failed" -> SyncStatus.FAILED
        "conflict" -> SyncStatus.CONFLICT
        else -> null
    }
```

- [ ] **Step 4: Create `WorkOrderCompletionViewModel.kt`**

```kotlin
package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkOrderCompletionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val workOrderDetailRepository: WorkOrderDetailRepository,
        private val completionRepository: WorkOrderCompletionRepository,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderCompletionUiState>(WorkOrderCompletionUiState.Loading)
        val uiState: StateFlow<WorkOrderCompletionUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                combine(
                    workOrderDetailRepository.observeWorkOrderDetail(workOrderId),
                    completionRepository.observeEvidence(workOrderId),
                    completionRepository.observeCompletion(workOrderId),
                    connectivityObserver.isOnline,
                ) { detail, evidence, completion, isOnline -> Quad(detail, evidence, completion, isOnline) }
                    .catch { e ->
                        _uiState.value = WorkOrderCompletionUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                    }.collect { (detail, evidence, completion, isOnline) ->
                        _uiState.value =
                            if (detail == null) {
                                WorkOrderCompletionUiState.Empty
                            } else {
                                val current = _uiState.value as? WorkOrderCompletionUiState.Success
                                WorkOrderCompletionUiState.Success(
                                    detail = detail,
                                    localEvidence = evidence,
                                    localCompletion = completion,
                                    isOnline = isOnline,
                                    reportNote = current?.reportNote ?: detail.reportNote.orEmpty(),
                                    materialsUsed = current?.materialsUsed ?: detail.materialsUsed.orEmpty(),
                                    faultOutcomes = current?.faultOutcomes ?: emptyMap(),
                                )
                            }
                    }
            }
        }

        fun onReportNoteChanged(value: String) {
            updateSuccess { it.copy(reportNote = value) }
        }

        fun onMaterialsUsedChanged(value: String) {
            updateSuccess { it.copy(materialsUsed = value) }
        }

        fun onFaultOutcomeSelected(
            faultId: String,
            outcome: String,
        ) {
            updateSuccess { it.copy(faultOutcomes = it.faultOutcomes + (faultId to outcome)) }
        }

        fun submit() {
            val current = _uiState.value
            if (current !is WorkOrderCompletionUiState.Success || !current.canSubmit() || current.isSubmitting) return
            _uiState.value = current.copy(isSubmitting = true, submitError = null)
            viewModelScope.launch {
                val outcomes =
                    if (current.detail.taskKind == "inspection" && current.detail.faults.isNotEmpty()) {
                        current.detail.faults.map { FaultOutcome(it.faultId, current.faultOutcomes[it.faultId].orEmpty()) }
                    } else {
                        null
                    }
                completionRepository.submitCompletion(
                    workOrderId = workOrderId,
                    taskKind = current.detail.taskKind,
                    reportNote = current.reportNote.trim(),
                    materialsUsed = current.materialsUsed.trim().ifBlank { null },
                    faultOutcomes = outcomes,
                )
                val afterSubmit = _uiState.value
                if (afterSubmit is WorkOrderCompletionUiState.Success) {
                    _uiState.value = afterSubmit.copy(isSubmitting = false)
                }
            }
        }

        private fun updateSuccess(transform: (WorkOrderCompletionUiState.Success) -> WorkOrderCompletionUiState.Success) {
            val current = _uiState.value
            if (current is WorkOrderCompletionUiState.Success) {
                _uiState.value = transform(current)
            }
        }

        private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.completion.WorkOrderCompletionViewModelTest"`
Expected: PASS

- [ ] **Step 6: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionUiState.kt app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionViewModel.kt app/src/test/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add workordercompletionviewmodel

submit always writes through the repository regardless of
connectivity.isOnline - offline never blocks the write, only the
later sync does
EOF
)"
```

---

## Task 10: `WorkOrderCompletionScreen`, `SyncStatus.label()`, navigation, "Hoàn thành" button

**Files:**
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt`
- Modify: `app/src/main/java/com/luxmap/core/theme/Color.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt`
- Test: `app/src/test/java/com/luxmap/core/theme/SyncStatusLabelTest.kt`

**Interfaces:**
- Consumes: `WorkOrderCompletionViewModel` (Task 9), `inspectionOutcomeLabel()` (existing, `feature/workorder/ui/detail/WorkOrderFaultLabels.kt`), `ErrorBanner`/`PrimaryButton`/`StatusBadge` (existing `core/ui/components`).
- Produces: `Routes.WorkOrderCompletion` (`"work-order/{workOrderId}/complete"`); `WorkOrderDetailScreen` gains an `onComplete: () -> Unit` parameter.

This task has no automated UI test — matches the spec's own testing plan ("no Compose UI tests ... screens get a real-device check instead") and this codebase's existing convention of not unit-testing screen composables beyond the few already covered by `androidTest`.

- [ ] **Step 1: Write the failing `SyncStatus.label()` test**

```kotlin
package com.luxmap.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusLabelTest {
    @Test
    fun `every SyncStatus has the exact label from CLAUDE_md's sync badge table`() {
        assertEquals("Chờ mạng", SyncStatus.QUEUED_OFFLINE.label())
        assertEquals("Chờ đồng bộ", SyncStatus.QUEUED_ONLINE.label())
        assertEquals("Đang đồng bộ", SyncStatus.SYNCING.label())
        assertEquals("Đồng bộ lỗi", SyncStatus.FAILED.label())
        assertEquals("Xung đột", SyncStatus.CONFLICT.label())
        assertEquals("Đã đồng bộ", SyncStatus.DONE.label())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.SyncStatusLabelTest"`
Expected: FAIL — `label()` unresolved.

- [ ] **Step 3: Add `SyncStatus.label()` to `Color.kt`** (directly below the existing `SyncStatus.badgeColors()`)

```kotlin
fun SyncStatus.label(): String =
    when (this) {
        SyncStatus.QUEUED_OFFLINE -> "Chờ mạng"
        SyncStatus.QUEUED_ONLINE -> "Chờ đồng bộ"
        SyncStatus.SYNCING -> "Đang đồng bộ"
        SyncStatus.FAILED -> "Đồng bộ lỗi"
        SyncStatus.CONFLICT -> "Xung đột"
        SyncStatus.DONE -> "Đã đồng bộ"
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.core.theme.SyncStatusLabelTest"`
Expected: PASS

- [ ] **Step 5: Add the route**

In `Routes.kt`, after `WorkOrderDetail`:

```kotlin
    data object WorkOrderCompletion : Routes {
        override val route = "work-order/{workOrderId}/complete"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId/complete"
    }
```

- [ ] **Step 6: Add the "Hoàn thành" button to `WorkOrderDetailScreen`**

In `WorkOrderDetailScreen.kt`: add `onComplete: () -> Unit` to `WorkOrderDetailRoute`'s parameters, pass `onComplete = onComplete` down to `WorkOrderDetailScreen`'s new `onComplete: () -> Unit` parameter, thread it into `WorkOrderDetailContent`, and in `WorkOrderDetailContent`, right after the existing `if ("start" in detail.allowedActions) { ... }` block, add:

```kotlin
        if ("complete" in detail.allowedActions) {
            PrimaryButton(
                text = "Hoàn thành",
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
```

(every existing preview call site (`WorkOrderDetailScreenRepairPreview`, etc.) needs `onComplete = {}` added to its `WorkOrderDetailScreen(...)` call)

- [ ] **Step 7: Wire the route and the "Hoàn thành" button into `NavGraph`**

In `NavGraph.kt`, update the existing `Routes.WorkOrderDetail` composable block to pass `onComplete`, and add a new composable block for `Routes.WorkOrderCompletion`:

```kotlin
            composable(
                route = Routes.WorkOrderDetail.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable
                WorkOrderDetailRoute(
                    onBack = { navController.popBackStack() },
                    onComplete = { navController.navigate(Routes.WorkOrderCompletion.createRoute(workOrderId)) },
                )
            }
            composable(
                route = Routes.WorkOrderCompletion.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) {
                WorkOrderCompletionRoute(onBack = { navController.popBackStack() })
            }
```

Note for Task 11: this `Routes.WorkOrderCompletion` block does not capture `backStackEntry`, so `workOrderId` is not in scope here yet. Task 11 Step 9 (navigating to `Routes.WorkOrderEvidenceCapture`) must change this block's lambda to `{ backStackEntry -> val workOrderId = backStackEntry.arguments?.getString("workOrderId") ?: return@composable ... }`, the same pattern the `Routes.WorkOrderDetail` block above already uses, before it can build that route.

(add import `com.luxmap.feature.workorder.ui.completion.WorkOrderCompletionRoute`; note the existing `WorkOrderDetail` composable block did not previously read `backStackEntry.arguments` — it now needs to, to build the completion route)

- [ ] **Step 8: Create `WorkOrderCompletionScreen.kt`**

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.ErrorBanner
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.ui.detail.inspectionOutcomeLabel

@Composable
fun WorkOrderCompletionRoute(
    onBack: () -> Unit,
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
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkOrderCompletionScreen(
    uiState: WorkOrderCompletionUiState,
    onBack: () -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Hoàn thành lệnh") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
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
                    )
                is WorkOrderCompletionUiState.Empty -> MessageState("Không tìm thấy lệnh này")
                is WorkOrderCompletionUiState.Error -> MessageState(uiState.message)
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
            RepairCompletionSection(state = state, onReportNoteChanged = onReportNoteChanged, onMaterialsUsedChanged = onMaterialsUsedChanged)
        } else {
            InspectionCompletionSection(state = state, onReportNoteChanged = onReportNoteChanged, onFaultOutcomeSelected = onFaultOutcomeSelected)
        }
        if (state.submitError != null) {
            ErrorBanner(message = state.submitError)
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = if (state.localEvidence != null) "Đã chụp ảnh sau" else "Chưa có ảnh sau",
            style = MaterialTheme.typography.bodyMedium,
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
    }
}

@Composable
private fun InspectionCompletionSection(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        state.detail.faults.forEach { fault ->
            FaultOutcomeRow(fault = fault, selected = state.faultOutcomes[fault.faultId], onSelected = onFaultOutcomeSelected)
        }
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Ghi chú kiểm tra") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FaultOutcomeRow(
    fault: WorkOrderFaultDetail,
    selected: String?,
    onSelected: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = fault.poleId ?: fault.faultId, style = MaterialTheme.typography.titleMedium)
        listOf("fault_present", "fault_absent", "inconclusive").forEach { outcome ->
            PrimaryButton(
                text = inspectionOutcomeLabel(outcome) + if (selected == outcome) " ✓" else "",
                onClick = { onSelected(fault.faultId, outcome) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun BoxScope.MessageState(text: String) {
    Text(text = text, modifier = Modifier.align(Alignment.Center).padding(Spacing.lg))
}
```

(`NavHostController` import is unused if no preview needs it — drop it if ktlint flags it as unused)

- [ ] **Step 9: Build the app and manually verify on a device/emulator**

Run: `./gradlew :app:assembleDebug`

Real-device check: open a repair work order with `"complete"` in `allowed_actions`, confirm "Nộp" stays disabled until an after photo exists (Task 11 wires the actual capture — until then, this task can only be checked up to "button present, correctly disabled"); open an inspection work order and confirm the per-fault picker and note field render and gate "Nộp" correctly.

- [ ] **Step 10: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt app/src/main/java/com/luxmap/core/theme/Color.kt app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/main/java/com/luxmap/feature/workorder/ui/detail/WorkOrderDetailScreen.kt app/src/test/java/com/luxmap/core/theme/SyncStatusLabelTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add workordercompletionscreen and wire it into navigation
EOF
)"
```

---

## Task 11: Evidence capture — system Camera app, FileProvider, Fused Location

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/EvidenceCaptureViewModel.kt`
- Create: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/EvidenceCaptureScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/luxmap/navigation/Routes.kt`
- Modify: `app/src/main/java/com/luxmap/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt`
- Test: `app/src/test/java/com/luxmap/feature/workorder/ui/completion/EvidenceCaptureViewModelTest.kt`

**Interfaces:**
- Consumes: `WorkOrderCompletionRepository.captureAfterEvidence` (Task 6), `LocationTracker` (existing).
- Produces: `Routes.WorkOrderEvidenceCapture` (`"work-order/{workOrderId}/complete/evidence"`); "Chụp ảnh sau" button on the Repair branch of `WorkOrderCompletionScreen`.

- [ ] **Step 1: Write the failing `EvidenceCaptureViewModel` test** (the piece worth unit-testing: output-file naming and the capture call it makes — the camera intent/permission flow itself is UI glue, verified on a real device per Step 9)

```kotlin
package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.io.File

private const val WORK_ORDER_ID = "WO-1"

private fun savedStateHandle() =
    SavedStateHandle(mapOf(EvidenceCaptureViewModel.WORK_ORDER_ID_ARG to WORK_ORDER_ID))

@OptIn(ExperimentalCoroutinesApi::class)
class EvidenceCaptureViewModelTest {
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
    fun `outputFile is named after the viewModel's own clientOpId, under the work order's evidence folder`() {
        val repository = mockk<WorkOrderCompletionRepository>()
        val locationTracker = mockk<LocationTracker>()
        val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
        val baseDir = File.createTempFile("evidence-test", "").apply { delete(); mkdirs() }

        val file = vm.outputFile(baseDir)

        assertEquals("evidence/$WORK_ORDER_ID/${vm.clientOpId}.jpg", file.relativeTo(baseDir).path.replace("\\", "/"))
    }

    @Test
    fun `onPhotoCaptured writes evidence through the repository using the current location, then calls onDone`() =
        runTest {
            val repository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            every { locationTracker.hasLocationPermission() } returns true
            io.mockk.coEvery { locationTracker.getCurrentLocation() } returns LatLng(10.97, 106.49)
            val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
            var doneCalled = false
            val file = File("/data/evidence/${vm.clientOpId}.jpg")

            vm.onPhotoCaptured(file) { doneCalled = true }
            dispatcher.scheduler.advanceUntilIdle()

            coVerify {
                repository.captureAfterEvidence(
                    workOrderId = WORK_ORDER_ID,
                    clientOpId = vm.clientOpId,
                    filePath = file.absolutePath,
                    lat = 10.97,
                    lng = 106.49,
                    capturedAt = any(),
                )
            }
            org.junit.Assert.assertTrue(doneCalled)
        }

    @Test
    fun `onPhotoCaptured falls back to 0,0 when no location is available, rather than failing the capture`() =
        runTest {
            val repository = mockk<WorkOrderCompletionRepository>(relaxed = true)
            val locationTracker = mockk<LocationTracker>()
            every { locationTracker.hasLocationPermission() } returns false
            io.mockk.coEvery { locationTracker.getCurrentLocation() } returns null
            val vm = EvidenceCaptureViewModel(savedStateHandle(), repository, locationTracker)
            val file = File("/data/evidence/${vm.clientOpId}.jpg")

            vm.onPhotoCaptured(file) {}
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { repository.captureAfterEvidence(workOrderId = WORK_ORDER_ID, clientOpId = vm.clientOpId, filePath = file.absolutePath, lat = 0.0, lng = 0.0, capturedAt = any()) }
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.completion.EvidenceCaptureViewModelTest"`
Expected: FAIL — `EvidenceCaptureViewModel` unresolved.

- [ ] **Step 3: Add the FileProvider manifest entry and `file_paths.xml`**

`app/src/main/res/xml/file_paths.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <files-path name="evidence" path="evidence/" />
</paths>
```

In `AndroidManifest.xml`, inside `<application>`, alongside the `SurveyCaptureService` and the WorkManager-initializer `<provider>` added in Task 3:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 4: Create `EvidenceCaptureViewModel.kt`**

```kotlin
package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.location.LocationTracker
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EvidenceCaptureViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: WorkOrderCompletionRepository,
        private val locationTracker: LocationTracker,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])
        val clientOpId: String = UUID.randomUUID().toString()

        fun outputFile(filesDir: File): File {
            val dir = File(filesDir, "evidence/$workOrderId")
            dir.mkdirs()
            return File(dir, "$clientOpId.jpg")
        }

        fun onPhotoCaptured(
            file: File,
            onDone: () -> Unit,
        ) {
            viewModelScope.launch {
                val location = locationTracker.getCurrentLocation()
                repository.captureAfterEvidence(
                    workOrderId = workOrderId,
                    clientOpId = clientOpId,
                    filePath = file.absolutePath,
                    lat = location?.latitude ?: 0.0,
                    lng = location?.longitude ?: 0.0,
                    capturedAt = Instant.now(),
                )
                onDone()
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.ui.completion.EvidenceCaptureViewModelTest"`
Expected: PASS

- [ ] **Step 6: Create `EvidenceCaptureScreen.kt`**

```kotlin
package com.luxmap.feature.workorder.ui.completion

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@Composable
fun EvidenceCaptureRoute(
    onDone: () -> Unit,
    viewModel: EvidenceCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var hasRequestedLocationPermission by rememberSaveable { mutableStateOf(false) }

    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingFile
            if (success && file != null) {
                viewModel.onPhotoCaptured(file, onDone)
            } else {
                onDone()
            }
        }

    fun launchCamera() {
        val file = viewModel.outputFile(context.filesDir)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { launchCamera() }

    LaunchedEffect(Unit) {
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (granted || hasRequestedLocationPermission) {
            launchCamera()
        } else {
            hasRequestedLocationPermission = true
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
```

- [ ] **Step 7: Add the route**

In `Routes.kt`, after `WorkOrderCompletion`:

```kotlin
    data object WorkOrderEvidenceCapture : Routes {
        override val route = "work-order/{workOrderId}/complete/evidence"

        fun createRoute(workOrderId: String) = "work-order/$workOrderId/complete/evidence"
    }
```

- [ ] **Step 8: Wire the route into `NavGraph`**

```kotlin
            composable(
                route = Routes.WorkOrderEvidenceCapture.route,
                arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
            ) {
                EvidenceCaptureRoute(onDone = { navController.popBackStack() })
            }
```

(add import `com.luxmap.feature.workorder.ui.completion.EvidenceCaptureRoute`)

- [ ] **Step 9: Add the "Chụp ảnh sau" button to `RepairCompletionSection`**

In `WorkOrderCompletionScreen.kt`: thread a new `onCaptureEvidence: () -> Unit` parameter from `WorkOrderCompletionRoute` (where it calls `navController`... actually the route composable doesn't own the controller — thread it as a param from `NavGraph`'s call site instead, same as `onBack`) down through `WorkOrderCompletionScreen` → `WorkOrderCompletionContent` → `RepairCompletionSection`, and when `state.localEvidence == null`, show:

```kotlin
        if (state.localEvidence == null) {
            PrimaryButton(text = "Chụp ảnh sau", onClick = onCaptureEvidence, modifier = Modifier.fillMaxWidth())
        } else {
            Text(text = "Đã chụp ảnh sau", style = MaterialTheme.typography.bodyMedium)
        }
```

(replacing the plain `Text` that previously always showed "Đã chụp ảnh sau"/"Chưa có ảnh sau"); in `NavGraph.kt`'s `Routes.WorkOrderCompletion` block, pass `onCaptureEvidence = { navController.navigate(Routes.WorkOrderEvidenceCapture.createRoute(workOrderId)) }` into `WorkOrderCompletionRoute`.

- [ ] **Step 10: Run the full unit test suite, build, and manually verify on a device/emulator**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Real-device check: from a repair work order's completion screen, tap "Chụp ảnh sau", grant/deny the location permission prompt, take a photo in the system Camera app, confirm it returns to the completion screen showing "Đã chụp ảnh sau" and "Nộp" becomes enabled once the note is long enough; submit while in airplane mode and confirm the sync badge shows "Chờ mạng", then re-enable network and confirm it moves to "Đã đồng bộ" once `SyncWorker` runs.

- [ ] **Step 11: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/res/xml/file_paths.xml app/src/main/java/com/luxmap/feature/workorder/ui/completion/EvidenceCaptureViewModel.kt app/src/main/java/com/luxmap/feature/workorder/ui/completion/EvidenceCaptureScreen.kt app/src/main/AndroidManifest.xml app/src/main/java/com/luxmap/navigation/Routes.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt app/src/test/java/com/luxmap/feature/workorder/ui/completion/EvidenceCaptureViewModelTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add after-photo capture through the system camera app

delegates to the os camera via ActivityResultContracts.TakePicture(),
same "delegate instead of build" choice f09 made for navigation - no
camerax, this app has none
EOF
)"
```

---

## Task 12: Retake the after photo

**Files:**
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDao.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderCompletionRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepository.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionViewModel.kt`
- Modify: `app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt`
- Modify: `app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepositoryTest.kt`

**Interfaces:**
- Produces: `WorkOrderCompletionRepository.retakeEvidence(workOrderId: String)`; `WorkOrderCompletionViewModel.retakeEvidence(onReady: () -> Unit)`.

Note: `NavGraph.kt` is not touched by this task — `onRetakeEvidence` is wired entirely inside `WorkOrderCompletionScreen.kt`'s `WorkOrderCompletionRoute` by reusing the `onCaptureEvidence` callback Task 11 already threaded in from `NavGraph`; no new navigation call site is needed.

This closes the spec's error-handling table rows "Retry of a not-yet-synced evidence capture" (delete the local row + file, capture again) and "Retry of an already-synced evidence capture" (capture a new row, old one stays — the server allows multiple `after` photos).

- [ ] **Step 1: Write the failing repository test (append to `RealWorkOrderCompletionRepositoryTest.kt`)**

```kotlin
    @Test
    fun `retakeEvidence deletes the local row and file when the previous capture has not synced yet`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val tempFile = java.io.File.createTempFile("evidence", ".jpg")
            val pending =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = tempFile.absolutePath,
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "pending",
                )
            io.mockk.coEvery { dao.latestEvidenceClientOpId("WO-1") } returns "OP-1"
            io.mockk.coEvery { dao.evidenceByClientOpId("OP-1") } returns pending
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager)

            repository.retakeEvidence("WO-1")

            coVerify { dao.deleteEvidence("OP-1") }
            assertFalse(tempFile.exists())
        }

    @Test
    fun `retakeEvidence leaves the local row and file alone once the previous capture has synced`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val synced =
                LocalWorkOrderEvidenceEntity(
                    clientOpId = "OP-1",
                    workOrderId = "WO-1",
                    kind = "after",
                    filePath = "/data/OP-1.jpg",
                    capturedAt = Instant.parse("2026-10-06T10:00:00Z"),
                    lat = 10.97,
                    lng = 106.49,
                    uploadStatus = "synced",
                )
            io.mockk.coEvery { dao.latestEvidenceClientOpId("WO-1") } returns "OP-1"
            io.mockk.coEvery { dao.evidenceByClientOpId("OP-1") } returns synced
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager)

            repository.retakeEvidence("WO-1")

            coVerify(exactly = 0) { dao.deleteEvidence(any()) }
        }
```

(add `import org.junit.Assert.assertFalse` to the test file's imports)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`
Expected: FAIL — `retakeEvidence`/`deleteEvidence` unresolved.

- [ ] **Step 3: Add `deleteEvidence` to `WorkOrderCompletionDao`**

```kotlin
    @Query("DELETE FROM local_work_order_evidence WHERE clientOpId = :clientOpId")
    suspend fun deleteEvidence(clientOpId: String)
```

- [ ] **Step 4: Add `retakeEvidence` to the repository interface**

```kotlin
    suspend fun retakeEvidence(workOrderId: String)
```

- [ ] **Step 5: Implement it in `RealWorkOrderCompletionRepository`**

```kotlin
        override suspend fun retakeEvidence(workOrderId: String) {
            val clientOpId = dao.latestEvidenceClientOpId(workOrderId) ?: return
            val evidence = dao.evidenceByClientOpId(clientOpId) ?: return
            if (evidence.uploadStatus == "pending") {
                File(evidence.filePath).delete()
                dao.deleteEvidence(clientOpId)
            }
        }
```

(add `import java.io.File`)

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.luxmap.feature.workorder.data.RealWorkOrderCompletionRepositoryTest"`
Expected: PASS

- [ ] **Step 7: Add `retakeEvidence` to `WorkOrderCompletionViewModel`**

```kotlin
        fun retakeEvidence(onReady: () -> Unit) {
            viewModelScope.launch {
                completionRepository.retakeEvidence(workOrderId)
                onReady()
            }
        }
```

- [ ] **Step 8: Replace the static "Đã chụp ảnh sau" text with a "Chụp lại" button**

In `WorkOrderCompletionScreen.kt`, thread a new `onRetakeEvidence: () -> Unit` parameter from `WorkOrderCompletionRoute` down through `WorkOrderCompletionScreen` → `WorkOrderCompletionContent` → `RepairCompletionSection`, and change `RepairCompletionSection`'s body to:

```kotlin
        if (state.localEvidence == null) {
            PrimaryButton(text = "Chụp ảnh sau", onClick = onCaptureEvidence, modifier = Modifier.fillMaxWidth())
        } else {
            Text(text = "Đã chụp ảnh sau", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(text = "Chụp lại", onClick = onRetakeEvidence, modifier = Modifier.fillMaxWidth())
        }
```

In `WorkOrderCompletionRoute`, wire it as:

```kotlin
        onRetakeEvidence = { viewModel.retakeEvidence(onCaptureEvidence) },
```

(so a retake always deletes the stale pending row first, if there is one, then opens the camera the same way a first capture does — `onCaptureEvidence` is the same navigate-to-`WorkOrderEvidenceCapture` callback Task 11 already added)

- [ ] **Step 9: Build and manually verify on a device/emulator**

Run: `./gradlew :app:assembleDebug`

Real-device check: capture an after photo, confirm "Chụp lại" appears; tap it before the sync badge shows "Đã đồng bộ" and confirm the old file is gone and the camera reopens; capture again, wait for sync to finish, tap "Chụp lại" again and confirm this time the old (now-synced) photo is left alone while a new one is captured.

- [ ] **Step 10: Run ktlint and commit**

```bash
./gradlew ktlintCheck
git add app/src/main/java/com/luxmap/feature/workorder/data/dao/WorkOrderCompletionDao.kt app/src/main/java/com/luxmap/feature/workorder/data/WorkOrderCompletionRepository.kt app/src/main/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepository.kt app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionViewModel.kt app/src/main/java/com/luxmap/feature/workorder/ui/completion/WorkOrderCompletionScreen.kt app/src/main/java/com/luxmap/navigation/NavGraph.kt app/src/test/java/com/luxmap/feature/workorder/data/RealWorkOrderCompletionRepositoryTest.kt
git commit -m "$(cat <<'EOF'
feat(fm-10): add retake for the after photo

deletes the stale local row and file only when the previous capture
has not synced yet - once synced, the server already allows more than
one after photo, so retaking just adds a new one
EOF
)"
```
