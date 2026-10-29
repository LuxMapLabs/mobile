package com.luxmap.feature.survey.data

import android.content.Context
import android.util.Log
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FakeSurveyRepository"
private const val UPLOADING_SYNC_STATE = "queued"

@Singleton
class FakeSurveyRepository
    @Inject
    constructor(
        private val sessionDao: SurveySessionDao,
        @ApplicationContext private val context: Context,
    ) : SurveyRepository {
        override fun observeAssignedRoutes(): Flow<AssignedRoutesResult> =
            flow {
                emit(
                    AssignedRoutesResult(
                        routes =
                            listOf(
                                AssignedSurveyRoute(
                                    workOrderId = "WO-2031",
                                    title = "Khảo sát đường liên thôn 3-4",
                                    communeId = "COM-001",
                                    woStatus = "assigned",
                                    dueDate = null,
                                    scheduledDate = "2026-10-01",
                                ),
                            ),
                        isStale = false,
                    ),
                )
            }

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

        override suspend fun startRoute(workOrderId: String): Result<Unit> = Result.success(Unit)

        override suspend fun requestCancellation(
            workOrderId: String,
            reason: String,
        ): Result<Unit> = Result.success(Unit)
    }
