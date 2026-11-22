package dev.tacuchi.hope.ui.assets

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.media.AssetPreview
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.testing.FakeAssetRepository
import dev.tacuchi.hope.domain.testing.FakeHopeRepository
import dev.tacuchi.hope.domain.testing.collectWhileTesting
import dev.tacuchi.hope.domain.usecase.DeleteAssetUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetUsesUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetsUseCase
import dev.tacuchi.hope.domain.usecase.ObserveHopesUseCase
import dev.tacuchi.hope.domain.usecase.ReleaseAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.StopAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ToggleAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.UseAssetInHopeUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Job
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

/** Preescucha de Assets sin ExoPlayer. */
private class FakeAssetPreview : AssetPreview {
    override val playingId = MutableStateFlow<Long?>(null)
    val toggledFiles = mutableListOf<File>()
    var released = false

    override fun toggle(id: Long, file: File) {
        toggledFiles += file
        playingId.value = if (playingId.value == id) null else id
    }

    override fun stopIfCurrent(id: Long) {
        if (playingId.value == id) playingId.value = null
    }

    override fun release() {
        released = true
        playingId.value = null
    }
}

@RunWith(AndroidJUnit4::class)
class AssetsViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var assets: FakeAssetRepository
    private lateinit var hopes: FakeHopeRepository
    private val preview = FakeAssetPreview()
    private val collectors = mutableListOf<Job>()
    private var clock = 1_000L

    @Before
    fun setUp() {
        assets = FakeAssetRepository(temp.newFolder("assets")) { clock++ }
        hopes = FakeHopeRepository(assets) { clock++ }
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
    }

    private fun viewModel() = AssetsViewModel(
        ObserveAssetsUseCase(assets),
        ObserveAssetUsesUseCase(hopes),
        ObserveHopesUseCase(hopes),
        ObserveAssetPreviewUseCase(preview),
        ToggleAssetPreviewUseCase(preview),
        StopAssetPreviewUseCase(preview),
        ReleaseAssetPreviewUseCase(preview),
        DeleteAssetUseCase(assets),
        UseAssetInHopeUseCase(hopes),
    ).also {
        collectors += collectWhileTesting(it.state)
        waitUntil { it.state.value.assets != null }
    }

    private val AssetsViewModel.now: AssetsUiState
        get() {
            shadowOf(Looper.getMainLooper()).idle()
            return state.value
        }

    private fun add(kind: AssetNameKind, durationMs: Long? = null): Asset = runBlocking {
        val file = temp.newFile("f-${clock++}.bin").apply { writeText("datos") }
        assets.add(listOf(NewAsset(file, kind, AssetOrigin.TikTok, "audio/mp4", durationMs = durationMs))).single()
    }

    private fun saveHope(title: String, background: Asset? = null, cover: Asset? = null): Long = runBlocking {
        hopes.save(
            Hope(
                title = title, coverAssetId = cover?.id, backgroundAssetId = background?.id, voiceAssetId = null,
                backgroundVolume = 100, voiceVolume = 100, trimStartMs = 0, trimEndMs = null,
                voiceOffsetMs = 0, createdAt = 0,
            ),
        )
    }

    @Test
    fun theFilterNarrowsTheCatalogByType() {
        val audio = add(AssetNameKind.Audio, durationMs = 5_000)
        val frame = add(AssetNameKind.Frame)
        val vm = viewModel()
        assertEquals(setOf(audio, frame), vm.now.assets!!.toSet())

        vm.selectFilter(AssetFilter.Image)
        waitUntil { vm.state.value.filter == AssetFilter.Image && vm.state.value.assets == listOf(frame) }

        vm.selectFilter(AssetFilter.Audio)
        waitUntil { vm.state.value.assets == listOf(audio) }
        assertEquals(AssetFilter.Audio, vm.now.filter)
    }

    @Test
    fun previewPlaysOnlyAudioAndShowsWhatSounds() {
        val audio = add(AssetNameKind.Audio, durationMs = 5_000)
        val frame = add(AssetNameKind.Frame)
        val vm = viewModel()

        vm.togglePlay(frame)
        assertTrue(preview.toggledFiles.isEmpty())

        vm.togglePlay(audio)
        assertEquals(listOf(audio.file), preview.toggledFiles)
        assertEquals(audio.id, vm.now.playingId)

        vm.togglePlay(audio)
        assertNull(vm.now.playingId)
    }

    @Test
    fun showsWhichHopesUseEachAssetAndCanPlaceOneInASavedHope() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val frame = add(AssetNameKind.Frame)
        val id = saveHope("Respira", background = background)
        val vm = viewModel()
        waitUntil { vm.state.value.hopes.isNotEmpty() }
        assertEquals(listOf(HopeChoice(id, "Respira")), vm.now.hopes)
        assertEquals(listOf(AssetUse(background.id, id, "Respira")), vm.now.uses[background.id])

        var opened: Long? = null
        vm.useInHope(frame, id) { opened = it }
        waitUntil { opened != null }

        assertEquals(id, opened)
        assertEquals(frame, runBlocking { hopes.get(id) }!!.cover)
        waitUntil { vm.state.value.uses[frame.id] == listOf(AssetUse(frame.id, id, "Respira")) }
    }

    @Test
    fun deletingAFreeAssetRemovesItAndStopsItsPreview() {
        val audio = add(AssetNameKind.Audio, durationMs = 5_000)
        val vm = viewModel()
        vm.togglePlay(audio)

        vm.delete(audio)
        waitUntil { vm.state.value.assets?.isEmpty() == true }

        assertNull(vm.now.playingId)
        assertFalse(audio.file.exists())
    }

    @Test
    fun anAssetInUseIsNotDeleted() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        saveHope("Respira", background = background)
        val vm = viewModel()

        vm.delete(background)
        // El borrado corre en segundo plano y no cambia el estado: se le da tiempo a terminar.
        repeat(20) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }

        assertEquals(listOf(background), vm.now.assets)
        assertTrue(background.file.exists())
    }

    @Test
    fun leavingTheScreenReleasesThePreview() {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[AssetsViewModel::class]

        store.clear()

        assertTrue(preview.released)
    }
}
