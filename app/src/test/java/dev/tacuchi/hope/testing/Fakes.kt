package dev.tacuchi.hope.testing

import android.os.Looper
import dev.tacuchi.hope.playback.HopePlayer
import dev.tacuchi.hope.playback.MixPlayback
import dev.tacuchi.hope.playback.MixSpec
import dev.tacuchi.hope.ui.assets.AssetPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.robolectric.Shadows.shadowOf
import java.io.File

/** Room trabaja en sus hilos y vuelve al principal: se procesa el principal hasta que se cumpla. */
fun waitUntil(condition: () -> Boolean) {
    repeat(500) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        Thread.sleep(5)
    }
    error("No se cumplió a tiempo")
}

/** Recoge [flow] como lo haría la pantalla, para que los estados con `WhileSubscribed` se activen. */
fun collectWhileTesting(flow: Flow<*>): Job = CoroutineScope(Dispatchers.Main.immediate).launch { flow.collect {} }

/** Reproductor de hopes sin audio: anota lo pedido y deja fijar qué suena. */
class FakeHopePlayer : HopePlayer {
    override val state = MutableStateFlow(MixPlayback())
    val toggled = mutableListOf<Pair<String, MixSpec>>()
    val stopped = mutableListOf<String>()

    override fun toggle(key: String, mix: MixSpec) {
        toggled += key to mix
        val current = state.value
        state.value = if (current.key == key && current.isPlaying) current.copy(isPlaying = false)
        else MixPlayback(key, mix.presentation, isPlaying = true, durationMs = mix.durationMs)
    }

    override fun play(key: String, mix: MixSpec, startMs: Long?) {
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

/** Preescucha de Assets sin ExoPlayer. */
class FakeAssetPreview : AssetPreview {
    override val playingId = MutableStateFlow<Long?>(null)
    val toggledFiles = mutableListOf<File>()
    var released = false

    override fun toggle(id: Long, file: File) {
        toggledFiles += file
        playingId.value = if (playingId.value == id) null else id
    }

    override fun stopIfCurrent(id: Long) {
        if (playingId.value == id) playingId.value = null
    }

    override fun release() {
        released = true
        playingId.value = null
    }
}
