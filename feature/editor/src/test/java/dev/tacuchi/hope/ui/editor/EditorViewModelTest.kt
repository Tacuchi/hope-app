package dev.tacuchi.hope.ui.editor

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.media.AudioOutputs
import dev.tacuchi.hope.domain.media.VoiceRecorder
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkNetwork
import dev.tacuchi.hope.domain.model.LinkPreview
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.repository.LinkPreviewRepository
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
            discardRecordingLeftovers = DiscardRecordingLeftoversUseCase(recorder),
            getHope = GetHopeUseCase(hopes),
            getAsset = GetAssetUseCase(assets),
            saveHope = SaveHopeUseCase(hopes),
            recognizeLink = RecognizeLinkUseCase(),
            getLinkPreview = GetLinkPreviewUseCase(previews),
            toggleMix = ToggleMixUseCase(player),
            playMix = PlayMixUseCase(player),
            updateMix = UpdateMixUseCase(player),
            stopMix = StopMixUseCase(player),
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
        assertEquals(2_000L, saved.hope.voiceOffsetMs)
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
}
