package dev.tacuchi.hope.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Un tramo en el que la ganancia baja a [level], con rampas lineales que terminan en [startMs] y empiezan en [endMs]. */
data class Dip(val startMs: Long, val endMs: Long, val level: Double, val attackMs: Long, val releaseMs: Long)

/**
 * La ganancia de una pista (o de la mezcla) a lo largo de su duración en la hope: el volumen, que
 * se cambia en vivo, por el fundido de entrada, el de salida y las bajadas. Los fundidos son
 * rampas lineales de amplitud acotadas al largo de la pista; si se pisan, se multiplican.
 *
 * Media3 pide la ganancia muestra a muestra contando desde la posición que informa al saltar, así
 * que una rampa sigue en su punto después de pausar o de saltar a la mitad.
 */
@OptIn(UnstableApi::class)
class GainEnvelope(
    private val lengthMs: Long,
    fadeInMs: Long = 0,
    fadeOutMs: Long = 0,
    private val dips: List<Dip> = emptyList(),
) : GainProcessor.GainProvider {
    @Volatile
    var volume: Float = 1f

    private val fadeInMs = fadeInMs.coerceIn(0, lengthMs)
    private val fadeOutMs = fadeOutMs.coerceIn(0, lengthMs)

    /** Donde la forma deja de valer 1: el inicio del fundido de salida y de cada rampa de bajada. */
    private val unityEndsUs: List<Long> = buildList {
        if (this@GainEnvelope.fadeOutMs > 0) add((lengthMs - this@GainEnvelope.fadeOutMs) * 1_000)
        dips.forEach { add(max(0, it.startMs - it.attackMs) * 1_000) }
    }.sorted()

    /** La forma sin el volumen, en un instante de la pista. */
    fun shapeAt(ms: Double): Double {
        var gain = 1.0
        if (fadeInMs > 0 && ms < fadeInMs) gain *= (ms / fadeInMs).coerceIn(0.0, 1.0)
        if (fadeOutMs > 0 && ms > lengthMs - fadeOutMs) gain *= ((lengthMs - ms) / fadeOutMs).coerceIn(0.0, 1.0)
        for (dip in dips) gain *= dipAt(dip, ms)
        return gain
    }

    /** Lo que se espera oír en [ms]: volumen por forma. */
    fun gainAt(ms: Double): Double = volume * shapeAt(ms)

    private fun dipAt(dip: Dip, ms: Double): Double {
        val attackStart = (dip.startMs - dip.attackMs).toDouble()
        val releaseEnd = (dip.endMs + dip.releaseMs).toDouble()
        return when {
            ms <= attackStart || ms >= releaseEnd -> 1.0
            ms < dip.startMs -> 1.0 - (1.0 - dip.level) * (ms - attackStart) / dip.attackMs
            ms <= dip.endMs -> dip.level
            else -> dip.level + (1.0 - dip.level) * (ms - dip.endMs) / dip.releaseMs
        }
    }

    private fun msAt(samplePosition: Long, sampleRate: Int) = samplePosition * 1_000.0 / sampleRate

    override fun getGainFactorAtSamplePosition(samplePosition: Long, sampleRate: Int): Float =
        (volume * shapeAt(msAt(samplePosition, sampleRate))).toFloat()

    /**
     * Unidad hasta la próxima rampa, y nunca más de 100 ms: así un cambio de volumen se aplica
     * enseguida, como en la mezcla actual. Se decide solo por la forma: si el volumen cambia entre
     * las dos preguntas de Media3, a lo sumo sigue 100 ms con el volumen anterior, sin fallar.
     */
    override fun isUnityUntil(samplePosition: Long, sampleRate: Int): Long {
        val ms = msAt(samplePosition, sampleRate)
        if (shapeAt(ms).toFloat() != 1f) return C.TIME_UNSET
        val us = (ms * 1_000).toLong()
        val nextUs = min(unityEndsUs.firstOrNull { it > us } ?: Long.MAX_VALUE, us + LIVE_WINDOW_US)
        return max(samplePosition + 1, nextUs * sampleRate / 1_000_000)
    }

    private companion object {
        const val LIVE_WINDOW_US = 100_000L
    }
}

/** Cuánto baja el fondo mientras suena la voz. */
enum class DuckLevel(val db: Double) {
    /** «Poco». */
    Little(-6.0),

    /** «Medio». */
    Medium(-12.0),

    /** «Mucho». */
    Much(-18.0);

    val level: Double get() = db.fromDb()

    companion object {
        /** La rampa de bajada termina justo cuando entra la voz: el fondo ya está abajo en su primera sílaba. */
        const val ATTACK_MS = 200L

        /** La subida empieza cuando termina la voz y es más lenta, para que no se note el regreso. */
        const val RELEASE_MS = 500L
    }
}

