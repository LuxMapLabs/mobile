package com.luxmap.feature.history.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.luxmap.feature.history.data.entity.LocalHistoryItemEntity

@Dao
interface HistoryCacheDao {
    @Query("SELECT * FROM local_history_item WHERE ownerUserId = :ownerUserId ORDER BY position")
    suspend fun all(ownerUserId: String): List<LocalHistoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LocalHistoryItemEntity>)

    @Query("DELETE FROM local_history_item WHERE ownerUserId = :ownerUserId")
    suspend fun deleteAll(ownerUserId: String)

    @Query("DELETE FROM local_history_item")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(
        ownerUserId: String,
        items: List<LocalHistoryItemEntity>,
    ) {
        require(items.all { it.ownerUserId == ownerUserId })
        deleteAll(ownerUserId)
        insertAll(items)
    }
}
