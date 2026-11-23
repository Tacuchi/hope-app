package dev.tacuchi.hope.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.tacuchi.hope.ui.R

/**
 * Manrope (SIL Open Font License 1.1, licencia en `assets/licenses/manrope-OFL.txt`) para títulos
 * y tiempos; Roboto, la del sistema, para el resto. El archivo es de peso variable.
 */
private fun manrope(weight: FontWeight) = Font(
    R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private val base = Typography()

/** Los títulos de Material 3 en Manrope; el cuerpo y las etiquetas siguen en Roboto. */
val HopeTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
)

/** Tiempos de audio: Manrope con cifras de ancho fijo, para que no bailen mientras avanzan. */
object TimeStyle {
    val small = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFeatureSettings = "tnum")
    val medium = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFeatureSettings = "tnum")
    val large = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, fontFeatureSettings = "tnum")
}
