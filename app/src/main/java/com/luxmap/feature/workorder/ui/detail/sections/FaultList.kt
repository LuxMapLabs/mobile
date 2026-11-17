package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.luxmap.core.theme.AssetCondition
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing
import com.luxmap.core.theme.badgeColors
import com.luxmap.core.ui.components.LuxCard
import com.luxmap.core.ui.components.LuxListItem
import com.luxmap.core.ui.components.StatusBadge
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.data.hasLocation
import com.luxmap.feature.workorder.ui.detail.faultTypeLabel
import com.luxmap.feature.workorder.ui.detail.inspectionOutcomeLabel

@Composable
internal fun FaultListCard(
    title: String,
    faults: List<WorkOrderFaultDetail>,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (WorkOrderFaultDetail) -> Unit = {},
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        CountedSectionHeader(title = title, count = "${faults.size} sự cố")
        if (faults.isEmpty()) {
            LuxCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Không có sự cố nào trong lệnh này",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LuxCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = Spacing.xs)) {
                faults.forEachIndexed { index, fault ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    FaultItem(fault = fault, onNavigate = { onNavigateFault(fault.lat, fault.lng) })
                    footer(fault)
                }
            }
        }
    }
}

@Composable
internal fun FaultFooter(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md)) {
        content()
    }
}

@Composable
private fun FaultItem(
    fault: WorkOrderFaultDetail,
    onNavigate: () -> Unit,
) {
    LuxListItem(
        headline = faultTypeLabel(fault.faultType),
        supporting = listOfNotNull(fault.poleId, fault.faultId).joinToString("\n"),
        leadingIcon = faultTypeIcon(fault.faultType),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (fault.hasLocation()) {
                    IconButton(onClick = onNavigate, modifier = Modifier.size(Dimens.minTouchTarget)) {
                        Icon(
                            imageVector = Icons.Filled.Navigation,
                            contentDescription = "Điều hướng",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Text(
                        text = "Chưa có toạ độ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private fun faultTypeIcon(faultType: String): ImageVector =
    when (faultType) {
        "lamp_out" -> Icons.Filled.Lightbulb
        "lamp_dim" -> Icons.Filled.WbTwilight
        "segment_outage" -> Icons.Filled.PowerOff
        "node_offline" -> Icons.Filled.SensorsOff
        "runtime_decline" -> Icons.Filled.TrendingDown
        else -> Icons.Filled.ReportProblem
    }

@Composable
internal fun InspectionOutcomeBadge(outcome: String) {
    val (condition, icon) =
        when (outcome) {
            "fault_present" -> AssetCondition.OUT to Icons.Filled.Error
            "fault_absent" -> AssetCondition.NORMAL to Icons.Filled.CheckCircle
            else -> AssetCondition.UNKNOWN to Icons.Filled.Help
        }
    StatusBadge(text = inspectionOutcomeLabel(outcome), colors = condition.badgeColors(), icon = icon)
}
