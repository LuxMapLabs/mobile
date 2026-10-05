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
        assertEquals("future_type", faultTypeLabel("future_type"))
    }

    @Test
    fun `fault status labels`() {
        assertEquals("Đã phát hiện", faultStatusLabel("detected"))
        assertEquals("Đã xác nhận", faultStatusLabel("confirmed"))
        assertEquals("Đã từ chối", faultStatusLabel("rejected"))
        assertEquals("Đang xử lý", faultStatusLabel("in_progress"))
        assertEquals("Đã xử lý", faultStatusLabel("resolved"))
        assertEquals("Đã nghiệm thu", faultStatusLabel("verified"))
        assertEquals("future_status", faultStatusLabel("future_status"))
    }

    @Test
    fun `inspection outcome labels, with a fallback for an unknown value`() {
        assertEquals("Xác nhận có sự cố", inspectionOutcomeLabel("fault_present"))
        assertEquals("Không phát hiện sự cố", inspectionOutcomeLabel("fault_absent"))
        assertEquals("Chưa xác định", inspectionOutcomeLabel("inconclusive"))
        assertEquals("future_outcome", inspectionOutcomeLabel("future_outcome"))
    }
}
