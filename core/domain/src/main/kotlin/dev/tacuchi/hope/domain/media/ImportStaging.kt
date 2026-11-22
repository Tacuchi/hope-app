package dev.tacuchi.hope.domain.media

import dev.tacuchi.hope.domain.model.StagedFile
import java.io.File

/**
 * Carpeta temporal privada de la importación. El permiso para leer lo compartido dura poco, así
 * que todo se copia aquí antes de inspeccionarlo. Cada importación usa su propia sesión.
 */
interface ImportStaging {
    fun newSession(): File

    /** Copia lo que señala [uri] (la URI como texto) dentro de [session]. */
    suspend fun copy(uri: String, session: File, declaredMime: String? = null): StagedFile

    /** Subcarpeta nueva de la sesión, para que una segunda copia no pise la primera. */
    fun newSubfolder(session: File, name: String): File

    fun discard(session: File)

    /** Restos de una importación que no terminó (por ejemplo, si el sistema cerró la app). */
    fun discardAll()
}