/** Velocidad del fondo; el tono baja con ella, como un disco más lento. */
enum class SlowLevel(val speed: Float) {
    /** «Normal 1×». */
    Normal(1f),

    /** «Lento 0,9×». */
    Slow(0.9f),

    /** «Más lento 0,8×». */
    Slower(0.8f),
}

/** Una velocidad fija para toda la pista. */
@OptIn(UnstableApi::class)
class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * Tipos de sala. [t60Seconds] es el tiempo nominal en que cada filtro de la cola baja 60 dB;
 * [damping] apaga antes los agudos (por eso la cola medida sale algo más corta que la nominal);
 * [preDelayMs] separa la voz seca de su cola; [wetAtFull] deja la cola 3 dB por debajo de la señal
 * seca con «Cuánto se nota» al 100 %, medido con ruido blanco, para que los tres tipos se noten parecido.
 */
enum class RoomKind(val t60Seconds: Double, val damping: Double, val preDelayMs: Double, val wetAtFull: Double) {
    /** «Cuarto»: cola medida de unos 0,4 s. */
    Room(0.5, 0.45, 5.0, 2.81),

    /** «Sala»: cola medida de unos 1,2 s. */
    Hall(1.5, 0.30, 20.0, 2.24),

    /** «Iglesia»: cola medida de unos 3 s. */
    Church(3.5, 0.15, 35.0, 1.61),
}

/**
 * Sala de una sola pista, con la estructura de Freeverb (dominio público): ocho filtros peine en
 * paralelo con un paso bajo en su realimentación y cuatro pasa todo en serie, por canal. Cada peine
 * tiene su propia realimentación para que todos caigan 60 dB en [RoomKind.t60Seconds]. La señal
 * seca pasa entera; [amount] (de 0 a 1) solo cambia cuánto se suma de la cola.
 *
 * La salida tiene tantas muestras como la entrada: la pista dura lo mismo y la cola se corta donde
 * termina la pista (un fundido de salida la apaga antes, porque va después).
 */
@OptIn(UnstableApi::class)
class RoomReverb(private val kind: RoomKind, amount: Float) : BaseAudioProcessor() {
    private val wet = kind.wetAtFull * amount.coerceIn(0f, 1f)
    private var banks: List<ReverbBank> = emptyList()
    private var preDelay = FloatArray(1)
    private var preDelayIndex = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    /** Al saltar o empezar, la cola anterior ya no corresponde: arranca en silencio. */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // Media3 también avisa saltos antes de saber el formato; entonces no hay nada que preparar.
        if (inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) prepare()
    }

    private fun prepare() {
        val rate = inputAudioFormat.sampleRate
        banks = List(inputAudioFormat.channelCount) { channel -> ReverbBank(kind, rate, spread = channel * STEREO_SPREAD) }
        preDelay = FloatArray(max(1, (kind.preDelayMs * rate / 1000).roundToInt()))
        preDelayIndex = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val channels = format.channelCount
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        if (banks.size != channels) prepare()
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(input.remaining())
        val frame = FloatArray(channels)
        while (input.hasRemaining()) {
            var mono = 0f
            for (c in 0 until channels) {
                frame[c] = if (isFloat) input.float else input.short / 32768f
                mono += frame[c]
            }
            val delayed = preDelay[preDelayIndex]
            preDelay[preDelayIndex] = mono / channels
            preDelayIndex = (preDelayIndex + 1) % preDelay.size
            for (c in 0 until channels) {
                val value = frame[c] + (wet * banks[c].process(delayed * INPUT_GAIN)).toFloat()
                if (isFloat) output.putFloat(value) else output.putShort((value * 32768f).coerceIn(-32768f, 32767f).toInt().toShort())
            }
        }
        output.flip()
    }

    override fun onReset() {
        banks = emptyList()
        preDelay = FloatArray(1)
    }

    private class ReverbBank(kind: RoomKind, rate: Int, spread: Int) {
        private val scale = rate / 44_100.0
        private val combs = COMBS.map { (((it + spread) * scale).roundToInt()) }
        private val combBuffers = combs.map { FloatArray(it) }
        private val combIndex = IntArray(combs.size)
        private val combFeedback = combs.map { 10.0.pow(-3.0 * it / (rate * kind.t60Seconds)) }
        private val combLow = DoubleArray(combs.size)
        private val damping = kind.damping
        private val allpassBuffers = ALLPASSES.map { FloatArray(((it + spread) * scale).roundToInt()) }
        private val allpassIndex = IntArray(ALLPASSES.size)

        fun process(input: Float): Double {
            var sum = 0.0
            for (i in combBuffers.indices) {
                val buffer = combBuffers[i]
                val out = buffer[combIndex[i]].toDouble()
                combLow[i] = out * (1 - damping) + combLow[i] * damping
                buffer[combIndex[i]] = (input + combLow[i] * combFeedback[i]).toFloat()
                combIndex[i] = (combIndex[i] + 1) % buffer.size
                sum += out
            }
            for (i in allpassBuffers.indices) {
                val buffer = allpassBuffers[i]
                val stored = buffer[allpassIndex[i]].toDouble()
                val out = stored - sum
                buffer[allpassIndex[i]] = (sum + stored * ALLPASS_FEEDBACK).toFloat()
                allpassIndex[i] = (allpassIndex[i] + 1) % buffer.size
                sum = out
            }
            return sum
        }
    }

    private companion object {
        /** Largos de Freeverb a 44,1 kHz; se escalan a la frecuencia de la pista. */
        val COMBS = listOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASSES = listOf(556, 441, 341, 225)
        const val STEREO_SPREAD = 23
        const val ALLPASS_FEEDBACK = 0.5
        const val INPUT_GAIN = 0.015f
    }
}

