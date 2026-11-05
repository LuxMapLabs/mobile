package com.luxmap.feature.assetimport.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_asset_import_trip")
data class LocalAssetImportTripEntity(
    @PrimaryKey val tripId: String,
    val areaCode: String,
    val areaTitle: String,
    val startedAt: Instant,
    val status: String,
    val submittedAt: Instant?,
)
