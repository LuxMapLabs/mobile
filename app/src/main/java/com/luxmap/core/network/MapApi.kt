package com.luxmap.core.network

import com.luxmap.feature.map.data.dto.PoleDetailDto
import com.luxmap.feature.map.data.dto.PoleFeatureCollectionDto
import com.luxmap.feature.map.data.dto.RoadSegmentFeatureCollectionDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// GET /map/poles, /map/segments and /map/poles/{pole_id} (luxmap_backend MapController.cs) —
// bbox is REQUIRED on the list endpoints, "minLng,minLat,maxLng,maxLat" as one string
// (BoundingBox.Parse). The server already narrows results to the caller's own communes, so no
// commune_id param is needed here.
interface MapApi {
    @GET("api/v1/map/poles")
    suspend fun poles(
        @Query("bbox") bbox: String,
    ): PoleFeatureCollectionDto

    @GET("api/v1/map/segments")
    suspend fun segments(
        @Query("bbox") bbox: String,
    ): RoadSegmentFeatureCollectionDto

    @GET("api/v1/map/poles/{pole_id}")
    suspend fun poleDetail(
        @Path("pole_id") poleId: String,
    ): PoleDetailDto
}
