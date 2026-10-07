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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.theme.label
import com.luxmap.core.ui.components.ErrorBanner
import com.luxmap.core.ui.components.PrimaryButton
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.ui.detail.inspectionOutcomeLabel

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

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text("Hoàn thành lệnh") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
            )
        },
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
                is WorkOrderCompletionUiState.Empty -> MessageState("Không tìm thấy lệnh này")
                is WorkOrderCompletionUiState.Error -> MessageState(uiState.message)
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
        if (state.detail.taskKind == "repair") {
            RepairCompletionSection(
                state = state,
                onReportNoteChanged = onReportNoteChanged,
                onMaterialsUsedChanged = onMaterialsUsedChanged,
                onCaptureEvidence = onCaptureEvidence,
                onRetakeEvidence = onRetakeEvidence,
            )
        } else {
            InspectionCompletionSection(
                state = state,
                onReportNoteChanged = onReportNoteChanged,
                onFaultOutcomeSelected = onFaultOutcomeSelected,
            )
        }
        if (state.submitError != null) {
            ErrorBanner(message = state.submitError)
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (state.localEvidence == null) {
            PrimaryButton(text = "Chụp ảnh sau", onClick = onCaptureEvidence, modifier = Modifier.fillMaxWidth())
        } else {
            Text(text = "Đã chụp ảnh sau", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(text = "Chụp lại", onClick = onRetakeEvidence, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            value = state.reportNote,
            onValueChange = onReportNoteChanged,
            label = { Text("Kết quả sửa chữa") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.materialsUsed,
            onValueChange = onMaterialsUsedChanged,
            label = { Text("Vật tư đã dùng (không bắt buộc)") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InspectionCompletionSection(
    state: WorkOrderCompletionUiState.Success,
    onReportNoteChanged: (String) -> Unit,
    onFaultOutcomeSelected: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        state.detail.faults.forEach { fault ->
            FaultOutcomeRow(
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
    }
}

@Composable
private fun FaultOutcomeRow(
    fault: WorkOrderFaultDetail,
    selected: String?,
    onSelected: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(text = fault.poleId ?: fault.faultId, style = MaterialTheme.typography.titleMedium)
        listOf("fault_present", "fault_absent", "inconclusive").forEach { outcome ->
            PrimaryButton(
                text = inspectionOutcomeLabel(outcome) + if (selected == outcome) " ✓" else "",
                onClick = { onSelected(fault.faultId, outcome) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
}

@Composable
private fun BoxScope.MessageState(text: String) {
    Text(text = text, modifier = Modifier.align(Alignment.Center).padding(Spacing.lg))
}
