package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.HopePlayer
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeWithAssets
import dev.tacuchi.hope.domain.model.libraryKey
import dev.tacuchi.hope.domain.repository.HopeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHopesUseCase @Inject constructor(private val hopes: HopeRepository) {
    operator fun invoke(): Flow<List<HopeWithAssets>> = hopes.observe()
}

/** Las hopes que usan cada asset, por id de asset. */
class ObserveAssetUsesUseCase @Inject constructor(private val hopes: HopeRepository) {
    operator fun invoke(): Flow<Map<Long, List<AssetUse>>> = hopes.observeUses()
}

class GetHopeUseCase @Inject constructor(private val hopes: HopeRepository) {
    suspend operator fun invoke(id: Long): HopeWithAssets? = hopes.get(id)
}

/** Crea la hope si es nueva o la reemplaza. Devuelve su id. */
class SaveHopeUseCase @Inject constructor(private val hopes: HopeRepository) {
    suspend operator fun invoke(hope: Hope): Long = hopes.save(hope)
}

/** Deja de sonar la hope si sonaba y la borra; sus assets quedan en el catálogo. */
class DeleteHopeUseCase @Inject constructor(private val hopes: HopeRepository, private val player: HopePlayer) {
    suspend operator fun invoke(id: Long) {
        player.stop(libraryKey(id))
        hopes.delete(id)
    }
}

/** Pone un asset en una hope guardada: una imagen como portada, un audio como fondo. */
class UseAssetInHopeUseCase @Inject constructor(private val hopes: HopeRepository) {
    suspend operator fun invoke(hopeId: Long, asset: Asset) = hopes.useAsset(hopeId, asset)
}
