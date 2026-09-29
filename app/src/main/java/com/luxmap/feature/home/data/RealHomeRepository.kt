package com.luxmap.feature.home.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// Calls the real GET /auth/me + GET /work-orders (see UserApi/WorkOrdersApi) - no Room cache yet,
// same one-shot shape FakeHomeRepository always had. plannedSweepCount stays null: the backend
// has no survey-sweep API to answer it (see docs/Backend_API_Requirements_For_Mobile.docx).
@Singleton
class RealHomeRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val workOrdersApi: WorkOrdersApi,
    ) : HomeRepository {
        override fun observeHomeData(): Flow<HomeData> =
            flow {
                val userId = userApi.me().userId
                val items = workOrdersApi.list(assignedTo = userId).items
                emit(items.toHomeData())
            }

        private fun List<WorkOrderItemDto>.toHomeData(): HomeData {
            val today = LocalDate.now()
            val metrics =
                HomeMetrics(
                    assignedCount = count { it.woStatus == "assigned" },
                    inProgressCount = count { it.woStatus == "in_progress" },
                    overdueCount = count { it.isOverdue(today) },
                    plannedSweepCount = null,
                )
            val clusters =
                groupBy { it.communeId }
                    .map { (communeId, group) ->
                        WorkOrderCluster(
                            clusterLabel = communeId,
                            items = group.map { it.toSummaryItem() },
                        )
                    }
            return HomeData(metrics = metrics, clusters = clusters)
        }

        // A cancelled or already-finished order is never "overdue", no matter its due_date.
        private fun WorkOrderItemDto.isOverdue(today: LocalDate): Boolean {
            if (woStatus in TERMINAL_STATUSES) return false
            val due = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return false
            return due.isBefore(today)
        }

        private fun WorkOrderItemDto.toSummaryItem() =
            WorkOrderSummaryItem(
                workOrderId = workOrderId,
                woStatus = woStatus,
                dueDate = dueDate,
                priorityScore = priorityScore,
            )

        private companion object {
            val TERMINAL_STATUSES = setOf("done", "verified", "cancelled")
        }
    }
