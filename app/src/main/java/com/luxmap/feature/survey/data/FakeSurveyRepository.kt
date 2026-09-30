package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSurveyRepository
    @Inject
    constructor() : SurveyRepository {
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
    }
