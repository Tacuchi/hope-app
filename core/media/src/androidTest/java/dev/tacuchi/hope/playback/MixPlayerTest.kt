package dev.tacuchi.hope.playback

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.HopeWithAssets
import dev.tacuchi.hope.domain.model.MixSpec
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.domain.model.libraryKey
import dev.tacuchi.hope.domain.model.toMixSpec
import dev.tacuchi.hope.importing.testMedia
import dev.tacuchi.hope.playback.Tolerance.TONE
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

    /**
     * Referencia de la mezcla actual, para compararla con una hope migrada: fondo al 30 % recortado
     * al tramo de 330 Hz (de 3 a 7 s del archivo) y la voz entera, que entra a los 1,5 s. Cada
     * archivo cambia de tono a cada lado de los cortes, así se ve qué parte suena y en qué instante.
     */
    @Test
    fun theReferenceMixKeepsItsTrimOffsetAndLevels() {
        val capture = MixCapture()
        val player = onMain { MixPlayer(context, capture) }.also { players += it }
        val spec = MixSpec(
            background = testMedia("fondo-tramos.m4a", dir), backgroundLengthMs = 10_000, backgroundVolume = 30,
            trimStartMs = 3_000, trimEndMs = 7_000,
            voice = testMedia("voz-tramos.m4a", dir), voiceLengthMs = 6_000, voiceVolume = 100, voiceOffsetMs = 1_500,
        )
        assertEquals(7_500L, spec.durationMs)
        onMain { player.play("referencia", spec) }
        playUntilDone(player, maxMs = 12_000)
        val mix = capture.first()

        // Fondo solo, antes de la voz: nivel, estabilidad en ventanas de 20 ms y que no se cuele otro tramo.
        val plateau = (300..1_300 step 10).map { mix.toneAt(330.0, it.toDouble()) / TONE }
        val background = plateau.average()
        val jitter = kotlin.math.sqrt(plateau.sumOf { (it - background) * (it - background) } / plateau.size) / background
        val bgLevel = mix.tone(330.0, 200.0, 3_800.0)
        val leak = maxOf(mix.tone(220.0, 200.0, 3_800.0), mix.tone(440.0, 200.0, 3_800.0)) / bgLevel
        val bgEnd = mix.toneEnd(330.0, bgLevel / 2, 3_000.0, 5_000.0)!!
        // El tramo anterior al corte (220 Hz) que se cuela al empezar, por el relleno del AAC.
        val bgEdge = mix.toneEnd(220.0, bgLevel / 2, 0.0, 100.0) ?: 0.0
        // La voz: entra con su primer tramo, cambia de tono donde debe y suena hasta el final.
        val voiceStart = mix.toneOnset(660.0, TONE / 2, 500.0, 2_500.0)!!
        val voiceSecond = mix.toneOnset(880.0, TONE / 2, 3_000.0, 4_000.0)!!
        val voiceThird = mix.toneOnset(990.0, TONE / 2, 6_000.0, 7_000.0)!!
        val voiceEnd = mix.toneEnd(990.0, TONE / 2, 7_000.0, 8_000.0)!!
        val voiceLevels = listOf(
            mix.tone(660.0, 1_700.0, 3_300.0), mix.tone(880.0, 3_700.0, 6_300.0), mix.tone(990.0, 6_700.0, 7_400.0),
        ).map { it / TONE }
        Log.i(
            TAG,
            "Referencia: fondo al 30 % mide ${"%.4f".format(background)} (desvío en 20 ms ${"%.2f".format(jitter * 100)} %); " +
                "tramos vecinos ${"%.1f".format(leak.db())} dB; el tramo anterior al corte suena hasta los ${"%.0f".format(bgEdge)} ms; " +
                "el fondo termina a los ${"%.0f".format(bgEnd)} ms; " +
                "la voz entra a los ${"%.0f".format(voiceStart)} ms, cambia a los ${"%.0f".format(voiceSecond)} y " +
                "${"%.0f".format(voiceThird)} ms y suena hasta los ${"%.0f".format(voiceEnd)} ms " +
                "(fin de la captura ${"%.0f".format(mix.endMs)} ms); niveles de la voz ${voiceLevels.joinToString { "%.4f".format(it) }}",
        )

        assertEquals(0.30, background, 0.03)
        assertTrue("Se coló otro tramo del fondo: ${leak.db()} dB", leak.db() < -40)
        assertEquals(4_000.0, bgEnd, 100.0)
        assertEquals(1_500.0, voiceStart, 100.0)
        assertEquals(3_500.0, voiceSecond, 100.0)
        assertEquals(6_500.0, voiceThird, 100.0)
        assertEquals(7_500.0, mix.endMs, 50.0)
        assertTrue("La voz se cortó a los $voiceEnd ms", voiceEnd >= mix.endMs - 20)
        voiceLevels.forEach { assertEquals(1.0, it, 0.05) }
    }

    /**
     * Referencia del volumen en vivo de la mezcla actual: el fondo de 10 s baja de 100 % a 50 % a los
     * 2 s de reproducción. Mide cuánto tarda en oírse, porque la mezcla procesa el audio por delante
     * de lo que suena.
     */
    @Test
    fun theLiveVolumeReference() {
        val capture = MixCapture()
        val player = onMain { MixPlayer(context, capture) }.also { players += it }
        val spec = MixSpec(background = background, backgroundLengthMs = 10_000)
        onMain { player.play("volumen", spec) }
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline && onMain { player.state.value.positionMs } < 2_000) Thread.sleep(10)
        val changedAt = onMain { player.state.value.positionMs }
        onMain { player.update("volumen", spec.copy(backgroundVolume = 50)) }
        while (System.currentTimeMillis() < deadline + 4_000 && onMain { player.state.value.positionMs } < 6_000) Thread.sleep(10)
        onMain { player.pause() }
        val mix = capture.first()
        val heardAt = generateSequence(changedAt.toDouble()) { it + 1 }.first { mix.toneAt(220.0, it) / TONE < 0.75 }
        val after = mix.tone(220.0, heardAt + 200, minOf(mix.endMs, 6_000.0)) / TONE
        Log.i(TAG, "Volumen en vivo: cambiado con la reproducción en $changedAt ms, se oye desde los ${"%.0f".format(heardAt)} ms (demora ${"%.0f".format(heardAt - changedAt)} ms); después mide ${"%.4f".format(after)}")

        assertEquals(0.5, after, 0.03)
    }

    /**
     * La misma hope con todos sus efectos suena igual en el editor y en la biblioteca: el editor
     * arma su mezcla con los ajustes del borrador y la biblioteca con los de la hope guardada
     * ([toMixSpec]). Se comparan las dos salidas en ventanas de 20 ms y la huella de los archivos
     * originales antes y después.
     */
    @Test
    fun theEditorAndTheLibraryPlayTheSameHopeWithAllItsEffects() {
        val backgroundFile = testMedia("fondo-tramos.m4a", dir)
        val voiceFile = testMedia("voz-tramos.m4a", dir)
        val before = listOf(backgroundFile, voiceFile).map { it.sha256() }
        fun audio(id: Long, file: File, lengthMs: Long) = Asset(
            id = id, type = AssetType.Audio, nameKind = AssetNameKind.Audio, number = id.toInt(), frameMs = null,
            origin = AssetOrigin.Files, createdAt = 0, durationMs = lengthMs, file = file, mimeType = "audio/mp4",
        )
        val hope = Hope(
            id = 1, title = "Todo", coverAssetId = null, backgroundAssetId = 1, voiceAssetId = 2,
            backgroundVolume = 60, voiceVolume = 90, trimStartMs = 3_000, trimEndMs = 7_000, voiceOffsetMs = 1_000, createdAt = 0,
            voiceTrimStartMs = 2_000, voiceTrimEndMs = 5_000,
            backgroundEffects = BackgroundEffects(Fades(500, 800), RoomEffect(RoomType.Hall, 30), SpeedLevel.Slow),
            voiceEffects = VoiceEffects(Fades(300, 500), RoomEffect(RoomType.Room, 20), clearVoice = true),
            hopeEffects = HopeEffects(Fades(400, 600), DuckLevel.Medium),
        )
        val library = HopeWithAssets(hope, null, audio(1, backgroundFile, 10_000), audio(2, voiceFile, 6_000)).toMixSpec()
        // Lo que arma el editor con el mismo borrador.
        val editor = MixSpec(
            background = backgroundFile, backgroundLengthMs = 10_000, backgroundVolume = 60, trimStartMs = 3_000, trimEndMs = 7_000,
            voice = voiceFile, voiceLengthMs = 6_000, voiceVolume = 90, voiceOffsetMs = 1_000, voiceTrimStartMs = 2_000, voiceTrimEndMs = 5_000,
            backgroundEffects = hope.backgroundEffects, voiceEffects = hope.voiceEffects, hopeEffects = hope.hopeEffects,
            presentation = library.presentation,
        )
        assertEquals(library, editor)

        fun play(key: String, spec: MixSpec): Take {
            val capture = MixCapture()
            val player = onMain { MixPlayer(context, capture) }.also { players += it }
            onMain { player.play(key, spec) }
            playUntilDone(player, maxMs = 12_000)
            return capture.first()
        }
        val fromEditor = play("editor", editor)
        val fromLibrary = play(libraryKey(1), library)

        // El fondo lento dura 4,44 s; la voz recortada entra a 1 s y termina a 4 s.
        assertEquals(4_444L, library.durationMs)
        val points = (50..4_400 step 50).map { it.toDouble() }
        val worst = points.maxOf { abs(fromEditor.rms(it - 10, it + 10) - fromLibrary.rms(it - 10, it + 10)) }
        Log.i(TAG, "Editor contra biblioteca con todos los efectos: diferencia máxima de nivel ${"%.4f".format(worst)} en ${points.size} ventanas de 20 ms")
        assertTrue("Editor y biblioteca difieren en $worst", worst <= 0.03)
        assertEquals(before, listOf(backgroundFile, voiceFile).map { it.sha256() })
    }

    private fun File.sha256(): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "HopeMixTest"
    }
}
