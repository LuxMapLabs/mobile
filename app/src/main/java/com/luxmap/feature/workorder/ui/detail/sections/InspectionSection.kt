package com.luxmap.feature.workorder.ui.detail.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.LuxSegment
import com.luxmap.core.ui.components.LuxSegmented
import com.luxmap.feature.workorder.data.WorkOrderFaultDetail
import com.luxmap.feature.workorder.ui.detail.faultTypeLabel

private val OUTCOME_SEGMENTS =
    listOf(
        LuxSegment("fault_present", "Có sự cố"),
        LuxSegment("fault_absent", "Không có"),
        LuxSegment("inconclusive", "Chưa rõ"),
    )

@Composable
internal fun InspectionSection(
    faults: List<WorkOrderFaultDetail>,
    selectedOutcomes: Map<String, String>,
    reportNote: String,
    editable: Boolean,
    locked: Boolean,
    onNavigateFault: (lat: Double, lng: Double) -> Unit,
    onFaultOutcomeSelected: (faultId: String, outcome: String) -> Unit,
    onReportNoteChanged: (String) -> Unit,
    onOpenPoleOnMap: ((poleId: String, lat: Double, lng: Double) -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        FaultListCard(
            title = "Sự cố cần kiểm tra",
            faults = faults,
            onNavigateFault = onNavigateFault,
            onOpenPoleOnMap = onOpenPoleOnMap,
            footer = { fault ->
                if (editable) {
                    FaultFooter {
                        LuxSegmented(
                            segments = OUTCOME_SEGMENTS,
                            selected = selectedOutcomes[fault.faultId],
                            onSelect = { outcome -> onFaultOutcomeSelected(fault.faultId, outcome) },
                            enabled = !locked,
                            modifier =
                                Modifier.semantics {
                                    contentDescription = "Kết quả kiểm tra ${faultTypeLabel(fault.faultType)} " +
                                        fault.poleId.orEmpty()
                                },
                        )
                    }
                } else {
                    fault.inspectionOutcome?.let { outcome ->
                        FaultFooter { InspectionOutcomeBadge(outcome) }
                    }
                }
            },
        )
        if (editable) {
            ReportNoteField(
                title = "Ghi chú kiểm tra",
                value = reportNote,
                onValueChange = onReportNoteChanged,
                placeholder = "VD: Cảm biến mất tín hiệu từ 08/10",
                locked = locked,
            )
        }
    }
}
