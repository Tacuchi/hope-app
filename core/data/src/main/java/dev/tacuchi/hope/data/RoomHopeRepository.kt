package dev.tacuchi.hope.data

import androidx.room.withTransaction
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeWithAssets
import dev.tacuchi.hope.domain.repository.HopeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

/** Las hopes y su relación con los assets, en Room. Borrar una hope nunca borra sus assets. */
class RoomHopeRepository(
    private val database: HopeDatabase,
    private val assetsDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : HopeRepository {
    private val dao = database.hopeDao()
    private val assetDao = database.assetDao()

    override fun observe(): Flow<List<HopeWithAssets>> =
        combine(dao.observe(), assetDao.observe(null)) { hopes, assets ->
            val byId = assets.associateBy { it.id }
            hopes.map { it.withAssets(byId) }
        }

    override fun observeUses(): Flow<Map<Long, List<AssetUse>>> =
        dao.observeUses().map { uses -> uses.map { it.toDomain() }.groupBy { it.assetId } }

    override suspend fun get(id: Long): HopeWithAssets? = database.withTransaction {
        val hope = dao.get(id) ?: return@withTransaction null
        val ids = listOfNotNull(hope.coverAssetId, hope.backgroundAssetId, hope.voiceAssetId)
        hope.withAssets(assetDao.getAll(ids).associateBy { it.id })
    }

    override suspend fun save(hope: Hope): Long =
        if (hope.id == 0L) {
            dao.insert(hope.copy(createdAt = now()).toEntity())
        } else {
            dao.update(hope.toEntity())
            hope.id
        }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun useAsset(hopeId: Long, asset: Asset) = database.withTransaction {
        val hope = dao.get(hopeId) ?: return@withTransaction
        dao.update(
            when (asset.type) {
                AssetType.Image -> hope.copy(coverAssetId = asset.id)
                AssetType.Audio -> hope.copy(backgroundAssetId = asset.id, trimStartMs = 0, trimEndMs = null)
            },
        )
    }

    private fun HopeEntity.withAssets(byId: Map<Long, AssetEntity>) = HopeWithAssets(
        hope = toDomain(),
        cover = coverAssetId?.let(byId::get)?.toDomain(assetsDir),
        background = backgroundAssetId?.let(byId::get)?.toDomain(assetsDir),
        voice = voiceAssetId?.let(byId::get)?.toDomain(assetsDir),
    )
}
