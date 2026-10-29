package com.luxmap.feature.survey.data

import android.content.Context
import android.util.Log
import com.luxmap.core.network.UserApi
import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.survey.data.dao.SurveyRouteCacheDao
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import com.luxmap.feature.survey.data.entity.LocalAssignedSurveyRouteEntity
import com.luxmap.feature.workorder.data.dto.RequestCancellationRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RealSurveyRepository"
private const val UPLOADING_SYNC_STATE = "queued"
private const val SURVEY_TASK_KIND = "survey"

// Calls the real GET /auth/me + GET /work-orders?task_kind=survey (see UserApi/WorkOrdersApi),
// same endpoint Home already uses - there is no separate survey-sweeps endpoint, confirmed
// against the luxmap_backend source 2026-10-14 (see CLAUDE.md).
@Singleton
class RealSurveyRepository
    @Inject
    constructor(
        private val userApi: UserApi,
        private val workOrdersApi: WorkOrdersApi,
        private val sessionDao: SurveySessionDao,
        private val routeCacheDao: SurveyRouteCacheDao,
        @ApplicationContext private val context: Context,
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

        private fun AssignedSurveyRoute.toEntity() =
            LocalAssignedSurveyRouteEntity(workOrderId, title, communeId, woStatus, dueDate, scheduledDate)

        private fun LocalAssignedSurveyRouteEntity.toDomain() =
            AssignedSurveyRoute(workOrderId, title, communeId, woStatus, dueDate, scheduledDate)

        // Pure local data - no network involved, so this matches FakeSurveyRepository (see the
        // interface's own comment for why Fake and Real do the same thing here).
        override fun observeUploadingSessions(): Flow<List<QueuedUploadSession>> =
            sessionDao.observeSessionsBySyncState(UPLOADING_SYNC_STATE).map { sessions ->
                sessions.map { QueuedUploadSession(sessionId = it.sessionId, surveySweepId = it.surveySweepId) }
            }

        override suspend fun segmentFilePathsFor(sessionId: String): List<String> =
            sessionDao.segmentsFor(sessionId).map { it.filePath }

        override suspend fun discardSession(sessionId: String): String {
            val session =
                requireNotNull(sessionDao.sessionById(sessionId)) {
                    "No local_survey_session row for sessionId=$sessionId"
                }
            // File deletion failing must never block the Room cleanup below - a stuck session
            // the user can never redo is worse than a leftover file to clean up later.
            runCatching {
                File(context.getExternalFilesDir(null), "survey/$sessionId").deleteRecursively()
            }.onFailure { error ->
                Log.w(TAG, "Failed to delete session files for $sessionId", error)
            }
            sessionDao.deleteSessionAndSegments(sessionId)
            return session.surveySweepId
        }

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
