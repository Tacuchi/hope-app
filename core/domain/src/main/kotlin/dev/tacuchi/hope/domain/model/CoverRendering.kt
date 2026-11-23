package dev.tacuchi.hope.domain.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Dibuja la portada con sus efectos en una imagen nueva de [width] × [height] píxeles ARGB. La
 * imagen original ([source], de [sourceWidth] × [sourceHeight]) no se toca. La app y la
 * notificación usan este mismo cálculo, así la portada se ve igual en todos lados.
 *
 * Orden: el marco reserva su papel, la imagen se recorta al centro con su zoom y desplazamiento,
 * después la textura (desenfoque, grano o viñeta), el filtro de color y por último la línea del
 * marco o su cinta de luz.
 */
fun renderCover(
    source: IntArray,
    sourceWidth: Int,
    sourceHeight: Int,
    width: Int,
    height: Int,
    effects: CoverEffects,
): IntArray {
    require(source.size >= sourceWidth * sourceHeight && width > 0 && height > 0)
    val out = IntArray(width * height) { PAPER }
    val geometry = effects.frame.geometry()
    val ax = (geometry.paperLeft * width).roundToInt()
    val ay = (geometry.paperTop * width).roundToInt()
    val aw = (width - ax - (geometry.paperRight * width).roundToInt()).coerceAtLeast(1)
    val ah = (height - ay - (geometry.paperBottom * width).roundToInt()).coerceAtLeast(1)

    var area = crop(source, sourceWidth, sourceHeight, aw, ah, effects)
    if (effects.texture == CoverTexture.Blur && effects.hasTexture) {
        val radius = (effects.textureAmount / 100f * BLUR_AT_FULL * aw).roundToInt()
        area = boxBlur(boxBlur(area, aw, ah, radius), aw, ah, radius)
    }
    val matrix = effects.colorMatrix()
    val amount = effects.textureAmount.coerceIn(0, 100) / 100f
    val grain = effects.texture == CoverTexture.Grain && effects.hasTexture
    val vignette = effects.texture == CoverTexture.Vignette && effects.hasTexture
    for (y in 0 until ah) {
        for (x in 0 until aw) {
            val pixel = area[y * aw + x]
            var r = pixel shr 16 and 0xFF
            var g = pixel shr 8 and 0xFF
            var b = pixel and 0xFF
            if (effects.hasFilter) {
                val mapped = applyColorMatrix(matrix, r, g, b)
                r = mapped[0]; g = mapped[1]; b = mapped[2]
            }
            if (vignette) {
                val dx = (x + 0.5f) / aw - 0.5f
                val dy = (y + 0.5f) / ah - 0.5f
                val distance = sqrt(dx * dx + dy * dy) / HALF_DIAGONAL
                val shade = 1f - amount * VIGNETTE_DEPTH * smoothstep(0.35f, 1f, distance)
                r = (r * shade).roundToInt(); g = (g * shade).roundToInt(); b = (b * shade).roundToInt()
            }
            if (grain) {
                val noise = ((noiseAt(x, y) - 0.5f) * 2f * amount * GRAIN_AT_FULL).roundToInt()
                r = (r + noise).coerceIn(0, 255); g = (g + noise).coerceIn(0, 255); b = (b + noise).coerceIn(0, 255)
            }
            out[(ay + y) * width + ax + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    when (effects.frame) {
        CoverFrame.Thin -> drawLine(out, width, height, geometry)
        CoverFrame.LightTape -> drawLightTape(out, width, height)
        CoverFrame.None, CoverFrame.Polaroid -> Unit
    }
    return out
}

/** La parte visible de la imagen: recortada al centro a la forma del área, con zoom y desplazamiento. */
private fun crop(source: IntArray, sw: Int, sh: Int, aw: Int, ah: Int, effects: CoverEffects): IntArray {
    val areaRatio = aw.toFloat() / ah
    var cropW = sw.toFloat()
    var cropH = sw / areaRatio
    if (cropH > sh) {
        cropH = sh.toFloat()
        cropW = sh * areaRatio
    }
    val zoom = effects.zoom.coerceIn(CoverEffects.MIN_ZOOM, CoverEffects.MAX_ZOOM) / 100f
    val visibleW = cropW / zoom
    val visibleH = cropH / zoom
    val centerX = sw / 2f + effects.offsetX.coerceIn(-100, 100) / 100f * (cropW - visibleW) / 2f
    val centerY = sh / 2f + effects.offsetY.coerceIn(-100, 100) / 100f * (cropH - visibleH) / 2f
    val left = centerX - visibleW / 2f
    val top = centerY - visibleH / 2f
    return IntArray(aw * ah) { i ->
        val x = i % aw
        val y = i / aw
        val sx = (left + (x + 0.5f) * visibleW / aw).toInt().coerceIn(0, sw - 1)
        val sy = (top + (y + 0.5f) * visibleH / ah).toInt().coerceIn(0, sh - 1)
        source[sy * sw + sx]
    }
}

/** Desenfoque de caja separable; dos pasadas se parecen a un desenfoque gaussiano. */
private fun boxBlur(pixels: IntArray, w: Int, h: Int, radius: Int): IntArray {
    if (radius < 1) return pixels
    fun pass(input: IntArray, horizontal: Boolean): IntArray {
        val output = IntArray(input.size)
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        for (line in 0 until lines) {
            fun index(i: Int) = if (horizontal) line * w + i.coerceIn(0, length - 1) else i.coerceIn(0, length - 1) * w + line
            var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = input[index(i)]
                r += p shr 16 and 0xFF; g += p shr 8 and 0xFF; b += p and 0xFF
            }
            val size = radius * 2 + 1
            for (i in 0 until length) {
                output[index(i)] = (0xFF shl 24) or ((r / size) shl 16) or ((g / size) shl 8) or (b / size)
                val add = input[index(i + radius + 1)]
                val remove = input[index(i - radius)]
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
        return output
    }
    return pass(pass(pixels, true), false)
}

/** Ruido fijo por posición: la misma portada da siempre el mismo grano. */
private fun noiseAt(x: Int, y: Int): Float {
    var h = x.toLong() * 374_761_393L + y.toLong() * 668_265_263L + CoverEffects.GRAIN_SEED
    h = (h xor (h ushr 13)) * 1_274_126_177L
    h = h xor (h ushr 16)
    return (h and 0xFFFF).toFloat() / 0xFFFF
}

/** La línea blanca del borde fino: un rectángulo redondeado metido hacia adentro. */
private fun drawLine(out: IntArray, w: Int, h: Int, geometry: FrameGeometry) {
    val side = min(w, h).toFloat()
    val inset = geometry.lineInset * side
    val half = max(1f, geometry.lineWidth * side) / 2f
    val radius = geometry.lineRadius * side
    val halfW = w / 2f - inset
    val halfH = h / 2f - inset
    for (y in 0 until h) {
        for (x in 0 until w) {
            val px = abs(x + 0.5f - w / 2f) - (halfW - radius)
            val py = abs(y + 0.5f - h / 2f) - (halfH - radius)
            val outside = sqrt(max(px, 0f) * max(px, 0f) + max(py, 0f) * max(py, 0f)) + min(max(px, py), 0f) - radius
            val coverage = (half + 0.5f - abs(outside)).coerceIn(0f, 1f)
            if (coverage > 0f) out[y * w + x] = blend(out[y * w + x], LINE, coverage * LINE_ALPHA)
        }
    }
}

/** Una franja de luz cálida en diagonal, como luz que se cuela en la película. */
private fun drawLightTape(out: IntArray, w: Int, h: Int) {
    for (y in 0 until h) {
        for (x in 0 until w) {
            val u = ((x + 0.5f) / w + (y + 0.5f) / h) / 2f
            val d = (u - TAPE_CENTER) / TAPE_WIDTH
            val alpha = TAPE_ALPHA * exp(-d * d)
            if (alpha > 0.004f) out[y * w + x] = screen(out[y * w + x], TAPE, alpha)
        }
    }
}

private fun blend(base: Int, over: Int, alpha: Float): Int {
    fun channel(shift: Int) = ((base shr shift and 0xFF) * (1 - alpha) + (over shr shift and 0xFF) * alpha).roundToInt()
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Mezcla de pantalla: aclara sin quemar. */
private fun screen(base: Int, light: Int, alpha: Float): Int {
    fun channel(shift: Int): Int {
        val a = (base shr shift and 0xFF) / 255f
        val l = (light shr shift and 0xFF) / 255f
        val screened = 1 - (1 - a) * (1 - l)
        return ((a + (screened - a) * alpha) * 255).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

/** Papel del polaroid, línea del borde fino y luz de la cinta. */
private const val PAPER = 0xFFF7F4EE.toInt()
private const val LINE = 0xFFFFFFFF.toInt()
private const val TAPE = 0xFFFFE6C8.toInt()
private const val LINE_ALPHA = 0.92f
private const val TAPE_CENTER = 0.32f
private const val TAPE_WIDTH = 0.09f
private const val TAPE_ALPHA = 0.75f
private const val BLUR_AT_FULL = 0.03f
private const val GRAIN_AT_FULL = 48f
private const val VIGNETTE_DEPTH = 0.85f
private val HALF_DIAGONAL = sqrt(0.5f)
