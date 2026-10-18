package com.luxmap.feature.notification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// NotificationPage in NotificationContracts.cs - one poll of the list refreshes both the list
// and the badge (unread_count rides along), so this is its own shape, not the shared
// PagedResultDto<T> (that one has no unread_count field).
@Serializable
data class NotificationPageDto(
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
    @SerialName("unread_count") val unreadCount: Int,
    val items: List<NotificationItemDto>,
)
