package com.luxmap.core.network

import com.luxmap.feature.map.data.dto.PoleFeatureCollectionDto
import com.luxmap.feature.map.data.dto.RoadSegmentFeatureCollectionDto
import retrofit2.http.GET
import retrofit2.http.Query

// GET /map/poles and GET /map/segments (luxmap_backend MapController.cs) — bbox is REQUIRED on
// both, "minLng,minLat,maxLng,maxLat" as one string (BoundingBox.Parse). The server already
// narrows results to the caller's own communes, so no commune_id param is needed here.
interface MapApi {
    @GET("api/v1/map/poles")
    suspend fun poles(
        @Query("bbox") bbox: String,
    ): PoleFeatureCollectionDto

    @GET("api/v1/map/segments")
    suspend fun segments(
        @Query("bbox") bbox: String,
    ): RoadSegmentFeatureCollectionDto
}
