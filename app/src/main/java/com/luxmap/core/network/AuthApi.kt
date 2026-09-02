package com.luxmap.core.network

import com.luxmap.feature.auth.data.AuthTokenResponseDto
import com.luxmap.feature.auth.data.LoginRequestDto
import com.luxmap.feature.auth.data.LogoutRequestDto
import com.luxmap.feature.auth.data.RefreshRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

// Route xác nhận từ AuthController.cs (BE-07): [Route("api/v{version:apiVersion}/auth")], v1.0.
// register KHÔNG có ở đây — FM-05 chỉ làm luồng đăng nhập (xem CLAUDE.md mobile), tài khoản
// Tổ khảo sát/sửa chữa được cấp sẵn, không tự đăng ký qua app.
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
