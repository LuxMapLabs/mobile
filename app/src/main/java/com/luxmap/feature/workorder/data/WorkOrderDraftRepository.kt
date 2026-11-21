package com.luxmap.feature.workorder.data

data class WorkOrderDraft(
    val reportNote: String,
    val materialsUsed: String,
    val faultOutcomes: Map<String, String>,
)

interface WorkOrderDraftRepository {
    suspend fun ownerUserId(): String?

    suspend fun load(
        ownerUserId: String,
        workOrderId: String,
    ): WorkOrderDraft?

    suspend fun save(
        ownerUserId: String,
        workOrderId: String,
        draft: WorkOrderDraft,
    )

    suspend fun delete(
        ownerUserId: String,
        workOrderId: String,
    )
}