/** Filtro de segundo orden (fórmulas de Robert Bristow-Johnson), forma directa transpuesta II. */
class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    /** Ganancia en [hz] (proporción de amplitudes). */
    fun magnitude(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz / sampleRate
        val (c1, s1, c2, s2) = listOf(cos(w), -sin(w), cos(2 * w), -sin(2 * w))
        val numRe = b0 + b1 * c1 + b2 * c2
        val numIm = b1 * s1 + b2 * s2
        val denRe = 1 + a1 * c1 + a2 * c2
        val denIm = a1 * s1 + a2 * s2
        return hypot(numRe, numIm) / hypot(denRe, denIm)
    }

    companion object {
        fun highPass(hz: Double, q: Double, sampleRate: Int): Biquad {
            val w = 2 * PI * hz / sampleRate
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad((1 + cos(w)) / 2 / a0, -(1 + cos(w)) / a0, (1 + cos(w)) / 2 / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
        }

        fun peak(hz: Double, q: Double, gainDb: Double, sampleRate: Int): Biquad {
            val w = 2 * PI * hz / sampleRate
            val a = 10.0.pow(gainDb / 40)
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha / a
            return Biquad((1 + alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha * a) / a0, -2 * cos(w) / a0, (1 - alpha / a) / a0)
        }
    }
}

/**
 * «Voz clara»: un paso alto de 150 Hz (12 dB por octava) quita el retumbe y el golpe del micrófono
 * del teléfono, y un realce suave de 3 dB en 3 kHz acerca la voz. Un tono de 80 Hz baja unos 11 dB;
 * uno de 1 kHz casi no cambia.
 */
@OptIn(UnstableApi::class)
class ClearVoice : BaseAudioProcessor() {
    private var filters: List<Pair<Biquad, Biquad>> = emptyList()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        if (inputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) return
        filters = List(inputAudioFormat.channelCount) { filtersFor(inputAudioFormat.sampleRate) }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        if (filters.size != format.channelCount) filters = List(format.channelCount) { filtersFor(format.sampleRate) }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(input.remaining())
        while (input.hasRemaining()) {
            for (c in 0 until format.channelCount) {
                val x = if (isFloat) input.float.toDouble() else input.short / 32768.0
                val (highPass, presence) = filters[c]
                val y = presence.process(highPass.process(x)).toFloat()
                if (isFloat) output.putFloat(y) else output.putShort((y * 32768f).coerceIn(-32768f, 32767f).toInt().toShort())
            }
        }
        output.flip()
    }

    override fun onReset() {
        filters = emptyList()
    }

    companion object {
        const val HIGH_PASS_HZ = 150.0
        const val PRESENCE_HZ = 3_000.0
        const val PRESENCE_DB = 3.0
        const val PRESENCE_Q = 0.9

        private fun filtersFor(sampleRate: Int) =
            Biquad.highPass(HIGH_PASS_HZ, Q_BUTTERWORTH, sampleRate) to Biquad.peak(PRESENCE_HZ, PRESENCE_Q, PRESENCE_DB, sampleRate)

        /** Lo que debería cambiar un seno de [hz] (proporción de amplitudes). */
        fun responseAt(hz: Double, sampleRate: Int): Double {
            val (highPass, presence) = filtersFor(sampleRate)
            return highPass.magnitude(hz, sampleRate) * presence.magnitude(hz, sampleRate)
        }

        private const val Q_BUTTERWORTH = 0.7071067811865476
    }
}
