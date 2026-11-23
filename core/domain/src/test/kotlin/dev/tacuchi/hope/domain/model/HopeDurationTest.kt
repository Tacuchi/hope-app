package dev.tacuchi.hope.domain.model

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

    /** Escenario de la spec: un fondo recortado de 44 s más lento alarga la hope; el recorte no cambia. */
    @Test
    fun aSlowerBackgroundLengthensTheHope() {
        assertEquals(48_889L, hopeDurationMs(72_000, 8_000, 52_000, 20_000, 3_000, backgroundSpeed = 0.9f))
        assertEquals(55_000L, hopeDurationMs(72_000, 8_000, 52_000, 20_000, 3_000, backgroundSpeed = 0.8f))
    }

    @Test
    fun theVoiceCountsOnlyItsTrimmedPart() {
        // Voz de 41 s recortada de 1 a 27 s (26 s) que entra a los 3 s: termina a los 29 s.
        assertEquals(29_000L, hopeDurationMs(null, 0, null, 41_000, 3_000, voiceTrimStartMs = 1_000, voiceTrimEndMs = 27_000))
        // Cortes fuera del archivo se acotan a él.
        assertEquals(41_000L, hopeDurationMs(null, 0, null, 41_000, 0, voiceTrimStartMs = -5, voiceTrimEndMs = 90_000))
        assertEquals(0L, hopeDurationMs(null, 0, null, 41_000, 0, voiceTrimStartMs = 50_000))
    }
}
