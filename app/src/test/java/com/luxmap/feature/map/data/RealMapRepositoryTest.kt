package com.luxmap.feature.map.data

import app.cash.turbine.test
import com.luxmap.core.network.MapApi
import com.luxmap.core.network.UserApi
import com.luxmap.feature.auth.data.CommuneBboxDto
import com.luxmap.feature.auth.data.CurrentUserResponseDto
import com.luxmap.feature.auth.data.MapBoundsDto
import com.luxmap.feature.auth.data.MapCenterDto
import com.luxmap.feature.map.data.dto.PoleFeatureCollectionDto
import com.luxmap.feature.map.data.dto.PoleFeatureDto
import com.luxmap.feature.map.data.dto.PoleGeometryDto
import com.luxmap.feature.map.data.dto.PolePropertiesDto
import com.luxmap.feature.map.data.dto.RoadSegmentFeatureCollectionDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private const val USER_ID = "USER-1"
private val BBOX =
    MapBoundsDto(
        bbox = CommuneBboxDto(minLng = 105.8, minLat = 10.5, maxLng = 105.9, maxLat = 10.6),
        center = MapCenterDto(lat = 10.55, lng = 105.85),
    )

class RealMapRepositoryTest {
    private fun repository(
        mapBounds: MapBoundsDto? = BBOX,
        polesResponse: PoleFeatureCollectionDto =
            PoleFeatureCollectionDto(type = "FeatureCollection", features = emptyList()),
        segmentsResponse: RoadSegmentFeatureCollectionDto =
            RoadSegmentFeatureCollectionDto(type = "FeatureCollection", features = emptyList()),
    ): Triple<RealMapRepository, UserApi, MapApi> {
        val userApi = mockk<UserApi>()
        coEvery { userApi.me() } returns
            CurrentUserResponseDto(
                userId = USER_ID,
                username = "fe1",
                email = "fe1@luxmap.vn",
                fullName = "Field Engineer 1",
                role = "field_engineer",
                communeIds = listOf("COM-001"),
                communes = emptyList(),
                mapBounds = mapBounds,
            )
        val mapApi = mockk<MapApi>()
        coEvery { mapApi.poles(bbox = "105.8,10.5,105.9,10.6") } returns polesResponse
        coEvery { mapApi.segments(bbox = "105.8,10.5,105.9,10.6") } returns segmentsResponse
        return Triple(RealMapRepository(userApi, mapApi), userApi, mapApi)
    }

    @Test
    fun `when map_bounds is set, calls poles and segments with that exact bbox string`() =
        runTest {
            val pole =
                PoleFeatureDto(
                    type = "Feature",
                    geometry = PoleGeometryDto(type = "Point", coordinates = listOf(105.85, 10.55)),
                    properties =
                        PolePropertiesDto(
                            poleId = "POLE-1",
                            segmentId = "SEG-1",
                            fixtureStatus = "normal",
                            warrantyStatus = "unknown",
                            communeId = "COM-001",
                            openFaultCount = 0,
                            hasIotNode = false,
                            nearSensitivePoi = false,
                        ),
                )
            val (repository, _, _) =
                repository(
                    polesResponse = PoleFeatureCollectionDto(type = "FeatureCollection", features = listOf(pole)),
                )

            repository.observeGisMapDataset().test {
                val dataset = awaitItem()
                assertEquals(1, dataset.poles.size)
                assertEquals("POLE-1", dataset.poles[0].poleId)
                assertEquals(10.55 to 105.85, dataset.center)
                awaitComplete()
            }
        }

    @Test
    fun `when map_bounds is null, emits an error instead of calling poles or segments`() =
        runTest {
            val (repository, _, mapApi) = repository(mapBounds = null)

            repository.observeGisMapDataset().test {
                val error = awaitError()
                assertTrue(error is MapBoundsUnavailableException)
            }

            io.mockk.coVerify(exactly = 0) { mapApi.poles(any()) }
        }

    @Test
    fun `a 413 from the poles endpoint surfaces the server's real message`() =
        runTest {
            val userApi = mockk<UserApi>()
            coEvery { userApi.me() } returns
                CurrentUserResponseDto(
                    userId = USER_ID, username = "fe1", email = "fe1@luxmap.vn",
                    fullName = "Field Engineer 1", role = "field_engineer",
                    communeIds = listOf("COM-001"), communes = emptyList(), mapBounds = BBOX,
                )
            val mapApi = mockk<MapApi>()
            val errorBody =
                """
                {"error":{"code":"BBOX_TOO_LARGE",
                "message":"That area holds 2500 poles, more than the 2000 this endpoint serves. Zoom in.",
                "details":{}}}
                """.trimIndent()
            coEvery { mapApi.poles(bbox = "105.8,10.5,105.9,10.6") } throws
                HttpException(Response.error<Any>(413, errorBody.toResponseBody("application/json".toMediaType())))

            val repository = RealMapRepository(userApi, mapApi)

            repository.observeGisMapDataset().test {
                val error = awaitError()
                assertEquals(
                    "That area holds 2500 poles, more than the 2000 this endpoint serves. Zoom in.",
                    error.message,
                )
            }
        }
}
