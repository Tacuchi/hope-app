package dev.tacuchi.hope.domain.media

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** La preescucha de Assets: una por pantalla, que se libera al salir. */
interface AssetPreview {
    /** El asset que está sonando ahora; nulo si nada suena. */
    val playingId: StateFlow<Long?>

    /** Toca un audio: si es el que suena lo pausa, si estaba en pausa sigue, si es otro lo cambia. */
    fun toggle(id: Long, file: File)

    /** Detiene el audio si es el de [id], por ejemplo antes de borrarlo. */
    fun stopIfCurrent(id: Long)

    fun release()
}
