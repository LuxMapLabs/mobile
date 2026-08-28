package com.luxmap.core.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

// Chỉ lưu cờ đăng nhập (mock) — CHƯA mã hoá bằng Android Keystore vì chưa có JWT thật để bảo vệ
// (xem CLAUDE.md mục "Khoảng trống đã biết" / FM-05). Khi có token thật từ FM-04, bọc thêm lớp
// Keystore ở đây mà không đổi API `observeIsLoggedIn`/`setLoggedIn` cho phía gọi.
@Singleton
class TokenStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun observeIsLoggedIn(): Flow<Boolean> =
            context.sessionDataStore.data.map { prefs -> prefs[IS_LOGGED_IN_KEY] ?: false }

        suspend fun setLoggedIn(value: Boolean) {
            context.sessionDataStore.edit { prefs -> prefs[IS_LOGGED_IN_KEY] = value }
        }

        private companion object {
            val IS_LOGGED_IN_KEY = booleanPreferencesKey("is_logged_in")
        }
    }
