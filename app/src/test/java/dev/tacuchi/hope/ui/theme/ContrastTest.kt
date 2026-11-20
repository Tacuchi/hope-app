package dev.tacuchi.hope.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import dev.tacuchi.hope.data.Palette
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contraste WCAG 2.2 de los pares de roles que usan las pantallas.
 * Texto: 4,5:1. Íconos y bordes de controles: 3:1. Los controles deshabilitados están exentos.
 */
class ContrastTest {

    @Test
    fun fixedSchemesMeetAa() {
        for (palette in Palette.entries.filter { it != Palette.Dynamic }) {
            for (dark in listOf(false, true)) assertPairs("$palette dark=$dark", fixedScheme(palette, dark))
        }
    }

    @Test
    fun formulaMatchesKnownValues() {
        // Valores de referencia de la especificación WCAG: negro sobre blanco es 21:1.
        assertNear(21.0, contrast(Color.Black, Color.White))
        assertNear(1.0, contrast(Color.White, Color.White))
    }

    private fun assertPairs(name: String, s: ColorScheme) {
        val text = listOf(
            "onBackground/background" to (s.onBackground to s.background),
            "onSurface/surface" to (s.onSurface to s.surface),
            "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
            "onSurface/surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
            "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
            "onSurfaceVariant/surfaceContainer" to (s.onSurfaceVariant to s.surfaceContainer),
            "primary/surface" to (s.primary to s.surface),
            "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
            "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        )
        val graphics = listOf(
            "outline/surface" to (s.outline to s.surface),
            "outline/surfaceContainer" to (s.outline to s.surfaceContainer),
            "primary/surfaceContainer" to (s.primary to s.surfaceContainer),
            "primary/surfaceContainerHigh" to (s.primary to s.surfaceContainerHigh),
            "primary/secondaryContainer" to (s.primary to s.secondaryContainer),
            "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
        )
        val failures = text.mapNotNull { (pair, colors) -> check(pair, colors, 4.5) } +
            graphics.mapNotNull { (pair, colors) -> check(pair, colors, 3.0) }
        assertTrue("Esquema $name bajo el umbral: $failures", failures.isEmpty())
    }

    private fun check(pair: String, colors: Pair<Color, Color>, minimum: Double): String? {
        val ratio = contrast(colors.first, colors.second)
        return if (ratio >= minimum) null else "$pair = %.2f (mínimo %.1f)".format(ratio, minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val argb = color.toArgb()
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun assertNear(expected: Double, actual: Double) =
        assertTrue("esperado $expected, obtenido $actual", kotlin.math.abs(expected - actual) < 0.01)
}
