package com.luxmap.feature.survey.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.survey.data.SurveyRepository
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SurveyHubViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
        private val completionRepository: WorkOrderCompletionRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SurveyHubUiState>(SurveyHubUiState.Loading)
        val uiState: StateFlow<SurveyHubUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null

        // Same pattern as F09's own startedSurveyWorkOrderId (WorkOrderDetailViewModel) - the
        // Route collects this once to navigate right after a successful start.
        private val _startedRoute = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val startedRoute: SharedFlow<String> = _startedRoute.asSharedFlow()

        // Not part of the combine() below on purpose - that flow rebuilds Success from scratch
        // on every emission, which would otherwise wipe this session-only marker on an unrelated
        // reload (see SurveyHubUiState's own note on why this cannot come from the server for
        // list items).
        private var cancelledWorkOrderIds: Set<String> = emptySet()

        init {
            load()
        }

        fun onRetry() {
            val current = _uiState.value
            if (current !is SurveyHubUiState.Error && current !is SurveyHubUiState.Empty) return
            _uiState.value = SurveyHubUiState.Loading
            load()
        }

        // Unlike onRetry, works from any state - called when the screen's own back stack entry
        // resumes (e.g. returning from the pole-photo list after "Hoan tat"), so a status change
        // made there (in_progress -> done) shows up without the user tapping anything.
        fun refresh() {
            load()
        }

        // No reachable "Bat dau" for a survey work order otherwise: Home deliberately hides
        // survey orders (see HomeScreen.kt). Reuses the same generic POST /work-orders/{id}/start
        // WorkOrderDetailScreen (F09) already uses.
        fun onStartRoute(workOrderId: String) {
            val current = _uiState.value as? SurveyHubUiState.Success ?: return
            if (current.startingWorkOrderId != null) return
            _uiState.value = current.copy(startingWorkOrderId = workOrderId, startError = null)
            viewModelScope.launch {
                repository.startRoute(workOrderId).fold(
                    onSuccess = {
                        load()
                        _startedRoute.tryEmit(workOrderId)
                    },
                    onFailure = { e ->
                        val latest = _uiState.value as? SurveyHubUiState.Success ?: current
                        _uiState.value =
                            latest.copy(
                                startingWorkOrderId = null,
                                startError = e.message ?: "Không bắt đầu được tuyến này",
                            )
                    },
                )
            }
        }

        fun onRequestCancellation(
            workOrderId: String,
            reason: String,
        ) {
            val current = _uiState.value as? SurveyHubUiState.Success ?: return
            if (current.requestingCancellationFor != null) return
            _uiState.value = current.copy(requestingCancellationFor = workOrderId, cancellationError = null)
            viewModelScope.launch {
                repository.requestCancellation(workOrderId, reason).fold(
                    onSuccess = {
                        cancelledWorkOrderIds = cancelledWorkOrderIds + workOrderId
                        val latest = _uiState.value as? SurveyHubUiState.Success ?: current
                        _uiState.value =
                            latest.copy(
                                requestingCancellationFor = null,
                                cancelledWorkOrderIds = cancelledWorkOrderIds,
                            )
                    },
                    onFailure = { e ->
                        val latest = _uiState.value as? SurveyHubUiState.Success ?: current
                        _uiState.value =
                            latest.copy(
                                requestingCancellationFor = null,
                                cancellationError = e.message ?: "Không gửi được yêu cầu hủy",
                            )
                    },
                )
            }
        }

        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch {
                    combine(
                        repository.observeAssignedRoutes(),
                        repository.observeAssignedPoleGpsOrders(),
                        completionRepository.observeCompletedWorkOrderIds(),
                        completionRepository.observeCompletionSyncStates(),
                    ) { routesResult, poleGpsOrders, completedIds, syncStates ->
                        val groups = surveyHubGroups(routesResult.routes, poleGpsOrders, syncStates, completedIds)
                        if (groups.isEmpty()) {
                            SurveyHubUiState.Empty
                        } else {
                            SurveyHubUiState.Success(
                                assignedRoutes = routesResult.routes,
                                assignedPoleGpsOrders = poleGpsOrders,
                                isStale = routesResult.isStale,
                                cancelledWorkOrderIds = cancelledWorkOrderIds,
                                locallyCompletedWorkOrderIds = completedIds,
                                completionSyncStates = syncStates,
                            )
                        }
                    }.catch { e ->
                        _uiState.value =
                            SurveyHubUiState.Error(
                                message = e.message ?: "Không tải được Khảo sát",
                                isNetworkError = e is IOException,
                            )
                    }.collect { state ->
                        _uiState.value = state
                    }
                }
        }
    }
