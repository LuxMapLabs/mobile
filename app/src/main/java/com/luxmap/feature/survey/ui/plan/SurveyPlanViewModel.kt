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

// UI-only, same unconfirmed-sweep-status gap as the Hub's rejected-sweep card (Phase 3) - no
// real "rejected" status exists on SurveySweepStatus yet. Kept as this package's own flag rather
// than sharing SurveyHubViewModel's SHOW_UNCONFIRMED_HUB_CARDS (see docs/contract-drift.md).
internal const val SHOW_RESURVEY_BANNER = false

private const val FAKE_REJECTION_REASON = "Video bị rung mạnh ở đoạn đầu, không đọc được cột đèn"

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
            loadRoutes()
        }

        fun onRetry() {
            if (_uiState.value !is SurveyPlanUiState.Error) return
            _uiState.value = SurveyPlanUiState.Loading
            loadRoutes()
        }

        private fun loadRoutes() {
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
                                SurveyPlanUiState.Success(
                                    routes = routes,
                                    rejectionReason = if (SHOW_RESURVEY_BANNER) FAKE_REJECTION_REASON else null,
                                )
                            }
                    }
            }
        }

        fun onRouteSelected(workOrderId: String) {
            val current = _uiState.value as? SurveyPlanUiState.Success ?: return
            _uiState.value = current.copy(selectedWorkOrderId = workOrderId, readiness = null)

            viewModelScope.launch {
                val input = readinessInputProvider.gather()
                val result = checkSurveyReadinessUseCase(input)

                // Only apply the result if the user has not since picked a different route.
                val latest = _uiState.value
                if (latest is SurveyPlanUiState.Success && latest.selectedWorkOrderId == workOrderId) {
                    _uiState.value = latest.copy(readiness = result)
                }
            }
        }
    }
