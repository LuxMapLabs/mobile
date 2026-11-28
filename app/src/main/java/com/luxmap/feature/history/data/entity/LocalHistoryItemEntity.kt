package com.luxmap.feature.history.data.entity

import androidx.room.Entity

@Entity(tableName = "local_history_item", primaryKeys = ["ownerUserId", "workOrderId"])
data class LocalHistoryItemEntity(
    val ownerUserId: String,
    val workOrderId: String,
    val title: String,
    val taskKind: String,
    val woStatus: String,
    val communeId: String,
    val updatedAt: String,
    val scheduledDate: String?,
    val position: Int,
    val fetchedAt: Long? = null,
    val completedAt: String? = null,
    val closedAt: String? = null,
)
