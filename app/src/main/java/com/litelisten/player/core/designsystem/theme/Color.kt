package com.litelisten.player.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 清爽雾蓝主调：主色沉稳不刺眼，背景近白微冷，层级靠浅色容器而非重边框区分。
 */
val LiteListenLightColors = lightColorScheme(
    primary = Color(0xFF3C6E9F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E7F5),
    onPrimaryContainer = Color(0xFF1E3F5C),
    secondary = Color(0xFF5C8BB8),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEAF5),
    onSecondaryContainer = Color(0xFF23435C),
    tertiary = Color(0xFF4E7E8E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFBFD),
    onBackground = Color(0xFF1A2430),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A2430),
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = Color(0xFF5E6B7A),
    surfaceTint = Color(0xFF3C6E9F),
    outline = Color(0xFFD3DCE6),
    outlineVariant = Color(0xFFE5EAF0),
    error = Color(0xFFC0423E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE4E2),
    onErrorContainer = Color(0xFF7F201D),
)

val LiteListenDarkColors = darkColorScheme(
    primary = Color(0xFF9CC5EA),
    onPrimary = Color(0xFF12283C),
    primaryContainer = Color(0xFF2A4B68),
    onPrimaryContainer = Color(0xFFD5E6F5),
    secondary = Color(0xFFA5C6E4),
    onSecondary = Color(0xFF142B3D),
    secondaryContainer = Color(0xFF2C4B63),
    onSecondaryContainer = Color(0xFFDCEBF8),
    tertiary = Color(0xFF9BC8D4),
    onTertiary = Color(0xFF123039),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF171E26),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF222B35),
    onSurfaceVariant = Color(0xFFA3B1C0),
    surfaceTint = Color(0xFF9CC5EA),
    outline = Color(0xFF3A4655),
    outlineVariant = Color(0xFF2A3441),
    error = Color(0xFFF2B4B0),
    onError = Color(0xFF601A17),
    errorContainer = Color(0xFF7F2A26),
    onErrorContainer = Color(0xFFFBE4E2),
)

/** OLED 纯黑：无阴影，靠 1px outline 分层。 */
val LiteListenBlackColors = LiteListenDarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF080B0F),
    surfaceVariant = Color(0xFF141A20),
)
