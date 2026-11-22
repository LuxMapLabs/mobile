package com.luxmap.feature.workorder.data.entity

import androidx.room.Entity

@Entity(tableName = "local_work_order_draft", primaryKeys = ["ownerUserId", "workOrderId"])
data class LocalWorkOrderDraftEntity(
    val ownerUserId: String,
    val workOrderId: String,
    val reportNote: String,
    val materialsUsed: String,
    val faultOutcomesJson: String,
)
