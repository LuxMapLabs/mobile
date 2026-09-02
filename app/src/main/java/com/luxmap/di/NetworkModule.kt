package com.luxmap.di

import com.luxmap.core.network.ApiClient
import com.luxmap.core.network.AuthApi
import com.luxmap.core.network.AuthHeaderInterceptor
import com.luxmap.core.network.TokenAuthenticator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

// Client "trần" cho AuthApi (login/refresh/logout) — không có Authorization header, không có
// Authenticator (xem ApiClient.kt vì sao refresh không được đi qua chính Authenticator của nó).
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlainClient

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindsModule {
    @Binds
    abstract fun bindAuthHeaderInterceptor(impl: AuthHeaderInterceptor): Interceptor

    @Binds
    abstract fun bindTokenAuthenticator(impl: TokenAuthenticator): Authenticator
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    @PlainClient
    fun providePlainOkHttpClient(): OkHttpClient = ApiClient.plainOkHttpClient()

    @Provides
    @Singleton
    @PlainClient
    fun providePlainRetrofit(
        @PlainClient client: OkHttpClient,
    ): Retrofit = ApiClient.retrofit(client)

    @Provides
    @Singleton
    fun provideAuthApi(
        @PlainClient retrofit: Retrofit,
    ): AuthApi = retrofit.create(AuthApi::class.java)

    // Client dùng cho mọi API khác ngoài auth — tự gắn Bearer token, tự làm mới khi hết hạn.
    @Provides
    @Singleton
    fun provideAuthenticatedOkHttpClient(
        authHeaderInterceptor: Interceptor,
        tokenAuthenticator: Authenticator,
    ): OkHttpClient = ApiClient.authenticatedOkHttpClient(authHeaderInterceptor, tokenAuthenticator)

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = ApiClient.retrofit(client)
}
