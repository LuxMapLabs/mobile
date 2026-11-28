package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun LuxConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Huỷ",
    destructive: Boolean = false,
    confirmModifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = onDismiss,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg).widthIn(max = 400.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = LuxTheme.colors.dialogContainer,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    val actionModifier = Modifier.fillMaxWidth()
                    if (destructive) {
                        LuxDestructiveButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            modifier = actionModifier.then(confirmModifier),
                        )
                    } else {
                        PrimaryButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            modifier = actionModifier.then(confirmModifier),
                        )
                    }
                    LuxTextButton(text = dismissLabel, onClick = onDismiss, modifier = actionModifier)
                }
            }
        }
    }
}

@Composable
fun LuxSubmittedDialog(
    onDismiss: () -> Unit,
    title: String = "Đã nộp báo cáo",
    message: String =
        "Báo cáo đã lưu trên máy và sẽ tự gửi lên khi có mạng. Quản lý sẽ duyệt báo cáo này.",
    confirmLabel: String = "Đã hiểu",
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .widthIn(max = 400.dp)
                    .testTag("submitted_dialog"),
            shape = MaterialTheme.shapes.extraLarge,
            color = LuxTheme.colors.dialogContainer,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Surface(
                    shape = CircleShape,
                    color = LuxTheme.colors.successContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = LuxTheme.colors.onSuccessContainer,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(
                    text = confirmLabel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("submitted_dialog_confirm"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LuxBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    contentModifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0)),
        containerColor = LuxTheme.colors.cardContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = close) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Đóng")
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .then(contentModifier)
                        .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}
