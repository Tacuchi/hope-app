package dev.tacuchi.hope.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Constraints
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.renderCover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Los píxeles de la imagen original, leídos una vez. */
private class SourcePixels(val pixels: IntArray, val width: Int, val height: Int)

/**
 * La portada de una hope con sus efectos (filtro, marco, textura y encuadre), dibujada sobre la
 * imagen original sin cambiarla. Se dibuja con el mismo cálculo que la imagen de la notificación,
 * a la forma del lugar que ocupa. Mientras calcula queda la versión anterior. Con [showOriginal]
 * muestra la original con el mismo recorte, para comparar.
 */
@Composable
fun CoverImage(
    file: File,
    effects: CoverEffects,
    modifier: Modifier = Modifier,
    maxSidePx: Int = 640,
    showOriginal: Boolean = false,
    contentDescription: String? = null,
) {
    BoxWithConstraints(modifier) {
        val ratio = if (constraints.maxHeight != Constraints.Infinity && constraints.maxHeight > 0 && constraints.maxWidth > 0) {
            constraints.maxWidth.toFloat() / constraints.maxHeight
        } else {
            1f
        }
        val source by produceState<SourcePixels?>(null, file, maxSidePx) {
            value = withContext(Dispatchers.IO) { readPixels(file, maxSidePx) }
        }
        val shown = if (showOriginal) CoverEffects() else effects
        val rendered by produceState<ImageBitmap?>(null, source, shown, ratio) {
            val original = source ?: return@produceState
            value = withContext(Dispatchers.Default) {
                val width = if (ratio >= 1f) maxSidePx else (maxSidePx * ratio).roundToInt().coerceAtLeast(1)
                val height = (width / ratio).roundToInt().coerceAtLeast(1)
                val pixels = renderCover(original.pixels, original.width, original.height, width, height, shown)
                Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
        rendered?.let {
            Image(it, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

private fun readPixels(file: File, maxSide: Int): SourcePixels? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return SourcePixels(pixels, bitmap.width, bitmap.height).also { bitmap.recycle() }
}
