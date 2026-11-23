package dev.tacuchi.hope.domain.media

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Lo que suena en la preescucha: qué asset, si suena, cuánto avanzó y cuánto dura. */
data class PreviewPlayback(
    /** El asset cargado, sonando o en pausa; nulo si no hay ninguno. */
    val assetId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    /** El asset que suena ahora; nulo si nada suena. */
    val playingId: Long? get() = assetId.takeIf { isPlaying }
}

/** La preescucha de un audio del catálogo: una por pantalla, que se libera al salir. */
interface AssetPreview {
    val state: StateFlow<PreviewPlayback>

    /** Toca un audio: si es el que suena lo pausa, si estaba en pausa sigue, si es otro lo cambia. */
    fun toggle(id: Long, file: File, durationMs: Long?)

    /** Detiene lo que suene, por ejemplo al cerrar el selector o al salir de la pantalla. */
    fun stop()

    /** Detiene el audio si es el de [id], por ejemplo antes de borrarlo. */
    fun stopIfCurrent(id: Long)

    fun release()
}
