package com.luxmap.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.luxmap.R

// Be Vietnam Pro (Design System v3.0.1) — 4 file .ttf bundle sẵn trong res/font/, KHÔNG dùng
// Google Fonts downloadable provider vì provider đó cần Play Services + tải font qua mạng lúc
// mở app lần đầu, ngược nguyên tắc offline-first (CLAUDE.md).
val BeVietnamPro =
    FontFamily(
        Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
        Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
        Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
        Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
    )

private fun luxStyle(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
) = TextStyle(
    fontFamily = BeVietnamPro,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

val Typography =
    Typography(
        displayLarge = luxStyle(34, 42, FontWeight.Bold),
        displayMedium = luxStyle(30, 38, FontWeight.Bold),
        displaySmall = luxStyle(28, 36, FontWeight.Bold),
        headlineLarge = luxStyle(26, 36, FontWeight.Bold),
        headlineMedium = luxStyle(24, 33, FontWeight.Bold),
        headlineSmall = luxStyle(22, 30, FontWeight.SemiBold),
        titleLarge = luxStyle(20, 28, FontWeight.Bold),
        titleMedium = luxStyle(17, 24, FontWeight.SemiBold),
        titleSmall = luxStyle(16, 24, FontWeight.SemiBold),
        bodyLarge = luxStyle(16, 24, FontWeight.Normal),
        bodyMedium = luxStyle(16, 22, FontWeight.Normal),
        bodySmall = luxStyle(14, 20, FontWeight.Medium),
        labelLarge = luxStyle(16, 24, FontWeight.SemiBold),
        labelMedium = luxStyle(14, 20, FontWeight.SemiBold),
        labelSmall = luxStyle(14, 20, FontWeight.Medium),
    )

// v2.1 redesign typography scale — additive TextStyle vals, deliberately NOT remapped onto
// the Typography object's existing M3 slots (see Phase 0 plan rationale: several slot names
// here collide with different current meanings, e.g. the existing bodyMedium is 16sp/400 body
// text while the spec's "label" role is 14sp/500 metadata — redefining the slot in place would
// silently reskin every current MaterialTheme.typography.bodyMedium call site). Screens migrate
// to these explicit vals one at a time in their own redesign phase.
val NumericTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp)
val DisplayTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp)
val HeadlineTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
val MetricTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
val TitleTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
val BodyStrongTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
val BodyTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
val LabelTextStyle =
    TextStyle(fontFamily = BeVietnamPro, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
val OverlineTextStyle = Typography.labelMedium
val BadgeTextStyle = Typography.labelMedium
