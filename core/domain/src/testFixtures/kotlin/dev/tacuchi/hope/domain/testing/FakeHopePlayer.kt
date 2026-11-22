package dev.tacuchi.hope.domain.testing

import dev.tacuchi.hope.domain.media.HopePlayer
import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.MixSpec
import kotlinx.coroutines.flow.MutableStateFlow

/** Reproductor de hopes sin audio: anota lo pedido y deja fijar qué suena. */
class FakeHopePlayer : HopePlayer {
    override val state = MutableStateFlow(MixPlayback())
    val toggled = mutableListOf<Pair<String, MixSpec>>()
    val played = mutableListOf<Triple<String, MixSpec, Long?>>()
    val stopped = mutableListOf<String>()

    override fun toggle(key: String, mix: MixSpec) {
        toggled += key to mix
        val current = state.value
        state.value = if (current.key == key && current.isPlaying) current.copy(isPlaying = false)
        else MixPlayback(key, mix.presentation, isPlaying = true, durationMs = mix.durationMs)
    }

    override fun play(key: String, mix: MixSpec, startMs: Long?) {
        played += Triple(key, mix, startMs)
        state.value = MixPlayback(key, mix.presentation, isPlaying = true, positionMs = startMs ?: 0, durationMs = mix.durationMs)
    }

    override fun update(key: String, mix: MixSpec) = Unit

    override fun pause() {
        state.value = state.value.copy(isPlaying = false)
    }

    override fun stop(key: String) {
        stopped += key
        if (state.value.key == key) state.value = MixPlayback()
    }
}
