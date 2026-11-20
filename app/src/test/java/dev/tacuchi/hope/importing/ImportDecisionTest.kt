package dev.tacuchi.hope.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportDecisionTest {

    private fun inspection(image: Boolean = false, video: Boolean = false, audio: Boolean = false, durationMs: Long? = null) =
        MediaInspection(isImage = image, hasVideo = video, hasAudio = audio, durationMs = durationMs)

    @Test
    fun decidesByWhatTheFileContains() {
        assertEquals(ImportDecision.Image, ImportDecision.from(inspection(image = true)))
        assertEquals(ImportDecision.Audio(3_000), ImportDecision.from(inspection(audio = true, durationMs = 3_000)))
        assertEquals(
            ImportDecision.VideoWithAudio(5_000),
            ImportDecision.from(inspection(video = true, audio = true, durationMs = 5_000)),
        )
        assertEquals(
            ImportDecision.VideoWithoutAudio(3_000),
            ImportDecision.from(inspection(video = true, durationMs = 3_000)),
        )
        assertEquals(ImportDecision.Unsupported, ImportDecision.from(inspection()))
    }

    @Test
    fun warnsOnlyAboveTenMinutes() {
        val tenMinutes = 10 * 60 * 1000L
        assertFalse(ImportDecision.VideoWithAudio(tenMinutes).isLong)
        assertTrue(ImportDecision.VideoWithAudio(tenMinutes + 1).isLong)
        assertTrue(ImportDecision.VideoWithoutAudio(tenMinutes + 1).isLong)
        assertTrue(ImportDecision.Audio(tenMinutes + 1).isLong)
        assertFalse(ImportDecision.Audio(null).isLong)
    }

    @Test
    fun namesTheReceivedFormatForTheUnsupportedMessage() {
        assertEquals("PDF (application/pdf)", receivedFormatLabel("documento.pdf", "application/pdf"))
        assertEquals("DOCX", receivedFormatLabel("informe.docx", null))
        assertEquals("application/zip", receivedFormatLabel("sin-extension", "application/zip"))
        assertEquals(null, receivedFormatLabel(null, null))
    }
}
