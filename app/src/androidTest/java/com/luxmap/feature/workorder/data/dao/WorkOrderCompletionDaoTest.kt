package com.luxmap.feature.workorder.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SYNC_ATTEMPTS_EXHAUSTED
import com.luxmap.core.sync.SyncQueueProcessor
import com.luxmap.feature.workorder.data.dto.EvidenceItemDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.CompleteWorkOrderSyncHandler
import com.luxmap.feature.workorder.data.sync.CompletionSyncPayload
import com.luxmap.feature.workorder.data.sync.EVIDENCE_REPLACEMENT_REQUIRED
import com.luxmap.feature.workorder.data.sync.UploadWorkOrderEvidenceSyncHandler
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
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
    fun observeEvidenceListReturnsOnlyTheOrdersPhotosInStableNewestFirstOrder() =
        runTest {
            dao.insertEvidence(evidence("A"))
            dao.insertEvidence(evidence("B"))
            dao.insertEvidence(evidence("OLD", capturedAt = Instant.parse("2026-10-06T09:00:00Z")))
            dao.insertEvidence(evidence("OTHER", workOrderId = "WO-2"))

            dao.observeEvidenceList("WO-1").test {
                assertEquals(listOf("B", "A", "OLD"), awaitItem().map { it.clientOpId })
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
    fun stagingCompletionEnqueuesAllDraftsAndKeepsLegacyUploads() =
        runTest {
            dao.insertEvidence(evidence("existing").copy(uploadStatus = "synced"))
            dao.insertDraftEvidence(evidence("new-a").copy(uploadStatus = "draft"))
            dao.insertDraftEvidence(evidence("new-b").copy(uploadStatus = "draft"))
            dao.insertDraftEvidence(evidence("other-kind").copy(kind = "observation", uploadStatus = "draft"))

            assertTrue(dao.stageEvidenceCompletion(completion(), "after"))
            assertFalse(dao.stageEvidenceCompletion(completion(clientOpId = "COMP-2"), "after"))

            val queued = database.syncQueueDao().queuedRows()
            assertEquals(listOf("new-a", "new-b", "COMP-1"), queued.map { it.clientOpId })
            val payload = Json.decodeFromString<CompletionSyncPayload>(queued.last().payloadJson)
            assertEquals(setOf("existing", "new-a", "new-b"), payload.evidenceClientOpIds?.toSet())
            assertEquals("pending", dao.evidenceByClientOpId("new-a")?.uploadStatus)
            assertEquals("draft", dao.evidenceByClientOpId("other-kind")?.uploadStatus)
        }

    @Test
    fun anOfflineSubmissionResumesAndCompletesOnlyAfterBothPhotosReachTheApi() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val older = File.createTempFile("older", ".jpg", context.cacheDir).apply { writeText("older") }
            val newer = File.createTempFile("newer", ".jpg", context.cacheDir).apply { writeText("newer") }
            try {
                dao.insertDraftEvidence(
                    evidence("older", capturedAt = Instant.parse("2026-10-06T09:00:00Z"))
                        .copy(filePath = older.path, uploadStatus = "draft"),
                )
                dao.insertDraftEvidence(evidence("newer").copy(filePath = newer.path, uploadStatus = "draft"))
                assertTrue(dao.stageEvidenceCompletion(completion(), "after"))

                val serverEvents = mutableListOf<String>()
                val available = mutableSetOf<String>()
                val otherApi = mockk<WorkOrdersApi>(relaxed = true)
                coEvery { otherApi.complete(any(), any()) } coAnswers {
                    serverEvents += "complete"
                    mockk(relaxed = true)
                }
                val api =
                    object : WorkOrdersApi by otherApi {
                        override suspend fun uploadEvidence(
                            id: String,
                            file: MultipartBody.Part,
                            kind: RequestBody,
                            capturedAt: RequestBody,
                            lat: RequestBody,
                            lng: RequestBody,
                            clientOpId: RequestBody,
                            poleId: RequestBody?,
                        ): EvidenceItemDto {
                            val photoId = Buffer().also { clientOpId.writeTo(it) }.readUtf8()
                            if (photoId !in available) throw IOException("Offline")
                            serverEvents += photoId
                            return EvidenceItemDto(
                                photoId,
                                "after",
                                "2026-10-06T10:00:00Z",
                                10.0,
                                106.0,
                                "thumb",
                                "original",
                            )
                        }
                    }
                val worker =
                    SyncQueueProcessor(
                        database.syncQueueDao(),
                        setOf(
                            UploadWorkOrderEvidenceSyncHandler(api, dao),
                            CompleteWorkOrderSyncHandler(api, dao, mockk(relaxed = true)),
                        ),
                    )

                assertTrue(worker.processQueuedOps())
                assertTrue(serverEvents.isEmpty())
                available += "newer"
                assertTrue(worker.processQueuedOps())
                assertEquals(listOf("newer"), serverEvents)
                available += "older"
                assertFalse(worker.processQueuedOps())
                assertEquals(listOf("newer", "older", "complete"), serverEvents)
                assertEquals("synced", dao.completionByWorkOrderId("WO-1")?.submitStatus)
            } finally {
                older.delete()
                newer.delete()
            }
        }

    @Test
    fun retryRestartsOnlyFailedUploadAndCompletionWithTheSameIds() =
        runTest {
            stageTwoPhotos()
            finishUpload("good", "done")
            finishUpload("other", "failed", SYNC_ATTEMPTS_EXHAUSTED)
            finishUpload("COMP-1", "failed")
            dao.updateCompletionSubmitStatus("WO-1", "failed")

            assertTrue(dao.retryEvidenceSubmission("WO-1"))
            assertEquals(listOf("other", "COMP-1"), database.syncQueueDao().queuedRows().map { it.clientOpId })
            assertEquals("done", database.syncQueueDao().statusOf("good"))
            assertEquals("pending", dao.completionByWorkOrderId("WO-1")?.submitStatus)
        }

    @Test
    fun reopeningAReportRemovesRejectedPhotosAndPreservesTheReportAndSuccessfulUploads() =
        runTest {
            stageTwoPhotos()
            finishUpload("good", "done")
            finishUpload("other", "failed", EVIDENCE_REPLACEMENT_REQUIRED)
            finishUpload("COMP-1", "failed")
            dao.updateCompletionSubmitStatus("WO-1", "failed")

            assertFalse(dao.retryEvidenceSubmission("WO-1"))
            assertEquals(listOf("/data/evidence/other.jpg"), dao.reopenEvidenceSubmission("WO-1"))
            assertNull(dao.evidenceByClientOpId("other"))
            assertNull(database.syncQueueDao().statusOf("COMP-1"))
            assertEquals("done", database.syncQueueDao().statusOf("good"))
            assertEquals(completion().copy(submitStatus = "draft"), dao.completionByWorkOrderId("WO-1"))

            dao.insertDraftEvidence(evidence("replacement").copy(uploadStatus = "draft"))
            assertTrue(dao.stageEvidenceCompletion(completion(clientOpId = "COMP-2"), "after"))
            assertEquals(listOf("replacement", "COMP-2"), database.syncQueueDao().queuedRows().map { it.clientOpId })
        }

    private suspend fun stageTwoPhotos() {
        dao.insertDraftEvidence(evidence("good").copy(uploadStatus = "draft"))
        dao.insertDraftEvidence(evidence("other").copy(uploadStatus = "draft"))
        dao.stageEvidenceCompletion(completion(), "after")
    }

    private suspend fun finishUpload(
        clientOpId: String,
        status: String,
        error: String? = null,
    ) {
        val queue = database.syncQueueDao()
        val operation = queue.queuedRows().single { it.clientOpId == clientOpId }
        queue.updateStatus(operation.id, status, 1, error, Instant.now())
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
