package dev.tacuchi.hope.waves

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import dev.tacuchi.hope.domain.media.WaveformSource
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.Waveform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Ondas decodificadas del audio original y guardadas en la caché privada ([cacheDir]). La clave
 * junta el asset, la revisión de su archivo (tamaño y fecha) y la versión del cálculo: un archivo
 * cambiado o un cálculo nuevo no reusan una onda vieja. Cada onda se escribe en un temporal y
 * recién entera se renombra, así nunca se lee una a medias. Si el sistema vació la caché, se
 * vuelve a calcular.
 */
class DecodedWaveforms(private val cacheDir: File) : WaveformSource {

    override suspend fun waveform(asset: Asset): Waveform = withContext(Dispatchers.IO) {
        val cached = cacheFile(asset)
        read(cached)?.let { return@withContext it }
        val wave = decode(asset.file, asset.durationMs)
        write(wave, cached)
        wave
    }

    internal fun cacheFile(asset: Asset): File {
        val file = asset.file
        return File(cacheDir, "${asset.id}-${file.length()}-${file.lastModified()}-v$VERSION.wave")
    }

    private fun read(file: File): Waveform? = try {
        DataInputStream(file.inputStream().buffered()).use { input ->
            val duration = input.readLong()
            val count = input.readInt()
            Waveform(FloatArray(count) { input.readFloat() }, duration)
        }
    } catch (_: IOException) {
        null
    }

    private fun write(wave: Waveform, target: File) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.${System.nanoTime()}.tmp")
        try {
            DataOutputStream(temp.outputStream().buffered()).use { out ->
                out.writeLong(wave.durationMs)
                out.writeInt(wave.peaks.size)
                wave.peaks.forEach(out::writeFloat)
            }
            if (!temp.renameTo(target)) temp.delete()
        } catch (_: IOException) {
            // Sin caché la onda igual se muestra; la próxima vez se calcula de nuevo.
            temp.delete()
        }
    }

    /** Decodifica a PCM por bloques y guarda el pico de cada intervalo del archivo. */
    private suspend fun decode(file: File, knownDurationMs: Long?): Waveform {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("Sin pista de audio")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = knownDurationMs?.takeIf { it > 0 }?.times(1_000)
                ?: format.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }?.getLong(MediaFormat.KEY_DURATION)
                ?: throw IOException("Sin duración")
            val peaks = FloatArray(Waveform.BINS)
            val decoder = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var isFloat = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var frame = 0L
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = checkNotNull(decoder.getInputBuffer(index))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val changed = decoder.outputFormat
                        channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        sampleRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        isFloat = changed.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            changed.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    out >= 0 -> {
                        val buffer = checkNotNull(decoder.getOutputBuffer(out)).order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val bytes = if (isFloat) 4 else 2
                        val frames = info.size / (bytes * channels)
                        repeat(frames) {
                            var peak = 0f
                            repeat(channels) {
                                val value = if (isFloat) buffer.float else buffer.short / 32_768f
                                peak = max(peak, abs(value))
                            }
                            // La posición sale de las muestras contadas: no depende de las marcas de cada bloque.
                            val us = frame * 1_000_000 / sampleRate
                            val bin = (us * Waveform.BINS / durationUs).toInt()
                            if (bin in peaks.indices && peak > peaks[bin]) peaks[bin] = peak
                            frame++
                        }
                        decoder.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            return Waveform(peaks, durationUs / 1_000)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
        }
    }

    private companion object {
        /** Sube si cambia el cálculo: las ondas viejas dejan de servir. */
        const val VERSION = 1
        const val TIMEOUT_US = 10_000L
    }
}
