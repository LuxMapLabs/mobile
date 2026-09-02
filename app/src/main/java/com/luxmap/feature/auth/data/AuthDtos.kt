package com.luxmap.feature.auth.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// DTO ánh xạ đúng field JSON của backend (snake_case, JsonNamingPolicy.SnakeCaseLower).
// Xác nhận trực tiếp từ luxmap_backend/src/LuxMap.Modules.Identity/Auth/AuthContracts.cs (BE-07),
// KHÔNG đoán tên field.

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class RefreshRequestDto(
    val refresh_token: String,
)

@Serializable
data class LogoutRequestDto(
    val refresh_token: String,
)

// AuthTokenResponse: đúng 4 field, dùng chung cho login và refresh.
@Serializable
data class AuthTokenResponseDto(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int,
)

// Khuôn lỗi chung toàn API — CLAUDE.md backend mục 0: { "error": { "code", "message", "details" } }.
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
