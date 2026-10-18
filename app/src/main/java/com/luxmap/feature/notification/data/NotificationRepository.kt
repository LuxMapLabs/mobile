package com.luxmap.feature.notification.data

// type/entityType stay the raw wire String here too (same convention as WorkOrderItem.woStatus) -
// mapped to NotificationType only where it's displayed (NotificationListScreen), not here.
data class NotificationItem(
    val notificationId: String,
    val type: String,
    val title: String,
    val body: String,
    val entityType: String,
    val entityId: String,
    val createdAt: String,
    val readAt: String?,
)

data class NotificationPage(
    val items: List<NotificationItem>,
    val total: Int,
    val unreadCount: Int,
)

interface NotificationRepository {
    suspend fun list(unreadOnly: Boolean = false): Result<NotificationPage>

    suspend fun unreadCount(): Result<Int>

    suspend fun markRead(notificationId: String): Result<Unit>

    suspend fun markAllRead(): Result<Unit>
}
