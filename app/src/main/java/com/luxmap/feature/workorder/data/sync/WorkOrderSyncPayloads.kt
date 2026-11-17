package com.luxmap.feature.workorder.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FaultOutcome(
    @SerialName("fault_id") val faultId: String,
    val outcome: String,
)

@Serializable
data class EvidenceSyncPayload(
    @SerialName("client_op_id") val clientOpId: String,
)

@Serializable
data class CompletionSyncPayload(
    @SerialName("work_order_id") val workOrderId: String,
    @SerialName("evidence_client_op_ids") val evidenceClientOpIds: List<String>? = null,
    @SerialName("pole_review_client_op_ids") val poleReviewClientOpIds: List<String>? = null,
)
