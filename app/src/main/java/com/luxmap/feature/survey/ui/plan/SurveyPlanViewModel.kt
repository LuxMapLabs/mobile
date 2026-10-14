package com.luxmap.feature.survey.ui.plan

import androidx.lifecycle.SavedStateHandle
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
// real "rejected" status exists on WorkOrderStatus yet. Kept as this package's own flag rather
// than sharing SurveyHubViewModel's SHOW_UNCONFIRMED_HUB_CARDS (see docs/contract-drift.md).
internal const val SHOW_RESURVEY_BANNER = false

private const val FAKE_REJECTION_REASON = "Video bị rung mạnh ở đoạn đầu, không đọc được cột đèn"

@HiltViewModel
class SurveyPlanViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: SurveyRepository,
        private val readinessInputProvider: SurveyReadinessInputProvider,
        private val checkSurveyReadinessUseCase: CheckSurveyReadinessUseCase,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<SurveyPlanUiState>(SurveyPlanUiState.Loading)
        val uiState: StateFlow<SurveyPlanUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun onRetry() {
            if (_uiState.value !is SurveyPlanUiState.Error) return
            _uiState.value = SurveyPlanUiState.Loading
            load()
        }

        private fun load() {
            viewModelScope.launch {
                repository
                    .observeAssignedRoutes()
                    .catch { e ->
                        _uiState.value = SurveyPlanUiState.Error(e.message ?: "Không tải được tuyến khảo sát")
                    }.collect { routes ->
                        val route = routes.firstOrNull { it.workOrderId == workOrderId }
                        if (route == null) {
                            _uiState.value = SurveyPlanUiState.Empty
                        } else {
                            _uiState.value =
                                SurveyPlanUiState.Success(
                                    route = route,
                                    rejectionReason = if (SHOW_RESURVEY_BANNER) FAKE_REJECTION_REASON else null,
                                )
                            checkReadiness()
                        }
                    }
            }
        }

        // Runs once the route loads, and again after the user returns from a permission prompt -
        // there is only one route in scope now, so there is no selection step to gate this on.
        fun checkReadiness() {
            viewModelScope.launch {
                val input = readinessInputProvider.gather()
                val result = checkSurveyReadinessUseCase(input)
                val latest = _uiState.value
                if (latest is SurveyPlanUiState.Success) {
                    _uiState.value = latest.copy(readiness = result)
                }
            }
        }

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
