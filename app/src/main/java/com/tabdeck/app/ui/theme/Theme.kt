package com.tabdeck.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tabdeck.app.model.AccentStyle
import com.tabdeck.app.model.AppSettings
import com.tabdeck.app.model.ThemeMode

private data class AccentPalette(
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val tertiary: Color,
)

private fun palette(style: AccentStyle): AccentPalette = when (style) {
    AccentStyle.VIOLET -> AccentPalette(Color(0xFF6650C8), Color(0xFFC8B6FF), Color(0xFF006B60), Color(0xFF8B4D00))
    AccentStyle.OCEAN -> AccentPalette(Color(0xFF00639A), Color(0xFF93CCFF), Color(0xFF006C51), Color(0xFF735B00))
    AccentStyle.FOREST -> AccentPalette(Color(0xFF356A35), Color(0xFFA0D69B), Color(0xFF006A68), Color(0xFF7A5700))
    AccentStyle.SUNSET -> AccentPalette(Color(0xFF9C423D), Color(0xFFFFB4AC), Color(0xFF765A00), Color(0xFF6E55A3))
    AccentStyle.MONO -> AccentPalette(Color(0xFF4D5B65), Color(0xFFBFC8CF), Color(0xFF555F71), Color(0xFF68587A))
}

private fun lightScheme(style: AccentStyle) = palette(style).let { accent ->
    lightColorScheme(
        primary = accent.primary,
        onPrimary = Color.White,
        primaryContainer = blend(accent.primary, Color.White, 0.82f),
        onPrimaryContainer = blend(accent.primary, Color.Black, 0.35f),
        secondary = accent.secondary,
        onSecondary = Color.White,
        secondaryContainer = blend(accent.secondary, Color.White, 0.82f),
        onSecondaryContainer = blend(accent.secondary, Color.Black, 0.38f),
        tertiary = accent.tertiary,
        onTertiary = Color.White,
        tertiaryContainer = blend(accent.tertiary, Color.White, 0.83f),
        onTertiaryContainer = blend(accent.tertiary, Color.Black, 0.38f),
        background = Color(0xFFF8F9FF),
        onBackground = Color(0xFF181B20),
        surface = Color(0xFFFFFBFF),
        onSurface = Color(0xFF181B20),
        surfaceVariant = Color(0xFFE1E3EC),
        onSurfaceVariant = Color(0xFF44474F),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C7CF),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF2F3FA),
        surfaceContainer = Color(0xFFECEEF5),
        surfaceContainerHigh = Color(0xFFE6E8EF),
        surfaceContainerHighest = Color(0xFFE0E2E9),
    )
}

private fun darkScheme(style: AccentStyle) = palette(style).let { accent ->
    darkColorScheme(
        primary = accent.primaryDark,
        onPrimary = blend(accent.primary, Color.Black, 0.50f),
        primaryContainer = blend(accent.primary, Color.Black, 0.44f),
        onPrimaryContainer = blend(accent.primaryDark, Color.White, 0.26f),
        secondary = blend(accent.secondary, Color.White, 0.46f),
        onSecondary = blend(accent.secondary, Color.Black, 0.58f),
        secondaryContainer = blend(accent.secondary, Color.Black, 0.46f),
        onSecondaryContainer = blend(accent.secondary, Color.White, 0.68f),
        tertiary = blend(accent.tertiary, Color.White, 0.48f),
        onTertiary = blend(accent.tertiary, Color.Black, 0.58f),
        tertiaryContainer = blend(accent.tertiary, Color.Black, 0.45f),
        onTertiaryContainer = blend(accent.tertiary, Color.White, 0.70f),
        background = Color(0xFF0D1015),
        onBackground = Color(0xFFE4E7ED),
        surface = Color(0xFF111419),
        onSurface = Color(0xFFE4E7ED),
        surfaceVariant = Color(0xFF44474F),
        onSurfaceVariant = Color(0xFFC4C7CF),
        outline = Color(0xFF8E9199),
        outlineVariant = Color(0xFF44474F),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        surfaceContainerLowest = Color(0xFF090C10),
        surfaceContainerLow = Color(0xFF15181D),
        surfaceContainer = Color(0xFF191C21),
        surfaceContainerHigh = Color(0xFF23262B),
        surfaceContainerHighest = Color(0xFF2E3036),
    )
}

private fun blend(foreground: Color, background: Color, backgroundWeight: Float): Color {
    val weight = backgroundWeight.coerceIn(0f, 1f)
    return Color(
        red = foreground.red * (1f - weight) + background.red * weight,
        green = foreground.green * (1f - weight) + background.green * weight,
        blue = foreground.blue * (1f - weight) + background.blue * weight,
        alpha = 1f,
    )
}

private val TabDeckShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val TabDeckTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 31.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

@Composable
fun TabDeckTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        dark -> darkScheme(settings.accentStyle)
        else -> lightScheme(settings.accentStyle)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = TabDeckTypography,
        shapes = TabDeckShapes,
        content = content,
    )
}
