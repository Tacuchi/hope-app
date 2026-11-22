package dev.tacuchi.hope.domain.media

import java.io.File

/** Saca el audio de un video, en el teléfono. */
interface AudioExtractor {
    /**
     * Extrae el audio de [input] en [output] e informa el avance de 0 a 100 (nulo si aún no se
     * sabe). Devuelve la duración del audio en milisegundos. Cancelar la corrutina detiene la
     * extracción y borra [output].
     */
    suspend fun extract(input: File, output: File, onProgress: (Int?) -> Unit = {}): Long
}
