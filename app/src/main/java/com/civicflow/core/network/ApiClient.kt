package com.civicflow.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// Backend chưa sẵn sàng (xem "Tầng API" trong CLAUDE.md) — BASE_URL là placeholder,
// cập nhật khi có endpoint thật, chưa cần thay đổi UI/repository khi đổi.
object ApiClient {
    private const val BASE_URL = "https://api.civicflow.example.com/"

    private val json = Json { ignoreUnknownKeys = true }

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    private val okHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
