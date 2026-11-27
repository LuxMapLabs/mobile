package com.luxmap.feature.survey.data

import com.luxmap.core.sync.SyncQueueDao
import com.luxmap.core.sync.SyncQueueEntity
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.LuxReadingDao
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RealLuxReadingRepositoryTest {
    private val dao = mockk<LuxReadingDao>(relaxed = true)
    private val syncQueueManager =
        mockk<SyncQueueManager>(relaxed = true) { coEvery { pendingStartOf(any()) } returns null }
    private val syncQueueDao = mockk<SyncQueueDao>()
    private val repository = RealLuxReadingRepository(dao, syncQueueManager, syncQueueDao)

    private fun entity(
        id: String,
        poleId: String = "POLE-1",
        photoId: String = "PHOTO-1",
        measuredAtMs: Long = 1000,
    ) = LocalLuxReadingEntity(
        clientOpId = id,
        workOrderId = "WO-1",
        poleId = poleId,
        photoId = photoId,
        measuredAt = Instant.ofEpochMilli(measuredAtMs),
        luxValue = 12.3,
        meterModel = "ESP32C3_LUX",
        dataSource = "field",
        createdAt = Instant.ofEpochMilli(measuredAtMs),
    )

    private fun queueRow(
        clientOpId: String,
        status: String,
    ) = SyncQueueEntity(
        clientOpId = clientOpId,
        opType = "upload_lux_reading",
        payloadJson = "{}",
        dependsOnClientOpId = null,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun reading(
        id: String,
        poleId: String,
        photoId: String,
        measuredAtMs: Long,
    ) = LuxReading(id, poleId, photoId, 1.0, Instant.ofEpochMilli(measuredAtMs), null)

    @Test
    fun `record saves the reading then enqueues its upload`() =
        runTest {
            val saved = slot<LocalLuxReadingEntity>()
            val measuredAt = Instant.parse("2026-11-23T12:42:00Z")

            val result = repository.record("WO-1", "POLE-1", "PHOTO-1", 12.3, measuredAt)

            coVerify { dao.insert(capture(saved)) }
            assertEquals("ESP32C3_LUX", saved.captured.meterModel)
            assertEquals("field", saved.captured.dataSource)
            assertEquals(measuredAt, saved.captured.measuredAt)
            assertEquals("PHOTO-1", saved.captured.photoId)
            coVerify {
                syncQueueManager.enqueue(
                    opType = "upload_lux_reading",
                    payloadJson = """{"client_op_id":"${saved.captured.clientOpId}"}""",
                    clientOpId = saved.captured.clientOpId,
                    dependsOnClientOpId = null,
                )
            }
            assertEquals(saved.captured.clientOpId, result.clientOpId)
            assertEquals("queued", result.syncStatus)
        }

    @Test
    fun `observeReadings attaches each reading's sync status`() =
        runTest {
            every { dao.observeByWorkOrder("WO-1") } returns flowOf(listOf(entity("L1"), entity("L2")))
            every { syncQueueDao.observeStatusesFor(listOf("L1", "L2")) } returns
                flowOf(listOf(queueRow("L1", "done"), queueRow("L2", "failed")))

            val readings = repository.observeReadings("WO-1").first()

            assertEquals(listOf("done", "failed"), readings.map { it.syncStatus })
        }

    @Test
    fun `observeReadings returns an empty list without querying the queue`() =
        runTest {
            every { dao.observeByWorkOrder("WO-1") } returns flowOf(emptyList())

            assertEquals(emptyList<LuxReading>(), repository.observeReadings("WO-1").first())
        }

    @Test
    fun `latestByPole keeps the newest reading of each pole`() {
        val readings =
            listOf(
                reading("A1", "POLE-A", "P1", 1000),
                reading("A2", "POLE-A", "P2", 3000),
                reading("B1", "POLE-B", "P3", 2000),
            )

        val latest = readings.latestByPole()

        assertEquals("A2", latest.getValue("POLE-A").clientOpId)
        assertEquals("B1", latest.getValue("POLE-B").clientOpId)
    }

    @Test
    fun `latestForPhoto only uses readings taken with that photo`() {
        val readings =
            listOf(
                reading("A1", "POLE-A", "P1", 1000),
                reading("A2", "POLE-A", "P1", 3000),
                reading("A3", "POLE-A", "P2", 5000),
            )

        assertEquals("A2", readings.latestForPhoto("P1")?.clientOpId)
        assertNull(readings.latestForPhoto("P9"))
    }

    @Test
    fun `a reading with its window and module is stored as a protocol reading at the window's exact median`() =
        runTest {
            val saved = slot<LocalLuxReadingEntity>()

            repository.record(
                "WO-1",
                "POLE-1",
                "PHOTO-1",
                12.35,
                Instant.parse("2026-11-23T12:42:00Z"),
                samples = listOf(12.3, 12.4, 12.31, 12.39, 12.35),
                moduleId = "AA:BB:CC:DD:EE:FF",
            )

            coVerify { dao.insert(capture(saved)) }
            assertEquals(12.35, saved.captured.luxValue, 0.0)
            assertEquals("[12.3,12.4,12.31,12.39,12.35]", saved.captured.samplesJson)
            assertEquals("AA:BB:CC:DD:EE:FF", saved.captured.moduleId)
            assertEquals(1.0, saved.captured.sensorHeightM!!, 0.0)
        }

    @Test
    fun `a reading without its window stays a reference reading`() =
        runTest {
            val saved = slot<LocalLuxReadingEntity>()

            repository.record("WO-1", "POLE-1", "PHOTO-1", 12.3, Instant.parse("2026-11-23T12:42:00Z"))

            coVerify { dao.insert(capture(saved)) }
            assertNull(saved.captured.samplesJson)
            assertNull(saved.captured.moduleId)
            assertNull(saved.captured.sensorHeightM)
        }
}
