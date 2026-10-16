package com.luxmap.feature.survey.data

import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PolePhotoDao
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

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
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
            val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
            val repository = RealPolePhotoRepository(dao, syncQueueManager, syncQueueDao)

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
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            every { syncQueueDao.observeStatusesFor(listOf("PHOTO-1", "PHOTO-2")) } returns
                flowOf(listOf(queueRow("PHOTO-1", "done"), queueRow("PHOTO-2", "failed")))
            val repository = RealPolePhotoRepository(dao, syncQueueManager, syncQueueDao)

            val result = repository.observeUploadStatuses(listOf("PHOTO-1", "PHOTO-2"))

            assertEquals(mapOf("PHOTO-1" to "done", "PHOTO-2" to "failed"), result.first())
        }

    @Test
    fun `observeUploadStatuses returns an empty map without querying when given no ids`() =
        runTest {
            val dao = mockk<PolePhotoDao>()
            val syncQueueManager = mockk<SyncQueueManager>()
            val syncQueueDao = mockk<SyncQueueDao>()
            val repository = RealPolePhotoRepository(dao, syncQueueManager, syncQueueDao)

            val result = repository.observeUploadStatuses(emptyList())

            assertEquals(emptyMap<String, String>(), result.first())
        }
}
