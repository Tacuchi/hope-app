package dev.tacuchi.hope.ui.assets

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import dev.tacuchi.hope.domain.media.PreviewPlayback
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.ui.common.formatShortDate
import dev.tacuchi.hope.ui.theme.HopeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import dev.tacuchi.hope.ui.R as UiR

@RunWith(AndroidJUnit4::class)
class AssetsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val today = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val audio = Asset(
        id = 2, type = AssetNameKind.AudioFromVideo.type, nameKind = AssetNameKind.AudioFromVideo, number = 1,
        frameMs = null, origin = AssetOrigin.WhatsApp, createdAt = today, durationMs = 52_000,
        file = java.io.File("a.m4a"), mimeType = "audio/mp4",
    )
    private val frame = Asset(
        id = 1, type = AssetNameKind.Frame.type, nameKind = AssetNameKind.Frame, number = null,
        frameMs = 14_000, origin = AssetOrigin.WhatsApp, createdAt = today, durationMs = null,
        file = java.io.File("f.jpg"), mimeType = "image/jpeg",
    )

    private var deleted: Asset? = null
    private var toggled: Asset? = null

    private var usedInNew: Asset? = null
    private var placed: Pair<Asset, Long>? = null

    private fun show(
        assets: List<Asset>,
        uses: Map<Long, List<AssetUse>> = emptyMap(),
        hopes: List<HopeChoice> = emptyList(),
        preview: PreviewPlayback = PreviewPlayback(),
    ) {
        compose.setContent {
            HopeTheme {
                AssetsContent(
                    state = AssetsUiState(assets = assets, uses = uses, hopes = hopes, preview = preview),
                    onBack = {},
                    onSelectFilter = {},
                    onTogglePlay = { toggled = it },
                    onDelete = { deleted = it },
                    onUseInNewHope = { usedInNew = it },
                    onUseInHope = { asset, hopeId -> placed = asset to hopeId },
                    onImportVisual = {},
                    onImportAudio = {},
                )
            }
        }
    }

    @Test
    fun showsNameOriginDateAndDuration() {
        show(listOf(audio, frame))
        val date = formatShortDate(today)

        compose.onNodeWithText(text(UiR.string.asset_name_audio_from_video, 1)).assertExists()
        compose.onNodeWithText("Audio · WhatsApp · $date · 0:52").assertExists()
        compose.onNodeWithText(text(UiR.string.asset_name_frame, "0:14")).assertExists()
        compose.onNodeWithText("Imagen · WhatsApp · $date").assertExists()
    }

    @Test
    fun tappingAnAudioPlaysIt() {
        show(listOf(audio))
        compose.onNodeWithText(text(UiR.string.asset_name_audio_from_video, 1)).performClick()
        assertEquals(audio, toggled)
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        show(listOf(audio, frame))
        val name = text(UiR.string.asset_name_frame, "0:14")

        compose.onNodeWithContentDescription(text(R.string.assets_more, name)).performClick()
        compose.onNodeWithText(text(R.string.assets_delete)).performClick()

        assertEquals(null, deleted)
        compose.onNodeWithText(text(R.string.assets_delete_title, name)).assertExists()
        compose.onAllNodesWithText(text(R.string.assets_delete))[0].performClick()

        assertEquals(frame, deleted)
    }

    @Test
    fun emptyCatalogShowsTheEmptyStateAndImportOffersBothPickers() {
        var visual = false
        var audioPicker = false
        compose.setContent {
            HopeTheme {
                AssetsContent(
                    state = AssetsUiState(assets = emptyList()),
                    onBack = {},
                    onSelectFilter = {},
                    onTogglePlay = {},
                    onDelete = {},
                    onUseInNewHope = {},
                    onUseInHope = { _, _ -> },
                    onImportVisual = { visual = true },
                    onImportAudio = { audioPicker = true },
                )
            }
        }
        compose.onNodeWithText(text(R.string.assets_empty_title)).assertExists()

        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(UiR.string.assets_import))), useUnmergedTree = true).performClick()
        compose.onNodeWithText(text(R.string.assets_import_visual)).performClick()
        assertTrue(visual)

        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(UiR.string.assets_import))), useUnmergedTree = true).performClick()
        compose.onNodeWithText(text(R.string.assets_import_audio)).performClick()
        assertTrue(audioPicker)
    }

    @Test
    fun anAssetInUseShowsItsCountAndListsTheHopesInsteadOfDeleting() {
        val uses = listOf(AssetUse(audio.id, 2, "Antes de entrar"), AssetUse(audio.id, 1, "Respira"))
        show(listOf(audio, frame), uses = mapOf(audio.id to uses))
        val name = text(UiR.string.asset_name_audio_from_video, 1)

        compose.onNodeWithText("En uso en 2 hopes").assertExists()
        compose.onNodeWithContentDescription(text(R.string.assets_more, name)).performClick()
        compose.onNodeWithText(text(R.string.assets_delete)).performClick()

        compose.onNodeWithText(text(R.string.assets_in_use_title, name)).assertExists()
        compose.onNodeWithText(text(R.string.assets_in_use_item, "Antes de entrar")).assertExists()
        compose.onNodeWithText(text(R.string.assets_in_use_item, "Respira")).assertExists()
        compose.onNodeWithText(text(UiR.string.action_accept)).performClick()
        assertEquals(null, deleted)
    }

    @Test
    fun usesTheAssetInANewHopeOrInAnExistingOne() {
        show(listOf(audio), hopes = listOf(HopeChoice(7, "Respira")))
        val name = text(UiR.string.asset_name_audio_from_video, 1)

        compose.onNodeWithContentDescription(text(R.string.assets_more, name)).performClick()
        compose.onNodeWithText(text(R.string.assets_use_new_hope)).performClick()
        assertEquals(audio, usedInNew)

        compose.onNodeWithContentDescription(text(R.string.assets_more, name)).performClick()
        compose.onNodeWithText(text(R.string.assets_use_existing_hope)).performClick()
        compose.onNodeWithText("Respira").performClick()
        assertEquals(audio to 7L, placed)
    }

    @Test
    fun withoutSavedHopesUsingInAnExistingOneIsDisabled() {
        show(listOf(audio))
        compose.onNodeWithContentDescription(text(R.string.assets_more, text(UiR.string.asset_name_audio_from_video, 1))).performClick()
        compose.onNodeWithText(text(R.string.assets_use_existing_hope)).assertIsNotEnabled()
    }

    /** En reposo cada audio muestra su botón de reproducir; mientras suena, pausar y su avance frente a la duración. */
    @Test
    fun eachAudioShowsAPlayButtonAndItsProgressWhilePlaying() {
        show(listOf(audio, frame))
        val name = text(UiR.string.asset_name_audio_from_video, 1)
        compose.onNodeWithContentDescription(text(R.string.assets_play, name)).performClick()
        assertEquals(audio, toggled)
        compose.onAllNodesWithText("0:17 / 0:52").assertCountEquals(0)
    }

    @Test
    fun aPlayingAudioShowsPauseAndHowFarItWent() {
        show(listOf(audio, frame), preview = PreviewPlayback(audio.id, isPlaying = true, positionMs = 17_000, durationMs = 52_000))
        val name = text(UiR.string.asset_name_audio_from_video, 1)
        compose.onNodeWithContentDescription(text(R.string.assets_pause, name)).assertExists()
        compose.onNodeWithText("0:17 / 0:52").assertExists()
    }
}
