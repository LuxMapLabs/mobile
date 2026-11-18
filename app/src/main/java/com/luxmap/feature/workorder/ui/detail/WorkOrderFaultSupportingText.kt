package com.luxmap.feature.workorder.ui.detail

import com.luxmap.feature.workorder.data.WorkOrderFaultDetail

internal fun faultSupportingText(fault: WorkOrderFaultDetail): String =
    buildList {
        listOf(
            "Tủ điện" to fault.cabinetId,
            "Node" to fault.originNodeId,
            "Mạch" to fault.feederId,
            "Mã lỗi" to fault.iotAnomalyCode,
        ).forEach { (label, value) ->
            value?.trim()?.takeIf { it.isNotEmpty() }?.let { add("$label: $it") }
        }
        fault.poleId?.let { add(it) }
        add(fault.faultId)
    }.joinToString("\n")
