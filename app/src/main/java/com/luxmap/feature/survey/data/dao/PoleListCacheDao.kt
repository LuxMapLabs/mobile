package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luxmap.feature.survey.data.entity.LocalPoleListItemEntity

@Dao
interface PoleListCacheDao {
    @Query("SELECT * FROM local_pole_list_item WHERE workOrderId = :workOrderId ORDER BY position")
    suspend fun byWorkOrder(workOrderId: String): List<LocalPoleListItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalPoleListItemEntity>)

    @Query("DELETE FROM local_pole_list_item WHERE workOrderId = :workOrderId")
    suspend fun deleteForWorkOrder(workOrderId: String)

    // Wraps delete+insert in one transaction so a fresh fetch never leaves the cache briefly
    // empty (or mixing old and new rows) if the process dies mid-call.
    @Transaction
    suspend fun replaceForWorkOrder(
        workOrderId: String,
        items: List<LocalPoleListItemEntity>,
    ) {
        deleteForWorkOrder(workOrderId)
        insertAll(items)
    }
}
