package com.luxmap.feature.survey.data

import android.content.Context
import android.util.Log
import com.luxmap.feature.survey.data.dao.SurveySessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FakeSurveyRepository"

@Singleton
class FakeSurveyRepository
    @Inject
    constructor(
        private val sessionDao: SurveySessionDao,
        @ApplicationContext private val context: Context,
    ) : SurveyRepository {
        override fun observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>> =
            flow {
                emit(
                    listOf(
                        AssignedSurveyRoute(
                            surveySweepId = "SWEEP-2031",
                            assignedByName = "Kỹ sư bảo trì Trần Văn A",
                            plannedDate = "2026-10-01",
                            status = SurveySweepStatus.PLANNED,
                            roadSegments =
                                listOf(
                                    AssignedRoadSegment(
                                        roadSegmentId = "RS-1001",
                                        name = "Đường liên thôn 3",
                                        lengthMeters = 1200.0,
                                    ),
                                    AssignedRoadSegment(
                                        roadSegmentId = "RS-1002",
                                        name = "Đường liên thôn 4",
                                        lengthMeters = 800.0,
                                    ),
                                ),
                        ),
                    ),
                )
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
    }
