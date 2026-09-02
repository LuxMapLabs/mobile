package com.luxmap.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.luxmap.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

// BASE_URL cấu hình qua BuildConfig.API_BASE_URL (xem app/build.gradle.kts) — mặc định trỏ
// 10.0.2.2, địa chỉ loopback dành cho Android Emulator, khớp cổng http trong
// luxmap_backend/src/LuxMap.Api/Properties/launchSettings.json.
object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonConverterFactory = json.asConverterFactory("application/json".toMediaType())

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    // Client "trần" — không gắn Authorization, không có Authenticator. AuthApi (login/refresh/
    // logout) đi qua client này: refresh không cần access token, và không được tự đi qua client
    // có Authenticator của chính nó — nếu không, một lần refresh thất bại (401) sẽ khiến
    // Authenticator lại cố refresh, gọi vòng lặp vô hạn.
    fun plainOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    // Client cho mọi API còn lại (bản đồ, work order...) — tự gắn Bearer token, tự làm mới token
    // khi hết hạn qua authenticator. Vì AuthApi không dùng client này, interceptor không cần loại
    // trừ theo path.
    fun authenticatedOkHttpClient(
        authHeaderInterceptor: Interceptor,
        tokenAuthenticator: Authenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authHeaderInterceptor)
            .addInterceptor(loggingInterceptor)
            .authenticator(tokenAuthenticator)
            .build()

    fun retrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(jsonConverterFactory)
            .build()
}
