package com.luxmap.feature.assetimport.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "local_asset_import_pole")
data class LocalAssetImportPoleEntity(
    @PrimaryKey val poleEntryId: String,
    val tripId: String,
    val seq: Int,
    val photoFilePath: String,
    val lat: Double?,
    val lng: Double?,
    val manuallyAdjusted: Boolean,
    val note: String,
    val capturedAt: Instant,
)
