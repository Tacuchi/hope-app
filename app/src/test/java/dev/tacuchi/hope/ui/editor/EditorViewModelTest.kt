package dev.tacuchi.hope.ui.editor

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.AssetType
import dev.tacuchi.hope.data.Hope
import dev.tacuchi.hope.data.HopeDatabase
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.data.NewAsset
import dev.tacuchi.hope.importing.MediaInspector
import dev.tacuchi.hope.playback.MixPlayer
import dev.tacuchi.hope.recording.AudioCapture
import dev.tacuchi.hope.recording.VoiceRecorder
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

/** Graba bytes de mentira: el micrófono no existe en las pruebas. */
private class FakeCapture : AudioCapture {
    private var target: File? = null
    override fun start(target: File) {
        this.target = target.apply { writeBytes(ByteArray(64) { 1 }) }
    }
    override fun stop() {
        target = null
    }
    override fun release() {
        target = null
    }
}

@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: HopeDatabase
    private lateinit var assets: AssetRepository
    private lateinit var hopes: HopeRepository
    private lateinit var recordingDir: File
    private val collectors = mutableListOf<Job>()
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HopeDatabase::class.java)
            .build()
        assets = AssetRepository(database, temp.newFolder("assets")) { clock++ }
        hopes = HopeRepository(database) { clock++ }
        recordingDir = temp.newFolder("recording-tmp")
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
        database.close()
    }

    private fun viewModel(args: EditorArgs = EditorArgs(), headphones: Boolean = false) = EditorViewModel(
        args = args,
        hopes = hopes,
        assets = assets,
        player = MixPlayer(ApplicationProvider.getApplicationContext()),
        recorder = VoiceRecorder(FakeCapture(), recordingDir),
        inspector = MediaInspector(),
        headphonesConnected = { headphones },
    ).also { vm ->
        collectors += CoroutineScope(Dispatchers.Main.immediate).launch { vm.state.collect {} }
        waitUntil { !vm.now.loading }
    }

    /** Room trabaja en sus hilos y vuelve al principal: se procesa el principal hasta que se cumpla. */
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
        assertTrue(assets.fileOf(voice).exists())
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
}
