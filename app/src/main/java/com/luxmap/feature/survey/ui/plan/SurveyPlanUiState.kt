package com.luxmap.feature.survey.ui.plan

import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

sealed interface SurveyPlanUiState {
    data object Loading : SurveyPlanUiState

    // One work order per screen, matching the real business flow - F03 is reached by tapping a
    // single assigned survey work order (from Home or the Hub's "Tuyến được giao" card), not by
    // browsing a list. readiness starts null and is filled in once SurveyReadinessInputProvider
    // finishes its first run.
    data class Success(
        val route: AssignedSurveyRoute,
        val readiness: SurveyReadinessResult? = null,
        val rejectionReason: String? = null,
    ) : SurveyPlanUiState

    // The work order id this screen was opened with isn't (or is no longer) in the caller's
    // assigned survey work orders - e.g. it was reassigned or cancelled after Home/Hub last synced.
    data object Empty : SurveyPlanUiState

    data class Error(val message: String) : SurveyPlanUiState
}
