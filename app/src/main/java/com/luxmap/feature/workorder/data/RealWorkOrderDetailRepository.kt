package com.luxmap.feature.workorder.data

import com.luxmap.core.network.WorkOrdersApi
import com.luxmap.feature.workorder.data.dto.RequestCancellationRequestDto
import com.luxmap.feature.workorder.polegps.data.PoleGpsDetailSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealWorkOrderDetailRepository
    @Inject
    constructor(
        private val workOrdersApi: WorkOrdersApi,
        private val poleGpsDetailSync: PoleGpsDetailSync,
    ) : WorkOrderDetailRepository {
        override fun observeWorkOrderDetail(workOrderId: String): Flow<WorkOrderDetail?> =
            flow {
                val detail =
                    try {
                        val dto = workOrdersApi.detail(workOrderId)
                        poleGpsDetailSync.store(dto)
                        dto.toWorkOrderDetail()
                    } catch (e: HttpException) {
                        if (e.code() == 404) null else throw e
                    } catch (e: IOException) {
                        poleGpsDetailSync.cached(workOrderId)?.toWorkOrderDetail() ?: throw e
                    }
                emit(detail)
            }

        override suspend fun start(workOrderId: String): Result<WorkOrderDetail> =
            runCatching { workOrdersApi.start(workOrderId).toWorkOrderDetail() }

        override suspend fun requestCancellation(
            workOrderId: String,
            reason: String,
        ): Result<WorkOrderDetail> =
            runCatching {
                val body = RequestCancellationRequestDto(reason)
                workOrdersApi.requestCancellation(workOrderId, body).toWorkOrderDetail()
            }
    }
