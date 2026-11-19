package com.luxmap.feature.survey.data.entity

import androidx.room.Entity

@Entity(tableName = "local_assigned_pole_gps_order", primaryKeys = ["ownerUserId", "workOrderId"])
data class LocalAssignedPoleGpsOrderEntity(
    val ownerUserId: String,
    val workOrderId: String,
    val title: String,
    val communeId: String,
    val woStatus: String,
    val dueDate: String?,
    val scheduledDate: String?,
)
