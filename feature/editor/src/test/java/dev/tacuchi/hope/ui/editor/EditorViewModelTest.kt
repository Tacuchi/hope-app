package dev.tacuchi.hope.ui.editor

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.media.AudioOutputs
import dev.tacuchi.hope.domain.media.VoiceRecorder
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.CoverFilter
import dev.tacuchi.hope.domain.model.CoverFrame
import dev.tacuchi.hope.domain.model.CoverTexture
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.domain.model.WaveState
import dev.tacuchi.hope.domain.model.toMixSpec
import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkNetwork
import dev.tacuchi.hope.domain.model.LinkPreview
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.media.WaveformSource
import dev.tacuchi.hope.domain.model.Waveform
import dev.tacuchi.hope.domain.repository.LinkPreviewRepository
import dev.tacuchi.hope.domain.testing.FakeAssetPreview
import dev.tacuchi.hope.domain.usecase.LoadWaveformUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ReleaseAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.StopAllAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ToggleAssetPreviewUseCase
import dev.tacuchi.hope.domain.testing.FakeAssetRepository
import dev.tacuchi.hope.domain.testing.FakeHopePlayer
import dev.tacuchi.hope.domain.testing.FakeHopeRepository
import dev.tacuchi.hope.domain.testing.FakeMediaInspector
import dev.tacuchi.hope.domain.usecase.CheckBackgroundWhileRecordingUseCase
import dev.tacuchi.hope.domain.usecase.DiscardRecordingLeftoversUseCase
import dev.tacuchi.hope.domain.usecase.DiscardRecordingUseCase
import dev.tacuchi.hope.domain.usecase.GetAssetUseCase
import dev.tacuchi.hope.domain.usecase.GetHopeUseCase
import dev.tacuchi.hope.domain.usecase.GetLinkPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetsUseCase
import dev.tacuchi.hope.domain.usecase.ObservePlaybackUseCase
import dev.tacuchi.hope.domain.usecase.PlayMixUseCase
import dev.tacuchi.hope.domain.usecase.RecognizeLinkUseCase
import dev.tacuchi.hope.domain.usecase.SaveHopeUseCase
import dev.tacuchi.hope.domain.usecase.SaveRecordingUseCase
import dev.tacuchi.hope.domain.usecase.StartRecordingUseCase
import dev.tacuchi.hope.domain.usecase.StopMixUseCase
import dev.tacuchi.hope.domain.usecase.StopRecordingUseCase
import dev.tacuchi.hope.domain.usecase.ToggleMixUseCase
import dev.tacuchi.hope.domain.usecase.UpdateMixUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.util.UUID

/** Graba bytes de mentira a un temporal de [tempDir]: el micrófono no existe en las pruebas. */
private class FakeVoiceRecorder(private val tempDir: File) : VoiceRecorder {
    private var current: File? = null

    override val isRecording get() = current != null

    override fun start() {
        discard()
        current = File(tempDir, "${UUID.randomUUID()}.m4a").apply { writeBytes(ByteArray(64) { 1 }) }
    }

    override fun finish(): File? = current.also { current = null }

    override fun discard() {
        current?.delete()
        current = null
    }

    override fun discardTake(take: File) {
        take.delete()
    }

    override fun discardLeftovers() {
        if (current == null) tempDir.listFiles()?.forEach { it.delete() }
    }
}

/** Audífonos conectados o no, según la prueba. */
private class FakeAudioOutputs(var headphones: Boolean = false) : AudioOutputs {
    override fun headphonesConnected() = headphones
}

/** Ondas sin decodificar: una por asset, o un error para los ids de [failing]. Anota cada cálculo. */
private class FakeWaveforms(val failing: MutableSet<Long> = mutableSetOf()) : WaveformSource {
    val computed = mutableListOf<Long>()

    override suspend fun waveform(asset: Asset): Waveform {
        computed += asset.id
        if (asset.id in failing) throw java.io.IOException("No se pudo leer")
        return Waveform(FloatArray(Waveform.BINS) { (it % 10) / 10f }, asset.durationMs ?: 0)
    }
}

