package com.luxmap.feature.workorder.ui.completion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.SyncStatus
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.AppTopBar
import com.luxmap.core.ui.components.EvidencePicker
import com.luxmap.core.ui.components.InlineBanner
import com.luxmap.core.ui.components.InlineBannerTone
import com.luxmap.core.ui.components.MessageState
import com.luxmap.core.ui.components.OutcomeSegment
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.Requirement
import com.luxmap.core.ui.components.RequirementList
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail

@Composable
fun WorkOrderCompletionRoute(
    onBack: () -> Unit,
    onCaptureEvidence: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkOrderCompletionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    WorkOrderCompletionScreen(
        uiState = uiState,
        onBack = onBack,
        onReportNoteChanged = viewModel::onReportNoteChanged,
        onMaterialsUsedChanged = viewModel::onMaterialsUsedChanged,
        onFaultOutcomeSelected = viewModel::onFaultOutcomeSelected,
        onSubmit = viewModel::submit,
        onCaptureEvidence = onCaptureEvidence,
        onRetakeEvidence = { viewModel.retakeEvidence(onCaptureEvidence) },
        modifier = modifier,
    )
}

@Composable
fun WorkOrderCompletionScreen(
    uiState: WorkOrderCompletionUiState,
    onBack: () -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { AppTopBar(title = "Hoàn thành lệnh", onBack = onBack) },
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            when (uiState) {
                is WorkOrderCompletionUiState.Loading -> LoadingState()
                is WorkOrderCompletionUiState.Success ->
                    WorkOrderCompletionContent(
                        state = uiState,
                        onReportNoteChanged = onReportNoteChanged,
                        onMaterialsUsedChanged = onMaterialsUsedChanged,
                        onFaultOutcomeSelected = onFaultOutcomeSelected,
                        onSubmit = onSubmit,
                        onCaptureEvidence = onCaptureEvidence,
                        onRetakeEvidence = onRetakeEvidence,
                    )
                is WorkOrderCompletionUiState.Empty ->
                    MessageState(
                        icon = Icons.Filled.Assignment,
                        iconColors = SyncStatus.DONE.badgeColors(),
                        title = "Không tìm thấy lệnh này",
                        subtitle = "Lệnh có thể đã bị xoá hoặc không thuộc phạm vi của bạn.",
                        action = {},
                    )
                is WorkOrderCompletionUiState.Error ->
                    MessageState(
                        icon = Icons.Filled.CloudOff,
                        iconColors = SyncStatus.FAILED.badgeColors(),
                        title = uiState.message,
                        subtitle = "Kiểm tra kết nối mạng rồi thử lại.",
                        action = {},
                    )
            }
        }
    }
}

@Composable
private fun WorkOrderCompletionContent(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (!state.detail.reviewNote.isNullOrBlank()) {
            Text(
                text = "Manager yêu cầu bổ sung: ${state.detail.reviewNote}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.syncStatus()?.let { status ->
            StatusBadge(text = status.label(), colors = status.badgeColors())
        }
        when (state.detail.taskKind) {
            "repair" ->
                RepairCompletionSection(
                    state = state,
                    onReportNoteChanged = onReportNoteChanged,
                    onMaterialsUsedChanged = onMaterialsUsedChanged,
                    onCaptureEvidence = onCaptureEvidence,
                    onRetakeEvidence = onRetakeEvidence,
                )
            "inspection" ->
                InspectionCompletionSection(
                    state = state,
                    onReportNoteChanged = onReportNoteChanged,
                    onFaultOutcomeSelected = onFaultOutcomeSelected,
                )
            else ->
                Text(
                    text = "Loại lệnh này chưa hỗ trợ nộp qua ứng dụng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (state.submitError != null) {
            InlineBanner(message = state.submitError, tone = InlineBannerTone.ERROR)
        }
        PrimaryButton(
            text = "Nộp",
            onClick = onSubmit,
            enabled = state.canSubmit() && !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RepairCompletionSection(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    val hasPhoto = state.localEvidence != null
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = "Nghiệm thu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EvidencePicker(
            hasPhoto = hasPhoto,
            onTileClick = if (hasPhoto) onRetakeEvidence else onCaptureEvidence,
        )
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Kết quả sửa chữa") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${state.reportNote.trim().length} ký tự · tối thiểu 10",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.materialsUsed,
            onValueChange = onMaterialsUsedChanged,
            label = { Text("Vật tư đã dùng (không bắt buộc)") },
            modifier = Modifier.fillMaxWidth(),
        )
        RequirementList(
            items =
                listOf(
                    Requirement("Có ảnh sau sửa chữa", met = hasPhoto),
                    Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
                ),
        )
    }
}

@Composable
private fun InspectionCompletionSection(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
) {
    val faults = state.detail.faults
    val recordedCount = faults.count { state.faultOutcomes.containsKey(it.faultId) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(text = "$recordedCount/${faults.size} cột đã ghi nhận", style = MaterialTheme.typography.bodyMedium)
        faults.forEach { fault ->
            FaultOutcomeSection(
                fault = fault,
                selected = state.faultOutcomes[fault.faultId],
                onSelected = onFaultOutcomeSelected,
            )
        }
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Ghi chú kiểm tra") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${state.reportNote.trim().length} ký tự · tối thiểu 10",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RequirementList(
            items =
                listOf(
                    Requirement("Đã ghi nhận đủ kết quả kiểm tra từng cột", met = recordedCount == faults.size),
                    Requirement("Đã ghi chú kết quả (≥ 10 ký tự)", met = state.reportNote.trim().length >= 10),
                ),
        )
    }
}

@Composable
private fun FaultOutcomeSection(
    fault: WorkOrderFaultDetail,
    selected: String?,
    onSelected: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = fault.poleId ?: fault.faultId, style = MaterialTheme.typography.titleMedium)
        OutcomeSegment(selected = selected, onSelected = { outcome -> onSelected(fault.faultId, outcome) })
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag("work_order_completion_loading"))
}
