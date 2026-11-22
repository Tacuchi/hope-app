package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.AssetPreview
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** El asset que suena en la preescucha; nulo si nada suena. */
class ObserveAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke(): StateFlow<Long?> = preview.playingId
}

/** Escucha un audio del catálogo, o lo pausa si ya suena. Las imágenes no suenan. */
class ToggleAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke(asset: Asset) {
        if (asset.type != AssetType.Audio) return
        preview.toggle(asset.id, asset.file)
    }
}

/** Detiene la preescucha si suena [asset], por ejemplo antes de borrarlo. */
class StopAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke(asset: Asset) = preview.stopIfCurrent(asset.id)
}

class ReleaseAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke() = preview.release()
}
