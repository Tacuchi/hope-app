package dev.tacuchi.hope.playback

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong

/** Una pista de la prueba: el archivo, su recorte, su volumen, sus fundidos y sus efectos. */
data class ProbeTrack(
    val file: File,
    val fileLengthMs: Long,
    val startMs: Long = 0,
    val endMs: Long? = null,
    val volume: Float = 1f,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val room: RoomKind? = null,
    val roomAmount: Float = 1f,
    val clearVoice: Boolean = false,
    val speed: Float = 1f,
) {
    /** Lo que dura el tramo recortado del archivo. */
    val trimmedMs: Long get() = ((endMs ?: fileLengthMs).coerceAtMost(fileLengthMs) - startMs).coerceAtLeast(0)

    /** Lo que dura en la hope: el tramo recortado dividido por la velocidad. */
    val lengthInHopeUs: Long get() = (trimmedMs * 1_000 / speed.toDouble()).roundToLong()
    val lengthInHopeMs: Double get() = lengthInHopeUs / 1_000.0
}

/** Una hope de prueba: el fondo empieza en 0 y la voz en su desfase, como en la mezcla actual. */
data class ProbeMix(
    val background: ProbeTrack? = null,
    val voice: ProbeTrack? = null,
    val voiceOffsetMs: Long = 0,
    val fadeInMs: Long = 0,
    val fadeOutMs: Long = 0,
    val duck: DuckLevel? = null,
) {
    /** Hasta el final de la última pista. */
    val durationUs: Long get() = max(background?.lengthInHopeUs ?: 0, voice?.let { voiceOffsetMs * 1_000 + it.lengthInHopeUs } ?: 0)
    val durationMs: Double get() = durationUs / 1_000.0

    val voiceStartMs: Double get() = voiceOffsetMs.toDouble()
    val voiceEndMs: Double get() = voiceOffsetMs + (voice?.lengthInHopeMs ?: 0.0)

    /** El fondo baja mientras suena la voz: del desfase al desfase más el largo de la voz recortada. */
    fun backgroundDips(): List<Dip> {
        val level = duck ?: return emptyList()
        if (background == null || voice == null) return emptyList()
        return listOf(Dip(voiceOffsetMs, voiceEndMs.roundToLong(), level.level, DuckLevel.ATTACK_MS, DuckLevel.RELEASE_MS))
    }
}

/**
 * Lo que se arma para una reproducción: la composición y las piezas que la prueba ajusta o mide.
 * Cada pista lleva sus efectos en este orden: voz clara, sala, ganancia (volumen, fundidos y
 * bajada) y, con [captureTracks], una captura de la pista. La mezcla lleva su fundido global y la
 * captura de la hope. Cada captura de pista suma un búfer después de la ganancia: para medir la
 * demora del volumen en vivo se arma sin ellas, como quedaría en la app.
 */
@OptIn(UnstableApi::class)
class ProbeRun(val mix: ProbeMix, private val captureTracks: Boolean = true) {
    val mixCapture = MixCapture()
    val backgroundCapture = MixCapture()
    val voiceCapture = MixCapture()
    val backgroundGain: GainEnvelope? = mix.background?.let {
        GainEnvelope(it.lengthInHopeUs / 1_000, it.fadeInMs, it.fadeOutMs, mix.backgroundDips()).apply { volume = it.volume }
    }
    val voiceGain: GainEnvelope? = mix.voice?.let {
        GainEnvelope(it.lengthInHopeUs / 1_000, it.fadeInMs, it.fadeOutMs).apply { volume = it.volume }
    }
    val mixGain = GainEnvelope(mix.durationUs / 1_000, mix.fadeInMs, mix.fadeOutMs)

    val composition: Composition = run {
        val totalUs = mix.durationUs
        val sequences = buildList {
            val bg = mix.background
            if (bg != null) {
                val sequence = audioSequence().addItem(edited(bg, checkNotNull(backgroundGain), backgroundCapture))
                if (totalUs > bg.lengthInHopeUs) sequence.addGap(totalUs - bg.lengthInHopeUs)
                add(sequence.build())
            }
            val voice = mix.voice
            if (voice != null) {
                val offsetUs = mix.voiceOffsetMs * 1_000
                val endUs = offsetUs + voice.lengthInHopeUs
                val sequence = audioSequence()
                if (offsetUs > 0) sequence.addGap(offsetUs)
                sequence.addItem(edited(voice, checkNotNull(voiceGain), voiceCapture))
                if (totalUs > endUs) sequence.addGap(totalUs - endUs)
                add(sequence.build())
            }
        }
        // El fundido de la hope va sobre la mezcla solo si hay uno. Ese procesador suma un búfer después de las
        // ganancias de pista: con él, la demora del volumen en vivo pasó de 1,8-2,0 s a 1,8-2,5 s.
        val mixProcessors = if (mix.fadeInMs > 0 || mix.fadeOutMs > 0) listOf(GainProcessor(mixGain), mixCapture) else listOf(mixCapture)
        Composition.Builder(sequences)
            .setEffects(Effects(mixProcessors, emptyList()))
            .build()
    }

    private fun audioSequence() = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))

    private fun edited(track: ProbeTrack, gain: GainEnvelope, capture: MixCapture): EditedMediaItem {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(track.startMs)
            .apply { track.endMs?.let(::setEndPositionMs) }
            .build()
        val item = MediaItem.Builder().setUri(Uri.fromFile(track.file)).setClippingConfiguration(clipping).build()
        val processors = buildList<AudioProcessor> {
            if (track.clearVoice) add(ClearVoice())
            track.room?.let { if (track.roomAmount > 0f) add(RoomReverb(it, track.roomAmount)) }
            add(GainProcessor(gain))
            if (captureTracks) add(capture)
        }
        return EditedMediaItem.Builder(item)
            .setRemoveVideo(true)
            .setDurationUs(track.fileLengthMs * 1_000)
            .apply { if (track.speed != 1f) setSpeed(ConstantSpeed(track.speed)) }
            .setEffects(Effects(processors, emptyList()))
            .build()
    }
}

/** Un `CompositionPlayer` para la prueba. Todo se llama en el hilo principal. */
@OptIn(UnstableApi::class)
class ProbePlayer(context: Context) {
    private val player = CompositionPlayer.Builder(context).build()

    fun start(run: ProbeRun, fromMs: Long = 0) {
        player.setComposition(run.composition, fromMs)
        player.prepare()
        player.play()
    }

    fun pause() = player.pause()
    fun play() = player.play()
    fun seekTo(ms: Long) = player.seekTo(ms)
    val positionMs: Long get() = player.currentPosition
    val durationMs: Long get() = player.duration
    val isPlaying: Boolean get() = player.isPlaying
    val ended: Boolean get() = player.playbackState == Player.STATE_ENDED
    fun release() = player.release()
}
