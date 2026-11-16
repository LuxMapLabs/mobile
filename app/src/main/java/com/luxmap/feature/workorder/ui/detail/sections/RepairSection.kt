package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.EvidencePicker
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxSectionHeader
import java.io.File

@Composable
internal fun RepairCompletionSection(
    evidenceFilePath: String?,
    reportNote: String,
    materialsUsed: String,
    locked: Boolean,
    onReportNoteChanged: (String) -> Unit,
    onMaterialsUsedChanged: (String) -> Unit,
    onCaptureEvidence: () -> Unit,
    onRetakeEvidence: () -> Unit,
) {
    val hasPhoto = evidenceFilePath != null
    val noteValid = reportNote.trim().length >= MIN_REPORT_NOTE_LENGTH
    val requiredDone = listOf(hasPhoto, noteValid).count { it }
    val focusManager = LocalFocusManager.current
    var showEvidencePreview by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LuxSectionHeader(title = "Nghiệm thu", modifier = Modifier.weight(1f))
            Text(
                text = "$requiredDone/2 bắt buộc",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LuxCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CompletionFieldHeader(title = "Ảnh sau sửa chữa", required = true)
                Text(
                    text = if (hasPhoto) "1 ảnh · chạm vào ảnh để xem" else "Chụp rõ chóa đèn sau khi sửa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EvidencePicker(
                    hasPhoto = hasPhoto,
                    filePath = evidenceFilePath,
                    onTileClick =
                        when {
                            hasPhoto -> ({ showEvidencePreview = true })
                            locked -> ({})
                            else -> onCaptureEvidence
                        },
                    enabled = !locked || hasPhoto,
                )
            }
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

    if (showEvidencePreview && evidenceFilePath != null) {
        EvidencePreviewDialog(
            filePath = evidenceFilePath,
            showRetake = !locked,
            onDismiss = { showEvidencePreview = false },
            onRetake = {
                showEvidencePreview = false
                onRetakeEvidence()
            },
        )
    }
}

@Composable
private fun EvidencePreviewDialog(
    filePath: String,
    showRetake: Boolean,
    onDismiss: () -> Unit,
    onRetake: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(Radius.card))
                    .padding(Spacing.md),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Đóng", tint = Color.White)
                }
            }
            AsyncImage(
                model = File(filePath),
                contentDescription = "Ảnh sau sửa chữa",
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(Radius.card)),
            )
            if (showRetake) {
                Spacer(Modifier.height(Spacing.md))
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                ) {
                    Icon(imageVector = Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Chụp lại")
                }
            }
        }
    }
}
