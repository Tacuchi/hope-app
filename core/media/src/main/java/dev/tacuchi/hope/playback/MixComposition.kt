package dev.tacuchi.hope.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.GainShape
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.domain.model.SpeedLevel
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * La ganancia de una pista mientras suena: el volumen, que se cambia en vivo, por la forma de sus
 * fundidos, sus bajadas y el fundido de la hope completa ([GainShape]). Media3 la pide muestra a
 * muestra contando desde la posición que informa al saltar, así una rampa sigue en su punto
 * después de pausar o de saltar a la mitad.
 */
@OptIn(UnstableApi::class)
internal class GainEnvelope(private val shape: GainShape) : GainProcessor.GainProvider {
    @Volatile
    var volume: Float = 1f

    fun shapeAt(ms: Double): Double = shape.at(ms)

    /** Lo que se espera oír en [ms] de la pista: volumen por forma. */
    fun gainAt(ms: Double): Double = volume * shape.at(ms)

    private fun msAt(samplePosition: Long, sampleRate: Int) = samplePosition * 1_000.0 / sampleRate

    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float =
        (volume * shape.at(msAt(samplePosition, sampleRate))).toFloat()

    /**
     * Unidad hasta la próxima rampa, y nunca más de 100 ms: así un cambio de volumen se oye pronto.
     * Se decide solo por la forma: si el volumen cambia entre las dos preguntas de Media3, a lo
     * sumo sigue 100 ms con el volumen anterior, sin fallar.
     */
    override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long {
        val ms = msAt(samplePosition, sampleRate)
        if (shape.at(ms).toFloat() != 1f) return C.TIME_UNSET
        val us = (ms * 1_000).toLong()
        val nextUs = min(shape.unityEndsMs.map { it * 1_000 }.firstOrNull { it > us } ?: Long.MAX_VALUE, us + LIVE_WINDOW_US)
        return max(samplePosition + 1, nextUs * sampleRate / 1_000_000)
    }

    private companion object {
        const val LIVE_WINDOW_US = 100_000L
    }
}

/**
 * Procesadores que las pruebas agregan para medir: uno después de la ganancia de cada pista y los
 * de la mezcla ya hecha. La app no agrega ninguno.
 */
@OptIn(UnstableApi::class)
internal class MixTaps(
    val background: AudioProcessor? = null,
    val voice: AudioProcessor? = null,
    val mix: List<AudioProcessor> = emptyList(),
)

/**
 * Lo que suena para un [MixSpec]: dos secuencias que Media3 mezcla en una sola salida de audio, así
 * no pueden desfasarse.
 * - El fondo: su archivo recortado, a su velocidad (el tono baja con ella), con su sala y su
 *   ganancia; después, silencio hasta el final de la hope.
 * - La voz: silencio del largo de su desfase, su archivo recortado con voz clara, sala y ganancia,
 *   y silencio hasta el final.
 * El fundido de la hope completa es una ganancia sobre la mezcla ya hecha, y solo se agrega si la
 * hope tiene fundido. Dentro de cada pista no sirve: Media3 fija la posición de los procesadores de
 * un ítem con la marca del primer búfer decodificado, que el relleno inicial del AAC corre unos
 * 15 a 30 ms, así que la rampa de la voz (un ítem después de un silencio) quedaba adelantada
 * respecto de la hope. Ese procesador suma un búfer: con fundido de la hope, un cambio de volumen
 * en vivo tarda hasta unos 2,5 s en oírse (sin él, unos 2 s).
 */
@OptIn(UnstableApi::class)
internal class MixComposition(val spec: MixSpec, taps: MixTaps? = null) {
    private val hasBackground = spec.background != null && spec.backgroundSpanMs > 0
    private val hasVoice = spec.voice != null && spec.voiceSpanMs > 0

    /** El fundido de la hope completa, sobre la mezcla; nulo si la hope no tiene fundido. */
    val hopeGain: GainEnvelope? = spec.hopeEffects.fades.takeIf { it.count > 0 }?.let { fades ->
        GainEnvelope(GainShape(spec.durationMs, hopeFades = fades))
    }

    val backgroundGain: GainEnvelope? = if (hasBackground) GainEnvelope(spec.backgroundShape().withoutHopeFades()).apply {
        volume = spec.backgroundVolume.coerceIn(0, 100) / 100f
    } else null

