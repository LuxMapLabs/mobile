package com.luxmap.feature.auth.data

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(
        identifier: String,
        password: String,
    ): Result<Unit>

    // Best-effort: luôn xoá phiên cục bộ dù gọi API logout thất bại (offline) — FM-26 dùng lại
    // hàm này khi màn Cá nhân có nút đăng xuất, không cần đổi gì ở tầng repository.
    suspend fun logout(): Result<Unit>

    fun observeIsLoggedIn(): Flow<Boolean>
}
