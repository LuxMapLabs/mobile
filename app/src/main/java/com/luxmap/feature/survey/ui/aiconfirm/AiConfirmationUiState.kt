package com.luxmap.feature.survey.ui.aiconfirm

import com.luxmap.core.theme.AssetCondition
import com.luxmap.feature.survey.data.LocalAiClassificationReview

enum class ReviewFilter { ALL, NEEDS_REVIEW, OVERRIDDEN }

data class AiReviewSheetState(
    val review: LocalAiClassificationReview,
    val chosenClass: AssetCondition,
    val reason: String,
)

sealed interface AiConfirmationUiState {
    data object Loading : AiConfirmationUiState

    data class Success(
        val reviews: List<LocalAiClassificationReview>,
        val filter: ReviewFilter,
        val normalCount: Int,
        val dimCount: Int,
        val outCount: Int,
    ) : AiConfirmationUiState

    data object Empty : AiConfirmationUiState

    data class Error(val message: String) : AiConfirmationUiState
}
