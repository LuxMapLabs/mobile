package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkOrderIotDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun decode(metadata: String): WorkOrderFaultDetail =
        json.decodeFromString<WorkOrderDetailDto>(
            """{"work_order_id":"WO-1","title":"IoT","task_kind":"inspection","wo_status":"assigned",
            "faults":[{"fault_id":"FAULT-1","pole_id":null,"location":{"lat":10.97,"lng":106.49},
            "fault_type":"voltage_anomaly","fault_status":"detected","severity":"high"$metadata}]}""",
        ).toWorkOrderDetail().faults.single()

    @Test
    fun `IoT metadata survives decoding and domain mapping without a pole`() {
        val fault =
            decode(
                ""","origin_node_id":"NODE-004","feeder_id":"FDR-001","cabinet_id":"CAB-001",
                "iot_anomaly_code":"voltage_loss"""",
            )
        assertEquals("NODE-004", fault.originNodeId)
        assertEquals("FDR-001", fault.feederId)
        assertEquals("CAB-001", fault.cabinetId)
        assertEquals("voltage_loss", fault.iotAnomalyCode)
        assertNull(fault.poleId)
        assertEquals(10.97, fault.lat, 0.0)
        assertEquals(106.49, fault.lng, 0.0)
    }

    @Test
    fun `old cached responses and explicit null metadata remain readable`() {
        listOf("", ""","origin_node_id":null,"feeder_id":null,"cabinet_id":null,"iot_anomaly_code":null""").forEach {
            val fault = decode(it)
            assertNull(fault.originNodeId)
            assertNull(fault.feederId)
            assertNull(fault.cabinetId)
            assertNull(fault.iotAnomalyCode)
        }
    }
}
