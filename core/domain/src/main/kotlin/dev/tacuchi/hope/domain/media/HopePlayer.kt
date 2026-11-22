package dev.tacuchi.hope.domain.media

import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.MixSpec
import kotlinx.coroutines.flow.StateFlow

/** El único reproductor de hopes del proceso: lo comparten el editor, la biblioteca y la notificación. */
interface HopePlayer {
    val state: StateFlow<MixPlayback>

    /** Toca una hope: si es la que suena la pausa; si estaba en pausa sigue; si es otra, empieza. */
    fun toggle(key: String, mix: MixSpec)

    /** Suena [mix] desde [startMs], o si es nulo, desde donde quedó si es la misma y desde el inicio si cambió. */
    fun play(key: String, mix: MixSpec, startMs: Long? = null)

    /** Cambia los ajustes de lo que suena para [key], sin cortar si solo cambió el volumen. */
    fun update(key: String, mix: MixSpec)

    fun pause()

    /** Detiene si suena [key], por ejemplo al cerrar el editor o borrar la hope. */
    fun stop(key: String)
}
