package com.luxmap.feature.workorder.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkOrderFaultLabelsTest {
    @Test
    fun `fault type labels`() {
        assertEquals("Đèn tắt", faultTypeLabel("lamp_out"))
        assertEquals("Đèn mờ", faultTypeLabel("lamp_dim"))
        assertEquals("Mất điện cả tuyến", faultTypeLabel("segment_outage"))
        assertEquals("Mất kết nối cảm biến", faultTypeLabel("node_offline"))
        assertEquals("Suy giảm thời gian chiếu sáng", faultTypeLabel("runtime_decline"))
        assertEquals("Điện áp bất thường", faultTypeLabel("voltage_anomaly"))
        assertEquals("Lỗi phần cứng IoT", faultTypeLabel("iot_hardware_fault"))
        assertEquals("future_type", faultTypeLabel("future_type"))
    }

    @Test
    fun `inspection outcome labels, with a fallback for an unknown value`() {
        assertEquals("Xác nhận có sự cố", inspectionOutcomeLabel("fault_present"))
        assertEquals("Không phát hiện sự cố", inspectionOutcomeLabel("fault_absent"))
        assertEquals("Chưa xác định", inspectionOutcomeLabel("inconclusive"))
        assertEquals("future_outcome", inspectionOutcomeLabel("future_outcome"))
    }
}
