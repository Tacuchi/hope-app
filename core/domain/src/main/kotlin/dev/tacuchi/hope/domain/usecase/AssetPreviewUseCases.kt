package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.AssetPreview
import dev.tacuchi.hope.domain.media.HopePlayer
import dev.tacuchi.hope.domain.media.PreviewPlayback
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Lo que suena en la preescucha: el asset, si suena, su avance y su duración. */
class ObserveAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke(): StateFlow<PreviewPlayback> = preview.state
}

/**
 * Escucha un audio del catálogo, o lo pausa si ya suena. Las imágenes no suenan. Antes pausa la
 * mezcla que suene, así se oye una sola cosa.
 */
class ToggleAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview, private val player: HopePlayer) {
    operator fun invoke(asset: Asset) {
        if (asset.type != AssetType.Audio) return
        if (preview.state.value.playingId != asset.id) player.pause()
        preview.toggle(asset.id, asset.file, asset.durationMs)
    }
}

/** Detiene la preescucha si suena [asset], por ejemplo antes de borrarlo. */
class StopAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke(asset: Asset) = preview.stopIfCurrent(asset.id)
}

/** Detiene lo que suene en la preescucha: al cerrar el selector, cambiar de audio o escuchar la mezcla. */
class StopAllAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke() = preview.stop()
}

class ReleaseAssetPreviewUseCase @Inject constructor(private val preview: AssetPreview) {
    operator fun invoke() = preview.release()
}
