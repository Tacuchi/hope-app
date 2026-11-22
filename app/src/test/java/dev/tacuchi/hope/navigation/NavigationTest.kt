package dev.tacuchi.hope.navigation

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.MainActivity
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.ui.importing.ImportPurpose
import dev.tacuchi.hope.ui.importing.ImportViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import java.io.File
import dev.tacuchi.hope.ui.R as UiR
import dev.tacuchi.hope.ui.settings.R as SettingsR
import dev.tacuchi.hope.ui.search.R as SearchR
import dev.tacuchi.hope.ui.library.R as LibraryR
import dev.tacuchi.hope.ui.assets.R as AssetsR
import dev.tacuchi.hope.ui.editor.R as EditorR

/** La prueba abre Hope con una apariencia ya guardada: Oscuro y Coral. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SavedDarkCoral

private fun appearancePreferences() =
    ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("appearance", Context.MODE_PRIVATE)

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    val compose = createAndroidComposeRule<MainActivity>()

    /** Escribe la apariencia antes de que se abra la actividad, como una instalación con datos. */
    private val savedAppearance: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            if (description.getAnnotation(SavedDarkCoral::class.java) == null) return
            appearancePreferences().edit(commit = true) {
                putString("theme", "Dark")
                putString("palette", "Coral")
            }
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(savedAppearance).around(compose)

    private fun text(id: Int) = compose.activity.getString(id)

    /** Atrás del sistema (gesto o botón), por el mismo despachador que usa la actividad. */
    private fun pressBack() {
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** La biblioteca lee las hopes de Room en segundo plano: el estado vacío llega después. */
    @OptIn(ExperimentalTestApi::class)
    private fun assertOnLibrary() {
        compose.waitUntilExactlyOneExists(hasText(text(LibraryR.string.library_empty_title)), timeoutMillis = 5_000)
    }

    /** Assets lee el catálogo de Room en segundo plano: el estado vacío llega después. */
    @OptIn(ExperimentalTestApi::class)
    private fun waitForEmptyAssets() {
        compose.waitUntilExactlyOneExists(hasText(text(AssetsR.string.assets_empty_title)), timeoutMillis = 5_000)
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [30])
    fun olderAndroidOffersOnlyFixedColors() {
        compose.onNodeWithContentDescription(text(UiR.string.settings)).performClick()
        compose.onNodeWithText(text(SettingsR.string.palette_violet)).assertExists()
        compose.onNodeWithText(text(SettingsR.string.palette_dynamic)).assertDoesNotExist()
    }

    @Test
    fun opensWithEmptyLibrary() {
        assertOnLibrary()
        compose.onNodeWithText(text(LibraryR.string.library_new_hope)).assertExists()
        compose.onNodeWithText(text(LibraryR.string.library_find_inspiration)).assertExists()
    }

    @Test
    fun searchOpensFromTopBarAndReturnsWithTopBarBack() {
        compose.onNodeWithContentDescription(text(LibraryR.string.library_search)).performClick()
        compose.onNodeWithText(text(SearchR.string.search_found_title)).assertExists()

        compose.onNodeWithContentDescription(text(UiR.string.action_back)).performClick()
        assertOnLibrary()
    }

    @Test
    fun searchOpensFromInspirationButtonAndReturnsWithSystemBack() {
        assertOnLibrary()
        compose.onNodeWithText(text(LibraryR.string.library_find_inspiration)).performClick()
        compose.onNodeWithText(text(SearchR.string.search_intro)).assertExists()

        pressBack()
        assertOnLibrary()
    }

    @Test
    fun assetsOpensAndReturnsWithSystemBack() {
        compose.onNodeWithContentDescription(text(LibraryR.string.library_assets)).performClick()
        waitForEmptyAssets()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText(text(UiR.string.assets_import))), useUnmergedTree = true).assertIsEnabled()

        pressBack()
        assertOnLibrary()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newHopeOpensTheEditorAndCloses() {
        assertOnLibrary()
        compose.onNodeWithText(text(LibraryR.string.library_new_hope)).performClick()
        compose.waitUntilExactlyOneExists(hasText(text(EditorR.string.editor_record)), timeoutMillis = 5_000)
        // Sin pistas no hay nada que escuchar; el enlace de origen llega en otra fase.
        compose.onNodeWithText(text(EditorR.string.editor_listen)).assertIsNotEnabled()
        compose.onNodeWithText(text(EditorR.string.editor_save)).assertIsEnabled()

        compose.onNodeWithContentDescription(text(UiR.string.action_close)).performClick()
        assertOnLibrary()
    }

    @Test
    fun destinationSurvivesActivityRecreation() {
        compose.onNodeWithContentDescription(text(LibraryR.string.library_assets)).performClick()

        compose.activityRule.scenario.recreate()

        waitForEmptyAssets()
        pressBack()
        assertOnLibrary()
    }

    /** Si la actividad pinta las barras del sistema para un fondo claro: así se ve el tema aplicado. */
    private fun lightBars(): Boolean {
        var light = false
        compose.activityRule.scenario.onActivity {
            light = WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars
        }
        return light
    }

    @Test
    @SavedDarkCoral
    fun savedAppearanceAppliesAndAChangeSurvivesRecreation() {
        assertOnLibrary()
        assertEquals(false, lightBars())
        compose.onNodeWithContentDescription(text(UiR.string.settings)).performClick()
        compose.onNodeWithText(text(SettingsR.string.theme_dark)).assertIsSelected()
        compose.onNodeWithText(text(SettingsR.string.palette_coral)).assertIsSelected()

        compose.onNodeWithText(text(SettingsR.string.theme_light)).performClick()
        compose.onNodeWithText(text(SettingsR.string.palette_violet)).performClick()
        compose.waitForIdle()
        assertEquals(true, lightBars())
        pressBack()
        assertOnLibrary()

        compose.activityRule.scenario.recreate()

        assertOnLibrary()
        assertEquals(true, lightBars())
        assertEquals("Light", appearancePreferences().getString("theme", null))
        assertEquals("Violet", appearancePreferences().getString("palette", null))
        compose.onNodeWithContentDescription(text(UiR.string.settings)).performClick()
        compose.onNodeWithText(text(SettingsR.string.theme_light)).assertIsSelected()
        compose.onNodeWithText(text(SettingsR.string.palette_violet)).assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun licensesOpenFromSettingsWithTheGeneratedList() {
        compose.onNodeWithContentDescription(text(UiR.string.settings)).performClick()
        compose.onNodeWithText(text(SettingsR.string.licenses)).performClick()

        compose.waitUntilAtLeastOneExists(hasText("AboutLibraries", substring = true), timeoutMillis = 5_000)
        pressBack()
        compose.onNodeWithText(text(SettingsR.string.licenses)).assertExists()
    }

    /** La importación de la actividad: la misma instancia que recibe lo compartido. */
    private fun importViewModel(): ImportViewModel {
        var vm: ImportViewModel? = null
        compose.activityRule.scenario.onActivity { vm = ViewModelProvider(it)[ImportViewModel::class.java] }
        return vm!!
    }

    /** Una imagen como la que elegiría la persona en el selector del sistema. */
    private fun pickedImage(): Uri {
        val file = File(compose.activity.cacheDir, "elegida-${System.nanoTime()}.png")
        file.outputStream().use { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anImportFromAssetsEndsInAssetsWithTheNewAsset() {
        compose.onNodeWithContentDescription(text(LibraryR.string.library_assets)).performClick()
        waitForEmptyAssets()

        compose.runOnIdle { importViewModel().start(pickedImage(), "image/png", AssetOrigin.Gallery, ImportPurpose.Catalog) }

        compose.waitUntilExactlyOneExists(hasText(compose.activity.getString(UiR.string.asset_name_image, 1)), timeoutMillis = 5_000)
        pressBack()
        assertOnLibrary()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun anImportFromTheEditorReturnsToTheEditorWithTheCover() {
        assertOnLibrary()
        compose.onNodeWithText(text(LibraryR.string.library_new_hope)).performClick()
        compose.waitUntilExactlyOneExists(hasText(text(EditorR.string.editor_pick_cover)), timeoutMillis = 5_000)

        compose.runOnIdle { importViewModel().start(pickedImage(), "image/png", AssetOrigin.Gallery, ImportPurpose.EditorCover) }

        compose.waitUntilExactlyOneExists(hasText(text(EditorR.string.editor_change_cover)), timeoutMillis = 5_000)
        // El resultado se entrega una sola vez: recrear la actividad no lo vuelve a aplicar ni cambia de destino.
        compose.activityRule.scenario.recreate()
        compose.waitUntilExactlyOneExists(hasText(text(EditorR.string.editor_record)), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription(text(UiR.string.action_close)).performClick()
        assertOnLibrary()
    }
}
