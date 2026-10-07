package com.luxmap.feature.workorder.ui.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.core.network.ConnectivityObserver
import com.luxmap.feature.workorder.data.WorkOrderCompletionRepository
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import com.luxmap.feature.workorder.data.sync.FaultOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkOrderCompletionViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val workOrderDetailRepository: WorkOrderDetailRepository,
        private val completionRepository: WorkOrderCompletionRepository,
        private val connectivityObserver: ConnectivityObserver,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderCompletionUiState>(WorkOrderCompletionUiState.Loading)
        val uiState: StateFlow<WorkOrderCompletionUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                combine(
                    workOrderDetailRepository.observeWorkOrderDetail(workOrderId),
                    completionRepository.observeEvidence(workOrderId),
                    completionRepository.observeCompletion(workOrderId),
                    connectivityObserver.isOnline,
                ) { detail, evidence, completion, isOnline -> Quad(detail, evidence, completion, isOnline) }
                    .catch { e ->
                        _uiState.value = WorkOrderCompletionUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                    }.collect { (detail, evidence, completion, isOnline) ->
                        _uiState.value =
                            if (detail == null) {
                                WorkOrderCompletionUiState.Empty
                            } else {
                                val current = _uiState.value as? WorkOrderCompletionUiState.Success
                                WorkOrderCompletionUiState.Success(
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
            if (current !is WorkOrderCompletionUiState.Success || !current.canSubmit() || current.isSubmitting) return
            _uiState.value = current.copy(isSubmitting = true, submitError = null)
            viewModelScope.launch {
                val outcomes =
                    if (current.detail.taskKind == "inspection" && current.detail.faults.isNotEmpty()) {
                        current.detail.faults.map {
                            FaultOutcome(
                                it.faultId,
                                current.faultOutcomes[it.faultId].orEmpty(),
                            )
                        }
                    } else {
                        null
                    }
                completionRepository.submitCompletion(
                    workOrderId = workOrderId,
                    taskKind = current.detail.taskKind,
                    reportNote = current.reportNote.trim(),
                    materialsUsed = current.materialsUsed.trim().ifBlank { null },
                    faultOutcomes = outcomes,
                )
                val afterSubmit = _uiState.value
                if (afterSubmit is WorkOrderCompletionUiState.Success) {
                    _uiState.value = afterSubmit.copy(isSubmitting = false)
                }
            }
        }

        private fun updateSuccess(
            transform: (WorkOrderCompletionUiState.Success) -> WorkOrderCompletionUiState.Success,
        ) {
            val current = _uiState.value
            if (current is WorkOrderCompletionUiState.Success) {
                _uiState.value = transform(current)
            }
        }

        private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
