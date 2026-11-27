package com.luxmap.core.ui.components

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.BodyStrongTextStyle
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Radius
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.clearFocusOnTapOutside

// Used by F09 (WorkOrderDetailScreen) for the generic POST /work-orders/{id}/request-cancellation
// (CLAUDE.md WO-CANCEL-REQUEST).
private val CANCEL_REASON_OPTIONS =
    listOf(
        "Thời tiết không cho phép",
        "Không tiếp cận được hiện trường",
        "Trùng với lệnh khác",
        "Lý do khác",
    )

@Composable
fun CancelRequestSheet(
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSend: (reason: String) -> Unit,
) {
    var selectedReason by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    LuxBottomSheet(
        title = "Yêu cầu hủy lệnh",
        onDismiss = onDismiss,
        contentModifier = Modifier.clearFocusOnTapOutside(),
    ) {
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
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        LuxActionPair(
            secondary = { actionModifier ->
                LuxSecondaryButton(text = "Quay lại", onClick = onDismiss, modifier = actionModifier)
            },
            primary = { actionModifier ->
                LuxDestructiveButton(
                    text = if (isSubmitting) "Đang gửi..." else "Gửi yêu cầu",
                    onClick = {
                        selectedReason?.let { reason ->
                            onSend(if (note.isBlank()) reason else "$reason - ${note.trim()}")
                        }
                    },
                    enabled = selectedReason != null && !isSubmitting,
                    modifier = actionModifier,
                )
            },
        )
    }
}

@Composable
private fun CancelReasonRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val outColors = AssetCondition.OUT.badgeColors()
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
