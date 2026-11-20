package dev.tacuchi.hope.importing

import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Abre el archivo copiado y mira qué es de verdad, sin fiarse de la extensión ni del tipo. */
class MediaInspector {

    suspend fun inspect(file: File): MediaInspection = withContext(Dispatchers.IO) {
        if (isImage(file)) {
            return@withContext MediaInspection(isImage = true, hasVideo = false, hasAudio = false, durationMs = null)
        }
        var hasVideo = false
        var hasAudio = false
        var durationUs = 0L
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                when {
                    mime.startsWith("video/") -> hasVideo = true
                    mime.startsWith("audio/") -> hasAudio = true
                }
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                }
            }
        } catch (_: Exception) {
            // No es un contenedor que Android sepa leer: queda sin pistas, es decir, no compatible.
        } finally {
            extractor.release()
        }
        val durationMs = if (durationUs > 0) durationUs / 1000 else metadataDuration(file)
        MediaInspection(isImage = false, hasVideo = hasVideo, hasAudio = hasAudio, durationMs = durationMs)
    }

    private fun isImage(file: File): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun metadataDuration(file: File): Long? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }
    }.getOrNull()
}
