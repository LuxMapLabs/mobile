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

fun faultStatusLabel(value: String): String =
    when (value) {
        "detected" -> "Đã phát hiện"
        "confirmed" -> "Đã xác nhận"
        "rejected" -> "Đã từ chối"
        "in_progress" -> "Đang xử lý"
        "resolved" -> "Đã xử lý"
        "verified" -> "Đã nghiệm thu"
        else -> value
    }

fun inspectionOutcomeLabel(value: String): String =
    when (value) {
        "fault_present" -> "Xác nhận có sự cố"
        "fault_absent" -> "Không phát hiện sự cố"
        "inconclusive" -> "Chưa xác định"
        else -> value
    }
