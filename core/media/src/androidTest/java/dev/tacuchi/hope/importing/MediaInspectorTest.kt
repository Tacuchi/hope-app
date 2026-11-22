package dev.tacuchi.hope.importing

import dev.tacuchi.hope.domain.model.ImportDecision
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Los formatos reales que llegan a Hope terminan en la decisión que corresponde. */
@RunWith(AndroidJUnit4::class)
class MediaInspectorTest {

    private val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "inspector-test")
    private val inspector = AndroidMediaInspector()

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun decide(name: String) = runBlocking { ImportDecision.from(inspector.inspect(testMedia(name, dir))) }

    @Test
    fun videoWithAacAudio() {
        val decision = decide("video-aac.mp4")
        assertTrue(decision is ImportDecision.VideoWithAudio)
        assertEquals(5_000.0, (decision as ImportDecision.VideoWithAudio).durationMs!!.toDouble(), 300.0)
    }

    @Test
    fun webmWithVp9AndOpus() {
        assertTrue(decide("video-vp9-opus.webm") is ImportDecision.VideoWithAudio)
    }

    @Test
    fun videoWithoutAudio() {
        assertTrue(decide("video-sin-audio.mp4") is ImportDecision.VideoWithoutAudio)
    }

    @Test
    fun audiosStayAudio() {
        listOf("nota-de-voz.ogg", "audio-opus.webm", "tono.mp3").forEach { name ->
            val decision = decide(name)
            assertTrue("$name -> $decision", decision is ImportDecision.Audio)
            assertEquals(name, 3_000.0, (decision as ImportDecision.Audio).durationMs!!.toDouble(), 300.0)
        }
    }

    @Test
    fun images() {
        assertEquals(ImportDecision.Image, decide("imagen.png"))
        assertEquals(ImportDecision.Image, decide("imagen.jpg"))
    }

    @Test
    fun aDocumentIsUnsupported() {
        assertEquals(ImportDecision.Unsupported, decide("documento.pdf"))
    }

    @Test
    fun anAudioAndroidCannotReadIsUnsupported() {
        assertEquals(ImportDecision.Unsupported, decide("audio.wma"))
    }
}