    val voiceGain: GainEnvelope? = if (hasVoice) GainEnvelope(spec.voiceShape().withoutHopeFades()).apply {
        volume = spec.voiceVolume.coerceIn(0, 100) / 100f
    } else null

    /** Hasta el final de la última pista, en microsegundos. */
    val durationUs: Long = max(
        if (hasBackground) backgroundInHopeUs() else 0,
        if (hasVoice) spec.voiceStartMs * 1_000 + spec.voiceSpanMs * 1_000 else 0,
    )

    val composition: Composition = run {
        val sequences = buildList {
            if (hasBackground) {
                val lengthUs = backgroundInHopeUs()
                val item = edited(
                    file = checkNotNull(spec.background),
                    fileLengthMs = checkNotNull(spec.backgroundLengthMs),
                    startMs = spec.trimStartMs,
                    endMs = spec.trimEndMs,
                    speed = spec.backgroundEffects.speed,
                    processors = buildList {
                        spec.backgroundEffects.room.takeIf { it.isOn }?.let { add(RoomReverb(it.type, it.amount / 100f)) }
                        add(GainProcessor(checkNotNull(backgroundGain)))
                        taps?.background?.let(::add)
                    },
                )
                val sequence = audioSequence().addItem(item)
                if (durationUs > lengthUs) sequence.addGap(durationUs - lengthUs)
                add(sequence.build())
            }
            if (hasVoice) {
                val offsetUs = spec.voiceStartMs * 1_000
                val endUs = offsetUs + spec.voiceSpanMs * 1_000
                val sequence = audioSequence()
                if (offsetUs > 0) sequence.addGap(offsetUs)
                val effects = spec.voiceEffects
                sequence.addItem(
                    edited(
                        file = checkNotNull(spec.voice),
                        fileLengthMs = checkNotNull(spec.voiceLengthMs),
                        startMs = spec.voiceTrimStartMs,
                        endMs = spec.voiceTrimEndMs,
                        speed = SpeedLevel.Normal,
                        processors = buildList {
                            if (effects.clearVoice) add(ClearVoice())
                            effects.room.takeIf { it.isOn }?.let { add(RoomReverb(it.type, it.amount / 100f)) }
                            add(GainProcessor(checkNotNull(voiceGain)))
                            taps?.voice?.let(::add)
                        },
                    ),
                )
                if (durationUs > endUs) sequence.addGap(durationUs - endUs)
                add(sequence.build())
            }
        }
        val mixProcessors = listOfNotNull(hopeGain?.let(::GainProcessor)) + taps?.mix.orEmpty()
        Composition.Builder(sequences)
            .apply { if (mixProcessors.isNotEmpty()) setEffects(Effects(mixProcessors, emptyList())) }
            .build()
    }

    /** La forma de una pista sin el fundido de la hope, que va sobre la mezcla. */
    private fun GainShape.withoutHopeFades() = copy(hopeFades = Fades())

    private fun backgroundInHopeUs(): Long =
        (spec.backgroundSpanMs * 1_000 / spec.backgroundEffects.speed.speed.toDouble()).roundToLong()

    private fun audioSequence() = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))

    /**
     * Un archivo recortado: los cortes se acotan al archivo y siempre dejan un tramo que se puede
     * reproducir. Declara la duración del archivo para que Media3 no tenga que buscarla.
     */
    private fun edited(
        file: File,
        fileLengthMs: Long,
        startMs: Long,
        endMs: Long?,
        speed: SpeedLevel,
        processors: List<AudioProcessor>,
    ): EditedMediaItem {
        val start = startMs.coerceIn(0, max(0, fileLengthMs - 1))
        val end = endMs?.coerceIn(start + 1, fileLengthMs)?.takeIf { it < fileLengthMs }
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(start)
            .apply { end?.let(::setEndPositionMs) }
            .build()
        val item = MediaItem.Builder().setUri(Uri.fromFile(file)).setClippingConfiguration(clipping).build()
        return EditedMediaItem.Builder(item)
            .setRemoveVideo(true)
            .setDurationUs(fileLengthMs * 1_000)
            .apply { if (speed != SpeedLevel.Normal) setSpeed(ConstantSpeed(speed.speed)) }
            .setEffects(Effects(processors, emptyList()))
            .build()
    }
}
