package com.luxmap.feature.workorder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderDraftEntity

@Dao
interface WorkOrderDraftDao {
    @Query("SELECT * FROM local_work_order_draft WHERE ownerUserId = :ownerUserId AND workOrderId = :workOrderId")
    suspend fun get(
        ownerUserId: String,
        workOrderId: String,
    ): LocalWorkOrderDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(draft: LocalWorkOrderDraftEntity)

    @Query("DELETE FROM local_work_order_draft WHERE ownerUserId = :ownerUserId AND workOrderId = :workOrderId")
    suspend fun delete(
        ownerUserId: String,
        workOrderId: String,
    )
}
