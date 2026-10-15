package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    // IntrinsicSize.Min on the row + fillMaxHeight on each button makes every button match the
    // tallest one - without it, "Chưa xác định" wraps to 2 lines while the other two stay on 1,
    // leaving the row looking uneven.
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
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
                modifier = Modifier.fillMaxHeight().defaultMinSize(minHeight = 48.dp),
            ) {
                Text(text = text)
            }
        }
    }
}
