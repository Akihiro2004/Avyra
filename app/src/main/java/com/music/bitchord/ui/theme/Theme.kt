package com.music.bitchord.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Avyra's core violet. It is intentionally unrelated to Spotify green or Apple Music red. */
val AvyraViolet = Color(0xFF7057FF)
val AvyraAqua = Color(0xFF35E2C1)
val AvyraCoral = Color(0xFFFF7A90)

private val DarkColors = darkColorScheme(
    primary = AvyraViolet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF292050),
    onPrimaryContainer = Color(0xFFE7E1FF),
    secondary = AvyraAqua,
    onSecondary = Color(0xFF00251E),
    secondaryContainer = Color(0xFF123D37),
    onSecondaryContainer = Color(0xFFB7F6E8),
    tertiary = AvyraCoral,
    onTertiary = Color(0xFF3B0712),
    background = Color(0xFF080A12),
    onBackground = Color(0xFFF2F3FA),
    surface = Color(0xFF111522),
    onSurface = Color(0xFFF2F3FA),
    surfaceVariant = Color(0xFF1A2030),
    onSurfaceVariant = Color(0xFFA9B0C3),
    outline = Color(0xFF30384D),
    outlineVariant = Color(0xFF242B3C),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5940EA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E3FF),
    onPrimaryContainer = Color(0xFF211269),
    secondary = Color(0xFF007F6C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F3E8),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFB83356),
    onTertiary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF10121B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF10121B),
    surfaceVariant = Color(0xFFECEFFA),
    onSurfaceVariant = Color(0xFF5E6577),
    outline = Color(0xFFD1D6E4),
    outlineVariant = Color(0xFFE1E5EF),
)

/*
 * A neutral Android sans keeps the brand portable and avoids shipping a
 * platform-owner typeface. Character comes from the wider display rhythm,
 * calmer weights and compact labels rather than imitating another app.
 */
private val AvyraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W700,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W700,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 23.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.25.sp,
    ),
)

private val AvyraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun AvyraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AvyraTypography,
        shapes = AvyraShapes,
        content = content,
    )
}

/** Draws status and navigation glyphs for the theme the app is actually painting. */
@Composable
fun SystemBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = (view.context as? Activity)?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = dark
            isAppearanceLightNavigationBars = dark
        }
    }
}
