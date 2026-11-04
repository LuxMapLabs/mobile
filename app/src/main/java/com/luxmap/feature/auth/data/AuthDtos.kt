package com.luxmap.feature.auth.data

import kotlinx.serialization.SerialName
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

// GET /api/v1/auth/me response (AuthContracts.cs CurrentUserResponse) — read from the database,
// not decoded from the JWT, so a fresh commune assignment shows up right away instead of waiting
// for the next login.
@Serializable
data class CurrentUserResponseDto(
    @SerialName("user_id") val userId: String,
    val username: String,
    val email: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    @SerialName("commune_ids") val communeIds: List<String>,
    val communes: List<CommuneSummaryDto> = emptyList(),
    @SerialName("map_bounds") val mapBounds: MapBoundsDto? = null,
)

// COMMUNE-BBOX (luxmap_backend AuthContracts.cs) — a commune as the signed-in user sees it.
// bbox/center are null until a System Admin sets a frame for that commune on Web GIS.
@Serializable
data class CommuneSummaryDto(
    @SerialName("commune_id") val communeId: String,
    val name: String,
    val bbox: CommuneBboxDto? = null,
    val center: MapCenterDto? = null,
)

// The union box over every commune the caller is assigned to, plus its centre point — the one
// bbox GET /map/poles and GET /map/segments need (see RealMapRepository, FM-15).
@Serializable
data class MapBoundsDto(
    val bbox: CommuneBboxDto,
    val center: MapCenterDto,
)

@Serializable
data class CommuneBboxDto(
    @SerialName("min_lng") val minLng: Double,
    @SerialName("min_lat") val minLat: Double,
    @SerialName("max_lng") val maxLng: Double,
    @SerialName("max_lat") val maxLat: Double,
)

@Serializable
data class MapCenterDto(
    val lat: Double,
    val lng: Double,
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
