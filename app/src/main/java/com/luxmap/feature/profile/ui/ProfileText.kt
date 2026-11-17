package com.luxmap.feature.profile.ui

import com.luxmap.core.common.DateFormatUtils
import java.time.Instant

fun profileSyncSummary(
    pending: Int,
    errors: Int,
    lastSyncedAt: Instant?,
): String {
    val parts =
        buildList {
            if (pending > 0) add("Chờ $pending")
            if (errors > 0) add("Lỗi $errors")
        }
    val last = lastSyncedAt?.let { "Lần cuối ${DateFormatUtils.formatInstant(it)}" }
    return when {
        parts.isEmpty() && last == null -> "Chưa đồng bộ lần nào"
        parts.isEmpty() -> "Đã đồng bộ · $last"
        last == null -> parts.joinToString(" · ")
        else -> (parts + last).joinToString(" · ")
    }
}
