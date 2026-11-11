package com.luxmap.feature.map.data

import app.cash.turbine.test
import com.luxmap.core.network.MapApi
import com.luxmap.feature.map.data.dto.PoleDetailCurrentStatusDto
import com.luxmap.feature.map.data.dto.PoleDetailDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

private const val POLE_ID = "POLE-1"

private fun httpException(code: Int): HttpException =
    HttpException(Response.error<Any>(code, "".toResponseBody("application/json".toMediaType())))

private fun detailDto() =
    PoleDetailDto(
        poleId = POLE_ID,
        segmentId = null,
        segmentName = null,
        communeId = "COM-001",
        location = null,
        fixture = null,
        currentStatus = PoleDetailCurrentStatusDto(fixtureStatus = "unknown"),
        iotNode = null,
        note = null,
    )

class RealPoleDetailRepositoryTest {
    @Test
    fun `emits the mapped detail when the backend returns 200`() =
        runTest {
            val mapApi = mockk<MapApi>()
            coEvery { mapApi.poleDetail(POLE_ID) } returns detailDto()
            val repository = RealPoleDetailRepository(mapApi)

            repository.observePoleDetail(POLE_ID).test {
                val detail = awaitItem()
                assertEquals(POLE_ID, detail?.poleId)
                awaitComplete()
            }
        }

    @Test
    fun `emits null on a 404, not an Error`() =
        runTest {
            val mapApi = mockk<MapApi>()
            coEvery { mapApi.poleDetail(POLE_ID) } throws httpException(404)
            val repository = RealPoleDetailRepository(mapApi)

            repository.observePoleDetail(POLE_ID).test {
                assertNull(awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun `a non-404 HTTP error propagates instead of being treated as not-found`() =
        runTest {
            val mapApi = mockk<MapApi>()
            coEvery { mapApi.poleDetail(POLE_ID) } throws httpException(500)
            val repository = RealPoleDetailRepository(mapApi)

            repository.observePoleDetail(POLE_ID).test {
                val error = awaitError()
                assertTrue(error is HttpException)
            }
        }
}
