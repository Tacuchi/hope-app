package dev.tacuchi.hope.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.Dip
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.GainShape
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.VoiceEffects
import java.io.File
import kotlin.math.roundToInt
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
 * Lo que se arma para una reproducción: la composición de la app ([MixComposition]) para la misma
 * hope y las piezas que la prueba ajusta o mide. Con [captureTracks], cada pista lleva una captura
 * después de su ganancia; la mezcla lleva siempre la captura de la hope. Cada captura de pista suma
 * un búfer después de la ganancia: para medir la demora del volumen en vivo se arma sin ellas.
 *
 * Las ganancias esperadas se calculan aparte: la de cada pista (su volumen, sus fundidos y su
 * bajada) y la de la hope ([mixGain]), que la app aplica sobre la mezcla antes de la captura.
 */
@OptIn(UnstableApi::class)
class ProbeRun(val mix: ProbeMix, private val captureTracks: Boolean = true) {
    val mixCapture = MixCapture()
    val backgroundCapture = MixCapture()
    val voiceCapture = MixCapture()

    /** El mismo [MixSpec] que armaría la app para esta hope. */
    val spec: MixSpec = mix.toSpec()

    private val built = MixComposition(
        spec,
        MixTaps(
            background = backgroundCapture.takeIf { captureTracks },
            voice = voiceCapture.takeIf { captureTracks },
            mix = listOf(mixCapture),
        ),
    )

    val backgroundGain: ExpectedGain? = mix.background?.let {
        ExpectedGain(GainShape(it.lengthInHopeUs / 1_000, Fades(it.fadeInMs, it.fadeOutMs), dips = mix.backgroundDips()), built.backgroundGain)
    }
    val voiceGain: ExpectedGain? = mix.voice?.let {
        ExpectedGain(GainShape(it.lengthInHopeUs / 1_000, Fades(it.fadeInMs, it.fadeOutMs)), built.voiceGain)
    }
    val mixGain = ExpectedGain(GainShape(mix.durationUs / 1_000, Fades(mix.fadeInMs, mix.fadeOutMs)), null)

    val composition: Composition = built.composition
}

/** La hope de prueba como la guarda la app. */
private fun ProbeMix.toSpec(): MixSpec {
    fun percent(value: Float) = (value * 100).roundToInt()
    fun room(track: ProbeTrack) = RoomEffect(track.room ?: RoomType.None, percent(track.roomAmount))
    val bg = background
    val vo = voice
    return MixSpec(
        background = bg?.file,
        backgroundLengthMs = bg?.fileLengthMs,
        backgroundVolume = bg?.let { percent(it.volume) } ?: 100,
        trimStartMs = bg?.startMs ?: 0,
        trimEndMs = bg?.endMs,
        voice = vo?.file,
        voiceLengthMs = vo?.fileLengthMs,
        voiceVolume = vo?.let { percent(it.volume) } ?: 100,
        voiceOffsetMs = voiceOffsetMs,
        voiceTrimStartMs = vo?.startMs ?: 0,
        voiceTrimEndMs = vo?.endMs,
        backgroundEffects = BackgroundEffects(
            fades = Fades(bg?.fadeInMs ?: 0, bg?.fadeOutMs ?: 0),
            room = bg?.let(::room) ?: RoomEffect(),
            speed = SpeedLevel.entries.first { it.speed == (bg?.speed ?: 1f) },
        ),
        voiceEffects = VoiceEffects(
            fades = Fades(vo?.fadeInMs ?: 0, vo?.fadeOutMs ?: 0),
            room = vo?.let(::room) ?: RoomEffect(),
            clearVoice = vo?.clearVoice ?: false,
        ),
        hopeEffects = HopeEffects(Fades(fadeInMs, fadeOutMs), duck),
    )
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
