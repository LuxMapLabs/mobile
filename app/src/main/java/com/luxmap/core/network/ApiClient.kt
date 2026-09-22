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

// BASE_URL comes from BuildConfig.API_BASE_URL (see app/build.gradle.kts) — defaults to
// 10.0.2.2, the loopback address Android Emulator uses to reach the host machine, matching the
// http port in luxmap_backend/src/LuxMap.Api/Properties/launchSettings.json.
object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonConverterFactory = json.asConverterFactory("application/json".toMediaType())

    // Never log bodies or headers: login and refresh bodies hold the password and tokens, and the
    // Authorization header holds the access token. Release logs nothing; debug logs only the
    // request line and status code.
    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }

    // "Plain" client — no Authorization header, no Authenticator. AuthApi (login/refresh/
    // logout) uses this client: refresh doesn't need an access token, and it must not go
    // through its own Authenticator — otherwise a failed refresh (401) would make the
    // Authenticator try to refresh again, looping forever.
    fun plainOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    // Client for every other API (map, work orders...) — attaches the Bearer token
    // automatically and refreshes it on expiry through the authenticator. AuthApi doesn't use
    // this client, so the interceptor doesn't need to skip any path.
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
