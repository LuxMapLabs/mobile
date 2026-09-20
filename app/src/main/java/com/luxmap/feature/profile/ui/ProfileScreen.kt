package com.luxmap.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.Spacing

// F15 Cá nhân — for now only the account block and Logout (FM-26). Upload settings and offline
// map cache come later. There is no "full name / area" yet because the backend has no
// GET /users/me, so the screen shows the username used to sign in.
@Composable
fun ProfileScreen(
    username: String,
    isLoggingOut: Boolean,
    onConfirmLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Survives rotation, so the dialog does not disappear while the user is deciding.
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(Spacing.xl),
    ) {
        Text(text = "Cá nhân", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.xl))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(username = username)
            Spacer(Modifier.height(Spacing.md))
            Text(text = username, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Tổ khảo sát/sửa chữa",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f))

        // Outlined (not filled) red: logout only ends the session on this device, so it should
        // be easy to find but not look like the main action of the screen.
        OutlinedButton(
            onClick = { showConfirmDialog = true },
            enabled = !isLoggingOut,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Dimens.minTouchTarget),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            if (isLoggingOut) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Đăng xuất")
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Đăng xuất khỏi thiết bị này?") },
            text = { Text("Bạn cần đăng nhập lại để tiếp tục sử dụng ứng dụng.") },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
                ) {
                    Text("Huỷ")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onConfirmLogout()
                    },
                    modifier = Modifier.defaultMinSize(minHeight = Dimens.minTouchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Đăng xuất")
                }
            },
        )
    }
}

// Round avatar showing the first letter of the username (no profile photo in the data yet).
@Composable
private fun Avatar(username: String) {
    Box(
        modifier =
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
