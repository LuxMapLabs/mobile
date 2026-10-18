package com.luxmap.feature.notification.data

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeNotificationRepository
    @Inject
    constructor() : NotificationRepository {
        private val items =
            MutableStateFlow(
                listOf(
                    NotificationItem(
                        "NTF-000001",
                        "work_order_assigned",
                        "Bạn được giao phiếu sửa chữa WO-1001",
                        "WO-1001 — Thay bóng đèn. Hạn 20/10/2026.",
                        "work_order",
                        "WO-1001",
                        "2026-10-18T08:00:00Z",
                        readAt = null,
                    ),
                    NotificationItem(
                        "NTF-000002",
                        "survey_returned",
                        "Khảo sát SWEEP-1 bị trả lại",
                        "Phiếu WO-900. Lý do: thiếu đoạn cuối tuyến.",
                        "survey_sweep",
                        "SWEEP-1",
                        "2026-10-17T10:00:00Z",
                        readAt = "2026-10-17T12:00:00Z",
                    ),
                ),
            )

        override suspend fun list(unreadOnly: Boolean): Result<NotificationPage> {
            val current = if (unreadOnly) items.value.filter { it.readAt == null } else items.value
            return Result.success(
                NotificationPage(current, total = current.size, unreadCount = items.value.count { it.readAt == null }),
            )
        }

        override suspend fun unreadCount(): Result<Int> = Result.success(items.value.count { it.readAt == null })

        override suspend fun markRead(notificationId: String): Result<Unit> {
            items.value =
                items.value.map {
                    if (it.notificationId == notificationId) it.copy(readAt = "2026-10-18T00:00:00Z") else it
                }
            return Result.success(Unit)
        }

        override suspend fun markAllRead(): Result<Unit> {
            items.value = items.value.map { it.copy(readAt = it.readAt ?: "2026-10-18T00:00:00Z") }
            return Result.success(Unit)
        }
    }
