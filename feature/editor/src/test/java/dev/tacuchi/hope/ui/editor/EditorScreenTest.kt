package dev.tacuchi.hope.ui.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.media.PreviewPlayback
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.ui.theme.HopeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import dev.tacuchi.hope.ui.R as UiR

/** La parte visual del editor sin ViewModel: pasos, hojas «Efectos» y selector. */
@RunWith(AndroidJUnit4::class)
class EditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun audio(id: Long, kind: AssetNameKind, durationMs: Long) = Asset(
        id = id, type = kind.type, nameKind = kind, number = id.toInt(), frameMs = null, origin = AssetOrigin.TikTok,
        createdAt = 0, durationMs = durationMs, file = File("a-$id.m4a"), mimeType = "audio/mp4",
    )

    private val background = audio(1, AssetNameKind.Audio, 72_000)
    private val voice = audio(2, AssetNameKind.Recording, 41_000)

    private fun state(step: EditorStep, draft: EditorDraft, effects: EffectsSheet? = null) =
        EditorUiState(loading = false, step = step, reached = EditorStep.Review, draft = draft, effects = effects)

    private fun sheetFor(part: EffectsPart, draft: EditorDraft) =
        EffectsSheet(part, draft.backgroundEffects, draft.voiceEffects, draft.hopeEffects, draft.coverEffects)

    @Test
    fun theCurrentStepIsShownAndADoneStepCanBeTouched() {
        var opened: EditorStep? = null
        compose.setContent {
            HopeTheme {
                EditorContent(state(EditorStep.Background, EditorDraft(background = background)), EditorActions(onStep = { opened = it }))
            }
        }
        compose.onNodeWithText(text(R.string.editor_step_progress, 2, 5, text(R.string.step_background))).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.step_done, text(R.string.step_cover))).performClick()
        assertEquals(EditorStep.Cover, opened)
        compose.onNodeWithText(text(R.string.editor_next, text(R.string.step_voice))).assertIsDisplayed()
    }

    /** El botón «Efectos» de cada paso resume lo activo. */
    @Test
    fun theEffectsButtonSummarizesWhatIsOn() {
        val draft = EditorDraft(
            background = background,
            backgroundEffects = BackgroundEffects(Fades(2_000, 4_000), RoomEffect(RoomType.Hall, 30), SpeedLevel.Slow),
        )
        var opened: EffectsPart? = null
        compose.setContent { HopeTheme { EditorContent(state(EditorStep.Background, draft), EditorActions(onOpenEffects = { opened = it })) } }

        val summary = listOf(text(R.string.room_hall), text(R.string.speed_slow), context.resources.getQuantityString(R.plurals.effects_fade_count, 2, 2))
            .joinToString(text(UiR.string.assets_subtitle_separator))
        compose.onNodeWithText(summary).performScrollTo().performClick()
        assertEquals(EffectsPart.Background, opened)
        // El recorte sigue en tiempos del original y dice cuánto dura en la hope.
        compose.onNodeWithText(text(R.string.editor_trim_in_hope, "1:12", "1:20")).performScrollTo().assertIsDisplayed()
    }

    /** Cada hoja se abre con los efectos de su parte, con los fundidos incluidos, y cierra con «Cancelar» o «Aceptar». */
    @Test
    fun eachSheetHasTheEffectsOfItsPart() {
        val draft = EditorDraft(background = background, voice = voice)
        val expected = mapOf(
            EffectsPart.Background to listOf(R.string.effects_title_background, R.string.effects_fades, R.string.effects_room, R.string.effects_speed),
            EffectsPart.Voice to listOf(R.string.effects_title_voice, R.string.effects_fades, R.string.effects_room, R.string.effects_clear_voice),
            EffectsPart.Hope to listOf(R.string.effects_title_hope, R.string.effects_fades, R.string.effects_duck),
        )
        var part by mutableStateOf(EffectsPart.Background)
        compose.setContent {
            HopeTheme {
                AudioEffectsSheet(state(EditorStep.Mix, draft), sheetFor(part, draft), {}, {}, {}, {}, {}, {}, {})
            }
        }
        for ((sheet, titles) in expected) {
            part = sheet
            compose.waitForIdle()
            titles.forEach { assertTrue(compose.onAllNodesWithText(text(it)).fetchSemanticsNodes().isNotEmpty()) }
            compose.onNodeWithText(text(R.string.effects_listen)).assertExists()
            compose.onNodeWithText(text(R.string.effects_cancel)).assertExists()
            compose.onNodeWithText(text(R.string.effects_accept)).assertExists()
        }
    }

    /** Cambiar un efecto en la hoja cambia lo que se prueba; escuchar antes de cerrar pide su parte. */
    @Test
    fun changingAnEffectChangesWhatIsTried() {
        val draft = EditorDraft(background = background, trimStartMs = 8_000, trimEndMs = 52_000)
        var sheet by mutableStateOf(sheetFor(EffectsPart.Background, draft))
        var listened = 0
        compose.setContent {
            HopeTheme {
                AudioEffectsSheet(
                    state(EditorStep.Background, draft, sheet), sheet,
                    onBackground = { change -> sheet = sheet.copy(background = change(sheet.background)) },
                    onVoice = {}, onHope = {}, onListen = { listened++ }, onCancel = {}, onAccept = {}, onGoToVoice = {},
                )
            }
        }
        compose.onNodeWithText(text(R.string.room_church)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.speed_slower)).performScrollTo().performClick()
        assertEquals(RoomType.Church, sheet.background.room.type)
        assertEquals(SpeedLevel.Slower, sheet.background.speed)
        // La duración que resulta de la velocidad: 44 s a 0,8× son 55 s.
        compose.onNodeWithText(text(R.string.effects_speed_hint, "0:55", "0:44")).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText(text(R.string.effects_listen)).performClick()
        assertEquals(1, listened)
    }

    /** Con una hope solo con fondo, bajar el fondo bajo la voz no se puede activar y se explica. */
    @Test
    fun withoutAVoiceTheDuckIsDisabledAndExplained() {
        val draft = EditorDraft(background = background)
        var changed = false
        var wentToVoice = false
        compose.setContent {
            HopeTheme {
                AudioEffectsSheet(
                    state(EditorStep.Mix, draft), sheetFor(EffectsPart.Hope, draft),
                    onBackground = {}, onVoice = {}, onHope = { changed = true }, onListen = {}, onCancel = {}, onAccept = {},
                    onGoToVoice = { wentToVoice = true },
                )
            }
        }
        compose.onNodeWithText(text(R.string.effects_duck_needs_voice)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.effects_duck_needs_voice)).performClick()
        assertTrue(!changed)
        compose.onNodeWithText(text(R.string.effects_go_to_voice)).performScrollTo().performClick()
        assertTrue(wentToVoice)
    }

    @Test
    fun theCoverSheetHasFilterFrameTextureAndFraming() {
        var effects by mutableStateOf(CoverEffects())
        compose.setContent {
            HopeTheme {
                CoverEffectsSheet(null, effects, { change -> effects = change(effects) }, {}, {})
            }
        }
        listOf(R.string.cover_tab_filter, R.string.cover_tab_frame, R.string.cover_tab_texture, R.string.cover_tab_framing).forEach {
            compose.onNodeWithText(text(it)).assertExists()
        }
        compose.onNodeWithText(text(R.string.filter_warm)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.cover_tab_frame)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.frame_polaroid)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.cover_tab_texture)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.texture_vignette)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.cover_tab_framing)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.cover_zoom_more)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.cover_move_up)).performScrollTo().performClick()
        assertEquals(
            CoverEffects(filter = dev.tacuchi.hope.domain.model.CoverFilter.Warm, frame = dev.tacuchi.hope.domain.model.CoverFrame.Polaroid,
                texture = dev.tacuchi.hope.domain.model.CoverTexture.Vignette, zoom = 110, offsetY = -CoverEffects.OFFSET_STEP),
            effects,
        )
        compose.onNodeWithText(text(R.string.effects_accept)).assertExists()
    }

    /** En el selector, escuchar y elegir son dos cosas: solo «Usar» cambia la pista. */
    @Test
    fun thePickerListensWithoutChoosing() {
        val first = audio(3, AssetNameKind.Audio, 48_000)
        val second = audio(4, AssetNameKind.Audio, 30_000)
        var listened: Asset? = null
        var confirmed: Asset? = null
        var preview by mutableStateOf(PreviewPlayback())
        compose.setContent {
            HopeTheme {
                AssetPickerSheet(
                    EditorSlot.Background, listOf(PickableAsset(first, first.file), PickableAsset(second, second.file)), inUse = null,
                    preview = preview, onListen = { listened = it }, onConfirm = { confirmed = it }, {}, {}, {}, {},
                )
            }
        }
        compose.onNodeWithContentDescription(text(R.string.picker_listen, text(UiR.string.asset_name_audio, 3))).performClick()
        assertEquals(first, listened)
        assertNull(confirmed)
        preview = PreviewPlayback(first.id, isPlaying = true, positionMs = 17_000, durationMs = 48_000)
        compose.onNodeWithContentDescription(text(R.string.picker_pause, text(UiR.string.asset_name_audio, 3))).assertExists()

        compose.onNodeWithText(text(UiR.string.asset_name_audio, 4)).performClick()
        assertNull(confirmed)
        compose.onNodeWithText(text(R.string.picker_use, text(UiR.string.asset_name_audio, 4))).performClick()
        assertEquals(second, confirmed)
    }

    /** Revisar muestra cada parte con «Cambiar», que abre su paso, y guarda desde ahí. */
    @Test
    fun theReviewJumpsToEachPartAndSaves() {
        val draft = EditorDraft(title = "Antes de entrar", background = background, voice = voice, hopeEffects = HopeEffects(Fades(1_000, 0)))
        val opened = mutableListOf<EditorStep>()
        var saved = false
        compose.setContent {
            HopeTheme { EditorContent(state(EditorStep.Review, draft), EditorActions(onStep = { opened += it }, onSave = { saved = true })) }
        }
        listOf(
            text(R.string.review_cover_row),
            text(R.string.review_background_row, text(UiR.string.asset_name_audio, 1)),
            text(R.string.review_voice_row, text(UiR.string.asset_name_recording, 2)),
            text(R.string.review_mix_row),
        ).forEach { compose.onNodeWithContentDescription(text(R.string.review_change_part, it)).performScrollTo().performClick() }
        assertEquals(listOf(EditorStep.Cover, EditorStep.Background, EditorStep.Voice, EditorStep.Mix), opened)
        compose.onNodeWithText(text(R.string.editor_save)).performClick()
        assertTrue(saved)
    }

    @Test
    fun aVoiceStepWithoutVoiceOffersToGoOnWithout() {
        compose.setContent { HopeTheme { EditorContent(state(EditorStep.Voice, EditorDraft(background = background)), EditorActions()) } }
        compose.onNodeWithText(text(R.string.editor_skip_voice)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.editor_record)).assertIsDisplayed()
        assertTrue(VoiceEffects().isEmpty)
    }
}
