package com.litelisten.player.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val body = TextStyle(fontFamily = FontFamily.Default)

val LiteListenTypography = Typography(
    displaySmall = body.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = body.copy(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    // tnum：进度时间等宽，避免逐秒跳动。
    bodySmall = body.copy(fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum"),
)
