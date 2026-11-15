package com.luxmap.feature.survey.data

import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import com.luxmap.feature.workorder.data.dto.RequestCancellationRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
    ) : SurveyRepository {
        override fun observeAssignedRoutes(): Flow<AssignedRoutesResult> =
            flow {
                try {
                    val userId = userApi.me().userId
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
                    routeCacheDao.replaceAll(routes.map { it.toEntity() })
                    emit(AssignedRoutesResult(routes, isStale = false))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline-first fallback (CLAUDE.md A4/A6): a failed fetch still shows the
                    // last known list instead of a blank error screen, marked isStale.
                    val cached = routeCacheDao.all().map { it.toDomain() }
                    if (cached.isEmpty()) throw e
                    emit(AssignedRoutesResult(cached, isStale = true))
                }
            }

        override fun observeAssignedPoleGpsOrders(): Flow<List<AssignedSurveyRoute>> =
            flow {
                try {
                    val userId = userApi.me().userId
                    val items = workOrdersApi.list(assignedTo = userId, taskKind = POLE_GPS_UPDATE_TASK_KIND).items
                    emit(
                        items.map { item ->
                            AssignedSurveyRoute(
                                workOrderId = item.workOrderId,
                                title = item.title,
                                communeId = item.communeId,
                                woStatus = item.woStatus,
                                dueDate = item.dueDate,
                                scheduledDate = item.scheduledDate,
                            )
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    emit(emptyList())
                }
            }

        private fun AssignedSurveyRoute.toEntity() =
            LocalAssignedSurveyRouteEntity(workOrderId, title, communeId, woStatus, dueDate, scheduledDate)

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
