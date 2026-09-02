package com.luxmap.core.network

import com.luxmap.core.security.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

// Attaches "Authorization: Bearer <access_token>" to every request on the authenticated
// client. Reads TokenStore synchronously — an Interceptor always runs on an OkHttp background
// thread, not the main thread, so blocking here is normal for how Interceptors work.
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
