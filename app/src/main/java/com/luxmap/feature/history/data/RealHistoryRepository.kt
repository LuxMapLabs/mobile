package com.luxmap.feature.history.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.security.TokenStore
import com.luxmap.feature.history.data.dao.HistoryCacheDao
import com.luxmap.feature.history.data.entity.LocalHistoryItemEntity
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val CLOSED_STATUSES = "done,verified,cancelled"
private const val CLOSED_SORT = "-closed_at"

@Singleton
class RealHistoryRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val workOrdersApi: WorkOrdersApi,
        private val cacheDao: HistoryCacheDao,
        private val tokenStore: TokenStore,
    ) : HistoryRepository {
        override fun observeHistory(): Flow<HistoryResult> =
            flow {
                val sessionId = tokenStore.identitySessionId() ?: throw CancellationException("No active session")
                var ownerUserId = tokenStore.cachedUserId()
                try {
                    val me = userApi.me()
                    val userId = me.userId
                    val communeNames = me.communes.associate { it.communeId to it.name }
                    ownerUserId = userId
                    if (!tokenStore.saveCachedUserIdForSession(userId, sessionId)) {
                        throw CancellationException("Session changed")
                    }
                    val items =
                        workOrdersApi.list(
                            assignedTo = userId,
                            woStatus = CLOSED_STATUSES,
                            sort = CLOSED_SORT,
                        ).items.pendingReviewFirst()
                    ensureSession(sessionId)
                    val fetchedAt = Instant.now()
                    cacheDao.replaceAll(
                        userId,
                        items.mapIndexed {
                                index,
                                dto,
                            ->
                            dto.toEntity(userId, index, fetchedAt.toEpochMilli(), communeNames[dto.communeId])
                        },
                    )
                    emit(
                        HistoryResult(
                            items.map { it.toDomain(communeNames[it.communeId]) },
                            isStale = false,
                            syncedAt = fetchedAt,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!e.isTemporaryFailure()) throw e
                    val owner = ownerUserId
                    val cached =
                        if (owner != null && tokenStore.cachedUserId() == owner) cacheDao.all(owner) else emptyList()
                    ensureSession(sessionId)
                    if (cached.isEmpty()) throw e
                    val syncedAt = cached.firstNotNullOfOrNull { it.fetchedAt }?.let(Instant::ofEpochMilli)
                    emit(HistoryResult(cached.map { it.toDomain() }, isStale = true, syncedAt = syncedAt))
                }
            }

        private suspend fun ensureSession(expectedSessionId: String) {
            if (tokenStore.identitySessionId() != expectedSessionId) throw CancellationException("Session changed")
        }

        private fun Exception.isTemporaryFailure(): Boolean =
            this is IOException || this is HttpException && (code() >= 500 || code() == 408 || code() == 429)

        private fun List<WorkOrderItemDto>.pendingReviewFirst(): List<WorkOrderItemDto> {
            val (pending, closed) = partition { it.closedAt == null }
            return pending.sortedByDescending { it.completedAt?.let(Instant::parse) } + closed
        }

        private fun WorkOrderItemDto.toDomain(communeName: String?) =
            HistoryItem(
                workOrderId, title, taskKind, woStatus, communeId, updatedAt, scheduledDate, completedAt, closedAt,
                communeName,
            )

        private fun WorkOrderItemDto.toEntity(
            ownerUserId: String,
            position: Int,
            fetchedAt: Long,
            communeName: String?,
        ) = LocalHistoryItemEntity(
            ownerUserId = ownerUserId,
            workOrderId = workOrderId,
            title = title,
            taskKind = taskKind,
            woStatus = woStatus,
            communeId = communeId,
            updatedAt = updatedAt,
            scheduledDate = scheduledDate,
            position = position,
            fetchedAt = fetchedAt,
            completedAt = completedAt,
            closedAt = closedAt,
            communeName = communeName,
        )

        private fun LocalHistoryItemEntity.toDomain() =
            HistoryItem(
                workOrderId, title, taskKind, woStatus, communeId, updatedAt, scheduledDate, completedAt, closedAt,
                communeName,
            )
    }
