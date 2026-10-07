package com.luxmap.feature.workorder.data

import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

            assertEquals("OP-1", repository.observeEvidence("WO-1").first()?.clientOpId)
            assertEquals("COMP-1", repository.observeCompletion("WO-1").first()?.clientOpId)
        }
}
