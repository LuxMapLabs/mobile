package com.luxmap.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.Spacing

data class LuxSegment<T>(val value: T, val label: String, val icon: ImageVector? = null)

private val ChipIconSize = 18.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LuxSegmented(
    segments: List<LuxSegment<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val labelStyle =
        if (segments.size <= 2) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        segments.forEachIndexed { index, segment ->
            SegmentedButton(
                selected = segment.value == selected,
                onClick = { onSelect(segment.value) },
                enabled = enabled,
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = segments.size,
                        baseShape = MaterialTheme.shapes.small,
                    ),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        activeBorderColor = MaterialTheme.colorScheme.outline,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                        inactiveBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                label = { Text(text = segment.label, style = labelStyle, maxLines = 2) },
                icon = {
                    SegmentedButtonDefaults.Icon(
                        active = segment.value == selected,
                        inactiveContent =
                            segment.icon?.let { icon ->
                                {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                }
                            },
                    )
                },
            )
        }
    }
}

@Composable
fun LuxFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    enabled: Boolean = true,
    leadingDot: (@Composable () -> Unit)? = null,
) {
    val spoken = if (count != null) "$label, $count" else label
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.semantics { contentDescription = spoken },
        shape = MaterialTheme.shapes.small,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingDot?.invoke()
                Text(text = label, style = MaterialTheme.typography.labelMedium)
                if (count != null) {
                    Text(text = count.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        leadingIcon =
            if (selected) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(ChipIconSize),
                    )
                }
            } else {
                null
            },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
        border =
            FilterChipDefaults.filterChipBorder(
                enabled = enabled,
                selected = selected,
                borderColor = MaterialTheme.colorScheme.outline,
                selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
    )
}

@LuxPreviews
@Composable
private fun LuxSelectionPreview() {
    LuxMapTheme {
        var outcome by remember { mutableStateOf<String?>("present") }
        var unreadOnly by remember { mutableStateOf(true) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LuxSegmented(
                segments =
                    listOf(
                        LuxSegment("present", "Có sự cố"),
                        LuxSegment("absent", "Không có"),
                        LuxSegment("unknown", "Chưa rõ"),
                    ),
                selected = outcome,
                onSelect = { outcome = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LuxFilterChip(label = "Tất cả", selected = !unreadOnly, onClick = { unreadOnly = false })
                LuxFilterChip(label = "Chưa đọc", count = 3, selected = unreadOnly, onClick = { unreadOnly = true })
            }
        }
    }
}
