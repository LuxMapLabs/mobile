package com.luxmap.feature.auth.data

import com.luxmap.core.security.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// Chấp nhận mọi mã nhân viên/SĐT + mật khẩu không rỗng — chưa có backend thật (FM-04 đang chờ
// BE-05, xem CLAUDE.md mục "Tầng API"). Đổi sang RealAuthRepository qua Hilt binding khi FM-04
// xong, không sửa ViewModel/UI.
@Singleton
class FakeAuthRepository
    @Inject
    constructor(
        private val tokenStore: TokenStore,
    ) : AuthRepository {
        override suspend fun login(
            identifier: String,
            password: String,
        ): Result<Unit> {
            if (identifier.isBlank() || password.isBlank()) {
                return Result.failure(IllegalArgumentException("Vui lòng nhập đầy đủ thông tin"))
            }
            delay(LOGIN_DELAY_MS)
            tokenStore.setLoggedIn(true)
            return Result.success(Unit)
        }

        override fun observeIsLoggedIn(): Flow<Boolean> = tokenStore.observeIsLoggedIn()

        private companion object {
            const val LOGIN_DELAY_MS = 600L
        }
    }
