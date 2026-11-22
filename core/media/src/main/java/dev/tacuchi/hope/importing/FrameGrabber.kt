package dev.tacuchi.hope.importing

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import dev.tacuchi.hope.domain.media.FrameGrabber
import dev.tacuchi.hope.domain.media.FrameReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Fotogramas con `MediaMetadataRetriever`, escritos como JPEG para que la pantalla los dibuje. */
class RetrieverFrameGrabber : FrameGrabber {
    override fun open(video: File, previewDir: File): FrameReader = RetrieverFrameReader(video, previewDir)
}

private class RetrieverFrameReader(video: File, private val previewDir: File) : FrameReader {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(video.path) }
    private val lock = Mutex()
    private val written = AtomicInteger()

    override val durationMs: Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    /** Cada vista previa va a un archivo nuevo: la pantalla la reconoce por su ruta. */
    override suspend fun preview(positionMs: Long, maxSide: Int): File? {
        val bitmap = grab(positionMs, maxSide) ?: return null
        val target = File(previewDir, "preview-${written.incrementAndGet()}.jpg")
        return target.takeIf { write(bitmap, target, PREVIEW_QUALITY) }
    }

    override suspend fun save(positionMs: Long, target: File): Boolean {
        val bitmap = grab(positionMs, maxSide = null) ?: return false
        return write(bitmap, target, FULL_QUALITY)
    }

    private suspend fun grab(positionMs: Long, maxSide: Int?): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            val timeUs = positionMs * 1000
            // OPTION_CLOSEST y no el cuadro clave más cercano: en un video corto todos caerían en el mismo.
            runCatching {
                if (maxSide == null) {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } else {
                    retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, maxSide, maxSide)
                }
            }.getOrNull()
        }
    }

    private suspend fun write(bitmap: Bitmap, target: File, quality: Int): Boolean = withContext(Dispatchers.IO) {
        target.parentFile?.mkdirs()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    override fun close() {
        retriever.release()
    }

    private companion object {
        const val FULL_QUALITY = 90
        const val PREVIEW_QUALITY = 85
    }
}
