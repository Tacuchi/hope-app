package dev.tacuchi.hope.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Píxeles de muestra de la portada con cada efecto frente a la original. La original nunca cambia
 * y sin efectos la portada es la original.
 */
class CoverEffectsTest {
    private val w = 80
    private val h = 60

    /** Un degradé: el rojo crece hacia la derecha, el verde hacia abajo, el azul fijo en 128. */
    private val original = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        rgb(x * 255 / (w - 1), y * 255 / (h - 1), 128)
    }
    private val untouched = original.copyOf()

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun Int.r() = this shr 16 and 0xFF
    private fun Int.g() = this shr 8 and 0xFF
    private fun Int.b() = this and 0xFF
    private fun IntArray.at(x: Int, y: Int) = this[y * w + x]
    private fun render(effects: CoverEffects) = renderCover(original, w, h, w, h, effects)

    @Test
    fun withoutEffectsTheCoverIsTheOriginal() {
        assertTrue(CoverEffects().isEmpty)
        assertArrayEquals(original, render(CoverEffects()))
    }

    @Test
    fun eachFilterChangesTheColorsAndItsIntensityScalesIt() {
        val center = original.at(40, 30)
        val warm = render(CoverEffects(filter = CoverFilter.Warm, filterAmount = 100)).at(40, 30)
        assertTrue(warm.r() > center.r() && warm.b() < center.b())

        val cool = render(CoverEffects(filter = CoverFilter.Cool, filterAmount = 100)).at(40, 30)
        assertTrue(cool.r() < center.r() && cool.b() > center.b())

        val mono = render(CoverEffects(filter = CoverFilter.Mono, filterAmount = 100)).at(40, 30)
        assertEquals(mono.r(), mono.g())
        assertEquals(mono.g(), mono.b())

        // Duotono: lo oscuro va a la sombra azul profunda, lo claro hacia el turquesa.
        val duo = render(CoverEffects(filter = CoverFilter.Duotone, filterAmount = 100))
        assertTrue(duo.at(0, 0).b() > duo.at(0, 0).r() && duo.at(79, 59).g() > duo.at(0, 0).g())

        val half = render(CoverEffects(filter = CoverFilter.Warm, filterAmount = 50)).at(40, 30)
        assertTrue(half.b() in (warm.b() + 1) until center.b())
        assertArrayEquals(original, render(CoverEffects(filter = CoverFilter.Warm, filterAmount = 0)))
    }

    @Test
    fun eachFrameDrawsAroundTheImage() {
        // Borde fino: una línea blanca metida un 3 % del lado; el centro no cambia.
        val thin = render(CoverEffects(frame = CoverFrame.Thin))
        val line = (0..4).maxOf { thin.at(40, it).g() }
        assertTrue(line > (0..4).maxOf { original.at(40, it).g() } + 100)
        assertEquals(original.at(40, 30), thin.at(40, 30))

        // Polaroid: papel claro alrededor y abajo más ancho; la imagen queda adentro.
        val polaroid = render(CoverEffects(frame = CoverFrame.Polaroid))
        assertEquals(0xFFF7F4EE.toInt(), polaroid.at(40, 58))
        assertEquals(0xFFF7F4EE.toInt(), polaroid.at(1, 30))
        assertTrue(polaroid.at(40, 20) != 0xFFF7F4EE.toInt())

        // Cinta de luz: aclara su franja diagonal y deja oscura la esquina opuesta.
        val tape = render(CoverEffects(frame = CoverFrame.LightTape))
        val band = original.at(25, 20)
        assertTrue(tape.at(25, 20).g() > band.g() + 30)
        assertEquals(original.at(79, 59), tape.at(79, 59))
    }

    @Test
    fun eachTextureChangesThePixelsByItsIntensity() {
        val grain = render(CoverEffects(texture = CoverTexture.Grain, textureAmount = 100))
        val changed = original.indices.count { abs(grain[it].g() - original[it].g()) > 4 }
        assertTrue(changed > original.size / 2)
        // El grano es el mismo cada vez: la app y la notificación lo ven igual.
        assertArrayEquals(grain, render(CoverEffects(texture = CoverTexture.Grain, textureAmount = 100)))

        val vignette = render(CoverEffects(texture = CoverTexture.Vignette, textureAmount = 100))
        assertTrue(vignette.at(79, 0).r() < original.at(79, 0).r() - 60)
        assertTrue(abs(vignette.at(40, 30).r() - original.at(40, 30).r()) <= 1)

        // El desenfoque suaviza un borde duro.
        val edge = IntArray(w * h) { if (it % w < w / 2) rgb(0, 0, 0) else rgb(255, 255, 255) }
        val blurred = renderCover(edge, w, h, w, h, CoverEffects(texture = CoverTexture.Blur, textureAmount = 100))
        assertTrue(blurred[30 * w + 39].r() in 20..235)
        assertEquals(0, blurred[30 * w + 5].r())
    }

    @Test
    fun framingZoomsAndMovesInsideTheImage() {
        val zoomed = render(CoverEffects(zoom = 200))
        // Al doble, el borde izquierdo muestra lo que estaba a un cuarto del ancho.
        assertTrue(abs(zoomed.at(0, 30).r() - original.at(20, 30).r()) <= 4)
        assertTrue(abs(zoomed.at(40, 30).r() - original.at(40, 30).r()) <= 4)

        // Movida del todo a la derecha, el borde derecho es el de la imagen.
        val right = render(CoverEffects(zoom = 200, offsetX = 100))
        assertTrue(abs(right.at(79, 30).r() - original.at(79, 30).r()) <= 4)

        val centered = CoverEffects(zoom = 150, offsetX = 50, offsetY = -25).centered()
        assertTrue(centered.isEmpty)
        assertEquals(CoverEffects(zoom = 110), CoverEffects().zoomedBy(1))
        assertEquals(CoverEffects.MAX_ZOOM, CoverEffects().zoomedBy(99).zoom)
    }

    @Test
    fun drawingNeverTouchesTheOriginal() {
        val all = CoverEffects(CoverFilter.Duotone, 80, CoverFrame.Polaroid, CoverTexture.Blur, 60, 160, -50, 50)
        render(all)
        render(all.copy(frame = CoverFrame.Thin, texture = CoverTexture.Grain))
        assertArrayEquals(untouched, original)
    }
}
