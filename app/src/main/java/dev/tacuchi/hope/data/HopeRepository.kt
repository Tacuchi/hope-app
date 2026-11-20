package dev.tacuchi.hope.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Las hopes y su relación con los assets. Borrar una hope nunca borra sus assets. */
class HopeRepository(
    private val database: HopeDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.hopeDao()
    private val assetDao = database.assetDao()

    /** De la más reciente a la más antigua, con sus assets resueltos. */
    fun observe(): Flow<List<HopeWithAssets>> =
        combine(dao.observe(), assetDao.observe(null)) { hopes, assets ->
            val byId = assets.associateBy { it.id }
            hopes.map { it.withAssets(byId) }
        }

    /** Las hopes que usan cada asset, por id de asset. */
    fun observeUses(): Flow<Map<Long, List<AssetUse>>> =
        dao.observeUses().map { uses -> uses.groupBy { it.assetId } }

    suspend fun get(id: Long): HopeWithAssets? = database.withTransaction {
        val hope = dao.get(id) ?: return@withTransaction null
        val ids = listOfNotNull(hope.coverAssetId, hope.backgroundAssetId, hope.voiceAssetId)
        hope.withAssets(assetDao.getAll(ids).associateBy { it.id })
    }

    /** Crea la hope si es nueva ([Hope.id] 0) o la reemplaza. Devuelve su id. */
    suspend fun save(hope: Hope): Long =
        if (hope.id == 0L) {
            dao.insert(hope.copy(createdAt = now()))
        } else {
            dao.update(hope)
            hope.id
        }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Pone el asset en la hope: una imagen como portada, un audio como fondo. */
    suspend fun useAsset(hopeId: Long, asset: Asset) = database.withTransaction {
        val hope = dao.get(hopeId) ?: return@withTransaction
        dao.update(
            when (asset.type) {
                AssetType.Image -> hope.copy(coverAssetId = asset.id)
                AssetType.Audio -> hope.copy(backgroundAssetId = asset.id, trimStartMs = 0, trimEndMs = null)
            },
        )
    }

    private fun Hope.withAssets(byId: Map<Long, Asset>) = HopeWithAssets(
        hope = this,
        cover = coverAssetId?.let(byId::get),
        background = backgroundAssetId?.let(byId::get),
        voice = voiceAssetId?.let(byId::get),
    )
}
