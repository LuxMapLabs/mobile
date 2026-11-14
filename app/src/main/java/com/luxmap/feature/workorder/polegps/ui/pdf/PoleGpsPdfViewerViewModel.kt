package com.luxmap.feature.workorder.polegps.ui.pdf

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxmap.feature.workorder.polegps.data.AttachmentStatus
import com.luxmap.feature.workorder.polegps.data.PoleGpsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PoleGpsPdfViewerViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: PoleGpsRepository,
    ) : ViewModel() {
        private val workOrderId: String = checkNotNull(savedStateHandle["workOrderId"])
        private val attachmentId: String = checkNotNull(savedStateHandle["attachmentId"])

        val state: StateFlow<PoleGpsPdfViewerUiState> =
            repository
                .observeAttachments(workOrderId)
                .map { items -> toUiState(items.firstOrNull { it.attachmentId == attachmentId }) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, PoleGpsPdfViewerUiState.Loading)

        fun onRetry() {
            viewModelScope.launch { repository.retryAttachment(workOrderId, attachmentId) }
        }

        private fun toUiState(
            attachment: com.luxmap.feature.workorder.polegps.data.PoleGpsAttachment?,
        ): PoleGpsPdfViewerUiState =
            when {
                attachment == null -> PoleGpsPdfViewerUiState.Empty
                attachment.status == AttachmentStatus.READY && attachment.localPath != null ->
                    PoleGpsPdfViewerUiState.Ready(attachment.fileName, attachment.localPath)
                attachment.status == AttachmentStatus.FAILED ->
                    PoleGpsPdfViewerUiState.Error("Tải file lỗi, thử lại", canRetry = true)
                else -> PoleGpsPdfViewerUiState.Error("Đang tải, hãy thử lại sau", canRetry = false)
            }
    }
