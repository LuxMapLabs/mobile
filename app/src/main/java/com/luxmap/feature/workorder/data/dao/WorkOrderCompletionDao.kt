package com.luxmap.feature.workorder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkOrderCompletionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEvidence(evidence: LocalWorkOrderEvidenceEntity)

    @Query("SELECT * FROM local_work_order_evidence WHERE workOrderId = :workOrderId ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatestEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?>

    @Query("SELECT * FROM local_work_order_evidence WHERE clientOpId = :clientOpId LIMIT 1")
    suspend fun evidenceByClientOpId(clientOpId: String): LocalWorkOrderEvidenceEntity?

    @Query(
        "SELECT clientOpId FROM local_work_order_evidence WHERE workOrderId = :workOrderId " +
            "ORDER BY capturedAt DESC LIMIT 1",
    )
    suspend fun latestEvidenceClientOpId(workOrderId: String): String?

    @Query("UPDATE local_work_order_evidence SET uploadStatus = :status WHERE clientOpId = :clientOpId")
    suspend fun updateEvidenceUploadStatus(
        clientOpId: String,
        status: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceCompletion(completion: LocalWorkOrderCompletionEntity)

    @Query("SELECT * FROM local_work_order_completion WHERE workOrderId = :workOrderId LIMIT 1")
    fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?>

    @Query("SELECT * FROM local_work_order_completion WHERE workOrderId = :workOrderId LIMIT 1")
    suspend fun completionByWorkOrderId(workOrderId: String): LocalWorkOrderCompletionEntity?

    @Query("UPDATE local_work_order_completion SET submitStatus = :status WHERE workOrderId = :workOrderId")
    suspend fun updateCompletionSubmitStatus(
        workOrderId: String,
        status: String,
    )
}
