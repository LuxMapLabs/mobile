package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.luxmap.core.theme.Spacing

internal const val MIN_REPORT_NOTE_LENGTH = 10

@Composable
internal fun CompletionFieldHeader(
    title: String,
    required: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Text(
            text = if (required) "Bắt buộc" else "Không bắt buộc",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ReportNoteField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    locked: Boolean,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CompletionFieldHeader(title = title, required = true)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            enabled = !locked,
            minLines = 3,
            maxLines = 6,
            supportingText = {
                Text("${value.trim().length}/$MIN_REPORT_NOTE_LENGTH ký tự tối thiểu")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
