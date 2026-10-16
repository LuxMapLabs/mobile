package com.luxmap.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.luxmap.core.database.AppDatabase
import kotlinx.coroutines.flow.first
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

    @Test
    fun observeOpsForSessionOnlyReturnsRowsForThatSession() =
        runTest {
            dao.insert(row("SESSION-1:clip:0"))
            dao.insert(row("SESSION-2:clip:0"))

            val rows = dao.observeOpsForSession("SESSION-1:%").first()

            assertEquals(1, rows.size)
            assertEquals("SESSION-1:clip:0", rows.first().clientOpId)
        }

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

    @Test
    fun observeStatusesForOnlyReturnsRowsMatchingTheGivenIds() =
        runTest {
            dao.insert(row("OP-1", status = "done"))
            dao.insert(row("OP-2", status = "failed"))
            dao.insert(row("OP-3", status = "queued"))

            val rows = dao.observeStatusesFor(listOf("OP-1", "OP-2")).first()

            assertEquals(setOf("OP-1", "OP-2"), rows.map { it.clientOpId }.toSet())
        }
}
