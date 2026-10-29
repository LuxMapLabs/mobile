package com.luxmap.feature.survey.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.SurveyRepository
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
import javax.inject.Inject

internal const val SHOW_UNCONFIRMED_HUB_CARDS = false

// Which screen to navigate to after a successful start - the 2 survey methods run in parallel,
// never merged (CLAUDE.md "Quyet dinh nghiep vu moi da chot 2026-10-15"), so the same start API
// call can be followed by either destination depending on which Hub button triggered it.
enum class SurveyRouteStartDestination { POLE_CAPTURE, READINESS_CHECK }

data class StartedRouteNavigation(val workOrderId: String, val destination: SurveyRouteStartDestination)

private val FAKE_REJECTED_SWEEPS =
    listOf(
        LocalRejectedSweep(
            surveySweepId = "SWEEP-2030",
            rejectionReason = "Video bị rung mạnh ở đoạn đầu, không đọc được cột đèn",
            rejectedByName = "Kỹ sư bảo trì Trần Văn A",
        ),
    )

private val FAKE_PENDING_AI_CONFIRMATION =
    listOf(
        LocalPendingAiConfirmation(surveySweepId = "SWEEP-2029", normalCount = 11, dimCount = 2, outCount = 1),
    )

@HiltViewModel
class SurveyHubViewModel
    @Inject
    constructor(
        private val repository: SurveyRepository,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SurveyHubUiState>(SurveyHubUiState.Loading)
        val uiState: StateFlow<SurveyHubUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null

        // Same pattern as F09's own startedSurveyWorkOrderId (WorkOrderDetailViewModel) - the
        // Route collects this once to navigate right after a successful start, instead of the
        // button calling 2 callbacks itself. Carries a destination since 2 different Hub
        // buttons (pole capture CTA, "Quay video toan tuyen") both start the route then go
        // somewhere different (CLAUDE.md 2026-10-29).
        private val _startedRoute = MutableSharedFlow<StartedRouteNavigation>(extraBufferCapacity = 1)
        val startedRoute: SharedFlow<StartedRouteNavigation> = _startedRoute.asSharedFlow()

        // Not part of the combine() below on purpose - that flow rebuilds Success from scratch
        // on every emission (routes, uploads, connectivity), which would otherwise wipe this
        // session-only marker on an unrelated reload (see SurveyHubUiState's own note on why
        // this cannot come from the server for list items).
        private var cancelledWorkOrderIds: Set<String> = emptySet()

        init {
            load()
        }

        fun onRetry() {
            if (_uiState.value !is SurveyHubUiState.Error) return
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
        // WorkOrderDetailScreen (F09) already uses. Both Hub buttons that can start a route
        // (pole capture CTA, "Quay video toan tuyen") call this with their own destination so
        // the Route knows where to navigate once the call succeeds.
        fun onStartRoute(
            workOrderId: String,
            destination: SurveyRouteStartDestination,
        ) {
            val current = _uiState.value as? SurveyHubUiState.Success ?: return
            if (current.startingWorkOrderId != null) return
            _uiState.value = current.copy(startingWorkOrderId = workOrderId, startError = null)
            viewModelScope.launch {
                repository.startRoute(workOrderId).fold(
                    onSuccess = {
                        load()
                        _startedRoute.tryEmit(StartedRouteNavigation(workOrderId, destination))
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
                        repository.observeUploadingSessions(),
                        connectivityObserver.isOnline,
                    ) { routesResult, queuedSessions, isOnline ->
                        val uploading =
                            queuedSessions.map { session ->
                                UploadingSurveySession(
                                    sessionId = session.sessionId,
                                    surveySweepId = session.surveySweepId,
                                    syncStatus = if (isOnline) SyncStatus.QUEUED_ONLINE else SyncStatus.QUEUED_OFFLINE,
                                )
                            }
                        val rejected = if (SHOW_UNCONFIRMED_HUB_CARDS) FAKE_REJECTED_SWEEPS else emptyList()
                        val pending = if (SHOW_UNCONFIRMED_HUB_CARDS) FAKE_PENDING_AI_CONFIRMATION else emptyList()
                        val hasNothing =
                            routesResult.routes.isEmpty() && uploading.isEmpty() &&
                                rejected.isEmpty() && pending.isEmpty()
                        if (hasNothing) {
                            SurveyHubUiState.Empty
                        } else {
                            SurveyHubUiState.Success(
                                assignedRoutes = routesResult.routes,
                                uploadingSessions = uploading,
                                rejectedSweeps = rejected,
                                pendingAiConfirmation = pending,
                                isStale = routesResult.isStale,
                                cancelledWorkOrderIds = cancelledWorkOrderIds,
                            )
                        }
                    }.catch { e ->
                        _uiState.value = SurveyHubUiState.Error(e.message ?: "Không tải được Khảo sát")
                    }.collect { state ->
                        _uiState.value = state
                    }
                }
        }
    }
