package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val OUTCOME_OPTIONS =
    listOf(
        "fault_present" to "Có sự cố",
        "fault_absent" to "Không có",
        "inconclusive" to "Chưa xác định",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutcomeSegment(
    selected: String?,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        OUTCOME_OPTIONS.forEachIndexed { index, (value, text) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelected(value) },
                shape = SegmentedButtonDefaults.itemShape(index, OUTCOME_OPTIONS.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.onSurface,
                        activeContentColor = MaterialTheme.colorScheme.surface,
                    ),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = text)
            }
        }
    }
}
