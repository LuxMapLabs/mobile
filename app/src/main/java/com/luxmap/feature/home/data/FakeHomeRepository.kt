package com.luxmap.feature.home.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

// Kept for previewing/testing the UI without a network call. Field shape matches the real
// GET /work-orders list response (see RealHomeRepository) - plannedSweepCount stays null, the
// same "no data source yet" state RealHomeRepository always reports.
@Singleton
class FakeHomeRepository
    @Inject
    constructor() : HomeRepository {
        override fun observeHomeData(): Flow<HomeData> =
            flow {
                emit(
                    HomeData(
                        metrics =
                            HomeMetrics(
                                assignedCount = 5,
                                inProgressCount = 2,
                                overdueCount = 1,
                                plannedSweepCount = null,
                            ),
                        clusters =
                            listOf(
                                WorkOrderCluster(
                                    clusterLabel = "COM-001",
                                    items =
                                        listOf(
                                            WorkOrderSummaryItem(
                                                workOrderId = "WO-2031",
                                                woStatus = "assigned",
                                                dueDate = "2026-09-27",
                                                priorityScore = 92.0,
                                                taskKind = "inspection",
                                                communeId = "COM-001",
                                                title = "Kiểm tra đèn tuyến liên xã Phước Hiệp",
                                                scheduledDate = "2026-09-27",
                                                faultCount = 3,
                                            ),
                                            WorkOrderSummaryItem(
                                                workOrderId = "WO-2028",
                                                woStatus = "in_progress",
                                                dueDate = "2026-09-29",
                                                priorityScore = 54.0,
                                                taskKind = "repair",
                                                communeId = "COM-001",
                                                title = "Thay bóng đèn đoạn qua chợ",
                                                scheduledDate = "2026-09-28",
                                                faultCount = 1,
                                            ),
                                        ),
                                ),
                                WorkOrderCluster(
                                    clusterLabel = "COM-002",
                                    items =
                                        listOf(
                                            WorkOrderSummaryItem(
                                                workOrderId = "WO-2019",
                                                woStatus = "assigned",
                                                dueDate = "2026-09-26",
                                                priorityScore = 78.0,
                                                taskKind = "repair",
                                                communeId = "COM-002",
                                                title = "Sửa tủ điện ấp 3",
                                                scheduledDate = null,
                                                faultCount = 2,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            }
    }
