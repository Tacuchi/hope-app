package dev.tacuchi.hope.domain.testing

import dev.tacuchi.hope.domain.media.AssetPreview
import dev.tacuchi.hope.domain.media.PreviewPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Preescucha sin ExoPlayer: anota lo pedido y deja fijar el avance. */
class FakeAssetPreview : AssetPreview {
    override val state = MutableStateFlow(PreviewPlayback())
    val toggledFiles = mutableListOf<File>()
    var released = false

    override fun toggle(id: Long, file: File, durationMs: Long?) {
        toggledFiles += file
        val current = state.value
        state.value = when {
            current.assetId == id -> current.copy(isPlaying = !current.isPlaying)
            else -> PreviewPlayback(id, isPlaying = true, durationMs = durationMs ?: 0)
        }
    }

    /** Lleva lo que suena a [positionMs]. */
    fun advanceTo(positionMs: Long) {
        state.value = state.value.copy(positionMs = positionMs)
    }

    override fun stop() {
        state.value = PreviewPlayback()
    }

    override fun stopIfCurrent(id: Long) {
        if (state.value.assetId == id) state.value = PreviewPlayback()
    }

    override fun release() {
        released = true
        state.value = PreviewPlayback()
    }
}
