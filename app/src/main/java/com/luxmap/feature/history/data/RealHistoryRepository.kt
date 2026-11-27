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
import javax.inject.Inject
import javax.inject.Singleton

private const val CLOSED_STATUSES = "done,verified,cancelled"

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
                    val userId = userApi.me().userId
                    ownerUserId = userId
                    if (!tokenStore.saveCachedUserIdForSession(userId, sessionId)) {
                        throw CancellationException("Session changed")
                    }
                    val items = workOrdersApi.list(assignedTo = userId, woStatus = CLOSED_STATUSES).items
                    ensureSession(sessionId)
                    cacheDao.replaceAll(userId, items.mapIndexed { index, dto -> dto.toEntity(userId, index) })
                    emit(HistoryResult(items.map { it.toDomain() }, isStale = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!e.isTemporaryFailure()) throw e
                    val owner = ownerUserId
                    val cached =
                        if (owner != null && tokenStore.cachedUserId() == owner) cacheDao.all(owner) else emptyList()
                    ensureSession(sessionId)
                    if (cached.isEmpty()) throw e
                    emit(HistoryResult(cached.map { it.toDomain() }, isStale = true))
                }
            }

        private suspend fun ensureSession(expectedSessionId: String) {
            if (tokenStore.identitySessionId() != expectedSessionId) throw CancellationException("Session changed")
        }

        private fun Exception.isTemporaryFailure(): Boolean =
            this is IOException || this is HttpException && (code() >= 500 || code() == 408 || code() == 429)

        private fun WorkOrderItemDto.toDomain() =
            HistoryItem(workOrderId, title, taskKind, woStatus, communeId, updatedAt, scheduledDate)

        private fun WorkOrderItemDto.toEntity(
            ownerUserId: String,
            position: Int,
        ) = LocalHistoryItemEntity(
            ownerUserId, workOrderId, title, taskKind, woStatus, communeId, updatedAt, scheduledDate, position,
        )

        private fun LocalHistoryItemEntity.toDomain() =
            HistoryItem(workOrderId, title, taskKind, woStatus, communeId, updatedAt, scheduledDate)
    }
