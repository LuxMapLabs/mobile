package com.luxmap.feature.survey.data

import com.luxmap.feature.survey.data.dao.PolePhotoDao
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePolePhotoRepositoryTest {
    @Test
    fun `capture writes a row with the given fields and a fresh photoId`() =
        runTest {
            val dao = mockk<PolePhotoDao>(relaxed = true)
            val repository = FakePolePhotoRepository(dao)

            val result =
                repository.capture(
                    workOrderId = "WO-1",
                    poleId = "POLE-1",
                    filePath = "/data/pole-photos/x.jpg",
                    lat = 10.0,
                    lng = 106.0,
                )

            assertEquals("WO-1", result.workOrderId)
            assertEquals("POLE-1", result.poleId)
            assertEquals("/data/pole-photos/x.jpg", result.filePath)
            assertEquals(10.0, result.lat, 0.0)
            assertEquals(106.0, result.lng, 0.0)
            coVerify(exactly = 1) { dao.insert(result) }
        }

    @Test
    fun `observePhotos delegates straight to the DAO`() =
        runTest {
            val dao = mockk<PolePhotoDao>()
            val rows =
                listOf(
                    LocalPolePhotoEntity(
                        photoId = "PH-1",
                        workOrderId = "WO-1",
                        poleId = "POLE-1",
                        filePath = "/data/x.jpg",
                        capturedAt = java.time.Instant.parse("2026-10-15T10:00:00Z"),
                        lat = 10.0,
                        lng = 106.0,
                    ),
                )
            every { dao.observeByWorkOrder("WO-1") } returns flowOf(rows)
            val repository = FakePolePhotoRepository(dao)

            repository.observePhotos("WO-1").collect { assertEquals(rows, it) }
        }
}
