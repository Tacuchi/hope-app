package dev.tacuchi.hope.domain.testing

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetDeletion
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeWithAssets
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.repository.AssetRepository
import dev.tacuchi.hope.domain.repository.HopeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

/**
 * Catálogo en memoria con las mismas reglas que el de la app: los archivos pasan a [assetsDir],
 * la numeración sigue por tipo de nombre y un asset en uso no se borra.
 */
class FakeAssetRepository(
    private val assetsDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : AssetRepository {
    internal val rows = MutableStateFlow<List<Asset>>(emptyList())
    private var nextId = 1L

    /** Quién usa cada asset; lo conecta [FakeHopeRepository]. */
    internal var usesOf: (Long) -> List<AssetUse> = { emptyList() }

    override fun observe(type: AssetType?): Flow<List<Asset>> = rows.map { list ->
        list.filter { type == null || it.type == type }
            .sortedWith(compareByDescending<Asset> { it.createdAt }.thenByDescending { it.id })
    }

    override suspend fun get(id: Long): Asset? = rows.value.firstOrNull { it.id == id }

    override suspend fun add(items: List<NewAsset>): List<Asset> {
        assetsDir.mkdirs()
        val created = mutableListOf<Asset>()
        for (item in items) {
            val suffix = item.file.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()
            val target = File(assetsDir, "${UUID.randomUUID()}$suffix")
            item.file.copyTo(target)
            item.file.delete()
            val number = if (item.nameKind == AssetNameKind.Frame) {
                null
            } else {
                ((rows.value + created).filter { it.nameKind == item.nameKind }.mapNotNull { it.number }.maxOrNull() ?: 0) + 1
            }
            created += Asset(
                id = nextId++,
                type = item.nameKind.type,
                nameKind = item.nameKind,
                number = number,
                frameMs = item.frameMs,
                origin = item.origin,
                createdAt = now(),
                durationMs = item.durationMs,
                file = target,
                mimeType = item.mimeType,
            )
        }
        rows.value = rows.value + created
        return created
    }

    override suspend fun delete(asset: Asset): AssetDeletion {
        val uses = usesOf(asset.id)
        if (uses.isNotEmpty()) return AssetDeletion.InUse(uses)
        rows.value = rows.value.filterNot { it.id == asset.id }
        asset.file.delete()
        return AssetDeletion.Deleted
    }
}

/** Hopes en memoria sobre [assets]: resuelve sus assets y le informa qué asset está en uso. */
class FakeHopeRepository(
    private val assets: FakeAssetRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : HopeRepository {
    private val rows = MutableStateFlow<List<Hope>>(emptyList())
    private var nextId = 1L

    init {
        assets.usesOf = { id -> uses().filter { it.assetId == id } }
    }

    private fun sorted(list: List<Hope>) =
        list.sortedWith(compareByDescending<Hope> { it.createdAt }.thenByDescending { it.id })

    private fun Hope.withAssets(byId: Map<Long, Asset>) = HopeWithAssets(
        hope = this,
        cover = coverAssetId?.let(byId::get),
        background = backgroundAssetId?.let(byId::get),
        voice = voiceAssetId?.let(byId::get),
    )

    private fun uses(list: List<Hope> = rows.value): List<AssetUse> = sorted(list).flatMap { hope ->
        listOfNotNull(hope.coverAssetId, hope.backgroundAssetId, hope.voiceAssetId).distinct()
            .map { AssetUse(it, hope.id, hope.title) }
    }

    override fun observe(): Flow<List<HopeWithAssets>> = combine(rows, assets.rows) { hopes, catalog ->
        val byId = catalog.associateBy { it.id }
        sorted(hopes).map { it.withAssets(byId) }
    }

    override fun observeUses(): Flow<Map<Long, List<AssetUse>>> = rows.map { list -> uses(list).groupBy { it.assetId } }

    override suspend fun get(id: Long): HopeWithAssets? =
        rows.value.firstOrNull { it.id == id }?.withAssets(assets.rows.value.associateBy { it.id })

    override suspend fun save(hope: Hope): Long =
        if (hope.id == 0L) {
            val id = nextId++
            rows.value = rows.value + hope.copy(id = id, createdAt = now())
            id
        } else {
            rows.value = rows.value.map { if (it.id == hope.id) hope else it }
            hope.id
        }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun useAsset(hopeId: Long, asset: Asset) {
        rows.value = rows.value.map { hope ->
            if (hope.id != hopeId) {
                hope
            } else if (asset.type == AssetType.Image) {
                hope.copy(coverAssetId = asset.id)
            } else {
                hope.copy(backgroundAssetId = asset.id, trimStartMs = 0, trimEndMs = null)
            }
        }
    }
}
