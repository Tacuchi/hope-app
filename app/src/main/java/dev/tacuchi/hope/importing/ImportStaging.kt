package dev.tacuchi.hope.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/** Copia de lo recibido, con lo que dijo quien lo mandó. */
data class StagedFile(val file: File, val declaredMime: String?, val displayName: String?)

/**
 * Carpeta temporal privada de la importación. El permiso para leer lo compartido dura poco, así
 * que todo se copia aquí antes de inspeccionarlo. Cada importación usa su propia subcarpeta.
 */
class ImportStaging(private val resolver: ContentResolver, private val root: File) {

    fun newSession(): File = File(root, UUID.randomUUID().toString()).apply { mkdirs() }

    suspend fun copy(uri: Uri, session: File, declaredMime: String? = null): StagedFile =
        withContext(Dispatchers.IO) {
            val mime = declaredMime?.takeUnless { it.endsWith("/*") } ?: resolver.getType(uri)
            val name = displayName(uri)
            val extension = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                ?: mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            val target = File(session, "original" + (extension?.let { ".${it.lowercase()}" } ?: ""))
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            StagedFile(target, mime, name)
        }

    /** Subcarpeta nueva de la sesión, para que una segunda copia no pise la primera. */
    fun newSubfolder(session: File, name: String): File =
        File(session, "$name-${UUID.randomUUID()}").apply { mkdirs() }

    fun discard(session: File) {
        session.deleteRecursively()
    }

    /** Restos de una importación que no terminó (por ejemplo, si el sistema cerró la app). */
    fun discardAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
