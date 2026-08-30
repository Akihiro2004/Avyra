package com.avyra.music.ui.theme

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

/**
 * Avyra's accent, and the two colours allowed to sit beside it.
 *
 * A single vivid blue carries the whole interface: it is the only saturated
 * thing on screen that isn't album art, which is what makes a tapped control
 * read as tapped without any of the surfaces needing to be tinted. The neutrals
 * below are mixed *towards* it rather than being pure grey — a faint cool cast,
 * far too slight to read as blue on its own, but enough that the accent looks
 * like it belongs to the surface rather than being dropped onto it.
 *
 * [AvyraCyan] and [AvyraPink] are deliberately close relatives rather than
 * contrasts. They exist for the handful of places Material insists on a
 * secondary and a tertiary, and the palette is at its best when neither is
 * especially visible.
 */
val AvyraBlue = Color(0xFF0A84FF)
val AvyraCyan = Color(0xFF5AC8FA)
val AvyraPink = Color(0xFFFF6482)

/**
 * The accent the Replay screens colour their rank badges with.
 *
 * Upstream's name, kept so those files merge cleanly, but pointed at Avyra's
 * own accent rather than the red it names — a badge in somebody else's brand
 * colour is the one thing on those screens that would look borrowed. Rename it
 * if Replay ever wants an accent of its own; nothing else reads it.
 */
val AccentRed = AvyraBlue

/*
 * The dark scheme is the one the app is designed in — it opens on it, and the
 * player fills the screen with artwork whichever theme is set.
 *
 * The background is near-black rather than the charcoal Material reaches for by
 * default, because every surface above it is a card and every card wants an
 * edge. On OLED it is also simply off, which is worth having for an app people
 * leave on a lock screen.
 */
private val DarkColors = darkColorScheme(
    primary = AvyraBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF10294A),
    onPrimaryContainer = Color(0xFFCFE4FF),
    secondary = AvyraCyan,
    onSecondary = Color(0xFF00212E),
    secondaryContainer = Color(0xFF10333F),
    onSecondaryContainer = Color(0xFFBFE9FA),
    tertiary = AvyraPink,
    onTertiary = Color(0xFF3D0716),
    background = Color(0xFF05070B),
    onBackground = Color(0xFFF5F6F8),
    surface = Color(0xFF121418),
    onSurface = Color(0xFFF5F6F8),
    surfaceVariant = Color(0xFF1C1F26),
    onSurfaceVariant = Color(0xFF9BA1AD),
    // Bright enough to draw a hairline that survives a phone at half
    // brightness, dim enough that a list of them doesn't read as a grid.
    outline = Color(0xFF2E323C),
    outlineVariant = Color(0xFF23262E),
)

/*
 * Light is the same palette with the stack inverted: the page is the tinted
 * grey and the cards are white, rather than the other way round. That is what
 * keeps artwork sitting *on* the page in both themes instead of being punched
 * out of it in one and floating in the other.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9FF),
    onPrimaryContainer = Color(0xFF00294D),
    secondary = Color(0xFF0B7FA8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7E9F7),
    onSecondaryContainer = Color(0xFF00323F),
    tertiary = Color(0xFFD6335A),
    onTertiary = Color.White,
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF0A0C10),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0C10),
    surfaceVariant = Color(0xFFEBEDF3),
    onSurfaceVariant = Color(0xFF62666F),
    outline = Color(0xFFD3D6DE),
    outlineVariant = Color(0xFFE4E7EE),
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
