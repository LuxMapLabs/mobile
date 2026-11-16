package com.luxmap.feature.workorder.ui.detail

fun faultTypeLabel(value: String): String =
    when (value) {
        "lamp_out" -> "Đèn tắt"
        "lamp_dim" -> "Đèn mờ"
        "segment_outage" -> "Mất điện cả tuyến"
        "node_offline" -> "Mất kết nối cảm biến"
        "runtime_decline" -> "Suy giảm thời gian chiếu sáng"
        else -> value
    }

fun inspectionOutcomeLabel(value: String): String =
    when (value) {
        "fault_present" -> "Xác nhận có sự cố"
        "fault_absent" -> "Không phát hiện sự cố"
        "inconclusive" -> "Chưa xác định"
        else -> value
    }
