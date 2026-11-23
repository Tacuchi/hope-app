package dev.tacuchi.hope.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import dev.tacuchi.hope.domain.model.RoomType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Efectos de audio de una sola pista. Media3 los aplica a cada ítem de la composición, antes de
// mezclar: así la sala, la velocidad o la voz clara de una pista no tocan la otra.

/** Una velocidad fija para toda la pista. */
@OptIn(UnstableApi::class)
internal class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed

    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * Sala de una sola pista, con la estructura de Freeverb (dominio público): ocho filtros peine en
 * paralelo con un paso bajo en su realimentación y cuatro pasa todo en serie, por canal. Cada peine
 * tiene su propia realimentación para que todos caigan 60 dB en [RoomType.t60Seconds]. La señal
 * seca pasa entera; [amount] (de 0 a 1) solo cambia cuánto se suma de la cola.
 *
 * La salida tiene tantas muestras como la entrada: la pista dura lo mismo y la cola se corta donde
 * termina la pista (un fundido de salida la apaga antes, porque va después).
 */
@OptIn(UnstableApi::class)
internal class RoomReverb(private val kind: RoomType, amount: Float) : BaseAudioProcessor() {
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

    private class ReverbBank(kind: RoomType, rate: Int, spread: Int) {
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
internal class Biquad(private val b0: Double, private val b1: Double, private val b2: Double, private val a1: Double, private val a2: Double) {
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
internal class ClearVoice : BaseAudioProcessor() {
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