/** La vista previa de YouTube sin red: la que fije la prueba, o ninguna como sin conexión. */
private class FakeLinkPreviews(var preview: (Link) -> LinkPreview? = { null }) : LinkPreviewRepository {
    override suspend fun previewOf(link: Link): LinkPreview? = preview(link)
}

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var assets: FakeAssetRepository
    private lateinit var hopes: FakeHopeRepository
    private lateinit var recordingDir: File
    private val player = FakeHopePlayer()
    private val preview = FakeAssetPreview()
    private val waveforms = FakeWaveforms()
    private val collectors = mutableListOf<Job>()
    private var clock = 1_000L

    /** Sin red por defecto: no hay vista previa, como sin conexión. */
    private val previews = FakeLinkPreviews()

    @Before
    fun setUp() {
        assets = FakeAssetRepository(temp.newFolder("assets")) { clock++ }
        hopes = FakeHopeRepository(assets) { clock++ }
        recordingDir = temp.newFolder("recording-tmp")
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
    }

    /** El ViewModel con los casos de uso reales sobre los dobles de sus contratos. */
    private fun newViewModel(args: EditorArgs = EditorArgs(), headphones: Boolean = false): EditorViewModel {
        val recorder = FakeVoiceRecorder(recordingDir)
        return EditorViewModel(
            args = args,
            observeAssets = ObserveAssetsUseCase(assets),
            observePlayback = ObservePlaybackUseCase(player),
            observePreview = ObserveAssetPreviewUseCase(preview),
            discardRecordingLeftovers = DiscardRecordingLeftoversUseCase(recorder),
            loadWaveform = LoadWaveformUseCase(waveforms),
            getHope = GetHopeUseCase(hopes),
            getAsset = GetAssetUseCase(assets),
            saveHope = SaveHopeUseCase(hopes),
            recognizeLink = RecognizeLinkUseCase(),
            getLinkPreview = GetLinkPreviewUseCase(previews),
            toggleMix = ToggleMixUseCase(player),
            playMix = PlayMixUseCase(player),
            updateMix = UpdateMixUseCase(player),
            stopMix = StopMixUseCase(player),
            togglePreview = ToggleAssetPreviewUseCase(preview, player),
            stopPreview = StopAllAssetPreviewUseCase(preview),
            releasePreview = ReleaseAssetPreviewUseCase(preview),
            checkBackgroundWhileRecording = CheckBackgroundWhileRecordingUseCase(FakeAudioOutputs(headphones)),
            startVoiceTake = StartRecordingUseCase(recorder),
            stopVoiceTake = StopRecordingUseCase(recorder),
            saveVoiceTake = SaveRecordingUseCase(recorder, FakeMediaInspector(), assets),
            discardVoiceTake = DiscardRecordingUseCase(recorder),
        )
    }

    private fun viewModel(args: EditorArgs = EditorArgs(), headphones: Boolean = false) = newViewModel(args, headphones).also { vm ->
        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { vm.state.collect {} }
        waitUntil { !vm.now.loading }
    }

    /** El ViewModel trabaja en corrutinas que vuelven al hilo principal: se procesa hasta que se cumpla. */
    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(5)
        }
        error("No se cumplió a tiempo")
    }

    private fun add(kind: AssetNameKind, durationMs: Long? = null): Asset = runBlocking {
        val file = temp.newFile("f-${clock++}.bin").apply { writeText("datos") }
        assets.add(listOf(NewAsset(file, kind, AssetOrigin.TikTok, "audio/mp4", durationMs = durationMs))).single()
    }

    /** El estado de la pantalla, ya procesados los cambios pendientes del hilo principal. */
    private val EditorViewModel.now: EditorUiState
        get() {
            shadowOf(Looper.getMainLooper()).idle()
            return state.value
        }

    private fun savedHopes() = runBlocking { hopes.observe().first() }

    @Test
    fun theCatalogArrivesInTheSameStateAndTellsLoadingFromEmpty() {
        val vm = newViewModel()
        // Antes de leer la base: cargando, no vacío.
        assertNull(vm.state.value.catalog)

        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { vm.state.collect {} }
        waitUntil { vm.state.value.catalog != null }
        assertEquals(emptyList<PickableAsset>(), vm.now.catalog)

        val frame = add(AssetNameKind.Frame)
        waitUntil { vm.state.value.catalog?.size == 1 }
        assertEquals(PickableAsset(frame, frame.file), vm.now.catalog!!.single())
        // El borrador sigue en el mismo estado mientras cambia el catálogo.
        vm.setTitle("Respira")
        assertEquals("Respira", vm.now.draft.title)
        assertEquals(1, vm.now.catalog!!.size)
    }

    @Test
    fun savingNeedsATitleAndAtLeastOneTrack() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val vm = viewModel()

        vm.save()
        waitUntil { vm.state.value.showErrors }
        vm.setTitle("Antes de entrar")
        vm.save()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(vm.now.saved)
        assertTrue(savedHopes().isEmpty())

        vm.choose(EditorSlot.Background, background)
        vm.setTitle("   ")
        vm.save()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(savedHopes().isEmpty())

        vm.setTitle("Antes de entrar")
        vm.save()
        waitUntil { vm.state.value.saved }
        assertEquals(listOf("Antes de entrar"), savedHopes().map { it.hope.title })
    }

    @Test
    fun savesTheMixSettingsAsTheyWereLeft() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val cover = add(AssetNameKind.Frame)
        val vm = viewModel(EditorArgs(coverAssetId = cover.id, backgroundAssetId = background.id))
        assertEquals(cover, vm.now.draft.cover)
        assertEquals(background, vm.now.draft.background)

        vm.setTitle("Antes de entrar")
        vm.setBackgroundVolume(30)
        vm.setTrim(4_000, 48_000)
        vm.shiftVoiceOffset(EditorViewModel.OFFSET_STEP_MS)
        vm.shiftVoiceOffset(EditorViewModel.OFFSET_STEP_MS)
        vm.save()
        waitUntil { vm.state.value.saved }

        val saved = savedHopes().single()
        assertEquals(cover, saved.cover)
        assertEquals(30, saved.hope.backgroundVolume)
        assertEquals(4_000L, saved.hope.trimStartMs)
        assertEquals(48_000L, saved.hope.trimEndMs)
        assertEquals(2 * EditorViewModel.OFFSET_STEP_MS, saved.hope.voiceOffsetMs)
    }

    @Test
    fun editingOpensWithEverySettingAndSavesOverTheSameHope() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val voice = add(AssetNameKind.Recording, durationMs = 10_000)
        val id = runBlocking {
            hopes.save(
                Hope(
                    title = "Respira", coverAssetId = null, backgroundAssetId = background.id, voiceAssetId = voice.id,
                    backgroundVolume = 30, voiceVolume = 80, trimStartMs = 1_000, trimEndMs = 40_000,
                    voiceOffsetMs = 2_000, createdAt = 0,
                ),
            )
        }
        val vm = viewModel(EditorArgs(hopeId = id))

        val draft = vm.now.draft
        assertFalse(vm.now.isNew)
        assertEquals(EditorDraft("Respira", null, background, voice, 30, 80, 1_000, 40_000, 2_000), draft)

        vm.setVoiceVolume(100)
        vm.save()
        waitUntil { vm.state.value.saved }

        val saved = savedHopes().single()
        assertEquals(id, saved.hope.id)
        assertEquals(100, saved.hope.voiceVolume)
        assertEquals(30, saved.hope.backgroundVolume)
        assertEquals(2_000L, saved.hope.voiceOffsetMs)
    }

    @Test
    fun aFinishedTakeBecomesARecordingAssetAndTheVoiceTrack() {
        val vm = viewModel()

        vm.startRecording()
        assertTrue(vm.now.recording != null)
        vm.stopRecording()
        vm.finishRecording()
        waitUntil { vm.state.value.recording == null }

        val voice = vm.now.draft.voice!!
        assertEquals(AssetNameKind.Recording, voice.nameKind)
        assertEquals(AssetOrigin.Recording, voice.origin)
        assertEquals(1, voice.number)
        assertTrue(voice.file.exists())
        assertTrue(recordingDir.listFiles()!!.isEmpty())
    }

    @Test
    fun aDiscardedTakeLeavesNothing() {
        val vm = viewModel()

        vm.startRecording()
        assertEquals(1, recordingDir.listFiles()!!.size)
        vm.discardRecording()

        assertNull(vm.now.recording)
        assertNull(vm.now.draft.voice)
        assertTrue(recordingDir.listFiles()!!.isEmpty())
        assertTrue(runBlocking { assets.observe(AssetType.Audio).first() }.isEmpty())

        // También después de detener, antes de «Listo».
        vm.startRecording()
        vm.stopRecording()
        vm.discardRecording()
        assertTrue(recordingDir.listFiles()!!.isEmpty())
        assertTrue(runBlocking { assets.observe(AssetType.Audio).first() }.isEmpty())
    }

    @Test
    fun withoutHeadphonesTheBackgroundStaysSilentWhileRecording() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)

        val speaker = viewModel(EditorArgs(backgroundAssetId = background.id), headphones = false)
        speaker.startRecording()
        assertEquals(false, speaker.now.recording?.backgroundPlays)
        assertEquals(true, speaker.now.recording?.hasBackground)
        speaker.discardRecording()
    }

    @Test
    fun aDeniedMicrophoneKeepsTheRestOfTheEditorWorking() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val vm = viewModel()

        vm.onMicDenied()
        assertTrue(vm.now.micDenied)
        vm.setTitle("Sin voz")
        vm.choose(EditorSlot.Background, background)
        vm.save()
        waitUntil { vm.state.value.saved }
        assertEquals(listOf("Sin voz"), savedHopes().map { it.hope.title })
    }

    @Test
    fun anImportFillsTheSlotThatAskedAndTheOtherOnlyIfEmpty() {
        val audio = add(AssetNameKind.AudioFromVideo, durationMs = 10_000)
        val frame = add(AssetNameKind.Frame)
        val vm = viewModel()

        vm.applyImported(EditorSlot.Background, listOf(audio, frame))
        assertEquals(audio, vm.now.draft.background)
        assertEquals(frame, vm.now.draft.cover)

        val otherAudio = add(AssetNameKind.Audio, durationMs = 5_000)
        val otherFrame = add(AssetNameKind.Frame)
        vm.applyImported(EditorSlot.Cover, listOf(otherAudio, otherFrame))
        assertEquals(otherFrame, vm.now.draft.cover)
        assertEquals(audio, vm.now.draft.background)
    }

    @Test
    fun aSharedYouTubeLinkIsSavedWithItsNetworkTitleAndThumbnail() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        previews.preview = { link ->
            LinkPreview(link.url, "Me at the zoo", "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg").takeIf { link.network == LinkNetwork.YouTube }
        }
        val vm = viewModel(EditorArgs(backgroundAssetId = background.id, linkUrl = "https://youtu.be/jNQXAC9IVRw"))

        assertEquals(LinkNetwork.YouTube, vm.now.draft.link?.network)
        waitUntil { vm.state.value.draft.currentPreview != null }
        vm.setTitle("Antes de entrar")
        vm.save()
        waitUntil { vm.state.value.saved }

        val saved = savedHopes().single().hope
        assertEquals("https://youtu.be/jNQXAC9IVRw", saved.linkUrl)
        assertEquals(LinkNetwork.YouTube, saved.linkNetwork)
        assertEquals("Me at the zoo", saved.linkTitle)
        assertEquals("https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg", saved.linkThumbnailUrl)
    }

    @Test
    fun withoutConnectionTheLinkIsSavedWithItsNetworkOnly() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val vm = viewModel(EditorArgs(backgroundAssetId = background.id))

        vm.setTitle("Respira")
        vm.setLink("https://youtu.be/jNQXAC9IVRw")
        vm.save()
        waitUntil { vm.state.value.saved }

        val saved = savedHopes().single().hope
        assertEquals("https://youtu.be/jNQXAC9IVRw", saved.linkUrl)
        assertEquals(LinkNetwork.YouTube, saved.linkNetwork)
        assertNull(saved.linkTitle)
        assertNull(saved.linkThumbnailUrl)
    }

    @Test
    fun textThatIsNotALinkIsFlaggedAndKeepsTheHopeFromSaving() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val vm = viewModel(EditorArgs(backgroundAssetId = background.id))
        vm.setTitle("Respira")

        vm.setLink("mira este video")
        assertTrue(vm.now.draft.linkInvalid)
        vm.save()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(savedHopes().isEmpty())

        // Un enlace de otro sitio se acepta como genérico; vaciar el campo también deja guardar.
        vm.setLink("https://vimeo.com/76979871")
        assertEquals(LinkNetwork.Generic, vm.now.draft.link?.network)
        vm.setLink("")
        vm.save()
        waitUntil { vm.state.value.saved }
        assertNull(savedHopes().single().hope.linkUrl)
    }

    /** Una hope que usa todos los campos y todos los efectos. */
    private fun fullHope(background: Asset, voice: Asset, cover: Asset) = Hope(
        title = "Antes de entrar", coverAssetId = cover.id, backgroundAssetId = background.id, voiceAssetId = voice.id,
        backgroundVolume = 60, voiceVolume = 90, trimStartMs = 8_000, trimEndMs = 52_000, voiceOffsetMs = 3_000, createdAt = 0,
        linkUrl = "https://youtu.be/jNQXAC9IVRw", linkNetwork = LinkNetwork.YouTube,
        voiceTrimStartMs = 1_000, voiceTrimEndMs = 27_000,
        backgroundEffects = BackgroundEffects(Fades(2_000, 4_000), RoomEffect(RoomType.Hall, 30), SpeedLevel.Slow),
        voiceEffects = VoiceEffects(Fades(500, 1_000), RoomEffect(RoomType.Room, 20), clearVoice = true),
        hopeEffects = HopeEffects(Fades(1_000, 3_000), DuckLevel.Medium),
        coverEffects = CoverEffects(CoverFilter.Warm, 70, CoverFrame.Thin, CoverTexture.Grain, 30, 120, 0, -25),
    )

    /** Se avanza y se vuelve por los pasos sobre el mismo borrador: no se pierde nada. */
    @Test
    fun stepsGoForwardAndBackWithoutLosingAnything() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 72_000)
        val vm = viewModel()
        assertEquals(EditorStep.Cover, vm.now.step)
        vm.setTitle("Antes de entrar")

        vm.next()
        assertEquals(EditorStep.Background, vm.now.step)
        vm.choose(EditorSlot.Background, background)
        vm.setTrim(8_000, 52_000)
        // No se salta a un paso que todavía no se alcanzó.
        vm.goTo(EditorStep.Review)
        assertEquals(EditorStep.Background, vm.now.step)

        vm.next()
        vm.startRecording()
        vm.stopRecording()
        vm.finishRecording()
        waitUntil { vm.state.value.recording == null }
        vm.next()
        assertEquals(EditorStep.Mix, vm.now.step)
        vm.setBackgroundVolume(60)
        vm.shiftVoiceOffset(EditorViewModel.OFFSET_STEP_MS)

        assertTrue(vm.back())
        assertTrue(vm.back())
        assertEquals(EditorStep.Background, vm.now.step)
        // Un paso ya alcanzado se toca para volver a él.
        vm.goTo(EditorStep.Mix)
        assertEquals(EditorStep.Mix, vm.now.step)
        vm.goTo(EditorStep.Cover)
        assertFalse(vm.back())

        val draft = vm.now.draft
        assertEquals("Antes de entrar", draft.title)
        assertEquals(background, draft.background)
        assertEquals(8_000L to 52_000L, draft.trimStartMs to draft.trimEndMs)
        assertEquals(AssetNameKind.Recording, draft.voice?.nameKind)
        assertEquals(60, draft.backgroundVolume)
        assertEquals(EditorViewModel.OFFSET_STEP_MS, draft.voiceOffsetMs)
    }

    /** «Editar» abre Revisar con todos los ajustes; «Usar en una hope existente» abre el paso del asset. */
    @Test
    fun editingOpensTheReviewWithEverySettingAndAnAssetOpensItsStep() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 72_000)
        val voice = add(AssetNameKind.Recording, durationMs = 41_000)
        val cover = add(AssetNameKind.Frame)
        val hope = fullHope(background, voice, cover)
        val id = runBlocking { hopes.save(hope) }

        val edit = viewModel(EditorArgs(hopeId = id))
        assertEquals(EditorStep.Review, edit.now.step)
        assertEquals(EditorStep.Review, edit.now.reached)
        val draft = edit.now.draft
        assertEquals(hope.backgroundEffects, draft.backgroundEffects)
        assertEquals(hope.voiceEffects, draft.voiceEffects)
        assertEquals(hope.hopeEffects, draft.hopeEffects)
        assertEquals(hope.coverEffects, draft.coverEffects)
        assertEquals(1_000L to 27_000L, draft.voiceTrimStartMs to draft.voiceTrimEndMs)

        assertEquals(EditorStep.Background, viewModel(EditorArgs(hopeId = id, openStep = EditorStep.Background)).now.step)
        assertEquals(EditorStep.Cover, viewModel(EditorArgs(hopeId = id, openStep = EditorStep.Cover)).now.step)
    }

    /** Lo compartido o elegido en Assets abre el primer paso con lo que trae puesto. */
    @Test
    fun aNewHopeOpensInTheFirstStepWithWhatItBrings() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val cover = add(AssetNameKind.Frame)
        val vm = viewModel(EditorArgs(coverAssetId = cover.id, backgroundAssetId = background.id))
        assertEquals(EditorStep.Cover, vm.now.step)
        assertEquals(cover, vm.now.draft.cover)
        assertEquals(background, vm.now.draft.background)
    }

    /** Escuchar un candidato no cambia el borrador; elegirlo sí, y deja de sonar. */
    @Test
    fun listeningToACandidateKeepsTheDraftAndChoosingChangesIt() {
        val current = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val first = add(AssetNameKind.Audio, durationMs = 48_000)
        val second = add(AssetNameKind.Audio, durationMs = 30_000)
        val vm = viewModel(EditorArgs(backgroundAssetId = current.id))
        vm.toggleListen()
        assertTrue(player.state.value.isPlaying)

        vm.togglePickerPreview(first)
        assertEquals(first.id, vm.now.preview.playingId)
        // Se oye una sola cosa: la mezcla del editor se detuvo.
        assertFalse(player.state.value.isPlaying)
        vm.togglePickerPreview(second)
        assertEquals(second.id, vm.now.preview.playingId)
        assertEquals(current, vm.now.draft.background)

        vm.closePicker()
        assertNull(vm.now.preview.assetId)
        assertEquals(current, vm.now.draft.background)

        vm.togglePickerPreview(first)
        vm.choose(EditorSlot.Background, first)
        assertEquals(first, vm.now.draft.background)
        assertNull(vm.now.preview.assetId)
    }

    /** La onda se calcula una vez por audio: recortar o cambiar el volumen no la vuelve a pedir. */
    @Test
    fun theWaveIsComputedOncePerAudioAndRetriedAfterAnError() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val other = add(AssetNameKind.Audio, durationMs = 30_000)
        val vm = viewModel(EditorArgs(backgroundAssetId = background.id))
        waitUntil { vm.state.value.backgroundWave is WaveState.Ready }
        vm.setTrim(4_000, 40_000)
        vm.setBackgroundVolume(40)
        vm.setTrim(5_000, 40_000)
        waitUntil { vm.state.value.draft.trimStartMs == 5_000L }
        assertEquals(listOf(background.id), waveforms.computed)

        waveforms.failing += other.id
        vm.choose(EditorSlot.Background, other)
        waitUntil { vm.state.value.backgroundWave == WaveState.Failed }
        waveforms.failing.clear()
        vm.retryWaves()
        waitUntil { vm.state.value.backgroundWave is WaveState.Ready }
        assertEquals(listOf(background.id, other.id, other.id), waveforms.computed)
    }

    /** Los recortes se acotan al archivo y siempre dejan un tramo; el audio original no cambia. */
    @Test
    fun trimsStayInsideTheFileAndTheOriginalIsUntouched() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 10_000)
        val voice = add(AssetNameKind.Recording, durationMs = 6_000)
        val bytes = voice.file.readBytes()
        val id = runBlocking {
            hopes.save(Hope(title = "Respira", coverAssetId = null, backgroundAssetId = background.id, voiceAssetId = voice.id,
                backgroundVolume = 100, voiceVolume = 100, trimStartMs = 0, trimEndMs = null, voiceOffsetMs = 0, createdAt = 0))
        }
        val vm = viewModel(EditorArgs(hopeId = id))

        vm.setVoiceTrim(1_000, 5_000)
        assertEquals(1_000L to 5_000L, vm.now.draft.voiceTrimStartMs to vm.now.draft.voiceTrimEndMs)
        vm.setVoiceTrim(4_900, 4_900)
        assertEquals(4_900L to 5_400L, vm.now.draft.voiceTrimStartMs to vm.now.draft.voiceTrimEndMs)
        vm.setVoiceTrim(-500, 99_000)
        assertEquals(0L to null, vm.now.draft.voiceTrimStartMs to vm.now.draft.voiceTrimEndMs)
        vm.setVoiceTrim(1_000, 5_000)
        vm.setTrim(9_800, 20_000)
        assertEquals(9_500L to null, vm.now.draft.trimStartMs to vm.now.draft.trimEndMs)

        vm.save()
        waitUntil { vm.state.value.saved }
        val saved = savedHopes().single().hope
        assertEquals(1_000L to 5_000L, saved.voiceTrimStartMs to saved.voiceTrimEndMs)
        assertTrue(bytes.contentEquals(voice.file.readBytes()))
    }

    /**
     * Desde cada hoja se escucha su parte con los efectos que se prueban, antes de cerrarla.
     * «Cancelar» deja el borrador como estaba; «Aceptar» se los pasa.
     */
    @Test
    fun eachEffectsSheetIsHeardBeforeClosingAndOnlyAcceptChangesTheDraft() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val voice = add(AssetNameKind.Recording, durationMs = 10_000)
        val cover = add(AssetNameKind.Frame)
        val id = runBlocking {
            hopes.save(Hope(title = "Respira", coverAssetId = cover.id, backgroundAssetId = background.id, voiceAssetId = voice.id,
                backgroundVolume = 100, voiceVolume = 100, trimStartMs = 0, trimEndMs = null, voiceOffsetMs = 2_000, createdAt = 0))
        }
        val vm = viewModel(EditorArgs(hopeId = id))

        vm.openEffects(EffectsPart.Background)
        vm.editBackgroundEffects { it.copy(room = RoomEffect(RoomType.Hall, 30), speed = SpeedLevel.Slow) }
        vm.toggleSheetListen()
        val heard = player.toggled.last()
        assertEquals("editor-background", heard.first)
        assertEquals(RoomEffect(RoomType.Hall, 30), heard.second.backgroundEffects.room)
        assertNull(heard.second.voice)
        assertEquals(BackgroundEffects(), vm.now.draft.backgroundEffects)
        vm.cancelEffects()
        assertNull(vm.now.effects)
        assertEquals(BackgroundEffects(), vm.now.draft.backgroundEffects)
        assertTrue("editor-background" in player.stopped)

        vm.openEffects(EffectsPart.Voice)
        vm.editVoiceEffects { it.copy(clearVoice = true, fades = Fades(500, 1_000)) }
        vm.toggleSheetListen()
        assertEquals("editor-voice", player.toggled.last().first)
        assertTrue(player.toggled.last().second.voiceEffects.clearVoice)
        assertNull(player.toggled.last().second.background)
        vm.acceptEffects()
        assertEquals(VoiceEffects(Fades(500, 1_000), clearVoice = true), vm.now.draft.voiceEffects)

        vm.openEffects(EffectsPart.Hope)
        vm.editHopeEffects { it.copy(fades = Fades(1_000, 3_000), duck = DuckLevel.Much) }
        vm.toggleSheetListen()
        assertEquals("editor", player.toggled.last().first)
        assertEquals(HopeEffects(Fades(1_000, 3_000), DuckLevel.Much), player.toggled.last().second.hopeEffects)
        vm.acceptEffects()

        vm.openEffects(EffectsPart.Cover)
        vm.editCoverEffects { it.copy(filter = CoverFilter.Mono) }
        assertTrue(vm.now.draft.coverEffects.isEmpty)
        vm.acceptEffects()
        assertEquals(CoverFilter.Mono, vm.now.draft.coverEffects.filter)

        vm.save()
        waitUntil { vm.state.value.saved }
        val saved = savedHopes().single().hope
        assertEquals(VoiceEffects(Fades(500, 1_000), clearVoice = true), saved.voiceEffects)
        assertEquals(HopeEffects(Fades(1_000, 3_000), DuckLevel.Much), saved.hopeEffects)
        assertEquals(CoverFilter.Mono, saved.coverEffects.filter)
    }

    /** Sin voz, el fondo no puede bajar bajo la voz: el control no se activa y no se guarda. */
    @Test
    fun theBackgroundCannotDuckWithoutAVoice() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val vm = viewModel(EditorArgs(backgroundAssetId = background.id))
        vm.openEffects(EffectsPart.Hope)
        vm.editHopeEffects { it.copy(duck = DuckLevel.Much) }
        assertNull(vm.now.effects?.hope?.duck)
    }

    /** El borrador suena igual que la misma hope guardada y vuelta a leer, con todos sus efectos. */
    @Test
    fun theDraftSoundsLikeTheSavedAndReloadedHope() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 72_000)
        val voice = add(AssetNameKind.Recording, durationMs = 41_000)
        val cover = add(AssetNameKind.Frame)
        val id = runBlocking { hopes.save(fullHope(background, voice, cover)) }
        val vm = viewModel(EditorArgs(hopeId = id))

        vm.toggleListen()
        val editorMix = player.toggled.last().second
        vm.save()
        waitUntil { vm.state.value.saved }
        val libraryMix = runBlocking { hopes.get(id)!! }.toMixSpec()

        assertEquals(libraryMix, editorMix)
        // El fondo lento alarga la hope: 44 s a 0,9× son 48,9 s.
        assertEquals(48_889L, editorMix.durationMs)
    }

    /** Volver de una importación desde un paso pone lo importado una sola vez y deja el resto. */
    @Test
    fun anImportFromAStepKeepsTheRestOfTheDraft() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val voice = add(AssetNameKind.Recording, durationMs = 10_000)
        val cover = add(AssetNameKind.Frame)
        val id = runBlocking { hopes.save(fullHope(background, voice, cover)) }
        val vm = viewModel(EditorArgs(hopeId = id, openStep = EditorStep.Cover))
        val before = vm.now.draft
        val imported = add(AssetNameKind.Image)

        vm.applyImported(EditorSlot.Cover, listOf(imported))

        assertEquals(before.copy(cover = imported), vm.now.draft)
        assertEquals(EditorStep.Cover, vm.now.step)
    }
}
