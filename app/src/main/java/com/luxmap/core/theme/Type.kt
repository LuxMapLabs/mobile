package com.luxmap.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Thang chữ theo mục 3 Design System v2.0. Nội dung đọc/hành động không nhỏ hơn 16sp;
// Caption (14sp) chỉ dùng cho metadata phụ, không dùng cho cảnh báo/hướng dẫn.
val Typography =
    Typography(
        // Display Number 28sp/34sp/700 — số lớn: lux, số lượng, quãng đường
        displayLarge =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
            ),
        // H1 20sp/26sp/700 — tiêu đề màn hình
        titleLarge =
            TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        // H2 17sp/24sp/600 — tiêu đề section/card
        titleMedium =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            ),
        // Body 16sp/24sp/400 — nội dung chính
        bodyLarge =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // Body Strong 16sp/24sp/600 — label quan trọng, chữ trên nút (Button mặc định dùng labelLarge)
        labelLarge =
            TextStyle(
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        // Caption 14sp/20sp/500 — timestamp, metadata phụ
        bodySmall =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        labelSmall =
            TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
    )
