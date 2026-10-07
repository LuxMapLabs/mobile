package com.luxmap.core.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            coEvery { dao.queuedRows() } returns
                listOf(row(2, "OP-2", opType = "complete_work_order", dependsOnClientOpId = "OP-1"))
            coEvery { dao.statusOf("OP-1") } returns "queued"
            val processor = SyncQueueProcessor(dao, setOf(handler("complete_work_order", SyncOpResult.Done)))

            processor.processQueuedOps()

            coVerify(exactly = 0) { dao.updateStatus(2, any(), any(), any(), any()) }
        }

    @Test
    fun `a row whose dependency permanently failed is also marked failed, not left queued forever`() =
        runTest {
            val dao = mockk<SyncQueueDao>(relaxed = true)
            coEvery { dao.queuedRows() } returns
                listOf(row(2, "OP-2", opType = "complete_work_order", dependsOnClientOpId = "OP-1"))
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
            coVerify {
                dao.updateStatus(1, "failed", SyncQueueProcessor.MAX_ATTEMPTS, "Exceeded retry attempts", any())
            }
        }
}
