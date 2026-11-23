package dev.tacuchi.hope.cover

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.renderCover
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * La portada con sus efectos como imagen, para la notificación y la pantalla de bloqueo, que solo
 * reciben un archivo. Se dibuja con el mismo cálculo que la app ([renderCover]) en la caché
 * privada ([cacheDir]), con clave por imagen (ruta, tamaño y fecha) y ajustes; la original no se
 * toca. Sin efectos se entrega la original. Corre fuera del hilo principal.
 */
class CoverComposer(private val cacheDir: File) {

    /** La imagen que debe mostrar la sesión: la compuesta, o la original si no hay efectos o no se pudo componer. */
    fun artwork(cover: File?, effects: CoverEffects): File? {
        if (cover == null || effects.isEmpty) return cover
        return try {
            compose(cover, effects)
        } catch (_: IOException) {
            cover
        }
    }

    internal fun cacheFile(cover: File, effects: CoverEffects): File {
        val key = "${cover.absolutePath}|${cover.length()}|${cover.lastModified()}|$effects|v$VERSION"
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(cacheDir, digest.take(12).joinToString("") { "%02x".format(it) } + ".jpg")
    }

    private fun compose(cover: File, effects: CoverEffects): File {
        val target = cacheFile(cover, effects)
        if (target.exists()) return target
        val source = decode(cover) ?: throw IOException("No se pudo leer la portada")
        val sw = source.width
        val sh = source.height
        val pixels = IntArray(sw * sh)
        source.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        source.recycle()
        val rendered = renderCover(pixels, sw, sh, sw, sh, effects)
        val bitmap = Bitmap.createBitmap(rendered, sw, sh, Bitmap.Config.ARGB_8888)
        cacheDir.mkdirs()
        val temp = File(cacheDir, "${target.name}.${System.nanoTime()}.tmp")
        try {
            temp.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it)) throw IOException("No se pudo escribir") }
            if (!temp.renameTo(target)) throw IOException("No se pudo guardar")
        } finally {
            temp.delete()
            bitmap.recycle()
        }
        return target
    }

    /** La original reducida a [MAX_SIDE] como mucho: la notificación no necesita más. */
    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longest = max(decoded.width, decoded.height)
        if (longest <= MAX_SIDE) return decoded
        val scale = MAX_SIDE.toFloat() / longest
        return decoded.scale((decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt())
            .also { if (it !== decoded) decoded.recycle() }
    }

    private companion object {
        const val MAX_SIDE = 720
        const val QUALITY = 90
        const val VERSION = 1
    }
}
