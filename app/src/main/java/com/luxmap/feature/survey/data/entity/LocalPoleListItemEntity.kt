package com.luxmap.feature.survey.data.entity

import androidx.room.Entity

// Cache of the last successfully fetched pole list per work order - lets PoleListScreen still
// show something (marked isStale) when GET work-orders/{id}/poles fails offline. A pole can only
// belong to one work order's list at a time in practice, but the composite key keeps this cache
// correct even if the same poleId ever appears under two different work orders.
@Entity(tableName = "local_pole_list_item", primaryKeys = ["workOrderId", "poleId"])
data class LocalPoleListItemEntity(
    val workOrderId: String,
    val poleId: String,
    val position: Int,
    val fixtureStatus: String,
)
