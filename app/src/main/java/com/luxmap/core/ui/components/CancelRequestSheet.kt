package com.luxmap.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.TitleTextStyle
import com.luxmap.core.theme.badgeColors

// Shared by F09 (WorkOrderDetailScreen) and the Survey Hub route cards - both call the same
// generic POST /work-orders/{id}/request-cancellation, so the reason sheet and its trigger
// button are not duplicated per feature (CLAUDE.md WO-CANCEL-REQUEST).
@Composable
fun CancelRequestButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text("Yêu cầu hủy")
    }
}

private val CANCEL_REASON_OPTIONS =
    listOf(
        "Thời tiết không cho phép",
        "Không tiếp cận được hiện trường",
        "Trùng với lệnh khác",
        "Lý do khác",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelRequestSheet(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSend: (reason: String) -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(text = "Yêu cầu hủy lệnh", style = TitleTextStyle, color = MaterialTheme.colorScheme.onSurface)
            Text(text = "Lý do", style = BodyStrongTextStyle, color = MaterialTheme.colorScheme.onSurface)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CANCEL_REASON_OPTIONS.forEach { reason ->
                    CancelReasonRow(
                        label = reason,
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                    )
                }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Ghi chú thêm (không bắt buộc)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.minTouchTarget),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("Quay lại")
                }
                Button(
                    onClick = {
                        val reason = selectedReason ?: return@Button
                        onSend(if (note.isBlank()) reason else "$reason - ${note.trim()}")
                    },
                    enabled = selectedReason != null && !isSubmitting,
                    modifier = Modifier.weight(1.5f).heightIn(min = Dimens.minTouchTarget),
                    shape = RoundedCornerShape(Radius.control),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(if (isSubmitting) "Đang gửi..." else "Gửi yêu cầu")
                }
            }
        }
    }
}

@Composable
private fun CancelReasonRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outColors = AssetCondition.OUT.badgeColors(isDark = false)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.minTouchTarget)
                .background(
                    if (selected) outColors.background else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(Radius.chip),
                )
                .border(if (selected) 2.dp else 0.dp, outColors.text, RoundedCornerShape(Radius.chip))
                .selectable(selected = selected, onClick = onClick)
                .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = BodyStrongTextStyle,
            color = if (selected) outColors.text else MaterialTheme.colorScheme.onSurface,
        )
    }
}
