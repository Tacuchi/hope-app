package dev.tacuchi.hope.ui.importing

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.HopeDatabase
import dev.tacuchi.hope.importing.AudioExtractor
import dev.tacuchi.hope.importing.ImportStaging
import dev.tacuchi.hope.importing.MediaInspection
import dev.tacuchi.hope.importing.MediaInspector
import dev.tacuchi.hope.testing.waitUntil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Dice qué es cada archivo sin abrirlo: lo que la prueba fije en [next]. */
private class FakeInspector : MediaInspector() {
    var next = MediaInspection(isImage = false, hasVideo = false, hasAudio = true, durationMs = 5_000)
    override suspend fun inspect(file: File) = next
}

/** Extrae sin Media3: escribe bytes de mentira o falla. Con [gate] espera hasta que se complete. */
private class FakeExtractor(context: Context) : AudioExtractor(context) {
    var failure: Exception? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun extract(input: File, output: File, onProgress: (Int?) -> Unit): Long {
        onProgress(50)
        gate?.await()
        failure?.let { throw it }
        output.writeBytes(ByteArray(32) { 2 })
        return 20_000
    }
}

@RunWith(AndroidJUnit4::class)
class ImportViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: HopeDatabase
    private lateinit var repository: AssetRepository
    private lateinit var stagingRoot: File
    private val inspector = FakeInspector()
    private val extractor = FakeExtractor(context)
    private lateinit var vm: ImportViewModel

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HopeDatabase::class.java).build()
        repository = AssetRepository(database, temp.newFolder("assets"))
        stagingRoot = temp.newFolder("import-tmp")
        // Restos de una importación que el sistema cortó: el ViewModel los borra al crearse.
        File(stagingRoot, "sesion-anterior").mkdirs()
        vm = ImportViewModel(ImportStaging(context.contentResolver, stagingRoot), inspector, extractor, repository)
        waitUntil { stagingIsEmpty() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Un archivo recibido, como lo daría otra app. */
    private fun received(name: String): Uri = Uri.fromFile(temp.newFile(name).apply { writeBytes(ByteArray(64) { 1 }) })

    private fun savedAssets() = runBlocking { repository.observe(null).first() }

    private fun stagingIsEmpty() = stagingRoot.listFiles().isNullOrEmpty()

    private fun finished(): ImportState.Finished {
        waitUntil { vm.state.value is ImportState.Finished }
        return vm.state.value as ImportState.Finished
    }

    @Test
    fun anAudioFromAssetsGoesBackToAssets() {
        vm.start(received("cancion.mp3"), declaredMime = null, origin = AssetOrigin.Files)

        val result = finished()

        assertEquals(ImportPurpose.Catalog, result.purpose)
        assertEquals(AssetNameKind.Audio, result.assets.single().nameKind)
        assertEquals(5_000L, result.assets.single().durationMs)
        assertEquals(result.assets, savedAssets())
        assertTrue(stagingIsEmpty())

        vm.acknowledgeFinished()
        assertEquals(ImportState.Idle, vm.state.value)
    }

    @Test
    fun aSharedImageGoesToANewHope() {
        inspector.next = MediaInspection(isImage = true, hasVideo = false, hasAudio = false, durationMs = null)

        vm.start(received("foto.png"), declaredMime = "image/png", origin = AssetOrigin.WhatsApp, purpose = ImportPurpose.Share)

        val result = finished()
        assertEquals(ImportPurpose.Share, result.purpose)
        assertEquals(AssetNameKind.Image, result.assets.single().nameKind)
        assertEquals(AssetOrigin.WhatsApp, result.assets.single().origin)
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun anImportFromTheEditorReturnsToTheSlotThatAsked() {
        vm.start(received("nota.ogg"), declaredMime = null, origin = AssetOrigin.Files, purpose = ImportPurpose.EditorBackground)

        val result = finished()
        assertEquals(ImportPurpose.EditorBackground, result.purpose)
        assertEquals(AssetNameKind.VoiceNote, result.assets.single().nameKind)
    }

    @Test
    fun anUnsupportedFileIsReportedAndCancellingLeavesNothing() {
        inspector.next = MediaInspection(isImage = false, hasVideo = false, hasAudio = false, durationMs = null)

        vm.start(received("documento.pdf"), declaredMime = "application/pdf", origin = AssetOrigin.Files)
        waitUntil { vm.state.value is ImportState.Unsupported }
        assertEquals(ImportState.Unsupported("PDF (application/pdf)"), vm.state.value)

        vm.cancel()

        assertEquals(ImportState.Idle, vm.state.value)
        assertTrue(stagingIsEmpty())
        assertTrue(savedAssets().isEmpty())
    }

    @Test
    fun aFailedExtractionCreatesNothingAndCleansUp() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)
        extractor.failure = IOException("códec raro")

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok, purpose = ImportPurpose.Share)
        waitUntil { vm.state.value == ImportState.Failed }

        assertTrue(stagingIsEmpty())
        assertTrue(savedAssets().isEmpty())
    }

    @Test
    fun cancellingWhileExtractingCreatesNothingAndCleansUp() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)
        extractor.gate = CompletableDeferred()

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok)
        waitUntil { vm.state.value == ImportState.Extracting(50) }
        vm.cancel()
        waitUntil { stagingIsEmpty() }

        assertEquals(ImportState.Idle, vm.state.value)
        assertTrue(savedAssets().isEmpty())
    }

    @Test
    fun cancellingTheCoverKeepsTheExtractedAudio() {
        inspector.next = MediaInspection(isImage = false, hasVideo = true, hasAudio = true, durationMs = 20_000)

        vm.start(received("video.mp4"), declaredMime = null, origin = AssetOrigin.TikTok, purpose = ImportPurpose.Share)
        waitUntil { vm.state.value is ImportState.ChoosingCover }
        vm.cancel()

        val result = finished()
        assertEquals(ImportPurpose.Share, result.purpose)
        val audio = result.assets.single()
        assertEquals(AssetNameKind.AudioFromVideo, audio.nameKind)
        assertEquals(AssetOrigin.TikTok, audio.origin)
        assertEquals(20_000L, audio.durationMs)
        assertEquals(listOf(audio), savedAssets())
        assertTrue(stagingIsEmpty())
    }
}
