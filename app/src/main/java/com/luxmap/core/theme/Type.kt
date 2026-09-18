package com.luxmap.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

// Thang chữ theo mục 3 Design System v2.0. Nội dung đọc/hành động không nhỏ hơn 16sp;
// Caption (14sp) chỉ dùng cho metadata phụ, không dùng cho cảnh báo/hướng dẫn.
val Typography =
    Typography(
        // Display Number 28sp/34sp/700 — số lớn: lux, số lượng, quãng đường
        displayLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        // H1 20sp/26sp/700 — tiêu đề màn hình
        titleLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        // H2 17sp/24sp/600 — tiêu đề section/card
        titleMedium =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        // Body 16sp/24sp/400 — nội dung chính
        bodyLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // Body Strong 16sp/24sp/600 — label quan trọng, chữ trên nút (Button mặc định dùng labelLarge)
        labelLarge =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // Caption 14sp/20sp/500 — timestamp, metadata phụ
        bodySmall =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = BeVietnamPro,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
    )
