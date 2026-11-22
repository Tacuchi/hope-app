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
}
