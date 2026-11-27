package com.luxmap.feature.history.ui

import com.luxmap.feature.history.data.HistoryItem

enum class HistoryFilter(val wireKind: String?, val label: String) {
    ALL(null, "Tất cả"),
    REPAIR("repair", "Sửa chữa"),
    INSPECTION("inspection", "Kiểm tra"),
    SURVEY("survey", "Khảo sát"),
    POLE_GPS_UPDATE("pole_gps_update", "GPS cột"),
}

fun List<HistoryItem>.countFor(filter: HistoryFilter): Int =
    if (filter.wireKind == null) size else count { it.taskKind == filter.wireKind }

fun List<HistoryItem>.filteredBy(filter: HistoryFilter): List<HistoryItem> =
    if (filter.wireKind == null) this else filter { it.taskKind == filter.wireKind }

fun List<HistoryItem>.availableFilters(): List<HistoryFilter> =
    HistoryFilter.entries.filter { it == HistoryFilter.ALL || countFor(it) > 0 }
