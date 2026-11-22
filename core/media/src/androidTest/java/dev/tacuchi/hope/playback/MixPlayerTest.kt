package dev.tacuchi.hope.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.importing.testMedia
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Deja pasar la mezcla tal cual y anota su nivel (RMS) cada 10 ms, con el momento de la hope en
 * que cae. Así se mide lo que de verdad sale, no lo que dicen los reproductores.
 */
@OptIn(UnstableApi::class)
private class LevelMeter : BaseAudioProcessor() {
    /** (milisegundo de la hope, nivel de 0 a 1) */
    private val levels = mutableListOf<Pair<Long, Double>>()
    private var frames = 0L
    private var sum = 0.0
    private var count = 0
    private var windowStartFrame = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val format = inputAudioFormat
        val channels = format.channelCount
        val window = format.sampleRate / 100
        val input = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val bytesPerSample = if (format.encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        while (input.remaining() >= bytesPerSample * channels) {
            repeat(channels) {
                val sample = if (bytesPerSample == 4) input.float.toDouble() else input.short / 32768.0
                sum += sample * sample
                count++
            }
            frames++
            if (frames - windowStartFrame >= window) {
                synchronized(levels) { levels += windowStartFrame * 1000 / format.sampleRate to sqrt(sum / count) }
                windowStartFrame = frames
                sum = 0.0
                count = 0
            }
        }
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer).flip()
    }

    override fun onReset() {
        frames = 0
        sum = 0.0
        count = 0
        windowStartFrame = 0
    }

    fun snapshot(): List<Pair<Long, Double>> = synchronized(levels) { levels.toList() }
}

@RunWith(AndroidJUnit4::class)
class MixPlayerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "mix-test")
    private val players = mutableListOf<MixPlayer>()

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { players.forEach { it.release() } }
        dir.deleteRecursively()
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun newPlayer(meter: LevelMeter? = null): MixPlayer = onMain { MixPlayer(context, meter) }.also { players += it }

    /** Espera a que termine sola o a que pasen [maxMs]. */
    private fun playUntilDone(player: MixPlayer, maxMs: Long) {
        val deadline = System.currentTimeMillis() + maxMs
        Thread.sleep(300)
        while (System.currentTimeMillis() < deadline && onMain { player.state.value.isPlaying }) Thread.sleep(50)
    }

    private fun median(values: List<Double>) = values.sorted()[values.size / 2]

    private val background get() = testMedia("fondo-10s.m4a", dir)
    private val voice get() = testMedia("voz-6s.m4a", dir)

    @Test
    fun theVoiceStartsAtItsOffsetOverTheLoweredBackground() {
        // Fondo de 10 s al 30 %; voz de 6 s que entra a los 2 s.
        val mixMeter = LevelMeter()
        val mixPlayer = newPlayer(mixMeter)
        val spec = MixSpec(
            background = background, backgroundLengthMs = 10_000, backgroundVolume = 30,
            voice = voice, voiceLengthMs = 6_000, voiceVolume = 100, voiceOffsetMs = 2_000,
        )
        onMain { mixPlayer.play("prueba", spec) }
        playUntilDone(mixPlayer, maxMs = 4_000)
        onMain { mixPlayer.pause() }
        val mix = mixMeter.snapshot()

        // El mismo fondo al 100 %, solo, para saber cuánto bajó.
        val fullMeter = LevelMeter()
        val fullPlayer = newPlayer(fullMeter)
        onMain { fullPlayer.play("prueba", MixSpec(background = background, backgroundLengthMs = 10_000)) }
        playUntilDone(fullPlayer, maxMs = 2_000)
        onMain { fullPlayer.pause() }
        val full = fullMeter.snapshot()

        val lowered = median(mix.filter { it.first in 300L..1_800L }.map { it.second })
        val fullLevel = median(full.filter { it.first in 300L..1_800L }.map { it.second })
        val withVoice = median(mix.filter { it.first in 2_300L..3_500L }.map { it.second })
        // La voz entra cuando el nivel pasa la mitad del camino entre el fondo solo y fondo más voz.
        val threshold = (lowered + withVoice) / 2
        val voiceStart = mix.first { it.first > 500 && it.second > threshold }.first
        Log.i(
            TAG,
            "Fondo al 30 %: ${"%.4f".format(lowered)} contra ${"%.4f".format(fullLevel)} al 100 % " +
                "(proporción ${"%.3f".format(lowered / fullLevel)}); con voz ${"%.4f".format(withVoice)}; " +
                "la voz entra a los $voiceStart ms (desvío ${voiceStart - 2_000} ms)",
        )

        assertEquals(0.3, lowered / fullLevel, 0.03)
        assertTrue("La voz entró a los $voiceStart ms", abs(voiceStart - 2_000) <= 100)
    }

    @Test
    fun itEndsWithTheLastTrackAndGoesBackToTheStart() {
        val player = newPlayer()
        // Fondo recortado a 1 s y voz de 6 s que entra a los 0,5 s: termina a los 6,5 s.
        val spec = MixSpec(
            background = background, backgroundLengthMs = 10_000, trimStartMs = 2_000, trimEndMs = 3_000,
            voice = voice, voiceLengthMs = 6_000, voiceOffsetMs = 500,
        )
        assertEquals(6_500L, spec.durationMs)
        val started = System.currentTimeMillis()
        onMain { player.play("prueba", spec) }
        playUntilDone(player, maxMs = 10_000)
        val elapsed = System.currentTimeMillis() - started

        Log.i(TAG, "Sonó $elapsed ms para una hope de 6500 ms")
        assertFalse(onMain { player.state.value.isPlaying })
        assertEquals(0L, onMain { player.state.value.positionMs })
        assertTrue("Sonó $elapsed ms", elapsed in 6_000L..8_000L)
    }

    @Test
    fun pausingKeepsThePositionAndPlayingAgainResumes() {
        val spec = MixSpec(background = background, backgroundLengthMs = 10_000)
        val player = newPlayer()
        onMain { player.play("prueba", spec) }
        Thread.sleep(1_500)

        onMain { player.pause() }
        val paused = onMain { player.state.value }
        assertFalse(paused.isPlaying)
        assertTrue("Pausó en ${paused.positionMs} ms", paused.positionMs in 700L..1_600L)

        onMain { player.toggle("prueba", spec) }
        Thread.sleep(500)
        val resumed = onMain { player.state.value }
        assertTrue(resumed.isPlaying)
        assertTrue("Siguió en ${resumed.positionMs} ms", resumed.positionMs > paused.positionMs)
    }

    private companion object {
        const val TAG = "HopeMixTest"
    }
}
