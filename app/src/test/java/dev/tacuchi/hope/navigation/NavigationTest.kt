package dev.tacuchi.hope.navigation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.MainActivity
import dev.tacuchi.hope.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int) = compose.activity.getString(id)

    /** Atrás del sistema (gesto o botón), por el mismo despachador que usa la actividad. */
    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** La biblioteca lee las hopes de Room en segundo plano: el estado vacío llega después. */
    @OptIn(ExperimentalTestApi::class)
    private fun assertOnLibrary() {
        compose.waitUntilExactlyOneExists(hasText(text(R.string.library_empty_title)), timeoutMillis = 5_000)
    }

    /** Assets lee el catálogo de Room en segundo plano: el estado vacío llega después. */
    @OptIn(ExperimentalTestApi::class)
    private fun waitForEmptyAssets() {
        compose.waitUntilExactlyOneExists(hasText(text(R.string.assets_empty_title)), timeoutMillis = 5_000)
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [30])
    fun olderAndroidOffersOnlyFixedColors() {
        compose.onNodeWithContentDescription(text(R.string.settings)).performClick()
        compose.onNodeWithText(text(R.string.palette_violet)).assertExists()
        compose.onNodeWithText(text(R.string.palette_dynamic)).assertDoesNotExist()
    }

    @Test
    fun opensWithEmptyLibrary() {
        assertOnLibrary()
        compose.onNodeWithText(text(R.string.library_new_hope)).assertExists()
        compose.onNodeWithText(text(R.string.library_find_inspiration)).assertExists()
    }

    @Test
    fun searchOpensFromTopBarAndReturnsWithTopBarBack() {
        compose.onNodeWithContentDescription(text(R.string.library_search)).performClick()
        compose.onNodeWithText(text(R.string.search_found_title)).assertExists()

        compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        assertOnLibrary()
    }

    @Test
    fun searchOpensFromInspirationButtonAndReturnsWithSystemBack() {
        assertOnLibrary()
        compose.onNodeWithText(text(R.string.library_find_inspiration)).performClick()
        compose.onNodeWithText(text(R.string.search_intro)).assertExists()

        pressBack()
        assertOnLibrary()
    }

    @Test
    fun assetsOpensAndReturnsWithSystemBack() {
        compose.onNodeWithContentDescription(text(R.string.library_assets)).performClick()
        waitForEmptyAssets()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(R.string.assets_import))), useUnmergedTree = true).assertIsEnabled()

        pressBack()
        assertOnLibrary()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newHopeOpensTheEditorAndCloses() {
        assertOnLibrary()
        compose.onNodeWithText(text(R.string.library_new_hope)).performClick()
        compose.waitUntilExactlyOneExists(hasText(text(R.string.editor_record)), timeoutMillis = 5_000)
        // Sin pistas no hay nada que escuchar; el enlace de origen llega en otra fase.
        compose.onNodeWithText(text(R.string.editor_listen)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.editor_save)).assertIsEnabled()

        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        assertOnLibrary()
    }

    @Test
    fun destinationSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription(text(R.string.library_assets)).performClick()

        compose.activityRule.scenario.recreate()

        waitForEmptyAssets()
        pressBack()
        assertOnLibrary()
    }
}
