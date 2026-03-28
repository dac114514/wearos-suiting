package com.example.suiting.presentation.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Typography

// Wear OS Expression 风格：大字重字体
val Typography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 34.sp, lineHeight = 40.sp),
    displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,   fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
