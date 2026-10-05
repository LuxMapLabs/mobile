package com.luxmap.core.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderTaskKindTest {
    @Test
    fun `inspection wire value maps to Inspection`() {
        assertEquals(WorkOrderTaskKind.INSPECTION, workOrderTaskKindFromWire("inspection"))
    }

    @Test
    fun `repair wire value maps to Repair`() {
        assertEquals(WorkOrderTaskKind.REPAIR, workOrderTaskKindFromWire("repair"))
    }

    @Test
    fun `survey wire value maps to Survey`() {
        assertEquals(WorkOrderTaskKind.SURVEY, workOrderTaskKindFromWire("survey"))
    }

    @Test
    fun `an unknown wire value falls back to Inspection, not a crash`() {
        assertEquals(WorkOrderTaskKind.INSPECTION, workOrderTaskKindFromWire("something_new"))
    }

    @Test
    fun `labels are Kiểm tra, Sửa chữa, and Khảo sát`() {
        assertEquals("Kiểm tra", WorkOrderTaskKind.INSPECTION.label())
        assertEquals("Sửa chữa", WorkOrderTaskKind.REPAIR.label())
        assertEquals("Khảo sát", WorkOrderTaskKind.SURVEY.label())
    }

    @Test
    fun `badge colors reuse existing pairs, not new colors`() {
        assertEquals(WorkOrderStatus.ASSIGNED.badgeColors(), WorkOrderTaskKind.INSPECTION.badgeColors())
        assertEquals(WorkOrderStatus.IN_PROGRESS.badgeColors(), WorkOrderTaskKind.REPAIR.badgeColors())
        assertEquals(WorkOrderStatus.DONE.badgeColors(), WorkOrderTaskKind.SURVEY.badgeColors())
    }
}
