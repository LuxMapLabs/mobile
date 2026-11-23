package com.luxmap.core.network

import com.luxmap.feature.survey.data.dto.CreateLuxReadingRequestDto
import com.luxmap.feature.survey.data.dto.LuxReadingDto
import retrofit2.http.Body
import retrofit2.http.POST

interface LuxReadingsApi {
    @POST("api/v1/lux-readings")
    suspend fun create(
        @Body request: CreateLuxReadingRequestDto,
    ): LuxReadingDto
}
