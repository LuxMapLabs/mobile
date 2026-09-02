package com.luxmap.core.network

import com.luxmap.feature.auth.data.AuthTokenResponseDto
import com.luxmap.feature.auth.data.LoginRequestDto
import com.luxmap.feature.auth.data.LogoutRequestDto
import com.luxmap.feature.auth.data.RefreshRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Route checked against AuthController.cs (BE-07): [Route("api/v{version:apiVersion}/auth")], v1.0.
// register is NOT here — FM-05 only covers login (see mobile CLAUDE.md). Field Crew accounts
// are already set up for each user; the app has no self-registration screen.
interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(
        @Body body: LoginRequestDto,
    ): AuthTokenResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequestDto,
    ): AuthTokenResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(
        @Body body: LogoutRequestDto,
    ): Response<Unit>
}
