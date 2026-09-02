package com.luxmap.core.network

import com.luxmap.core.security.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// Gắn "Authorization: Bearer <access_token>" cho mọi request đi qua client đã xác thực. Đọc
// đồng bộ từ TokenStore — Interceptor luôn chạy trên luồng nền của OkHttp, không phải main
// thread, nên chặn (block) ở đây là hành vi bình thường của cơ chế Interceptor.
class AuthHeaderInterceptor
    @Inject
    constructor(
        private val tokenStore: TokenStore,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val accessToken = tokenStore.currentTokensBlocking()?.accessToken
            val request =
                if (accessToken != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                } else {
                    chain.request()
                }
            return chain.proceed(request)
        }
    }
