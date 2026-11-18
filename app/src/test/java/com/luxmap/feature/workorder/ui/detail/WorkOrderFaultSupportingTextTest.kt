package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderFaultSupportingTextTest {
    private val fault =
        WorkOrderFaultDetail(
            faultId = "FAULT-1",
            poleId = null,
            lat = 10.97,
            lng = 106.49,
            faultType = "voltage_anomaly",
            faultStatus = "detected",
            severity = "high",
            inspectionOutcome = null,
        )

    @Test
    fun `device details identify the target before the fault identifier`() {
        assertEquals(
            "Tủ điện: CAB-001\nNode: NODE-004\nMạch: FDR-001\nMã lỗi: voltage_loss\nFAULT-1",
            faultSupportingText(
                fault.copy(
                    cabinetId = "CAB-001",
                    originNodeId = "NODE-004",
                    feederId = "FDR-001",
                    iotAnomalyCode = "voltage_loss",
                ),
            ),
        )
    }

    @Test
    fun `partial metadata is trimmed and blank values are hidden`() {
        assertEquals(
            "Node: NODE-004\nFAULT-1",
            faultSupportingText(fault.copy(cabinetId = " ", originNodeId = " NODE-004 ", feederId = "")),
        )
    }

    @Test
    fun `pole faults retain the existing pole and fault identifiers`() {
        assertEquals("POLE-1\nFAULT-1", faultSupportingText(fault.copy(poleId = "POLE-1")))
        assertEquals("FAULT-1", faultSupportingText(fault))
    }

    @Test
    fun `separate faults keep their own device metadata`() {
        val first = fault.copy(cabinetId = "CAB-001")
        val second = fault.copy(faultId = "FAULT-2", cabinetId = "CAB-002")
        assertEquals(
            listOf("Tủ điện: CAB-001\nFAULT-1", "Tủ điện: CAB-002\nFAULT-2"),
            listOf(first, second).map(::faultSupportingText),
        )
    }
}
