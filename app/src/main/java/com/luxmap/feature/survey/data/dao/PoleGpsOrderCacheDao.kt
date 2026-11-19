package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity

@Dao
interface PoleGpsOrderCacheDao {
    @Query("SELECT * FROM local_assigned_pole_gps_order WHERE ownerUserId = :ownerUserId ORDER BY scheduledDate")
    suspend fun all(ownerUserId: String): List<LocalAssignedPoleGpsOrderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalAssignedPoleGpsOrderEntity>)

    @Query("DELETE FROM local_assigned_pole_gps_order WHERE ownerUserId = :ownerUserId")
    suspend fun deleteAll(ownerUserId: String)

    @Transaction
    suspend fun replaceAll(
        ownerUserId: String,
        items: List<LocalAssignedPoleGpsOrderEntity>,
    ) {
        require(items.all { it.ownerUserId == ownerUserId })
        deleteAll(ownerUserId)
        insertAll(items)
    }
}
