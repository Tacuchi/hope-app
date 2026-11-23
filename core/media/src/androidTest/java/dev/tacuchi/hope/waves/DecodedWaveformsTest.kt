package dev.tacuchi.hope.waves

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.Waveform
import dev.tacuchi.hope.importing.testMedia
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Ondas de archivos reales: los picos caen donde suena algo y la caché se reusa o se rehace. */
@RunWith(AndroidJUnit4::class)
class DecodedWaveformsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "waves-test")
    private val cache = File(dir, "cache")
    private val source = DecodedWaveforms(cache)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun asset(id: Long, name: String, durationMs: Long) = Asset(
        id = id, type = AssetType.Audio, nameKind = AssetNameKind.Audio, number = 1, frameMs = null,
        origin = AssetOrigin.Files, createdAt = 0, durationMs = durationMs, file = testMedia(name, dir), mimeType = "audio/mp4",
    )

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }

    /** Nueve pulsos de 50 ms, uno cada 500 ms desde los 0,5 s, y silencio entre ellos. */
    @Test
    fun peaksFallWhereThePulsesAre() = runBlocking {
        val pulses = asset(1, "pulsos.m4a", 5_000)
        val before = pulses.file.sha256()

        val wave = source.waveform(pulses)

        assertEquals(Waveform.BINS, wave.peaks.size)
        assertEquals(5_000L, wave.durationMs)
        // El relleno inicial del AAC corre todo unos 20 ms: se mide en el medio de cada pulso.
        for (i in 0 until 9) {
            val onset = 500L + i * 500
            assertTrue("pulso a los $onset ms", wave.peakAt(onset + 30) > 0.08f)
            assertTrue("silencio a los ${onset + 250} ms", wave.peakAt(onset + 250) < 0.01f)
        }
        assertTrue(wave.peakAt(200) < 0.01f)
        // Calcular la onda no toca el audio original.
        assertEquals(before, pulses.file.sha256())
    }

    /** Dos audios distintos dan ondas distintas, con los picos en su lugar. */
    @Test
    fun eachAudioHasItsOwnWave() = runBlocking {
        val single = source.waveform(asset(2, "pulso.m4a", 4_000))
        assertTrue(single.peakAt(515) > 0.05f)
        assertTrue(single.peakAt(2_000) < 0.01f)
        assertFalse(single == source.waveform(asset(1, "pulsos.m4a", 5_000)))
    }

    @Test
    fun theWaveIsCachedAndRecomputedAfterTheCacheIsCleared() = runBlocking {
        val pulses = asset(1, "pulsos.m4a", 5_000)
        val first = source.waveform(pulses)
        val cached = source.cacheFile(pulses)
        assertTrue(cached.exists())
        assertTrue(cache.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

        // Reabrir la toma de la caché: una onda alterada en el archivo prueba que no se decodificó de nuevo.
        val stamp = cached.lastModified()
        assertEquals(first, source.waveform(pulses))
        assertEquals(stamp, cached.lastModified())

        cache.deleteRecursively()
        assertEquals(first, source.waveform(pulses))
        assertTrue(cached.exists())
    }
}
