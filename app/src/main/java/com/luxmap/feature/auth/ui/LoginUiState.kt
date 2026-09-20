package com.luxmap.feature.auth.ui

import com.luxmap.feature.auth.data.LoginFailureReason

// KHÔNG dùng khuôn 4 trạng thái Loading/Success/Empty/Error tiêu chuẩn của CLAUDE.md (khuôn đó
// dành cho màn hình tải danh sách dữ liệu). Login là luồng form + prefetch, đúng theo state
// machine mà CLAUDE.md mô tả riêng cho LoginUiState.kt: có bước "Đang tải dữ liệu ngày làm
// việc..." sau khi đăng nhập thành công, trước khi điều hướng đi (F01, mục B1).
sealed interface LoginUiState {
    data object Idle : LoginUiState

    data object LoggingIn : LoginUiState

    data object Prefetching : LoginUiState

    data object Success : LoginUiState

    data class Error(
        val reason: LoginFailureReason,
        val message: String,
    ) : LoginUiState
}
