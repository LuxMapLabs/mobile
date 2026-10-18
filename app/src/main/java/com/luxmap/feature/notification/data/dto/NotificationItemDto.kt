package com.luxmap.feature.notification.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Checked directly against luxmap_backend/src/LuxMap.Modules.Notifications/NotificationContracts.cs
// (NotificationItem), read 2026-10-18. type/entityType stay plain String, never a
// @Serializable enum - an unrecognized value must decode safely, not throw (see
// NotificationType.notificationTypeFromWire, mapped at the UI layer like woStatus elsewhere).
@Serializable
data class NotificationItemDto(
    @SerialName("notification_id") val notificationId: String,
    val type: String,
    val title: String,
    val body: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null,
)
