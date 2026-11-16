package com.luxmap.feature.workorder.data

import app.cash.turbine.test
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

private fun syncCompletion(
    workOrderId: String,
    clientOpId: String,
    submitStatus: String,
) = LocalWorkOrderCompletionEntity(
    workOrderId = workOrderId,
    reportNote = "note",
    materialsUsed = null,
    faultOutcomesJson = null,
    clientOpId = clientOpId,
    submitStatus = submitStatus,
)

private fun syncOp(
    clientOpId: String,
    status: String,
) = SyncQueueEntity(
    clientOpId = clientOpId,
    opType = "complete_work_order",
    payloadJson = "{}",
    dependsOnClientOpId = null,
    status = status,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

class RealWorkOrderCompletionRepositoryTest {
    @Test
    fun `sync states map queued, failed, conflict and a missing op, and leave out synced rows`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>()
            val syncQueueDao = mockk<SyncQueueDao>()
            every { dao.observeAllCompletions() } returns
                flowOf(
                    listOf(
                        syncCompletion("WO-Q", "OP-Q", "pending"),
                        syncCompletion("WO-F", "OP-F", "pending"),
                        syncCompletion("WO-C", "OP-C", "pending"),
                        syncCompletion("WO-M", "OP-M", "pending"),
                        syncCompletion("WO-S", "OP-S", "synced"),
                    ),
                )
            every { syncQueueDao.observeStatusesFor(listOf("OP-Q", "OP-F", "OP-C", "OP-M")) } returns
                flowOf(listOf(syncOp("OP-Q", "queued"), syncOp("OP-F", "failed"), syncOp("OP-C", "conflict")))
            val repository = RealWorkOrderCompletionRepository(dao, mockk(relaxed = true), syncQueueDao)

            assertEquals(
                mapOf(
                    "WO-Q" to CompletionSyncState.PENDING,
                    "WO-F" to CompletionSyncState.FAILED,
                    "WO-C" to CompletionSyncState.CONFLICT,
                    "WO-M" to CompletionSyncState.PENDING,
                ),
                repository.observeCompletionSyncStates().first(),
            )
        }

    @Test
    fun `a completion moving from pending to synced leaves the sync state map`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>()
            val syncQueueDao = mockk<SyncQueueDao>()
            val completions = MutableStateFlow(listOf(syncCompletion("WO-1", "OP-1", "pending")))
            every { dao.observeAllCompletions() } returns completions
            every { syncQueueDao.observeStatusesFor(listOf("OP-1")) } returns flowOf(listOf(syncOp("OP-1", "syncing")))
            val repository = RealWorkOrderCompletionRepository(dao, mockk(relaxed = true), syncQueueDao)

            repository.observeCompletionSyncStates().test {
                assertEquals(mapOf("WO-1" to CompletionSyncState.PENDING), awaitItem())
                completions.value = listOf(syncCompletion("WO-1", "OP-1", "synced"))
                assertEquals(emptyMap<String, CompletionSyncState>(), awaitItem())
            }
        }

    @Test
    fun `captureEvidence saves one draft per image without starting an upload`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueue = mockk<SyncQueueManager>(relaxed = true)
            val repository = RealWorkOrderCompletionRepository(dao, syncQueue, mockk(relaxed = true))
            val capturedAt = Instant.parse("2026-11-16T08:00:00Z")

            repository.captureEvidence("WO-1", EvidenceKind.AFTER, "photo-a", "/tmp/a.jpg", 10.0, 106.0, capturedAt)
            repository.captureEvidence(
                workOrderId = "WO-2",
                kind = EvidenceKind.OBSERVATION,
                clientOpId = "photo-b",
                filePath = "/tmp/b.jpg",
                lat = 10.0,
                lng = 106.0,
                capturedAt = capturedAt,
            )

            coVerify(exactly = 1) {
                dao.insertDraftEvidence(
                    match { it.clientOpId == "photo-a" && it.kind == "after" && it.uploadStatus == "draft" },
                )
            }
            coVerify(exactly = 1) {
                dao.insertDraftEvidence(
                    match { it.clientOpId == "photo-b" && it.kind == "observation" && it.uploadStatus == "draft" },
                )
            }
            coVerify(exactly = 0) { syncQueue.enqueue(any(), any(), any(), any()) }
        }

    @Test
    fun `captureAfterEvidence writes the local row then enqueues an upload op with no dependency`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager, mockk(relaxed = true))

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
    fun `submitCompletion for a repair stages all after photos before triggering sync`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            coEvery { dao.stageEvidenceCompletion(any(), "after") } returns true
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager, mockk(relaxed = true))

            repository.submitCompletion(
                workOrderId = "WO-1",
                taskKind = "repair",
                reportNote = "Đã thay bóng đèn",
                materialsUsed = null,
                faultOutcomes = null,
            )

            coVerify {
                dao.stageEvidenceCompletion(match { it.workOrderId == "WO-1" }, "after")
                syncQueueManager.triggerSync()
            }
        }

    @Test
    fun `submitCompletion for an inspection stages observation photos and keeps fault outcomes`() =
        runTest {
            val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            coEvery { dao.stageEvidenceCompletion(any(), "observation") } returns true
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager, mockk(relaxed = true))

            repository.submitCompletion(
                workOrderId = "WO-2",
                taskKind = "inspection",
                reportNote = "Không phát hiện sự cố",
                materialsUsed = null,
                faultOutcomes = listOf(FaultOutcome("FAULT-1", "fault_absent")),
            )

            coVerify {
                dao.stageEvidenceCompletion(
                    match { it.workOrderId == "WO-2" && it.faultOutcomesJson?.contains("FAULT-1") == true },
                    "observation",
                )
                syncQueueManager.triggerSync()
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
            val repository = RealWorkOrderCompletionRepository(dao, mockk(relaxed = true), mockk(relaxed = true))

            assertEquals("OP-1", repository.observeEvidence("WO-1").first()?.clientOpId)
            assertEquals("COMP-1", repository.observeCompletion("WO-1").first()?.clientOpId)
        }

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
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager, mockk(relaxed = true))

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
            val repository = RealWorkOrderCompletionRepository(dao, syncQueueManager, mockk(relaxed = true))

            repository.retakeEvidence("WO-1")

            coVerify(exactly = 0) { dao.deleteEvidence(any()) }
        }
}
