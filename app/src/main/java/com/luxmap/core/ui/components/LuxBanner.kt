package com.luxmap.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.luxmap.core.theme.Dimens
import com.luxmap.core.theme.LuxMapTheme
import com.luxmap.core.theme.LuxTheme
import com.luxmap.core.theme.Spacing

enum class LuxBannerTone { INFO, WARNING, ERROR, SUCCESS, NEUTRAL }

private data class BannerStyle(val container: Color, val content: Color, val icon: ImageVector)

private val BannerIconSize = 20.dp

@Composable
private fun LuxBannerTone.style(): BannerStyle =
    when (this) {
        LuxBannerTone.INFO ->
            BannerStyle(LuxTheme.colors.infoContainer, LuxTheme.colors.onInfoContainer, Icons.Filled.Info)
        LuxBannerTone.WARNING ->
            BannerStyle(LuxTheme.colors.warningContainer, LuxTheme.colors.onWarningContainer, Icons.Filled.WarningAmber)
        LuxBannerTone.ERROR ->
            BannerStyle(
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
                Icons.Filled.ErrorOutline,
            )
        LuxBannerTone.SUCCESS ->
            BannerStyle(LuxTheme.colors.successContainer, LuxTheme.colors.onSuccessContainer, Icons.Filled.CheckCircle)
        LuxBannerTone.NEUTRAL ->
            BannerStyle(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.colorScheme.onSurfaceVariant,
                Icons.Outlined.Info,
            )
    }

@Composable
fun LuxBanner(
    message: String,
    tone: LuxBannerTone,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val style = tone.style()
    val announcement = if (tone == LuxBannerTone.ERROR) LiveRegionMode.Assertive else LiveRegionMode.Polite
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(style.container, MaterialTheme.shapes.medium)
                .padding(Spacing.md),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = announcement },
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.padding(top = 2.dp).size(BannerIconSize),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (title != null) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, color = style.content)
                }
                Text(text = message, style = MaterialTheme.typography.bodyMedium, color = style.content)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End).heightIn(min = Dimens.minTouchTarget),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.textButtonColors(contentColor = style.content),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@LuxPreviews
@Composable
private fun LuxBannerPreview() {
    LuxMapTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LuxBanner(
                tone = LuxBannerTone.ERROR,
                title = "Quá hạn nộp báo cáo",
                message = "Form đã khoá. Liên hệ Kỹ sư bảo trì để được xử lý.",
            )
            LuxBanner(
                tone = LuxBannerTone.WARNING,
                title = "Kỹ sư bảo trì yêu cầu bổ sung",
                message = "Chụp lại ảnh chóa đèn sau sửa chữa.",
            )
            LuxBanner(
                tone = LuxBannerTone.INFO,
                message = "Đang xem dữ liệu đã lưu · cập nhật lúc 21:45",
                actionLabel = "Tải lại",
                onAction = {},
            )
            LuxBanner(tone = LuxBannerTone.SUCCESS, message = "Đã lưu trên thiết bị — sẽ đồng bộ khi có mạng")
            LuxBanner(tone = LuxBannerTone.NEUTRAL, message = "Đã gửi yêu cầu hủy · chờ Kỹ sư bảo trì duyệt")
        }
    }
}
