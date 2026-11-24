package com.luxmap.feature.workorder.polegps.data

import androidx.room.withTransaction
import com.luxmap.core.database.AppDatabase
import com.luxmap.core.network.AssetsApi
import com.luxmap.core.sync.SyncQueueManager
import com.luxmap.feature.survey.data.dao.PoleListCacheDao
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant

class PoleGpsRepositoryTest {
    // enqueuePendingPhotoUploads runs its insert+enqueue+status-flip through
    // AppDatabase.withTransaction, a top-level Room extension function - mockkStatic is the only
    // way to stub it on a plain mockk<AppDatabase>().
    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Unit>()) } coAnswers {
            secondArg<suspend () -> Unit>().invoke()
        }
        every { dao.observeRoute(any()) } returns flowOf(null)
        coEvery { dao.route(any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private val database = mockk<AppDatabase>()
    private val dao = mockk<PoleGpsDao>(relaxed = true)
    private val downloader = mockk<AttachmentDownloader>(relaxed = true)
    private val scheduler = mockk<AttachmentDownloadScheduler>(relaxed = true)
    private val sync = mockk<PoleGpsDetailSync>(relaxed = true)
    private val cache = mockk<PoleListCacheDao>(relaxed = true)
    private val completionDao = mockk<WorkOrderCompletionDao>(relaxed = true)
    private val syncQueueManager = mockk<SyncQueueManager>(relaxed = true)
    private val assetsApi = mockk<AssetsApi>()
    private val repository =
        PoleGpsRepository(database, dao, downloader, scheduler, sync, cache, completionDao, syncQueueManager, assetsApi)

    private fun row(
        poleId: String,
        source: String,
    ) = LocalPoleGpsMeasurementEntity("WO-1", poleId, 1.0, 2.0, 3.0, Instant.ofEpochSecond(5), source)

    @Test
    fun `saveCapturedPhoto stores a device measurement with the photo path and a pending upload status`() =
        runTest {
            val saved = slot<LocalPoleGpsMeasurementEntity>()
            coEvery { dao.upsertDeviceMeasurement(capture(saved)) } returns Unit
            repository.saveCapturedPhoto("WO-1", "P-1", "/f/p1.jpg", 10.0, 106.0, 5.0, Instant.ofEpochSecond(9))
            assertEquals("/f/p1.jpg", saved.captured.photoFilePath)
            assertEquals("pending", saved.captured.evidenceUploadStatus)
            assertEquals(MeasurementSourceColumn.DEVICE, saved.captured.source)
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
    fun `poles the server reported without a photo are flagged and exposed as missing`() =
        runTest {
            repository.markPhotosMissingOnServer("WO-1", listOf("P-1", "P-2"))
            coVerify { dao.updateEvidenceUploadStatus("WO-1", "P-1", PHOTO_MISSING_ON_SERVER) }
            coVerify { dao.updateEvidenceUploadStatus("WO-1", "P-2", PHOTO_MISSING_ON_SERVER) }
            coEvery { dao.measurements("WO-1") } returns
                listOf(
                    row("P-1", MeasurementSourceColumn.DEVICE).copy(evidenceUploadStatus = PHOTO_MISSING_ON_SERVER),
                    row("P-2", MeasurementSourceColumn.DEVICE).copy(evidenceUploadStatus = "queued"),
                )
            assertEquals(listOf(true, false), repository.allMeasurements("WO-1").map { it.photoMissingOnServer })
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
                listOf(
                    LocalPoleListItemEntity(
                        "WO-1", "P-1", 4, 10.0, 106.0, "unknown",
                        segmentId = "S1", note = "Cạnh cầu", fixtureType = "led", lampWatt = 100,
                        lampCount = 2, fixtureCount = 1, nearSensitivePoi = true,
                    ),
                )
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
                listOf(
                    LocalPoleListItemEntity(
                        "WO-1", "P-1", 4, 10.0, 106.0, "unknown",
                        segmentId = "S1", note = "Cạnh cầu", fixtureType = "led", lampWatt = 100,
                        lampCount = 2, fixtureCount = 1, nearSensitivePoi = true,
                    ),
                )
            val refs = repository.poleReferences("WO-1")
            assertEquals(listOf("P-1", "P-2"), refs.map { it.poleId })
            assertEquals(4, refs[0].position)
            assertEquals("Cạnh cầu", refs[0].identification?.note)
            assertEquals("S1", refs[0].identification?.segmentId)
            assertEquals("led", refs[0].identification?.fixtureType)
            assertEquals(100, refs[0].identification?.lampWatt)
            assertEquals(2, refs[0].identification?.lampCount)
            assertEquals(1, refs[0].identification?.fixtureCount)
            assertEquals(true, refs[0].identification?.nearSensitivePoi)
            assertNull(refs[1].identification)
            assertNull(refs[1].position)
            assertNull(refs[1].lat)
        }

    @Test
    fun `poleReferences is empty for an order the detail sync has never cached`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns null
            assertTrue(repository.poleReferences("WO-1").isEmpty())
        }

    private val route = "LINESTRING (106 10, 106.001 10)"

    @Test
    fun `refreshRoute stores the order segment geometry`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress", segmentId = "SEG-1")
            coEvery { assetsApi.segment("SEG-1") } returns SegmentDetailDto(route)
            val saved = slot<LocalPoleGpsRouteEntity>()
            coEvery { dao.upsertRoute(capture(saved)) } returns Unit
            repository.refreshRoute("WO-1")
            assertEquals("SEG-1", saved.captured.segmentId)
            assertEquals(route, saved.captured.geomWkt)
        }

    @Test
    fun `refreshRoute keeps the cached route when offline`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress", segmentId = "SEG-1")
            coEvery { assetsApi.segment("SEG-1") } throws IOException("offline")
            repository.refreshRoute("WO-1")
            coVerify(exactly = 0) { dao.upsertRoute(any()) }
        }

    @Test
    fun `refreshRoute does nothing for an order without a segment`() =
        runTest {
            coEvery { sync.cached("WO-1") } returns
                WorkOrderDetailDto("WO-1", "GPS", "pole_gps_update", "in_progress")
            repository.refreshRoute("WO-1")
            coVerify(exactly = 0) { assetsApi.segment(any()) }
        }

    @Test
    fun `observed measurements carry their corridor check`() =
        runTest {
            every { dao.observeMeasurements("WO-1") } returns
                flowOf(
                    listOf(
                        LocalPoleGpsMeasurementEntity(
                            "WO-1", "P-1", 10.0005, 106.0005, 3.0, Instant.EPOCH, MeasurementSourceColumn.DEVICE,
                        ),
                        LocalPoleGpsMeasurementEntity(
                            "WO-1", "P-2", 10.0001, 106.0005, 3.0, Instant.EPOCH, MeasurementSourceColumn.SERVER,
                        ),
                    ),
                )
            every { dao.observeRoute("WO-1") } returns
                flowOf(LocalPoleGpsRouteEntity("WO-1", "SEG-1", route, Instant.EPOCH))
            val measured = repository.observeMeasurements("WO-1").first()
            assertTrue(measured[0].corridor is CorridorCheck.Outside)
            assertTrue(measured[1].corridor is CorridorCheck.Inside)
        }

    @Test
    fun `measurements without a cached route are Unknown`() =
        runTest {
            every { dao.observeMeasurements("WO-1") } returns flowOf(listOf(row("P-1", MeasurementSourceColumn.DEVICE)))
            assertEquals(CorridorCheck.Unknown, repository.observeMeasurements("WO-1").first().single().corridor)
        }
}
