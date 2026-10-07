package com.luxmap.feature.workorder.data

import com.luxmap.feature.workorder.data.entity.LocalWorkOrderCompletionEntity
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface WorkOrderCompletionRepository {
    fun observeEvidence(workOrderId: String): Flow<LocalWorkOrderEvidenceEntity?>

    fun observeCompletion(workOrderId: String): Flow<LocalWorkOrderCompletionEntity?>

    suspend fun captureAfterEvidence(
        workOrderId: String,
        clientOpId: String,
        filePath: String,
        lat: Double,
        lng: Double,
        capturedAt: Instant,
    )

    suspend fun submitCompletion(
        workOrderId: String,
        taskKind: String,
        reportNote: String,
        materialsUsed: String?,
        faultOutcomes: List<FaultOutcome>?,
    )

    suspend fun retakeEvidence(workOrderId: String)
}
