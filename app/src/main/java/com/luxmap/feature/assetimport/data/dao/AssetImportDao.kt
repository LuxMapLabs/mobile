package com.luxmap.feature.assetimport.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.assetimport.data.entity.LocalAssetImportPoleEntity
import com.luxmap.feature.assetimport.data.entity.LocalAssetImportTripEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface AssetImportDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrip(trip: LocalAssetImportTripEntity)

    @Query("SELECT * FROM local_asset_import_trip WHERE areaCode = :areaCode AND status = 'in_progress' LIMIT 1")
    suspend fun inProgressTripForArea(areaCode: String): LocalAssetImportTripEntity?

    @Query("SELECT * FROM local_asset_import_trip WHERE tripId = :tripId LIMIT 1")
    fun observeTrip(tripId: String): Flow<LocalAssetImportTripEntity?>

    @Query("UPDATE local_asset_import_trip SET status = :status, submittedAt = :submittedAt WHERE tripId = :tripId")
    suspend fun updateTripSubmitted(
        tripId: String,
        status: String,
        submittedAt: Instant,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPole(pole: LocalAssetImportPoleEntity)

    @Query("DELETE FROM local_asset_import_pole WHERE poleEntryId = :poleEntryId")
    suspend fun deletePole(poleEntryId: String)

    @Query("SELECT * FROM local_asset_import_pole WHERE tripId = :tripId ORDER BY seq ASC")
    fun observePoles(tripId: String): Flow<List<LocalAssetImportPoleEntity>>

    @Query("SELECT COUNT(*) FROM local_asset_import_pole WHERE tripId = :tripId")
    suspend fun poleCount(tripId: String): Int
}
