package com.luxmap.core.network

import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import retrofit2.http.GET
import retrofit2.http.Query

// Route checked against WorkOrdersController.cs: [Route("api/v{version:apiVersion}/work-orders")].
// Only the list endpoint is here for now - F02 (Home) only needs a list, not the detail/action
// endpoints (start/complete/verify/...), which belong to F08-F10 when those screens are built.
interface WorkOrdersApi {
    @GET("api/v1/work-orders")
    suspend fun list(
        @Query("assigned_to") assignedTo: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): PagedResultDto<WorkOrderItemDto>
}
