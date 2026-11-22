package dev.tacuchi.hope.importing

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import dev.tacuchi.hope.domain.media.ImportStaging
import dev.tacuchi.hope.domain.model.StagedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

/**
 * Carpeta temporal privada de la importación. El permiso para leer lo compartido dura poco, así
 * que todo se copia aquí antes de inspeccionarlo. Cada importación usa su propia subcarpeta.
 */
class ContentImportStaging(private val resolver: ContentResolver, private val root: File) : ImportStaging {

    override fun newSession(): File = File(root, UUID.randomUUID().toString()).apply { mkdirs() }

    override suspend fun copy(uri: String, session: File, declaredMime: String?): StagedFile = copy(uri.toUri(), session, declaredMime)

    private suspend fun copy(uri: Uri, session: File, declaredMime: String?): StagedFile =
        withContext(Dispatchers.IO) {
            val mime = declaredMime?.takeUnless { it.endsWith("/*") } ?: resolver.getType(uri)
            val name = displayName(uri)
            val extension = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
                ?: mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            val target = File(session, "original" + (extension?.let { ".${it.lowercase()}" } ?: ""))
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            StagedFile(target, mime, name, mimeOf(target, mime))
        }

    /** Subcarpeta nueva de la sesión, para que una segunda copia no pise la primera. */
    override fun newSubfolder(session: File, name: String): File =
        File(session, "$name-${UUID.randomUUID()}").apply { mkdirs() }

    override fun discard(session: File) {
        session.deleteRecursively()
    }

    /** Restos de una importación que no terminó (por ejemplo, si el sistema cerró la app). */
    override fun discardAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** El tipo con que se guarda: el de su extensión, si no el declarado. */
    private fun mimeOf(file: File, declared: String?): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
            ?: declared
            ?: "application/octet-stream"

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
