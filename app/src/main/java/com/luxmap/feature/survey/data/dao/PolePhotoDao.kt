package com.luxmap.feature.survey.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.survey.data.entity.LocalPolePhotoEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface PolePhotoDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(photo: LocalPolePhotoEntity)

    @Query("SELECT * FROM local_pole_photo WHERE workOrderId = :workOrderId ORDER BY capturedAt DESC")
    fun observeByWorkOrder(workOrderId: String): Flow<List<LocalPolePhotoEntity>>

    @Query("SELECT * FROM local_pole_photo WHERE photoId = :photoId LIMIT 1")
    suspend fun byId(photoId: String): LocalPolePhotoEntity?

    @Query("SELECT * FROM local_pole_photo WHERE serverPhotoId = :serverPhotoId LIMIT 1")
    suspend fun byServerPhotoId(serverPhotoId: String): LocalPolePhotoEntity?

    @Query("UPDATE local_pole_photo SET serverPhotoId = :serverPhotoId WHERE photoId = :photoId")
    suspend fun updateServerPhotoId(
        photoId: String,
        serverPhotoId: String,
    )

    @Query(
        """
        UPDATE local_pole_photo SET
            aiStatus = :aiStatus,
            cvState = :cvState,
            confidence = :confidence,
            modelVersion = :modelVersion,
            analyzedAt = :analyzedAt,
            decision = :decision,
            decisionNote = :decisionNote,
            decidedBy = :decidedBy,
            decidedAt = :decidedAt
        WHERE photoId = :photoId
        """,
    )
    suspend fun updateAiResult(
        photoId: String,
        aiStatus: String,
        cvState: String?,
        confidence: Double?,
        modelVersion: String?,
        analyzedAt: Instant?,
        decision: String?,
        decisionNote: String?,
        decidedBy: String?,
        decidedAt: Instant?,
    )
}
