package dev.tacuchi.hope.importing

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AudioExtractorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "extractor-test")
    private val extractor = TransformerAudioExtractor(context)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** Pistas del archivo producido, por tipo. */
    private fun trackMimes(file: File): List<String> {
        val media = MediaExtractor()
        try {
            media.setDataSource(file.path)
            return (0 until media.trackCount).map { media.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!! }
        } finally {
            media.release()
        }
    }

    @Test
    fun mp4WithAacBecomesAnAacM4aOfTheSameLength() = runBlocking {
        val output = File(dir, "audio.m4a")
        val durationMs = extractor.extract(testMedia("video-aac.mp4", dir), output)

        assertEquals(5_000.0, durationMs.toDouble(), 300.0)
        assertEquals(listOf(MediaFormat.MIMETYPE_AUDIO_AAC), trackMimes(output))
    }

    @Test
    fun webmWithOpusIsAlsoExtractedToAac() = runBlocking {
        val output = File(dir, "audio.m4a")
        val durationMs = extractor.extract(testMedia("video-vp9-opus.webm", dir), output)

        assertEquals(3_000.0, durationMs.toDouble(), 300.0)
        assertEquals(listOf(MediaFormat.MIMETYPE_AUDIO_AAC), trackMimes(output))
    }

    @Test
    fun cancellingLeavesNoPartialFile() = runBlocking {
        val session = File(dir, "session")
        val input = testMedia("video-vp9-opus.webm", File(dir, "input"))
        val output = File(session.apply { mkdirs() }, "audio.m4a")
        val started = CompletableDeferred<Unit>()

        val extraction = async {
            extractor.extract(input, output) { started.complete(Unit) }
        }
        withTimeout(10_000) { started.await() }
        extraction.cancel()
        val cancelled = runCatching { extraction.await() }.exceptionOrNull()

        assertTrue(cancelled is CancellationException)
        assertFalse(output.exists())
        assertEquals(0, session.listFiles()!!.size)
    }
}
