package com.luxmap.feature.workorder.polegps.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PoleGpsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertOrder(entity: LocalPoleGpsOrderEntity)

    @Query("SELECT * FROM local_pole_gps_order WHERE workOrderId = :workOrderId")
    abstract suspend fun order(workOrderId: String): LocalPoleGpsOrderEntity?

    @Query("SELECT * FROM local_work_order_attachment WHERE workOrderId = :workOrderId ORDER BY fileName")
    abstract fun observeAttachments(workOrderId: String): Flow<List<LocalWorkOrderAttachmentEntity>>

    @Query("SELECT * FROM local_work_order_attachment WHERE workOrderId = :workOrderId ORDER BY fileName")
    abstract suspend fun attachments(workOrderId: String): List<LocalWorkOrderAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAttachmentsIgnore(items: List<LocalWorkOrderAttachmentEntity>)

    @Query(
        "DELETE FROM local_work_order_attachment WHERE workOrderId = :workOrderId AND attachmentId NOT IN (:keepIds)",
    )
    abstract suspend fun deleteAttachmentsNotIn(
        workOrderId: String,
        keepIds: List<String>,
    )

    @Query("DELETE FROM local_work_order_attachment WHERE workOrderId = :workOrderId")
    abstract suspend fun deleteAttachments(workOrderId: String)

    @Query(
        "UPDATE local_work_order_attachment SET status = :status, localPath = :localPath " +
            "WHERE workOrderId = :workOrderId AND attachmentId = :attachmentId",
    )
    abstract suspend fun updateAttachmentStatus(
        workOrderId: String,
        attachmentId: String,
        status: String,
        localPath: String?,
    )

    @Transaction
    open suspend fun replaceAttachments(
        workOrderId: String,
        items: List<LocalWorkOrderAttachmentEntity>,
    ) {
        insertAttachmentsIgnore(items)
        deleteAttachmentsNotIn(workOrderId, items.map { it.attachmentId })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDeviceMeasurement(entity: LocalPoleGpsMeasurementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertServerMeasurements(items: List<LocalPoleGpsMeasurementEntity>)

    @Query("SELECT * FROM local_pole_gps_measurement WHERE workOrderId = :workOrderId")
    abstract fun observeMeasurements(workOrderId: String): Flow<List<LocalPoleGpsMeasurementEntity>>

    @Query("SELECT * FROM local_pole_gps_measurement WHERE workOrderId = :workOrderId")
    abstract suspend fun measurements(workOrderId: String): List<LocalPoleGpsMeasurementEntity>

    @Query(
        "UPDATE local_pole_gps_measurement SET evidenceUploadStatus = :status " +
            "WHERE workOrderId = :workOrderId AND poleId = :poleId",
    )
    abstract suspend fun updateEvidenceUploadStatus(
        workOrderId: String,
        poleId: String,
        status: String,
    )

    @Query(
        "SELECT * FROM local_pole_gps_measurement WHERE workOrderId = :workOrderId " +
            "AND evidenceUploadStatus = 'pending'",
    )
    abstract suspend fun pendingPhotoMeasurements(workOrderId: String): List<LocalPoleGpsMeasurementEntity>
}
