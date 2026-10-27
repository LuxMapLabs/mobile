package com.luxmap.feature.workorder.data

import kotlinx.coroutines.flow.Flow

interface WorkOrderDetailRepository {
    fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?>

    suspend fun start(workOrderId: String): Result<WorkOrderDetail>

    suspend fun requestCancellation(
        workOrderId: String,
        reason: String,
    ): Result<WorkOrderDetail>
}
