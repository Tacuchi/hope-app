package dev.tacuchi.hope.domain.repository

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeWithAssets
import kotlinx.coroutines.flow.Flow

/** Las hopes y su relación con los assets. Borrar una hope nunca borra sus assets. */
interface HopeRepository {
    /** De la más reciente a la más antigua, con sus assets resueltos. */
    fun observe(): Flow<List<HopeWithAssets>>

    /** Las hopes que usan cada asset, por id de asset. */
    fun observeUses(): Flow<Map<Long, List<AssetUse>>>

    suspend fun get(id: Long): HopeWithAssets?

    /** Crea la hope si es nueva ([Hope.id] 0) o la reemplaza. Devuelve su id. */
    suspend fun save(hope: Hope): Long

    suspend fun delete(id: Long)

    /** Pone el asset en la hope: una imagen como portada, un audio como fondo. */
    suspend fun useAsset(hopeId: Long, asset: Asset)
}
