package dev.tacuchi.hope.domain.model

import dev.tacuchi.hope.domain.media.FrameReader

/**
 * Los fotogramas abiertos de un video para «Elegir portada». Es opaco: solo los casos de uso de
 * importación leen sus fotogramas y lo cierran.
 */
class CoverFrames internal constructor(internal val reader: FrameReader) {
    val durationMs: Long get() = reader.durationMs
}
