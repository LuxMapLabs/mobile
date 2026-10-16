package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PolePhotoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: LocalPolePhotoEntity)

    @Query("SELECT * FROM local_pole_photo WHERE workOrderId = :workOrderId ORDER BY capturedAt DESC")
    fun observeByWorkOrder(workOrderId: String): Flow<List<LocalPolePhotoEntity>>

    @Query("SELECT * FROM local_pole_photo WHERE photoId = :photoId LIMIT 1")
    suspend fun byId(photoId: String): LocalPolePhotoEntity?
}
