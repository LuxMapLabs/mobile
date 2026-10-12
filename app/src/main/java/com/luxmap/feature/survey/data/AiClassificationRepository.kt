package com.luxmap.feature.survey.data

import com.luxmap.core.theme.AssetCondition
import kotlinx.coroutines.flow.Flow

interface AiClassificationRepository {
    fun observeReviews(surveySweepId: String): Flow<List<LocalAiClassificationReview>>

    suspend fun setFinalClass(
        reviewId: String,
        finalClass: AssetCondition,
        overrideReason: String?,
        overriddenBy: String,
    )

    suspend fun submit(surveySweepId: String)
}
