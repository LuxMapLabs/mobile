package com.luxmap.feature.auth.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// DTO fields match the backend JSON exactly (snake_case, JsonNamingPolicy.SnakeCaseLower).
// Checked directly against luxmap_backend/src/LuxMap.Modules.Identity/Auth/AuthContracts.cs
// (BE-07) — field names are NOT guessed.

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

// AuthTokenResponse: exactly 4 fields, shared by both login and refresh.
@Serializable
data class AuthTokenResponseDto(
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int,
)

// Shared error shape for the whole API — backend CLAUDE.md section 0:
// { "error": { "code", "message", "details" } }.
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
