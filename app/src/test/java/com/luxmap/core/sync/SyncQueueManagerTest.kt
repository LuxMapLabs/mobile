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
