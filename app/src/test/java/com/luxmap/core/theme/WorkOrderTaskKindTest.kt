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

    @Test
    fun `wire value maps to POLE_GPS_UPDATE`() {
        assertEquals(WorkOrderTaskKind.POLE_GPS_UPDATE, workOrderTaskKindFromWire("pole_gps_update"))
    }

    @Test
    fun `unknown wire value still falls back to INSPECTION`() {
        assertEquals(WorkOrderTaskKind.INSPECTION, workOrderTaskKindFromWire("something_new"))
    }

    @Test
    fun `label is the Vietnamese pole GPS name`() {
        assertEquals("Cập nhật GPS cột", WorkOrderTaskKind.POLE_GPS_UPDATE.label())
    }

    @Test
    fun `badge uses the verified status palette`() {
        assertEquals(WorkOrderStatus.VERIFIED.badgeColors(), WorkOrderTaskKind.POLE_GPS_UPDATE.badgeColors())
    }
}
