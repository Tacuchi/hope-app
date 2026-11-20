package dev.tacuchi.hope.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import dev.tacuchi.hope.data.hopeDurationMs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Lo que hace falta para que suene una hope: sus archivos y sus ajustes. */
data class MixSpec(
    val background: File? = null,
    val backgroundLengthMs: Long? = null,
    /** De 0 a 100. */
    val backgroundVolume: Int = 100,
    val trimStartMs: Long = 0,
    val trimEndMs: Long? = null,
    val voice: File? = null,
    val voiceLengthMs: Long? = null,
    val voiceVolume: Int = 100,
    val voiceOffsetMs: Long = 0,
    val presentation: MixPresentation = MixPresentation(),
) {
    val durationMs: Long
        get() = hopeDurationMs(
            backgroundMs = if (background != null) backgroundLengthMs else null,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            voiceMs = if (voice != null) voiceLengthMs else null,
            voiceOffsetMs = voiceOffsetMs,
        )

    /** Lo que dura el fondo ya recortado. */
    val backgroundSpanMs: Long
        get() = hopeDurationMs(backgroundLengthMs.takeIf { background != null }, trimStartMs, trimEndMs, null, 0)

    /** Lo mismo salvo los volúmenes: se puede cambiar el volumen sin cortar lo que suena. */
    fun sameTimeline(other: MixSpec): Boolean =
        copy(backgroundVolume = other.backgroundVolume, voiceVolume = other.voiceVolume, presentation = other.presentation) == other
}

data class MixPresentation(val title: String = "", val cover: File? = null)

/** Qué está sonando. [key] identifica quién lo pidió: una hope de la biblioteca o el editor. */
data class MixPlayback(
    val key: String? = null,
    val presentation: MixPresentation = MixPresentation(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** Volumen que se puede cambiar mientras suena: el procesador lo lee muestra a muestra. */
@OptIn(UnstableApi::class, ExperimentalApi::class)
private class LiveGain : GainProcessor.GainProvider {
    @Volatile
    var gain: Float = 1f

    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float = gain

    /**
     * Al 100 % la ganancia es unitaria por un tramo corto (100 ms) y después se vuelve a preguntar:
     * así un cambio de volumen se aplica enseguida. Si no es 100 %, no hay tramo unitario.
     */
    override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long =
        if (gain == 1f) samplePosition + sampleRate / 10 else C.TIME_UNSET
}

/**
 * El único reproductor de hopes: lo usan el editor y la biblioteca, así se oye lo mismo en los
 * dos lados. Arma una sola reproducción con dos secuencias que Media3 mezcla en una misma salida
 * de audio: el fondo recortado y la voz precedida por un silencio del largo de su desfase. Como
 * salen por la misma pista de audio, no pueden desfasarse. El volumen de cada pista es un
 * procesador de ganancia que se ajusta en vivo. Todo corre en el hilo principal.
 *
 * [outputMeter] recibe la mezcla ya hecha; existe para medirla en las pruebas.
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
class MixPlayer internal constructor(
    private val context: Context,
    private val outputMeter: AudioProcessor?,
    private val onPlaybackRequested: (() -> Unit)? = null,
) {
    constructor(context: Context) : this(context, null)

    private val focus = PlaybackFocus(context, ::pause)
    private val handler = Handler(Looper.getMainLooper())
    private var player: CompositionPlayer? = null
    internal val sessionSource: Player? get() = player
    private var spec: MixSpec? = null
    private val backgroundGain = LiveGain()
    private val voiceGain = LiveGain()

    private val _state = MutableStateFlow(MixPlayback())
    val state: StateFlow<MixPlayback> = _state.asStateFlow()

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
    fun toggle(key: String, mix: MixSpec) {
        val current = _state.value
        if (current.key == key && current.isPlaying) pause() else play(key, mix)
    }

    /** Suena [mix] desde [startMs], o si no se da, desde donde quedó si es la misma y desde el inicio si cambió. */
    fun play(key: String, mix: MixSpec, startMs: Long? = null) {
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
    fun update(key: String, mix: MixSpec) {
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

    fun pause() {
        focus.abandon()
        val current = player ?: return
        if (!current.playWhenReady) return
        current.pause()
        _state.value = _state.value.copy(isPlaying = false, positionMs = current.currentPosition)
    }

    /** Detiene si suena [key], por ejemplo al cerrar el editor o borrar la hope. */
    fun stop(key: String) {
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
        _state.value = MixPlayback()
    }

    private fun load(mix: MixSpec, startMs: Long) {
        val current = player ?: CompositionPlayer.Builder(context).build().also {
            it.addListener(listener)
            player = it
        }
        current.setComposition(composition(mix), startMs)
        current.prepare()
    }

    private fun applyVolumes(mix: MixSpec) {
        backgroundGain.gain = mix.backgroundVolume.coerceIn(0, 100) / 100f
        voiceGain.gain = mix.voiceVolume.coerceIn(0, 100) / 100f
    }

    /**
     * Dos secuencias del mismo largo (la duración de la hope), rellenas con silencio: el fondo
     * recortado desde el inicio y la voz después de su desfase.
     */
    private fun composition(mix: MixSpec): Composition {
        val totalUs = mix.durationMs * 1_000
        val sequences = buildList {
            val bg = mix.background
            if (bg != null) {
                val clipping = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(mix.trimStartMs)
                    .apply { mix.trimEndMs?.let(::setEndPositionMs) }
                    .build()
                val item = MediaItem.Builder().setUri(Uri.fromFile(bg)).setClippingConfiguration(clipping).build()
                val spanUs = mix.backgroundSpanMs * 1_000
                val sequence = audioSequence().addItem(edited(item, mix.backgroundLengthMs, backgroundGain))
                if (totalUs > spanUs) sequence.addGap(totalUs - spanUs)
                add(sequence.build())
            }
            val voice = mix.voice
            if (voice != null) {
                val offsetUs = mix.voiceOffsetMs.coerceAtLeast(0) * 1_000
                val endUs = offsetUs + (mix.voiceLengthMs ?: 0) * 1_000
                val sequence = audioSequence()
                if (offsetUs > 0) sequence.addGap(offsetUs)
                sequence.addItem(edited(MediaItem.fromUri(Uri.fromFile(voice)), mix.voiceLengthMs, voiceGain))
                if (totalUs > endUs) sequence.addGap(totalUs - endUs)
                add(sequence.build())
            }
        }
        return Composition.Builder(sequences)
            .apply { outputMeter?.let { setEffects(Effects(listOf(it), emptyList())) } }
            .build()
    }

    private fun audioSequence() = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))

    private fun edited(item: MediaItem, lengthMs: Long?, gain: LiveGain): EditedMediaItem =
        EditedMediaItem.Builder(item)
            .setRemoveVideo(true)
            .apply { lengthMs?.let { setDurationUs(it * 1_000) } }
            .setEffects(Effects(listOf(GainProcessor(gain)), emptyList()))
            .build()

    private companion object {
        const val TICK_MS = 50L
    }
}
