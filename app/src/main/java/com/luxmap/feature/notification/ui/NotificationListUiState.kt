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

    data class Success(val items: List<NotificationListRowState>) : NotificationListUiState

    data object Empty : NotificationListUiState

    data class Error(val message: String) : NotificationListUiState
}
