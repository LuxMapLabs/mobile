package com.luxmap.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorTest {
    @Test
    fun `errorMessageOrNull reads the real server message out of the error envelope`() {
        val body =
            """
            {"error":{"code":"BBOX_TOO_LARGE",
            "message":"That area holds 2500 poles, more than the 2000 this endpoint serves. Zoom in.",
            "details":{"count":2500,"max":2000}}}
            """.trimIndent()
        val exception =
            HttpException(
                Response.error<Any>(413, body.toResponseBody("application/json".toMediaType())),
            )

        val message = exception.errorMessageOrNull()

        assertEquals("That area holds 2500 poles, more than the 2000 this endpoint serves. Zoom in.", message)
    }
}
