package com.luxmap.feature.survey.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSurveyRepository
    @Inject
    constructor() : SurveyRepository {
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

        override fun observeAssignedPoleGpsOrders(): Flow<List<AssignedSurveyRoute>> =
            flow {
                emit(
                    listOf(
                        AssignedSurveyRoute(
                            workOrderId = "WO-2040",
                            title = "Cập nhật GPS cột đường liên thôn 5",
                            communeId = "COM-001",
                            woStatus = "assigned",
                            dueDate = null,
                            scheduledDate = "2026-11-10",
                        ),
                    ),
                )
            }

        override suspend fun startRoute(workOrderId: String): Result<Unit> = Result.success(Unit)

        override suspend fun requestCancellation(
            workOrderId: String,
            reason: String,
        ): Result<Unit> = Result.success(Unit)
    }
