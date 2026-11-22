package dev.tacuchi.hope.ui.assets

import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.AssetUse
import dev.tacuchi.hope.data.Hope
import dev.tacuchi.hope.data.HopeDatabase
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.data.NewAsset
import dev.tacuchi.hope.testing.FakeAssetPreview
import dev.tacuchi.hope.testing.collectWhileTesting
import dev.tacuchi.hope.testing.waitUntil
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

@RunWith(AndroidJUnit4::class)
class AssetsViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: HopeDatabase
    private lateinit var assets: AssetRepository
    private lateinit var hopes: HopeRepository
    private val preview = FakeAssetPreview()
    private val collectors = mutableListOf<Job>()
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HopeDatabase::class.java)
            .build()
        assets = AssetRepository(database, temp.newFolder("assets")) { clock++ }
        hopes = HopeRepository(database) { clock++ }
    }

    @After
    fun tearDown() {
        collectors.forEach { it.cancel() }
        database.close()
    }

    private fun viewModel() = AssetsViewModel(assets, hopes, preview).also {
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
        assertEquals(listOf(assets.fileOf(audio)), preview.toggledFiles)
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
        assertFalse(assets.fileOf(audio).exists())
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
        assertTrue(assets.fileOf(background).exists())
    }

    @Test
    fun leavingTheScreenReleasesThePreview() {
        val store = ViewModelStore()
        ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[AssetsViewModel::class]

        store.clear()

        assertTrue(preview.released)
    }
}
