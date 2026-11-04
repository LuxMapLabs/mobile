package com.luxmap.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiErrorTest {
    @Test
    fun `errorCodeOrNull reads the code out of the error envelope`() {
        val body = """{"error":{"code":"AFTER_EVIDENCE_REQUIRED","message":"no after photo"}}"""
        val exception = HttpException(Response.error<Any>(409, body.toResponseBody("application/json".toMediaType())))

        assertEquals("AFTER_EVIDENCE_REQUIRED", exception.errorCodeOrNull())
    }

    @Test
    fun `errorCodeOrNull returns null when the body is not the expected shape`() {
        val exception = HttpException(Response.error<Any>(500, "oops".toResponseBody("text/plain".toMediaType())))

        assertNull(exception.errorCodeOrNull())
    }

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

    @Test
    fun `errorMessageOrNull returns null when the body is not the expected shape`() {
        val exception = HttpException(Response.error<Any>(500, "oops".toResponseBody("text/plain".toMediaType())))

        assertNull(exception.errorMessageOrNull())
    }
}
