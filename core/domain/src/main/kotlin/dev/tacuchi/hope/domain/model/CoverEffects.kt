package dev.tacuchi.hope.domain.model

/** Filtro de color de la portada. */
enum class CoverFilter { None, Warm, Cool, Mono, Duotone }

/** Marco de la portada. */
enum class CoverFrame { None, Thin, Polaroid, LightTape }

/** Textura de la portada. */
enum class CoverTexture { None, Grain, Vignette, Blur }

/**
 * Cómo se ve la portada. Se dibuja sobre la imagen original, que nunca cambia. Las intensidades
 * van de 0 a 100; el zoom, en porcentaje, de [MIN_ZOOM] a [MAX_ZOOM]; el desplazamiento, de −100
 * (izquierda o arriba) a 100 (derecha o abajo), es la parte del margen que deja el zoom.
 */
data class CoverEffects(
    val filter: CoverFilter = CoverFilter.None,
    val filterAmount: Int = DEFAULT_AMOUNT,
    val frame: CoverFrame = CoverFrame.None,
    val texture: CoverTexture = CoverTexture.None,
    val textureAmount: Int = DEFAULT_TEXTURE_AMOUNT,
    val zoom: Int = MIN_ZOOM,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
) {
    val hasFilter: Boolean get() = filter != CoverFilter.None && filterAmount > 0
    val hasTexture: Boolean get() = texture != CoverTexture.None && textureAmount > 0
    val hasFraming: Boolean get() = zoom > MIN_ZOOM
    val isEmpty: Boolean get() = !hasFilter && frame == CoverFrame.None && !hasTexture && !hasFraming

    fun zoomedBy(steps: Int): CoverEffects {
        val next = (zoom + steps * ZOOM_STEP).coerceIn(MIN_ZOOM, MAX_ZOOM)
        return if (next == MIN_ZOOM) copy(zoom = next, offsetX = 0, offsetY = 0) else copy(zoom = next)
    }

    fun movedBy(dx: Int, dy: Int): CoverEffects = copy(
        offsetX = (offsetX + dx * OFFSET_STEP).coerceIn(-100, 100),
        offsetY = (offsetY + dy * OFFSET_STEP).coerceIn(-100, 100),
    )

    fun centered(): CoverEffects = copy(zoom = MIN_ZOOM, offsetX = 0, offsetY = 0)

    companion object {
        const val DEFAULT_AMOUNT = 70
        const val DEFAULT_TEXTURE_AMOUNT = 30
        const val MIN_ZOOM = 100
        const val MAX_ZOOM = 200
        const val ZOOM_STEP = 10
        const val OFFSET_STEP = 25

        /** Semilla fija del grano: la misma portada da el mismo grano en la app y en la notificación. */
        const val GRAIN_SEED = 0x486F7065L
    }
}

/**
 * Matriz de color de 4 × 5 (filas R, G, B, A; la última columna suma de 0 a 255), como la de
 * Android y la de Compose. Con intensidad 0 es la identidad; con 100, el filtro entero.
 */
fun CoverEffects.colorMatrix(): FloatArray {
    val target = when (filter) {
        CoverFilter.None -> return IDENTITY.copyOf()
        CoverFilter.Warm -> floatArrayOf(
            1.08f, 0.04f, 0f, 0f, 14f,
            0.02f, 1.0f, 0f, 0f, 4f,
            0f, 0f, 0.82f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f,
        )
        CoverFilter.Cool -> floatArrayOf(
            0.86f, 0f, 0f, 0f, -10f,
            0f, 0.98f, 0.04f, 0f, 2f,
            0f, 0.04f, 1.1f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f,
        )
        CoverFilter.Mono -> floatArrayOf(
            LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
            LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
            LUMA_R, LUMA_G, LUMA_B, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        CoverFilter.Duotone -> duotone(DUO_DARK, DUO_LIGHT)
    }
    val t = filterAmount.coerceIn(0, 100) / 100f
    return FloatArray(20) { IDENTITY[it] + (target[it] - IDENTITY[it]) * t }
}

/** De la sombra [dark] a la luz [light] (RGB de 0 a 255) según el brillo de cada punto. */
private fun duotone(dark: IntArray, light: IntArray): FloatArray {
    val rows = (0..2).flatMap { c ->
        val span = (light[c] - dark[c]) / 255f
        listOf(LUMA_R * span, LUMA_G * span, LUMA_B * span, 0f, dark[c].toFloat())
    }
    return (rows + listOf(0f, 0f, 0f, 1f, 0f)).toFloatArray()
}

/** Aplica la matriz a un color RGB de 0 a 255, como lo hace Android. */
fun applyColorMatrix(matrix: FloatArray, r: Int, g: Int, b: Int): IntArray = IntArray(3) { row ->
    val o = row * 5
    (matrix[o] * r + matrix[o + 1] * g + matrix[o + 2] * b + matrix[o + 4]).toInt().coerceIn(0, 255)
}

/** Proporciones del marco respecto del lado de la portada. */
data class FrameGeometry(
    /** Margen de papel blanco (polaroid): izquierda, arriba, derecha y abajo. */
    val paperLeft: Float = 0f,
    val paperTop: Float = 0f,
    val paperRight: Float = 0f,
    val paperBottom: Float = 0f,
    /** Borde fino: cuánto se mete hacia adentro, su grosor y su radio. */
    val lineInset: Float = 0f,
    val lineWidth: Float = 0f,
    val lineRadius: Float = 0f,
)

fun CoverFrame.geometry(): FrameGeometry = when (this) {
    CoverFrame.None, CoverFrame.LightTape -> FrameGeometry()
    CoverFrame.Thin -> FrameGeometry(lineInset = 0.03f, lineWidth = 0.008f, lineRadius = 0.05f)
    CoverFrame.Polaroid -> FrameGeometry(paperLeft = 0.05f, paperTop = 0.05f, paperRight = 0.05f, paperBottom = 0.16f)
}

private val IDENTITY = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)
private const val LUMA_R = 0.299f
private const val LUMA_G = 0.587f
private const val LUMA_B = 0.114f
private val DUO_DARK = intArrayOf(9, 30, 62)
private val DUO_LIGHT = intArrayOf(97, 242, 228)
