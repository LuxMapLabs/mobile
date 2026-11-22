package com.luxmap.core.network

import com.luxmap.feature.workorder.polegps.data.SegmentDetailDto
import retrofit2.http.GET
import retrofit2.http.Path

interface AssetsApi {
    @GET("api/v1/assets/segments/{segment_id}")
    suspend fun segment(
        @Path("segment_id") segmentId: String,
    ): SegmentDetailDto
}
