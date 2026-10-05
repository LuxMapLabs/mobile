package com.luxmap.feature.workorder.data

import com.luxmap.core.network.WorkOrdersApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderDetailRepository
    @Inject
    constructor(
        private val workOrdersApi: WorkOrdersApi,
    ) : WorkOrderDetailRepository {
        override fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?> =
            flow {
                try {
                    emit(workOrdersApi.detail(workOrderId).toWorkOrderDetail())
                } catch (e: HttpException) {
                    if (e.code() == 404) emit(null) else throw e
                }
            }

        override suspend fun start(workOrderId: String): Result<Unit> = runCatching { workOrdersApi.start(workOrderId) }
    }
