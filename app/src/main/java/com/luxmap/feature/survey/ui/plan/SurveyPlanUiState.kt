package com.luxmap.feature.survey.ui.plan

import com.luxmap.feature.survey.data.AssignedSurveyRoute
import com.luxmap.feature.survey.domain.usecase.SurveyReadinessResult

sealed interface SurveyPlanUiState {
    data object Loading : SurveyPlanUiState

    data class Success(
        val routes: List<AssignedSurveyRoute>,
        val selectedSurveySweepId: String? = null,
        val readiness: SurveyReadinessResult? = null,
    ) : SurveyPlanUiState

    data object Empty : SurveyPlanUiState

    data class Error(val message: String) : SurveyPlanUiState
}
