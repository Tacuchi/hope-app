package dev.tacuchi.hope.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Deja pasar el audio tal cual y guarda una copia en mono, con el instante en que cae cada
 * muestra. Cada salto abre un tramo nuevo que empieza donde Media3 dice que sigue el audio, así
 * se mide también lo que sale después de saltar. Al final de la mezcla el instante es el de la
 * hope; al final de una pista, el de esa pista desde su primer instante audible.
 */
@OptIn(UnstableApi::class)
class MixCapture : BaseAudioProcessor() {
    private val lock = Any()
    private val takes = mutableListOf<TakeBuilder>()
    private var current: TakeBuilder? = null
    private var pendingStartUs = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    /** Media3 también avisa saltos antes de saber el formato: el tramo se abre con la primera muestra. */
    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        synchronized(lock) {
            current = null
            pendingStartUs = streamMetadata.positionOffsetUs
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val format = inputAudioFormat
        val channels = format.channelCount
        val isFloat = format.encoding == C.ENCODING_PCM_FLOAT
        val input = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val mono = FloatArray(input.remaining() / format.bytesPerFrame)
        for (i in mono.indices) {
            var sum = 0f
            repeat(channels) { sum += if (isFloat) input.float else input.short / 32768f }
            mono[i] = sum / channels
        }
        synchronized(lock) {
            val take = current ?: TakeBuilder(pendingStartUs, format.sampleRate).also {
                takes += it
                current = it
            }
            take.append(mono)
        }
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    override fun onReset() {
        synchronized(lock) {
            current = null
            pendingStartUs = 0
        }
    }

    /** Los tramos capturados con audio, en el orden en que sonaron. */
    fun takes(): List<Take> = synchronized(lock) { takes.filter { it.size > 0 }.map { it.build() } }

    /** El tramo que empieza en [startMs] (con 50 ms de margen), por ejemplo el que sigue a un salto. */
    fun takeFrom(startMs: Long): Take = takes().last { kotlin.math.abs(it.startMs - startMs) <= 50 }

    /** El primer tramo: lo que sonó desde que empezó, sin saltos. */
    fun first(): Take = takes().first()

    private class TakeBuilder(val startUs: Long, val sampleRate: Int) {
        private var samples = FloatArray(sampleRate * 2)
        var size = 0
            private set

        fun append(values: FloatArray) {
            if (size + values.size > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, size + values.size))
            values.copyInto(samples, size)
            size += values.size
        }

        fun build() = Take(startUs, sampleRate, samples.copyOf(size))
    }
}
