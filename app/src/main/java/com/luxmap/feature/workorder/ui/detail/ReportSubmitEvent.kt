package com.luxmap.feature.workorder.ui.detail

sealed interface ReportSubmitEvent {
    data object SavedLocally : ReportSubmitEvent

    data class Failed(val reason: String) : ReportSubmitEvent
}
