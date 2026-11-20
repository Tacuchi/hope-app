package dev.tacuchi.hope.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VoiceRecorderTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** Escribe [bytes] al empezar; si [failOnStop], la toma queda vacía como un MediaRecorder cortado al instante. */
    private class FakeCapture(private val bytes: Int = 64, private val failOnStop: Boolean = false) : AudioCapture {
        var released = false
        override fun start(target: File) = target.writeBytes(ByteArray(bytes))
        override fun stop() {
            if (failOnStop) throw RuntimeException("stop failed")
        }
        override fun release() {
            released = true
        }
    }

    private val dir get() = File(temp.root, "recording-tmp")

    @Test
    fun finishingHandsOverTheTake() {
        val recorder = VoiceRecorder(FakeCapture(), dir)
        recorder.start()
        assertTrue(recorder.isRecording)

        val take = recorder.finish()!!

        assertFalse(recorder.isRecording)
        assertEquals(64L, take.length())
        assertEquals(listOf(take), dir.listFiles()!!.toList())
    }

    @Test
    fun discardingLeavesNoFiles() {
        val capture = FakeCapture()
        val recorder = VoiceRecorder(capture, dir)
        recorder.start()

        recorder.discard()

        assertTrue(capture.released)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun anEmptyTakeLeavesNoFiles() {
        assertNull(VoiceRecorder(FakeCapture(failOnStop = true), dir).apply { start() }.finish())
        assertTrue(dir.listFiles()!!.isEmpty())

        assertNull(VoiceRecorder(FakeCapture(bytes = 0), dir).apply { start() }.finish())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun leftoversFromACutTakeAreCleaned() {
        dir.mkdirs()
        File(dir, "vieja.m4a").writeText("resto")

        VoiceRecorder(FakeCapture(), dir).discardLeftovers()

        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
