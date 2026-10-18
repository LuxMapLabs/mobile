package com.luxmap.feature.notification.ui

data class NotificationListRowState(
    val notificationId: String,
    // Raw wire value (e.g. "work_order_assigned") - mapped to an icon via
    // notificationTypeFromWire() in NotificationListScreen, same convention as woStatus elsewhere.
    val type: String,
    val title: String,
    val body: String,
    // Raw wire value (e.g. "work_order") - only "work_order" is navigable in v1, see CLAUDE.md.
    val entityType: String,
    val entityId: String,
    val createdAt: String,
    val isRead: Boolean,
)

sealed interface NotificationListUiState {
    data object Loading : NotificationListUiState

    // total defaults to items.size (not a separate "unknown" state) so a screen/test that only
    // cares about the items list doesn't have to know about paging - the list footer only shows
    // once total genuinely exceeds what was loaded.
    data class Success(
        val items: List<NotificationListRowState>,
        val total: Int = items.size,
    ) : NotificationListUiState

    data object Empty : NotificationListUiState

    data class Error(val message: String) : NotificationListUiState
}
