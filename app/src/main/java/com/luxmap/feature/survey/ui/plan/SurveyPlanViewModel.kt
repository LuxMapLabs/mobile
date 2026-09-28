package com.luxmap.feature.survey.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.survey.domain.SurveyReadinessInputProvider
import com.luxmap.feature.survey.domain.usecase.CheckSurveyReadinessUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SurveyPlanViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
        private val readinessInputProvider: SurveyReadinessInputProvider,
        private val checkSurveyReadinessUseCase: CheckSurveyReadinessUseCase,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SurveyPlanUiState>(SurveyPlanUiState.Loading)
        val uiState: StateFlow<SurveyPlanUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                repository
                    .observeAssignedRoutes()
                    .catch { e ->
                        _uiState.value = SurveyPlanUiState.Error(e.message ?: "Không tải được tuyến khảo sát")
                    }.collect { routes ->
                        _uiState.value =
                            if (routes.isEmpty()) {
                                SurveyPlanUiState.Empty
                            } else {
                                SurveyPlanUiState.Success(routes)
                            }
                    }
            }
        }

        fun onRouteSelected(surveySweepId: String) {
            val current = _uiState.value as? SurveyPlanUiState.Success ?: return
            _uiState.value = current.copy(selectedSurveySweepId = surveySweepId, readiness = null)

            viewModelScope.launch {
                val route = current.routes.first { it.surveySweepId == surveySweepId }
                val input = readinessInputProvider.gather(route)
                val result = checkSurveyReadinessUseCase(input)

                // Only apply the result if the user has not since picked a different route.
                val latest = _uiState.value
                if (latest is SurveyPlanUiState.Success && latest.selectedSurveySweepId == surveySweepId) {
                    _uiState.value = latest.copy(readiness = result)
                }
            }
        }
    }
