package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.feature.workorder.data.entity.LocalWorkOrderEvidenceEntity

@Composable
internal fun RepairCompletionSection(
    photos: List<LocalWorkOrderEvidenceEntity>,
    reportNote: String,
    materialsUsed: String,
    locked: Boolean,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRemoveEvidence: (String) -> Unit,
) {
    val hasPhoto = photos.isNotEmpty()
    val noteValid = reportNote.trim().length >= MIN_REPORT_NOTE_LENGTH
    val requiredDone = listOf(hasPhoto, noteValid).count { it }
    val focusManager = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CountedSectionHeader(title = "Nghiệm thu", count = "$requiredDone/2 bắt buộc")
        LuxCard(modifier = Modifier.fillMaxWidth()) {
            CompletionEvidenceSection(
                title = "Ảnh sau sửa chữa",
                required = true,
                photos = photos,
                editable = !locked,
                onAdd = onCaptureEvidence,
                onRemove = onRemoveEvidence,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.lg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            ReportNoteField(
                title = "Kết quả sửa chữa",
                value = reportNote,
                onValueChange = onReportNoteChanged,
                placeholder = "VD: Thay bóng LED, kiểm tra lại dây nguồn",
                locked = locked,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.lg),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CompletionFieldHeader(title = "Vật tư đã thay", required = false)
                OutlinedTextField(
                    value = materialsUsed,
                    onValueChange = onMaterialsUsedChanged,
                    placeholder = { Text("VD: 1 bóng LED 100W") },
                    enabled = !locked,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
