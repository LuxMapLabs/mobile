package com.luxmap.feature.workorder.data.sync

import com.luxmap.feature.workorder.data.dto.PoleLocationRequestDto
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
    // Upload ops of each pole's latest photo/video. A survey can now complete before every
    // upload has finished, and the server refuses uploads once the work order is completed.
    @SerialName("pole_media_client_op_ids") val poleMediaClientOpIds: List<String>? = null,
    @SerialName("pole_locations") val poleLocations: List<PoleLocationRequestDto>? = null,
    @SerialName("post_started") val postStarted: Boolean = false,
)
