package com.luxmap.feature.notification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnreadCountDto(
    @SerialName("unread_count") val unreadCount: Int,
)
