package com.luxmap.core.sync

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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
            coVerify(exactly = 1) { trigger.triggerNow() }
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
}
