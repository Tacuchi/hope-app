package dev.tacuchi.hope.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {

    @Test
    fun roundsToTheNearestSecond() {
        // La duración que informa el extractor de un video de 60 s llega con unos milisegundos menos.
        assertEquals("1:00", formatDuration(59_977))
        assertEquals("0:04", formatDuration(4_499))
        assertEquals("0:05", formatDuration(4_500))
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:02:05", formatDuration(3_725_000))
    }
}
