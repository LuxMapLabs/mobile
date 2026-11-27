package com.luxmap.feature.history.data

import java.time.Instant

data class HistoryItem(
    val workOrderId: String,
    val title: String,
    val taskKind: String,
    val woStatus: String,
    val communeId: String,
    val updatedAt: String,
    val scheduledDate: String?,
)

data class HistoryResult(
    val items: List<HistoryItem>,
    val isStale: Boolean,
    val syncedAt: Instant? = null,
)
