package com.luxmap.core.network

import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.survey.data.dto.PoleListItemDto
import com.luxmap.feature.survey.data.dto.PolePhotoItemDto
import com.luxmap.feature.survey.data.dto.PolePhotoReviewRequestDto
import com.luxmap.feature.survey.data.dto.PolePhotoUploadResponseDto
import com.luxmap.feature.workorder.data.dto.CompleteWorkOrderRequestDto
import com.luxmap.feature.workorder.data.dto.EvidenceItemDto
import com.luxmap.feature.workorder.data.dto.RequestCancellationRequestDto
import com.luxmap.feature.workorder.data.dto.WorkOrderDetailDto
import com.luxmap.feature.workorder.data.dto.WorkOrderItemDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

// Route checked against WorkOrdersController.cs: [Route("api/v{version:apiVersion}/work-orders")].
interface WorkOrdersApi {
    @GET("api/v1/work-orders")
    suspend fun list(
        @Query("assigned_to") assignedTo: String,
        @Query("task_kind") taskKind: String? = null,
        @Query("wo_status") woStatus: String? = null,
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

    @POST("api/v1/work-orders/{id}/request-cancellation")
    suspend fun requestCancellation(
        @Path("id") id: String,
        @Body body: RequestCancellationRequestDto,
    ): WorkOrderDetailDto

    @GET("api/v1/work-orders/{id}/poles")
    suspend fun poles(
        @Path("id") id: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): PagedResultDto<PoleListItemDto>

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

    @Multipart
    @POST("api/v1/work-orders/{id}/pole-photos")
    suspend fun uploadPolePhoto(
        @Path("id") id: String,
        @Part("pole_id") poleId: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("captured_at") capturedAt: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("client_op_id") clientOpId: RequestBody,
    ): PolePhotoUploadResponseDto

    @GET("api/v1/work-orders/{id}/pole-photos/{photo_id}")
    suspend fun polePhotoDetail(
        @Path("id") id: String,
        @Path("photo_id") photoId: String,
    ): PolePhotoItemDto

    // Field Engineer corrects/confirms cvState before a Manager decides - added 2026-10-20
    // (ticket PHOTO-REVIEW). See docs/contract-drift.md "PATCH .../review".
    @PATCH("api/v1/work-orders/{id}/pole-photos/{photo_id}/review")
    suspend fun reviewPolePhoto(
        @Path("id") id: String,
        @Path("photo_id") photoId: String,
        @Body body: PolePhotoReviewRequestDto,
    ): PolePhotoItemDto
}
