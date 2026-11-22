package dev.tacuchi.hope.data

import androidx.room.withTransaction
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetDeletion
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.repository.AssetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Catálogo de assets: las filas viven en Room y los archivos en [assetsDir], dentro del
 * almacenamiento privado de la app.
 */
class RoomAssetRepository(
    private val database: HopeDatabase,
    private val assetsDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : AssetRepository {
    private val dao = database.assetDao()
    private val hopeDao = database.hopeDao()

    override fun observe(type: AssetType?): Flow<List<Asset>> =
        dao.observe(type).map { rows -> rows.map { it.toDomain(assetsDir) } }

    override suspend fun get(id: Long): Asset? = dao.get(id)?.toDomain(assetsDir)

    /**
     * Mueve cada archivo a la carpeta de assets y crea sus filas en una sola transacción. Si algo
     * falla, no queda ninguna fila ni ningún archivo movido.
     */
    override suspend fun add(items: List<NewAsset>): List<Asset> = withContext(Dispatchers.IO) {
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
                    val row = AssetEntity(
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
                    row.copy(id = dao.insert(row)).toDomain(assetsDir)
                }
            }
        } catch (error: Exception) {
            moved.forEach { it.delete() }
            throw error
        }
    }

    /** Quita la fila y libera el archivo, salvo que alguna hope lo use: entonces no toca nada. */
    override suspend fun delete(asset: Asset): AssetDeletion = withContext(Dispatchers.IO) {
        val uses = database.withTransaction {
            hopeDao.usesOf(asset.id).also { if (it.isEmpty()) dao.delete(asset.toEntity()) }
        }
        if (uses.isNotEmpty()) return@withContext AssetDeletion.InUse(uses.map { it.toDomain() })
        File(assetsDir, asset.file.name).delete()
        AssetDeletion.Deleted
    }

    private fun uniqueTarget(extension: String): File {
        val suffix = if (extension.isEmpty()) "" else ".$extension"
        return File(assetsDir, "${UUID.randomUUID()}$suffix")
    }

    private fun moveFile(source: File, target: File) {
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }
}
