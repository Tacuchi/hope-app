package dev.tacuchi.hope.domain.media

import java.io.File

/** Fotogramas de un video copiado, para «Elegir portada». */
interface FrameGrabber {
    /** Abre [video]; las vistas previas se escriben en [previewDir]. Hay que cerrarlo al terminar. */
    fun open(video: File, previewDir: File): FrameReader
}

interface FrameReader : AutoCloseable {
    val durationMs: Long

    /** El fotograma en [positionMs], reducido a [maxSide] y escrito como imagen temporal; nulo si no hay. */
    suspend fun preview(positionMs: Long, maxSide: Int): File?

    /** Guarda en [target] el fotograma exacto en [positionMs], a tamaño completo. Falso si no hay. */
    suspend fun save(positionMs: Long, target: File): Boolean
}
