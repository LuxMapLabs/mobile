package com.luxmap.feature.survey.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.core.security.TokenStore
import com.luxmap.feature.survey.data.dao.PoleGpsOrderCacheDao
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.entity.LocalAssignedPoleGpsOrderEntity
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import com.luxmap.feature.workorder.data.dto.RequestCancellationRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val SURVEY_TASK_KIND = "survey"
private const val POLE_GPS_UPDATE_TASK_KIND = "pole_gps_update"

// Calls the real GET /auth/me + GET /work-orders?task_kind=survey (see UserApi/WorkOrdersApi),
// same endpoint Home already uses - there is no separate survey-sweeps endpoint, confirmed
// against the luxmap_backend source 2026-10-14 (see CLAUDE.md).
@Singleton
class RealSurveyRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val workOrdersApi: WorkOrdersApi,
        private val routeCacheDao: SurveyRouteCacheDao,
        private val gpsCacheDao: PoleGpsOrderCacheDao,
        private val tokenStore: TokenStore,
    ) : SurveyRepository {
        override fun observeAssignedRoutes(): Flow<AssignedRoutesResult> =
            flow {
                val sessionId = tokenStore.identitySessionId()
                val cachedOwner = tokenStore.cachedUserId()
                try {
                    val userId = verifiedUserId()
                    val items = workOrdersApi.list(assignedTo = userId, taskKind = SURVEY_TASK_KIND).items
                    val routes =
                        items.map { item ->
                            AssignedSurveyRoute(
                                workOrderId = item.workOrderId,
                                title = item.title,
                                communeId = item.communeId,
                                woStatus = item.woStatus,
                                dueDate = item.dueDate,
                                scheduledDate = item.scheduledDate,
                            )
                        }
                    if (tokenStore.identitySessionId() != sessionId) throw CancellationException("Session changed")
                    routeCacheDao.replaceAll(routes.map { it.toEntity(userId) })
                    ensureSession(sessionId)
                    emit(AssignedRoutesResult(routes, isStale = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is HttpException && (e.code() == 401 || e.code() == 403)) throw e
                    if (cachedOwner == null || tokenStore.cachedUserId() != cachedOwner ||
                        tokenStore.identitySessionId() != sessionId
                    ) {
                        throw e
                    }
                    // Offline-first fallback (CLAUDE.md A4/A6): a failed fetch still shows the
                    // last known list instead of a blank error screen, marked isStale.
                    val cached = routeCacheDao.allForOwner(cachedOwner).map { it.toDomain() }
                    if (cached.isEmpty()) throw e
                    ensureSession(sessionId)
                    emit(AssignedRoutesResult(cached, isStale = true))
                }
            }

        override fun observeAssignedPoleGpsOrders(): Flow<AssignedRoutesResult> =
            flow {
                val sessionId = tokenStore.identitySessionId()
                var ownerUserId = tokenStore.cachedUserId()
                try {
                    val verifiedUserId = verifiedUserId()
                    ownerUserId = verifiedUserId
                    val routes = mutableListOf<AssignedSurveyRoute>()
                    var page = 1
                    do {
                        val response =
                            workOrdersApi.list(
                                assignedTo = verifiedUserId,
                                taskKind = POLE_GPS_UPDATE_TASK_KIND,
                                page = page,
                            )
                        if (response.page != page || response.pageSize <= 0) {
                            throw IncompleteGpsPageException()
                        }
                        routes +=
                            response.items.map {
                                AssignedSurveyRoute(
                                    it.workOrderId, it.title, it.communeId, it.woStatus, it.dueDate, it.scheduledDate,
                                )
                            }
                        if (response.items.isEmpty() && routes.size < response.total) {
                            throw IncompleteGpsPageException()
                        }
                        page++
                    } while (routes.size < response.total)
                    // A concurrent logout/account switch must never publish the old owner's list.
                    if (tokenStore.cachedUserId() != verifiedUserId || tokenStore.identitySessionId() != sessionId) {
                        throw CancellationException("Session changed")
                    }
                    gpsCacheDao.replaceAll(
                        verifiedUserId,
                        routes.map {
                            LocalAssignedPoleGpsOrderEntity(
                                verifiedUserId,
                                it.workOrderId,
                                it.title,
                                it.communeId,
                                it.woStatus,
                                it.dueDate,
                                it.scheduledDate,
                            )
                        },
                    )
                    ensureSession(sessionId)
                    emit(AssignedRoutesResult(routes, isStale = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!e.isTemporaryFailure()) throw e
                    val owner = ownerUserId
                    val cached =
                        if (owner != null && tokenStore.cachedUserId() == owner &&
                            tokenStore.identitySessionId() == sessionId
                        ) {
                            gpsCacheDao.all(owner).map {
                                AssignedSurveyRoute(
                                    it.workOrderId,
                                    it.title,
                                    it.communeId,
                                    it.woStatus,
                                    it.dueDate,
                                    it.scheduledDate,
                                )
                            }
                        } else {
                            emptyList()
                        }
                    ensureSession(sessionId)
                    emit(AssignedRoutesResult(cached, isStale = true))
                }
            }

        private suspend fun ensureSession(expectedSessionId: String?) {
            if (expectedSessionId == null || tokenStore.identitySessionId() != expectedSessionId) {
                throw CancellationException("Session changed")
            }
        }

        private suspend fun verifiedUserId(): String {
            val sessionId = tokenStore.identitySessionId() ?: throw CancellationException("No active session")
            val userId = userApi.me().userId
            if (!tokenStore.saveCachedUserIdForSession(userId, sessionId)) {
                throw CancellationException("Session changed")
            }
            return userId
        }

        private fun Exception.isTemporaryFailure(): Boolean =
            this is IOException || this is HttpException && (code() >= 500 || code() == 408 || code() == 429)

        private fun AssignedSurveyRoute.toEntity(ownerUserId: String) =
            LocalAssignedSurveyRouteEntity(workOrderId, title, communeId, woStatus, dueDate, scheduledDate, ownerUserId)

        private fun LocalAssignedSurveyRouteEntity.toDomain() =
            AssignedSurveyRoute(workOrderId, title, communeId, woStatus, dueDate, scheduledDate)

        override suspend fun startRoute(workOrderId: String): Result<Unit> =
            runCatching { workOrdersApi.start(workOrderId) }.map {}

        override suspend fun requestCancellation(
            workOrderId: String,
            reason: String,
        ): Result<Unit> =
            runCatching {
                workOrdersApi.requestCancellation(workOrderId, RequestCancellationRequestDto(reason))
            }.map {}
    }

private class IncompleteGpsPageException : IOException("Danh sách GPS chưa tải đủ trang.")
