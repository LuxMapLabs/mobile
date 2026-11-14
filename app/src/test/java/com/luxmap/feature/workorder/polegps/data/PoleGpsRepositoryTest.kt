package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PoleGpsRepositoryTest {
    private val dao = mockk<PoleGpsDao>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val scheduler = mockk<AttachmentDownloadScheduler>(relaxed = true)
    private val sync = mockk<PoleGpsDetailSync>(relaxed = true)
    private val cache = mockk<PoleListCacheDao>(relaxed = true)
    private val repository = PoleGpsRepository(dao, downloader, scheduler, sync, cache)

    private fun row(
        poleId: String,
        source: String,
    ) = LocalPoleGpsMeasurementEntity("WO-1", poleId, 1.0, 2.0, 3.0, Instant.ofEpochSecond(5), source)

    @Test
    fun `saving a device measurement stores it as DEVICE with the fix values`() =
        runTest {
            val saved = slot<LocalPoleGpsMeasurementEntity>()
            coEvery { dao.upsertDeviceMeasurement(capture(saved)) } returns Unit
            repository.saveDeviceMeasurement("WO-1", "P-1", GpsFix(10.5, 106.5, 4.0, Instant.ofEpochSecond(9)))
            assertEquals("P-1", saved.captured.poleId)
            assertEquals(MeasurementSourceColumn.DEVICE, saved.captured.source)
            assertEquals(10.5, saved.captured.lat, 0.0)
            assertEquals(4.0, saved.captured.accuracyM, 0.0)
        }

    @Test
    fun `measurements are mapped with their source`() =
        runTest {
            coEvery { dao.measurements("WO-1") } returns
                listOf(row("P-1", MeasurementSourceColumn.DEVICE), row("P-2", MeasurementSourceColumn.SERVER))
            val all = repository.allMeasurements("WO-1")
            assertEquals(listOf(MeasurementSource.DEVICE, MeasurementSource.SERVER), all.map { it.source })
        }

    @Test
    fun `observed attachments expose a typed status`() =
        runTest {
            val entity =
                LocalWorkOrderAttachmentEntity(
                    "WO-1",
                    "A-1",
                    "a.pdf",
                    5L,
                    "/x",
                    AttachmentStatusColumn.READY,
                    "/f/a.pdf",
                )
            coEvery { dao.observeAttachments("WO-1") } returns flowOf(listOf(entity))
            val first = repository.observeAttachments("WO-1").first().single()
            assertEquals(AttachmentStatus.READY, first.status)
            assertEquals("/f/a.pdf", first.localPath)
        }

    @Test
    fun `retry resets the file and schedules the download`() =
        runTest {
            repository.retryAttachment("WO-1", "A-1")
            coVerify { downloader.retry("WO-1", "A-1") }
            verify { scheduler.schedule("WO-1") }
        }

    @Test
    fun `pole reference comes from the cached pole list when the pole belongs to the order`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1"),
                )
            coEvery { cache.byWorkOrder("WO-1") } returns
                listOf(LocalPoleListItemEntity("WO-1", "P-1", 4, 10.0, 106.0, "unknown"))
            val reference = repository.poleReference("WO-1", "P-1")!!
            assertEquals(4, reference.position)
            assertEquals(10.0, reference.lat!!, 0.0)
        }

    @Test
    fun `pole reference is null for a pole outside the order`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1"),
                )
            assertNull(repository.poleReference("WO-1", "P-99"))
        }

    @Test
    fun `pole in the order with no cached row has an empty reference`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1"),
                )
            coEvery { cache.byWorkOrder("WO-1") } returns emptyList()
            val reference = repository.poleReference("WO-1", "P-1")!!
            assertNull(reference.position)
            assertNull(reference.lat)
        }

    @Test
    fun `completionPoleLocations is null for an order the detail sync has never cached`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns null
            assertNull(repository.completionPoleLocations("WO-1"))
        }

    @Test
    fun `completionPoleLocations sends every local measurement, clamping a future timestamp to now`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "in_progress",
                    poleIds = listOf("P-1", "P-2"),
                )
            val future = Instant.now().plusSeconds(600)
            coEvery { dao.measurements("WO-1") } returns
                listOf(
                    LocalPoleGpsMeasurementEntity(
                        "WO-1",
                        "P-1",
                        10.0,
                        106.0,
                        3.0,
                        Instant.ofEpochSecond(5),
                        MeasurementSourceColumn.DEVICE,
                    ),
                    LocalPoleGpsMeasurementEntity(
                        "WO-1",
                        "P-2",
                        11.0,
                        107.0,
                        4.0,
                        future,
                        MeasurementSourceColumn.SERVER,
                    ),
                )
            val result = repository.completionPoleLocations("WO-1")!!
            assertEquals(listOf("P-1", "P-2"), result.map { it.poleId })
            assertEquals(Instant.ofEpochSecond(5).toString(), result[0].capturedAt)
            assertTrue(Instant.parse(result[1].capturedAt).isBefore(future))
        }

    @Test
    fun `completionPoleLocations drops a measurement for a pole no longer in the order's pole_ids`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "in_progress",
                    poleIds = listOf("P-1"),
                )
            coEvery { dao.measurements("WO-1") } returns
                listOf(
                    LocalPoleGpsMeasurementEntity(
                        "WO-1",
                        "P-1",
                        10.0,
                        106.0,
                        3.0,
                        Instant.ofEpochSecond(5),
                        MeasurementSourceColumn.DEVICE,
                    ),
                    LocalPoleGpsMeasurementEntity(
                        "WO-1",
                        "P-2",
                        11.0,
                        107.0,
                        4.0,
                        Instant.ofEpochSecond(5),
                        MeasurementSourceColumn.DEVICE,
                    ),
                )
            val result = repository.completionPoleLocations("WO-1")!!
            assertEquals(listOf("P-1"), result.map { it.poleId })
        }

    @Test
    fun `poleReferences lists every pole_id of the order, with null coordinates for an unlisted pole`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto(
                    workOrderId = "WO-1",
                    title = "t",
                    taskKind = "pole_gps_update",
                    woStatus = "assigned",
                    poleIds = listOf("P-1", "P-2"),
                )
            coEvery { cache.byWorkOrder("WO-1") } returns
                listOf(LocalPoleListItemEntity("WO-1", "P-1", 4, 10.0, 106.0, "unknown"))
            val refs = repository.poleReferences("WO-1")
            assertEquals(listOf("P-1", "P-2"), refs.map { it.poleId })
            assertEquals(4, refs[0].position)
            assertNull(refs[1].position)
            assertNull(refs[1].lat)
        }

    @Test
    fun `poleReferences is empty for an order the detail sync has never cached`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns null
            assertTrue(repository.poleReferences("WO-1").isEmpty())
        }
}
