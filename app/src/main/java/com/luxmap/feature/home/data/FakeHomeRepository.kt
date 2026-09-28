package com.luxmap.feature.home.data

import com.luxmap.core.theme.WorkOrderPriority
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

// Dữ liệu mẫu tĩnh cho tới khi có backend thật cho work-orders/assigned-to-me và
// survey-sweeps/planned (xem docs/Backend_API_Requirements_For_Mobile.docx mục 1 — cả 2 endpoint
// này chưa nằm trong danh sách "đã có Controller"). clusterLabel mô phỏng kết quả gom cụm địa lý
// mà BE-25 làm ở server thật (F02 spec: "gom theo cụm địa lý"), để dựng đúng UI cuối cùng trước.
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
                                plannedSweepCount = 1,
                            ),
                        clusters =
                            listOf(
                                WorkOrderCluster(
                                    clusterLabel = "Xã Đông Thịnh",
                                    items =
                                        listOf(
                                            WorkOrderSummaryItem(
                                                workOrderId = "WO-2031",
                                                shortAddress = "Đường liên thôn 3, xã Đông Thịnh",
                                                faultTypeLabel = "Đèn tắt",
                                                priority = WorkOrderPriority.URGENT,
                                                slaDueAt = "2026-09-27T10:00:00Z",
                                                distanceMeters = 850.0,
                                            ),
                                            WorkOrderSummaryItem(
                                                workOrderId = "WO-2028",
                                                shortAddress = "Cầu Đông Thịnh 2",
                                                faultTypeLabel = "Đèn mờ",
                                                priority = WorkOrderPriority.NORMAL,
                                                slaDueAt = "2026-09-29T10:00:00Z",
                                                distanceMeters = 1200.0,
                                            ),
                                        ),
                                ),
                                WorkOrderCluster(
                                    clusterLabel = "Xã Tân Lập",
                                    items =
                                        listOf(
                                            WorkOrderSummaryItem(
                                                workOrderId = "WO-2019",
                                                shortAddress = "Đường tỉnh 512, xã Tân Lập",
                                                faultTypeLabel = "Đèn tắt",
                                                priority = WorkOrderPriority.HIGH,
                                                slaDueAt = "2026-09-26T10:00:00Z",
                                                distanceMeters = 4300.0,
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            }
    }
