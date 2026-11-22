package dev.tacuchi.hope.domain.usecase

import dev.tacuchi.hope.domain.media.HopePlayer
import dev.tacuchi.hope.domain.model.HopeWithAssets
import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.domain.model.libraryKey
import dev.tacuchi.hope.domain.model.toMixSpec
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Lo que suena en el reproductor de hopes del proceso. */
class ObservePlaybackUseCase @Inject constructor(private val player: HopePlayer) {
    operator fun invoke(): StateFlow<MixPlayback> = player.state
}

/** Toca una hope guardada con sus ajustes: la pausa si suena, si no la reproduce. */
class ToggleHopePlaybackUseCase @Inject constructor(private val player: HopePlayer) {
    operator fun invoke(hope: HopeWithAssets) = player.toggle(libraryKey(hope.hope.id), hope.toMixSpec())
}

/** Toca una mezcla: si es la que suena la pausa; si estaba en pausa sigue; si es otra, empieza. */
class ToggleMixUseCase @Inject constructor(private val player: HopePlayer) {
    operator fun invoke(key: String, mix: MixSpec) = player.toggle(key, mix)
}

/** Suena una mezcla desde [startMs], o desde donde quedó si es la misma. */
class PlayMixUseCase @Inject constructor(private val player: HopePlayer) {
    operator fun invoke(key: String, mix: MixSpec, startMs: Long? = null) = player.play(key, mix, startMs)
}

/** Cambia los ajustes de lo que suena para [key], sin cortar si solo cambió el volumen. */
class UpdateMixUseCase @Inject constructor(private val player: HopePlayer) {
    operator fun invoke(key: String, mix: MixSpec) = player.update(key, mix)
}

/** Detiene lo que suena para [key]; si suena otra cosa, no la toca. */
class StopMixUseCase @Inject constructor(private val player: HopePlayer) {
    operator fun invoke(key: String) = player.stop(key)
}
