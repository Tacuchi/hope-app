package dev.tacuchi.hope.ui.library

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.R
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.Hope
import dev.tacuchi.hope.data.HopeWithAssets
import dev.tacuchi.hope.ui.theme.HopeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Pantalla de un teléfono común: la cuadrícula muestra dos columnas. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp")
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private val background = Asset(
        id = 1, type = AssetNameKind.AudioFromVideo.type, nameKind = AssetNameKind.AudioFromVideo, number = 1,
        frameMs = null, origin = AssetOrigin.TikTok, createdAt = 1, durationMs = 52_000,
        fileName = "a.m4a", mimeType = "audio/mp4",
    )

    private fun item(id: Long, title: String, trimEndMs: Long? = null, linkUrl: String? = null) = LibraryItem(
        HopeWithAssets(
            hope = Hope(
                id = id, title = title, coverAssetId = null, backgroundAssetId = background.id, voiceAssetId = null,
                backgroundVolume = 30, voiceVolume = 100, trimStartMs = 0, trimEndMs = trimEndMs, voiceOffsetMs = 0,
                createdAt = id, linkUrl = linkUrl,
            ),
            cover = null,
            background = background,
            voice = null,
        ),
        coverFile = null,
    )

    private var toggled: LibraryItem? = null
    private var edited: LibraryItem? = null
    private var deleted: LibraryItem? = null
    private var opened: LibraryItem? = null

    private fun show(state: LibraryUiState) {
        compose.setContent {
            HopeTheme {
                LibraryContent(
                    state = state,
                    onOpenSearch = {},
                    onOpenAssets = {},
                    onNewHope = {},
                    onTogglePlay = { toggled = it },
                    onEdit = { edited = it },
                    onDelete = { deleted = it },
                    onOpenOriginal = { opened = it },
                )
            }
        }
    }

    @Test
    fun showsEachHopeWithTitleAndDurationAndTheCount() {
        show(LibraryUiState(items = listOf(item(2, "Antes de entrar", trimEndMs = 48_000), item(1, "Respira"))))

        compose.onNodeWithText(text(R.string.library_your_hopes)).assertExists()
        compose.onNodeWithText("2 hopes").assertExists()
        compose.onNodeWithText("Antes de entrar").assertExists()
        compose.onNodeWithText("0:48").assertExists()
        compose.onNodeWithText("Respira").assertExists()
        compose.onNodeWithText("0:52").assertExists()
    }

    @Test
    fun tappingAHopePlaysIt() {
        val respira = item(1, "Respira")
        show(LibraryUiState(items = listOf(respira)))

        compose.onNodeWithText("Respira").performClick()

        assertEquals(respira, toggled)
    }

    @Test
    fun theMenuEditsAndDeletesOnlyAfterConfirming() {
        val respira = item(1, "Respira")
        show(LibraryUiState(items = listOf(respira)))

        compose.onNodeWithContentDescription(text(R.string.library_more, "Respira")).performClick()
        compose.onNodeWithText(text(R.string.library_edit)).performClick()
        assertEquals(respira, edited)

        compose.onNodeWithContentDescription(text(R.string.library_more, "Respira")).performClick()
        compose.onNodeWithText(text(R.string.library_delete)).performClick()
        assertEquals(null, deleted)
        compose.onNodeWithText(text(R.string.library_delete_title, "Respira")).assertExists()
        compose.onNodeWithText(text(R.string.library_delete_body)).assertExists()
        compose.onAllNodesWithText(text(R.string.library_delete))[0].performClick()

        assertEquals(respira, deleted)
    }

    @Test
    fun withoutHopesShowsTheEmptyState() {
        show(LibraryUiState(items = emptyList()))

        compose.onNodeWithText(text(R.string.library_empty_title)).assertExists()
        compose.onNodeWithText(text(R.string.library_new_hope)).assertExists()
    }

    @Test
    fun onlyAHopeWithALinkShowsItAndOpensTheOriginal() {
        val linked = item(2, "Antes de entrar", linkUrl = "https://youtu.be/jNQXAC9IVRw")
        show(LibraryUiState(items = listOf(linked, item(1, "Respira"))))

        compose.onAllNodesWithContentDescription(text(R.string.library_has_link)).assertCountEquals(1)
        compose.onNodeWithContentDescription(text(R.string.library_more, "Respira")).performClick()
        compose.onNodeWithText(text(R.string.library_open_original)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.library_edit)).performClick()

        compose.onNodeWithContentDescription(text(R.string.library_more, "Antes de entrar")).performClick()
        compose.onNodeWithText(text(R.string.library_open_original)).performClick()
        assertEquals(linked, opened)
    }
}
