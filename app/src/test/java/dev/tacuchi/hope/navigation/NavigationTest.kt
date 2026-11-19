package dev.tacuchi.hope.navigation

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

    private fun assertOnLibrary() {
        compose.onNodeWithText(text(R.string.library_empty_title)).assertExists()
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
        compose.onNodeWithText(text(R.string.library_find_inspiration)).performClick()
        compose.onNodeWithText(text(R.string.search_intro)).assertExists()

        pressBack()
        assertOnLibrary()
    }

    @Test
    fun assetsOpensAndReturnsWithSystemBack() {
        compose.onNodeWithContentDescription(text(R.string.library_assets)).performClick()
        compose.onNodeWithText(text(R.string.assets_empty_title)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.assets_import)).assertIsNotEnabled()

        pressBack()
        assertOnLibrary()
    }

    @Test
    fun newHopeOpensWithFutureControlsDisabledAndCloses() {
        compose.onNodeWithText(text(R.string.library_new_hope)).performClick()
        compose.onNodeWithText(text(R.string.editor_save)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.editor_listen)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.editor_record)).assertIsNotEnabled()

        compose.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        assertOnLibrary()
    }

    @Test
    fun destinationSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription(text(R.string.library_assets)).performClick()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText(text(R.string.assets_empty_title)).assertExists()
        pressBack()
        assertOnLibrary()
    }
}
