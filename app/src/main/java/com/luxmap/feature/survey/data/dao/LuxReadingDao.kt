package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.survey.data.entity.LocalLuxReadingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LuxReadingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reading: LocalLuxReadingEntity)

    @Query("SELECT * FROM local_lux_reading WHERE clientOpId = :clientOpId LIMIT 1")
    suspend fun byId(clientOpId: String): LocalLuxReadingEntity?

    @Query("UPDATE local_lux_reading SET serverLuxId = :serverLuxId WHERE clientOpId = :clientOpId")
    suspend fun updateServerLuxId(
        clientOpId: String,
        serverLuxId: String,
    )

    @Query("SELECT * FROM local_lux_reading WHERE workOrderId = :workOrderId ORDER BY measuredAt DESC, rowid DESC")
    fun observeByWorkOrder(workOrderId: String): Flow<List<LocalLuxReadingEntity>>
}
