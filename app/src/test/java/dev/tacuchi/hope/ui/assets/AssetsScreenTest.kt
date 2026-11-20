package dev.tacuchi.hope.ui.assets

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
import dev.tacuchi.hope.R
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.ui.theme.HopeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

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
        fileName = "a.m4a", mimeType = "audio/mp4",
    )
    private val frame = Asset(
        id = 1, type = AssetNameKind.Frame.type, nameKind = AssetNameKind.Frame, number = null,
        frameMs = 14_000, origin = AssetOrigin.WhatsApp, createdAt = today, durationMs = null,
        fileName = "f.jpg", mimeType = "image/jpeg",
    )

    private var deleted: Asset? = null
    private var toggled: Asset? = null

    private fun show(assets: List<Asset>) {
        compose.setContent {
            HopeTheme {
                AssetsContent(
                    state = AssetsUiState(assets = assets),
                    onBack = {},
                    onSelectFilter = {},
                    onTogglePlay = { toggled = it },
                    onDelete = { deleted = it },
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

        compose.onNodeWithText(text(R.string.asset_name_audio_from_video, 1)).assertExists()
        compose.onNodeWithText("Audio · WhatsApp · $date · 0:52").assertExists()
        compose.onNodeWithText(text(R.string.asset_name_frame, "0:14")).assertExists()
        compose.onNodeWithText("Imagen · WhatsApp · $date").assertExists()
    }

    @Test
    fun tappingAnAudioPlaysIt() {
        show(listOf(audio))
        compose.onNodeWithText(text(R.string.asset_name_audio_from_video, 1)).performClick()
        assertEquals(audio, toggled)
    }

    @Test
    fun deleteAsksForConfirmationFirst() {
        show(listOf(audio, frame))
        val name = text(R.string.asset_name_frame, "0:14")

        compose.onNodeWithContentDescription(text(R.string.assets_more, name)).performClick()
        compose.onNodeWithText(text(R.string.assets_use_new_hope)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.assets_use_existing_hope)).assertIsNotEnabled()
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
                    onImportVisual = { visual = true },
                    onImportAudio = { audioPicker = true },
                )
            }
        }
        compose.onNodeWithText(text(R.string.assets_empty_title)).assertExists()

        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(R.string.assets_import))), useUnmergedTree = true).performClick()
        compose.onNodeWithText(text(R.string.assets_import_visual)).performClick()
        assertTrue(visual)

        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(R.string.assets_import))), useUnmergedTree = true).performClick()
        compose.onNodeWithText(text(R.string.assets_import_audio)).performClick()
        assertTrue(audioPicker)
    }
}
