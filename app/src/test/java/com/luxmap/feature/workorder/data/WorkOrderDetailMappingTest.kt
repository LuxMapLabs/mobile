package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderFaultLocationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkOrderDetailMappingTest {
    @Test
    fun `maps every top-level field and flattens fault location into lat-lng`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-1",
                title = "Sửa đèn tuyến A",
                taskKind = "repair",
                woStatus = "assigned",
                dueDate = "2026-10-10",
                scheduledDate = "2026-10-09",
                note = "Ưu tiên xử lý trước mưa",
                allowedActions = listOf("start"),
                faults =
                    listOf(
                        WorkOrderFaultDetailDto(
                            faultId = "FAULT-1",
                            poleId = "POLE-0047",
                            location = WorkOrderFaultLocationDto(lat = 10.97, lng = 106.49),
                            faultType = "lamp_out",
                            faultStatus = "confirmed",
                            severity = "high",
                            inspectionOutcome = "fault_present",
                        ),
                    ),
            )

        val detail = dto.toWorkOrderDetail()

        assertEquals("WO-1", detail.workOrderId)
        assertEquals("Sửa đèn tuyến A", detail.title)
        assertEquals("repair", detail.taskKind)
        assertEquals("assigned", detail.woStatus)
        assertEquals("2026-10-10", detail.dueDate)
        assertEquals("2026-10-09", detail.scheduledDate)
        assertEquals("Ưu tiên xử lý trước mưa", detail.note)
        assertEquals(listOf("start"), detail.allowedActions)
        val fault = detail.faults.single()
        assertEquals("FAULT-1", fault.faultId)
        assertEquals("POLE-0047", fault.poleId)
        assertTrue(fault.lat == 10.97)
        assertTrue(fault.lng == 106.49)
        assertEquals("lamp_out", fault.faultType)
        assertEquals("confirmed", fault.faultStatus)
        assertEquals("high", fault.severity)
        assertEquals("fault_present", fault.inspectionOutcome)
    }

    @Test
    fun `a fault with no pole_id (a segment-level fault) maps to a null poleId, not a crash`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-3",
                title = "Sửa mất điện tuyến C",
                taskKind = "repair",
                woStatus = "assigned",
                dueDate = null,
                scheduledDate = null,
                note = null,
                allowedActions = listOf("start"),
                faults =
                    listOf(
                        WorkOrderFaultDetailDto(
                            faultId = "FAULT-2",
                            poleId = null,
                            location = WorkOrderFaultLocationDto(lat = 10.97, lng = 106.49),
                            faultType = "segment_outage",
                            faultStatus = "confirmed",
                            severity = "critical",
                            inspectionOutcome = null,
                        ),
                    ),
            )

        val fault = dto.toWorkOrderDetail().faults.single()

        assertEquals(null, fault.poleId)
    }

    @Test
    fun `hasLocation is false when lat and lng both come back 0 - the backend's no-coordinate sentinel`() {
        val noLocation =
            WorkOrderFaultDetail(
                faultId = "FAULT-3",
                poleId = null,
                lat = 0.0,
                lng = 0.0,
                faultType = "segment_outage",
                faultStatus = "confirmed",
                severity = "critical",
                inspectionOutcome = null,
            )

        assertTrue(!noLocation.hasLocation())
    }

    @Test
    fun `hasLocation is true for a real coordinate`() {
        val located =
            WorkOrderFaultDetail(
                faultId = "FAULT-1",
                poleId = "POLE-0047",
                lat = 10.97,
                lng = 106.49,
                faultType = "lamp_out",
                faultStatus = "confirmed",
                severity = "high",
                inspectionOutcome = null,
            )

        assertTrue(located.hasLocation())
    }

    @Test
    fun `a survey order has no faults and that maps to an empty list, not a crash`() {
        val dto =
            WorkOrderDetailDto(
                workOrderId = "WO-2",
                title = "Khảo sát tuyến B",
                taskKind = "survey",
                woStatus = "assigned",
                dueDate = null,
                scheduledDate = null,
                note = null,
                allowedActions = emptyList(),
                faults = emptyList(),
            )

        val detail = dto.toWorkOrderDetail()

        assertEquals("survey", detail.taskKind)
        assertTrue(detail.faults.isEmpty())
        assertEquals(null, detail.dueDate)
        assertEquals(null, detail.note)
    }
}
