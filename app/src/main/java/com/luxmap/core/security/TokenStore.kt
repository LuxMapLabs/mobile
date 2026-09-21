package com.luxmap.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

// access_token/refresh_token are stored as ciphertext (TokenCipher, AndroidKeyStore) inside
// DataStore — the exact pairing mobile CLAUDE.md asks for: "DataStore stores the token...
// Android Keystore protects it". expires_at is a plain epoch-second number; it isn't sensitive,
// so it's stored unencrypted for a fast read.
@Singleton
class TokenStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val cipher: TokenCipher,
    ) {
        fun observeIsLoggedIn(): Flow<Boolean> =
            context.sessionDataStore.data.map { prefs -> prefs[REFRESH_TOKEN_KEY] != null }

        // The name used to sign in, shown on the Profile screen. It is not secret, so it is stored
        // as plain text. Null for sessions saved before this was added.
        fun observeUsername(): Flow<String?> = context.sessionDataStore.data.map { prefs -> prefs[USERNAME_KEY] }

        // rememberMe and username: null means "keep the current value". Login passes both; the
        // token refresh (TokenAuthenticator) passes nothing, so a refresh never changes it.
        suspend fun saveSession(
            accessToken: String,
            refreshToken: String,
            expiresInSeconds: Int,
            rememberMe: Boolean? = null,
            username: String? = null,
        ) {
            val expiresAt = (System.currentTimeMillis() / MILLIS_PER_SECOND) + expiresInSeconds
            context.sessionDataStore.edit { prefs ->
                prefs[ACCESS_TOKEN_KEY] = cipher.encrypt(accessToken)
                prefs[REFRESH_TOKEN_KEY] = cipher.encrypt(refreshToken)
                prefs[EXPIRES_AT_KEY] = expiresAt
                if (rememberMe != null) prefs[REMEMBER_ME_KEY] = rememberMe
                if (username != null) prefs[USERNAME_KEY] = username
            }
        }

        // Called once when the app is launched fresh. A missing flag counts as "remembered", so
        // sessions saved before this option existed are not logged out by the update.
        suspend fun clearIfNotRemembered() {
            val remembered = context.sessionDataStore.data.first()[REMEMBER_ME_KEY] ?: true
            if (!remembered) clear()
        }

        // If the tokens cannot be decrypted (for example the Keystore key is gone after a restore
        // or a lock screen reset), the saved session is useless. Clear it so the app goes back to
        // Login instead of crashing.
        suspend fun currentTokens(): SessionTokens? =
            try {
                toSessionTokens(context.sessionDataStore.data.first())
            } catch (_: GeneralSecurityException) {
                clear()
                null
            } catch (_: IllegalArgumentException) {
                clear()
                null
            }

        // Used by TokenAuthenticator (okhttp3.Authenticator) — a synchronous API that runs on
        // an OkHttp background thread, not the main thread, so runBlocking is fine here.
        fun currentTokensBlocking(): SessionTokens? = runBlocking { currentTokens() }

        suspend fun clear() {
            context.sessionDataStore.edit { prefs ->
                prefs.remove(ACCESS_TOKEN_KEY)
                prefs.remove(REFRESH_TOKEN_KEY)
                prefs.remove(EXPIRES_AT_KEY)
                prefs.remove(REMEMBER_ME_KEY)
                prefs.remove(USERNAME_KEY)
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
            val REMEMBER_ME_KEY = booleanPreferencesKey("remember_me")
            val USERNAME_KEY = stringPreferencesKey("username")
            const val MILLIS_PER_SECOND = 1000L
        }
    }
