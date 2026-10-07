package com.luxmap.feature.workorder.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_work_order_completion")
data class LocalWorkOrderCompletionEntity(
    @PrimaryKey val workOrderId: String,
    val reportNote: String,
    val materialsUsed: String?,
    val faultOutcomesJson: String?,
    val clientOpId: String,
    val submitStatus: String,
)
