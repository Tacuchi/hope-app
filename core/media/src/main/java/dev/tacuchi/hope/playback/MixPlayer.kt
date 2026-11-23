package dev.tacuchi.hope.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.CompositionPlayer
import android.content.Intent
import dev.tacuchi.hope.domain.media.HopePlayer
import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.MixSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El único reproductor de hopes: lo usan el editor y la biblioteca, así se oye lo mismo en los
 * dos lados. Cada mezcla es una [MixComposition]: el fondo y la voz, recortados y con sus efectos,
 * en dos secuencias que salen por la misma pista de audio y no pueden desfasarse. El volumen de
 * cada pista se ajusta en vivo; cualquier otro cambio rearma la composición en el mismo punto.
 * Todo corre en el hilo principal.
 *
 * [outputMeter] recibe la mezcla ya hecha; existe para medirla en las pruebas.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
class MixPlayer internal constructor(
    private val context: Context,
    private val outputMeter: AudioProcessor?,
    private val onPlaybackRequested: (() -> Unit)? = null,
) : HopePlayer {
    /** El de la app: al empezar a sonar levanta [PlaybackService] para seguir en segundo plano. */
    constructor(context: Context) : this(context, null, {
        context.startService(Intent(context, PlaybackService::class.java))
    })

    private val focus = PlaybackFocus(context, ::pause)
    private val handler = Handler(Looper.getMainLooper())
    private var player: CompositionPlayer? = null
    internal val sessionSource: Player? get() = player
    private var spec: MixSpec? = null
    private var current: MixComposition? = null

    private val _state = MutableStateFlow(MixPlayback())
    override val state: StateFlow<MixPlayback> = _state.asStateFlow()

    private val tick = object : Runnable {
        override fun run() {
            val current = player ?: return
            if (!current.isPlaying) return
            _state.value = _state.value.copy(positionMs = current.currentPosition)
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val current = player ?: return
            _state.value = _state.value.copy(isPlaying = isPlaying, positionMs = current.currentPosition)
            handler.removeCallbacks(tick)
            if (isPlaying) handler.post(tick)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (_state.value.key != null) _state.value = _state.value.copy(positionMs = newPosition.positionMs)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            // Terminó la última pista: queda listo para volver a empezar.
            focus.abandon()
            player?.pause()
            player?.seekTo(0)
            _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
        }
    }

    /** Toca una hope: si es la que suena la pausa; si estaba en pausa sigue; si es otra, empieza. */
    override fun toggle(key: String, mix: MixSpec) {
        val current = _state.value
        if (current.key == key && current.isPlaying) pause() else play(key, mix)
    }

    /** Suena [mix] desde [startMs], o si no se da, desde donde quedó si es la misma y desde el inicio si cambió. */
    override fun play(key: String, mix: MixSpec, startMs: Long?) {
        val current = _state.value
        val same = current.key == key && spec?.sameTimeline(mix) == true
        val resumeAt = startMs ?: if (same) current.positionMs else 0L
        applyVolumes(mix)
        if (mix.durationMs <= 0 || !focus.acquire()) return
        val from = resumeAt.takeIf { it < mix.durationMs } ?: 0L
        if (!same) load(mix, from) else player?.seekTo(from)
        spec = mix
        _state.value = MixPlayback(key, mix.presentation, isPlaying = false, positionMs = from, durationMs = mix.durationMs)
        onPlaybackRequested?.invoke()
        player?.play()
    }

    /**
     * Cambia los ajustes de lo que suena para [key]. Si solo cambió el volumen se aplica en el
     * acto; si cambió el recorte, el desfase o una pista, sigue desde el mismo punto con la mezcla
     * nueva.
     */
    override fun update(key: String, mix: MixSpec) {
        val current = _state.value
        if (current.key != key) return
        val old = spec ?: return
        applyVolumes(mix)
        spec = mix
        _state.value = current.copy(presentation = mix.presentation)
        if (old.sameTimeline(mix)) return
        val position = current.positionMs.coerceIn(0, mix.durationMs)
        _state.value = _state.value.copy(positionMs = position, durationMs = mix.durationMs)
        if (mix.durationMs <= 0) {
            stop(key)
            return
        }
        val wasPlaying = player?.playWhenReady == true
        load(mix, position)
        if (wasPlaying) player?.play()
    }

    internal fun resume() {
        if (spec != null && focus.acquire()) player?.play()
    }

    override fun pause() {
        focus.abandon()
        val current = player ?: return
        if (!current.playWhenReady) return
        current.pause()
        _state.value = _state.value.copy(isPlaying = false, positionMs = current.currentPosition)
    }

    /** Detiene si suena [key], por ejemplo al cerrar el editor o borrar la hope. */
    override fun stop(key: String) {
        if (_state.value.key != key) return
        player?.stop()
        release()
    }

    fun release() {
        focus.abandon()
        handler.removeCallbacks(tick)
        player?.removeListener(listener)
        player?.release()
        player = null
        spec = null
        current = null
        _state.value = MixPlayback()
    }

    private fun load(mix: MixSpec, startMs: Long) {
        val current = player ?: CompositionPlayer.Builder(context).build().also {
            it.addListener(listener)
            player = it
        }
        val built = MixComposition(mix, outputMeter?.let { MixTaps(mix = listOf(it)) })
        this.current = built
        current.setComposition(built.composition, startMs)
        current.prepare()
    }

    /** El volumen de lo que ya suena cambia en el acto; una composición nueva lo toma de [mix]. */
    private fun applyVolumes(mix: MixSpec) {
        val built = current ?: return
        built.backgroundGain?.volume = mix.backgroundVolume.coerceIn(0, 100) / 100f
        built.voiceGain?.volume = mix.voiceVolume.coerceIn(0, 100) / 100f
    }

    private companion object {
        const val TICK_MS = 50L
    }
}
