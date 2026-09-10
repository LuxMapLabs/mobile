package com.luxmap.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import com.luxmap.core.theme.Spacing
import com.luxmap.core.ui.components.PrimaryButton

// F01 Đăng nhập — chỉ 1 vai trò (Tổ khảo sát/sửa chữa), không có màn chọn vai trò (CLAUDE.md/A2).
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    val isSubmitting = uiState is LoginUiState.LoggingIn || uiState is LoginUiState.Prefetching

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "Đăng nhập", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.xl))

        OutlinedTextField(
            value = viewModel.identifier,
            onValueChange = viewModel::onIdentifierChange,
            label = { Text("Mã nhân viên hoặc số điện thoại") },
            singleLine = true,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.md))

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Mật khẩu") },
            singleLine = true,
            enabled = !isSubmitting,
            visualTransformation =
                if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = viewModel::onTogglePasswordVisibility) {
                    Text(if (viewModel.isPasswordVisible) "Ẩn" else "Hiện")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.lg))

        val errorState = uiState as? LoginUiState.Error
        if (errorState != null) {
            Text(
                text = errorState.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(Spacing.md))
        }

        PrimaryButton(
            text = "Đăng nhập",
            onClick = viewModel::onLoginClick,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        if (uiState is LoginUiState.Prefetching) {
            Spacer(Modifier.height(Spacing.lg))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Đang tải dữ liệu ngày làm việc...",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
