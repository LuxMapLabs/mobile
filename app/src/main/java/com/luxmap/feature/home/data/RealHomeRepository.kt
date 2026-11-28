package com.luxmap.feature.home.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.offline.LocalOfflineSnapshotEntity
import com.luxmap.core.offline.OfflineSnapshotDao
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

// Calls the real GET /auth/me + GET /work-orders (see UserApi/WorkOrdersApi). The last answer is
// kept on the device so Home still lists the engineer's work when the signal drops.
// plannedSweepCount stays null: the backend
// has no survey-sweep API to answer it (see docs/Backend_API_Requirements_For_Mobile.docx).
@Singleton
class RealHomeRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val workOrdersApi: WorkOrdersApi,
        private val snapshots: OfflineSnapshotDao,
    ) : HomeRepository {
        override fun observeHomeData(): Flow<HomeData> =
            flow {
                val snapshot =
                    try {
                        val user = userApi.me()
                        // "Việc hôm nay" only means open work - without this filter the server returns
                        // every work order ever assigned to this user, including done/verified/cancelled
                        // ones from the past, which then show up mixed into today's task list.
                        val items = workOrdersApi.list(assignedTo = user.userId, woStatus = ACTIVE_STATUSES).items
                        HomeSnapshot(user, items).also {
                            snapshots.put(
                                LocalOfflineSnapshotEntity(SNAPSHOT_KEY, json.encodeToString(it), Instant.now()),
                            )
                        }
                    } catch (e: IOException) {
                        val saved = snapshots.get(SNAPSHOT_KEY) ?: throw e
                        emit(json.decodeFromString<HomeSnapshot>(saved.json).toHomeData(cachedAt = saved.fetchedAt))
                        return@flow
                    }
                emit(snapshot.toHomeData(cachedAt = null))
            }

        private fun HomeSnapshot.toHomeData(cachedAt: Instant?): HomeData =
            items.toHomeData(user).copy(cachedAt = cachedAt)

        private fun List<WorkOrderItemDto>.toHomeData(user: CurrentUserResponseDto): HomeData {
            val communeNames = user.communes.associate { it.communeId to it.name }
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
                            items = group.map { it.toSummaryItem().copy(communeName = communeNames[communeId]) },
                        )
                    }
            return HomeData(metrics = metrics, clusters = clusters, fullName = user.fullName)
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
                taskKind = taskKind,
                communeId = communeId,
                title = title,
                scheduledDate = scheduledDate,
                faultCount = faultIds.size,
            )

        @Serializable
        private data class HomeSnapshot(
            val user: CurrentUserResponseDto,
            val items: List<WorkOrderItemDto>,
        )

        private companion object {
            const val SNAPSHOT_KEY = "home"
            val json = Json { ignoreUnknownKeys = true }
            const val ACTIVE_STATUSES = "assigned,in_progress"
            val TERMINAL_STATUSES = setOf("done", "verified", "cancelled")
        }
    }
