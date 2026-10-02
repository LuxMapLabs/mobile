package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow

enum class SurveySweepStatus { PLANNED, IN_PROGRESS, PENDING_UPLOAD, SUBMITTED, PROCESSED }

data class AssignedRoadSegment(
    val roadSegmentId: String,
    val name: String,
    val lengthMeters: Double?,
)

data class AssignedSurveyRoute(
    val surveySweepId: String,
    val assignedByName: String,
    val plannedDate: String,
    val status: SurveySweepStatus,
    val roadSegments: List<AssignedRoadSegment>,
)

// Field Engineer never creates/edits a route on mobile (spec §10) — this only observes what
// "Kỹ sư bảo trì" assigned, from GET /api/v1/survey-sweeps/planned (already used by F02).
interface SurveyRepository {
    fun observeAssignedRoutes(): Flow<List<AssignedSurveyRoute>>

    // Pure local data - no network involved, so Fake and Real do the same thing here (see
    // the design spec §3 for why this still lives on the one repository interface).
    suspend fun segmentFilePathsFor(sessionId: String): List<String>

    // Deletes the session's local video/log files and its Room rows, returning the
    // surveySweepId so the caller can start a fresh recording for the same route.
    suspend fun discardSession(sessionId: String): String
}
