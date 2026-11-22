package dev.tacuchi.hope.ui.library

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.MixPlayback
import dev.tacuchi.hope.domain.model.NewAsset
import dev.tacuchi.hope.domain.model.libraryKey
import dev.tacuchi.hope.domain.testing.FakeAssetRepository
import dev.tacuchi.hope.domain.testing.FakeHopePlayer
import dev.tacuchi.hope.domain.testing.FakeHopeRepository
import dev.tacuchi.hope.domain.testing.collectWhileTesting
import dev.tacuchi.hope.domain.usecase.DeleteHopeUseCase
import dev.tacuchi.hope.domain.usecase.ObserveHopesUseCase
import dev.tacuchi.hope.domain.usecase.ObservePlaybackUseCase
import dev.tacuchi.hope.domain.usecase.ToggleHopePlaybackUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class LibraryViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var assets: FakeAssetRepository
    private lateinit var hopes: FakeHopeRepository
    private val player = FakeHopePlayer()
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

    private fun viewModel() = LibraryViewModel(
        ObserveHopesUseCase(hopes),
        ObservePlaybackUseCase(player),
        ToggleHopePlaybackUseCase(player),
        DeleteHopeUseCase(hopes, player),
    ).also { collectors += collectWhileTesting(it.state) }

    private val LibraryViewModel.now: LibraryUiState
        get() {
            shadowOf(Looper.getMainLooper()).idle()
            return state.value
        }

    private fun add(kind: AssetNameKind, durationMs: Long? = null): Asset = runBlocking {
        val file = temp.newFile("f-${clock++}.bin").apply { writeText("datos") }
        assets.add(listOf(NewAsset(file, kind, AssetOrigin.TikTok, "audio/mp4", durationMs = durationMs))).single()
    }

    private fun saveHope(title: String, background: Asset, cover: Asset? = null): Long = runBlocking {
        hopes.save(
            Hope(
                title = title, coverAssetId = cover?.id, backgroundAssetId = background.id, voiceAssetId = null,
                backgroundVolume = 70, voiceVolume = 100, trimStartMs = 2_000, trimEndMs = 30_000,
                voiceOffsetMs = 0, createdAt = 0,
            ),
        )
    }

    @Test
    fun showsTheSavedHopesWithTheirCoverOnceRead() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val cover = add(AssetNameKind.Frame)
        saveHope("Respira", background, cover)
        saveHope("Antes de entrar", background)

        val vm = viewModel()
        assertNull(LibraryUiState().items)
        waitUntil { vm.state.value.items != null }

        val items = vm.now.items!!
        assertEquals(listOf("Antes de entrar", "Respira"), items.map { it.title })
        assertEquals(cover.file, items.last().coverFile)
        assertNull(items.first().coverFile)
        assertEquals(28_000L, items.first().durationMs)
    }

    @Test
    fun tappingAHopePlaysItWithItsSettingsAndTappingAgainPauses() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val id = saveHope("Respira", background)
        val vm = viewModel()
        waitUntil { vm.state.value.items?.size == 1 }
        val item = vm.now.items!!.single()

        vm.togglePlay(item)

        val (key, spec) = player.toggled.single()
        assertEquals(libraryKey(id), key)
        assertEquals(background.file, spec.background)
        assertEquals(70, spec.backgroundVolume)
        assertEquals(2_000L, spec.trimStartMs)
        assertEquals("Respira", spec.presentation.title)
        assertEquals(id, vm.now.playingId)

        vm.togglePlay(item)
        assertNull(vm.now.playingId)
    }

    @Test
    fun whatTheEditorPlaysIsNotMarkedInTheLibrary() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        saveHope("Respira", background)
        val vm = viewModel()
        waitUntil { vm.state.value.items?.size == 1 }

        player.state.value = MixPlayback(key = "editor", isPlaying = true)

        assertNull(vm.now.playingId)
    }

    @Test
    fun deletingAHopeStopsItAndKeepsItsAssets() {
        val background = add(AssetNameKind.AudioFromVideo, durationMs = 52_000)
        val id = saveHope("Respira", background)
        val vm = viewModel()
        waitUntil { vm.state.value.items?.size == 1 }
        val item = vm.now.items!!.single()
        vm.togglePlay(item)

        vm.delete(item)
        waitUntil { vm.state.value.items?.isEmpty() == true }

        assertEquals(listOf(libraryKey(id)), player.stopped)
        assertNull(vm.now.playingId)
        assertEquals(listOf(background), runBlocking { assets.observe(null).first() })
    }
}
