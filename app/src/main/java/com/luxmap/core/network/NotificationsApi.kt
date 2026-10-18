package com.luxmap.core.network

import com.luxmap.feature.notification.data.dto.NotificationPageDto
import com.luxmap.feature.notification.data.dto.UnreadCountDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Route checked against NotificationsController.cs: [Route("api/v{version:apiVersion}/notifications")].
interface NotificationsApi {
    @GET("api/v1/notifications")
    suspend fun list(
        @Query("unread_only") unreadOnly: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
    ): NotificationPageDto

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): UnreadCountDto

    @POST("api/v1/notifications/{notification_id}/read")
    suspend fun markRead(
        @Path("notification_id") notificationId: String,
    )

    @POST("api/v1/notifications/read-all")
    suspend fun markAllRead()
}
