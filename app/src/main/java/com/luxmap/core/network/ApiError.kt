package com.luxmap.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)

private val errorJson = Json { ignoreUnknownKeys = true }

fun HttpException.errorCodeOrNull(): String? =
    runCatching {
        val body = response()?.errorBody()?.string() ?: return null
        errorJson.decodeFromString<ApiErrorEnvelope>(body).error.code
    }.getOrNull()
