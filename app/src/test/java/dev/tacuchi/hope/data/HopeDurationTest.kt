package dev.tacuchi.hope.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HopeDurationTest {

    @Test
    fun runsFromTheTrimStartToTheEndOfTheLastTrack() {
        // Fondo de 52 s recortado de 0:04 a 0:48 (44 s); voz de 10 s que entra a los 2 s.
        assertEquals(44_000L, hopeDurationMs(52_000, 4_000, 48_000, 10_000, 2_000))
        // La voz termina después que el fondo recortado.
        assertEquals(50_000L, hopeDurationMs(52_000, 4_000, 48_000, 41_000, 9_000))
    }

    @Test
    fun aMissingTrackDoesNotCount() {
        assertEquals(52_000L, hopeDurationMs(52_000, 0, null, null, 2_000))
        assertEquals(12_000L, hopeDurationMs(null, 0, null, 10_000, 2_000))
        assertEquals(0L, hopeDurationMs(null, 0, null, null, 0))
    }
}
