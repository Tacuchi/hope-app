package dev.tacuchi.hope.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Lime = Color(0xFFB6F23C)
private val Ink = Color(0xFF111111)

/**
 * Paleta de la marca (design/stitch/notas.md del hub). Sin color dinámico: el fondo de pantalla
 * del teléfono no altera la app.
 *
 * El lima es el relleno de las acciones principales (`primaryContainer`) en los dos temas.
 * En claro el lima no alcanza contraste como texto, así que `primary` es el verde oscuro.
 */
val HopeDarkColors: ColorScheme = darkColorScheme(
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

val HopeLightColors: ColorScheme = lightColorScheme(
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

@Composable
fun HopeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) HopeDarkColors else HopeLightColors,
        content = content,
    )
}
