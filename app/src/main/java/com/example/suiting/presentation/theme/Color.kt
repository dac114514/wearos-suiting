package com.example.suiting.presentation.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

// Wear OS Material3 Expression 风格配色
// 深黑背景 + 鲜明紫色主色 + 高对比文字
internal val wearColorScheme: ColorScheme = ColorScheme(
    primary                = Color(0xFFCFBCFF),  // 亮紫
    onPrimary              = Color(0xFF21005D),
    primaryContainer       = Color(0xFF381E72),
    onPrimaryContainer     = Color(0xFFEADDFF),
    secondary              = Color(0xFFCBC2DB),
    onSecondary            = Color(0xFF332D41),
    secondaryContainer     = Color(0xFF4A4458),
    onSecondaryContainer   = Color(0xFFE8DEF8),
    tertiary               = Color(0xFFEFB8C8),
    onTertiary             = Color(0xFF492532),
    tertiaryContainer      = Color(0xFF633B48),
    onTertiaryContainer    = Color(0xFFFFD8E4),
    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
    background             = Color(0xFF000000),  // 纯黑，OLED 省电
    onBackground           = Color(0xFFFFFFFF),
    onSurface              = Color(0xFFECE6F0),
    onSurfaceVariant       = Color(0xFFCAC4D0),
    outline                = Color(0xFF938F99),
    outlineVariant         = Color(0xFF49454F),
    surfaceContainerLow    = Color(0xFF0F0D13),
    surfaceContainer       = Color(0xFF1C1B1F),
    surfaceContainerHigh   = Color(0xFF2B2930),
    primaryDim             = Color(0xFF9A82DB),
    secondaryDim           = Color(0xFF9A8FAD),
    tertiaryDim            = Color(0xFFB58392),
)
