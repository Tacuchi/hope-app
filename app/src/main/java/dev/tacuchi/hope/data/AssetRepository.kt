package dev.tacuchi.hope.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Archivo ya preparado (en la carpeta temporal) que pasa a ser un asset. */
data class NewAsset(
    val file: File,
    val nameKind: AssetNameKind,
    val origin: AssetOrigin,
    val mimeType: String,
    val durationMs: Long? = null,
    val frameMs: Long? = null,
)

/** Resultado de intentar borrar un asset. */
sealed interface AssetDeletion {
    data object Deleted : AssetDeletion

    /** No se borró: lo usan estas hopes. */
    data class InUse(val hopes: List<AssetUse>) : AssetDeletion
}

/**
 * Catálogo de assets: las filas viven en Room y los archivos en [assetsDir], dentro del
 * almacenamiento privado de la app.
 */
class AssetRepository(
    private val database: HopeDatabase,
    private val assetsDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.assetDao()
    private val hopeDao = database.hopeDao()

    fun observe(type: AssetType?): Flow<List<Asset>> = dao.observe(type)

    fun fileOf(asset: Asset): File = File(assetsDir, asset.fileName)

    /**
     * Mueve cada archivo a la carpeta de assets y crea sus filas en una sola transacción. Si algo
     * falla, no queda ninguna fila ni ningún archivo movido.
     */
    suspend fun add(items: List<NewAsset>): List<Asset> = withContext(Dispatchers.IO) {
        assetsDir.mkdirs()
        val moved = mutableListOf<File>()
        try {
            database.withTransaction {
                items.map { item ->
                    val target = uniqueTarget(item.file.extension)
                    moveFile(item.file, target)
                    moved += target
                    val number = if (item.nameKind == AssetNameKind.Frame) {
                        null
                    } else {
                        (dao.maxNumber(item.nameKind) ?: 0) + 1
                    }
                    val asset = Asset(
                        type = item.nameKind.type,
                        nameKind = item.nameKind,
                        number = number,
                        frameMs = item.frameMs,
                        origin = item.origin,
                        createdAt = now(),
                        durationMs = item.durationMs,
                        fileName = target.name,
                        mimeType = item.mimeType,
                    )
                    asset.copy(id = dao.insert(asset))
                }
            }
        } catch (error: Exception) {
            moved.forEach { it.delete() }
            throw error
        }
    }

    suspend fun get(id: Long): Asset? = dao.get(id)

    /** Quita la fila y libera el archivo, salvo que alguna hope lo use: entonces no toca nada. */
    suspend fun delete(asset: Asset): AssetDeletion = withContext(Dispatchers.IO) {
        val uses = database.withTransaction {
            hopeDao.usesOf(asset.id).also { if (it.isEmpty()) dao.delete(asset) }
        }
        if (uses.isNotEmpty()) return@withContext AssetDeletion.InUse(uses)
        fileOf(asset).delete()
        AssetDeletion.Deleted
    }

    private fun uniqueTarget(extension: String): File {
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        return File(assetsDir, "${java.util.UUID.randomUUID()}$suffix")
    }

    private fun moveFile(source: File, target: File) {
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }
}
