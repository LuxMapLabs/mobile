package com.luxmap.core.network

import com.luxmap.core.network.dto.CreateSweepRequestDto
import com.luxmap.core.network.dto.SubmitSweepRequestDto
import com.luxmap.core.network.dto.SurveyClipResponseDto
import com.luxmap.core.network.dto.SurveyRawResponseDto
import com.luxmap.core.network.dto.SweepResponseDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface SweepsApi {
    @POST("api/v1/sweeps")
    suspend fun create(
        @Body body: CreateSweepRequestDto,
    ): SweepResponseDto

    @PUT("api/v1/sweeps/{id}/clips/{clipNo}")
    suspend fun uploadClip(
        @Path("id") sweepId: String,
        @Path("clipNo") clipNo: Int,
        @Header("X-Content-SHA256") sha256: String,
        @Body body: RequestBody,
    ): SurveyClipResponseDto

    @PUT("api/v1/sweeps/{id}/raw/{kind}")
    suspend fun uploadRaw(
        @Path("id") sweepId: String,
        @Path("kind") kind: String,
        @Body body: RequestBody,
    ): SurveyRawResponseDto

    @POST("api/v1/sweeps/{id}/submit")
    suspend fun submit(
        @Path("id") sweepId: String,
        @Body body: SubmitSweepRequestDto,
    ): SweepResponseDto
}
