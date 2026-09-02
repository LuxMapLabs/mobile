package com.luxmap.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

// access_token/refresh_token lưu dưới dạng ciphertext (TokenCipher, AndroidKeyStore) trong
// DataStore — đúng cặp công nghệ CLAUDE.md mobile chỉ định: "DataStore lưu token... Android
// Keystore bảo vệ token". expires_at là epoch giây, không nhạy cảm nên lưu thô để đọc nhanh.
@Singleton
class TokenStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val cipher: TokenCipher,
    ) {
        fun observeIsLoggedIn(): Flow<Boolean> =
            context.sessionDataStore.data.map { prefs -> prefs[REFRESH_TOKEN_KEY] != null }

        suspend fun saveSession(
            accessToken: String,
            refreshToken: String,
            expiresInSeconds: Int,
        ) {
            val expiresAt = (System.currentTimeMillis() / MILLIS_PER_SECOND) + expiresInSeconds
            context.sessionDataStore.edit { prefs ->
                prefs[ACCESS_TOKEN_KEY] = cipher.encrypt(accessToken)
                prefs[REFRESH_TOKEN_KEY] = cipher.encrypt(refreshToken)
                prefs[EXPIRES_AT_KEY] = expiresAt
            }
        }

        suspend fun currentTokens(): SessionTokens? = toSessionTokens(context.sessionDataStore.data.first())

        // Dùng cho TokenAuthenticator (okhttp3.Authenticator) — API đồng bộ, chạy trên luồng nền
        // của OkHttp, không phải main thread nên runBlocking chấp nhận được ở đây.
        fun currentTokensBlocking(): SessionTokens? = runBlocking { currentTokens() }

        suspend fun clear() {
            context.sessionDataStore.edit { prefs ->
                prefs.remove(ACCESS_TOKEN_KEY)
                prefs.remove(REFRESH_TOKEN_KEY)
                prefs.remove(EXPIRES_AT_KEY)
            }
        }

        private fun toSessionTokens(prefs: Preferences): SessionTokens? {
            val access = prefs[ACCESS_TOKEN_KEY] ?: return null
            val refresh = prefs[REFRESH_TOKEN_KEY] ?: return null
            val expiresAt = prefs[EXPIRES_AT_KEY] ?: return null
            return SessionTokens(cipher.decrypt(access), cipher.decrypt(refresh), expiresAt)
        }

        private companion object {
            val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
            val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
            val EXPIRES_AT_KEY = longPreferencesKey("expires_at")
            const val MILLIS_PER_SECOND = 1000L
        }
    }
