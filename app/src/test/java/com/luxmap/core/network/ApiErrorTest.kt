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
}
