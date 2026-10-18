package com.luxmap.feature.survey.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun localPhoto(
    photoId: String = "PHOTO-1",
    serverPhotoId: String? = null,
) = LocalPolePhotoEntity(
    photoId = photoId,
    workOrderId = "WO-1",
    poleId = "POLE-1",
    filePath = "/data/photo.jpg",
    capturedAt = Instant.parse("2026-10-16T10:00:00Z"),
    lat = 10.97,
    lng = 106.49,
    serverPhotoId = serverPhotoId,
)

private fun polePhotoItemDto(
    photoId: String = "PPH-000001",
    aiStatus: String = "available",
    cvState: String? = "on",
    confidence: Double? = 0.92,
) = PolePhotoItemDto(
    photoId = photoId,
    workOrderId = "WO-1",
    poleId = "POLE-1",
    dataSource = "pole_photo",
    capturedAt = "2026-10-16T10:00:00Z",
    lat = 10.97,
    lng = 106.49,
    uploadedBy = "U-1",
    uploadedAt = "2026-10-16T10:00:05Z",
    aiStatus = aiStatus,
    cvState = cvState,
    confidence = confidence,
    modelVersion = "cv-v1",
    artifactVersion = "art-v1",
    analyzedAt = "2026-10-16T10:00:10Z",
)

private fun queueRow(
    clientOpId: String,
    status: String,
) = SyncQueueEntity(
    clientOpId = clientOpId,
    opType = "upload_pole_photo",
    payloadJson = "{}",
    dependsOnClientOpId = null,
    status = status,
    attemptCount = 0,
    lastError = null,
    createdAt = Instant.parse("2026-10-16T10:00:00Z"),
    updatedAt = Instant.parse("2026-10-16T10:00:00Z"),
)

class RealPolePhotoRepositoryTest {
    @Test
    fun `capture writes the local row then enqueues an upload op keyed by the photo id`() =
        runTest {
            val api = mockk<WorkOrdersApi>(relaxed = true)
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val result =
                repository.capture(
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/photo.jpg",
                    lat = 10.97,
                    lng = 106.49,
                )

            coVerify {
                dao.insert(
                    match {
                        it.photoId == result.photoId && it.workOrderId == "WO-1" &&
                            it.poleId == "POLE-1" && it.filePath == "/data/photo.jpg"
                    },
                )
            }
            coVerify {
                syncQueueManager.enqueue(
                    opType = "upload_pole_photo",
                    payloadJson = "{\"photo_id\":\"${result.photoId}\"}",
                    clientOpId = result.photoId,
                )
            }
        }

    @Test
    fun `observeUploadStatuses maps sync_queue rows by clientOpId`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            every { syncQueueDao.observeStatusesFor(listOf("PHOTO-1", "PHOTO-2")) } returns
                flowOf(listOf(queueRow("PHOTO-1", "done"), queueRow("PHOTO-2", "failed")))
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val result = repository.observeUploadStatuses(listOf("PHOTO-1", "PHOTO-2"))

            assertEquals(mapOf("PHOTO-1" to "done", "PHOTO-2" to "failed"), result.first())
        }

    @Test
    fun `observeUploadStatuses returns an empty map without querying when given no ids`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val result = repository.observeUploadStatuses(emptyList())

            assertEquals(emptyMap<String, String>(), result.first())
        }

    @Test
    fun `fetchResult looks up the local row by serverPhotoId then writes the AI result onto it`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            coEvery { api.polePhotoDetail("WO-1", "PPH-000001") } returns polePhotoItemDto()
            coEvery { dao.byServerPhotoId("PPH-000001") } returns localPhoto(serverPhotoId = "PPH-000001")
            coEvery { dao.byId("PHOTO-1") } returns
                localPhoto(serverPhotoId = "PPH-000001").copy(aiStatus = "available", cvState = "on")
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            val result = repository.fetchResult("WO-1", "PPH-000001")

            assertTrue(result.isSuccess)
            assertEquals("on", result.getOrNull()?.cvState)
            coVerify {
                dao.updateAiResult(
                    photoId = "PHOTO-1",
                    aiStatus = "available",
                    cvState = "on",
                    confidence = 0.92,
                    modelVersion = "cv-v1",
                    analyzedAt = Instant.parse("2026-10-16T10:00:10Z"),
                    decision = null,
                    decisionNote = null,
                    decidedBy = null,
                    decidedAt = null,
                )
            }
        }

    @Test
    fun `fetchResult fails when no local row matches that serverPhotoId`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            coEvery { api.polePhotoDetail("WO-1", "PPH-000001") } returns polePhotoItemDto()
            coEvery { dao.byServerPhotoId("PPH-000001") } returns null
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            assertTrue(repository.fetchResult("WO-1", "PPH-000001").isFailure)
        }

    @Test
    fun `fetchResult fails when the backend request fails`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            coEvery { api.polePhotoDetail("WO-1", "PPH-000001") } throws IllegalStateException("network down")
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            assertTrue(repository.fetchResult("WO-1", "PPH-000001").isFailure)
        }

    @Test
    fun `cachedResult delegates straight to the DAO's byServerPhotoId lookup`() =
        runTest {
            val api = mockk<WorkOrdersApi>()
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            val cached = localPhoto(serverPhotoId = "PPH-000001")
            coEvery { dao.byServerPhotoId("PPH-000001") } returns cached
            val repository = RealPolePhotoRepository(api, dao, syncQueueManager, syncQueueDao)

            assertEquals(cached, repository.cachedResult("PPH-000001"))
        }
}
