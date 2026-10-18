package com.luxmap.feature.notification.data

import com.luxmap.core.network.NotificationsApi
import com.luxmap.feature.notification.data.dto.NotificationItemDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealNotificationRepository
    @Inject
    constructor(
        private val api: NotificationsApi,
    ) : NotificationRepository {
        override suspend fun list(unreadOnly: Boolean): Result<NotificationPage> =
            runCatching {
                val dto = api.list(unreadOnly = if (unreadOnly) true else null)
                NotificationPage(
                    items = dto.items.map { it.toDomain() },
                    total = dto.total,
                    unreadCount = dto.unreadCount,
                )
            }

        override suspend fun unreadCount(): Result<Int> = runCatching { api.unreadCount().unreadCount }

        override suspend fun markRead(notificationId: String): Result<Unit> = runCatching { api.markRead(notificationId) }

        override suspend fun markAllRead(): Result<Unit> = runCatching { api.markAllRead() }

        private fun NotificationItemDto.toDomain() =
            NotificationItem(notificationId, type, title, body, entityType, entityId, createdAt, readAt)
    }
