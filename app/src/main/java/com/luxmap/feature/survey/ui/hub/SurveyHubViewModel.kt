package com.luxmap.feature.survey.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.core.theme.SyncStatus
import com.luxmap.feature.survey.data.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val SHOW_UNCONFIRMED_HUB_CARDS = false

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

        init {
            load()
        }

        fun onRetry() {
            if (_uiState.value !is SurveyHubUiState.Error) return
            _uiState.value = SurveyHubUiState.Loading
            load()
        }

        // No reachable "Bat dau" for a survey work order otherwise: Home deliberately hides
        // survey orders (see HomeScreen.kt), and "Kiem tra san sang" below only checks local
        // device readiness for the video flow - it never calls this API. Reuses the same
        // generic POST /work-orders/{id}/start WorkOrderDetailScreen (F09) already uses.
        fun onStartRoute(workOrderId: String) {
            val current = _uiState.value as? SurveyHubUiState.Success ?: return
            if (current.startingWorkOrderId != null) return
            _uiState.value = current.copy(startingWorkOrderId = workOrderId, startError = null)
            viewModelScope.launch {
                repository.startRoute(workOrderId).fold(
                    onSuccess = { load() },
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
