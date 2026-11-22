package dev.tacuchi.hope.domain.repository

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetDeletion
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.NewAsset
import kotlinx.coroutines.flow.Flow

/** El catálogo de assets y sus archivos. */
interface AssetRepository {
    /** Del más reciente al más antiguo; [type] nulo trae todos. */
    fun observe(type: AssetType?): Flow<List<Asset>>

    suspend fun get(id: Long): Asset?

    /** Mueve cada archivo al catálogo y crea sus assets de una vez: si algo falla, no queda nada. */
    suspend fun add(items: List<NewAsset>): List<Asset>

    /** Borra el asset y su archivo, salvo que alguna hope lo use: entonces no toca nada. */
    suspend fun delete(asset: Asset): AssetDeletion
}
