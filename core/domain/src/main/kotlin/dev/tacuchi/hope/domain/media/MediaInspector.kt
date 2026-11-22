package dev.tacuchi.hope.domain.media

import dev.tacuchi.hope.domain.model.MediaInspection
import java.io.File

/** Mira qué es de verdad un archivo, sin fiarse de la extensión ni del tipo declarado. */
interface MediaInspector {
    suspend fun inspect(file: File): MediaInspection
}
