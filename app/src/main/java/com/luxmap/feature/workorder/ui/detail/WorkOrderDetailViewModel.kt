package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
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
class WorkOrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val detailRepository: WorkOrderDetailRepository,
        private val completionRepository: WorkOrderCompletionRepository,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderDetailUiState>(WorkOrderDetailUiState.Loading)
        val uiState: StateFlow<WorkOrderDetailUiState> = _uiState.asStateFlow()

        // One-shot: a survey work order's "Bắt đầu" should navigate into F03 (readiness check,
        // mandatory before capture per CLAUDE.md) exactly once per tap, not replay on every
        // recomposition the way a StateFlow would.
        private val _startedSurveyWorkOrderId = MutableSharedFlow<String>(extraBufferCapacity = 1)
        val startedSurveyWorkOrderId: SharedFlow<String> = _startedSurveyWorkOrderId.asSharedFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                combine(
                    detailRepository.observeWorkOrderDetail(workOrderId),
                    completionRepository.observeEvidence(workOrderId),
                    completionRepository.observeCompletion(workOrderId),
                    connectivityObserver.isOnline,
                ) { detail, evidence, completion, isOnline -> Quad(detail, evidence, completion, isOnline) }
                    .catch { e ->
                        _uiState.value = WorkOrderDetailUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                    }.collect { (detail, evidence, completion, isOnline) ->
                        _uiState.value =
                            if (detail == null) {
                                WorkOrderDetailUiState.Empty
                            } else {
                                val current = _uiState.value as? WorkOrderDetailUiState.Success
                                WorkOrderDetailUiState.Success(
                                    detail = detail,
                                    localEvidence = evidence,
                                    localCompletion = completion,
                                    isOnline = isOnline,
                                    reportNote = current?.reportNote ?: detail.reportNote.orEmpty(),
                                    materialsUsed = current?.materialsUsed ?: detail.materialsUsed.orEmpty(),
                                    faultOutcomes = current?.faultOutcomes ?: emptyMap(),
                                )
                            }
                    }
            }
        }

        fun start() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || current.isStarting) return
            _uiState.value = current.copy(isStarting = true, startError = null)
            viewModelScope.launch {
                detailRepository.start(workOrderId).fold(
                    onSuccess = { detail ->
                        val afterStart = _uiState.value
                        if (afterStart is WorkOrderDetailUiState.Success) {
                            _uiState.value = afterStart.copy(detail = detail, isStarting = false, startError = null)
                        }
                        if (detail.taskKind == "survey") {
                            _startedSurveyWorkOrderId.tryEmit(workOrderId)
                        }
                    },
                    onFailure = { e ->
                        val afterFailure = _uiState.value
                        if (afterFailure is WorkOrderDetailUiState.Success) {
                            _uiState.value =
                                afterFailure.copy(
                                    isStarting = false,
                                    startError = startErrorMessage(e),
                                )
                        }
                    },
                )
            }
        }

        fun onReportNoteChanged(value: String) {
            updateSuccess { it.copy(reportNote = value) }
        }

        fun onMaterialsUsedChanged(value: String) {
            updateSuccess { it.copy(materialsUsed = value) }
        }

        fun onFaultOutcomeSelected(
            faultId: String,
            outcome: String,
        ) {
            updateSuccess { it.copy(faultOutcomes = it.faultOutcomes + (faultId to outcome)) }
        }

        fun submit() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || !current.canSubmit() || current.isSubmitting) return
            _uiState.value = current.copy(isSubmitting = true, submitError = null)
            viewModelScope.launch {
                val faultOutcomes =
                    if (current.detail.taskKind == "inspection" && current.detail.faults.isNotEmpty()) {
                        current.detail.faults.map { fault ->
                            FaultOutcome(fault.faultId, current.faultOutcomes.getValue(fault.faultId))
                        }
                    } else {
                        null
                    }
                completionRepository.submitCompletion(
                    workOrderId = workOrderId,
                    taskKind = current.detail.taskKind,
                    reportNote = current.reportNote,
                    materialsUsed = current.materialsUsed.ifBlank { null },
                    faultOutcomes = faultOutcomes,
                )
                val afterSubmit = _uiState.value
                if (afterSubmit is WorkOrderDetailUiState.Success) {
                    _uiState.value = afterSubmit.copy(isSubmitting = false)
                }
            }
        }

        fun retakeEvidence(onReady: () -> Unit) {
            viewModelScope.launch {
                completionRepository.retakeEvidence(workOrderId)
                onReady()
            }
        }

        private fun updateSuccess(transform: (WorkOrderDetailUiState.Success) -> WorkOrderDetailUiState.Success) {
            val current = _uiState.value
            if (current is WorkOrderDetailUiState.Success) {
                _uiState.value = transform(current)
            }
        }

        private fun startErrorMessage(e: Throwable): String =
            if (e is IOException) "Mất kết nối. Vui lòng thử lại." else "Không bắt đầu được lệnh này"

        private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
