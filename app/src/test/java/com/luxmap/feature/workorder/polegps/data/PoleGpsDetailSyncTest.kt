package com.luxmap.feature.workorder.polegps.data

import com.luxmap.feature.workorder.data.dto.WorkOrderAttachmentDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderPoleLocationDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoleGpsDetailSyncTest {
    private val dao = mockk<PoleGpsDao>(relaxed = true)
    private val scheduler = mockk<AttachmentDownloadScheduler>(relaxed = true)
    private val files = mockk<PoleGpsFiles>(relaxed = true)
    private val sync = PoleGpsDetailSync(dao, scheduler, files)

    private fun dto(
        kind: String = "pole_gps_update",
        status: String = "assigned",
    ) = WorkOrderDetailDto(
        workOrderId = "WO-1",
        title = "t",
        taskKind = kind,
        woStatus = status,
        poleIds = listOf("P-1"),
        attachments =
            listOf(
                WorkOrderAttachmentDto(
                    "A-1",
                    "a.pdf",
                    10,
                    "2026-11-13T01:00:00Z",
                    "/api/v1/work-orders/WO-1/attachments/A-1",
                ),
            ),
        poleLocations = listOf(WorkOrderPoleLocationDto("P-1", 1.0, 2.0, 3.0, "2026-11-13T02:00:00Z")),
    )

    @Test
    fun `active order is cached, attachments stored, prefill stored and download scheduled`() =
        runTest {
            val items = slot<List<LocalWorkOrderAttachmentEntity>>()
            val prefill = slot<List<LocalPoleGpsMeasurementEntity>>()
            coEvery { dao.replaceAttachments("WO-1", capture(items)) } returns Unit
            coEvery { dao.insertServerMeasurements(capture(prefill)) } returns Unit
            sync.store(dto())
            coVerify { dao.upsertOrder(any()) }
            assertEquals(AttachmentStatusColumn.PENDING, items.captured.single().status)
            assertEquals(MeasurementSourceColumn.SERVER, prefill.captured.single().source)
            verify { scheduler.schedule("WO-1") }
        }

    @Test
    fun `finished order drops its files and rows`() =
        runTest {
            sync.store(dto(status = "done"))
            verify { files.deleteOrder("WO-1") }
            coVerify { dao.deleteAttachments("WO-1") }
            verify(exactly = 0) { scheduler.schedule(any()) }
        }

    @Test
    fun `other task kinds are ignored`() =
        runTest {
            sync.store(dto(kind = "repair"))
            coVerify(exactly = 0) { dao.upsertOrder(any()) }
        }

    @Test
    fun `cached returns the stored detail and null when missing`() =
        runTest {
            val stored = slot<LocalPoleGpsOrderEntity>()
            coEvery { dao.upsertOrder(capture(stored)) } returns Unit
            sync.store(dto())
            coEvery { dao.order("WO-1") } returns stored.captured
            coEvery { dao.order("WO-2") } returns null
            assertEquals(listOf("P-1"), sync.cached("WO-1")!!.poleIds)
            assertNull(sync.cached("WO-2"))
        }
}
