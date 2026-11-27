package com.luxmap.feature.workorder.data

import kotlinx.coroutines.flow.Flow

interface WorkOrderDetailRepository {
    fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?>

    suspend fun start(workOrderId: String): Result<WorkOrderDetail>

    // Returns once a start made without signal has reached the server.
    suspend fun awaitStartSent(workOrderId: String)

    suspend fun requestCancellation(
        workOrderId: String,
        reason: String,
    ): Result<WorkOrderDetail>
}
