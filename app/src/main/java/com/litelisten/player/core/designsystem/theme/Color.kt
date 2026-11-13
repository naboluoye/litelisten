package com.litelisten.player.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LiteListenLightColors = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3E8FF),
    secondary = Color(0xFF5C6BC0),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF17181C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181C),
    surfaceVariant = Color(0xFFF1F0EC),
    onSurfaceVariant = Color(0xFF61636B),
    outline = Color(0xFFDCDAD4),
    error = Color(0xFFBA1A1A),
)

val LiteListenDarkColors = darkColorScheme(
    primary = Color(0xFF9FB0FF),
    onPrimary = Color(0xFF101114),
    primaryContainer = Color(0xFF2A3566),
    secondary = Color(0xFF9FB0FF),
    background = Color(0xFF101114),
    onBackground = Color(0xFFEDEDEF),
    surface = Color(0xFF17181C),
    onSurface = Color(0xFFEDEDEF),
    surfaceVariant = Color(0xFF23252B),
    onSurfaceVariant = Color(0xFF9A9CA5),
    outline = Color(0xFF2E3036),
    error = Color(0xFFFFB4AB),
)

/** OLED 纯黑：无阴影，靠 1px outline 分层。 */
val LiteListenBlackColors = LiteListenDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0C),
    surfaceVariant = Color(0xFF141417),
)
