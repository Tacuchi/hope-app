package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetDeletion
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.repository.AssetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** El catálogo, del más reciente al más antiguo; [type] nulo trae todos. */
class ObserveAssetsUseCase @Inject constructor(private val assets: AssetRepository) {
    operator fun invoke(type: AssetType? = null): Flow<List<Asset>> = assets.observe(type)
}

class GetAssetUseCase @Inject constructor(private val assets: AssetRepository) {
    suspend operator fun invoke(id: Long): Asset? = assets.get(id)
}

/** Borra un asset libre con su archivo; uno en uso queda como estaba. */
class DeleteAssetUseCase @Inject constructor(private val assets: AssetRepository) {
    suspend operator fun invoke(asset: Asset): AssetDeletion = assets.delete(asset)
}
