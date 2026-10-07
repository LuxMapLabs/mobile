package com.luxmap.core.network

import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.EvidenceItemDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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

    @POST("api/v1/work-orders/{id}/complete")
    suspend fun complete(
        @Path("id") id: String,
        @Body body: CompleteWorkOrderRequestDto,
    ): WorkOrderDetailDto

    @Multipart
    @POST("api/v1/work-orders/{id}/evidence")
    suspend fun uploadEvidence(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part("kind") kind: RequestBody,
        @Part("captured_at") capturedAt: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("client_op_id") clientOpId: RequestBody,
    ): EvidenceItemDto
}
