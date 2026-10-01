package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luxmap.core.theme.Danger600
import com.luxmap.core.theme.Gray500
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.Success600

enum class ReadinessCheckStatus { CHECKING, PASS, FAIL }

// fixHint is shown only on FAIL - a passed or still-checking row has nothing to tell the user to
// do yet.
data class ReadinessCheckItem(
    val label: String,
    val status: ReadinessCheckStatus,
    val fixHint: String? = null,
)

// Design System v2.0 §6.7 - one row per automatic check, each with its own
// Checking/Pass/Fail state and an inline fix hint on failure.
@Composable
fun CameraReadinessPanel(
    items: List<ReadinessCheckItem>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEach { item ->
            CameraReadinessRow(item)
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun CameraReadinessRow(item: ReadinessCheckItem) {
    val (icon, color) =
        when (item.status) {
            ReadinessCheckStatus.CHECKING -> Icons.Filled.HourglassEmpty to Gray500
            ReadinessCheckStatus.PASS -> Icons.Filled.Check to Success600
            ReadinessCheckStatus.FAIL -> Icons.Filled.Close to Danger600
        }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(Spacing.sm))
            Text(text = item.label, style = MaterialTheme.typography.bodyLarge, color = color)
        }
        if (item.status == ReadinessCheckStatus.FAIL && item.fixHint != null) {
            Text(
                text = item.fixHint,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.padding(start = Spacing.xl),
            )
        }
    }
}
