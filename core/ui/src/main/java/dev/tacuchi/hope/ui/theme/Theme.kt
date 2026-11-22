package dev.tacuchi.hope.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.model.Palette
import dev.tacuchi.hope.domain.model.ThemeMode

private val Lime = Color(0xFFB6F23C)
private val Ink = Color(0xFF111111)

private val LimeDark: ColorScheme = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = Lime,
    onPrimaryContainer = Ink,
    secondary = Color(0xFFC4D9A0),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3A4A1A),
    onSecondaryContainer = Color(0xFFE6E6E2),
    tertiary = Lime,
    onTertiary = Ink,
    background = Color(0xFF121211),
    onBackground = Color(0xFFE6E6E2),
    surface = Color(0xFF121211),
    onSurface = Color(0xFFE6E6E2),
    surfaceVariant = Color(0xFF2B2B28),
    onSurfaceVariant = Color(0xFFA3A39B),
    surfaceContainerLowest = Color(0xFF0D0D0C),
    surfaceContainerLow = Color(0xFF181816),
    surfaceContainer = Color(0xFF1C1C1A),
    surfaceContainerHigh = Color(0xFF2B2B28),
    surfaceContainerHighest = Color(0xFF353532),
    outline = Color(0xFF8A8A83),
    outlineVariant = Color(0xFF3A3A36),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LimeLight: ColorScheme = lightColorScheme(
    primary = Color(0xFF4C6700),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Lime,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF4C6700),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE3A6),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF4C6700),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFE6E6E2),
    onBackground = Ink,
    surface = Color(0xFFE6E6E2),
    onSurface = Ink,
    surfaceVariant = Color(0xFFD9D9D4),
    onSurfaceVariant = Color(0xFF5C5C56),
    surfaceContainerLowest = Color(0xFFF1F1EE),
    surfaceContainerLow = Color(0xFFDFDFDB),
    surfaceContainer = Color(0xFFD9D9D4),
    surfaceContainerHigh = Color(0xFFF1F1EE),
    surfaceContainerHighest = Color(0xFFCDCDC8),
    outline = Color(0xFF75756F),
    outlineVariant = Color(0xFFC4C4BE),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private fun accentScheme(dark: Boolean, ink: Color, accent: Color, pale: Color, deep: Color): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val primary = if (dark) accent else ink
    val onPrimary = if (dark) deep else Color.White
    val container = if (dark) ink else pale
    val onContainer = if (dark) Color.White else deep
    return base.copy(
        primary = primary, onPrimary = onPrimary, primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onPrimary, tertiaryContainer = container, onTertiaryContainer = onContainer,
        surfaceTint = primary, inversePrimary = if (dark) ink else accent,
    )
}

fun fixedScheme(palette: Palette, dark: Boolean): ColorScheme = when (palette) {
    Palette.Lime -> if (dark) LimeDark else LimeLight
    Palette.Hope -> accentScheme(dark, Color(0xFF18478C), Color(0xFF61F2E4), Color(0xFFEEFFFE), Color(0xFF091E3E))
    Palette.Coral -> accentScheme(dark, Color(0xFF903A32), Color(0xFFFFB4A9), Color(0xFFFFDAD4), Color(0xFF3B0806))
    Palette.Violet -> accentScheme(dark, Color(0xFF634096), Color(0xFFD8BAFF), Color(0xFFEEDCFF), Color(0xFF290846))
    Palette.Dynamic -> error("Dynamic colors require the Android wallpaper")
}

@Composable
fun HopeTheme(appearance: Appearance = Appearance(), content: @Composable () -> Unit) {
    val dark = when (appearance.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = if (appearance.palette == Palette.Dynamic && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else {
        fixedScheme(if (appearance.palette == Palette.Dynamic) Palette.Hope else appearance.palette, dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
