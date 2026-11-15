package com.luxmap.core.network

import com.luxmap.core.network.dto.PagedResultDto
import com.luxmap.feature.polelocation.data.dto.PoleLocationResponseDto
import com.luxmap.feature.polelocation.data.dto.PoleMissingLocationItemDto
import com.luxmap.feature.polelocation.data.dto.SetPoleLocationRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Route checked against AssetsController.cs: [Route("api/v{version:apiVersion}/assets")].
interface AssetsApi {
    @GET("api/v1/assets/poles")
    suspend fun listPoles(
        @Query("missing_location") missingLocation: Boolean,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 200,
    ): PagedResultDto<PoleMissingLocationItemDto>

    @PUT("api/v1/assets/poles/{id}/location")
    suspend fun setPoleLocation(
        @Path("id") id: String,
        @Body body: SetPoleLocationRequestDto,
    ): PoleLocationResponseDto
}
