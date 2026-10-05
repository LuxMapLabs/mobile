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
        assertTrue(fault.lat == 10.97)
        assertTrue(fault.lng == 106.49)
        assertEquals("lamp_out", fault.faultType)
        assertEquals("confirmed", fault.faultStatus)
        assertEquals("high", fault.severity)
        assertEquals("fault_present", fault.inspectionOutcome)
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
