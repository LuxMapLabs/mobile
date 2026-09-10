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

// "Plain" client for AuthApi (login/refresh/logout) — no Authorization header, no
// Authenticator (see ApiClient.kt for why refresh must not go through its own Authenticator).
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

    // Client used by every API other than auth — attaches the Bearer token and refreshes it
    // automatically on expiry.
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
