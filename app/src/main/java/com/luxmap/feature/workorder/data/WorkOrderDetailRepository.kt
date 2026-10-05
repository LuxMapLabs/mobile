package com.luxmap.feature.workorder.data

import kotlinx.coroutines.flow.Flow

interface WorkOrderDetailRepository {
    fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?>

    suspend fun start(workOrderId: String): Result<Unit>
}
