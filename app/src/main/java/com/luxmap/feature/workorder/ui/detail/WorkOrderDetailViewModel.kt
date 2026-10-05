package com.luxmap.feature.workorder.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.data.WorkOrderDetailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class WorkOrderDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: WorkOrderDetailRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle[WORK_ORDER_ID_ARG])

        private val _uiState = MutableStateFlow<WorkOrderDetailUiState>(WorkOrderDetailUiState.Loading)
        val uiState: StateFlow<WorkOrderDetailUiState> = _uiState.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                _uiState.value = WorkOrderDetailUiState.Loading
                repository
                    .observeWorkOrderDetail(workOrderId)
                    .catch { e ->
                        _uiState.value = WorkOrderDetailUiState.Error(e.message ?: "Không tải được dữ liệu lệnh")
                    }.collect { detail ->
                        _uiState.value =
                            if (detail == null) {
                                WorkOrderDetailUiState.Empty
                            } else {
                                WorkOrderDetailUiState.Success(detail = detail)
                            }
                    }
            }
        }

        fun start() {
            val current = _uiState.value
            if (current !is WorkOrderDetailUiState.Success || current.isStarting) return
            _uiState.value = current.copy(isStarting = true, startError = null)
            viewModelScope.launch {
                repository.start(workOrderId).fold(
                    onSuccess = { detail -> _uiState.value = WorkOrderDetailUiState.Success(detail = detail) },
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

        private fun startErrorMessage(e: Throwable): String =
            if (e is IOException) "Mất kết nối. Vui lòng thử lại." else "Không bắt đầu được lệnh này"

        companion object {
            const val WORK_ORDER_ID_ARG = "workOrderId"
        }
    }
