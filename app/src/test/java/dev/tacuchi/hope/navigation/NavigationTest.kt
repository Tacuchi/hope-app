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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.tacuchi.hope.data.LocalCatalog
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.model.SpeedLevel
import kotlinx.coroutines.runBlocking
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

/** La prueba abre Hope con una hope ya guardada, con fondo, voz, portada y efectos. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SavedHope

/** Escribe una hope en la base de la app, como una instalación con datos. Los audios son de mentira: no se reproducen. */
private fun saveHope() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // El mismo catálogo que abre la app: misma base y misma carpeta de assets.
    val catalog = LocalCatalog(context)
    val assets = catalog.assets
    val hopes = catalog.hopes
    runBlocking {
        fun staged(name: String) = File(context.cacheDir, name).apply { writeText("datos") }
        val created = assets.add(
            listOf(
                NewAsset(staged("fondo.m4a"), AssetNameKind.Audio, AssetOrigin.Files, "audio/mp4", durationMs = 72_000),
                NewAsset(staged("voz.m4a"), AssetNameKind.Recording, AssetOrigin.Recording, "audio/mp4", durationMs = 41_000),
            ),
        )
        hopes.save(
            Hope(
                title = "Antes de entrar", coverAssetId = null, backgroundAssetId = created[0].id, voiceAssetId = created[1].id,
                backgroundVolume = 60, voiceVolume = 90, trimStartMs = 8_000, trimEndMs = 52_000, voiceOffsetMs = 3_000, createdAt = 0,
                backgroundEffects = BackgroundEffects(speed = SpeedLevel.Slow),
                hopeEffects = HopeEffects(Fades(1_000, 3_000), DuckLevel.Medium),
            ),
        )
    }
}

private fun appearancePreferences() =
    ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("appearance", Context.MODE_PRIVATE)

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    val compose = createAndroidComposeRule<MainActivity>()

    /** Escribe la apariencia antes de que se abra la actividad, como una instalación con datos. */
    private val savedAppearance: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            if (description.getAnnotation(SavedHope::class.java) != null) saveHope()
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

    private fun step(step: Int, name: Int) =
        compose.activity.getString(EditorR.string.editor_step_progress, step, 5, text(name))

    @OptIn(ExperimentalTestApi::class)
    private fun waitForStep(step: Int, name: Int) {
        compose.waitUntilExactlyOneExists(hasText(step(step, name)), timeoutMillis = 5_000)
    }

    /** Nueva hope abre en Portada; se avanza por los pasos y se vuelve sin perder el título. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun newHopeGoesThroughTheStepsAndBack() {
        assertOnLibrary()
        compose.onNodeWithText(text(LibraryR.string.library_new_hope)).performClick()
        waitForStep(1, EditorR.string.step_cover)
        compose.onNode(hasSetTextAction() and hasText(text(EditorR.string.editor_field_title))).performTextInput("Respira")

        compose.onNodeWithText(compose.activity.getString(EditorR.string.editor_next, text(EditorR.string.step_background))).performClick()
        waitForStep(2, EditorR.string.step_background)
        compose.onNodeWithText(compose.activity.getString(EditorR.string.editor_next, text(EditorR.string.step_voice))).performClick()
        waitForStep(3, EditorR.string.step_voice)
        // Sin voz, el paso ofrece seguir sin ella.
        compose.onNodeWithText(text(EditorR.string.editor_skip_voice)).performClick()
        waitForStep(4, EditorR.string.step_mix)
        compose.onNodeWithText(compose.activity.getString(EditorR.string.editor_next, text(EditorR.string.step_review))).performClick()
        waitForStep(5, EditorR.string.step_review)

        // Sin pistas no se guarda: se avisa y se queda en Revisar.
        compose.onNodeWithText(text(EditorR.string.editor_save)).performClick()
        compose.onNodeWithText(text(EditorR.string.editor_error_tracks)).performScrollTo().assertExists()

        pressBack()
        waitForStep(4, EditorR.string.step_mix)
        compose.onNodeWithContentDescription(compose.activity.getString(EditorR.string.step_done, text(EditorR.string.step_cover))).performClick()
        waitForStep(1, EditorR.string.step_cover)
        compose.onNodeWithText("Respira").assertExists()
        compose.onNodeWithContentDescription(text(UiR.string.action_close)).performClick()
        assertOnLibrary()
    }

    /**
     * «Editar» abre Revisar con los valores guardados; desde ahí se salta a un paso, se abre una
     * hoja «Efectos», se vuelve y se guarda sobre la misma hope.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    @SavedHope
    fun editingOpensTheReviewWithTheSavedValuesAndSavesBack() {
        compose.waitUntilExactlyOneExists(hasText("Antes de entrar"), timeoutMillis = 5_000)
        // En la pantalla chica de la prueba el botón «Nueva hope» tapa el menú: se lo acciona sin tocar.
        compose.onNodeWithContentDescription(compose.activity.getString(LibraryR.string.library_more, "Antes de entrar"))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntilExactlyOneExists(hasText(text(LibraryR.string.library_edit)), timeoutMillis = 5_000)
        compose.onNodeWithText(text(LibraryR.string.library_edit)).performClick()

        waitForStep(5, EditorR.string.step_review)
        compose.onAllNodesWithText("Antes de entrar").onFirst().assertExists()
        compose.onNodeWithText(compose.activity.getString(EditorR.string.review_volumes, 60, 90), substring = true).performScrollTo().assertExists()
        val mixRow = text(EditorR.string.review_mix_row)
        compose.onNodeWithContentDescription(compose.activity.getString(EditorR.string.review_change_part, mixRow)).performScrollTo().performClick()

        waitForStep(4, EditorR.string.step_mix)
        compose.onNodeWithText(text(EditorR.string.effects_hope_button)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(EditorR.string.effects_duck)), timeoutMillis = 5_000)
        compose.onNodeWithText(text(EditorR.string.effects_cancel)).performClick()
        compose.waitUntilDoesNotExist(hasText(text(EditorR.string.effects_cancel)), timeoutMillis = 5_000)

        compose.onNodeWithText(compose.activity.getString(EditorR.string.editor_next, text(EditorR.string.step_review))).performClick()
        waitForStep(5, EditorR.string.step_review)
        compose.onNodeWithText(text(EditorR.string.editor_save)).performClick()
        // Guardada, vuelve a la Biblioteca: la misma hope, no una nueva.
        compose.waitUntilExactlyOneExists(hasText("Antes de entrar"), timeoutMillis = 5_000)
        compose.onNodeWithText(step(5, EditorR.string.step_review)).assertDoesNotExist()
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
        compose.waitUntilExactlyOneExists(hasText(text(EditorR.string.editor_cover_empty)), timeoutMillis = 5_000)

        compose.runOnIdle { importViewModel().start(pickedImage(), "image/png", AssetOrigin.Gallery, ImportPurpose.EditorCover) }

        // Vuelve al mismo paso con la portada puesta.
        compose.waitUntilDoesNotExist(hasText(text(EditorR.string.editor_cover_empty)), timeoutMillis = 5_000)
        waitForStep(1, EditorR.string.step_cover)
        // El resultado se entrega una sola vez: recrear la actividad no lo vuelve a aplicar ni cambia de destino.
        compose.activityRule.scenario.recreate()
        waitForStep(1, EditorR.string.step_cover)
        compose.onNodeWithText(text(EditorR.string.editor_cover_empty)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(UiR.string.action_close)).performClick()
        assertOnLibrary()
    }
}
