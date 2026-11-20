package dev.tacuchi.hope.importing

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Fotogramas de un video copiado, para «Elegir portada». Hay que cerrarlo al terminar. */
class FrameGrabber(video: File) : AutoCloseable {
    private val retriever = MediaMetadataRetriever().apply { setDataSource(video.path) }
    private val lock = Mutex()

    val durationMs: Long =
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

    /** El fotograma en [positionMs], reducido para mostrarlo. */
    suspend fun preview(positionMs: Long, maxSide: Int): Bitmap? = grab(positionMs, maxSide)

    /** El fotograma exacto en [positionMs], a tamaño completo, para guardarlo. */
    suspend fun full(positionMs: Long): Bitmap? = grab(positionMs, maxSide = null)

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

    override fun close() {
        retriever.release()
    }
}
