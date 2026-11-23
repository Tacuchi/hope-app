package dev.tacuchi.hope.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MixSpecTest {

    private fun asset(id: Long, kind: AssetNameKind, durationMs: Long) = Asset(
        id = id, type = kind.type, nameKind = kind, number = 1, frameMs = null, origin = AssetOrigin.TikTok,
        createdAt = 0, durationMs = durationMs, file = File("/assets/$id.m4a"), mimeType = "audio/mp4",
    )

    /** La biblioteca suena con los ajustes guardados, tal como los dejó el editor. */
    @Test
    fun aSavedHopePlaysWithItsSettings() {
        val background = asset(1, AssetNameKind.AudioFromVideo, 52_000)
        val voice = asset(2, AssetNameKind.Recording, 41_000)
        val hope = Hope(
            id = 9, title = "Antes de entrar", coverAssetId = null, backgroundAssetId = 1, voiceAssetId = 2,
            backgroundVolume = 30, voiceVolume = 100, trimStartMs = 4_000, trimEndMs = 48_000, voiceOffsetMs = 2_000,
            createdAt = 0,
        )

        val spec = HopeWithAssets(hope, null, background, voice).toMixSpec()

        assertEquals(File("/assets/1.m4a"), spec.background)
        assertEquals(30, spec.backgroundVolume)
        assertEquals(4_000L, spec.trimStartMs)
        assertEquals(48_000L, spec.trimEndMs)
        assertEquals(File("/assets/2.m4a"), spec.voice)
        assertEquals(2_000L, spec.voiceOffsetMs)
        assertEquals(44_000L, spec.backgroundSpanMs)
        assertEquals(44_000L, spec.durationMs)
    }

    @Test
    fun onlyAVolumeChangeKeepsTheTimeline() {
        val spec = MixSpec(File("a"), 10_000, 100, 0, null, File("b"), 3_000, 100, 2_000)

        assertTrue(spec.sameTimeline(spec.copy(backgroundVolume = 30, voiceVolume = 50)))
        assertFalse(spec.sameTimeline(spec.copy(voiceOffsetMs = 3_000)))
        assertFalse(spec.sameTimeline(spec.copy(trimStartMs = 1_000)))
        assertFalse(spec.sameTimeline(spec.copy(voice = null)))
    }

    @Test
    fun aRampOfZeroKeepsUnityGainAndRampsStayInsideTheirTrack() {
        assertTrue(GainShape(lengthMs = 4_000).isUnity)
        assertEquals(1.0, GainShape(4_000).at(0.0), 0.0)

        // Un fundido más largo que la pista se acota a la pista.
        val shape = GainShape(lengthMs = 4_000, fades = Fades(inMs = 10_000))
        assertEquals(0.0, shape.at(0.0), 1e-9)
        assertEquals(0.5, shape.at(2_000.0), 1e-9)
        assertEquals(1.0, shape.at(4_000.0), 1e-9)
    }

    /** El fundido de la hope cuenta en tiempo de la hope y se multiplica con el de la pista y su bajada. */
    @Test
    fun overlappingGainsMultiply() {
        val spec = MixSpec(
            background = File("a"), backgroundLengthMs = 10_000, voice = File("b"), voiceLengthMs = 4_000, voiceOffsetMs = 2_000,
            backgroundEffects = BackgroundEffects(fades = Fades(inMs = 2_000)),
            voiceEffects = VoiceEffects(fades = Fades(outMs = 1_000)),
            hopeEffects = HopeEffects(fades = Fades(inMs = 4_000, outMs = 2_000), duck = DuckLevel.Medium),
        )
        val background = spec.backgroundShape()
        // A 1 s: fundido del fondo 0,5 por fundido de la hope 0,25.
        assertEquals(0.125, background.at(1_000.0), 1e-9)
        // A 3 s: la voz suena, el fondo está en su bajada media (−12 dB) y la hope en 0,75.
        assertEquals(0.75 * DuckLevel.Medium.level, background.at(3_000.0), 1e-9)
        // A 9 s: fundido de salida de la hope a la mitad.
        assertEquals(0.5, background.at(9_000.0), 1e-9)

        val voice = spec.voiceShape()
        // A 1 s de la voz (3 s de la hope): solo el fundido de la hope.
        assertEquals(0.75, voice.at(1_000.0), 1e-9)
        // A 3,5 s de la voz (5,5 s de la hope): su fundido de salida a la mitad.
        assertEquals(0.5, voice.at(3_500.0), 1e-9)
        // La ganancia del fondo deja de valer 1 donde empieza la bajada y el fundido de salida de la hope.
        assertEquals(listOf(1_800L, 8_000L), background.unityEndsMs)
    }

    @Test
    fun theBackgroundOnlyDucksWhenThereIsAVoice() {
        val spec = MixSpec(background = File("a"), backgroundLengthMs = 10_000, hopeEffects = HopeEffects(duck = DuckLevel.Much))
        assertFalse(spec.ducks)
        assertTrue(spec.backgroundShape().isUnity)
    }

    @Test
    fun aSlowerBackgroundLastsLongerAndAnEffectChangesTheTimeline() {
        val spec = MixSpec(background = File("a"), backgroundLengthMs = 72_000, trimStartMs = 8_000, trimEndMs = 52_000)
        val slow = spec.copy(backgroundEffects = BackgroundEffects(speed = SpeedLevel.Slow))
        assertEquals(44_000L, slow.backgroundSpanMs)
        assertEquals(48_889L, slow.backgroundInHopeMs)
        assertEquals(48_889L, slow.durationMs)
        assertFalse(spec.sameTimeline(slow))
    }
}
