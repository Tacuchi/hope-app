package dev.tacuchi.hope.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTest {

    @Test
    fun theBackgroundPlaysWhileRecordingOnlyWithHeadphonesAndABackground() {
        assertTrue(backgroundPlaysWhileRecording(headphones = true, hasBackground = true))
        assertFalse(backgroundPlaysWhileRecording(headphones = false, hasBackground = true))
        assertFalse(backgroundPlaysWhileRecording(headphones = true, hasBackground = false))
    }
}
