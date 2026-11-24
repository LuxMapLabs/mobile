package com.luxmap.feature.workorder.data.sync

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.sync.SyncOpResult
import com.luxmap.feature.workorder.data.dao.WorkOrderCompletionDao
import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderPoleLocationDto
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsCompletionSnapshotTest {
    private val api = mockk<WorkOrdersApi>(relaxed = true)
    private val dao = mockk<WorkOrderCompletionDao>(relaxed = true)
    private val gps = mockk<PoleGpsRepository>(relaxed = true)
    private val handler = CompleteWorkOrderSyncHandler(api, dao, gps)
    private val payload =
        """{"work_order_id":"WO-GPS","evidence_client_op_ids":["PHOTO-1"],
        "pole_locations":[{"pole_id":"P-1","lat":10.0,"lng":106.0,"accuracy_m":3.0,
        "captured_at":"2026-11-18T02:00:00Z"}]}"""
    private val startedPayload = payload.replace("""]}""", """],"post_started":true}""")

    private fun prepare(status: String?) {
        coEvery { dao.completionByWorkOrderId("WO-GPS") } returns
            LocalWorkOrderCompletionEntity("WO-GPS", "Đã đo vị trí cột", null, null, "COMP-1", "pending")
        coEvery { dao.completionEvidenceStatus("WO-GPS", "PHOTO-1") } returns status
        coEvery { api.detail("WO-GPS") } returns
            WorkOrderDetailDto(
                "WO-GPS", "GPS", "pole_gps_update", "in_progress",
            )
    }

    @Test
    fun `GPS completion waits until its photo is uploaded`() =
        runTest {
            prepare("queued")
            assertEquals(SyncOpResult.WaitingForDependency, handler.handle(payload))
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `GPS photo failure never completes the order`() =
        runTest {
            prepare("failed")
            assertTrue(handler.handle(payload) is SyncOpResult.Failed)
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `GPS completion uses the submitted snapshot instead of current measurements`() =
        runTest {
            prepare("done")
            coEvery { gps.completionPoleLocations("WO-GPS") } returns
                listOf(PoleLocationRequestDto("P-1", 99.0, 99.0, 1.0, "2026-11-18T03:00:00Z"))
            assertEquals(SyncOpResult.Done, handler.handle(payload))
            coVerify {
                api.complete(
                    "WO-GPS",
                    match {
                        it.poleLocations ==
                            listOf(PoleLocationRequestDto("P-1", 10.0, 106.0, 3.0, "2026-11-18T02:00:00Z"))
                    },
                )
            }
            coVerify(exactly = 0) { gps.completionPoleLocations(any()) }
        }

    @Test
    fun `a missing photo blocks GPS completion without sending it`() =
        runTest {
            prepare(null)
            assertTrue(handler.handle(payload) is SyncOpResult.Failed)
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `a conflicted photo blocks GPS completion and preserves conflict`() =
        runTest {
            prepare("conflict")
            assertTrue(handler.handle(payload) is SyncOpResult.Conflict)
            coVerify { dao.updateCompletionSubmitStatus("WO-GPS", "conflict") }
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `an interrupted GPS completion already accepted by server is reconciled without sending twice`() =
        runTest {
            prepare("done")
            coEvery { api.detail("WO-GPS") } returns
                WorkOrderDetailDto(
                    "WO-GPS", "GPS", "pole_gps_update", "done", reportNote = "Đã đo vị trí cột",
                    poleLocations = listOf(WorkOrderPoleLocationDto("P-1", 10.0, 106.0, 3.0, "2026-11-18T02:00:00Z")),
                )
            assertEquals(SyncOpResult.Done, handler.handle(payload))
            coVerify { dao.updateCompletionSubmitStatus("WO-GPS", "synced") }
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `an already completed GPS order with a different report preserves a conflict`() =
        runTest {
            prepare("done")
            coEvery { api.detail("WO-GPS") } returns
                WorkOrderDetailDto(
                    "WO-GPS", "GPS", "pole_gps_update", "done", reportNote = "Different server report",
                )
            assertTrue(handler.handle(payload) is SyncOpResult.Conflict)
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `an accepted GPS report returned by manager is not automatically submitted again after restart`() =
        runTest {
            prepare("done")
            coEvery { api.detail("WO-GPS") } returns returnedIdenticalReport()
            assertEquals(SyncOpResult.Done, handler.handle(startedPayload))
            coVerify(exactly = 0) { api.complete(any(), any()) }
        }

    @Test
    fun `an identical report the engineer submits again after a return is sent`() =
        runTest {
            prepare("done")
            coEvery { api.detail("WO-GPS") } returns returnedIdenticalReport()
            assertEquals(SyncOpResult.Done, handler.handle(payload))
            coVerify(exactly = 1) { api.complete("WO-GPS", any()) }
            coVerify { dao.updateCompletionSubmitStatus("WO-GPS", "synced") }
        }

    @Test
    fun `the first send is marked as started before the request so a retry knows`() =
        runTest {
            prepare("done")
            val saved = slot<String>()
            coEvery { dao.updateCompletionPayload("COMP-1", capture(saved)) } returns Unit
            handler.handle(payload)
            coVerifyOrder {
                dao.updateCompletionPayload("COMP-1", any())
                api.complete("WO-GPS", any())
            }
            assertTrue(saved.captured.contains("\"post_started\":true"))
        }

    private fun returnedIdenticalReport() =
        WorkOrderDetailDto(
            "WO-GPS",
            "GPS",
            "pole_gps_update",
            "in_progress",
            reportNote = "Đã đo vị trí cột",
            reviewNote = "Đo lại cột này",
            poleLocations = listOf(WorkOrderPoleLocationDto("P-1", 10.0, 106.0, 3.0, "2026-11-18T02:00:00Z")),
        )

    @Test
    fun `a returned GPS order accepts a genuinely revised report`() =
        runTest {
            prepare("done")
            coEvery { api.detail("WO-GPS") } returns
                WorkOrderDetailDto(
                    "WO-GPS", "GPS", "pole_gps_update", "in_progress", reportNote = "Báo cáo cũ từ kỹ sư",
                    reviewNote = "Đo lại cột này",
                    poleLocations = listOf(WorkOrderPoleLocationDto("P-1", 11.0, 106.0, 3.0, "2026-11-18T01:00:00Z")),
                )
            assertEquals(SyncOpResult.Done, handler.handle(payload))
            coVerify(exactly = 1) { api.complete("WO-GPS", any()) }
        }
}
