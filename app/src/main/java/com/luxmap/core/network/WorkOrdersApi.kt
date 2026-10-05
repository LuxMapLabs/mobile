package com.luxmap.core.network

import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Route checked against WorkOrdersController.cs: [Route("api/v{version:apiVersion}/work-orders")].
interface WorkOrdersApi {
    @GET("api/v1/work-orders")
    suspend fun list(
        @Query("assigned_to") assignedTo: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): PagedResultDto<WorkOrderItemDto>

    @GET("api/v1/work-orders/{id}")
    suspend fun detail(
        @Path("id") id: String,
    ): WorkOrderDetailDto

    @POST("api/v1/work-orders/{id}/start")
    suspend fun start(
        @Path("id") id: String,
    ): WorkOrderDetailDto
}
